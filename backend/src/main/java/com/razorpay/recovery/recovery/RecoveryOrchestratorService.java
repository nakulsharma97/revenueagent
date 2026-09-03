package com.razorpay.recovery.recovery;
import com.razorpay.recovery.recovery.RecoveryAttempt.AttemptOutcome;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.recovery.RecoveryAttempt.SourceType;
import com.razorpay.recovery.recovery.RecoveryAttempt.UpliftSegment;

import com.razorpay.recovery.audit.AuditEvent;
import com.razorpay.recovery.audit.AuditService;
import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.intelligence.RecoveryIntelligenceService;
import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.checkout.CheckoutSession.CheckoutStatus;
import com.razorpay.recovery.customer.Customer;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.receivable.Receivable.ReceivableStatus;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.TransactionRepository;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import com.razorpay.recovery.checkout.CheckoutSessionRepository;
import com.razorpay.recovery.receivable.ReceivableRepository;
import com.razorpay.recovery.recovery.mocks.MockPaymentGatewayService;
import com.razorpay.recovery.recovery.mocks.MockNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The end-to-end loop for ALL three revenue sources:
 * [1] Detection -> [2] Diagnosis -> [3] Decision -> [4] Execution -> [5] Ledger
 *
 * Handles: payment failures (subscription dunning), checkout abandonment, B2B overdue receivables.
 *
 * <p><b>Single processing core.</b> Every batch entry point — the blocking REST endpoint
 * ({@link #runBatch()}), the SSE streaming endpoint ({@link #runBatchWithCallback}), the startup
 * auto-run, and the scheduled run — funnels through {@link #runBatchWithCallback}, so REST and
 * streaming can never produce different decisions for the same entity. Control-group handling,
 * uplift segmentation, and customer-segment-aware bounds are applied identically everywhere.
 */
@Service
public class RecoveryOrchestratorService {

    private final TransactionRepository transactionRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ReceivableRepository receivableRepository;
    private final RecoveryAttemptRepository attemptRepository;
    private final DecisionAgentService decisionAgentService;
    private final MockPaymentGatewayService paymentGateway;
    private final MockNotificationService notificationService;
    private final BoundsConfig boundsConfig;
    private final RulesEngine rulesEngine;
    private final UpliftSegmentationService upliftService;
    private final AuditService auditService;
    private final RecoveryIntelligenceService intelligenceService;

    private int getCooldownMinutes() { return boundsConfig.getRetryCooldownMinutes(); }
    private int getMaxRetries() { return boundsConfig.getMaxRetries(); }

    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    public RecoveryOrchestratorService(TransactionRepository transactionRepository,
                                        CheckoutSessionRepository checkoutSessionRepository,
                                        ReceivableRepository receivableRepository,
                                        RecoveryAttemptRepository attemptRepository,
                                        DecisionAgentService decisionAgentService,
                                        MockPaymentGatewayService paymentGateway,
                                        MockNotificationService notificationService,
                                        BoundsConfig boundsConfig,
                                        RulesEngine rulesEngine,
                                        UpliftSegmentationService upliftService,
                                        AuditService auditService,
                                        RecoveryIntelligenceService intelligenceService) {
        this.transactionRepository = transactionRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.receivableRepository = receivableRepository;
        this.attemptRepository = attemptRepository;
        this.decisionAgentService = decisionAgentService;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.boundsConfig = boundsConfig;
        this.rulesEngine = rulesEngine;
        this.upliftService = upliftService;
        this.auditService = auditService;
        this.intelligenceService = intelligenceService;
    }

    /** True while a batch is executing — lets callers (e.g. the scheduler) skip gracefully. */
    public boolean isBatchRunning() {
        return batchRunning.get();
    }

    /**
     * Count eligible items across all 3 sources WITHOUT processing them.
     * Used by the SSE endpoint to send a 'total' event before the batch starts,
     * so the frontend progress bar can show accurate percentage.
     * The predicates here MUST match the worklist queries in {@link #runBatchWithCallback}
     * exactly — they are the single source of truth for the batch size.
     */
    public int countEligible() {
        int payments = transactionRepository.findByStatusIn(
                List.of(TransactionStatus.AT_RISK, TransactionStatus.IN_RECOVERY)).size();
        int checkouts = checkoutSessionRepository.findByStatusIn(
                List.of(CheckoutStatus.ABANDONED)).size();
        int receivables = receivableRepository.findByStatusIn(
                List.of(ReceivableStatus.OVERDUE)).size();
        return payments + checkouts + receivables;
    }

    /**
     * Blocking batch (POST /api/recovery/run-batch). Delegates to the shared streaming
     * implementation with a no-op callback so both paths share identical business logic.
     */
    @Transactional
    public List<RecoveryAttempt> runBatch() {
        return runBatchWithCallback(a -> { });
    }

    /**
     * Streaming variant: the single canonical batch implementation used by EVERY entry point.
     * Emits each attempt via {@code onAttempt} as it completes, so the frontend gets
     * incremental progress; the blocking path passes a no-op callback.
     */
    @Transactional
    public List<RecoveryAttempt> runBatchWithCallback(Consumer<RecoveryAttempt> onAttempt) {
        if (!batchRunning.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Batch already running");
        }
        try {
            String batchId = UUID.randomUUID().toString();
            audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.BATCH_STARTED,
                    "BATCH", batchId, "Recovery batch started");

            // Preload ALL existing SUCCESS event IDs in 3 bulk queries
            // (instead of hundreds of individual DB queries per item)
            Set<String> recoveredTxEvents = attemptRepository.findSuccessfulTransactionEventIds();
            Set<String> recoveredSessionEvents = attemptRepository.findSuccessfulCheckoutEventIds();
            Set<String> recoveredReceivableEvents = attemptRepository.findSuccessfulReceivableEventIds();

            List<RecoveryAttempt> all = new ArrayList<>();

            // ── Payment failures (subscription dunning) ──
            for (Transaction tx : transactionRepository.findByStatusIn(
                    List.of(TransactionStatus.AT_RISK, TransactionStatus.IN_RECOVERY))) {
                RecoveryAttempt attempt = processPaymentNoSave(tx, batchId, recoveredTxEvents);
                transactionRepository.save(tx);
                persistAndEmit(attempt, all, onAttempt);
                intelligenceService.recordOutcome(attempt);
            }

            // ── Checkout abandonment ──
            for (CheckoutSession session : checkoutSessionRepository.findByStatusIn(
                    List.of(CheckoutStatus.ABANDONED))) {
                RecoveryAttempt attempt = processCheckoutNoSave(session, batchId, recoveredSessionEvents);
                checkoutSessionRepository.save(session);
                persistAndEmit(attempt, all, onAttempt);
                intelligenceService.recordOutcome(attempt);
            }

            // ── B2B overdue receivables ──
            for (Receivable receivable : receivableRepository.findByStatusIn(
                    List.of(ReceivableStatus.OVERDUE))) {
                RecoveryAttempt attempt = processReceivableNoSave(receivable, batchId, recoveredReceivableEvents);
                receivableRepository.save(receivable);
                persistAndEmit(attempt, all, onAttempt);
                intelligenceService.recordOutcome(attempt);
            }

            long succeeded = all.stream().filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS).count();
            long failed = all.stream().filter(a -> a.getOutcome() == AttemptOutcome.FAILED).count();
            long skipped = all.stream().filter(a -> a.getOutcome() == AttemptOutcome.SKIPPED).count();
            audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.BATCH_COMPLETED,
                    "BATCH", batchId, "Recovery batch completed: " + all.size() + " attempts ("
                            + succeeded + " succeeded, " + failed + " failed, " + skipped + " skipped)");

            return all;
        } finally {
            batchRunning.set(false);
        }
    }

    /**
     * Persist one attempt (flush → real id assigned), then hand it to the SSE callback,
     * so streamed events carry usable ids and earlier attempts survive a mid-batch failure.
     */
    private void persistAndEmit(RecoveryAttempt attempt, List<RecoveryAttempt> all, Consumer<RecoveryAttempt> onAttempt) {
        attemptRepository.saveAndFlush(attempt);
        all.add(attempt);
        onAttempt.accept(attempt);
    }

    // ═══════════════════════════════════════════════════════════════
    // Process one payment failure (canonical, no individual save — caller persists)
    // ═══════════════════════════════════════════════════════════════

    private RecoveryAttempt processPaymentNoSave(Transaction tx, String batchId, Set<String> alreadyRecoveredEvents) {
        DecisionTrace trace = new DecisionTrace();
        trace.add("DETECTION", "Transaction TX#" + tx.getId() + " flagged as AT_RISK (amount=" + tx.getAmount() + ", failureReason=" + tx.getFailureReason() + ")");

        // ── Control group: no intervention, just monitor natural recovery ──
        if (tx.isControlGroup()) {
            trace.add("CONTROL_GROUP", "Entity is in control group — no agent intervention. Monitoring natural recovery.");
            UpliftSegment segment = upliftService.classify(tx);
            boolean naturalSuccess = paymentGateway.attemptCharge(tx); // same probability model, zero intervention
            trace.add("NATURAL_RECOVERY", "MockPaymentGatewayService.attemptCharge(TX#" + tx.getId() + ") -> " + (naturalSuccess ? "SUCCESS" : "FAILED") + " (no intervention)");
            audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.RECOVERY_ATTEMPT_SKIPPED,
                    "TRANSACTION", String.valueOf(tx.getId()), "Control group — no agent intervention applied");
            if (naturalSuccess) {
                tx.setStatus(TransactionStatus.RECOVERED); // recovered naturally — leave the worklist
            }
            return buildControlAttempt(SourceType.PAYMENT, tx, null, null, batchId, segment, naturalSuccess, trace,
                    "Control group: no agent intervention applied. Natural recovery " + (naturalSuccess ? "succeeded" : "failed") + ".");
        }

        // ── Idempotency guard: skip if already successfully recovered ──
        if (tx.getEventId() != null && alreadyRecoveredEvents.contains(tx.getEventId())) {
            trace.add("IDEMPOTENCY_SKIP", "EventId=" + tx.getEventId() + " already has a SUCCESS recovery — skipping to prevent double-count.");
            audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.RECOVERY_ATTEMPT_IDEMPOTENT_SKIP,
                    "TRANSACTION", String.valueOf(tx.getId()), "eventId=" + tx.getEventId() + " already recovered — skipping");
            return persistSkip(tx, null, null, batchId, trace, SkipReason.IDEMPOTENCY,
                    "eventId=" + tx.getEventId() + " already has a SUCCESS recovery — skipped to prevent double-count");
        }

        // ── Cooldown guard ──
        if (tx.getLastAttemptAt() != null) {
            long minSince = Duration.between(tx.getLastAttemptAt(), LocalDateTime.now()).toMinutes();
            if (minSince < getCooldownMinutes()) {
                trace.add("COOLDOWN_SKIP", "Last attempt " + minSince + "min ago, cooldown is " + getCooldownMinutes() + "min — skipping.");
                audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.COOLDOWN_ACTIVE,
                        "TRANSACTION", String.valueOf(tx.getId()), "Cooldown active — last attempt " + minSince + "min ago");
                return persistSkip(tx, null, null, batchId, trace, SkipReason.COOLDOWN,
                        "Last attempt " + minSince + "min ago, cooldown is " + getCooldownMinutes() + "min — skipped");
            }
        }

        // ── Classify uplift segment ──
        UpliftSegment segment = upliftService.classify(tx);
        trace.add("UPLIFT_CLASSIFY", "Segment: " + segment + " (reliability=" + (tx.getSubscription() != null && tx.getSubscription().getCustomer() != null ? tx.getSubscription().getCustomer().getPaymentReliabilityScore() : 0.5) + ", retryCount=" + tx.getRetryCount() + ", amount=" + tx.getAmount() + ")");

        // ── Segment-aware decision (HIGH_VALUE gets wider bounds) ──
        Customer.CustomerSegment customerSegment = decisionAgentService.segmentOf(tx);
        int segmentMaxRetries = boundsConfig.boundsFor(customerSegment).maxRetries();
        DecisionResult result = decisionAgentService.decideWithMeta(tx, customerSegment, trace);
        LlmDecision decision = result.decision();
        audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.AI_RECOMMENDATION_RECEIVED,
                "TRANSACTION", String.valueOf(tx.getId()),
                "Decision: " + decision.action() + " (confidence " + decision.confidence() + ", llmDriven=" + result.llmDriven() + ")");

        // ── Apply uplift-segment filter to eligible actions ──
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx, customerSegment);
        rulesEngine.filterByUpliftSegment(eligible, segment);
        if (!eligible.contains(decision.action())) {
            // Original choice filtered out by uplift segment — pick the first remaining eligible action
            trace.add("UPLIFT_FILTER", "Action " + decision.action() + " removed for segment " + segment + " — falling back to " + eligible.get(0));
            decision = new LlmDecision(eligible.get(0), "Uplift-segment " + segment + " filtered: " + decision.reasoning(), decision.confidence(), decision.discountPercent());
        }

        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setSourceType(SourceType.PAYMENT);
        attempt.setTransaction(tx);
        attempt.setActionTaken(decision.action());
        attempt.setDiscountPercent(decision.discountPercent());
        attempt.setReasoning(decision.reasoning());
        attempt.setConfidence(decision.confidence());
        attempt.setLlmDriven(result.llmDriven());
        attempt.setBatchId(batchId);
        attempt.setExecutedAt(LocalDateTime.now());
        attempt.setRequiresHumanSignoff(result.requiresHumanSignoff());
        attempt.setSignoffReason(result.signoffReason());
        attempt.setDecisionTrace(trace);
        attempt.setCustomerMessage(decision.customerMessage());
        attempt.setUpliftSegment(segment);

        // Intelligence pass BEFORE execution mutates the entity: persists the counterfactual
        // simulation, enriches the attempt with state/fatigue, flags anomalies, routes review.
        intelligenceService.recordDecision(attempt, batchId);

        boolean success = executePayment(tx, decision, attempt);
        trace.add("EXECUTION", "MockPaymentGatewayService.attemptCharge(TX#" + tx.getId() + ") -> " + (success ? "SUCCESS" : "FAILED") + " | action=" + decision.action());
        applyPaymentOutcome(tx, attempt, success, segmentMaxRetries);
        auditExecution(batchId, "TRANSACTION", String.valueOf(tx.getId()), decision.action().name(), success, attempt.getAmountRecovered());

        return attempt;
    }

    private boolean executePayment(Transaction tx, LlmDecision decision, RecoveryAttempt attempt) {
        // Set customerNotified based on action type
        attempt.setCustomerNotified(switch (decision.action()) {
            case RETRY_SILENT, RETRY_NOW, RETRY_SCHEDULED -> false;
            default -> true;
        });
        return switch (decision.action()) {
            case RETRY_SILENT, RETRY_NOW, RETRY_SCHEDULED -> paymentGateway.attemptCharge(tx);
            case SEND_PAYMENT_LINK -> {
                boolean paid = notificationService.sendPaymentLink(tx);
                attempt.setInterventionCost(notificationService.costOf(true));
                yield paid;
            }
            case OFFER_DISCOUNT -> {
                int pct = decision.discountPercent() == null ? 10 : decision.discountPercent();
                boolean paid = notificationService.sendDiscountOffer(tx, pct);
                BigDecimal discountValue = tx.getAmount()
                        .multiply(BigDecimal.valueOf(pct))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                attempt.setInterventionCost(notificationService.costOf(true).add(paid ? discountValue : BigDecimal.ZERO));
                yield paid;
            }
            case ESCALATE_TO_HUMAN, ABANDON -> false;
            default -> false;
        };
    }

    /**
     * Applies the outcome of an executed action to the transaction.
     * The LOST threshold is the <em>segment's</em> retry limit, so a HIGH_VALUE
     * customer (limit 5) is only written off after 5 failed retries, while a
     * STANDARD customer (limit 3) is written off after 3.
     */
    private void applyPaymentOutcome(Transaction tx, RecoveryAttempt attempt, boolean success, int segmentMaxRetries) {
        attempt.setOutcome(success ? AttemptOutcome.SUCCESS : AttemptOutcome.FAILED);
        if (success) {
            attempt.setAmountRecovered(tx.getAmount());
            tx.setStatus(TransactionStatus.RECOVERED);
        } else {
            tx.setRetryCount(tx.getRetryCount() + 1);
            tx.setStatus(tx.getRetryCount() >= segmentMaxRetries ? TransactionStatus.LOST : TransactionStatus.IN_RECOVERY);
        }
        tx.setLastAttemptAt(LocalDateTime.now());
    }

    // ═══════════════════════════════════════════════════════════════
    // Process one checkout abandonment (canonical — the caller persists)
    // ═══════════════════════════════════════════════════════════════

    private RecoveryAttempt processCheckoutNoSave(CheckoutSession session, String batchId, Set<String> alreadyRecoveredEvents) {
        DecisionTrace trace = new DecisionTrace();
        trace.add("DETECTION", "CheckoutSession#" + session.getId() + " flagged as ABANDONED (amount=" + session.getCartAmount() + ", reason=" + session.getAbandonmentReason() + ")");

        // ── Control group: no intervention ──
        if (session.isControlGroup()) {
            trace.add("CONTROL_GROUP", "Entity is in control group — no agent intervention.");
            UpliftSegment segment = upliftService.classify(session);
            boolean naturalSuccess = notificationService.sendCheckoutReminder(session); // same baseline, no discount
            trace.add("NATURAL_RECOVERY", "Natural recovery for Checkout#" + session.getId() + " -> " + (naturalSuccess ? "SUCCESS" : "FAILED"));
            audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.RECOVERY_ATTEMPT_SKIPPED,
                    "CHECKOUT_SESSION", String.valueOf(session.getId()), "Control group — no agent intervention applied");
            if (naturalSuccess) {
                session.setStatus(CheckoutStatus.RECOVERED); // recovered naturally — leave the worklist
            }
            return buildControlAttempt(SourceType.CHECKOUT, null, session, null, batchId, segment, naturalSuccess, trace,
                    "Control group: no agent intervention applied. Natural recovery " + (naturalSuccess ? "succeeded" : "failed") + ".");
        }

        // ── Idempotency guard ──
        if (session.getEventId() != null && alreadyRecoveredEvents.contains(session.getEventId())) {
            trace.add("IDEMPOTENCY_SKIP", "EventId=" + session.getEventId() + " already has a SUCCESS recovery — skipping to prevent double-count.");
            audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.RECOVERY_ATTEMPT_IDEMPOTENT_SKIP,
                    "CHECKOUT_SESSION", String.valueOf(session.getId()), "eventId=" + session.getEventId() + " already recovered — skipping");
            return persistSkip(null, session, null, batchId, trace, SkipReason.IDEMPOTENCY,
                    "eventId=" + session.getEventId() + " already has a SUCCESS recovery — skipped to prevent double-count");
        }

        // ── Cooldown guard ──
        if (session.getAbandonedAt() != null) {
            long minSince = Duration.between(session.getAbandonedAt(), LocalDateTime.now()).toMinutes();
            if (minSince < getCooldownMinutes()) {
                trace.add("COOLDOWN_SKIP", "Abandoned " + minSince + "min ago, cooldown is " + getCooldownMinutes() + "min — skipping.");
                audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.COOLDOWN_ACTIVE,
                        "CHECKOUT_SESSION", String.valueOf(session.getId()), "Cooldown active — abandoned " + minSince + "min ago");
                return persistSkip(null, session, null, batchId, trace, SkipReason.COOLDOWN,
                        "Abandoned " + minSince + "min ago, cooldown is " + getCooldownMinutes() + "min — skipped");
            }
        }

        UpliftSegment segment = upliftService.classify(session);
        trace.add("UPLIFT_CLASSIFY", "Segment: " + segment);

        DecisionResult result = decisionAgentService.decideWithMetaCheckout(session, trace);
        LlmDecision decision = result.decision();
        audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.AI_RECOMMENDATION_RECEIVED,
                "CHECKOUT_SESSION", String.valueOf(session.getId()),
                "Decision: " + decision.action() + " (confidence " + decision.confidence() + ", llmDriven=" + result.llmDriven() + ")");

        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setSourceType(SourceType.CHECKOUT);
        attempt.setCheckoutSession(session);
        attempt.setActionTaken(decision.action());
        attempt.setDiscountPercent(decision.discountPercent());
        attempt.setReasoning(decision.reasoning());
        attempt.setConfidence(decision.confidence());
        attempt.setLlmDriven(result.llmDriven());
        attempt.setBatchId(batchId);
        attempt.setExecutedAt(LocalDateTime.now());
        attempt.setRequiresHumanSignoff(result.requiresHumanSignoff());
        attempt.setSignoffReason(result.signoffReason());
        attempt.setDecisionTrace(trace);
        attempt.setCustomerMessage(decision.customerMessage());
        attempt.setUpliftSegment(segment);

        // Intelligence pass BEFORE execution mutates the session (see payment flow).
        intelligenceService.recordDecision(attempt, batchId);

        boolean success = executeCheckout(session, decision, attempt);
        trace.add("EXECUTION", "MockNotificationService.execute(Checkout#" + session.getId() + ") -> " + (success ? "SUCCESS" : "FAILED") + " | action=" + decision.action());
        applyCheckoutOutcome(session, attempt, success);
        auditExecution(batchId, "CHECKOUT_SESSION", String.valueOf(session.getId()), decision.action().name(), success, attempt.getAmountRecovered());

        return attempt;
    }

    private boolean executeCheckout(CheckoutSession session, LlmDecision decision, RecoveryAttempt attempt) {
        // Checkout actions are always customer-facing
        attempt.setCustomerNotified(true);
        return switch (decision.action()) {
            case CHECKOUT_REMINDER -> notificationService.sendCheckoutReminder(session);
            case SEND_PAYMENT_LINK -> {
                // Distinct executor: a direct payment link converts better than a generic reminder.
                boolean paid = notificationService.sendCheckoutPaymentLink(session);
                attempt.setInterventionCost(notificationService.costOf(false));
                yield paid;
            }
            case OFFER_DISCOUNT -> {
                int pct = decision.discountPercent() == null ? 10 : decision.discountPercent();
                boolean paid = notificationService.sendCheckoutDiscountOffer(session, pct);
                BigDecimal discountValue = session.getCartAmount()
                        .multiply(BigDecimal.valueOf(pct))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                attempt.setInterventionCost(notificationService.costOf(false).add(paid ? discountValue : BigDecimal.ZERO));
                yield paid;
            }
            case ESCALATE_TO_HUMAN, ABANDON -> false;
            default -> false;
        };
    }

    private void applyCheckoutOutcome(CheckoutSession session, RecoveryAttempt attempt, boolean success) {
        attempt.setOutcome(success ? AttemptOutcome.SUCCESS : AttemptOutcome.FAILED);
        if (success) {
            attempt.setAmountRecovered(session.getCartAmount());
            session.setStatus(CheckoutStatus.RECOVERED);
        } else {
            session.setReminderCount(session.getReminderCount() + 1);
            session.setStatus(session.getReminderCount() >= getMaxRetries() ? CheckoutStatus.LOST : CheckoutStatus.ABANDONED);
        }
        session.setAbandonedAt(LocalDateTime.now());
    }

    // ═══════════════════════════════════════════════════════════════
    // Process one overdue receivable (canonical — the caller persists)
    // ═══════════════════════════════════════════════════════════════

    private RecoveryAttempt processReceivableNoSave(Receivable receivable, String batchId, Set<String> alreadyRecoveredEvents) {
        DecisionTrace trace = new DecisionTrace();
        trace.add("DETECTION", "Receivable#" + receivable.getId() + " flagged as OVERDUE (amount=" + receivable.getInvoiceAmount() + ", daysOverdue=" + receivable.getDaysOverdue() + ")");

        // ── Control group: no intervention ──
        if (receivable.isControlGroup()) {
            trace.add("CONTROL_GROUP", "Entity is in control group — no agent intervention.");
            UpliftSegment segment = upliftService.classify(receivable);
            // Natural recovery: the same baseline conversion model a plain reminder would get
            // (shared deterministic Random — identical draws across runs for reproducible metrics).
            boolean naturalSuccess = notificationService.sendReceivableReminder(receivable);
            trace.add("NATURAL_RECOVERY", "Natural recovery for Receivable#" + receivable.getId() + " -> " + (naturalSuccess ? "SUCCESS" : "FAILED"));
            audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.RECOVERY_ATTEMPT_SKIPPED,
                    "RECEIVABLE", String.valueOf(receivable.getId()), "Control group — no agent intervention applied");
            if (naturalSuccess) {
                receivable.setStatus(ReceivableStatus.RECOVERED); // recovered naturally — leave the worklist
            }
            return buildControlAttempt(SourceType.RECEIVABLE, null, null, receivable, batchId, segment, naturalSuccess, trace,
                    "Control group: no agent intervention applied. Natural recovery " + (naturalSuccess ? "succeeded" : "failed") + ".");
        }

        // ── Idempotency guard ──
        if (receivable.getEventId() != null && alreadyRecoveredEvents.contains(receivable.getEventId())) {
            trace.add("IDEMPOTENCY_SKIP", "EventId=" + receivable.getEventId() + " already has a SUCCESS recovery — skipping to prevent double-count.");
            audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.RECOVERY_ATTEMPT_IDEMPOTENT_SKIP,
                    "RECEIVABLE", String.valueOf(receivable.getId()), "eventId=" + receivable.getEventId() + " already recovered — skipping");
            return persistSkip(null, null, receivable, batchId, trace, SkipReason.IDEMPOTENCY,
                    "eventId=" + receivable.getEventId() + " already has a SUCCESS recovery — skipped to prevent double-count");
        }

        // Promise-to-pay tracking: if promise was made and payment date has passed, mark as BROKEN
        if (receivable.getPromiseStatus() == Receivable.PromiseStatus.PROMISED
                && receivable.getPromisedPaymentDate() != null
                && receivable.getPromisedPaymentDate().isBefore(java.time.LocalDate.now())) {
            receivable.setPromiseStatus(Receivable.PromiseStatus.BROKEN);
            trace.add("PROMISE_CHECK", "Promise date " + receivable.getPromisedPaymentDate() + " has passed without payment — status set to BROKEN.");
        }

        UpliftSegment segment = upliftService.classify(receivable);
        trace.add("UPLIFT_CLASSIFY", "Segment: " + segment);

        DecisionResult result = decisionAgentService.decideWithMetaReceivable(receivable, trace);
        LlmDecision decision = result.decision();
        audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.AI_RECOMMENDATION_RECEIVED,
                "RECEIVABLE", String.valueOf(receivable.getId()),
                "Decision: " + decision.action() + " (confidence " + decision.confidence() + ", llmDriven=" + result.llmDriven() + ")");

        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setSourceType(SourceType.RECEIVABLE);
        attempt.setReceivable(receivable);
        attempt.setActionTaken(decision.action());
        attempt.setDiscountPercent(decision.discountPercent());
        attempt.setReasoning(decision.reasoning());
        attempt.setConfidence(decision.confidence());
        attempt.setLlmDriven(result.llmDriven());
        attempt.setBatchId(batchId);
        attempt.setExecutedAt(LocalDateTime.now());
        attempt.setRequiresHumanSignoff(result.requiresHumanSignoff());
        attempt.setSignoffReason(result.signoffReason());
        attempt.setDecisionTrace(trace);
        attempt.setCustomerMessage(decision.customerMessage());
        attempt.setUpliftSegment(segment);

        // Intelligence pass BEFORE execution mutates the receivable (see payment flow).
        intelligenceService.recordDecision(attempt, batchId);

        boolean success = executeReceivable(receivable, decision, attempt);
        trace.add("EXECUTION", "MockNotificationService.execute(Receivable#" + receivable.getId() + ") -> " + (success ? "SUCCESS" : "FAILED") + " | action=" + decision.action());
        applyReceivableOutcome(receivable, attempt, success);
        auditExecution(batchId, "RECEIVABLE", String.valueOf(receivable.getId()), decision.action().name(), success, attempt.getAmountRecovered());

        return attempt;
    }

    private boolean executeReceivable(Receivable receivable, LlmDecision decision, RecoveryAttempt attempt) {
        // Receivable actions are always customer-facing (B2B communication)
        attempt.setCustomerNotified(true);
        return switch (decision.action()) {
            case SEND_REMINDER -> notificationService.sendReceivableReminder(receivable);
            case OFFER_PAYMENT_PLAN -> {
                int installments = decision.discountPercent() != null ? decision.discountPercent() : 3;
                boolean paid = notificationService.offerPaymentPlan(receivable, installments);
                attempt.setInterventionCost(notificationService.costOf(false));
                yield paid;
            }
            case PROMISE_FOLLOWUP -> {
                // Follow up on a broken promise — send a firm reminder referencing the missed date
                boolean paid = notificationService.sendReceivableReminder(receivable);
                yield paid;
            }
            case ESCALATE_TO_HUMAN, ABANDON -> false;
            default -> false;
        };
    }

    private void applyReceivableOutcome(Receivable receivable, RecoveryAttempt attempt, boolean success) {
        attempt.setOutcome(success ? AttemptOutcome.SUCCESS : AttemptOutcome.FAILED);
        if (success) {
            attempt.setAmountRecovered(receivable.getInvoiceAmount());
            receivable.setStatus(ReceivableStatus.RECOVERED);
            // If a promise was outstanding and payment came through, mark it KEPT
            if (receivable.getPromiseStatus() == Receivable.PromiseStatus.PROMISED
                    || receivable.getPromiseStatus() == Receivable.PromiseStatus.BROKEN) {
                receivable.setPromiseStatus(Receivable.PromiseStatus.KEPT);
            }
        } else {
            receivable.setReminderCount(receivable.getReminderCount() + 1);
            if (receivable.getReminderCount() >= getMaxRetries()) {
                receivable.setStatus(ReceivableStatus.WRITTEN_OFF);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Shared helpers
    // ═══════════════════════════════════════════════════════════════

    /** Why an item was skipped — recorded honestly on the ledger instead of as a scheduled retry. */
    public enum SkipReason { IDEMPOTENCY, COOLDOWN }

    /** Build a skip attempt (cooldown/idempotency). The caller persists it like any other attempt. */
    private RecoveryAttempt persistSkip(Transaction tx, CheckoutSession session, Receivable receivable,
                                        String batchId, DecisionTrace trace, SkipReason reason, String detail) {
        RecoveryAttempt skip = new RecoveryAttempt();
        if (tx != null) { skip.setSourceType(SourceType.PAYMENT); skip.setTransaction(tx); }
        else if (session != null) { skip.setSourceType(SourceType.CHECKOUT); skip.setCheckoutSession(session); }
        else if (receivable != null) { skip.setSourceType(SourceType.RECEIVABLE); skip.setReceivable(receivable); }
        skip.setActionTaken(RecoveryAction.NO_ACTION);
        skip.setReasoning(reason + ": " + detail);
        skip.setConfidence(0.0);
        skip.setOutcome(AttemptOutcome.SKIPPED);
        skip.setExecutedAt(LocalDateTime.now());
        skip.setBatchId(batchId);
        skip.setDecisionTrace(trace);
        skip.setCustomerNotified(false);
        return skip;
    }

    /** Build a control-group attempt: NO_ACTION with natural (un-intervened) outcome, for uplift math. */
    private RecoveryAttempt buildControlAttempt(SourceType sourceType, Transaction tx, CheckoutSession session,
                                                Receivable receivable, String batchId, UpliftSegment segment,
                                                boolean naturalSuccess, DecisionTrace trace, String reasoning) {
        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setSourceType(sourceType);
        attempt.setTransaction(tx);
        attempt.setCheckoutSession(session);
        attempt.setReceivable(receivable);
        attempt.setActionTaken(RecoveryAction.NO_ACTION);
        attempt.setReasoning(reasoning);
        attempt.setConfidence(1.0);
        attempt.setOutcome(naturalSuccess ? AttemptOutcome.SUCCESS : AttemptOutcome.FAILED);
        attempt.setAmountRecovered(naturalSuccess ? controlAmount(tx, session, receivable) : BigDecimal.ZERO);
        attempt.setBatchId(batchId);
        attempt.setExecutedAt(LocalDateTime.now());
        attempt.setUpliftSegment(segment);
        attempt.setDecisionTrace(trace);
        attempt.setCustomerNotified(false);
        return attempt;
    }

    private BigDecimal controlAmount(Transaction tx, CheckoutSession session, Receivable receivable) {
        if (tx != null) return tx.getAmount();
        if (session != null) return session.getCartAmount();
        if (receivable != null) return receivable.getInvoiceAmount();
        return BigDecimal.ZERO;
    }

    /** Record an audit event for a batch (never throws — audit failures don't break the pipeline). */
    private void audit(String batchId, AuditEvent.Actor actor, AuditEvent.EventType type,
                       String entityType, String entityId, String reason) {
        auditService.recordForBatch(batchId, actor, type, entityType, entityId, reason);
    }

    private void auditExecution(String batchId, String entityType, String entityId,
                                String action, boolean success, BigDecimal amount) {
        audit(batchId, AuditEvent.Actor.SYSTEM, AuditEvent.EventType.RECOVERY_ATTEMPT_EXECUTED,
                entityType, entityId, "Action " + action + " executed");
        audit(batchId, AuditEvent.Actor.SYSTEM,
                success ? AuditEvent.EventType.RECOVERY_ATTEMPT_SUCCEEDED : AuditEvent.EventType.RECOVERY_ATTEMPT_FAILED,
                entityType, entityId, "Action " + action + (success ? " succeeded (recovered " + amount + ")" : " failed"));
    }
}
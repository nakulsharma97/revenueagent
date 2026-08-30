package com.razorpay.recovery.recovery;
import com.razorpay.recovery.recovery.RecoveryAttempt.AttemptOutcome;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.recovery.RecoveryAttempt.SourceType;

import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.checkout.CheckoutSession.CheckoutStatus;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The end-to-end loop for ALL three revenue sources:
 * [1] Detection -> [2] Diagnosis -> [3] Decision -> [4] Execution -> [5] Ledger
 *
 * Handles: payment failures (subscription dunning), checkout abandonment, B2B overdue receivables.
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
                                        BoundsConfig boundsConfig) {
        this.transactionRepository = transactionRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.receivableRepository = receivableRepository;
        this.attemptRepository = attemptRepository;
        this.decisionAgentService = decisionAgentService;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.boundsConfig = boundsConfig;
    }

    /**
     * Runs ALL three recovery pipelines and returns combined attempts.
     */
    @Transactional
    public List<RecoveryAttempt> runBatch() {
        if (!batchRunning.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Batch already running");
        }
        try {
            String batchId = UUID.randomUUID().toString();
            List<RecoveryAttempt> results = new java.util.ArrayList<>();
            results.addAll(runPaymentBatch(batchId));
            results.addAll(runCheckoutBatch(batchId));
            results.addAll(runReceivablesBatch(batchId));
            return results;
        } finally {
            batchRunning.set(false);
        }
    }

    /** Payment failure recovery — the original pipeline. */
    @Transactional
    public List<RecoveryAttempt> runPaymentBatch(String batchId) {
        List<Transaction> eligible = transactionRepository.findByStatusIn(
                List.of(TransactionStatus.AT_RISK, TransactionStatus.IN_RECOVERY));
        return eligible.stream().map(tx -> processPayment(tx, batchId)).toList();
    }

    /** Checkout abandonment recovery. */
    @Transactional
    public List<RecoveryAttempt> runCheckoutBatch(String batchId) {
        List<CheckoutSession> eligible = checkoutSessionRepository.findByStatusIn(
                List.of(CheckoutStatus.ABANDONED));
        return eligible.stream().map(session -> processCheckout(session, batchId)).toList();
    }

    /** B2B overdue receivables recovery. */
    @Transactional
    public List<RecoveryAttempt> runReceivablesBatch(String batchId) {
        List<Receivable> eligible = receivableRepository.findByStatusIn(
                List.of(ReceivableStatus.OVERDUE));
        return eligible.stream().map(r -> processReceivable(r, batchId)).toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // Process one payment failure
    // ═══════════════════════════════════════════════════════════════

    private RecoveryAttempt processPayment(Transaction tx, String batchId) {
        DecisionTrace trace = new DecisionTrace();
        trace.add("DETECTION", "Transaction TX#" + tx.getId() + " flagged as AT_RISK (amount=" + tx.getAmount() + ", failureReason=" + tx.getFailureReason() + ")");

        if (tx.getLastAttemptAt() != null) {
            long minSince = Duration.between(tx.getLastAttemptAt(), LocalDateTime.now()).toMinutes();
            if (minSince < getCooldownMinutes()) {
                trace.add("COOLDOWN_SKIP", "Last attempt " + minSince + "min ago, cooldown is " + getCooldownMinutes() + "min — skipping.");
                return persistSkip(tx, null, null, batchId, minSince, trace);
            }
        }

        DecisionResult result = decisionAgentService.decideWithMeta(tx, trace);
        LlmDecision decision = result.decision();

        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setSourceType(SourceType.PAYMENT);
        attempt.setTransaction(tx);
        attempt.setActionTaken(decision.action());
        attempt.setReasoning(decision.reasoning());
        attempt.setConfidence(decision.confidence());
        attempt.setLlmDriven(result.llmDriven());
        attempt.setBatchId(batchId);
        attempt.setExecutedAt(LocalDateTime.now());
        attempt.setRequiresHumanSignoff(result.requiresHumanSignoff());
        attempt.setSignoffReason(result.signoffReason());
        attempt.setDecisionTrace(trace);
        attempt.setCustomerMessage(decision.customerMessage());

        boolean success = executePayment(tx, decision, attempt);
        trace.add("EXECUTION", "MockPaymentGatewayService.attemptCharge(TX#" + tx.getId() + ") -> " + (success ? "SUCCESS" : "FAILED") + " | action=" + decision.action());
        applyPaymentOutcome(tx, attempt, success);

        transactionRepository.save(tx);
        return attemptRepository.save(attempt);
    }

    private boolean executePayment(Transaction tx, LlmDecision decision, RecoveryAttempt attempt) {
        return switch (decision.action()) {
            case RETRY_NOW, RETRY_SCHEDULED -> paymentGateway.attemptCharge(tx);
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

    private void applyPaymentOutcome(Transaction tx, RecoveryAttempt attempt, boolean success) {
        attempt.setOutcome(success ? AttemptOutcome.SUCCESS : AttemptOutcome.FAILED);
        if (success) {
            attempt.setAmountRecovered(tx.getAmount());
            tx.setStatus(TransactionStatus.RECOVERED);
        } else {
            tx.setRetryCount(tx.getRetryCount() + 1);
            tx.setStatus(tx.getRetryCount() >= getMaxRetries() ? TransactionStatus.LOST : TransactionStatus.IN_RECOVERY);
        }
        tx.setLastAttemptAt(LocalDateTime.now());
    }

    // ═══════════════════════════════════════════════════════════════
    // Process one checkout abandonment
    // ═══════════════════════════════════════════════════════════════

    private RecoveryAttempt processCheckout(CheckoutSession session, String batchId) {
        DecisionTrace trace = new DecisionTrace();
        trace.add("DETECTION", "CheckoutSession#" + session.getId() + " flagged as ABANDONED (amount=" + session.getCartAmount() + ", reason=" + session.getAbandonmentReason() + ")");

        if (session.getAbandonedAt() != null) {
            long minSince = Duration.between(session.getAbandonedAt(), LocalDateTime.now()).toMinutes();
            if (minSince < getCooldownMinutes()) {
                trace.add("COOLDOWN_SKIP", "Abandoned " + minSince + "min ago, cooldown is " + getCooldownMinutes() + "min — skipping.");
                return persistSkip(null, session, null, batchId, minSince, trace);
            }
        }

        DecisionResult result = decisionAgentService.decideWithMetaCheckout(session, trace);
        LlmDecision decision = result.decision();

        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setSourceType(SourceType.CHECKOUT);
        attempt.setCheckoutSession(session);
        attempt.setActionTaken(decision.action());
        attempt.setReasoning(decision.reasoning());
        attempt.setConfidence(decision.confidence());
        attempt.setLlmDriven(result.llmDriven());
        attempt.setBatchId(batchId);
        attempt.setExecutedAt(LocalDateTime.now());
        attempt.setRequiresHumanSignoff(result.requiresHumanSignoff());
        attempt.setSignoffReason(result.signoffReason());
        attempt.setDecisionTrace(trace);
        attempt.setCustomerMessage(decision.customerMessage());

        boolean success = executeCheckout(session, decision, attempt);
        trace.add("EXECUTION", "MockNotificationService.execute(Checkout#" + session.getId() + ") -> " + (success ? "SUCCESS" : "FAILED") + " | action=" + decision.action());
        applyCheckoutOutcome(session, attempt, success);

        checkoutSessionRepository.save(session);
        return attemptRepository.save(attempt);
    }

    private boolean executeCheckout(CheckoutSession session, LlmDecision decision, RecoveryAttempt attempt) {
        return switch (decision.action()) {
            case CHECKOUT_REMINDER -> notificationService.sendCheckoutReminder(session);
            case SEND_PAYMENT_LINK -> {
                boolean paid = notificationService.sendCheckoutReminder(session);
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
    // Process one overdue receivable
    // ═══════════════════════════════════════════════════════════════

    private RecoveryAttempt processReceivable(Receivable receivable, String batchId) {
        DecisionTrace trace = new DecisionTrace();
        trace.add("DETECTION", "Receivable#" + receivable.getId() + " flagged as OVERDUE (amount=" + receivable.getInvoiceAmount() + ", daysOverdue=" + receivable.getDaysOverdue() + ")");

        // Promise-to-pay tracking: if promise was made and payment date has passed, mark as BROKEN
        if (receivable.getPromiseStatus() == com.razorpay.recovery.receivable.Receivable.PromiseStatus.PROMISED
                && receivable.getPromisedPaymentDate() != null
                && receivable.getPromisedPaymentDate().isBefore(java.time.LocalDate.now())) {
            receivable.setPromiseStatus(com.razorpay.recovery.receivable.Receivable.PromiseStatus.BROKEN);
            trace.add("PROMISE_CHECK", "Promise date " + receivable.getPromisedPaymentDate() + " has passed without payment — status set to BROKEN.");
        }

        DecisionResult result = decisionAgentService.decideWithMetaReceivable(receivable, trace);
        LlmDecision decision = result.decision();

        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setSourceType(SourceType.RECEIVABLE);
        attempt.setReceivable(receivable);
        attempt.setActionTaken(decision.action());
        attempt.setReasoning(decision.reasoning());
        attempt.setConfidence(decision.confidence());
        attempt.setLlmDriven(result.llmDriven());
        attempt.setBatchId(batchId);
        attempt.setExecutedAt(LocalDateTime.now());
        attempt.setRequiresHumanSignoff(result.requiresHumanSignoff());
        attempt.setSignoffReason(result.signoffReason());
        attempt.setDecisionTrace(trace);
        attempt.setCustomerMessage(decision.customerMessage());

        boolean success = executeReceivable(receivable, decision, attempt);
        trace.add("EXECUTION", "MockNotificationService.execute(Receivable#" + receivable.getId() + ") -> " + (success ? "SUCCESS" : "FAILED") + " | action=" + decision.action());
        applyReceivableOutcome(receivable, attempt, success);

        receivableRepository.save(receivable);
        return attemptRepository.save(attempt);
    }

    private boolean executeReceivable(Receivable receivable, LlmDecision decision, RecoveryAttempt attempt) {
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
            if (receivable.getPromiseStatus() == com.razorpay.recovery.receivable.Receivable.PromiseStatus.PROMISED
                    || receivable.getPromiseStatus() == com.razorpay.recovery.receivable.Receivable.PromiseStatus.BROKEN) {
                receivable.setPromiseStatus(com.razorpay.recovery.receivable.Receivable.PromiseStatus.KEPT);
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

    /** Persist a cooldown-skip attempt so the ledger shows why an item was skipped. */
    private RecoveryAttempt persistSkip(Transaction tx, CheckoutSession session, Receivable receivable,
                                        String batchId, long minutesSinceLast, DecisionTrace trace) {
        RecoveryAttempt skip = new RecoveryAttempt();
        if (tx != null) { skip.setSourceType(SourceType.PAYMENT); skip.setTransaction(tx); }
        else if (session != null) { skip.setSourceType(SourceType.CHECKOUT); skip.setCheckoutSession(session); }
        else if (receivable != null) { skip.setSourceType(SourceType.RECEIVABLE); skip.setReceivable(receivable); }
        skip.setActionTaken(RecoveryAction.RETRY_SCHEDULED);
        skip.setReasoning("Skipped: cooldown not elapsed (" + minutesSinceLast + "min / " + getCooldownMinutes() + "min).");
        skip.setConfidence(0.0);
        skip.setOutcome(AttemptOutcome.PENDING);
        skip.setExecutedAt(LocalDateTime.now());
        skip.setBatchId(batchId);
        skip.setDecisionTrace(trace);
        return attemptRepository.save(skip);
    }
}

package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.audit.AuditEvent;
import com.razorpay.recovery.audit.AuditService;
import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.customer.Customer;
import com.razorpay.recovery.api.AttemptDto;
import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.checkout.CheckoutSessionRepository;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.receivable.ReceivableRepository;
import com.razorpay.recovery.recovery.RecoveryAttempt;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.recovery.RecoveryAttemptRepository;
import com.razorpay.recovery.recovery.RulesEngine;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Transactional coordinator for the intelligence layer. Called from inside the
 * recovery batch (per attempt, inside the batch's transaction) to:
 *
 * <ol>
 *   <li>persist the counterfactual simulation rows for the decision that just ran,</li>
 *   <li>enrich the attempt with the detected recovery state + fatigue score,</li>
 *   <li>persist anomaly findings (and route HIGH/CRITICAL ones to human review),</li>
 *   <li>route sign-off / low-confidence / escalation cases into the review queue,</li>
 *   <li>record the outcome row after execution (the ML training record).</li>
 * </ol>
 *
 * Every computation re-uses the same deterministic {@link NextBestActionEngine} that
 * made the decision, so the persisted counterfactuals always match what ran.
 */
@Service
public class RecoveryIntelligenceService {

    private final NextBestActionEngine engine;
    private final CounterfactualDecisionRepository counterfactualRepository;
    private final RecoveryOutcomeRepository outcomeRepository;
    private final HumanReviewCaseRepository reviewRepository;
    private final RecoveryAnomalyRepository anomalyRepository;
    private final AuditService auditService;
    private final BoundsConfig boundsConfig;
    private final RulesEngine rulesEngine;
    private final RecoveryAttemptRepository attemptRepository;
    private final RecoveryExperimentRepository experimentRepository;
    private final TransactionRepository transactionRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ReceivableRepository receivableRepository;
    private final OutcomeLearningService learningService;

    public RecoveryIntelligenceService(CounterfactualDecisionRepository counterfactualRepository,
                                       RecoveryOutcomeRepository outcomeRepository,
                                       HumanReviewCaseRepository reviewRepository,
                                       RecoveryAnomalyRepository anomalyRepository,
                                       AuditService auditService,
                                       BoundsConfig boundsConfig,
                                       RulesEngine rulesEngine,
                                       RecoveryAttemptRepository attemptRepository,
                                       RecoveryExperimentRepository experimentRepository,
                                       TransactionRepository transactionRepository,
                                       CheckoutSessionRepository checkoutSessionRepository,
                                       ReceivableRepository receivableRepository,
                                       NextBestActionEngine engine,
                                       OutcomeLearningService learningService) {
        this.engine = engine;
        this.learningService = learningService;
        this.counterfactualRepository = counterfactualRepository;
        this.outcomeRepository = outcomeRepository;
        this.reviewRepository = reviewRepository;
        this.anomalyRepository = anomalyRepository;
        this.auditService = auditService;
        this.boundsConfig = boundsConfig;
        this.rulesEngine = rulesEngine;
        this.attemptRepository = attemptRepository;
        this.experimentRepository = experimentRepository;
        this.transactionRepository = transactionRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.receivableRepository = receivableRepository;
    }

    /**
     * Build the canonical case for the attempt's source entity using the SAME
     * RulesEngine eligibility + segment bounds the decision agent used. Must be called
     * before the outcome mutates the entity (retryCount/status), i.e. right after the
     * decision and before execution.
     */
    private Optional<RecoveryCase> caseFor(RecoveryAttempt attempt) {
        if (attempt.getTransaction() != null) {
            Transaction tx = attempt.getTransaction();
            Customer.CustomerSegment seg = Customer.CustomerSegment.STANDARD;
            if (tx.getSubscription() != null && tx.getSubscription().getCustomer() != null
                    && tx.getSubscription().getCustomer().getCustomerSegment() != null) {
                seg = tx.getSubscription().getCustomer().getCustomerSegment();
            }
            var bounds = boundsConfig.boundsFor(seg);
            return Optional.of(RecoveryCase.fromPayment(tx, seg,
                    rulesEngine.eligibleActions(tx, seg), bounds.maxRetries(), bounds.maxDiscountPercent()));
        }
        if (attempt.getCheckoutSession() != null) {
            var session = attempt.getCheckoutSession();
            return Optional.of(RecoveryCase.fromCheckout(session, rulesEngine.eligibleActions(session),
                    boundsConfig.getMaxRetries(), boundsConfig.getMaxDiscountPercent()));
        }
        if (attempt.getReceivable() != null) {
            var receivable = attempt.getReceivable();
            return Optional.of(RecoveryCase.fromReceivable(receivable, rulesEngine.eligibleActions(receivable),
                    boundsConfig.getMaxRetries(), boundsConfig.getMaxDiscountPercent()));
        }
        return Optional.empty();
    }

    private String sourceName(RecoveryAttempt attempt) {
        return attempt.getSourceType() == null ? null : attempt.getSourceType().name();
    }

    private Long entityId(RecoveryAttempt attempt) {
        if (attempt.getTransaction() != null) return attempt.getTransaction().getId();
        if (attempt.getCheckoutSession() != null) return attempt.getCheckoutSession().getId();
        if (attempt.getReceivable() != null) return attempt.getReceivable().getId();
        return null;
    }

    private BigDecimal amountOf(RecoveryAttempt attempt) {
        if (attempt.getTransaction() != null) return attempt.getTransaction().getAmount();
        if (attempt.getCheckoutSession() != null) return attempt.getCheckoutSession().getCartAmount();
        if (attempt.getReceivable() != null) return attempt.getReceivable().getInvoiceAmount();
        return BigDecimal.ZERO;
    }

    /**
     * Called per attempt before it is persisted: simulates & persists the decision's
     * counterfactuals, enriches the attempt, detects anomalies and routes review cases.
     */
    @Transactional
    public void recordDecision(RecoveryAttempt attempt, String batchId) {
        // Control-group / cooldown / idempotency skips never went through the engine.
        if (attempt.getOutcome() == RecoveryAttempt.AttemptOutcome.SKIPPED
                || attempt.getActionTaken() == null
                || attempt.getActionTaken() == RecoveryAction.NO_ACTION) {
            return;
        }
        Optional<RecoveryCase> opt = caseFor(attempt);
        if (opt.isEmpty()) return;
        RecoveryCase full = opt.get();
        IntelligenceDecision decision = engine.decide(full, boundsConfig.getLanguage());

        // ── Enrich the attempt for the ledger UI ──
        attempt.setRecoveryState(decision.recoveryState());
        attempt.setFatigueScore(decision.fatigueScore());

        // ── Persist counterfactual rows ──
        String sourceType = sourceName(attempt);
        Long entityId = entityId(attempt);
        for (ActionEvaluation e : decision.alternatives()) {
            boolean selected = decision.chosen() != null
                    && e.action() == decision.chosen().action()
                    && java.util.Objects.equals(e.discountPercent(), decision.chosen().discountPercent());
            counterfactualRepository.save(CounterfactualDecision.from(e, sourceType, entityId, batchId, amountOf(attempt), selected));
        }

        // ── Anomalies ──
        boolean criticalAnomaly = false;
        for (AnomalyDetectionService.Finding f : engine.anomalies(full)) {
            boolean alreadyOpen = anomalyRepository
                    .findFirstByTypeAndSourceTypeAndSourceEntityIdAndStatus(f.type(), sourceType, entityId, RecoveryAnomaly.Status.OPEN)
                    .isPresent();
            if (alreadyOpen) continue;
            RecoveryAnomaly anomaly = RecoveryAnomaly.from(f, sourceType, entityId);
            anomalyRepository.save(anomaly);
            auditService.recordForBatch(batchId, AuditEvent.Actor.POLICY_ENGINE, AuditEvent.EventType.ANOMALY_DETECTED,
                    sourceType, String.valueOf(entityId), "Anomaly " + f.type() + " (" + f.severity() + "): " + f.description());
            if (f.severity() == AnomalyDetectionService.Severity.HIGH
                    || f.severity() == AnomalyDetectionService.Severity.CRITICAL) {
                criticalAnomaly = true;
            }
        }

        // ── Human review routing ──
        boolean needsReview = attempt.isRequiresHumanSignoff()
                || decision.automationPolicy() == DecisionConfidenceService.Policy.HUMAN_REVIEW
                || decision.chosen() != null && decision.chosen().action() == RecoveryAction.ESCALATE_TO_HUMAN
                || criticalAnomaly;
        if (needsReview) {
            HumanReviewCase existing = reviewRepository
                    .findFirstBySourceTypeAndSourceEntityIdAndStatus(sourceType, entityId, HumanReviewCase.Status.PENDING)
                    .orElse(null);
            if (existing == null) {
                HumanReviewCase.Priority priority = criticalAnomaly
                        ? HumanReviewCase.Priority.CRITICAL : HumanReviewCase.Priority.NORMAL;
                String reason = reviewReason(attempt, decision, criticalAnomaly);
                HumanReviewCase reviewCase = HumanReviewCase.pending(sourceType, entityId, attempt.getId(),
                        amountOf(attempt), decision.chosen() == null ? attempt.getActionTaken() : decision.chosen().action(),
                        decision.chosen() == null ? null : decision.chosen().discountPercent(),
                        decision.confidence(), reason, priority);
                reviewRepository.save(reviewCase);
                auditService.recordForBatch(batchId, AuditEvent.Actor.POLICY_ENGINE, AuditEvent.EventType.REVIEW_CASE_CREATED,
                        sourceType, String.valueOf(entityId), "Review case #" + reviewCase.getId() + ": " + reason);
            }
        }
    }

    private String reviewReason(RecoveryAttempt attempt, IntelligenceDecision decision, boolean criticalAnomaly) {
        StringBuilder sb = new StringBuilder();
        if (attempt.isRequiresHumanSignoff()) {
            sb.append("Human sign-off required: ").append(attempt.getSignoffReason() == null ? "policy rule" : attempt.getSignoffReason()).append(". ");
        }
        if (decision.automationPolicy() == DecisionConfidenceService.Policy.HUMAN_REVIEW) {
            sb.append("Engine confidence ").append(String.format("%.0f", decision.confidence() * 100))
              .append("% is below the 60% auto-execution floor. ");
        }
        if (decision.chosen() != null && decision.chosen().action() == RecoveryAction.ESCALATE_TO_HUMAN) {
            sb.append("Engine selected ESCALATE_TO_HUMAN as the next best action. ");
        }
        if (criticalAnomaly) {
            sb.append("A HIGH/CRITICAL anomaly was detected on this case. ");
        }
        return sb.toString().trim();
    }

    /** Record the outcome row after execution (attempt must already be persisted). */
    @Transactional
    public void recordOutcome(RecoveryAttempt attempt) {
        if (attempt.getActionTaken() == null || attempt.getActionTaken() == RecoveryAction.NO_ACTION) {
            return;
        }
        if (attempt.getOutcome() == RecoveryAttempt.AttemptOutcome.SKIPPED) {
            return;
        }
        RecoveryOutcome outcome = RecoveryOutcome.from(attempt);
        outcomeRepository.save(outcome);
        auditService.recordForBatch(attempt.getBatchId(), AuditEvent.Actor.SYSTEM, AuditEvent.EventType.OUTCOME_RECORDED,
                sourceName(attempt), String.valueOf(entityId(attempt)),
                "Outcome recorded for action " + attempt.getActionTaken() + " (success=" + outcome.isSuccess() + ")");
    }

    /** Aggregate action performance from persisted outcomes. */
    @Transactional(readOnly = true)
    public List<OutcomeLearningService.ActionPerformance> actionPerformance() {
        return learningService.rankByNetValue(outcomeRepository.findAll());
    }

    /** Latest counterfactual rows for one entity (sourceType = PAYMENT|CHECKOUT|RECEIVABLE). */
    @Transactional(readOnly = true)
    public List<CounterfactualDecision> counterfactualsFor(String sourceType, Long entityId) {
        return counterfactualRepository.findBySourceTypeAndSourceEntityIdOrderByCreatedAtDesc(sourceType, entityId);
    }

    /** Latest counterfactual rows across all entities (for the Decisions feed). */
    @Transactional(readOnly = true)
    public List<CounterfactualDecision> recentCounterfactuals(int limit) {
        return counterfactualRepository.findTop100ByOrderByCreatedAtDesc().stream().limit(limit).toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // Human Review Queue
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<HumanReviewCase> reviewQueue(String status) {
        if (status == null || status.isBlank() || "PENDING".equalsIgnoreCase(status)) {
            return reviewRepository.findByStatusOrderByPriorityDescCreatedAtAsc(HumanReviewCase.Status.PENDING);
        }
        return reviewRepository.findByStatusOrderByCreatedAtAsc(HumanReviewCase.Status.valueOf(status.toUpperCase()));
    }

    @Transactional
    public HumanReviewCase resolveReview(Long caseId, HumanReviewCase.Status status,
                                         RecoveryAction humanAction, String overrideReason) {
        if (status == HumanReviewCase.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolution status must be APPROVED, OVERRIDDEN or REJECTED");
        }
        HumanReviewCase c = reviewRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review case not found"));
        if (c.getStatus() != HumanReviewCase.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review case " + caseId + " is already resolved");
        }
        c.setStatus(status);
        c.setHumanDecision(status == HumanReviewCase.Status.APPROVED ? c.getAiRecommendation() : humanAction);
        c.setOverrideReason(overrideReason);
        c.setResolvedAt(LocalDateTime.now());
        reviewRepository.save(c);

        // Mirror the outcome onto the linked recovery attempt's sign-off state.
        if (c.getAttemptId() != null) {
            attemptRepository.findById(c.getAttemptId()).ifPresent(attempt -> {
                boolean approved = status == HumanReviewCase.Status.APPROVED || status == HumanReviewCase.Status.OVERRIDDEN;
                attempt.setSignoffStatus(approved ? RecoveryAttempt.SignoffStatus.APPROVED : RecoveryAttempt.SignoffStatus.REJECTED);
                attempt.setSignoffResolvedAt(LocalDateTime.now());
                if (status == HumanReviewCase.Status.OVERRIDDEN) {
                    // Provenance: the ledger now records a human decision, not the machine's.
                    attempt.setDecisionSource(RecoveryAttempt.DecisionSource.MANUAL_HUMAN_OVERRIDE);
                    attempt.setFallbackReason("Human overrode the engine's " + attempt.getActionTaken()
                            + " with " + (humanAction == null ? "no action" : humanAction)
                            + (overrideReason == null || overrideReason.isBlank() ? "" : ": " + overrideReason));
                }
                attemptRepository.save(attempt);
            });
        }
        auditService.record(AuditEvent.Actor.HUMAN_USER, AuditEvent.EventType.REVIEW_CASE_RESOLVED,
                "HUMAN_REVIEW_CASE", String.valueOf(caseId),
                c.getAiRecommendation() == null ? null : c.getAiRecommendation().name(),
                status.name(),
                (status == HumanReviewCase.Status.APPROVED ? "Approved AI recommendation "
                        : status == HumanReviewCase.Status.OVERRIDDEN ? "Overrode AI recommendation with " + (humanAction == null ? "?" : humanAction)
                        : "Rejected AI recommendation") + (overrideReason == null || overrideReason.isBlank() ? "" : ": " + overrideReason));
        return c;
    }

    // ═══════════════════════════════════════════════════════════════
    // Anomalies & Experiments
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<RecoveryAnomaly> anomalies(String status) {
        if (status == null || status.isBlank() || "OPEN".equalsIgnoreCase(status)) {
            return anomalyRepository.findByStatusOrderByCreatedAtDesc(RecoveryAnomaly.Status.OPEN);
        }
        return anomalyRepository.findByStatusOrderByCreatedAtDesc(RecoveryAnomaly.Status.valueOf(status.toUpperCase()));
    }

    @Transactional(readOnly = true)
    public List<RecoveryExperiment> experiments() {
        return experimentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public RecoveryExperiment createExperiment(String name, String description, double controlPercentage,
                                               String treatmentPolicy, String targetSegment,
                                               String targetCustomerSegment, java.time.LocalDate endDate) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Experiment name is required");
        }
        RecoveryExperiment e = new RecoveryExperiment();
        e.setName(name.trim());
        e.setDescription(description);
        e.setControlPercentage(Math.max(0, Math.min(50, controlPercentage)));
        e.setTreatmentPolicy(treatmentPolicy);
        e.setTargetSegment(targetSegment == null || targetSegment.isBlank() ? "ALL" : targetSegment);
        e.setTargetCustomerSegment(targetCustomerSegment == null || targetCustomerSegment.isBlank() ? "ALL" : targetCustomerSegment);
        e.setStatus(RecoveryExperiment.Status.ACTIVE);
        e.setStartDate(java.time.LocalDate.now());
        e.setEndDate(endDate);
        e.setCreatedAt(LocalDateTime.now());
        return experimentRepository.save(e);
    }

    // ═══════════════════════════════════════════════════════════════
    // Command Center + Timeline
    // ═══════════════════════════════════════════════════════════════

    /** Aggregate numbers for the Command Center header. */
    @Transactional(readOnly = true)
    public CommandCenterSummary commandCenter() {
        List<RecoveryAttempt> attempts = attemptRepository.findAll();
        BigDecimal recovered = attempts.stream()
                .filter(a -> a.getOutcome() == RecoveryAttempt.AttemptOutcome.SUCCESS)
                .map(a -> a.getAmountRecovered() == null ? BigDecimal.ZERO : a.getAmountRecovered())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costs = attempts.stream()
                .map(a -> a.getInterventionCost() == null ? BigDecimal.ZERO : a.getInterventionCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
        long decisionsToday = attempts.stream()
                .filter(a -> a.getExecutedAt() != null && a.getExecutedAt().isAfter(todayStart)).count();
        long fatigueAlerts = attempts.stream()
                .filter(a -> a.getFatigueScore() >= 0.6).count();

        BigDecimal atRisk = sum(transactionRepository.findByStatusIn(List.of(Transaction.TransactionStatus.AT_RISK,
                        Transaction.TransactionStatus.IN_RECOVERY)).stream().map(Transaction::getAmount).toList())
                .add(sum(checkoutSessionRepository.findByStatusIn(List.of(CheckoutSession.CheckoutStatus.ABANDONED))
                        .stream().map(CheckoutSession::getCartAmount).toList()))
                .add(sum(receivableRepository.findByStatusIn(List.of(Receivable.ReceivableStatus.OVERDUE))
                        .stream().map(Receivable::getInvoiceAmount).toList()));

        long pendingEscalations = reviewRepository.findByStatusOrderByCreatedAtAsc(HumanReviewCase.Status.PENDING).size();
        long openAnomalies = anomalyRepository.findByStatusOrderByCreatedAtDesc(RecoveryAnomaly.Status.OPEN).size();
        long outcomes = outcomeRepository.count();
        long activeExperiments = experimentRepository.findByStatusOrderByCreatedAtDesc(RecoveryExperiment.Status.ACTIVE).size();

        return new CommandCenterSummary(atRisk, recovered, costs, recovered.subtract(costs), attempts.size(),
                decisionsToday, pendingEscalations, openAnomalies, outcomes, activeExperiments, fatigueAlerts);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Ordered attempts for one entity = the Recovery Timeline of that case. */
    @Transactional(readOnly = true)
    public List<AttemptDto> timeline(String sourceType, Long entityId) {
        List<RecoveryAttempt> attempts;
        if (entityId == null) return List.of();
        attempts = switch (sourceType == null ? "" : sourceType.toUpperCase()) {
            case "PAYMENT" -> attemptRepository.findByTransactionIdOrderByExecutedAtAsc(entityId);
            case "CHECKOUT" -> attemptRepository.findByCheckoutSessionIdOrderByExecutedAtAsc(entityId);
            case "RECEIVABLE" -> attemptRepository.findByReceivableIdOrderByExecutedAtAsc(entityId);
            default -> List.of();
        };
        return attempts.stream().map(AttemptDto::from).toList();
    }

    /** Command Center aggregates. */
    public record CommandCenterSummary(BigDecimal revenueAtRisk, BigDecimal recoveredRevenue, BigDecimal interventionCost,
                                       BigDecimal netRecovered, long activeCases, long aiDecisionsToday,
                                       long pendingHumanEscalations, long openAnomalies, long outcomesRecorded,
                                       long activeExperiments, long fatigueAlerts) {}
}

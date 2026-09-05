package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.customer.Customer;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.recovery.RulesEngine;
import com.razorpay.recovery.subscription.Subscription;
import com.razorpay.recovery.transaction.Transaction;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recovery Intelligence API — Recovery Simulator, Counterfactual Decisions,
 * Human Review Queue, Anomalies, Experiments, Action Performance Lab,
 * Command Center and per-case Recovery Timelines.
 */
@RestController
@RequestMapping("/api/intelligence")
public class IntelligenceController {

    private final RecoveryIntelligenceService intelligence;
    private final RulesEngine rulesEngine;
    private final BoundsConfig boundsConfig;
    private final NextBestActionEngine engine;
    private final OutcomeMemoryService outcomeMemory;

    public IntelligenceController(RecoveryIntelligenceService intelligence, RulesEngine rulesEngine,
                                  BoundsConfig boundsConfig, NextBestActionEngine engine,
                                  OutcomeMemoryService outcomeMemory) {
        this.intelligence = intelligence;
        this.rulesEngine = rulesEngine;
        this.boundsConfig = boundsConfig;
        this.engine = engine;
        this.outcomeMemory = outcomeMemory;
    }

    // ═══════════════════════════════════════════════════════════════
    // Recovery Simulator
    // ═══════════════════════════════════════════════════════════════

    /** Request shape for the ad-hoc Recovery Simulator. */
    public record SimRequest(
            String sourceType,           // PAYMENT | CHECKOUT | RECEIVABLE
            BigDecimal amount,
            String failureReason,        // Transaction.FailureReason / CheckoutSession.AbandonmentReason
            int retryCount,
            int reminderCount,
            int daysOverdue,
            boolean promiseBroken,
            Double reliability,
            boolean highValue,
            String customerSegment,      // STANDARD | HIGH_VALUE
            String language              // en | hinglish
    ) {}

    @PostMapping("/simulate")
    public Map<String, Object> simulate(@RequestBody SimRequest req) {
        RecoveryCase c = buildSimulationCase(req);
        IntelligenceDecision d = engine.decide(c, req.language() == null ? "en" : req.language());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", c.label());
        out.put("sourceType", c.sourceType());
        out.put("amount", c.amount());
        out.put("recoveryState", d.recoveryState().name());
        out.put("fatigueBand", d.fatigueBand().name());
        out.put("fatigueScore", d.fatigueScore());
        out.put("baselineProbability", d.baselineProbability());
        out.put("confidence", d.confidence());
        out.put("automationPolicy", d.automationPolicy().name());
        out.put("reasoning", d.reasoning());
        out.put("customerMessage", d.customerMessage());
        out.put("topFactors", d.topFactors());
        out.put("eligible", c.eligible() == null ? List.of() : c.eligible().stream().map(Enum::name).toList());
        out.put("alternatives", d.alternatives().stream().map(IntelligenceController::evalView).toList());
        out.put("chosen", d.chosen() == null ? null : evalView(d.chosen()));
        return out;
    }

    private static Map<String, Object> evalView(ActionEvaluation e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", e.action().name());
        m.put("displayName", e.displayName());
        m.put("discountPercent", e.discountPercent());
        m.put("successProbability", e.successProbability());
        m.put("baselineProbability", e.baselineProbability());
        m.put("incrementalLift", e.incrementalLift());
        m.put("expectedNetValue", e.expectedNetValue());
        m.put("incrementalNetValue", e.incrementalNetValue());
        m.put("expectedRecovered", e.expectedRecovered());
        m.put("interventionCost", e.interventionCost());
        m.put("discountCost", e.discountCost());
        m.put("riskPenalty", e.riskPenalty());
        m.put("riskScore", e.riskScore());
        m.put("reasoning", e.reasoning());
        return m;
    }

    private RecoveryCase buildSimulationCase(SimRequest req) {
        if (req.sourceType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceType is required");
        }
        double reliability = req.reliability() == null ? 0.5 : Math.max(0.0, Math.min(1.0, req.reliability()));
        BigDecimal amount = req.amount() == null ? BigDecimal.ZERO : req.amount();
        try {
            switch (req.sourceType().toUpperCase()) {
                case "PAYMENT": {
                    Customer.CustomerSegment seg = req.highValue()
                            || "HIGH_VALUE".equalsIgnoreCase(req.customerSegment())
                            ? Customer.CustomerSegment.HIGH_VALUE : Customer.CustomerSegment.STANDARD;
                    BoundsConfig.SegmentBounds bounds = boundsConfig.boundsFor(seg);
                    Transaction tx = new Transaction();
                    tx.setAmount(amount);
                    tx.setRetryCount(req.retryCount());
                    if (req.failureReason() != null && !req.failureReason().isBlank()) {
                        tx.setFailureReason(Transaction.FailureReason.valueOf(req.failureReason()));
                    }
                    tx.setStatus(req.retryCount() > 0 ? Transaction.TransactionStatus.IN_RECOVERY : Transaction.TransactionStatus.AT_RISK);
                    // RecoveryCase.fromPayment reads the customer's reliability from the
                    // subscription → customer chain, so a bare Transaction would silently drop
                    // the simulator's reliability input (always defaulting to 0.5). Attach a
                    // transient chain when the caller supplied a score.
                    if (req.reliability() != null) {
                        Customer customer = new Customer();
                        customer.setPaymentReliabilityScore(reliability);
                        customer.setCustomerSegment(seg);
                        Subscription subscription = new Subscription();
                        subscription.setCustomer(customer);
                        tx.setSubscription(subscription);
                    }
                    List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx, seg);
                    return RecoveryCase.fromPayment(tx, seg, eligible, bounds.maxRetries(), bounds.maxDiscountPercent());
                }
                case "CHECKOUT": {
                    CheckoutSession session = new CheckoutSession();
                    session.setCartAmount(amount);
                    session.setReminderCount(req.reminderCount());
                    session.setStatus(CheckoutSession.CheckoutStatus.ABANDONED);
                    if (req.failureReason() != null && !req.failureReason().isBlank()) {
                        session.setAbandonmentReason(CheckoutSession.AbandonmentReason.valueOf(req.failureReason()));
                    }
                    List<RecoveryAction> eligible = rulesEngine.eligibleActions(session);
                    return RecoveryCase.fromCheckout(session, eligible,
                            boundsConfig.getMaxRetries(), boundsConfig.getMaxDiscountPercent());
                }
                case "RECEIVABLE": {
                    Receivable receivable = new Receivable();
                    receivable.setInvoiceAmount(amount);
                    receivable.setDaysOverdue(req.daysOverdue());
                    receivable.setReminderCount(req.reminderCount());
                    receivable.setStatus(Receivable.ReceivableStatus.OVERDUE);
                    if (req.promiseBroken()) {
                        receivable.setPromiseStatus(Receivable.PromiseStatus.BROKEN);
                    }
                    List<RecoveryAction> eligible = rulesEngine.eligibleActions(receivable);
                    RecoveryCase rc = RecoveryCase.fromReceivable(receivable, eligible,
                            boundsConfig.getMaxRetries(), boundsConfig.getMaxDiscountPercent());
                    // fromReceivable hardcodes 0.5 reliability — honour the simulator input.
                    return req.reliability() == null ? rc : rc.withReliability(reliability);
                }
                default:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown sourceType " + req.sourceType());
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad simulation parameter: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Command Center + counterfactuals + timelines
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/command-center")
    public RecoveryIntelligenceService.CommandCenterSummary commandCenter() {
        return intelligence.commandCenter();
    }

    /** Counterfactual rows for one case (sourceType=PAYMENT&id=12). */
    @GetMapping("/counterfactuals")
    public List<CounterfactualDecision> counterfactuals(@RequestParam(required = false) String sourceType,
                                                        @RequestParam(required = false) Long id) {
        if (sourceType != null && id != null) {
            return intelligence.counterfactualsFor(sourceType.toUpperCase(), id);
        }
        return intelligence.recentCounterfactuals(100);
    }

    @GetMapping("/timeline")
    public List<com.razorpay.recovery.api.AttemptDto> timeline(@RequestParam String sourceType,
                                                               @RequestParam Long id) {
        return intelligence.timeline(sourceType, id);
    }

    // ═══════════════════════════════════════════════════════════════
    // Human Review Queue
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/review")
    public List<HumanReviewCase> reviewQueue(@RequestParam(required = false, defaultValue = "PENDING") String status) {
        return intelligence.reviewQueue(status);
    }

    public record ReviewResolutionRequest(String decision, String action, String reason) {}

    @PostMapping("/review/{id}/resolve")
    public HumanReviewCase resolve(@PathVariable Long id, @RequestBody ReviewResolutionRequest req) {
        HumanReviewCase.Status status;
        try {
            status = HumanReviewCase.Status.valueOf(req.decision() == null ? "" : req.decision().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be APPROVED, OVERRIDDEN or REJECTED");
        }
        RecoveryAction humanAction = null;
        if (req.action() != null && !req.action().isBlank()) {
            try {
                humanAction = RecoveryAction.valueOf(req.action());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action " + req.action());
            }
        }
        return intelligence.resolveReview(id, status, humanAction, req.reason());
    }

    // ═══════════════════════════════════════════════════════════════
    // Anomalies
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/anomalies")
    public List<RecoveryAnomaly> anomalies(@RequestParam(required = false, defaultValue = "OPEN") String status) {
        return intelligence.anomalies(status);
    }

    // ═══════════════════════════════════════════════════════════════
    // Experiments
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/experiments")
    public List<RecoveryExperiment> experiments() {
        return intelligence.experiments();
    }

    public record ExperimentRequest(String name, String description, Double controlPercentage,
                                    String treatmentPolicy, String targetSegment,
                                    String targetCustomerSegment, String endDate) {}

    @PostMapping("/experiments")
    public RecoveryExperiment createExperiment(@RequestBody ExperimentRequest req) {
        java.time.LocalDate end = null;
        if (req.endDate() != null && !req.endDate().isBlank()) {
            try {
                end = java.time.LocalDate.parse(req.endDate());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be ISO yyyy-MM-dd");
            }
        }
        return intelligence.createExperiment(req.name(), req.description(),
                req.controlPercentage() == null ? 15.0 : req.controlPercentage(),
                req.treatmentPolicy(), req.targetSegment(), req.targetCustomerSegment(), end);
    }

    // ═══════════════════════════════════════════════════════════════
    // Action Performance Lab
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/action-performance")
    public List<OutcomeLearningService.ActionPerformance> actionPerformance() {
        return intelligence.actionPerformance();
    }

    // ═══════════════════════════════════════════════════════════════
    // Outcome Memory — context × segment × action priors from history
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/outcome-memory")
    public List<OutcomeMemoryService.MemoryRow> outcomeMemory() {
        return outcomeMemory.memory();
    }
}

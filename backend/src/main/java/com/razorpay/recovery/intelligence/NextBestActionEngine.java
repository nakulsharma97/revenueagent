package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Next-Best-Action engine — the heart of the Recovery Intelligence layer.
 *
 * <p>For every case it answers: <em>"for this customer, right now, which single action
 * creates the highest <b>incremental</b> net revenue?"</em>. The pipeline is:</p>
 *
 * <pre>
 *   feature vector ─▶ fatigue & state ─▶ simulate EVERY eligible action
 *        ─▶ filter fatigue/policy constraints ─▶ rank by incremental net value
 *        ─▶ confidence band ─▶ automation policy ─▶ NEXT BEST ACTION
 * </pre>
 *
 * Deterministic and pure: identical input always yields the identical decision, and no
 * random number is involved, so REST, SSE, startup and scheduler runs agree.
 *
 * <p>The engine is a Spring-managed singleton; all of its collaborators are injected so
 * every production caller (DecisionAgentService, RecoveryIntelligenceService,
 * IntelligenceController) shares ONE engine instance and can never drift into different
 * decision rules. Plain unit tests may construct it directly with real collaborators.</p>
 */
@Service
public class NextBestActionEngine {

    /** Version stamped onto every attempt the engine decided — proves engine provenance. */
    public static final String ENGINE_VERSION = "RECOVERY_INTELLIGENCE_V1";

    private final UpliftScoringService scorer;
    private final RecoveryFatigueService fatigueService;
    private final CustomerStateService stateService;
    private final DecisionConfidenceService confidenceService;
    private final RecoveryValueOptimizer optimizer;
    private final AnomalyDetectionService anomalyService;

    public NextBestActionEngine(UpliftScoringService scorer,
                                RecoveryFatigueService fatigueService,
                                CustomerStateService stateService,
                                DecisionConfidenceService confidenceService,
                                RecoveryValueOptimizer optimizer,
                                AnomalyDetectionService anomalyService) {
        this.scorer = scorer;
        this.fatigueService = fatigueService;
        this.stateService = stateService;
        this.confidenceService = confidenceService;
        this.optimizer = optimizer;
        this.anomalyService = anomalyService;
    }

    /** Discount tiers evaluated for OFFER_DISCOUNT (capped by the segment ceiling). */
    private static final int[] DISCOUNT_TIERS = {5, 10, 15, 20, 25};

    /** Default language for customer messages. */
    public IntelligenceDecision decide(RecoveryCase c) {
        return decide(c, "en");
    }

    /** Evaluate every eligible action and select the best valid one. */
    public IntelligenceDecision decide(RecoveryCase c, String language) {
        List<RecoveryAction> eligible = c.eligible() == null ? List.of() : new ArrayList<>(c.eligible());

        double fatigue = fatigueService.score(c);
        RecoveryCase fatigueCase = c.withFatigue(fatigue);
        FatigueBand fatigueBand = fatigueService.band(fatigue);
        RecoveryState state = stateService.detect(fatigueCase, fatigue);

        // ── Build the candidate evaluation set (counterfactual simulation) ──
        List<ActionEvaluation> candidates = simulateAll(fatigueCase);

        // ── Constraint pass: fatigue policy (stricter than RulesEngine) ──
        candidates.removeIf(e -> fatigueService.blockedByFatigue(fatigueCase, fatigue, e.action()));

        // ── Financial selection: highest incremental net value ──
        // Delegated to RecoveryValueOptimizer so the engine and any future caller share
        // ONE selection rule (net value, ties → lower risk) instead of duplicating it.
        List<ActionEvaluation> ranked = rank(fatigueCase, candidates);
        ActionEvaluation chosen = ranked.isEmpty() ? null : optimizer.bestByIncrementalNetValue(ranked);

        List<String> topFactors = factors(fatigueCase, fatigue, fatigueBand, state, chosen);

        // ── Confidence + automation policy ──
        if (chosen == null) {
            return new IntelligenceDecision(state, fatigueBand, fatigue, baselineOf(fatigueCase), null,
                    ranked, topFactors, 0.30, DecisionConfidenceService.Policy.HUMAN_REVIEW,
                    "No eligible action remains for this case — no intervention.",
                    null);
        }

        DecisionConfidenceService.Assessment assessment = confidenceService.assess(fatigueCase, chosen, fatigue);
        double confidence = assessment.score();
        DecisionConfidenceService.Policy policy = assessment.policy();

        String reasoning = buildReasoning(fatigueCase, fatigueBand, state, chosen, ranked);

        // Low confidence → the machine must not act alone: route to a human instead.
        if (policy == DecisionConfidenceService.Policy.HUMAN_REVIEW) {
            RecoveryAction human = eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN)
                    ? RecoveryAction.ESCALATE_TO_HUMAN : RecoveryAction.ABANDON;
            ActionEvaluation esc = evaluator(fatigueCase).evaluate(fatigueCase, human, null, baselineOf(fatigueCase));
            reasoning = "Confidence " + String.format("%.0f", confidence * 100)
                    + "% is below the 60% auto-execution floor — routed to human review. " + reasoning;
            chosen = esc;
        }

        return new IntelligenceDecision(state, fatigueBand, fatigue, baselineOf(fatigueCase), chosen, ranked,
                topFactors, confidence, policy, reasoning,
                customerMessage(fatigueCase, chosen.action(), chosen.discountPercent(), language));
    }

    /** Evaluate every eligible action (used by the simulator & persistence passes too). */
    public List<ActionEvaluation> simulateAll(RecoveryCase c) {
        List<ActionEvaluation> out = new ArrayList<>();
        if (c.eligible() == null) return out;
        double baseline = baselineOf(c);
        for (RecoveryAction action : new LinkedHashSet<>(c.eligible())) {
            if (action == RecoveryAction.OFFER_DISCOUNT) {
                for (int pct : DISCOUNT_TIERS) {
                    if (pct <= c.maxDiscountPercent() && c.maxDiscountPercent() > 0) {
                        out.add(evaluator(c).evaluate(c, action, pct, baseline));
                    }
                }
            } else {
                out.add(evaluator(c).evaluate(c, action, null, baseline));
            }
        }
        return out;
    }

    /** Natural (no-intervention) probability, exposed for the simulator/UI. */
    public double baselineProbability(RecoveryCase c) {
        return baselineOf(c);
    }

    /** Engine version stamped onto attempts this engine decided. */
    public String engineVersion() {
        return ENGINE_VERSION;
    }

    private double baselineOf(RecoveryCase c) {
        return scorer.baselineProbability(c);
    }

    private UpliftScoringService evaluator(RecoveryCase c) {
        return scorer;
    }

    /** Net-value ranking (highest incremental net value first). */
    public List<ActionEvaluation> rank(RecoveryCase c, List<ActionEvaluation> candidates) {
        List<ActionEvaluation> ranked = new ArrayList<>(candidates);
        ranked.sort((a, b) -> {
            int cmp = b.incrementalNetValue().compareTo(a.incrementalNetValue());
            if (cmp != 0) return cmp;
            return Double.compare(a.riskScore(), b.riskScore());
        });
        return ranked;
    }

    private String buildReasoning(RecoveryCase c, FatigueBand band, RecoveryState state,
                                  ActionEvaluation chosen, List<ActionEvaluation> ranked) {
        StringBuilder sb = new StringBuilder();
        sb.append("State ").append(state.name().replace('_', ' ')).append("; fatigue ")
          .append(String.format("%.0f", c.fatigue() * 100)).append("% (").append(band.name().toLowerCase()).append("). ");
        sb.append("Simulated ").append(ranked.size()).append(" candidate actions; selected ")
          .append(chosen.displayName()).append(" with incremental net value ₹").append(chosen.incrementalNetValue())
          .append(" (success model ").append(String.format("%.0f", chosen.successProbability() * 100))
          .append("% vs ").append(String.format("%.0f", chosen.baselineProbability() * 100))
          .append("% natural baseline).");
        if (ranked.size() >= 2) {
            sb.append(" Best alternative: ").append(ranked.get(1).displayName())
              .append(" worth ₹").append(ranked.get(1).incrementalNetValue()).append(".");
        }
        return sb.toString();
    }

    private List<String> factors(RecoveryCase c, double fatigue, FatigueBand band, RecoveryState state,
                                 ActionEvaluation chosen) {
        List<String> out = new ArrayList<>();
        out.add("Cause: " + (c.failureMode() == null ? (c.promiseBroken() ? "broken promise-to-pay" : "overdue") : c.failureMode().replace('_', ' '))
                + (c.retryable() && !c.sourceType().equals("CHECKOUT") ? " (transient)" : ""));
        out.add("Customer value: ₹" + (c.amount() == null ? BigDecimal.ZERO : c.amount())
                + (c.highValue() ? " — HIGH_VALUE segment" : ""));
        if (c.retryCount() > 0) out.add("Retry history: " + c.retryCount() + " prior attempt(s)");
        if (c.reminderCount() > 0) out.add("Prior touches: " + c.reminderCount() + " reminder(s)");
        out.add("Natural recovery baseline: " + String.format("%.0f", baselineOf(c) * 100) + "%");
        out.add("Recovery fatigue: " + String.format("%.0f", fatigue * 100) + "% (" + band.name().toLowerCase() + ")");
        out.add("Customer state: " + state.name().replace('_', ' '));
        if (chosen != null) {
            out.add("Highest incremental net value: " + chosen.displayName() + " ₹" + chosen.incrementalNetValue());
            out.add("Reliability score: " + String.format("%.2f", c.reliability()));
        }
        return out;
    }

    /** Customer-facing message for the chosen action (English or Hinglish). */
    public String customerMessage(RecoveryCase c, RecoveryAction action, Integer discountPct, String language) {
        boolean hinglish = "hinglish".equalsIgnoreCase(language);
        int pct = discountPct == null ? 0 : discountPct;
        switch (action) {
            case RETRY_SILENT, RETRY_NOW, RETRY_SCHEDULED:
                return null; // silent/retry — no customer message
            case SEND_PAYMENT_LINK:
                return hinglish
                        ? "Aapka payment complete nahi ho paya. Yahan se naya payment method use karke turant complete kar sakte hain: {link}"
                        : "Your recent payment didn't go through. You can complete it instantly with a fresh payment method here: {link}";
            case OFFER_DISCOUNT:
                return hinglish
                        ? "Aapke liye special " + pct + "% discount ready hai. Aaj hi payment complete karein: {link}"
                        : "We've applied a " + pct + "% discount to make this easy. Complete your payment today: {link}";
            case CHECKOUT_REMINDER:
                return hinglish
                        ? "Aapka cart abhi bhi save hai — checkout complete karein: {link}"
                        : "Your cart is still saved — finish your checkout here: {link}";
            case SEND_REMINDER:
                return hinglish
                        ? "Friendly reminder: aapka invoice abhi pending hai. Jaldi payment karein."
                        : "Friendly reminder: your invoice is still outstanding. Please pay at the earliest.";
            case OFFER_PAYMENT_PLAN:
                return hinglish
                        ? "Aapke invoice ko easy installments mein chukaya ja sakta hai — baat karte hain?"
                        : "Your invoice can be settled in easy installments — shall we arrange that?";
            case PROMISE_FOLLOWUP:
                return hinglish
                        ? "Aapne payment ka vaada kiya tha jo abhi tak nahi aaya. Kripya jaldi karein."
                        : "You promised payment by the agreed date, but it hasn't arrived yet. Please arrange it soon.";
            case ESCALATE_TO_HUMAN, ABANDON, NO_ACTION:
            default:
                return null;
        }
    }

    /** Anomaly findings for a case — the caller persists & routes HIGH/CRITICAL ones. */
    public List<AnomalyDetectionService.Finding> anomalies(RecoveryCase c) {
        return anomalyService.detect(c.withFatigue(c.fatigue() == 0 ? fatigueService.score(c) : c.fatigue()));
    }
}

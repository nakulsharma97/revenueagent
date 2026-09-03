package com.razorpay.recovery.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.transaction.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.intelligence.IntelligenceDecision;
import com.razorpay.recovery.intelligence.NextBestActionEngine;
import com.razorpay.recovery.intelligence.RecoveryCase;

import java.util.List;
import java.util.Map;

/**
 * Produces the recovery decision for one transaction / checkout session / receivable.
 *
 * <p><b>One canonical decision pipeline.</b> Every live path runs the structured
 * {@link NextBestActionEngine} (Spring-injected singleton): it counterfactually
 * simulates every eligible action, ranks by expected <em>incremental</em> net value,
 * applies fatigue/policy constraints, computes confidence, and returns the next best
 * action. A live LLM may ONLY enrich the reasoning as an explanation layer — it never
 * chooses the action, and {@code llmDriven=true} is set only when a real LLM response
 * was actually incorporated.
 *
 * <p>There is exactly ONE decision pipeline. The trace-aware
 * {@code decideWithMeta*(…, DecisionTrace)} overloads are what the recovery batch uses;
 * {@link #decideWithMeta(Transaction)} is the convenience form for tests and the
 * simulator. RulesEngine.enforceBounds() re-checks the engine's output before anything
 * executes, so no decision can leave the bounded action space.
 */
@Service
public class DecisionAgentService {

    private final RulesEngine rulesEngine;
    private final RestClient restClient = RestClient.create("https://api.anthropic.com");
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${llm.enabled}")
    private boolean llmEnabled;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    private final BoundsConfig boundsConfig;

    /**
     * Structured Next-Best-Action engine — the decision-maker for the live pipeline.
     * Spring-injected singleton: the SAME bean instance is shared by every production
     * caller, so REST, SSE, scheduler, startup and simulator runs can never diverge.
     * Unit tests may construct the agent with a directly-built engine.
     */
    private final NextBestActionEngine engine;

    public DecisionAgentService(RulesEngine rulesEngine, BoundsConfig boundsConfig, NextBestActionEngine engine) {
        this.rulesEngine = rulesEngine;
        this.boundsConfig = boundsConfig;
        this.engine = engine;
    }

    /**
     * Metadata-bearing entry point — returns whether the LLM explanation layer actually
     * contributed (llmDriven) and whether human sign-off is required.
     * Orchestrator uses the trace-aware overloads; tests use this convenience form.
     */
    public DecisionResult decideWithMeta(Transaction tx) {
        return decideWithMeta(tx, segmentOf(tx), new DecisionTrace());
    }

    /** Extract the customer segment for a transaction (STANDARD when unknown). */
    public com.razorpay.recovery.customer.Customer.CustomerSegment segmentOf(Transaction tx) {
        if (tx.getSubscription() != null && tx.getSubscription().getCustomer() != null
                && tx.getSubscription().getCustomer().getCustomerSegment() != null) {
            return tx.getSubscription().getCustomer().getCustomerSegment();
        }
        return com.razorpay.recovery.customer.Customer.CustomerSegment.STANDARD;
    }

    // ═══════════════════════════════════════════════════════════════
    // Trace-aware overloads — the recovery batch's canonical entry points
    // ═══════════════════════════════════════════════════════════════

    public DecisionResult decideWithMeta(Transaction tx, DecisionTrace trace) {
        return decideWithMeta(tx, segmentOf(tx), trace);
    }

    /**
     * Segment-aware entry point — the recovery pipeline's canonical payment decision path.
     * Uses the customer segment's bounds (HIGH_VALUE gets wider retry/discount limits)
     * throughout eligibility, enforcement, and sign-off computation.
     */
    public DecisionResult decideWithMeta(Transaction tx, com.razorpay.recovery.customer.Customer.CustomerSegment segment, DecisionTrace trace) {
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx, segment, trace);

        // ── Structured Next-Best-Action decision (deterministic, always within bounds) ──
        BoundsConfig.SegmentBounds bounds = boundsConfig.boundsFor(segment);
        RecoveryCase case_ = RecoveryCase.fromPayment(tx, segment, eligible,
                bounds.maxRetries(), bounds.maxDiscountPercent());
        IntelligenceDecision id = engine.decide(case_, boundsConfig.getLanguage());
        trace.add("INTELLIGENCE", "State " + id.recoveryState() + "; fatigue "
                + String.format("%.0f", id.fatigueScore() * 100) + "% (" + id.fatigueBand() + ")");
        trace.add("SIMULATION", "Counterfactually simulated " + id.alternatives().size()
                + " candidate actions against a " + String.format("%.0f", id.baselineProbability() * 100)
                + "% natural-recovery baseline");
        RecoveryDecision proposed = toRecoveryDecision(id, eligible);
        // The action ALWAYS comes from the structured engine; a live LLM may optionally
        // enrich the reasoning as an explanation layer (never to choose the action).
        ExplainOutcome ex = enrichWithLlmExplanation(proposed, id, trace);
        boolean usedLlm = ex.used();
        proposed = ex.decision();
        trace.add("SELECTION", "Next-Best-Action = " + proposed.action()
                + (proposed.discountPercent() != null ? " " + proposed.discountPercent() + "%" : "")
                + " (expected net value " + (id.chosen() == null ? "n/a" : String.valueOf(id.chosen().incrementalNetValue()))
                + ", confidence " + String.format("%.2f", proposed.confidence()) + ", policy " + id.automationPolicy() + ")");

        EnforcedDecision enforced = rulesEngine.enforceBounds(tx, segment, proposed, trace);
        // Compute the full signoff signal: enforceBounds flags discount caps;
        // RulesEngine.requiresHumanSignoff() also checks the last retry before the segment's limit.
        boolean signoffFromEnforced = enforced.requiresHumanSignoff();
        boolean signoffFromRules = rulesEngine.requiresHumanSignoff(tx, segment, proposed);
        boolean signoffRequired = signoffFromEnforced || signoffFromRules;
        String signoffReason = signoffFromEnforced ? enforced.signoffReason() : null;
        if (!signoffFromEnforced && signoffFromRules && tx.getRetryCount() >= segmentBoundsRetryFloor(segment) - 1) {
            signoffReason = "Last retry before the segment's retry limit — requires human review before final disposition.";
            trace.add("SIGNOFF", "Retry count " + tx.getRetryCount() + " at the segment's sign-off threshold (" + segment + ") — human sign-off required.");
        }
        if (signoffFromEnforced) {
            trace.add("SIGNOFF", "Human sign-off required: " + signoffReason);
        }

        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }

    private int segmentBoundsRetryFloor(com.razorpay.recovery.customer.Customer.CustomerSegment segment) {
        return boundsConfig.boundsFor(segment).maxRetries();
    }

    public DecisionResult decideWithMetaCheckout(CheckoutSession session, DecisionTrace trace) {
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(session, trace);

        RecoveryCase case_ = RecoveryCase.fromCheckout(session, eligible,
                boundsConfig.getMaxRetries(), boundsConfig.getMaxDiscountPercent());
        IntelligenceDecision id = engine.decide(case_, boundsConfig.getLanguage());
        trace.add("INTELLIGENCE", "State " + id.recoveryState() + "; fatigue "
                + String.format("%.0f", id.fatigueScore() * 100) + "% (" + id.fatigueBand() + ")");
        trace.add("SIMULATION", "Counterfactually simulated " + id.alternatives().size()
                + " candidate actions against a " + String.format("%.0f", id.baselineProbability() * 100)
                + "% natural-recovery baseline");
        RecoveryDecision proposed = toRecoveryDecision(id, eligible);
        ExplainOutcome ex = enrichWithLlmExplanation(proposed, id, trace);
        boolean usedLlm = ex.used();
        proposed = ex.decision();
        trace.add("SELECTION", "Next-Best-Action = " + proposed.action()
                + (proposed.discountPercent() != null ? " " + proposed.discountPercent() + "%" : "")
                + " (confidence " + String.format("%.2f", proposed.confidence()) + ")");

        EnforcedDecision enforced = rulesEngine.enforceBounds(session, proposed, trace);
        boolean signoffFromEnforced = enforced.requiresHumanSignoff();
        boolean signoffFromRules = rulesEngine.requiresHumanSignoff(session, proposed);
        boolean signoffRequired = signoffFromEnforced || signoffFromRules;
        String signoffReason = signoffFromEnforced ? enforced.signoffReason() : null;
        if (!signoffFromEnforced && signoffFromRules && session.getReminderCount() >= 2) {
            signoffReason = "3rd reminder attempt — requires human review.";
            trace.add("SIGNOFF", "3rd reminder on checkout session — human sign-off required.");
        }
        if (signoffFromEnforced) {
            trace.add("SIGNOFF", "Human sign-off required: " + signoffReason);
        }
        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }

    public DecisionResult decideWithMetaReceivable(Receivable receivable, DecisionTrace trace) {
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(receivable, trace);

        RecoveryCase case_ = RecoveryCase.fromReceivable(receivable, eligible,
                boundsConfig.getMaxRetries(), boundsConfig.getMaxDiscountPercent());
        IntelligenceDecision id = engine.decide(case_, boundsConfig.getLanguage());
        trace.add("INTELLIGENCE", "State " + id.recoveryState() + "; fatigue "
                + String.format("%.0f", id.fatigueScore() * 100) + "% (" + id.fatigueBand() + ")");
        trace.add("SIMULATION", "Counterfactually simulated " + id.alternatives().size()
                + " candidate actions against a " + String.format("%.0f", id.baselineProbability() * 100)
                + "% natural-recovery baseline");
        RecoveryDecision proposed = toRecoveryDecision(id, eligible);
        ExplainOutcome ex = enrichWithLlmExplanation(proposed, id, trace);
        boolean usedLlm = ex.used();
        proposed = ex.decision();
        trace.add("SELECTION", "Next-Best-Action = " + proposed.action()
                + (proposed.discountPercent() != null ? " " + proposed.discountPercent() + "%" : "")
                + " (confidence " + String.format("%.2f", proposed.confidence()) + ")");

        EnforcedDecision enforced = rulesEngine.enforceBounds(receivable, proposed, trace);
        boolean signoffFromEnforced = enforced.requiresHumanSignoff();
        boolean signoffFromRules = rulesEngine.requiresHumanSignoff(receivable, proposed);
        boolean signoffRequired = signoffFromEnforced || signoffFromRules;
        String signoffReason = signoffFromEnforced ? enforced.signoffReason() : null;
        if (!signoffFromEnforced && signoffFromRules && receivable.getReminderCount() >= 2) {
            signoffReason = "3rd reminder on overdue receivable — requires human review.";
            trace.add("SIGNOFF", "3rd reminder on overdue receivable — human sign-off required.");
        }
        if (signoffFromEnforced) {
            trace.add("SIGNOFF", "Human sign-off required: " + signoffReason);
        }
        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }

    // ═══════════════════════════════════════════════════════════════
    // Intelligence-engine mapping + optional LLM explanation layer
    // ═══════════════════════════════════════════════════════════════

    /** Map the engine's structured decision onto the pipeline's RecoveryDecision contract. */
    private RecoveryDecision toRecoveryDecision(IntelligenceDecision id, List<RecoveryAction> eligible) {
        if (id.chosen() == null) {
            RecoveryAction fallback = eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN)
                    ? RecoveryAction.ESCALATE_TO_HUMAN
                    : RecoveryAction.ABANDON;
            return new RecoveryDecision(fallback, id.reasoning(), 0.30, null, null);
        }
        var chosen = id.chosen();
        return new RecoveryDecision(
                chosen.action(),
                id.reasoning() + " " + id.whyThisAction(),
                id.confidence(),
                chosen.action() == RecoveryAction.OFFER_DISCOUNT ? chosen.discountPercent() : null,
                id.customerMessage()
        );
    }

    /** Whether an actual LLM response is possible at all (used to decide honesty of the flag). */
    private boolean llmConfigured() {
        return llmEnabled && apiKey != null && !apiKey.isBlank();
    }

    private record ExplainOutcome(boolean used, RecoveryDecision decision) {}

    /**
     * Optional LLM <em>explanation</em> layer. The action was already chosen by the
     * structured engine; when a real API key is configured the model is asked ONLY to
     * articulate why that action beats the runner-up (never to pick). Returns
     * {@code used=false} unless an actual API response was incorporated, so
     * {@code llmDriven} is never faked.
     */
    private ExplainOutcome enrichWithLlmExplanation(RecoveryDecision proposed, IntelligenceDecision id, DecisionTrace trace) {
        if (!llmConfigured() || id.chosen() == null || id.chosen().action() == RecoveryAction.ABANDON) {
            return new ExplainOutcome(false, proposed);
        }
        try {
            String runnerUp = null;
            for (var a : id.alternatives()) {
                if (a != id.chosen() && a.action() != id.chosen().action()) {
                    runnerUp = a.displayName();
                    break;
                }
            }
            String prompt = """
                    You are the explanation layer of a revenue-recovery decision engine. Do NOT choose an action.
                    The structured engine already chose: %s (%s%% confidence).
                    Runner-up candidate: %s.
                    In at most 2 short sentences, explain to a risk officer why this action was chosen over the
                    runner-up in terms of expected incremental net value, and name the single biggest risk.
                    Return only the explanation text.
                    """.formatted(
                    proposed.action().name().replace('_', ' '),
                    String.format("%.0f", proposed.confidence() * 100),
                    runnerUp == null ? "none" : runnerUp);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 200,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            JsonNode response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String text = response.get("content").get(0).get("text").asText().trim();
            if (text.isEmpty()) {
                return new ExplainOutcome(false, proposed);
            }
            String reasoning = proposed.reasoning() + "\n[LLM explanation] " + text;
            RecoveryDecision enriched = new RecoveryDecision(proposed.action(), reasoning, proposed.confidence(),
                    proposed.discountPercent(), proposed.customerMessage());
            trace.add("LLM_EXPLANATION", "LLM explanation layer contributed a real response for " + proposed.action());
            return new ExplainOutcome(true, enriched);
        } catch (Exception e) {
            trace.add("LLM_EXPLANATION", "LLM explanation unavailable (" + e.getClass().getSimpleName()
                    + ") — reasoning stays engine-derived.");
            return new ExplainOutcome(false, proposed);
        }
    }
}

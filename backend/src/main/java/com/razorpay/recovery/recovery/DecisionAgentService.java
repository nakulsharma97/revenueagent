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

import java.util.List;
import java.util.Map;

/**
 * Proposes a recovery action for one transaction.
 *
 * Two paths, same output contract (LlmDecision):
 *  - llm.enabled=true  -> calls the Claude API with the transaction context and the
 *                         RulesEngine's eligible-action list, asks for structured JSON back.
 *  - llm.enabled=false -> deterministic heuristic fallback, so the whole pipeline still
 *                         runs end-to-end without an API key (e.g. on a judge's machine).
 *
 * Either way, RulesEngine.enforceBounds() re-checks the output before anything executes.
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

    public DecisionAgentService(RulesEngine rulesEngine) {
        this.rulesEngine = rulesEngine;
    }

    /**
     * Original entry point — returns just the bounded LlmDecision.
     * Existing tests and callers depend on this signature staying stable.
     */
    public LlmDecision decide(Transaction tx) {
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);
        LlmDecision proposed = propose(tx, eligible);
        EnforcedDecision enforced = rulesEngine.enforceBounds(tx, proposed);
        return enforced.decision();
    }

    // ═══ Checkout abandonment decision ═══

    public DecisionResult decideWithMetaCheckout(CheckoutSession session) {
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(session);
        LlmDecision proposed = proposeCheckout(session, eligible);
        EnforcedDecision enforced = rulesEngine.enforceBounds(session, proposed);
        boolean signoffFromEnforced = enforced.requiresHumanSignoff();
        boolean signoffFromRules = rulesEngine.requiresHumanSignoff(session, proposed);
        boolean signoffRequired = signoffFromEnforced || signoffFromRules;
        String signoffReason = signoffFromEnforced ? enforced.signoffReason() : null;
        if (!signoffFromEnforced && signoffFromRules && session.getReminderCount() >= 2) {
            signoffReason = "3rd reminder attempt — requires human review.";
        }
        boolean usedLlm = llmEnabled && apiKey != null && !apiKey.isBlank();
        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }

    // ═══ B2B receivables decision ═══

    public DecisionResult decideWithMetaReceivable(Receivable receivable) {
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(receivable);
        LlmDecision proposed = proposeReceivable(receivable, eligible);
        EnforcedDecision enforced = rulesEngine.enforceBounds(receivable, proposed);
        boolean signoffFromEnforced = enforced.requiresHumanSignoff();
        boolean signoffFromRules = rulesEngine.requiresHumanSignoff(receivable, proposed);
        boolean signoffRequired = signoffFromEnforced || signoffFromRules;
        String signoffReason = signoffFromEnforced ? enforced.signoffReason() : null;
        if (!signoffFromEnforced && signoffFromRules && receivable.getReminderCount() >= 2) {
            signoffReason = "3rd reminder on overdue receivable — requires human review.";
        }
        boolean usedLlm = llmEnabled && apiKey != null && !apiKey.isBlank();
        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }

    /**
     * Additive entry point — returns full metadata including whether the LLM
     * was actually used, and whether human sign-off is required.
     * Orchestrator uses this; existing tests keep using decide().
     */
    public DecisionResult decideWithMeta(Transaction tx) {
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        boolean usedLlm = llmEnabled && apiKey != null && !apiKey.isBlank();
        LlmDecision proposed;
        if (usedLlm) {
            LlmDecision llmResult = callLlm(tx, eligible);
            usedLlm = !llmResult.reasoning().startsWith("Rules-only mode:");
            proposed = llmResult;
        } else {
            proposed = heuristicFallback(tx, eligible);
        }

        EnforcedDecision enforced = rulesEngine.enforceBounds(tx, proposed);
        // Compute the full signoff signal: enforceBounds flags discount caps;
        // RulesEngine.requiresHumanSignoff() also checks 3rd consecutive failure.
        boolean signoffFromEnforced = enforced.requiresHumanSignoff();
        boolean signoffFromRules = rulesEngine.requiresHumanSignoff(tx, proposed);
        boolean signoffRequired = signoffFromEnforced || signoffFromRules;
        String signoffReason = signoffFromEnforced ? enforced.signoffReason() : null;
        if (!signoffFromEnforced && signoffFromRules && tx.getRetryCount() >= 2) {
            signoffReason = "3rd consecutive failure — requires human review before final disposition.";
        }

        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }

    private LlmDecision callLlm(Transaction tx, List<RecoveryAction> eligible) {
        try {
            String prompt = buildPrompt(tx, eligible);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 400,
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

            String text = response.get("content").get(0).get("text").asText();
            String json = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1);
            JsonNode decision = mapper.readTree(json);

            return new LlmDecision(
                    RecoveryAction.valueOf(decision.get("action").asText()),
                    decision.get("reasoning").asText(),
                    decision.path("confidence").asDouble(0.7),
                    decision.has("discountPercent") && !decision.get("discountPercent").isNull()
                            ? decision.get("discountPercent").asInt() : null
            );
        } catch (Exception e) {
            return heuristicFallback(tx, eligible);
        }
    }

    private String buildPrompt(Transaction tx, List<RecoveryAction> eligible) {
        return """
                You are a payment-recovery decision agent. Choose exactly ONE action from the
                allowed list for this failed transaction, and justify it in one sentence.

                Transaction:
                - amount: %s
                - failure_reason: %s
                - retry_count: %d
                - customer_reliability_score: %.2f

                Allowed actions (you MUST pick one of these, nothing else): %s

                Respond with ONLY a JSON object, no other text:
                {"action": "<one of the allowed actions>", "reasoning": "<one sentence>", "confidence": <0-1>, "discountPercent": <integer or null>}
                """.formatted(
                tx.getAmount(),
                tx.getFailureReason(),
                tx.getRetryCount(),
                tx.getSubscription() != null && tx.getSubscription().getCustomer() != null
                        ? tx.getSubscription().getCustomer().getPaymentReliabilityScore() : 0.5,
                eligible
        );
    }

    /** Shared proposal logic — try LLM, fall back to heuristic. */
    private LlmDecision propose(Transaction tx, List<RecoveryAction> eligible) {
        boolean usedLlm = llmEnabled && apiKey != null && !apiKey.isBlank();
        if (usedLlm) {
            LlmDecision llmResult = callLlm(tx, eligible);
            if (!llmResult.reasoning().startsWith("Rules-only mode:")) return llmResult;
        }
        return heuristicFallback(tx, eligible);
    }

    private LlmDecision proposeCheckout(CheckoutSession session, List<RecoveryAction> eligible) {
        // In heuristic mode, skip LLM and go straight to heuristic
        if (!llmEnabled || apiKey == null || apiKey.isBlank()) {
            return heuristicFallbackCheckout(session, eligible);
        }
        // TODO: LLM prompt for checkout sessions
        return heuristicFallbackCheckout(session, eligible);
    }

    private LlmDecision proposeReceivable(Receivable receivable, List<RecoveryAction> eligible) {
        if (!llmEnabled || apiKey == null || apiKey.isBlank()) {
            return heuristicFallbackReceivable(receivable, eligible);
        }
        // TODO: LLM prompt for receivables
        return heuristicFallbackReceivable(receivable, eligible);
    }

    private LlmDecision heuristicFallbackCheckout(CheckoutSession session, List<RecoveryAction> eligible) {
        if (eligible.contains(RecoveryAction.CHECKOUT_REMINDER)) {
            return new LlmDecision(RecoveryAction.CHECKOUT_REMINDER,
                    "Rules-only mode: abandoned cart with retryable reason — send checkout reminder.", 0.55, null);
        }
        if (eligible.contains(RecoveryAction.OFFER_DISCOUNT)) {
            return new LlmDecision(RecoveryAction.OFFER_DISCOUNT,
                    "Rules-only mode: high-value cart abandoned — offer discount to recover.", 0.5, 10);
        }
        if (eligible.contains(RecoveryAction.SEND_PAYMENT_LINK)) {
            return new LlmDecision(RecoveryAction.SEND_PAYMENT_LINK,
                    "Rules-only mode: send payment link to complete checkout.", 0.5, null);
        }
        return new LlmDecision(RecoveryAction.ESCALATE_TO_HUMAN,
                "Rules-only mode: no automated checkout recovery action left.", 0.4, null);
    }

    private LlmDecision heuristicFallbackReceivable(Receivable receivable, List<RecoveryAction> eligible) {
        if (eligible.contains(RecoveryAction.OFFER_PAYMENT_PLAN)) {
            return new LlmDecision(RecoveryAction.OFFER_PAYMENT_PLAN,
                    "Rules-only mode: significantly overdue receivable — offer payment plan.", 0.5, 3);
        }
        if (eligible.contains(RecoveryAction.SEND_REMINDER)) {
            return new LlmDecision(RecoveryAction.SEND_REMINDER,
                    "Rules-only mode: overdue receivable — send payment reminder.", 0.55, null);
        }
        return new LlmDecision(RecoveryAction.ESCALATE_TO_HUMAN,
                "Rules-only mode: no automated receivable recovery action left.", 0.4, null);
    }

    /** Deterministic, explainable stand-in for the LLM — used when no API key is configured. */
    private LlmDecision heuristicFallback(Transaction tx, List<RecoveryAction> eligible) {
        if (eligible.contains(RecoveryAction.RETRY_NOW) && tx.getRetryCount() == 0) {
            return new LlmDecision(RecoveryAction.RETRY_NOW,
                    "Rules-only mode: first failure on a retryable decline code — retry immediately.", 0.6, null);
        }
        if (eligible.contains(RecoveryAction.RETRY_SCHEDULED)) {
            return new LlmDecision(RecoveryAction.RETRY_SCHEDULED,
                    "Rules-only mode: retryable failure with prior attempts — schedule after cooldown.", 0.55, null);
        }
        if (eligible.contains(RecoveryAction.OFFER_DISCOUNT)) {
            return new LlmDecision(RecoveryAction.OFFER_DISCOUNT,
                    "Rules-only mode: high-value transaction, terminal decline — incentivize a fresh payment method.", 0.5, 10);
        }
        if (eligible.contains(RecoveryAction.SEND_PAYMENT_LINK)) {
            return new LlmDecision(RecoveryAction.SEND_PAYMENT_LINK,
                    "Rules-only mode: default nudge — ask the customer to update their payment method.", 0.5, null);
        }
        return new LlmDecision(RecoveryAction.ESCALATE_TO_HUMAN,
                "Rules-only mode: no automated action left in bounds.", 0.4, null);
    }
}

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

    private final BoundsConfig boundsConfig;

    public DecisionAgentService(RulesEngine rulesEngine, BoundsConfig boundsConfig) {
        this.rulesEngine = rulesEngine;
        this.boundsConfig = boundsConfig;
    }

    private boolean isHinglish() {
        return "hinglish".equals(boundsConfig.getLanguage());
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
        // Checkout proposals are heuristic-only today (no LLM prompt implemented) — never claim LLM usage.
        boolean usedLlm = false;
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
        // Receivable proposals are heuristic-only today (no LLM prompt implemented) — never claim LLM usage.
        boolean usedLlm = false;
        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }

    /**
     * Additive entry point — returns full metadata including whether the LLM
     * was actually used, and whether human sign-off is required.
     * Orchestrator uses this; existing tests keep using decide().
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

            String customerMsg = decision.has("customerMessage") && !decision.get("customerMessage").isNull()
                    ? decision.get("customerMessage").asText() : null;

            return new LlmDecision(
                    RecoveryAction.valueOf(decision.get("action").asText()),
                    decision.get("reasoning").asText(),
                    decision.path("confidence").asDouble(0.7),
                    decision.has("discountPercent") && !decision.get("discountPercent").isNull()
                            ? decision.get("discountPercent").asInt() : null,
                    customerMsg
            );
        } catch (Exception e) {
            return heuristicFallback(tx, eligible);
        }
    }

    private String buildPrompt(Transaction tx, List<RecoveryAction> eligible) {
        String langInstruction = isHinglish()
                ? "\n\nAdditionally, compose a short, natural Hinglish SMS/email message (1-2 sentences) for the customer\n" +
                  "regarding this action. Use casual Hindi-English mix that Indian customers find natural.\n" +
                  "For example, for SEND_PAYMENT_LINK: 'Aapka payment fail ho gaya tha, yahan se dubara try kar sakte hain: {link}'\n" +
                  "Include the field 'customerMessage' in your JSON response with this message."
                : "";
        String jsonFields = isHinglish()
                ? "{\"action\": \"<one of the allowed actions>\", \"reasoning\": \"<one sentence>\", \"confidence\": <0-1>, \"discountPercent\": <integer or null>, \"customerMessage\": \"<short customer-facing SMS/email>\"}"
                : "{\"action\": \"<one of the allowed actions>\", \"reasoning\": \"<one sentence>\", \"confidence\": <0-1>, \"discountPercent\": <integer or null>}";
        return """
                You are a payment-recovery decision agent. Choose exactly ONE action from the
                allowed list for this failed transaction, and justify it in one sentence.

                Transaction:
                - amount: %s
                - failure_reason: %s
                - retry_count: %d
                - customer_reliability_score: %.2f

                Allowed actions (you MUST pick one of these, nothing else): %s
                %s
                Respond with ONLY a JSON object, no other text:
                %s
                """.formatted(
                tx.getAmount(),
                tx.getFailureReason(),
                tx.getRetryCount(),
                tx.getSubscription() != null && tx.getSubscription().getCustomer() != null
                        ? tx.getSubscription().getCustomer().getPaymentReliabilityScore() : 0.5,
                eligible,
                langInstruction,
                jsonFields
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
                    "Rules-only mode: abandoned cart with retryable reason — send checkout reminder.", 0.55, null,
                    hinglish("Apka cart abhi bhi save hai! Complete karo ab: {link}",
                             "Your cart is still saved! Complete your purchase now: {link}"));
        }
        if (eligible.contains(RecoveryAction.OFFER_DISCOUNT)) {
            return new LlmDecision(RecoveryAction.OFFER_DISCOUNT,
                    "Rules-only mode: high-value cart abandoned — offer discount to recover.", 0.5, 10,
                    hinglish("Aapke cart pe special discount mil raha hai! Jaldi complete karo: {link}",
                             "Special discount on your cart! Complete your order now: {link}"));
        }
        if (eligible.contains(RecoveryAction.SEND_PAYMENT_LINK)) {
            return new LlmDecision(RecoveryAction.SEND_PAYMENT_LINK,
                    "Rules-only mode: send payment link to complete checkout.", 0.5, null,
                    hinglish("Payment pending hai — yahan se complete kar sakte hain: {link}",
                             "Payment pending — complete it here: {link}"));
        }
        return new LlmDecision(RecoveryAction.ESCALATE_TO_HUMAN,
                "Rules-only mode: no automated checkout recovery action left.", 0.4, null);
    }

    private LlmDecision heuristicFallbackReceivable(Receivable receivable, List<RecoveryAction> eligible) {
        if (eligible.contains(RecoveryAction.PROMISE_FOLLOWUP)) {
            return new LlmDecision(RecoveryAction.PROMISE_FOLLOWUP,
                    "Rules-only mode: customer promised to pay by " + receivable.getPromisedPaymentDate() + " but payment has not arrived — follow up on broken promise.", 0.6, null,
                    hinglish("Aapne " + receivable.getPromisedPaymentDate() + " ko payment ka vaada kiya tha, lekin abhi tak payment nahi aaya. Jaldi karein.",
                             "You promised payment by " + receivable.getPromisedPaymentDate() + " but it has not arrived yet. Please pay at the earliest."));
        }
        if (eligible.contains(RecoveryAction.OFFER_PAYMENT_PLAN)) {
            return new LlmDecision(RecoveryAction.OFFER_PAYMENT_PLAN,
                    "Rules-only mode: significantly overdue receivable — offer payment plan.", 0.5, 3,
                    hinglish("Aapka invoice overdue hai. Hum 3 installments mein payment plan de sakte hain — batayein kya karein?",
                             "Your invoice is overdue. We can offer a 3-installment payment plan — let us know how to proceed."));
        }
        if (eligible.contains(RecoveryAction.SEND_REMINDER)) {
            return new LlmDecision(RecoveryAction.SEND_REMINDER,
                    "Rules-only mode: overdue receivable — send payment reminder.", 0.55, null,
                    hinglish("Friendly reminder: aapka invoice due date cross ho chuka hai. Jaldi payment karein.",
                             "Friendly reminder: your invoice is past due. Please make the payment at your earliest."));
        }
        return new LlmDecision(RecoveryAction.ESCALATE_TO_HUMAN,
                "Rules-only mode: no automated receivable recovery action left.", 0.4, null);
    }

    /** Deterministic, explainable stand-in for the LLM — used when no API key is configured. */
    private LlmDecision heuristicFallback(Transaction tx, List<RecoveryAction> eligible) {
        // Silent-first: first-attempt retryable failures use background retry only
        if (eligible.contains(RecoveryAction.RETRY_SILENT) && tx.getRetryCount() == 0) {
            return new LlmDecision(RecoveryAction.RETRY_SILENT,
                    "Rules-only mode: silent recovery — no customer contact, background retry only.", 0.6, null,
                    hinglish("Hum background mein dubara try kar rahe hain — aapko koi message nahi aayega.",
                             "Retrying silently in the background — no customer notification sent."));
        }
        if (eligible.contains(RecoveryAction.RETRY_NOW)) {
            return new LlmDecision(RecoveryAction.RETRY_NOW,
                    "Rules-only mode: silent retry exhausted — retry immediately with customer notification.", 0.6, null,
                    hinglish("Aapka payment fail ho gaya tha. Hum dubara try kar rahe hain — kuch der mein paisa debit ho jayega.",
                             "Your payment failed. We are retrying now — the amount will be debited shortly."));
        }
        if (eligible.contains(RecoveryAction.RETRY_SCHEDULED)) {
            return new LlmDecision(RecoveryAction.RETRY_SCHEDULED,
                    "Rules-only mode: retryable failure with prior attempts — schedule after cooldown.", 0.55, null,
                    hinglish("Payment problem aa raha hai. Thodi der baad hum dubara try karenge.",
                             "Payment issue detected. We will retry after a short cooldown period."));
        }
        if (eligible.contains(RecoveryAction.OFFER_DISCOUNT)) {
            return new LlmDecision(RecoveryAction.OFFER_DISCOUNT,
                    "Rules-only mode: high-value transaction, terminal decline — incentivize a fresh payment method.", 0.5, 10,
                    hinglish("Hum 10% discount de rahe hain taaki aap naya payment method try kar sakein: {link}",
                             "We are offering a 10% discount so you can try a different payment method: {link}"));
        }
        if (eligible.contains(RecoveryAction.SEND_PAYMENT_LINK)) {
            return new LlmDecision(RecoveryAction.SEND_PAYMENT_LINK,
                    "Rules-only mode: default nudge — ask the customer to update their payment method.", 0.5, null,
                    hinglish("Aapka payment method update karna hai? Yahan se naya method add kar sakte hain: {link}",
                             "Need to update your payment method? Add a new one here: {link}"));
        }
        return new LlmDecision(RecoveryAction.ESCALATE_TO_HUMAN,
                "Rules-only mode: no automated action left in bounds.", 0.4, null);
    }

    /** Returns the Hinglish or English template based on current language setting. */
    private String hinglish(String hinglishMsg, String englishMsg) {
        return isHinglish() ? hinglishMsg : englishMsg;
    }

    // ═══════════════════════════════════════════════════════════════
    // Trace-aware overloads — same logic, but append PROPOSAL step
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

        boolean usedLlm = llmEnabled && apiKey != null && !apiKey.isBlank();
        LlmDecision proposed;
        if (usedLlm) {
            LlmDecision llmResult = callLlm(tx, eligible);
            usedLlm = !llmResult.reasoning().startsWith("Rules-only mode:");
            proposed = llmResult;
            trace.add("PROPOSAL", (usedLlm ? "LLM" : "Heuristic fallback") + " proposed " + proposed.action()
                    + " (confidence " + String.format("%.2f", proposed.confidence()) + "): " + proposed.reasoning());
        } else {
            proposed = heuristicFallback(tx, eligible);
            trace.add("PROPOSAL", "Heuristic fallback proposed " + proposed.action()
                    + " (confidence " + String.format("%.2f", proposed.confidence()) + "): " + proposed.reasoning());
        }

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
        LlmDecision proposed = proposeCheckout(session, eligible);
        trace.add("PROPOSAL", "Heuristic proposed " + proposed.action()
                + " (confidence " + String.format("%.2f", proposed.confidence()) + "): " + proposed.reasoning());
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
        // Checkout proposals are heuristic-only today (no LLM prompt implemented) — never claim LLM usage.
        boolean usedLlm = false;
        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }

    public DecisionResult decideWithMetaReceivable(Receivable receivable, DecisionTrace trace) {
        List<RecoveryAction> eligible = rulesEngine.eligibleActions(receivable, trace);
        LlmDecision proposed = proposeReceivable(receivable, eligible);
        trace.add("PROPOSAL", "Heuristic proposed " + proposed.action()
                + " (confidence " + String.format("%.2f", proposed.confidence()) + "): " + proposed.reasoning());
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
        // Receivable proposals are heuristic-only today (no LLM prompt implemented) — never claim LLM usage.
        boolean usedLlm = false;
        return new DecisionResult(enforced, usedLlm, signoffRequired, signoffReason);
    }
}

package com.razorpay.recovery.recovery;

import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;

/**
 * The decision payload that flows through the pipeline: the chosen action, the
 * explainable reasoning, the engine's confidence, and any discount size.
 *
 * <p>The action is ALWAYS produced by the structured Next-Best-Action engine
 * (intelligence.NextBestActionEngine); a live LLM may only enrich {@code reasoning}
 * afterwards as an explanation layer. The class is named RecoveryDecision (not
 * "LLM decision") because that is what the type really carries. Never executed
 * directly — RulesEngine.enforceBounds() validates it first.
 *
 * <p>customerMessage: an optional customer-facing SMS/email draft in the configured
 * language (English or Hinglish), appropriate to the chosen action.
 */
public record RecoveryDecision(
        RecoveryAction action,
        String reasoning,
        double confidence,
        Integer discountPercent,   // null unless action == OFFER_DISCOUNT
        String customerMessage     // null unless a customer-facing action was chosen
) {
    /** Convenience constructor without customerMessage (backward-compatible). */
    public RecoveryDecision(RecoveryAction action, String reasoning, double confidence, Integer discountPercent) {
        this(action, reasoning, confidence, discountPercent, null);
    }
}
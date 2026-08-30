package com.razorpay.recovery.recovery;

import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;

/**
 * Structured shape the LLM is instructed to return (as JSON) for one transaction.
 * Never executed directly — RulesEngine.enforceBounds() validates it first.
 *
 * customerMessage: an optional customer-facing SMS/email draft in the configured
 * language (English or Hinglish), appropriate to the chosen action.
 */
public record LlmDecision(
        RecoveryAction action,
        String reasoning,
        double confidence,
        Integer discountPercent,   // null unless action == OFFER_DISCOUNT
        String customerMessage     // null unless language is set to "hinglish" or "en"
) {
    /** Convenience constructor without customerMessage (backward-compatible). */
    public LlmDecision(RecoveryAction action, String reasoning, double confidence, Integer discountPercent) {
        this(action, reasoning, confidence, discountPercent, null);
    }
}

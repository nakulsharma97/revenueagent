package com.razorpay.recovery.dto;

import com.razorpay.recovery.model.RecoveryAttempt.RecoveryAction;

/**
 * Structured shape the LLM is instructed to return (as JSON) for one transaction.
 * Never executed directly — RulesEngine.enforceBounds() validates it first.
 */
public record LlmDecision(
        RecoveryAction action,
        String reasoning,
        double confidence,
        Integer discountPercent   // null unless action == OFFER_DISCOUNT
) {
}

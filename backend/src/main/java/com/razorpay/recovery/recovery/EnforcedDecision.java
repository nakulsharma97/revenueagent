package com.razorpay.recovery.recovery;

/**
 * Returned by RulesEngine.enforceBounds(). Contains the bounded LlmDecision
 * plus metadata about whether human sign-off is required.
 */
public record EnforcedDecision(
        LlmDecision decision,
        boolean requiresHumanSignoff,
        String signoffReason
) {
    /** Convenience: no signoff needed. */
    public static EnforcedDecision ok(LlmDecision decision) {
        return new EnforcedDecision(decision, false, null);
    }
}

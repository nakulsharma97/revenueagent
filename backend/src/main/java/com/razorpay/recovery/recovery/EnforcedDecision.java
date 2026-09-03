package com.razorpay.recovery.recovery;

/**
 * Returned by RulesEngine.enforceBounds(). Contains the bounded RecoveryDecision
 * plus metadata about whether human sign-off is required.
 */
public record EnforcedDecision(
        RecoveryDecision decision,
        boolean requiresHumanSignoff,
        String signoffReason
) {
    /** Convenience: no signoff needed. */
    public static EnforcedDecision ok(RecoveryDecision decision) {
        return new EnforcedDecision(decision, false, null);
    }
}

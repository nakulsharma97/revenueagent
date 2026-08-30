package com.razorpay.recovery.recovery;

/**
 * Pairs an EnforcedDecision (bounded LlmDecision + signoff metadata) with whether
 * the decision came from a live LLM call or the heuristic fallback.
 * Used internally by DecisionAgentService so the orchestrator can set
 * RecoveryAttempt.llmDriven, requiresHumanSignoff, and signoffReason accurately.
 */
public record DecisionResult(
        EnforcedDecision enforced,
        boolean llmDriven,
        boolean requiresHumanSignoff,
        String signoffReason
) {

    /** Convenience: the bounded LlmDecision. */
    public LlmDecision decision() { return enforced.decision(); }

    /** Legacy constructor — uses enforced's signoff info only (for tests that don't compute signoff separately). */
    public DecisionResult(EnforcedDecision enforced, boolean llmDriven) {
        this(enforced, llmDriven, enforced.requiresHumanSignoff(), enforced.signoffReason());
    }
}

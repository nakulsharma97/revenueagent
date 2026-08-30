package com.razorpay.recovery.recovery;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of trace steps capturing how the agent reached its decision.
 * Built incrementally as RulesEngine and DecisionAgentService run — never
 * reconstructed after the fact, so the trace reflects what actually happened.
 *
 * Stored as JSON text in RecoveryAttempt.decisionTrace (@Lob).
 */
public class DecisionTrace {

    private final List<Step> steps = new ArrayList<>();

    /** Append a step to the trace. */
    public void add(String step, String detail) {
        steps.add(new Step(step, detail));
    }

    public List<Step> getSteps() {
        return steps;
    }

    /** A single step in the decision trace. */
    public record Step(String step, String detail) {}
}

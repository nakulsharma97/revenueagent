package com.razorpay.recovery.intelligence;

import java.util.List;

/**
 * The full output of the Next-Best-Action engine for one case: the chosen action
 * (highest valid incremental net value), every alternative that was simulated, the
 * detected customer state, fatigue, confidence band and an ordered list of the top
 * decision factors for explainability.
 */
public record IntelligenceDecision(
        RecoveryState recoveryState,
        FatigueBand fatigueBand,
        double fatigueScore,
        double baselineProbability,
        ActionEvaluation chosen,
        List<ActionEvaluation> alternatives,
        List<String> topFactors,
        double confidence,
        DecisionConfidenceService.Policy automationPolicy,
        String reasoning,
        String customerMessage
) {

    /** Human-readable title, e.g. "10% Discount". */
    public String chosenDisplayName() {
        return chosen == null ? "NO_ACTION" : chosen.displayName();
    }

    /** Short explanation of the winner relative to the runner-up. */
    public String whyThisAction() {
        if (chosen == null) return "No intervention — natural recovery expected.";
        String head = "Selected " + chosen.displayName() + " — highest expected incremental net value ("
                + chosen.incrementalNetValue() + " over a " + String.format("%.0f", chosen.baselineProbability() * 100)
                + "% natural baseline).";
        if (alternatives == null || alternatives.isEmpty()) return head;
        ActionEvaluation second = null;
        for (ActionEvaluation a : alternatives) {
            if (a == chosen || a.action() == chosen.action()) continue;
            if (second == null || a.incrementalNetValue().compareTo(second.incrementalNetValue()) > 0) second = a;
        }
        if (second != null) {
            head += " Runner-up " + second.displayName() + " was worth " + second.incrementalNetValue()
                    + " (lift " + String.format("%+.0f", second.incrementalLift() * 100) + "pp).";
        }
        return head;
    }
}

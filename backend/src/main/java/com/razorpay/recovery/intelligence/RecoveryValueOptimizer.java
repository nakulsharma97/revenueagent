package com.razorpay.recovery.intelligence;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * The financial optimization layer of the engine. Decides which of several simulated
 * actions is economically best — not by success probability, but by expected net value
 * after intervention cost, expected discount cost and operational-risk penalty.
 *
 * <p>Example (the one in the product brief): an action recovering ₹9,000 at 90% with a
 * ₹2,000 discount is worth ₹7,000, while a no-frills action recovering ₹8,000 at 90%
 * is worth ₹7,980 — the optimizer prefers the latter even though the former has the
 * higher headline.</p>
 */
@Service
public class RecoveryValueOptimizer {

    /**
     * Picks the winner by incremental net value. Ties break toward the lower-risk option
     * — when two actions are worth the same, there is no revenue argument for taking the
     * riskier one, so don't.
     */
    public ActionEvaluation bestByIncrementalNetValue(java.util.List<ActionEvaluation> candidates) {
        ActionEvaluation best = null;
        for (ActionEvaluation e : candidates) {
            if (best == null) {
                best = e;
                continue;
            }
            int cmp = e.incrementalNetValue().compareTo(best.incrementalNetValue());
            if (cmp > 0) {
                best = e;
            } else if (cmp == 0 && e.riskScore() < best.riskScore()) {
                best = e;
            }
        }
        return best;
    }
}

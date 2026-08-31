package com.razorpay.recovery.recovery.execution;

import java.math.BigDecimal;

/**
 * Result returned by RecoveryActionExecutor.execute().
 * Encapsulates whether the action succeeded, how much was recovered,
 * and the intervention cost incurred.
 */
public record ExecutionResult(
    boolean success,
    BigDecimal amountRecovered,
    BigDecimal interventionCost,
    String message
) {
    /** Factory for a successful execution */
    public static ExecutionResult success(BigDecimal amountRecovered, BigDecimal cost) {
        return new ExecutionResult(true, amountRecovered, cost, "Action executed successfully");
    }

    /** Factory for a failed execution */
    public static ExecutionResult failure(String reason) {
        return new ExecutionResult(false, BigDecimal.ZERO, BigDecimal.ZERO, reason);
    }
}

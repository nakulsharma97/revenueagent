package com.razorpay.recovery.metrics;

import java.math.BigDecimal;

/**
 * What-if simulation result: projected metrics under hypothetical bounds,
 * plus the delta (simulated minus actual) so the UI can show the impact.
 */
public record SimulationResult(
        BatchMetrics simulated,
        BigDecimal deltaRevenue,
        BigDecimal deltaCost,
        BigDecimal deltaNet,
        long deltaRecoveredCount,
        long deltaAttempts,
        SimulationAssumptions assumptions
) {
    public record SimulationAssumptions(
            int maxRetries,
            int maxDiscountPercent,
            BigDecimal minAmountForDiscount,
            int retryCooldownMinutes
    ) {}
}

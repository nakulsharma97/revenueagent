package com.razorpay.recovery.metrics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record BatchMetrics(
        long totalAtRisk,
        long recoveredCount,
        BigDecimal revenueRecovered,
        BigDecimal interventionCost,
        BigDecimal netRecovered,
        double recoveryRatePercent,
        BigDecimal baselineNetRecovered,
        long baselineRecoveryCount,
        long paymentAtRisk,
        long checkoutAtRisk,
        long receivableAtRisk,
        long paymentRecovered,
        long checkoutRecovered,
        long receivableRecovered,
        Map<String, Object> bySource
) {
}

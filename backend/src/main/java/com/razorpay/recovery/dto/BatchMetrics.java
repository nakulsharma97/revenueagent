package com.razorpay.recovery.dto;

import java.math.BigDecimal;

public record BatchMetrics(
        long totalAtRisk,
        long recoveredCount,
        BigDecimal revenueRecovered,
        BigDecimal interventionCost,
        BigDecimal netRecovered,
        double recoveryRatePercent,
        BigDecimal baselineNetRecovered   // naive "retry once" comparison, for the demo chart
) {
}

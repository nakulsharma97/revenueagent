package com.razorpay.recovery.metrics;

import java.math.BigDecimal;
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
        Map<String, Object> bySource,
        double promiseKeepRate,
        /** % of recovered revenue that came from silent (no-customer-contact) attempts. */
        double silentRecoveryRate,
        /** Days Sales Outstanding for receivables — lower is better, <45 is healthy B2B. */
        double dso,
        /** Average days-overdue for currently OVERDUE receivables. */
        double avgDaysOverdue,
        /** Recovery breakdown by customer segment (STANDARD, HIGH_VALUE). */
        Map<String, Object> bySegment
) {
}

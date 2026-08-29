package com.razorpay.recovery.dto;

import java.math.BigDecimal;

/**
 * Per-action success rate breakdown — shows which interventions actually recover money.
 */
public record ActionBreakdown(
        String action,
        long totalAttempts,
        long successes,
        long failures,
        double successRate,
        BigDecimal amountRecovered,
        BigDecimal interventionCost
) {
}

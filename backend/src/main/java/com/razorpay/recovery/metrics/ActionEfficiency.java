package com.razorpay.recovery.metrics;

import java.math.BigDecimal;

/**
 * Per-action ROI metrics — shows which recovery actions give the best
 * return on intervention cost. recoveredPerRupeeSpent = totalRecovered / totalCost.
 * Zero-cost actions (like RETRY_NOW) have null recoveredPerRupeeSpent with a note.
 */
public record ActionEfficiency(
        String action,
        long totalAttempts,
        long successCount,
        BigDecimal totalRecovered,
        BigDecimal totalCost,
        BigDecimal recoveredPerRupeeSpent,   // null when totalCost is zero
        String costNote                       // "No direct cost" for zero-cost actions
) {
}

package com.razorpay.recovery.metrics;

/**
 * Funnel snapshot: status distribution across the recovery pipeline.
 */
public record FunnelData(
        long atRisk,
        long inRecovery,
        long recovered,
        long lost,
        long pendingAttempts,
        long skippedAttempts,
        long succeededAttempts,
        long failedAttempts
) {
}

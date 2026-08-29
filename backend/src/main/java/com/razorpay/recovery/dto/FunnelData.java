package com.razorpay.recovery.dto;

/**
 * Funnel snapshot: status distribution across the recovery pipeline.
 */
public record FunnelData(
        long atRisk,
        long inRecovery,
        long recovered,
        long lost,
        long pendingAttempts,
        long succeededAttempts,
        long failedAttempts
) {
}

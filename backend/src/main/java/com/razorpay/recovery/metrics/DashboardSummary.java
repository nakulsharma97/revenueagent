package com.razorpay.recovery.metrics;

import java.util.List;

/**
 * Combined dashboard data returned by GET /api/dashboard/summary.
 * Groups metrics, funnel, action breakdown, and efficiency into a single
 * response to reduce round-trips on initial page load.
 */
public record DashboardSummary(
        BatchMetrics metrics,
        FunnelData funnel,
        List<ActionBreakdown> actions,
        List<ActionEfficiency> efficiency
) {
}

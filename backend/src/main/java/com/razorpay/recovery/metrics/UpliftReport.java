package com.razorpay.recovery.metrics;

import java.util.Map;

/**
 * Uplift analysis report: compares control group (no intervention) recovery rate
 * against treatment group (agent intervention) recovery rate, broken down by
 * uplift segment.
 *
 * <p>The delta per segment = (treatment recovery rate) - (control recovery rate).
 * A large positive delta for PERSUADABLE proves intervention helps;
 * a small delta for SURE_THING proves discounts would be wasted there.</p>
 */
public record UpliftReport(
        long controlTotal,
        long controlRecovered,
        double controlRecoveryRate,
        Map<String, SegmentReport> bySegment
) {
    /**
     * Per-segment comparison: control vs treatment recovery rates + delta.
     */
    public record SegmentReport(
            String segment,
            long controlTotal,
            long controlRecovered,
            double controlRate,
            long treatmentTotal,
            long treatmentRecovered,
            double treatmentRate,
            double delta
    ) {}
}

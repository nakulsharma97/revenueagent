package com.razorpay.recovery.intelligence;

/** Human-readable band for a customer's recovery-fatigue score (0..1). */
public enum FatigueBand {
    /** Score < 0.30 — normal intervention allowed. */
    LOW,

    /** Score 0.30–0.59 — prefer low-cost interventions. */
    MODERATE,

    /** Score 0.60–0.84 — reduce communication; drop discounts. */
    HIGH,

    /** Score >= 0.85 — stop automated intervention; hand to a human. */
    SEVERE
}

package com.razorpay.recovery.intelligence;

/**
 * The detected recovery state of a customer/entity. Derived deterministically from
 * features (reliability, retry history, fatigue, amount, segment) by
 * {@link CustomerStateService} and used by the Next-Best-Action engine to shape
 * which interventions are appropriate.
 */
public enum RecoveryState {
    /** First observed failure, no prior interventions. */
    NEW_FAILURE,

    /** One prior attempt; still low risk, normal intervention appropriate. */
    SOFT_RISK,

    /** Two or more consecutive failures from the same customer. */
    REPEATED_FAILURE,

    /** High-value customer whose revenue is now genuinely at risk. */
    HIGH_VALUE_AT_RISK,

    /** Customer has been contacted several times — further nudges backfire. */
    RECOVERY_FATIGUE,

    /** High reliability + benign failure: customer will likely resolve it unaided. */
    LIKELY_TO_SELF_RECOVER,

    /** Reliability in the mid band where a well-priced offer changes behaviour. */
    DISCOUNT_SENSITIVE,

    /** High amount / unusual pattern — a human should look at the case. */
    HUMAN_ATTENTION_REQUIRED,

    /** Fatigue/opt-out threshold reached — automated interventions must stop. */
    STOP_INTERVENTION
}

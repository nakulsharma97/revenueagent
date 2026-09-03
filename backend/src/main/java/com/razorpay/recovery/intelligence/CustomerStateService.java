package com.razorpay.recovery.intelligence;

import java.math.BigDecimal;

/**
 * Detects the customer's recovery state from features. The state is displayed in the
 * decision ledger and influences which interventions the engine considers appropriate.
 * Deterministic — the same features always produce the same state.
 */
public class CustomerStateService {

    private static final BigDecimal HIGH_AMOUNT = new BigDecimal("100000");

    public RecoveryState detect(RecoveryCase c, double fatigueScore) {
        // 1. Stop conditions dominate.
        if (fatigueScore >= 0.85) return RecoveryState.STOP_INTERVENTION;
        if (fatigueScore >= 0.60) return RecoveryState.RECOVERY_FATIGUE;

        // 2. Very large amounts always deserve a human.
        if (c.amount() != null && c.amount().compareTo(HIGH_AMOUNT) >= 0) {
            return RecoveryState.HUMAN_ATTENTION_REQUIRED;
        }

        // 3. Retry budget is nearly spent — final disposition needs a human.
        if (c.retryCount() >= Math.max(1, c.maxRetries() - 1) && c.retryCount() > 0) {
            return RecoveryState.HUMAN_ATTENTION_REQUIRED;
        }

        // 4. High-value revenue under pressure.
        if (c.highValue() && (c.retryCount() >= 1 || c.terminal())) {
            return RecoveryState.HIGH_VALUE_AT_RISK;
        }

        // 5. Benign first failure on a reliable customer — they will likely self-serve.
        if (c.reliability() >= 0.78 && !c.terminal() && c.retryCount() == 0) {
            return RecoveryState.LIKELY_TO_SELF_RECOVER;
        }

        // 6. Mid-band reliability with a cause that incentives actually move.
        boolean priceOrMethodCause = c.failureMode() != null
                && (c.failureMode().equals("PRICE_HESITATION")
                || c.failureMode().equals("PAYMENT_METHOD_DECLINED")
                || c.failureMode().equals("CARD_EXPIRED")
                || c.failureMode().equals("INVALID_CVV")
                || c.failureMode().equals("VPA_INVALID"));
        if (c.reliability() >= 0.35 && c.reliability() <= 0.70
                && (c.retryCount() >= 1 || c.reminderCount() >= 1 || priceOrMethodCause)) {
            return RecoveryState.DISCOUNT_SENSITIVE;
        }

        // 7. Repeated failures.
        if (c.retryCount() >= 2) return RecoveryState.REPEATED_FAILURE;

        // 8. One prior touchpoint.
        if (c.retryCount() >= 1 || c.reminderCount() >= 1) return RecoveryState.SOFT_RISK;

        return RecoveryState.NEW_FAILURE;
    }
}

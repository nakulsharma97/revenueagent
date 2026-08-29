package com.razorpay.recovery.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mutable runtime configuration for the RulesEngine's hard bounds.
 * Refactored from @Value-injected fields in RulesEngine to allow live editing
 * via PUT /api/config/bounds without a server restart.
 *
 * Thread-safety: writes are volatile (single-writer from HTTP thread),
 * reads are volatile (multiple-reader from batch-processing threads).
 * AtomicReference would be overkill for three scalars that change rarely.
 */
@Getter
@Setter
@Component
public class BoundsConfig {

    @Value("${recovery.max-retries}")
    private volatile int maxRetries;

    @Value("${recovery.max-discount-percent}")
    private volatile int maxDiscountPercent;

    @Value("${recovery.min-amount-for-discount}")
    private volatile BigDecimal minAmountForDiscount;

    @Value("${recovery.retry-cooldown-minutes}")
    private volatile int retryCooldownMinutes;

    /** Snapshot of current config for the GET endpoint. */
    public record BoundsSnapshot(int maxRetries, int maxDiscountPercent, BigDecimal minAmountForDiscount, int retryCooldownMinutes) {}

    public BoundsSnapshot snapshot() {
        return new BoundsSnapshot(maxRetries, maxDiscountPercent, minAmountForDiscount, retryCooldownMinutes);
    }

    /** Apply values from a PUT request. Only non-null fields are updated. */
    public void apply(Integer maxRetries, Integer maxDiscountPercent, BigDecimal minAmountForDiscount, Integer retryCooldownMinutes) {
        if (maxRetries != null && maxRetries >= 1 && maxRetries <= 10) {
            this.maxRetries = maxRetries;
        }
        if (maxDiscountPercent != null && maxDiscountPercent >= 0 && maxDiscountPercent <= 50) {
            this.maxDiscountPercent = maxDiscountPercent;
        }
        if (minAmountForDiscount != null && minAmountForDiscount.compareTo(BigDecimal.ZERO) > 0) {
            this.minAmountForDiscount = minAmountForDiscount;
        }
        if (retryCooldownMinutes != null && retryCooldownMinutes >= 0 && retryCooldownMinutes <= 1440) {
            this.retryCooldownMinutes = retryCooldownMinutes;
        }
    }
}

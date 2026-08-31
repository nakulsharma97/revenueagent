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

    // 3 retries: RBI's Settlement Committee guidelines allow banks up to 3
    // automatic retry attempts before the transaction is flagged for manual
    // intervention — going beyond this risks duplicate debits.
    @Value("${recovery.max-retries}")
    private volatile int maxRetries;

    // 15% cap: at our lowest subscription tier (₹299/mo Starter), a 15% discount
    // costs ₹44.85 — recoverable if it prevents churn. At 20% the discount on
    // ₹299 becomes ₹59.80, which exceeds the payment-gateway fee margin on
    // that tier, making the recovery net-negative.
    @Value("${recovery.max-discount-percent}")
    private volatile int maxDiscountPercent;

    // ₹500 minimum: below this, even a 15% discount saves <₹75 — not enough
    // to offset the SMS/email cost (₹0.35 per SMS) plus gateway retry fees.
    // Our ₹299 Starter tier is explicitly excluded to preserve margin.
    @Value("${recovery.min-amount-for-discount}")
    private volatile BigDecimal minAmountForDiscount;

    // 60 minutes: average UPI retry window before the PSP marks the session
    // stale. Card network retries (Rupay/NPCI) also settle within 45-60 min.
    // Shorter cooldowns risk retrying a genuinely broken session; longer ones
    // let the customer forget and switch to a competitor.
    @Value("${recovery.retry-cooldown-minutes}")
    private volatile int retryCooldownMinutes;

    /** Language for customer-facing messages: "en" (English) or "hinglish". */
    private volatile String language = "en";

    // ── HIGH_VALUE segment bounds (wider limits for high-LTV customers) ──
    // A real deployment would derive these from LTV/tenure data.
    @Value("${recovery.hv.max-retries:5}")
    private volatile int hvMaxRetries;

    @Value("${recovery.hv.max-discount-percent:25}")
    private volatile int hvMaxDiscountPercent;

    @Value("${recovery.hv.min-amount-for-discount:500}")
    private volatile BigDecimal hvMinAmountForDiscount;

    /** Bounds for a given segment. */
    public record SegmentBounds(int maxRetries, int maxDiscountPercent, BigDecimal minAmountForDiscount) {}

    public SegmentBounds boundsFor(com.razorpay.recovery.customer.Customer.CustomerSegment segment) {
        if (segment == com.razorpay.recovery.customer.Customer.CustomerSegment.HIGH_VALUE) {
            return new SegmentBounds(hvMaxRetries, hvMaxDiscountPercent, hvMinAmountForDiscount);
        }
        return new SegmentBounds(maxRetries, maxDiscountPercent, minAmountForDiscount);
    }

    /** Snapshot of current config for the GET endpoint. */
    public record BoundsSnapshot(int maxRetries, int maxDiscountPercent, BigDecimal minAmountForDiscount, int retryCooldownMinutes, String language,
                                  int hvMaxRetries, int hvMaxDiscountPercent, BigDecimal hvMinAmountForDiscount) {}

    public BoundsSnapshot snapshot() {
        return new BoundsSnapshot(maxRetries, maxDiscountPercent, minAmountForDiscount, retryCooldownMinutes, language,
                hvMaxRetries, hvMaxDiscountPercent, hvMinAmountForDiscount);
    }

    /** Apply values from a PUT request. Only non-null fields are updated. */
    public void apply(Integer maxRetries, Integer maxDiscountPercent, BigDecimal minAmountForDiscount, Integer retryCooldownMinutes) {
        apply(maxRetries, maxDiscountPercent, minAmountForDiscount, retryCooldownMinutes, null, null, null, null);
    }

    public void apply(Integer maxRetries, Integer maxDiscountPercent, BigDecimal minAmountForDiscount, Integer retryCooldownMinutes,
                      Integer hvMaxRetries, Integer hvMaxDiscountPercent, BigDecimal hvMinAmountForDiscount, String lang) {
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
        if (lang != null && (lang.equals("en") || lang.equals("hinglish"))) {
            this.language = lang;
        }
        if (hvMaxRetries != null && hvMaxRetries >= 1 && hvMaxRetries <= 15) {
            this.hvMaxRetries = hvMaxRetries;
        }
        if (hvMaxDiscountPercent != null && hvMaxDiscountPercent >= 0 && hvMaxDiscountPercent <= 50) {
            this.hvMaxDiscountPercent = hvMaxDiscountPercent;
        }
        if (hvMinAmountForDiscount != null && hvMinAmountForDiscount.compareTo(BigDecimal.ZERO) > 0) {
            this.hvMinAmountForDiscount = hvMinAmountForDiscount;
        }
    }
}

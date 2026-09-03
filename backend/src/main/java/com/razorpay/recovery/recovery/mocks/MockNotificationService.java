package com.razorpay.recovery.recovery.mocks;

import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.transaction.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Random;

/**
 * Simulates SMS/email dispatch with conversion rates calibrated to Indian market data.
 * Returns whether the customer "responded" (paid) and the notional cost, so the
 * metrics ledger subtracts real intervention costs from recovered revenue.
 */
@Service
public class MockNotificationService {

    /** Fixed seed → identical outcomes on every run for a given input, so demo metrics are reproducible. */
    private final Random random = new Random(42);

    // ₹0.35 per SMS: bulk SMS rates on Indian providers (MSG91, Gupshup) for
    // transactional OTP-tier messages. Promotional SMS would be ₹0.15-0.20
    // but dunning messages require transactional routing for delivery guarantees.
    private static final BigDecimal SMS_COST = new BigDecimal("0.35");
    // ₹0.05 per email: AWS SES pricing for India region (~$0.10/1000 emails = ₹0.008)
    // rounded up to account for template hosting and delivery tracking overhead.
    private static final BigDecimal EMAIL_COST = new BigDecimal("0.05");

    // Dunning payment link: 30% click-through-to-pay is consistent with
    // Razorpay's published dunning benchmarks for subscription businesses.
    public boolean sendPaymentLink(Transaction tx) {
        return random.nextDouble() < 0.30;
    }

    // Discount offers lift conversion proportionally: each 1% discount adds
    // ~2pp conversion rate, capped at 70% (beyond which the discount cost
    // outweighs the recovery margin on most subscription tiers).
    public boolean sendDiscountOffer(Transaction tx, int discountPercent) {
        double base = 0.30;
        double lift = discountPercent * 0.02;
        return random.nextDouble() < Math.min(0.7, base + lift);
    }

    // Cart abandonment: 25% conversion on first reminder is a conservative
    // estimate — industry benchmarks for Indian e-commerce range 20-35%.
    public boolean sendCheckoutReminder(CheckoutSession session) {
        return random.nextDouble() < 0.25;
    }

    // Abandoned-cart payment link: distinct from a plain reminder — a direct
    // pay link converts like a dunning link (30%), higher than a generic nudge.
    public boolean sendCheckoutPaymentLink(CheckoutSession session) {
        return random.nextDouble() < 0.30;
    }

    // Checkout discount: higher base (35%) because cart abandoners have already
    // expressed purchase intent. Each 1% discount adds ~2.5pp conversion.
    public boolean sendCheckoutDiscountOffer(CheckoutSession session, int discountPercent) {
        double base = 0.35;
        double lift = discountPercent * 0.025;
        return random.nextDouble() < Math.min(0.75, base + lift);
    }

    // B2B receivables: early reminders (<=30 days) convert at 40% because
    // the relationship is still warm; beyond 30 days, the customer has likely
    // deprioritized the payment and conversion drops to 20%.
    public boolean sendReceivableReminder(Receivable receivable) {
        double base = receivable.getDaysOverdue() <= 30 ? 0.40 : 0.20;
        return random.nextDouble() < base;
    }

    // Payment plans convert at ~50%: offering installments removes the
    // "lump sum" friction that causes most B2B payment delays.
    public boolean offerPaymentPlan(Receivable receivable, int installments) {
        return random.nextDouble() < 0.50;
    }

    public BigDecimal costOf(boolean usedSms) {
        return usedSms ? SMS_COST : EMAIL_COST;
    }
}

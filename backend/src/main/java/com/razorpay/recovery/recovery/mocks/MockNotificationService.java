package com.razorpay.recovery.recovery.mocks;

import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.transaction.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Random;

/**
 * Stands in for SMS/email dispatch. Returns whether the customer "responded" (paid)
 * plus the notional cost of sending it, so the metrics ledger has a real number to subtract.
 */
@Service
public class MockNotificationService {

    private final Random random = new Random();
    private static final BigDecimal SMS_COST = new BigDecimal("0.35");
    private static final BigDecimal EMAIL_COST = new BigDecimal("0.05");

    public boolean sendPaymentLink(Transaction tx) {
        return random.nextDouble() < 0.30; // typical dunning-link click-through-to-pay rate
    }

    public boolean sendDiscountOffer(Transaction tx, int discountPercent) {
        double base = 0.30;
        double lift = discountPercent * 0.02;
        return random.nextDouble() < Math.min(0.7, base + lift);
    }

    // Checkout abandonment notifications
    public boolean sendCheckoutReminder(CheckoutSession session) {
        // ~25% conversion on cart abandonment reminders
        return random.nextDouble() < 0.25;
    }

    public boolean sendCheckoutDiscountOffer(CheckoutSession session, int discountPercent) {
        double base = 0.35;
        double lift = discountPercent * 0.025;
        return random.nextDouble() < Math.min(0.75, base + lift);
    }

    // B2B receivables notifications
    public boolean sendReceivableReminder(Receivable receivable) {
        // Payment reminder conversion varies by days overdue
        double base = receivable.getDaysOverdue() <= 30 ? 0.40 : 0.20;
        return random.nextDouble() < base;
    }

    public boolean offerPaymentPlan(Receivable receivable, int installments) {
        // Payment plans have higher conversion — ~50%
        return random.nextDouble() < 0.50;
    }

    public BigDecimal costOf(boolean usedSms) {
        return usedSms ? SMS_COST : EMAIL_COST;
    }
}

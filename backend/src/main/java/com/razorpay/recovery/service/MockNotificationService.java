package com.razorpay.recovery.service;

import com.razorpay.recovery.model.Transaction;
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
        double lift = discountPercent * 0.02; // bigger discount, higher conversion — with diminishing honesty
        return random.nextDouble() < Math.min(0.7, base + lift);
    }

    public BigDecimal costOf(boolean usedSms) {
        return usedSms ? SMS_COST : EMAIL_COST;
    }
}

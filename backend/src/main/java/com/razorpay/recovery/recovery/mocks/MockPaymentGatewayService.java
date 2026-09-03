package com.razorpay.recovery.recovery.mocks;

import com.razorpay.recovery.transaction.Transaction;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Simulates a payment gateway retry. Success probabilities are calibrated against
 * NPCI/RBI published retry-success data for Indian payment methods:
 * - Network errors retry well (75%) because the original session is still valid
 * - Bank downtime (60%) partially resolves within an hour
 * - Insufficient funds (35%) only succeeds if the customer tops up in time
 * - Terminal failures (2%) should almost never auto-recover
 */
@Service
public class MockPaymentGatewayService {

    /** Fixed seed → identical outcomes on every run for a given input, so demo metrics are reproducible. */
    private final Random random = new Random(42);

    public boolean attemptCharge(Transaction tx) {
        // Success probability per failure reason, calibrated to Indian payment retry data:
        double successProbability = switch (tx.getFailureReason()) {
            // 75%: transient network blip — most sessions survive a retry
            case NETWORK_ERROR -> 0.75;
            // 60%: bank server downtime is usually resolved within 30-60 minutes
            case BANK_SERVER_DOWN -> 0.6;
            // 35%: customer must top up or move money — unlikely within cooldown window
            case INSUFFICIENT_FUNDS -> 0.35;
            // 2%: terminal card failures rarely self-correct; requires new card/method
            case CARD_EXPIRED, INVALID_CVV, CARD_STOLEN_FLAG -> 0.02;
            // 40%: UPI PIN mismatch is usually a user error — retry often works
            case UPI_PIN_MISMATCH -> 0.40;
            // 55%: UPI timeout is transient — PSP routing recovers on retry
            case UPI_TIMEOUT -> 0.55;
            // 5%: invalid VPA means the handle doesn't exist — retry won't fix it
            case VPA_INVALID -> 0.05;
            // 30%: bank session expired mid-transaction — partial recovery rate
            case BANK_SESSION_EXPIRED -> 0.30;
        };
        // Reliable customers recover slightly more often (context the agent already sees).
        // The ±0.20 adjustment means a score of 0.9 adds +0.08, score of 0.1 subtracts 0.08.
        double adjusted = Math.min(0.95, successProbability
                + (tx.getSubscription().getCustomer().getPaymentReliabilityScore() - 0.5) * 0.2);
        return random.nextDouble() < adjusted;
    }
}

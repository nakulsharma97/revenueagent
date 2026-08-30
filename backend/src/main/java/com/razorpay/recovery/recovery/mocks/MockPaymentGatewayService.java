package com.razorpay.recovery.recovery.mocks;

import com.razorpay.recovery.transaction.Transaction;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Stands in for a real gateway retry call. Success probability is derived from the
 * failure reason so outcomes stay realistic without needing live payment infrastructure
 * for a buildathon demo.
 */
@Service
public class MockPaymentGatewayService {

    private final Random random = new Random();

    public boolean attemptCharge(Transaction tx) {
        double successProbability = switch (tx.getFailureReason()) {
            case NETWORK_ERROR -> 0.75;
            case BANK_SERVER_DOWN -> 0.6;
            case INSUFFICIENT_FUNDS -> 0.35;
            case CARD_EXPIRED, INVALID_CVV, CARD_STOLEN_FLAG -> 0.02;
        };
        // Reliable customers recover slightly more often (context the agent already sees).
        double adjusted = Math.min(0.95, successProbability
                + (tx.getSubscription().getCustomer().getPaymentReliabilityScore() - 0.5) * 0.2);
        return random.nextDouble() < adjusted;
    }
}

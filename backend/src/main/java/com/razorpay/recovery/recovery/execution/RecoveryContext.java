package com.razorpay.recovery.recovery.execution;

import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.recovery.LlmDecision;
import com.razorpay.recovery.recovery.RecoveryAttempt;
import com.razorpay.recovery.transaction.Transaction;

/**
 * Execution context passed to RecoveryActionExecutor implementations.
 * Contains all entity references needed to execute an action.
 */
public record RecoveryContext(
    Transaction transaction,
    CheckoutSession checkoutSession,
    Receivable receivable,
    LlmDecision decision,
    RecoveryAttempt attempt,
    String batchId
) {
    /** Convenience: get the source entity ID as a string */
    public String getSourceId() {
        if (transaction != null) return "TX#" + transaction.getId();
        if (checkoutSession != null) return "Checkout#" + checkoutSession.getId();
        if (receivable != null) return "Receivable#" + receivable.getId();
        return "Unknown";
    }

    /** Convenience: get the source type */
    public String getSourceType() {
        if (transaction != null) return "PAYMENT";
        if (checkoutSession != null) return "CHECKOUT";
        if (receivable != null) return "RECEIVABLE";
        return "UNKNOWN";
    }
}

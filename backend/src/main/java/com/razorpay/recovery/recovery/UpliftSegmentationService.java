package com.razorpay.recovery.recovery;

import com.razorpay.recovery.recovery.RecoveryAttempt.UpliftSegment;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import com.razorpay.recovery.customer.Customer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Classifies each revenue-at-risk entity into one of four causal-response segments
 * based on uplift modeling / CATE (Conditional Average Treatment Effect) estimation.
 *
 * <p>The classification is heuristic but explainable — each rule is documented inline
 * with its causal reasoning. In a production system, this would be a trained model
 * (e.g. a meta-learner or causal forest), but the segmentation logic is identical:
 * identify who benefits from intervention and who doesn't.</p>
 *
 * <p>Based on the uplift modeling literature (e.g. Rad & Ricci, "Learning Treatment
 * Assignments from Testimonials"; used in production by Criteo for ad targeting and
 * studied for churn/retention by Verbeke et al., "Why you should stop predicting
 * customer churn and start using uplift models").</p>
 */
@Service
public class UpliftSegmentationService {

    /**
     * Classify an entity into an uplift segment based on its features.
     *
     * @param reliabilityScore customer payment reliability (0.0–1.0)
     * @param failureReason    the failure reason (nullable for non-payment sources)
     * @param retryCount       how many previous attempts have been made
     * @param amount           transaction/checkout/receivable amount
     * @param isRetryable      whether the failure reason is retryable
     * @return the uplift segment
     */
    public UpliftSegment classify(double reliabilityScore, FailureReason failureReason,
                                   int retryCount, BigDecimal amount, boolean isRetryable) {

        // ── SURE_THING ──────────────────────────────────────────────
        // High reliability + transient/retryable failure.
        // Causal reasoning: these customers almost always resolve the issue themselves
        // (e.g. card tops up, bank server recovers). Spending a discount here is
        // wasted margin — the recovery would have happened anyway.
        // Evidence: reliabilityScore > 0.7 AND failure is retryable (network, timeout, etc.)
        if (reliabilityScore > 0.7 && isRetryable) {
            return UpliftSegment.SURE_THING;
        }

        // ── LOST_CAUSE ──────────────────────────────────────────────
        // Low reliability + hard/terminal decline.
        // Causal reasoning: no intervention meaningfully changes a terminal decline
        // (stolen card, invalid VPA, expired card). The customer must take action
        // themselves (new card, correct VPA). A discount or message won't fix this.
        // Evidence: reliabilityScore < 0.4 AND failure is non-retryable (terminal)
        if (reliabilityScore < 0.4 && !isRetryable) {
            return UpliftSegment.LOST_CAUSE;
        }

        // ── DO_NOT_DISTURB ──────────────────────────────────────────
        // Already tried once (retryCount > 0) AND small amount (< ₹1000).
        // Causal reasoning: a prior attempt already failed, and the revenue at stake
        // is low. Sending a discount or payment link on a ₹500–₹800 item risks
        // customer annoyance for negligible upside. The silent-first policy already
        // handles this — prefer silence over escalation.
        // Evidence: retryCount >= 1 AND amount < ₹1000
        if (retryCount > 0 && amount != null && amount.compareTo(new BigDecimal("1000")) < 0) {
            return UpliftSegment.DO_NOT_DISTURB;
        }

        // ── PERSUADABLE (default) ───────────────────────────────────
        // Everything else: this is the majority case where intervention genuinely
        // changes the outcome. A retry, discount, or payment link on a medium-to-high
        // value transaction with moderate reliability is exactly where the agent's
        // ROI is highest.
        return UpliftSegment.PERSUADABLE;
    }

    /**
     * Convenience overload for transactions — extracts features from the entity.
     */
    public UpliftSegment classify(Transaction tx) {
        double reliability = 0.5;
        if (tx.getSubscription() != null && tx.getSubscription().getCustomer() != null) {
            reliability = tx.getSubscription().getCustomer().getPaymentReliabilityScore();
        }
        boolean isRetryable = tx.getFailureReason() != null && tx.getFailureReason().isRetryable();
        return classify(reliability, tx.getFailureReason(), tx.getRetryCount(), tx.getAmount(), isRetryable);
    }

    /**
     * Convenience overload for checkout sessions.
     */
    public UpliftSegment classify(com.razorpay.recovery.checkout.CheckoutSession session) {
        // Checkout sessions don't have reliabilityScore directly — use a default of 0.5
        // A real deployment would look up the customer profile
        boolean isRetryable = session.getAbandonmentReason() != null && session.getAbandonmentReason().isRetryable();
        return classify(0.5, null, session.getReminderCount(), session.getCartAmount(), isRetryable);
    }

    /**
     * Convenience overload for receivables.
     */
    public UpliftSegment classify(com.razorpay.recovery.receivable.Receivable receivable) {
        // B2B receivables — use a default reliability of 0.5
        // A real deployment would use payment history data
        return classify(0.5, null, receivable.getReminderCount(), receivable.getInvoiceAmount(), true);
    }
}

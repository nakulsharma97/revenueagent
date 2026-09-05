package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.customer.Customer;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.recovery.RecoveryAttempt.SourceType;
import com.razorpay.recovery.transaction.Transaction;

import java.math.BigDecimal;
import java.util.List;

/**
 * The feature vector a Next-Best-Action decision is made from. Built from any of the
 * three revenue sources (payment failure / checkout abandonment / B2B receivable)
 * via the static factories below, so REST, SSE, startup and scheduler runs all see
 * the exact same case and therefore the exact same decision.
 *
 * @param sourceType           PAYMENT | CHECKOUT | RECEIVABLE
 * @param entityId             id of the source entity (null for ad-hoc simulator cases)
 * @param amount               revenue at risk
 * @param retryCount           prior silent/agent payment retries
 * @param reminderCount        prior reminders sent on this source
 * @param totalInterventions   prior interventions across all channels (fatigue input)
 * @param daysOverdue          receivable age (0 for payments/checkouts)
 * @param retryable            underlying failure is transient/retryable
 * @param terminal             underlying failure is terminal (retry cannot fix it)
 * @param reliability          customer payment reliability 0..1 (default 0.5)
 * @param highValue            customer segment is HIGH_VALUE
 * @param failureMode          normalized failure key, e.g. "INSUFFICIENT_FUNDS", "PRICE_HESITATION"
 * @param promiseBroken        receivable promise-to-pay was missed
 * @param eligible             the RulesEngine-eligible action set (hard constraint)
 * @param maxRetries           segment retry limit
 * @param maxDiscountPercent   segment discount ceiling
 * @param label                short human label for traces, e.g. "TX#12"
 * @param fatigueScore         recovery-fatigue score 0..1 (computed by RecoveryFatigueService)
 */
public record RecoveryCase(
        String sourceType,
        Long entityId,
        BigDecimal amount,
        int retryCount,
        int reminderCount,
        int totalInterventions,
        int daysOverdue,
        boolean retryable,
        boolean terminal,
        double reliability,
        boolean highValue,
        String failureMode,
        boolean promiseBroken,
        List<RecoveryAction> eligible,
        int maxRetries,
        int maxDiscountPercent,
        String label,
        double fatigueScore
) {

    /** Copy with an explicit fatigue score (computed before probability modelling). */
    public RecoveryCase withFatigue(double fatigueScore) {
        return new RecoveryCase(sourceType, entityId, amount, retryCount, reminderCount, totalInterventions,
                daysOverdue, retryable, terminal, reliability, highValue, failureMode, promiseBroken,
                eligible, maxRetries, maxDiscountPercent, label, fatigueScore);
    }

    /** Copy with an explicit customer reliability score (ad-hoc simulator requests). */
    public RecoveryCase withReliability(double reliabilityScore) {
        return new RecoveryCase(sourceType, entityId, amount, retryCount, reminderCount, totalInterventions,
                daysOverdue, retryable, terminal, reliabilityScore, highValue, failureMode, promiseBroken,
                eligible, maxRetries, maxDiscountPercent, label, fatigueScore);
    }

    public double fatigue() {
        return fatigueScore;
    }

    public static RecoveryCase fromPayment(Transaction tx, Customer.CustomerSegment segment,
                                           List<RecoveryAction> eligible, int maxRetries, int maxDiscountPercent) {
        double reliability = tx.getSubscription() != null && tx.getSubscription().getCustomer() != null
                ? tx.getSubscription().getCustomer().getPaymentReliabilityScore() : 0.5;
        boolean retryable = tx.getFailureReason() != null && tx.getFailureReason().isRetryable();
        return new RecoveryCase(
                SourceType.PAYMENT.name(),
                tx.getId(),
                tx.getAmount(),
                tx.getRetryCount(),
                0,
                tx.getRetryCount(),
                0,
                retryable,
                !retryable,
                reliability,
                segment == Customer.CustomerSegment.HIGH_VALUE,
                tx.getFailureReason() == null ? null : tx.getFailureReason().name(),
                false,
                eligible,
                maxRetries,
                maxDiscountPercent,
                "TX#" + tx.getId(),
                0.0
        );
    }

    public static RecoveryCase fromCheckout(CheckoutSession session, List<RecoveryAction> eligible,
                                            int maxRetries, int maxDiscountPercent) {
        boolean retryable = session.getAbandonmentReason() != null && session.getAbandonmentReason().isRetryable();
        return new RecoveryCase(
                SourceType.CHECKOUT.name(),
                session.getId(),
                session.getCartAmount(),
                0,
                session.getReminderCount(),
                session.getReminderCount(),
                0,
                retryable,
                false,
                0.5,
                false,
                session.getAbandonmentReason() == null ? null : session.getAbandonmentReason().name(),
                false,
                eligible,
                maxRetries,
                maxDiscountPercent,
                "Checkout#" + session.getId(),
                0.0
        );
    }

    public static RecoveryCase fromReceivable(Receivable receivable, List<RecoveryAction> eligible,
                                              int maxRetries, int maxDiscountPercent) {
        return new RecoveryCase(
                SourceType.RECEIVABLE.name(),
                receivable.getId(),
                receivable.getInvoiceAmount(),
                0,
                receivable.getReminderCount(),
                receivable.getReminderCount(),
                receivable.getDaysOverdue(),
                true,
                false,
                0.5,
                false,
                null,
                receivable.getPromiseStatus() == Receivable.PromiseStatus.BROKEN,
                eligible,
                maxRetries,
                maxDiscountPercent,
                "Receivable#" + receivable.getId(),
                0.0
        );
    }

    /** Money value as a plain double for probability math. */
    public double amountValue() {
        return amount == null ? 0 : amount.doubleValue();
    }
}

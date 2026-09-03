package com.razorpay.recovery.api;

import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.recovery.DecisionTrace;
import com.razorpay.recovery.recovery.RecoveryAttempt;
import com.razorpay.recovery.subscription.Subscription;
import com.razorpay.recovery.transaction.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Serialization-safe view of a {@link RecoveryAttempt} for API responses and the
 * SSE batch stream. Nested entities are flattened into plain records so the shape
 * matches what the frontend already consumes, with no lazy-loading at serialization
 * time (OSIV is disabled). Map only inside a service-layer transaction.
 */
public record AttemptDto(
        Long id,
        String sourceType,
        String actionTaken,
        String reasoning,
        double confidence,
        String outcome,
        BigDecimal amountRecovered,
        BigDecimal interventionCost,
        LocalDateTime executedAt,
        boolean llmDriven,
        boolean requiresHumanSignoff,
        String signoffReason,
        String signoffStatus,
        LocalDateTime signoffResolvedAt,
        String batchId,
        String customerMessage,
        boolean customerNotified,
        String upliftSegment,
        Integer discountPercent,
        String recoveryState,
        double fatigueScore,
        String decisionSource,
        String engineVersion,
        String fallbackReason,
        List<DecisionTrace.Step> decisionTrace,
        TxDto transaction,
        SessionDto checkoutSession,
        ReceivableDto receivable
) {
    public static AttemptDto from(RecoveryAttempt a) {
        return new AttemptDto(
                a.getId(),
                a.getSourceType() == null ? null : a.getSourceType().name(),
                a.getActionTaken() == null ? null : a.getActionTaken().name(),
                a.getReasoning(),
                a.getConfidence(),
                a.getOutcome() == null ? null : a.getOutcome().name(),
                a.getAmountRecovered(),
                a.getInterventionCost(),
                a.getExecutedAt(),
                a.isLlmDriven(),
                a.isRequiresHumanSignoff(),
                a.getSignoffReason(),
                a.getSignoffStatus() == null ? null : a.getSignoffStatus().name(),
                a.getSignoffResolvedAt(),
                a.getBatchId(),
                a.getCustomerMessage(),
                a.isCustomerNotified(),
                a.getUpliftSegment() == null ? null : a.getUpliftSegment().name(),
                a.getDiscountPercent(),
                a.getRecoveryState() == null ? null : a.getRecoveryState().name(),
                a.getFatigueScore(),
                a.getDecisionSource() == null ? null : a.getDecisionSource().name(),
                a.getEngineVersion(),
                a.getFallbackReason(),
                a.getDecisionTrace() == null ? List.of() : a.getDecisionTrace().getSteps(),
                a.getTransaction() == null ? null : TxDto.from(a.getTransaction()),
                a.getCheckoutSession() == null ? null : SessionDto.from(a.getCheckoutSession()),
                a.getReceivable() == null ? null : ReceivableDto.from(a.getReceivable())
        );
    }

    /** Nested transaction view (kept small — enough for the ledger table and case-file modal). */
    public record TxDto(
            Long id,
            BigDecimal amount,
            String status,
            String failureReason,
            int retryCount,
            String eventId,
            LocalDateTime createdAt,
            SubDto subscription
    ) {
        public static TxDto from(Transaction tx) {
            return new TxDto(
                    tx.getId(),
                    tx.getAmount(),
                    tx.getStatus() == null ? null : tx.getStatus().name(),
                    tx.getFailureReason() == null ? null : tx.getFailureReason().name(),
                    tx.getRetryCount(),
                    tx.getEventId(),
                    tx.getCreatedAt(),
                    tx.getSubscription() == null ? null : SubDto.from(tx.getSubscription())
            );
        }
    }

    public record SubDto(Long id, String planName, BigDecimal amount, String status, CustomerDto customer) {
        public static SubDto from(Subscription sub) {
            return new SubDto(
                    sub.getId(),
                    sub.getPlanName(),
                    sub.getAmount(),
                    sub.getStatus() == null ? null : sub.getStatus().name(),
                    sub.getCustomer() == null ? null : CustomerDto.from(sub.getCustomer())
            );
        }
    }

    public record CustomerDto(Long id, String name, String email, double paymentReliabilityScore, String customerSegment) {
        public static CustomerDto from(com.razorpay.recovery.customer.Customer c) {
            return new CustomerDto(
                    c.getId(),
                    c.getName(),
                    c.getEmail(),
                    c.getPaymentReliabilityScore(),
                    c.getCustomerSegment() == null ? null : c.getCustomerSegment().name()
            );
        }
    }

    /** Nested checkout-session view. */
    public record SessionDto(
            Long id,
            String customerId,
            String customerName,
            String customerEmail,
            BigDecimal cartAmount,
            String status,
            String abandonmentReason,
            int reminderCount,
            String eventId
    ) {
        public static SessionDto from(CheckoutSession s) {
            return new SessionDto(
                    s.getId(),
                    s.getCustomerId(),
                    s.getCustomerName(),
                    s.getCustomerEmail(),
                    s.getCartAmount(),
                    s.getStatus() == null ? null : s.getStatus().name(),
                    s.getAbandonmentReason() == null ? null : s.getAbandonmentReason().name(),
                    s.getReminderCount(),
                    s.getEventId()
            );
        }
    }
}
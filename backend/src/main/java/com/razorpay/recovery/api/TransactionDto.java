package com.razorpay.recovery.api;

import com.razorpay.recovery.subscription.Subscription;
import com.razorpay.recovery.transaction.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Flat, serialization-safe view of a {@link Transaction} for API responses.
 * Mapped inside a service-layer transaction, so no lazy entity access leaks
 * into Jackson (OSIV is disabled).
 */
public record TransactionDto(
        Long id,
        BigDecimal amount,
        String status,
        String failureReason,
        int retryCount,
        String eventId,
        LocalDateTime createdAt,
        String planName,
        String customerName,
        double paymentReliabilityScore,
        String customerSegment
) {
    public static TransactionDto from(Transaction tx) {
        String planName = null;
        String customerName = null;
        double reliability = 0.5;
        String segment = null;
        if (tx.getSubscription() != null) {
            Subscription sub = tx.getSubscription();
            planName = sub.getPlanName();
            if (sub.getCustomer() != null) {
                customerName = sub.getCustomer().getName();
                reliability = sub.getCustomer().getPaymentReliabilityScore();
                if (sub.getCustomer().getCustomerSegment() != null) {
                    segment = sub.getCustomer().getCustomerSegment().name();
                }
            }
        }
        return new TransactionDto(
                tx.getId(),
                tx.getAmount(),
                tx.getStatus() == null ? null : tx.getStatus().name(),
                tx.getFailureReason() == null ? null : tx.getFailureReason().name(),
                tx.getRetryCount(),
                tx.getEventId(),
                tx.getCreatedAt(),
                planName,
                customerName,
                reliability,
                segment
        );
    }
}
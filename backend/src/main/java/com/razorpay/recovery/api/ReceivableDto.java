package com.razorpay.recovery.api;

import com.razorpay.recovery.receivable.Receivable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Flat, serialization-safe view of a {@link Receivable} for API responses.
 * Mapped inside a service-layer transaction (OSIV is disabled).
 */
public record ReceivableDto(
        Long id,
        String businessCustomerId,
        String businessName,
        String contactEmail,
        BigDecimal invoiceAmount,
        String invoiceNumber,
        LocalDate dueDate,
        int daysOverdue,
        String status,
        int reminderCount,
        String promiseStatus,
        LocalDate promisedPaymentDate,
        String eventId
) {
    public static ReceivableDto from(Receivable r) {
        return new ReceivableDto(
                r.getId(),
                r.getBusinessCustomerId(),
                r.getBusinessName(),
                r.getContactEmail(),
                r.getInvoiceAmount(),
                r.getInvoiceNumber(),
                r.getDueDate(),
                r.getDaysOverdue(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getReminderCount(),
                r.getPromiseStatus() == null ? null : r.getPromiseStatus().name(),
                r.getPromisedPaymentDate(),
                r.getEventId()
        );
    }
}
package com.razorpay.recovery.transaction;

import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> findByStatusIn(List<TransactionStatus> statuses);

    /** Idempotency: an indexed lookup by webhook/payment event ID (no full-table scans). */
    boolean existsByEventId(String eventId);

    /** Idempotency: an indexed lookup by Razorpay payment ID. */
    Optional<Transaction> findByRazorpayPaymentId(String razorpayPaymentId);
}

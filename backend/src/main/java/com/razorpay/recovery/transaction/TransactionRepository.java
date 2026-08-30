package com.razorpay.recovery.transaction;

import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> findByStatusIn(List<TransactionStatus> statuses);
}

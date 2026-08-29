package com.razorpay.recovery.repository;

import com.razorpay.recovery.model.Transaction;
import com.razorpay.recovery.model.Transaction.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> findByStatusIn(List<TransactionStatus> statuses);
}

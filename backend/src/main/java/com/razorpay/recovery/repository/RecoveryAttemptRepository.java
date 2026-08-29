package com.razorpay.recovery.repository;

import com.razorpay.recovery.model.RecoveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryAttemptRepository extends JpaRepository<RecoveryAttempt, Long> {
    List<RecoveryAttempt> findByTransactionId(Long transactionId);
    List<RecoveryAttempt> findByRequiresHumanSignoffTrue();
    List<RecoveryAttempt> findByBatchId(String batchId);
}

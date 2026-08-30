package com.razorpay.recovery.recovery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryAttemptRepository extends JpaRepository<RecoveryAttempt, Long> {
    List<RecoveryAttempt> findByTransactionId(Long transactionId);
    List<RecoveryAttempt> findByRequiresHumanSignoffTrue();
    List<RecoveryAttempt> findByRequiresHumanSignoffTrueAndSignoffStatus(RecoveryAttempt.SignoffStatus status);
    List<RecoveryAttempt> findByBatchId(String batchId);
}

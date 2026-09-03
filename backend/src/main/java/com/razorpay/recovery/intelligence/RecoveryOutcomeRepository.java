package com.razorpay.recovery.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryOutcomeRepository extends JpaRepository<RecoveryOutcome, Long> {

    List<RecoveryOutcome> findByAction(com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction action);

    long countBySuccessTrue();
}

package com.razorpay.recovery.recovery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface RecoveryAttemptRepository extends JpaRepository<RecoveryAttempt, Long> {
    List<RecoveryAttempt> findByTransactionId(Long transactionId);
    List<RecoveryAttempt> findByRequiresHumanSignoffTrue();
    List<RecoveryAttempt> findByRequiresHumanSignoffTrueAndSignoffStatus(RecoveryAttempt.SignoffStatus status);
    List<RecoveryAttempt> findByBatchId(String batchId);

    /** Idempotency check: has this transaction already been successfully recovered? */
    boolean existsByTransactionEventIdAndOutcome(String eventId, RecoveryAttempt.AttemptOutcome outcome);
    /** Idempotency check: has this checkout session already been successfully recovered? */
    boolean existsByCheckoutSessionEventIdAndOutcome(String eventId, RecoveryAttempt.AttemptOutcome outcome);
    /** Idempotency check: has this receivable already been successfully recovered? */
    boolean existsByReceivableEventIdAndOutcome(String eventId, RecoveryAttempt.AttemptOutcome outcome);

    /** Bulk preload: get all transaction eventIds that have a SUCCESS attempt (for idempotency check). */
    @Query("SELECT t.eventId FROM RecoveryAttempt a JOIN a.transaction t WHERE a.outcome = 'SUCCESS' AND t.eventId IS NOT NULL")
    Set<String> findSuccessfulTransactionEventIds();

    /** Bulk preload: get all checkout session eventIds that have a SUCCESS attempt. */
    @Query("SELECT cs.eventId FROM RecoveryAttempt a JOIN a.checkoutSession cs WHERE a.outcome = 'SUCCESS' AND cs.eventId IS NOT NULL")
    Set<String> findSuccessfulCheckoutEventIds();

    /** Bulk preload: get all receivable eventIds that have a SUCCESS attempt. */
    @Query("SELECT r.eventId FROM RecoveryAttempt a JOIN a.receivable r WHERE a.outcome = 'SUCCESS' AND r.eventId IS NOT NULL")
    Set<String> findSuccessfulReceivableEventIds();
}

package com.razorpay.recovery.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecoveryAnomalyRepository extends JpaRepository<RecoveryAnomaly, Long> {

    List<RecoveryAnomaly> findByStatusOrderByCreatedAtDesc(RecoveryAnomaly.Status status);

    List<RecoveryAnomaly> findTop50ByOrderByCreatedAtDesc();

    Optional<RecoveryAnomaly> findFirstByTypeAndSourceTypeAndSourceEntityIdAndStatus(
            String type, String sourceType, Long sourceEntityId, RecoveryAnomaly.Status status);
}

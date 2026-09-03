package com.razorpay.recovery.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryExperimentRepository extends JpaRepository<RecoveryExperiment, Long> {

    List<RecoveryExperiment> findByStatusOrderByCreatedAtDesc(RecoveryExperiment.Status status);

    List<RecoveryExperiment> findAllByOrderByCreatedAtDesc();
}

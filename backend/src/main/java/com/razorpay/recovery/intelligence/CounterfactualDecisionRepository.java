package com.razorpay.recovery.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CounterfactualDecisionRepository extends JpaRepository<CounterfactualDecision, Long> {

    List<CounterfactualDecision> findBySourceTypeAndSourceEntityIdOrderByCreatedAtDesc(
            String sourceType, Long sourceEntityId);

    List<CounterfactualDecision> findTop100ByOrderByCreatedAtDesc();

    List<CounterfactualDecision> findByBatchIdOrderByCreatedAtAsc(String batchId);
}

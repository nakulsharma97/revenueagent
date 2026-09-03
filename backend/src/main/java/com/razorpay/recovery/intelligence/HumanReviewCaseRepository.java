package com.razorpay.recovery.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HumanReviewCaseRepository extends JpaRepository<HumanReviewCase, Long> {

    List<HumanReviewCase> findByStatusOrderByCreatedAtAsc(HumanReviewCase.Status status);

    List<HumanReviewCase> findByStatusOrderByPriorityDescCreatedAtAsc(HumanReviewCase.Status status);

    List<HumanReviewCase> findTop50ByOrderByCreatedAtDesc();

    Optional<HumanReviewCase> findFirstBySourceTypeAndSourceEntityIdAndStatus(
            String sourceType, Long sourceEntityId, HumanReviewCase.Status status);

    Optional<HumanReviewCase> findFirstByAttemptIdAndStatus(Long attemptId, HumanReviewCase.Status status);
}

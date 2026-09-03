package com.razorpay.recovery.intelligence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A case routed to the Human Review Queue. Enters the queue when: the structured engine's
 * confidence is below the auto-execution floor, the recovery attempt is flagged for
 * human sign-off (retry budget nearly spent / discount ceiling), or an anomaly of HIGH
 * or CRITICAL severity was detected. A reviewer can approve the AI recommendation,
 * override it with a different action, or reject it.
 */
@Entity
@Table(name = "human_review_cases", indexes = {
        @Index(name = "idx_review_status", columnList = "status"),
        @Index(name = "idx_review_source", columnList = "sourceType, sourceEntityId")
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class HumanReviewCase {

    public enum Status { PENDING, APPROVED, OVERRIDDEN, REJECTED }

    public enum Priority { NORMAL, HIGH, CRITICAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String sourceType;

    private Long sourceEntityId;

    /** The attempt this case reviews (when created from the live pipeline). */
    private Long attemptId;

    private BigDecimal amount;

    /** The engine's recommendation that is under review. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RecoveryAction aiRecommendation;

    private Integer aiDiscountPercent;

    private double aiConfidence;

    /** Why this case was routed to a human. */
    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority priority = Priority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** The action the human chose (for APPROVE: same as AI recommendation). */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RecoveryAction humanDecision;

    @Column(length = 1000)
    private String overrideReason;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public static HumanReviewCase pending(String sourceType, Long entityId, Long attemptId, BigDecimal amount,
                                          RecoveryAction aiRecommendation, Integer discountPercent,
                                          double aiConfidence, String reason, Priority priority) {
        HumanReviewCase c = new HumanReviewCase();
        c.setSourceType(sourceType);
        c.setSourceEntityId(entityId);
        c.setAttemptId(attemptId);
        c.setAmount(amount);
        c.setAiRecommendation(aiRecommendation);
        c.setAiDiscountPercent(discountPercent);
        c.setAiConfidence(aiConfidence);
        c.setReason(reason);
        c.setPriority(priority == null ? Priority.NORMAL : priority);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }
}

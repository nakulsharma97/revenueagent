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
 * One observed outcome after an action executed — the training record of the feedback
 * loop. Aggregated by the Action Performance Lab; shaped so a future ML model can learn
 * per-action success probabilities from real (or mock-real) results.
 */
@Entity
@Table(name = "recovery_outcomes", indexes = {
        @Index(name = "idx_outcome_action", columnList = "action"),
        @Index(name = "idx_outcome_source", columnList = "sourceType, sourceEntityId")
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RecoveryOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long attemptId;

    @Column(length = 20)
    private String sourceType;

    private Long sourceEntityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecoveryAction action;

    /** Whether the action converted into payment. */
    private boolean success;

    private BigDecimal amountRecovered = BigDecimal.ZERO;
    private BigDecimal interventionCost = BigDecimal.ZERO;
    private BigDecimal netValue = BigDecimal.ZERO;

    private double fatigueBefore;
    private String recoveryState;
    private String customerSegment;

    /** Time from the entity's failure/abandonment to payment, in hours (null when unsuccessful). */
    private Double timeToRecoveryHours;

    private LocalDateTime createdAt;

    public static RecoveryOutcome from(com.razorpay.recovery.recovery.RecoveryAttempt attempt) {
        RecoveryOutcome o = new RecoveryOutcome();
        o.setAttemptId(attempt.getId());
        String source = attempt.getSourceType() == null ? null : attempt.getSourceType().name();
        o.setSourceType(source);
        Long entityId = attempt.getTransaction() != null ? attempt.getTransaction().getId()
                : attempt.getCheckoutSession() != null ? attempt.getCheckoutSession().getId()
                : attempt.getReceivable() != null ? attempt.getReceivable().getId() : null;
        o.setSourceEntityId(entityId);
        o.setAction(attempt.getActionTaken());
        boolean success = attempt.getOutcome() == com.razorpay.recovery.recovery.RecoveryAttempt.AttemptOutcome.SUCCESS;
        o.setSuccess(success);
        o.setAmountRecovered(attempt.getAmountRecovered() == null ? BigDecimal.ZERO : attempt.getAmountRecovered());
        o.setInterventionCost(attempt.getInterventionCost() == null ? BigDecimal.ZERO : attempt.getInterventionCost());
        o.setNetValue(o.getAmountRecovered().subtract(o.getInterventionCost()));
        o.setFatigueBefore(attempt.getFatigueScore());
        o.setRecoveryState(attempt.getRecoveryState() == null ? null : attempt.getRecoveryState().name());
        String seg = null;
        if (attempt.getTransaction() != null && attempt.getTransaction().getSubscription() != null
                && attempt.getTransaction().getSubscription().getCustomer() != null
                && attempt.getTransaction().getSubscription().getCustomer().getCustomerSegment() != null) {
            seg = attempt.getTransaction().getSubscription().getCustomer().getCustomerSegment().name();
        }
        o.setCustomerSegment(seg);
        if (success && attempt.getExecutedAt() != null) {
            var created = attempt.getTransaction() != null ? attempt.getTransaction().getCreatedAt()
                    : null;
            if (created != null) {
                o.setTimeToRecoveryHours(java.time.Duration.between(created, attempt.getExecutedAt()).toMinutes() / 60.0);
            }
        }
        o.setCreatedAt(attempt.getExecutedAt() == null ? LocalDateTime.now() : attempt.getExecutedAt());
        return o;
    }
}

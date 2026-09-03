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
 * One persisted counterfactual simulation row: what WOULD have happened for this entity
 * if a given candidate action had been chosen. The selected row is flagged so the UI
 * can render the counterfactual bar chart (selected action vs alternatives).
 */
@Entity
@Table(name = "counterfactual_decisions", indexes = {
        @Index(name = "idx_cf_source", columnList = "sourceType, sourceEntityId"),
        @Index(name = "idx_cf_batch", columnList = "batchId")
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CounterfactualDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String sourceType;

    private Long sourceEntityId;

    /** Batch that produced this simulation (null for ad-hoc simulator runs). */
    @Column(length = 36)
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecoveryAction action;

    private Integer discountPercent;

    private BigDecimal amount;

    private double baselineProbability;
    private double successProbability;
    private double incrementalLift;
    private double riskScore;

    private BigDecimal interventionCost = BigDecimal.ZERO;
    private BigDecimal discountCost = BigDecimal.ZERO;
    private BigDecimal riskPenalty = BigDecimal.ZERO;
    private BigDecimal expectedNetValue = BigDecimal.ZERO;
    private BigDecimal incrementalNetValue = BigDecimal.ZERO;

    /** True for the action the engine actually selected for this case. */
    private boolean selected;

    @Column(length = 600)
    private String reasoning;

    private LocalDateTime createdAt;

    public static CounterfactualDecision from(ActionEvaluation e, String sourceType, Long entityId,
                                              String batchId, BigDecimal amount, boolean selected) {
        CounterfactualDecision d = new CounterfactualDecision();
        d.setSourceType(sourceType);
        d.setSourceEntityId(entityId);
        d.setBatchId(batchId);
        d.setAction(e.action());
        d.setDiscountPercent(e.discountPercent());
        d.setAmount(amount);
        d.setBaselineProbability(e.baselineProbability());
        d.setSuccessProbability(e.successProbability());
        d.setIncrementalLift(e.incrementalLift());
        d.setRiskScore(e.riskScore());
        d.setInterventionCost(e.interventionCost());
        d.setDiscountCost(e.discountCost());
        d.setRiskPenalty(e.riskPenalty());
        d.setExpectedNetValue(e.expectedNetValue());
        d.setIncrementalNetValue(e.incrementalNetValue());
        d.setSelected(selected);
        d.setReasoning(e.reasoning());
        d.setCreatedAt(LocalDateTime.now());
        return d;
    }
}

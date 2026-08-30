package com.razorpay.recovery.recovery;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.receivable.Receivable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.receivable.Receivable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_attempts")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RecoveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which revenue source this attempt targets. */
    @Enumerated(EnumType.STRING)
    private SourceType sourceType = SourceType.PAYMENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_session_id")
    private CheckoutSession checkoutSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receivable_id")
    private Receivable receivable;

    @Enumerated(EnumType.STRING)
    private RecoveryAction actionTaken;

    /** The agent's natural-language justification — shown in the UI for explainability. */
    @Column(length = 1000)
    private String reasoning;

    private double confidence;

    @Enumerated(EnumType.STRING)
    private AttemptOutcome outcome;

    private BigDecimal amountRecovered = BigDecimal.ZERO;
    private BigDecimal interventionCost = BigDecimal.ZERO;

    private LocalDateTime executedAt;

    /** True when a live LLM call produced the decision; false when the rules-only fallback did. */
    private boolean llmDriven;

    /** True when this attempt requires human review before execution (per the brief's bounded-workflow rule). */
    private boolean requiresHumanSignoff;

    /** Explains why sign-off is required (e.g. "LLM proposed 20% discount, capped to 15%" or "3rd consecutive failure"). */
    @Column(length = 500)
    private String signoffReason;

    /** Groups all attempts from one batch run for per-batch metrics. */
    @Column(length = 36)
    private String batchId;

    public enum SourceType {
        PAYMENT, CHECKOUT, RECEIVABLE
    }

    public enum RecoveryAction {
        RETRY_NOW, RETRY_SCHEDULED, SEND_PAYMENT_LINK, OFFER_DISCOUNT,
        ESCALATE_TO_HUMAN, ABANDON,
        CHECKOUT_REMINDER, OFFER_PAYMENT_PLAN, SEND_REMINDER
    }

    public enum AttemptOutcome {
        PENDING, SUCCESS, FAILED
    }
}

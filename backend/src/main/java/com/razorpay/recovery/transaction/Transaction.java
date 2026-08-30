package com.razorpay.recovery.transaction;
import com.razorpay.recovery.subscription.Subscription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    private FailureReason failureReason;

    private int retryCount = 0;

    private LocalDateTime createdAt;
    private LocalDateTime lastAttemptAt;

    public enum TransactionStatus {
        AT_RISK, IN_RECOVERY, RECOVERED, LOST
    }

    /** Mirrors real gateway decline codes so the demo reads as production-realistic. */
    public enum FailureReason {
        INSUFFICIENT_FUNDS(true),
        NETWORK_ERROR(true),
        BANK_SERVER_DOWN(true),
        CARD_EXPIRED(false),
        CARD_STOLEN_FLAG(false),
        INVALID_CVV(false);

        private final boolean retryable;

        FailureReason(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}

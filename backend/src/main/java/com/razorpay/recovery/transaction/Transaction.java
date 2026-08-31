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

    /** Razorpay payment ID (e.g. "pay_XXX") — set when ingested from a webhook. Null for seeded data. */
    @Column(length = 64)
    private String razorpayPaymentId;

    /**
     * Idempotency key — unique externally-meaningful identifier (e.g. payment/webhook event ID).
     * The UNIQUE database constraint on this field is the guarantee-of-last-resort against
     * duplicate processing; the orchestrator also checks for an existing SUCCESS attempt
     * before executing any action.
     */
    @Column(unique = true, length = 128)
    private String eventId;

    /** True for entities in the held-out evaluation split (never used by the agent). */
    private boolean isHeldOut;

    private LocalDateTime createdAt;
    private LocalDateTime lastAttemptAt;

    public enum TransactionStatus {
        AT_RISK, IN_RECOVERY, RECOVERED, LOST
    }

    /**
     * Failure codes modeled on Indian payment rails — UPI (NPCI), cards (Visa/Mastercard/Rupay),
     * and netbanking. UPI dominates Indian digital payments (~70% of volume per NPCI reports),
     * so the enum is weighted toward UPI-specific failures rather than a generic card-only list.
     */
    public enum FailureReason {
        // ── Card failures (Visa/Mastercard/Rupay decline codes) ──
        INSUFFICIENT_FUNDS(true),   // Indian cards: most common decline, retryable if customer tops up
        NETWORK_ERROR(true),        // Transient ISP/PSP routing failure — session survives retry
        BANK_SERVER_DOWN(true),     // Issuer bank maintenance window — usually resolves within 30 min
        CARD_EXPIRED(false),        // Terminal: card must be replaced, retry won't help
        CARD_STOLEN_FLAG(false),    // Terminal: issuer has blocked the card permanently
        INVALID_CVV(false),         // Terminal: customer must re-enter CVV on a new attempt

        // ── UPI failures (NPCI UPI decline codes) ──
        // UPI is ~70% of Indian digital payment volume (NPCI FY2025 data).
        // These failures are the majority of what a real Indian recovery agent would see.
        UPI_PIN_MISMATCH(true),     // Customer entered wrong UPI PIN — often succeeds on retry
        UPI_TIMEOUT(true),          // PSP (Google Pay/PhonePe/Paytm) routing timeout — transient
        VPA_INVALID(false),         // Virtual Payment Address doesn't exist — terminal, won't retry

        // ── Netbanking failures ──
        BANK_SESSION_EXPIRED(true); // NB session timed out mid-transaction — retry within window

        private final boolean retryable;

        FailureReason(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}

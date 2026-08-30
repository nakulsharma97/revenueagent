package com.razorpay.recovery.checkout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "checkout_sessions")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CheckoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerId;
    private String customerName;
    private String customerEmail;

    private BigDecimal cartAmount;

    private LocalDateTime startedAt;
    private LocalDateTime abandonedAt;

    @Enumerated(EnumType.STRING)
    private CheckoutStatus status;

    @Enumerated(EnumType.STRING)
    private AbandonmentReason abandonmentReason;

    private int reminderCount = 0;

    public enum CheckoutStatus {
        IN_PROGRESS, ABANDONED, RECOVERED, LOST
    }

    public enum AbandonmentReason {
        PRICE_HESITATION(true),
        PAYMENT_METHOD_DECLINED(true),
        DISTRACTED_NO_COMPLETION(true),
        TECHNICAL_ERROR(true);

        private final boolean retryable;

        AbandonmentReason(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}

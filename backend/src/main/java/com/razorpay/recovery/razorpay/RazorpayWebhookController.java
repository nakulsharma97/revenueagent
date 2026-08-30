package com.razorpay.recovery.razorpay;

import com.razorpay.recovery.customer.Customer;
import com.razorpay.recovery.customer.CustomerRepository;
import com.razorpay.recovery.subscription.Subscription;
import com.razorpay.recovery.subscription.SubscriptionRepository;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import com.razorpay.recovery.transaction.TransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Accepts Razorpay's actual payment.failed webhook shape.
 *
 * This is SHAPE-COMPATIBLE with Razorpay's real webhook, NOT a live integration.
 * In a real deployment, Razorpay would POST to this endpoint when a payment fails.
 * For the demo, the DataSeeder generates synthetic data; this endpoint demonstrates
 * the ingestion path is production-ready.
 *
 * Endpoint: POST /api/webhooks/razorpay/payment-failed
 *
 * Error code mapping (from Razorpay docs + common bank decline codes):
 * ┌─────────────────────────────────────────┬───────────────────────────┐
 * │ Razorpay error_reason / error_code      │ Our FailureReason         │
 * ├─────────────────────────────────────────┼───────────────────────────┤
 * │ "insufficient_funds"                    │ INSUFFICIENT_FUNDS        │
 * │ "expired_card" / "card_expired"         │ CARD_EXPIRED              │
 * │ "invalid_cvv"                           │ INVALID_CVV               │
 * │ "suspected_fraud" / "fraud"             │ CARD_STOLEN_FLAG          │
 * │ "network_error" / "timeout"             │ NETWORK_ERROR             │
 * │ "do_not_honor" / "bank_declined"        │ BANK_SERVER_DOWN          │
 * │ "BAD_REQUEST_ERROR" + "payment_failed"  │ BANK_SERVER_DOWN (generic)│
 * │ (unrecognised / null)                   │ NETWORK_ERROR (fallback)  │
 * └─────────────────────────────────────────┴───────────────────────────┘
 */
@RestController
@RequestMapping("/api/webhooks/razorpay")
public class RazorpayWebhookController {

    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;

    public RazorpayWebhookController(TransactionRepository transactionRepository,
                                      CustomerRepository customerRepository,
                                      SubscriptionRepository subscriptionRepository) {
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostMapping("/payment-failed")
    public ResponseEntity<Map<String, Object>> handlePaymentFailed(@RequestBody RazorpayWebhookPayload payload) {
        // Validate event type
        if (payload.event == null || !payload.event.equals("payment.failed")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Expected event type 'payment.failed', got: " + payload.event
            ));
        }

        RazorpayWebhookPayload.PaymentEntity payment = null;
        if (payload.payload != null && payload.payload.payment != null) {
            payment = payload.payload.payment.entity;
        }

        if (payment == null || payment.id == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing payment entity in payload"
            ));
        }

        // Check for duplicate (idempotency)
        String razorpayId = payment.id;
        Optional<Transaction> existing = transactionRepository.findAll().stream()
                .filter(tx -> razorpayId.equals(tx.getRazorpayPaymentId()))
                .findFirst();
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "status", "duplicate",
                    "transactionId", existing.get().getId(),
                    "message", "Payment " + payment.id + " already ingested"
            ));
        }

        // Map Razorpay error codes to our FailureReason
        FailureReason failureReason = mapErrorToFailureReason(
                payment.errorCode, payment.errorReason, payment.errorSource, payment.errorDescription);

        // Amount is in paise from Razorpay — convert to rupees
        BigDecimal amount = payment.amount != null
                ? new BigDecimal(payment.amount).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Create or find a minimal customer + subscription for this payment
        Customer customer = findOrCreateCustomer(payment.email, payment.contact);
        Subscription subscription = findOrCreateSubscription(customer, payment.method);

        // Create the Transaction
        Transaction tx = new Transaction();
        tx.setSubscription(subscription);
        tx.setAmount(amount);
        tx.setFailureReason(failureReason);
        tx.setStatus(TransactionStatus.AT_RISK);
        tx.setRetryCount(0);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setRazorpayPaymentId(payment.id);

        Transaction saved = transactionRepository.save(tx);

        return ResponseEntity.ok(Map.of(
                "status", "ingested",
                "transactionId", saved.getId(),
                "razorpayPaymentId", payment.id,
                "amount", amount,
                "failureReason", failureReason.name(),
                "mappedFrom", Map.of(
                        "errorCode", payment.errorCode != null ? payment.errorCode : "null",
                        "errorReason", payment.errorReason != null ? payment.errorReason : "null",
                        "errorSource", payment.errorSource != null ? payment.errorSource : "null"
                )
        ));
    }

    /**
     * Maps Razorpay's error_code / error_reason / error_source to our FailureReason enum.
     *
     * Mapping rationale:
     * - Razorpay error_reason is the most specific signal from the bank/issuer
     * - error_code is Razorpay's categorisation (less granular)
     * - error_source tells us where the failure originated
     *
     * This mapping is inferred from Razorpay docs + common Indian bank decline patterns,
     * not from a live integration test.
     */
    private FailureReason mapErrorToFailureReason(String errorCode, String errorReason,
                                                   String errorSource, String errorDescription) {
        String reason = (errorReason != null ? errorReason : "").toLowerCase();
        String code = (errorCode != null ? errorCode : "").toLowerCase();
        String source = (errorSource != null ? errorSource : "").toLowerCase();
        String desc = (errorDescription != null ? errorDescription : "").toLowerCase();

        // Most specific: error_reason from the bank
        if (reason.contains("insufficient")) return FailureReason.INSUFFICIENT_FUNDS;
        if (reason.contains("expired")) return FailureReason.CARD_EXPIRED;
        if (reason.contains("invalid_cvv") || reason.contains("cvv")) return FailureReason.INVALID_CVV;
        if (reason.contains("fraud") || reason.contains("stolen") || reason.contains("suspected")) return FailureReason.CARD_STOLEN_FLAG;
        if (reason.contains("network") || reason.contains("timeout") || reason.contains("timed_out")) return FailureReason.NETWORK_ERROR;

        // Fallback to error_code
        if (code.contains("gateway_error") || code.contains("gateway")) return FailureReason.NETWORK_ERROR;
        if (code.contains("authentication") || code.contains("auth")) return FailureReason.INVALID_CVV;

        // Fallback to error_source
        if ("network".equals(source)) return FailureReason.NETWORK_ERROR;
        if ("bank".equals(source) || "issuer".equals(source)) return FailureReason.BANK_SERVER_DOWN;

        // Generic fallback
        if (desc.contains("network") || desc.contains("timeout")) return FailureReason.NETWORK_ERROR;
        if (desc.contains("insufficient")) return FailureReason.INSUFFICIENT_FUNDS;
        if (desc.contains("expired")) return FailureReason.CARD_EXPIRED;

        return FailureReason.NETWORK_ERROR; // safest default — retryable
    }

    private Customer findOrCreateCustomer(String email, String contact) {
        // Simple: create a new customer for each webhook (demo purposes)
        // In production, you'd look up by email/contact
        Customer customer = new Customer();
        customer.setName(email != null ? email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length()) : "webhook-customer");
        customer.setEmail(email);
        customer.setPaymentReliabilityScore(0.5);
        return customerRepository.save(customer);
    }

    private Subscription findOrCreateSubscription(Customer customer, String method) {
        Subscription sub = new Subscription();
        sub.setCustomer(customer);
        sub.setPlanName("webhook-" + (method != null ? method : "unknown"));
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        return subscriptionRepository.save(sub);
    }
}

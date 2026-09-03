package com.razorpay.recovery.razorpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Shape-compatible with Razorpay's actual payment.failed webhook payload.
 * Source: https://razorpay.com/docs/webhooks/payments/ (Payment Failed section)
 *
 * This is NOT a live integration — it accepts the same JSON shape that Razorpay
 * would send in production, so swapping the DataSeeder for a real webhook endpoint
 * requires only pointing Razorpay's webhook URL at this endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RazorpayWebhookPayload {

    /** Always "event" for webhook payloads. */
    @JsonProperty("entity")
    public String entity;

    /** Razorpay account ID (e.g. "acc_BFQ7uQEaa7j2z7"). */
    @JsonProperty("account_id")
    public String accountId;

    /** Event type — we expect "payment.failed". */
    @JsonProperty("event")
    public String event;

    /**
     * Razorpay's webhook event ID (stable per delivery attempt, when present).
     * Used as the idempotency key; when absent, the controller derives a
     * deterministic key from the payment ID instead.
     */
    @JsonProperty("event_id")
    public String eventId;

    /** Which entities are included (e.g. ["payment"]). */
    @JsonProperty("contains")
    public List<String> contains;

    /** The nested payload containing the payment entity. */
    @JsonProperty("payload")
    public Payload payload;

    /** Unix timestamp of when the event was created. */
    @JsonProperty("created_at")
    public Long createdAt;

    /** ── Nested objects matching Razorpay's actual shape ── */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        @JsonProperty("payment")
        public PaymentWrapper payment;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentWrapper {
        @JsonProperty("entity")
        public PaymentEntity entity;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentEntity {
        /** Razorpay payment ID (e.g. "pay_DEAU825sJlCbGa"). */
        @JsonProperty("id")
        public String id;

        /** Always "payment". */
        @JsonProperty("entity")
        public String entityType;

        /** Amount in paise (e.g. 50000 = ₹500). */
        @JsonProperty("amount")
        public Long amount;

        /** Currency code (e.g. "INR"). */
        @JsonProperty("currency")
        public String currency;

        /** Payment status — "failed" for this webhook. */
        @JsonProperty("status")
        public String status;

        /** Linked Razorpay order ID. */
        @JsonProperty("order_id")
        public String orderId;

        /** Payment method used: "card", "netbanking", "upi", "wallet". */
        @JsonProperty("method")
        public String method;

        /** Customer email. */
        @JsonProperty("email")
        public String email;

        /** Customer phone number. */
        @JsonProperty("contact")
        public String contact;

        /** Razorpay error code (e.g. "BAD_REQUEST_ERROR", "GATEWAY_ERROR"). */
        @JsonProperty("error_code")
        public String errorCode;

        /** Human-readable error description. */
        @JsonProperty("error_description")
        public String errorDescription;

        /** Error source: "bank", "issuer", "customer", "network". */
        @JsonProperty("error_source")
        public String errorSource;

        /** Error step: "payment_authorization", etc. */
        @JsonProperty("error_step")
        public String errorStep;

        /** Specific error reason from the bank/issuer. */
        @JsonProperty("error_reason")
        public String errorReason;

        /** Bank name (for netbanking/card). */
        @JsonProperty("bank")
        public String bank;

        /** Card details (if card payment). */
        @JsonProperty("card")
        public Map<String, Object> card;

        /** UPI VPA (if UPI payment). */
        @JsonProperty("vpa")
        public String vpa;

        /** Wallet name (if wallet payment). */
        @JsonProperty("wallet")
        public String wallet;

        /** Unix timestamp of payment creation. */
        @JsonProperty("created_at")
        public Long createdAt;
    }
}

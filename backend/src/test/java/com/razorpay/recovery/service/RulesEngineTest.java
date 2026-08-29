package com.razorpay.recovery.service;

import com.razorpay.recovery.dto.LlmDecision;
import com.razorpay.recovery.model.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.model.Transaction;
import com.razorpay.recovery.model.Transaction.FailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RulesEngine — the bounded-workflow hard boundary.
 * Uses reflection to set @Value fields so no Spring context is needed.
 */
class RulesEngineTest {

    private RulesEngine rulesEngine;

    @BeforeEach
    void setUp() throws Exception {
        rulesEngine = new RulesEngine();
        setField(rulesEngine, "maxRetries", 3);
        setField(rulesEngine, "maxDiscountPercent", 15);
        setField(rulesEngine, "minAmountForDiscount", new BigDecimal("500"));
    }

    // ── eligibleActions tests ──────────────────────────────────────────

    @Test
    void atMaxRetries_onlyOffersNonRetryActions() {
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 3, new BigDecimal("1000"));

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertEquals(3, eligible.size());
        assertTrue(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
        assertTrue(eligible.contains(RecoveryAction.ABANDON));
        assertFalse(eligible.contains(RecoveryAction.RETRY_NOW),
                "RETRY_NOW must not appear when retryCount >= maxRetries");
        assertFalse(eligible.contains(RecoveryAction.RETRY_SCHEDULED),
                "RETRY_SCHEDULED must not appear when retryCount >= maxRetries");
        assertFalse(eligible.contains(RecoveryAction.OFFER_DISCOUNT),
                "OFFER_DISCOUNT must not appear when retries are exhausted");
    }

    @Test
    void belowDiscountThreshold_noOfferDiscount() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("299"));

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertFalse(eligible.contains(RecoveryAction.OFFER_DISCOUNT),
                "OFFER_DISCOUNT must not appear for transactions below ₹500");
        // Retryable failure + below threshold: should have RETRY_NOW, RETRY_SCHEDULED, SEND_PAYMENT_LINK, ESCALATE
        assertTrue(eligible.contains(RecoveryAction.RETRY_NOW));
        assertTrue(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    @Test
    void terminalFailure_noRetries() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("1000"));

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertFalse(eligible.contains(RecoveryAction.RETRY_NOW),
                "RETRY_NOW must not appear for terminal (non-retryable) failures");
        assertFalse(eligible.contains(RecoveryAction.RETRY_SCHEDULED));
        assertTrue(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK));
        assertTrue(eligible.contains(RecoveryAction.OFFER_DISCOUNT)); // amount >= 500
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    @Test
    void retryableFailure_highValue_allOptionsOpen() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("2499"));

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertTrue(eligible.contains(RecoveryAction.RETRY_NOW));
        assertTrue(eligible.contains(RecoveryAction.RETRY_SCHEDULED));
        assertTrue(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK));
        assertTrue(eligible.contains(RecoveryAction.OFFER_DISCOUNT));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    // ── enforceBounds tests ────────────────────────────────────────────

    @Test
    void enforceBounds_clampsDiscountAboveMax() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("2499"));
        LlmDecision proposed = new LlmDecision(
                RecoveryAction.OFFER_DISCOUNT, "LLM wants a big discount", 0.8, 40);

        LlmDecision result = rulesEngine.enforceBounds(tx, proposed);

        assertEquals(RecoveryAction.OFFER_DISCOUNT, result.action());
        assertEquals(15, result.discountPercent(),
                "Discount must be capped to the configured max (15%)");
        assertTrue(result.reasoning().contains("capped by RulesEngine"));
    }

    @Test
    void enforceBounds_clampsDiscountToMax_whenExactlyAtMax() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("1000"));
        LlmDecision proposed = new LlmDecision(
                RecoveryAction.OFFER_DISCOUNT, "15% is fair", 0.7, 15);

        LlmDecision result = rulesEngine.enforceBounds(tx, proposed);

        assertEquals(15, result.discountPercent(), "Exactly at max should pass through unchanged");
    }

    @Test
    void enforceBounds_rejectsActionNotInEligibleSet() {
        // retryCount = 3 → max retries exhausted → only SEND_PAYMENT_LINK, ESCALATE, ABANDON
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 3, new BigDecimal("1000"));
        LlmDecision proposed = new LlmDecision(
                RecoveryAction.RETRY_NOW, "LLM hallucinated a retry", 0.9, null);

        LlmDecision result = rulesEngine.enforceBounds(tx, proposed);

        assertNotEquals(RecoveryAction.RETRY_NOW, result.action(),
                "RETRY_NOW must be rejected when retries are exhausted");
        // Should fall back to SEND_PAYMENT_LINK (preferred) or ESCALATE
        assertTrue(result.action() == RecoveryAction.SEND_PAYMENT_LINK
                        || result.action() == RecoveryAction.ESCALATE_TO_HUMAN,
                "Fallback should be SEND_PAYMENT_LINK or ESCALATE_TO_HUMAN, got: " + result.action());
        assertTrue(result.reasoning().contains("out of bounds"));
    }

    @Test
    void enforceBounds_rejectsNullProposed() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 1, new BigDecimal("1000"));

        LlmDecision result = rulesEngine.enforceBounds(tx, null);

        assertNotNull(result);
        assertNotNull(result.action());
        assertTrue(result.action() == RecoveryAction.SEND_PAYMENT_LINK
                        || result.action() == RecoveryAction.ESCALATE_TO_HUMAN,
                "Null proposal should fall back to a safe action");
    }

    @Test
    void enforceBounds_validDecisionPassesThrough() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("2499"));
        LlmDecision proposed = new LlmDecision(
                RecoveryAction.RETRY_NOW, "Transient failure, retry immediately", 0.8, null);

        LlmDecision result = rulesEngine.enforceBounds(tx, proposed);

        assertEquals(RecoveryAction.RETRY_NOW, result.action());
        assertEquals("Transient failure, retry immediately", result.reasoning());
        assertEquals(0.8, result.confidence());
        assertNull(result.discountPercent());
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Transaction buildTx(FailureReason reason, int retryCount, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setFailureReason(reason);
        tx.setRetryCount(retryCount);
        tx.setAmount(amount);
        return tx;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

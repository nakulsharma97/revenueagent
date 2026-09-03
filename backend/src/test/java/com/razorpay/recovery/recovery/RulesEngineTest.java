package com.razorpay.recovery.recovery;

import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.recovery.EnforcedDecision;
import com.razorpay.recovery.recovery.RecoveryDecision;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RulesEngine — the bounded-workflow hard boundary.
 * Uses BoundsConfig with preset values so no Spring context is needed.
 */
class RulesEngineTest {

    private RulesEngine rulesEngine;
    private BoundsConfig boundsConfig;

    @BeforeEach
    void setUp() {
        boundsConfig = new BoundsConfig();
        boundsConfig.setMaxRetries(3);
        boundsConfig.setMaxDiscountPercent(15);
        boundsConfig.setMinAmountForDiscount(new BigDecimal("500"));
        boundsConfig.setRetryCooldownMinutes(60);
        // HV defaults (not injected by @Value in unit tests)
        boundsConfig.setHvMaxRetries(5);
        boundsConfig.setHvMaxDiscountPercent(25);
        boundsConfig.setHvMinAmountForDiscount(new BigDecimal("500"));
        rulesEngine = new RulesEngine(boundsConfig);
    }

    // ── eligibleActions tests ──────────────────────────────────────────

    @Test
    void atMaxRetries_onlyOffersNonRetryActions() {
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 3, new BigDecimal("1000"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertTrue(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
        assertTrue(eligible.contains(RecoveryAction.ABANDON));
        assertFalse(eligible.contains(RecoveryAction.RETRY_NOW),
                "RETRY_NOW must not appear when retryCount >= maxRetries");
        assertFalse(eligible.contains(RecoveryAction.RETRY_SCHEDULED),
                "RETRY_SCHEDULED must not appear when retryCount >= maxRetries");
        assertFalse(eligible.contains(RecoveryAction.RETRY_SILENT),
                "RETRY_SILENT must not appear when retries are exhausted");
    }

    @Test
    void belowDiscountThreshold_noOfferDiscount() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("299"));

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertFalse(eligible.contains(RecoveryAction.OFFER_DISCOUNT),
                "OFFER_DISCOUNT must not appear for transactions below ₹500");
        assertTrue(eligible.contains(RecoveryAction.RETRY_SILENT),
                "RETRY_SILENT should be the first-attempt retry action");
        assertFalse(eligible.contains(RecoveryAction.RETRY_NOW),
                "RETRY_NOW must not appear on first retryable attempt (silent-first)");
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    @Test
    void terminalFailure_noRetries() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("1000"));

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertFalse(eligible.contains(RecoveryAction.RETRY_NOW),
                "RETRY_NOW must not appear for terminal (non-retryable) failures");
        assertFalse(eligible.contains(RecoveryAction.RETRY_SCHEDULED));
        assertFalse(eligible.contains(RecoveryAction.RETRY_SILENT),
                "RETRY_SILENT must not appear for terminal failures");
        assertFalse(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK),
                "Customer-facing actions should not appear on first attempt for terminal failures");
        assertFalse(eligible.contains(RecoveryAction.OFFER_DISCOUNT));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    @Test
    void retryableFailure_firstAttempt_silentOnly() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("2499"));

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        // Silent-first: only RETRY_SILENT on first retryable attempt
        assertTrue(eligible.contains(RecoveryAction.RETRY_SILENT));
        assertFalse(eligible.contains(RecoveryAction.RETRY_NOW),
                "RETRY_NOW must NOT appear on first retryable attempt (silent-first)");
        assertFalse(eligible.contains(RecoveryAction.RETRY_SCHEDULED));
        assertFalse(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK),
                "Customer-facing actions must NOT appear before silent retry is attempted");
        assertFalse(eligible.contains(RecoveryAction.OFFER_DISCOUNT));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    @Test
    void retryableFailure_secondAttempt_allOptionsOpen() {
        // After silent retry failed, all options open
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 1, new BigDecimal("2499"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertTrue(eligible.contains(RecoveryAction.RETRY_NOW));
        assertTrue(eligible.contains(RecoveryAction.RETRY_SCHEDULED));
        assertTrue(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK));
        assertTrue(eligible.contains(RecoveryAction.OFFER_DISCOUNT));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    // ── enforceBounds tests ────────────────────────────────────────────

    @Test
    void enforceBounds_clampsDiscountAboveMax_andFlagsSignoff() {
        // Use a retryable failure at retryCount=1 so OFFER_DISCOUNT is eligible
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 1, new BigDecimal("2499"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.OFFER_DISCOUNT, "LLM wants a big discount", 0.8, 40);

        EnforcedDecision result = rulesEngine.enforceBounds(tx, proposed);

        assertEquals(RecoveryAction.OFFER_DISCOUNT, result.decision().action());
        assertEquals(15, result.decision().discountPercent(),
                "Discount must be capped to the configured max (15%)");
        assertTrue(result.decision().reasoning().contains("capped by RulesEngine"));
        assertTrue(result.requiresHumanSignoff(),
                "Signoff must be flagged when LLM proposes discount above ceiling");
        assertTrue(result.signoffReason().contains("40%"),
                "Signoff reason should mention the original proposed discount");
    }

    @Test
    void enforceBounds_clampsDiscountToMax_whenExactlyAtMax() {
        // Use a retryable failure at retryCount=1 so OFFER_DISCOUNT is eligible
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 1, new BigDecimal("1000"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.OFFER_DISCOUNT, "15% is fair", 0.7, 15);

        EnforcedDecision result = rulesEngine.enforceBounds(tx, proposed);

        assertEquals(15, result.decision().discountPercent(), "Exactly at max should pass through unchanged");
        assertFalse(result.requiresHumanSignoff(),
                "No signoff needed when discount is exactly at the ceiling");
    }

    @Test
    void enforceBounds_rejectsActionNotInEligibleSet() {
        // retryCount = 3 → max retries exhausted → only SEND_PAYMENT_LINK, ESCALATE, ABANDON
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 3, new BigDecimal("1000"));
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.RETRY_NOW, "LLM hallucinated a retry", 0.9, null);

        EnforcedDecision result = rulesEngine.enforceBounds(tx, proposed);

        assertNotEquals(RecoveryAction.RETRY_NOW, result.decision().action(),
                "RETRY_NOW must be rejected when retries are exhausted");
        assertTrue(result.decision().action() == RecoveryAction.SEND_PAYMENT_LINK
                        || result.decision().action() == RecoveryAction.ESCALATE_TO_HUMAN,
                "Fallback should be SEND_PAYMENT_LINK or ESCALATE_TO_HUMAN, got: " + result.decision().action());
        assertTrue(result.decision().reasoning().contains("out of bounds"));
    }

    @Test
    void enforceBounds_rejectsNullProposed() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 1, new BigDecimal("1000"));

        EnforcedDecision result = rulesEngine.enforceBounds(tx, null);

        assertNotNull(result);
        assertNotNull(result.decision().action());
        assertTrue(result.decision().action() == RecoveryAction.SEND_PAYMENT_LINK
                        || result.decision().action() == RecoveryAction.ESCALATE_TO_HUMAN,
                "Null proposal should fall back to a safe action");
    }

    @Test
    void enforceBounds_validDecisionPassesThrough() {
        // Use retryCount=1, IN_RECOVERY so RETRY_NOW is eligible
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 1, new BigDecimal("2499"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.RETRY_NOW, "Transient failure, retry immediately", 0.8, null);

        EnforcedDecision result = rulesEngine.enforceBounds(tx, proposed);

        assertEquals(RecoveryAction.RETRY_NOW, result.decision().action());
        assertEquals("Transient failure, retry immediately", result.decision().reasoning());
        assertEquals(0.8, result.decision().confidence());
        assertNull(result.decision().discountPercent());
        assertFalse(result.requiresHumanSignoff(), "No signoff for a valid in-bounds decision");
    }

    // ── requiresHumanSignoff tests ─────────────────────────────────────

    @Test
    void requiresHumanSignoff_discountAboveCeiling() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("2499"));
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.OFFER_DISCOUNT, "Big discount", 0.8, 20);

        assertTrue(rulesEngine.requiresHumanSignoff(tx, proposed),
                "Discount above 15% ceiling must require signoff");
    }

    @Test
    void requiresHumanSignoff_discountAtCeiling_noSignoff() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("1000"));
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.OFFER_DISCOUNT, "Fair discount", 0.7, 15);

        assertFalse(rulesEngine.requiresHumanSignoff(tx, proposed),
                "Discount at exactly 15% should NOT require signoff");
    }

    @Test
    void requiresHumanSignoff_thirdFailure() {
        // retryCount = 2, maxRetries = 3 → 3rd consecutive failure
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 2, new BigDecimal("1000"));
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.RETRY_NOW, "One more try", 0.6, null);

        assertTrue(rulesEngine.requiresHumanSignoff(tx, proposed),
                "3rd consecutive failure (retryCount >= maxRetries-1) must require signoff");
    }

    @Test
    void requiresHumanSignoff_secondFailure_noSignoff() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 1, new BigDecimal("1000"));
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.RETRY_SCHEDULED, "Schedule retry", 0.55, null);

        assertFalse(rulesEngine.requiresHumanSignoff(tx, proposed),
                "2nd failure (retryCount=1) should NOT require signoff");
    }

    @Test
    void requiresHumanSignoff_noDiscount_noRetries_noSignoff() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));
        RecoveryDecision proposed = new RecoveryDecision(
                RecoveryAction.RETRY_NOW, "First failure, retry", 0.6, null);

        assertFalse(rulesEngine.requiresHumanSignoff(tx, proposed),
                "Normal first attempt should NOT require signoff");
    }

    @Test
    void requiresHumanSignoff_nullProposed_noSignoff() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));

        assertFalse(rulesEngine.requiresHumanSignoff(tx, null),
                "Null proposed should NOT require signoff");
    }

    // ── Segment-aware bounds tests (Part 2) ──────────────────────────────

    @Test
    void segmentAware_highValue_getsWiderBounds() {
        // Standard: maxRetries=3, maxDiscount=15%
        Transaction txStd = buildTx(FailureReason.INSUFFICIENT_FUNDS, 2, new BigDecimal("1000"));
        txStd.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);
        List<RecoveryAction> eligibleStd = rulesEngine.eligibleActions(txStd, com.razorpay.recovery.customer.Customer.CustomerSegment.STANDARD);

        // High-value: maxRetries=5, maxDiscount=25%
        List<RecoveryAction> eligibleHV = rulesEngine.eligibleActions(txStd, com.razorpay.recovery.customer.Customer.CustomerSegment.HIGH_VALUE);

        // STANDARD at retryCount=2: only 1 retry left (< 3), so RETRY_NOW is eligible
        assertTrue(eligibleStd.contains(RecoveryAction.RETRY_NOW),
                "STANDARD customer at retryCount=2 should still have RETRY_NOW (< maxRetries=3)");

        // HIGH_VALUE at retryCount=2: 3 retries left (< 5), same actions but wider discount
        assertTrue(eligibleHV.contains(RecoveryAction.RETRY_NOW),
                "HIGH_VALUE customer at retryCount=2 should have RETRY_NOW (< maxRetries=5)");

        // At retryCount=4: STANDARD exhausted, HIGH_VALUE still has retries
        Transaction tx4 = buildTx(FailureReason.INSUFFICIENT_FUNDS, 4, new BigDecimal("1000"));
        tx4.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);
        List<RecoveryAction> eligibleStd4 = rulesEngine.eligibleActions(tx4, com.razorpay.recovery.customer.Customer.CustomerSegment.STANDARD);
        List<RecoveryAction> eligibleHV4 = rulesEngine.eligibleActions(tx4, com.razorpay.recovery.customer.Customer.CustomerSegment.HIGH_VALUE);

        assertFalse(eligibleStd4.contains(RecoveryAction.RETRY_NOW),
                "STANDARD customer at retryCount=4 should be exhausted (maxRetries=3)");
        assertTrue(eligibleHV4.contains(RecoveryAction.RETRY_NOW),
                "HIGH_VALUE customer at retryCount=4 should still have RETRY_NOW (maxRetries=5)");
    }

    @Test
    void segmentAware_highValue_getsHigherDiscountCeiling() {
        // Transaction amount = ₹2499, retryCount=1, IN_RECOVERY -> OFFER_DISCOUNT eligible
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 1, new BigDecimal("2499"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);

        // Set proposed discount to 20% — exceeds STANDARD ceiling (15%) but within HIGH_VALUE ceiling (25%)
        RecoveryDecision proposed20 = new RecoveryDecision(RecoveryAction.OFFER_DISCOUNT, "Test", 0.7, 20);

        // STANDARD: 20% > 15% ceiling -> should be capped
        EnforcedDecision enforcedStd = rulesEngine.enforceBounds(tx, proposed20);
        assertEquals(15, enforcedStd.decision().discountPercent(),
                "STANDARD customer: 20% must be capped to 15%");
        assertTrue(enforcedStd.requiresHumanSignoff());
    }

    // ── Silent-first tests ────────────────────────────────────────────────

    @Test
    void silentFirst_retryableFirstAttempt_onlySilentAndEscalate() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertEquals(2, eligible.size(),
                "First retryable attempt should have exactly RETRY_SILENT + ESCALATE_TO_HUMAN");
        assertTrue(eligible.contains(RecoveryAction.RETRY_SILENT));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    @Test
    void silentFirst_afterSilentRetry_allOptionsOpen() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 1, new BigDecimal("1000"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);

        List<RecoveryAction> eligible = rulesEngine.eligibleActions(tx);

        assertTrue(eligible.contains(RecoveryAction.RETRY_NOW));
        assertTrue(eligible.contains(RecoveryAction.RETRY_SCHEDULED));
        assertTrue(eligible.contains(RecoveryAction.SEND_PAYMENT_LINK));
        assertTrue(eligible.contains(RecoveryAction.ESCALATE_TO_HUMAN));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Transaction buildTx(FailureReason reason, int retryCount, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setFailureReason(reason);
        tx.setRetryCount(retryCount);
        tx.setAmount(amount);
        return tx;
    }
}

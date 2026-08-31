package com.razorpay.recovery.recovery;

import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.recovery.LlmDecision;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that live-updating BoundsConfig at runtime changes
 * RulesEngine behavior on the NEXT call — without restart.
 */
class LiveBoundsEditorTest {

    private BoundsConfig boundsConfig;
    private RulesEngine rulesEngine;

    @BeforeEach
    void setUp() {
        boundsConfig = new BoundsConfig();
        boundsConfig.setMaxRetries(3);
        boundsConfig.setMaxDiscountPercent(15);
        boundsConfig.setMinAmountForDiscount(new BigDecimal("500"));
        boundsConfig.setRetryCooldownMinutes(60);
        boundsConfig.setHvMaxRetries(5);
        boundsConfig.setHvMaxDiscountPercent(25);
        boundsConfig.setHvMinAmountForDiscount(new BigDecimal("500"));
        rulesEngine = new RulesEngine(boundsConfig);
    }

    @Test
    void loweringDiscountCeiling_removesOfferDiscountFromEligible() {
        // After silent retry (retryCount=1, IN_RECOVERY), customer-facing actions are available
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 1, new BigDecimal("2499"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);
        assertTrue(rulesEngine.eligibleActions(tx).contains(RecoveryAction.OFFER_DISCOUNT),
                "OFFER_DISCOUNT should be eligible with default 15% ceiling after silent retry");

        // Lower the ceiling to 0 — OFFER_DISCOUNT should disappear from eligible
        boundsConfig.setMaxDiscountPercent(0);
        List<RecoveryAction> eligibleAfter = rulesEngine.eligibleActions(tx);
        assertFalse(eligibleAfter.contains(RecoveryAction.OFFER_DISCOUNT),
                "OFFER_DISCOUNT must NOT be eligible when discount ceiling is 0%");
    }

    @Test
    void loweringMaxRetries_reducesRetryEligibleActions() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 2, new BigDecimal("1000"));

        // With maxRetries=3, retryCount=2 < 3, so RETRY_NOW and RETRY_SCHEDULED are eligible
        assertTrue(rulesEngine.eligibleActions(tx).contains(RecoveryAction.RETRY_NOW),
                "RETRY_NOW should be eligible when retryCount=2, maxRetries=3");

        // Lower maxRetries to 2 — retryCount=2 >= 2, retries exhausted
        boundsConfig.setMaxRetries(2);
        List<RecoveryAction> eligibleAfter = rulesEngine.eligibleActions(tx);
        assertFalse(eligibleAfter.contains(RecoveryAction.RETRY_NOW),
                "RETRY_NOW must NOT be eligible when retryCount >= maxRetries");
        assertFalse(eligibleAfter.contains(RecoveryAction.RETRY_SCHEDULED),
                "RETRY_SCHEDULED must NOT be eligible when retryCount >= maxRetries");
        assertTrue(eligibleAfter.contains(RecoveryAction.SEND_PAYMENT_LINK));
        assertTrue(eligibleAfter.contains(RecoveryAction.ABANDON));
    }

    @Test
    void requiresHumanSignoff_reflectsLoweredCeiling() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("1000"));
        LlmDecision proposed = new LlmDecision(
                RecoveryAction.OFFER_DISCOUNT, "Discount offer", 0.7, 10);

        // With 15% ceiling, 10% is within bounds — no signoff
        assertFalse(rulesEngine.requiresHumanSignoff(tx, proposed),
                "10% discount should NOT require signoff with 15% ceiling");

        // Lower ceiling to 8% — now 10% exceeds ceiling
        boundsConfig.setMaxDiscountPercent(8);
        assertTrue(rulesEngine.requiresHumanSignoff(tx, proposed),
                "10% discount MUST require signoff when ceiling is lowered to 8%");
    }

    @Test
    void loweringMinAmountForDiscount_removesLowValueDiscount() {
        // Use retryCount=1, IN_RECOVERY so OFFER_DISCOUNT is eligible
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 1, new BigDecimal("600"));
        tx.setStatus(com.razorpay.recovery.transaction.Transaction.TransactionStatus.IN_RECOVERY);

        // With default ₹500 min, ₹600 qualifies for discount
        assertTrue(rulesEngine.eligibleActions(tx).contains(RecoveryAction.OFFER_DISCOUNT),
                "OFFER_DISCOUNT should be eligible for ₹600 with ₹500 min");

        // Raise minimum to ₹1000 — ₹600 no longer qualifies
        boundsConfig.setMinAmountForDiscount(new BigDecimal("1000"));
        assertFalse(rulesEngine.eligibleActions(tx).contains(RecoveryAction.OFFER_DISCOUNT),
                "OFFER_DISCOUNT must NOT be eligible when amount < min threshold");
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

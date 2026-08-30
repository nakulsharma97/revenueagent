package com.razorpay.recovery.recovery;

import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.recovery.DecisionResult;
import com.razorpay.recovery.recovery.LlmDecision;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DecisionAgentService — confirms the heuristic fallback path
 * returns a valid LlmDecision for every FailureReason without throwing.
 * Tests the original decide() method signature.
 */
class DecisionAgentServiceTest {

    private DecisionAgentService decisionAgentService;

    @BeforeEach
    void setUp() throws Exception {
        BoundsConfig boundsConfig = new BoundsConfig();
        boundsConfig.setMaxRetries(3);
        boundsConfig.setMaxDiscountPercent(15);
        boundsConfig.setMinAmountForDiscount(new BigDecimal("500"));
        boundsConfig.setRetryCooldownMinutes(60);
        RulesEngine rulesEngine = new RulesEngine(boundsConfig);

        decisionAgentService = new DecisionAgentService(rulesEngine, boundsConfig);
        // Force heuristic fallback path (no LLM)
        setField(decisionAgentService, "llmEnabled", false);
        setField(decisionAgentService, "apiKey", "");
        setField(decisionAgentService, "model", "test-model");
    }

    @ParameterizedTest
    @EnumSource(FailureReason.class)
    void heuristicFallback_returnsValidDecisionForEveryFailureReason(FailureReason reason) throws Exception {
        Transaction tx = buildTx(reason, 0, new BigDecimal("1000"));

        // Should never throw — decide() returns LlmDecision
        LlmDecision decision = decisionAgentService.decide(tx);

        assertNotNull(decision, "Decision must not be null for " + reason);
        assertNotNull(decision.action(), "Action must not be null for " + reason);
        assertNotNull(decision.reasoning(), "Reasoning must not be null for " + reason);
        assertFalse(decision.reasoning().isBlank(), "Reasoning must not be blank for " + reason);
        assertTrue(decision.confidence() >= 0.0 && decision.confidence() <= 1.0,
                "Confidence must be in [0, 1] for " + reason);

        // Verify the chosen action is actually in the eligible set
        BoundsConfig bc = new BoundsConfig();
        bc.setMaxRetries(3);
        bc.setMaxDiscountPercent(15);
        bc.setMinAmountForDiscount(new BigDecimal("500"));
        bc.setRetryCooldownMinutes(60);
        RulesEngine rulesEngine = new RulesEngine(bc);
        assertTrue(rulesEngine.eligibleActions(tx).contains(decision.action()),
                "Chosen action " + decision.action() + " must be in eligible set for " + reason);
    }

    @Test
    void heuristicFallback_firstRetryableFailure_returnsRetryNow() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));

        LlmDecision decision = decisionAgentService.decide(tx);

        assertEquals(RecoveryAction.RETRY_NOW, decision.action(),
                "First retryable failure should trigger immediate retry");
    }

    @Test
    void heuristicFallback_secondRetryableFailure_returnsRetryScheduled() {
        Transaction tx = buildTx(FailureReason.BANK_SERVER_DOWN, 1, new BigDecimal("1000"));

        LlmDecision decision = decisionAgentService.decide(tx);

        assertEquals(RecoveryAction.RETRY_SCHEDULED, decision.action(),
                "Second retryable failure should trigger scheduled retry");
    }

    @Test
    void heuristicFallback_terminalHighValue_returnsOfferDiscount() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("2499"));

        LlmDecision decision = decisionAgentService.decide(tx);

        assertEquals(RecoveryAction.OFFER_DISCOUNT, decision.action(),
                "Terminal failure on high-value tx should trigger discount offer");
        assertEquals(10, decision.discountPercent(),
                "Heuristic should propose 10% discount");
    }

    @Test
    void heuristicFallback_terminalLowValue_returnsSendPaymentLink() {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("299"));

        LlmDecision decision = decisionAgentService.decide(tx);

        assertEquals(RecoveryAction.SEND_PAYMENT_LINK, decision.action(),
                "Terminal failure on low-value tx (below discount threshold) should send payment link");
    }

    @Test
    void heuristicFallback_maxRetriesExhausted_prefersPaymentLinkOrEscalate() {
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 3, new BigDecimal("1000"));

        LlmDecision decision = decisionAgentService.decide(tx);

        // When retries exhausted, eligible = {SEND_PAYMENT_LINK, ESCALATE_TO_HUMAN, ABANDON}
        assertTrue(decision.action() == RecoveryAction.SEND_PAYMENT_LINK
                        || decision.action() == RecoveryAction.ESCALATE_TO_HUMAN,
                "Exhausted retries should use payment link or escalate, got: " + decision.action());
    }

    @Test
    void decide_alwaysPassesThroughEnforceBounds() throws Exception {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));

        LlmDecision decision = decisionAgentService.decide(tx);

        BoundsConfig bc = new BoundsConfig();
        bc.setMaxRetries(3);
        bc.setMaxDiscountPercent(15);
        bc.setMinAmountForDiscount(new BigDecimal("500"));
        bc.setRetryCooldownMinutes(60);
        RulesEngine rulesEngine = new RulesEngine(bc);
        assertTrue(rulesEngine.eligibleActions(tx).contains(decision.action()),
                "Final decision must always be within the rules-engine bounds");
    }

    @Test
    void invalidApiKey_fallsBackToHeuristic_withoutCrashing() throws Exception {
        BoundsConfig bc = new BoundsConfig();
        bc.setMaxRetries(3);
        bc.setMaxDiscountPercent(15);
        bc.setMinAmountForDiscount(new BigDecimal("500"));
        bc.setRetryCooldownMinutes(60);
        RulesEngine rulesEngine = new RulesEngine(bc);

        DecisionAgentService service = new DecisionAgentService(rulesEngine, bc);
        setField(service, "llmEnabled", true);
        setField(service, "apiKey", "sk-ant-INVALID-KEY-FOR-TESTING");
        setField(service, "model", "claude-sonnet-4-6");

        for (FailureReason reason : FailureReason.values()) {
            Transaction tx = buildTx(reason, 0, new BigDecimal("1000"));
            LlmDecision decision = service.decide(tx);

            assertNotNull(decision, "Must not return null for " + reason);
            assertNotNull(decision.action(), "Action must not be null for " + reason);
            assertTrue(rulesEngine.eligibleActions(tx).contains(decision.action()),
                    "Fallback action must be within bounds for " + reason);
        }
    }

    // ── decideWithMeta tests ─────────────────────────────────────────

    @Test
    void decideWithMeta_heuristic_llmDrivenIsFalse() throws Exception {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));

        DecisionResult result = decisionAgentService.decideWithMeta(tx);

        assertNotNull(result);
        assertFalse(result.llmDriven(), "Heuristic path must set llmDriven=false");
        assertNotNull(result.decision());
    }

    @Test
    void decideWithMeta_invalidApiKey_llmDrivenIsFalse() throws Exception {
        BoundsConfig bc = new BoundsConfig();
        bc.setMaxRetries(3);
        bc.setMaxDiscountPercent(15);
        bc.setMinAmountForDiscount(new BigDecimal("500"));
        bc.setRetryCooldownMinutes(60);
        RulesEngine rulesEngine = new RulesEngine(bc);

        DecisionAgentService service = new DecisionAgentService(rulesEngine, bc);
        setField(service, "llmEnabled", true);
        setField(service, "apiKey", "sk-ant-INVALID-KEY-FOR-TESTING");
        setField(service, "model", "claude-sonnet-4-6");

        for (FailureReason reason : FailureReason.values()) {
            Transaction tx = buildTx(reason, 0, new BigDecimal("1000"));
            DecisionResult result = service.decideWithMeta(tx);

            assertFalse(result.llmDriven(),
                    "Invalid API key must fall back to heuristic with llmDriven=false for " + reason);
        }
    }

    @Test
    void decideWithMeta_thirdFailure_flagsSignoff() throws Exception {
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 2, new BigDecimal("1000"));

        DecisionResult result = decisionAgentService.decideWithMeta(tx);

        assertTrue(result.requiresHumanSignoff(),
                "retryCount=2 (3rd failure) must require human sign-off");
    }

    @Test
    void decideWithMeta_firstFailure_noSignoff() throws Exception {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));

        DecisionResult result = decisionAgentService.decideWithMeta(tx);

        assertFalse(result.requiresHumanSignoff(),
                "retryCount=0 should NOT require sign-off");
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

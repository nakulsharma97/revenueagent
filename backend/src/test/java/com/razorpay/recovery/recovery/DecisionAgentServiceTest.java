package com.razorpay.recovery.recovery;

import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.intelligence.AnomalyDetectionService;
import com.razorpay.recovery.intelligence.CustomerStateService;
import com.razorpay.recovery.intelligence.DecisionConfidenceService;
import com.razorpay.recovery.intelligence.NextBestActionEngine;
import com.razorpay.recovery.intelligence.RecoveryFatigueService;
import com.razorpay.recovery.intelligence.RecoveryValueOptimizer;
import com.razorpay.recovery.intelligence.UpliftScoringService;
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
 * Unit tests for DecisionAgentService — the canonical, engine-driven decision path.
 *
 * <p>There is no separate heuristic/LLM picker anymore: every decision comes from the
 * injected Next-Best-Action engine. These tests prove that for every failure reason the
 * pipeline returns a bounded decision (always inside the RulesEngine-eligible set),
 * never claims LLM usage unless a real API response was incorporated, and applies the
 * human sign-off thresholds correctly.
 */
class DecisionAgentServiceTest {

    private DecisionAgentService decisionAgentService;

    private static NextBestActionEngine intelligenceEngine() {
        return new NextBestActionEngine(new UpliftScoringService(), new RecoveryFatigueService(),
                new CustomerStateService(), new DecisionConfidenceService(),
                new RecoveryValueOptimizer(), new AnomalyDetectionService());
    }

    @BeforeEach
    void setUp() throws Exception {
        BoundsConfig boundsConfig = new BoundsConfig();
        boundsConfig.setMaxRetries(3);
        boundsConfig.setMaxDiscountPercent(15);
        boundsConfig.setMinAmountForDiscount(new BigDecimal("500"));
        boundsConfig.setRetryCooldownMinutes(60);
        boundsConfig.setHvMaxRetries(5);
        boundsConfig.setHvMaxDiscountPercent(25);
        boundsConfig.setHvMinAmountForDiscount(new BigDecimal("500"));
        RulesEngine rulesEngine = new RulesEngine(boundsConfig);

        decisionAgentService = new DecisionAgentService(rulesEngine, boundsConfig, intelligenceEngine());
        // No LLM configured → the explanation layer must stay silent (llmDriven=false).
        setField(decisionAgentService, "llmEnabled", false);
        setField(decisionAgentService, "apiKey", "");
        setField(decisionAgentService, "model", "test-model");
    }

    @ParameterizedTest
    @EnumSource(FailureReason.class)
    void canonicalPath_returnsValidBoundedDecisionForEveryFailureReason(FailureReason reason) {
        Transaction tx = buildTx(reason, 0, new BigDecimal("1000"));

        DecisionResult result = decisionAgentService.decideWithMeta(tx);

        assertNotNull(result);
        RecoveryDecision decision = result.decision();
        assertNotNull(decision.action(), "Action must not be null for " + reason);
        assertNotNull(decision.reasoning(), "Reasoning must not be null for " + reason);
        assertFalse(decision.reasoning().isBlank(), "Reasoning must not be blank for " + reason);
        assertTrue(decision.confidence() >= 0.0 && decision.confidence() <= 1.0,
                "Confidence must be in [0, 1] for " + reason);
        assertFalse(result.llmDriven(), "Without an LLM key the engine path must never claim LLM usage for " + reason);

        // The engine's choice is always re-validated against the hard bounds.
        BoundsConfig bc = new BoundsConfig();
        bc.setMaxRetries(3);
        bc.setMaxDiscountPercent(15);
        bc.setMinAmountForDiscount(new BigDecimal("500"));
        bc.setRetryCooldownMinutes(60);
        RulesEngine rulesEngine = new RulesEngine(bc);
        assertTrue(rulesEngine.eligibleActions(tx).contains(decision.action()),
                "Engine choice " + decision.action() + " must be within the eligible set for " + reason);
    }

    @Test
    void canonicalPath_firstRetryableFailure_prefersSilentRecovery() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));

        RecoveryDecision decision = decisionAgentService.decideWithMeta(tx).decision();

        // A fresh transient failure on a reliable customer: the free silent retry dominates
        // the economics (zero cost, no customer contact), so nothing should be spent on it.
        assertTrue(decision.action() == RecoveryAction.RETRY_SILENT
                        || decision.action() == RecoveryAction.RETRY_NOW,
                "First retryable failure must stay in the silent/retry family, got: " + decision.action());
    }

    @Test
    void canonicalPath_terminalFirstAttempt_escalatesOrWaits() {
        // Terminal failure with no retry history: no customer-facing automated action is
        // eligible, so the engine must not pick an intervention that cannot execute.
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("2499"));

        RecoveryDecision decision = decisionAgentService.decideWithMeta(tx).decision();

        assertNotNull(decision.action());
        assertFalse(decision.action() == RecoveryAction.OFFER_DISCOUNT,
                "No discount may be offered when it is not RulesEngine-eligible");
    }

    @Test
    void decideWithMeta_noLlmKey_llmDrivenIsFalse() {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));

        DecisionResult result = decisionAgentService.decideWithMeta(tx);

        assertNotNull(result);
        assertFalse(result.llmDriven(), "Deterministic engine decisions must set llmDriven=false");
        assertNotNull(result.decision());
    }

    @Test
    void decideWithMeta_invalidApiKey_neverClaimsLlm() throws Exception {
        BoundsConfig bc = new BoundsConfig();
        bc.setMaxRetries(3);
        bc.setMaxDiscountPercent(15);
        bc.setMinAmountForDiscount(new BigDecimal("500"));
        bc.setRetryCooldownMinutes(60);
        RulesEngine rulesEngine = new RulesEngine(bc);

        DecisionAgentService service = new DecisionAgentService(rulesEngine, bc, intelligenceEngine());
        setField(service, "llmEnabled", true);
        setField(service, "apiKey", "sk-ant-INVALID-KEY-FOR-TESTING");
        setField(service, "model", "claude-sonnet-4-6");

        for (FailureReason reason : FailureReason.values()) {
            Transaction tx = buildTx(reason, 0, new BigDecimal("1000"));
            DecisionResult result = service.decideWithMeta(tx);

            assertFalse(result.llmDriven(),
                    "A failed LLM explanation call must fall back silently with llmDriven=false for " + reason);
            assertTrue(rulesEngine.eligibleActions(tx).contains(result.decision().action()),
                    "Engine decision must stay within bounds for " + reason);
        }
    }

    @Test
    void decideWithMeta_thirdFailure_flagsSignoff() {
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 2, new BigDecimal("1000"));

        DecisionResult result = decisionAgentService.decideWithMeta(tx);

        assertTrue(result.requiresHumanSignoff(),
                "retryCount=2 (3rd failure) must require human sign-off");
    }

    @Test
    void decideWithMeta_firstFailure_noSignoff() {
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
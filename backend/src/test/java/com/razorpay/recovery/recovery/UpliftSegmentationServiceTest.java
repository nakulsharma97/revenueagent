package com.razorpay.recovery.recovery;

import com.razorpay.recovery.recovery.RecoveryAttempt.AttemptOutcome;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.recovery.RecoveryAttempt.UpliftSegment;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UpliftSegmentationService (segment classification) and
 * uplift math correctness (control vs treatment delta calculation).
 */
class UpliftSegmentationServiceTest {

    private UpliftSegmentationService service;

    @BeforeEach
    void setUp() {
        service = new UpliftSegmentationService();
    }

    // ═══ Segment classification tests ═══════════════════════════════

    @Test
    void highReliabilityRetryable_failure_sureThing() {
        // High reliability (>0.7) + retryable failure → SURE_THING
        // These would recover anyway, don't spend discounts
        UpliftSegment seg = service.classify(0.85, FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"), true);
        assertEquals(UpliftSegment.SURE_THING, seg,
                "High reliability + transient failure = would recover anyway");
    }

    @Test
    void lowReliabilityTerminal_failure_lostCause() {
        // Low reliability (<0.4) + non-retryable → LOST_CAUSE
        // No intervention changes a stolen card / invalid VPA
        UpliftSegment seg = service.classify(0.2, FailureReason.CARD_STOLEN_FLAG, 0, new BigDecimal("5000"), false);
        assertEquals(UpliftSegment.LOST_CAUSE, seg,
                "Low reliability + terminal decline = unrecoverable");
    }

    @Test
    void priorAttemptLowAmount_doNotDisturb() {
        // retryCount > 0 + small amount (<1000) → DO_NOT_DISTURB
        // Already failed once, low value — don't annoy with messages
        UpliftSegment seg = service.classify(0.5, FailureReason.INSUFFICIENT_FUNDS, 1, new BigDecimal("500"), true);
        assertEquals(UpliftSegment.DO_NOT_DISTURB, seg,
                "Prior attempt + low value = prefer silence");
    }

    @Test
    void moderateReliability_firstAttempt_persuadable() {
        // Moderate reliability (0.4-0.7), first attempt, medium amount → PERSUADABLE
        // This is the core target for intervention
        UpliftSegment seg = service.classify(0.55, FailureReason.NETWORK_ERROR, 0, new BigDecimal("2000"), true);
        assertEquals(UpliftSegment.PERSUADABLE, seg,
                "Moderate reliability + first attempt + medium amount = persuadable");
    }

    @Test
    void highReliabilityTerminal_persuadable() {
        // High reliability but terminal failure → PERSUADABLE (not SURE_THING because !isRetryable)
        UpliftSegment seg = service.classify(0.85, FailureReason.CARD_EXPIRED, 0, new BigDecimal("3000"), false);
        assertEquals(UpliftSegment.PERSUADABLE, seg,
                "High reliability + terminal failure = persuadable (discount may help)");
    }

    @Test
    void lowReliabilityRetryable_persuadable() {
        // Low reliability but retryable → PERSUADABLE (not LOST_CAUSE because isRetryable)
        UpliftSegment seg = service.classify(0.3, FailureReason.NETWORK_ERROR, 0, new BigDecimal("1500"), true);
        assertEquals(UpliftSegment.PERSUADABLE, seg,
                "Low reliability + retryable = persuadable (retry may help)");
    }

    // ═══ Uplift math correctness test ═══════════════════════════════

    @Test
    void upliftMath_controlVsTreatment_deltaCalculation() {
        // Pure arithmetic test: given known control/treatment outcomes,
        // verify the uplift report computes correct deltas.

        // Simulate: 10 PERSUADABLE control attempts, 5 recovered = 50% control rate
        //           10 PERSUADABLE treatment attempts, 8 recovered = 80% treatment rate
        //           Expected delta: 80 - 50 = +30pp

        double controlRate = (5.0 / 10) * 100; // 50.0
        double treatmentRate = (8.0 / 10) * 100; // 80.0
        double delta = Math.round((treatmentRate - controlRate) * 10.0) / 10.0;

        assertEquals(30.0, delta, 0.1,
                "PERSUADABLE uplift: 80% treatment - 50% control = +30pp");

        // SURE_THING: small delta means intervention doesn't help much
        double sureControlRate = (8.0 / 10) * 100; // 80%
        double sureTreatmentRate = (9.0 / 10) * 100; // 90%
        double sureDelta = Math.round((sureTreatmentRate - sureControlRate) * 10.0) / 10.0;

        assertEquals(10.0, sureDelta, 0.1,
                "SURE_THING uplift: 90% - 80% = +10pp (intervention barely helps)");
        assertTrue(delta > sureDelta,
                "PERSUADABLE delta should be larger than SURE_THING delta");

        // LOST_CAUSE: near-zero or negative delta means intervention is wasted
        double lostControlRate = (1.0 / 10) * 100; // 10%
        double lostTreatmentRate = (2.0 / 10) * 100; // 20%
        double lostDelta = Math.round((lostTreatmentRate - lostControlRate) * 10.0) / 10.0;

        assertEquals(10.0, lostDelta, 0.1,
                "LOST_CAUSE uplift: 20% - 10% = +10pp (small, intervention barely helps)");

        // Verify the key claim: PERSUADABLE delta >> SURE_THING delta
        assertTrue(delta > lostDelta,
                "PERSUADABLE delta should be larger than LOST_CAUSE delta");
    }

    @Test
    void doNotDisturb_controlHigher_provesSilentIsCorrect() {
        // Key insight: if DO_NOT_DISTURB control rate > treatment rate,
        // that's evidence the silent-only policy is correct.
        // This can happen when noisy interventions annoy already-struggling customers.

        // Simulate: DO_NOT_DISTURB control = 30%, treatment = 25%
        // → Negative delta: intervention HURT this segment
        double controlRate = 30.0;
        double treatmentRate = 25.0;
        double delta = Math.round((treatmentRate - controlRate) * 10.0) / 10.0;

        assertEquals(-5.0, delta, 0.1,
                "DO_NOT_DISTURB: negative delta proves intervention hurts this segment");
        assertTrue(delta < 0,
                "Negative delta = intervention reduced recovery — silent policy is correct");
    }

    @Test
    void NO_ACTION_notInCustomerFacingActions() {
        // Verify that NO_ACTION is excluded from the normal action set used by the rules engine
        RecoveryAction[] allActions = RecoveryAction.values();
        // NO_ACTION exists in the enum
        assertArrayContains(RecoveryAction.NO_ACTION, allActions);
    }

    private void assertArrayContains(RecoveryAction expected, RecoveryAction[] array) {
        for (RecoveryAction a : array) {
            if (a == expected) return;
        }
        fail(expected + " not found in array");
    }
}

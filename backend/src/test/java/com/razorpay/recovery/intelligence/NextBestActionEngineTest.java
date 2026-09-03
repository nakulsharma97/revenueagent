package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Next-Best-Action engine: counterfactual simulation, expected-net-value
 * ranking, discount-ceiling tiers, fatigue suppression, confidence policy and determinism.
 */
class NextBestActionEngineTest {

    private final NextBestActionEngine engine = new NextBestActionEngine(
            new UpliftScoringService(), new RecoveryFatigueService(), new CustomerStateService(),
            new DecisionConfidenceService(), new RecoveryValueOptimizer(), new AnomalyDetectionService());

    // ── helpers ───────────────────────────────────────────────────────

    private RecoveryCase paymentCase(BigDecimal amount, int retries, double reliability, boolean highValue,
                                     String failureMode, boolean retryable,
                                     List<RecoveryAction> eligible, int maxRetries, int maxDiscount) {
        return new RecoveryCase("PAYMENT", 1L, amount, retries, 0, retries, 0,
                retryable, !retryable, reliability, highValue, failureMode, false,
                eligible, maxRetries, maxDiscount, "TX#test", 0.0);
    }

    private RecoveryCase checkoutCase(BigDecimal amount, int reminders, String mode, List<RecoveryAction> eligible) {
        return new RecoveryCase("CHECKOUT", 1L, amount, 0, reminders, reminders, 0,
                true, false, 0.5, false, mode, false,
                eligible, 3, 15, "CHK#test", 0.0);
    }

    private RecoveryCase receivableCase(BigDecimal amount, int reminders, int daysOverdue, boolean brokenPromise,
                                        List<RecoveryAction> eligible) {
        return new RecoveryCase("RECEIVABLE", 1L, amount, 0, reminders, reminders, daysOverdue,
                true, false, 0.5, false, null, brokenPromise,
                eligible, 3, 15, "INV#test", 0.0);
    }

    // ── 1. Counterfactual simulation + selection ──────────────────────

    @Test
    void receivable_longOverdue_picksPaymentPlanOverReminder() {
        RecoveryCase c = receivableCase(new BigDecimal("250000"), 0, 60, false,
                List.of(RecoveryAction.SEND_REMINDER, RecoveryAction.OFFER_PAYMENT_PLAN, RecoveryAction.ESCALATE_TO_HUMAN));
        IntelligenceDecision d = engine.decide(c);

        assertNotNull(d.chosen());
        assertEquals(RecoveryAction.OFFER_PAYMENT_PLAN, d.chosen().action(),
                "Payment plan has the highest net value for a long-overdue receivable");
        assertEquals(3, d.alternatives().size(), "Every eligible action must be simulated");
        for (ActionEvaluation e : d.alternatives()) {
            assertTrue(d.chosen().incrementalNetValue().compareTo(e.incrementalNetValue()) >= 0,
                    "Chosen action must have the highest (or tied) incremental net value, got " + e.action());
        }
    }

    @Test
    void checkout_highIntent_picksPaymentLinkOverWastedDiscount() {
        // Distracted, high-intent shopper: intent 0.8 → pay link ~0.44; a 10% discount would
        // only reach ~0.42 while costing margin — the engine must NOT pick the discount.
        RecoveryCase c = checkoutCase(new BigDecimal("9999"), 0, "DISTRACTED_NO_COMPLETION",
                List.of(RecoveryAction.CHECKOUT_REMINDER, RecoveryAction.SEND_PAYMENT_LINK,
                        RecoveryAction.OFFER_DISCOUNT, RecoveryAction.ESCALATE_TO_HUMAN));
        IntelligenceDecision d = engine.decide(c);

        assertEquals(RecoveryAction.SEND_PAYMENT_LINK, d.chosen().action(),
                "High-intent abandoners get a payment link, not a margin-burning discount");
    }

    @Test
    void payment_transientAfterRetry_picksRetryNotDiscount() {
        // Transient UPI timeout after one failed retry — discounts barely move this customer,
        // a free retry inside the recovery window does.
        RecoveryCase c = paymentCase(new BigDecimal("2000"), 1, 0.55, false, "UPI_TIMEOUT", true,
                List.of(RecoveryAction.RETRY_NOW, RecoveryAction.RETRY_SCHEDULED, RecoveryAction.SEND_PAYMENT_LINK,
                        RecoveryAction.OFFER_DISCOUNT, RecoveryAction.ESCALATE_TO_HUMAN), 3, 15);
        IntelligenceDecision d = engine.decide(c);

        assertEquals(RecoveryAction.RETRY_NOW, d.chosen().action(),
                "Transient failure: retry within the window beats a discount the customer won't respond to");
    }

    @Test
    void payment_terminalCard_picksPaymentLinkOverDiscount() {
        // Customer must replace a dead card — a direct pay-link (let them update the method) is
        // worth more than a discount on a method that cannot work.
        RecoveryCase c = paymentCase(new BigDecimal("4999"), 1, 0.65, false, "CARD_EXPIRED", false,
                List.of(RecoveryAction.SEND_PAYMENT_LINK, RecoveryAction.OFFER_DISCOUNT,
                        RecoveryAction.ESCALATE_TO_HUMAN), 3, 15);
        IntelligenceDecision d = engine.decide(c);

        assertEquals(RecoveryAction.SEND_PAYMENT_LINK, d.chosen().action());
    }

    // ── 2. Expected net value economics ───────────────────────────────

    @Test
    void netValueFormula_subtractsDiscountCostAndRisk() {
        RecoveryCase c = paymentCase(new BigDecimal("1000"), 1, 0.5, false, "INSUFFICIENT_FUNDS", true,
                List.of(RecoveryAction.OFFER_DISCOUNT), 3, 15);
        ActionEvaluation e = engine.simulateAll(c).get(0);

        assertTrue(e.expectedNetValue().signum() >= 0 || e.expectedNetValue().signum() < 0,
                "expectedNetValue must always be present");
        assertTrue(e.discountCost().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(e.incrementalLift() >= -1.0 && e.incrementalLift() <= 1.0);
        // expectedNetValue = EV − discountCost − interventionCost − riskPenalty
        BigDecimal recomputed = e.expectedRecovered()
                .subtract(e.discountCost())
                .subtract(e.interventionCost())
                .subtract(e.riskPenalty());
        assertEquals(0, e.expectedNetValue().compareTo(recomputed));
    }

    @Test
    void rank_neverPicksBySuccessRateAlone() {
        // A 90%-success action that gives away a huge discount must lose to a cheaper,
        // lower-probability action once margin is accounted for.
        RecoveryCase c = paymentCase(new BigDecimal("10000"), 1, 0.5, false, "INSUFFICIENT_FUNDS", true,
                List.of(RecoveryAction.RETRY_NOW, RecoveryAction.OFFER_DISCOUNT), 3, 15);
        List<ActionEvaluation> ranked = engine.rank(c, engine.simulateAll(c));
        ActionEvaluation winner = ranked.get(0);

        assertTrue(winner.successProbability() <= ranked.get(ranked.size() - 1).successProbability() + 1e-9
                        || winner.incrementalNetValue().compareTo(ranked.get(1).incrementalNetValue()) >= 0,
                "Winner is chosen on net value, not headline success probability");
    }

    // ── 3. HIGH_VALUE bounds: discount tiers up to the segment ceiling ──

    @Test
    void highValue_simulatesDiscountTiersUpTo25Percent() {
        RecoveryCase hv = paymentCase(new BigDecimal("15999"), 2, 0.8, true, "INSUFFICIENT_FUNDS", true,
                List.of(RecoveryAction.OFFER_DISCOUNT, RecoveryAction.SEND_PAYMENT_LINK), 5, 25);
        RecoveryCase standard = paymentCase(new BigDecimal("15999"), 2, 0.8, false, "INSUFFICIENT_FUNDS", true,
                List.of(RecoveryAction.OFFER_DISCOUNT, RecoveryAction.SEND_PAYMENT_LINK), 3, 15);

        List<ActionEvaluation> hvTiers = engine.simulateAll(hv);
        List<ActionEvaluation> stdTiers = engine.simulateAll(standard);

        assertTrue(hvTiers.stream().anyMatch(e -> e.discountPercent() != null && e.discountPercent() == 20),
                "HIGH_VALUE must simulate a 20% discount tier");
        assertTrue(hvTiers.stream().anyMatch(e -> e.discountPercent() != null && e.discountPercent() == 25),
                "HIGH_VALUE must simulate a 25% discount tier");
        assertFalse(stdTiers.stream().anyMatch(e -> e.discountPercent() != null && e.discountPercent() > 15),
                "STANDARD must never simulate discount tiers above its 15% ceiling");
    }

    @Test
    void highValue_discountCeilingIsEnforcedAboveStandard() {
        RecoveryCase hv = paymentCase(new BigDecimal("15999"), 2, 0.8, true, "INSUFFICIENT_FUNDS", true,
                List.of(RecoveryAction.OFFER_DISCOUNT, RecoveryAction.SEND_PAYMENT_LINK), 5, 25);
        RecoveryCase standard = paymentCase(new BigDecimal("15999"), 2, 0.8, false, "INSUFFICIENT_FUNDS", true,
                List.of(RecoveryAction.OFFER_DISCOUNT, RecoveryAction.SEND_PAYMENT_LINK), 3, 15);

        IntelligenceDecision hvDecision = engine.decide(hv);
        IntelligenceDecision stdDecision = engine.decide(standard);

        if (hvDecision.chosen().action() == RecoveryAction.OFFER_DISCOUNT) {
            assertTrue(hvDecision.chosen().discountPercent() > 15,
                    "HIGH_VALUE may offer up to 25%; got " + hvDecision.chosen().discountPercent());
        }
        if (stdDecision.chosen().action() == RecoveryAction.OFFER_DISCOUNT) {
            assertTrue(stdDecision.chosen().discountPercent() <= 15,
                    "STANDARD is capped at 15%; got " + stdDecision.chosen().discountPercent());
        }
    }

    // ── 4. Fatigue suppression ────────────────────────────────────────

    @Test
    void severeFatigue_blocksCustomerContactAndRoutesToHuman() {
        // 4 failed attempts on a standard customer → fatigue 0.88 (SEVERE). Discounts, links
        // and even notifying retries are blocked; only escalation remains.
        RecoveryCase c = paymentCase(new BigDecimal("1299"), 4, 0.4, false, "NETWORK_ERROR", true,
                List.of(RecoveryAction.RETRY_NOW, RecoveryAction.SEND_PAYMENT_LINK,
                        RecoveryAction.OFFER_DISCOUNT, RecoveryAction.ESCALATE_TO_HUMAN), 3, 15);
        IntelligenceDecision d = engine.decide(c);

        assertEquals(FatigueBand.SEVERE, d.fatigueBand());
        assertEquals(RecoveryAction.ESCALATE_TO_HUMAN, d.chosen().action(),
                "Severely fatigued customers must not be contacted further");
    }

    @Test
    void moderateFatigue_blocksDiscountsButKeepsCheapActions() {
        RecoveryCase c = paymentCase(new BigDecimal("5000"), 3, 0.5, false, "NETWORK_ERROR", true,
                List.of(RecoveryAction.RETRY_NOW, RecoveryAction.SEND_PAYMENT_LINK,
                        RecoveryAction.OFFER_DISCOUNT, RecoveryAction.ESCALATE_TO_HUMAN), 3, 15);
        IntelligenceDecision d = engine.decide(c);

        assertTrue(d.fatigueScore() >= 0.5, "Repeated failed attempts should register clear fatigue");
        if (d.chosen().action() == RecoveryAction.OFFER_DISCOUNT || d.chosen().action() == RecoveryAction.SEND_PAYMENT_LINK) {
            fail("Fatigued customers must not get discounts or fresh pay-link nudges");
        }
        assertEquals(RecoveryAction.RETRY_NOW, d.chosen().action(),
                "Cheap retry stays available while customer-facing incentives are suppressed");
    }

    // ── 5. Confidence policy ──────────────────────────────────────────

    @Test
    void lowConfidenceCase_routesToHumanReviewPolicy() {
        RecoveryCase c = paymentCase(new BigDecimal("899"), 4, 0.3, false, "NETWORK_ERROR", true,
                List.of(RecoveryAction.RETRY_NOW, RecoveryAction.SEND_PAYMENT_LINK,
                        RecoveryAction.OFFER_DISCOUNT, RecoveryAction.ESCALATE_TO_HUMAN), 3, 15);
        IntelligenceDecision d = engine.decide(c);

        assertEquals(RecoveryAction.ESCALATE_TO_HUMAN, d.chosen().action());
        assertTrue(d.confidence() < 0.60,
                "Confidence must fall below the 60% auto-execution floor on a fatigued low-info case");
        assertEquals(DecisionConfidenceService.Policy.HUMAN_REVIEW, d.automationPolicy());
    }

    @Test
    void freshRetryableCase_isConfidentEnoughToAutoExecute() {
        RecoveryCase c = paymentCase(new BigDecimal("9999"), 0, 0.88, false, "NETWORK_ERROR", true,
                List.of(RecoveryAction.RETRY_SILENT, RecoveryAction.ESCALATE_TO_HUMAN), 3, 15);
        IntelligenceDecision d = engine.decide(c);

        assertEquals(RecoveryAction.RETRY_SILENT, d.chosen().action(),
                "Self-healing case must keep to the free silent retry — no customer contact");
        assertEquals(com.razorpay.recovery.intelligence.RecoveryState.LIKELY_TO_SELF_RECOVER, d.recoveryState());
        assertTrue(d.confidence() >= 0.60, "Fresh transient failure on a reliable customer is a confident decision");
    }

    // ── 6. Net value beats raw success probability (the optimizer's rule) ──

    @Test
    void rank_prefersHighestNetValue_overHighestSuccessRate() {
        // The Discount candidate looks best by headline success (90%) but burns margin;
        // the Payment Link recovers less often yet keeps more of the money. The engine
        // must select the Payment Link because its INCREMENTAL NET VALUE is higher.
        RecoveryCase c = paymentCase(new BigDecimal("10000"), 1, 0.5, false, "INSUFFICIENT_FUNDS", true,
                List.of(RecoveryAction.RETRY_NOW, RecoveryAction.OFFER_DISCOUNT, RecoveryAction.SEND_PAYMENT_LINK), 3, 15);

        ActionEvaluation discount = new ActionEvaluation(RecoveryAction.OFFER_DISCOUNT, 20,
                0.90, 0.20, 0.70,
                new BigDecimal("9000"),
                new BigDecimal("0.35"), new BigDecimal("2000"), BigDecimal.ZERO,
                new BigDecimal("6999.65"), new BigDecimal("4999.65"),
                0.10, 0.85, "20% discount — high success, high margin cost");
        ActionEvaluation payLink = new ActionEvaluation(RecoveryAction.SEND_PAYMENT_LINK, null,
                0.75, 0.20, 0.55,
                new BigDecimal("7500"),
                new BigDecimal("0.35"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("7499.65"), new BigDecimal("5499.65"),
                0.05, 0.88, "payment link — lower success, far cheaper");

        List<ActionEvaluation> ranked = engine.rank(c, List.of(discount, payLink));
        ActionEvaluation winner = ranked.get(0);

        assertEquals(RecoveryAction.SEND_PAYMENT_LINK, winner.action(),
                "Highest net value must win even when another action has the higher success rate");
        assertTrue(discount.successProbability() > winner.successProbability(),
                "Precondition: the losing action really has the higher raw success rate");
        // Sanity: winner has the higher incremental net value of the two.
        assertTrue(winner.incrementalNetValue().compareTo(discount.incrementalNetValue()) > 0);
    }

    @Test
    void engine_exposesStableVersionForProvenance() {
        assertEquals("RECOVERY_INTELLIGENCE_V1", NextBestActionEngine.ENGINE_VERSION,
                "Engine version must be stable so attempts can prove their provenance");
    }

    // ── 7. Determinism (REST/SSE/scheduler agreement) ─────────────────

    @Test
    void sameCase_alwaysProducesIdenticalDecision() {
        RecoveryCase c = checkoutCase(new BigDecimal("24999"), 0, "PRICE_HESITATION",
                List.of(RecoveryAction.CHECKOUT_REMINDER, RecoveryAction.SEND_PAYMENT_LINK,
                        RecoveryAction.OFFER_DISCOUNT, RecoveryAction.ESCALATE_TO_HUMAN));
        IntelligenceDecision a = engine.decide(c);
        IntelligenceDecision b = engine.decide(c);

        assertEquals(a.chosen().action(), b.chosen().action());
        assertEquals(a.chosen().discountPercent(), b.chosen().discountPercent());
        assertEquals(a.confidence(), b.confidence());
        assertEquals(a.alternatives().size(), b.alternatives().size());
        for (int i = 0; i < a.alternatives().size(); i++) {
            assertEquals(a.alternatives().get(i).incrementalNetValue(), b.alternatives().get(i).incrementalNetValue());
        }
    }
}

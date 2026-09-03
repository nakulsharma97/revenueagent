package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;

import java.math.BigDecimal;

/**
 * The counterfactual evaluation of ONE candidate action for ONE case:
 *
 * <pre>
 * expectedNetValue     = expectedRecovered - discountCost - interventionCost - riskPenalty
 * incrementalNetValue  = expectedNetValue - (amount * baselineProbability)
 * </pre>
 *
 * The Next-Best-Action engine ranks candidates by incremental net value, never by
 * raw success probability alone — an action that merely collects money the customer
 * would have paid anyway has ~zero incremental value.
 *
 * @param action            candidate action
 * @param discountPercent   discount size for OFFER_DISCOUNT (null otherwise)
 * @param successProbability probability the action leads to payment (0..1)
 * @param baselineProbability probability the customer pays with NO intervention (0..1)
 * @param incrementalLift   successProbability - baselineProbability
 * @param expectedRecovered amount * successProbability
 * @param interventionCost  fixed channel cost (SMS/email)
 * @param discountCost      expected cost of the discount (amount * pct * successProbability)
 * @param riskPenalty       monetary penalty for contacting a fatigued/at-risk customer
 * @param expectedNetValue  see formula above
 * @param incrementalNetValue see formula above
 * @param riskScore         0..1 operational-risk estimate for the action
 * @param confidence        per-action confidence (0..1)
 * @param reasoning         why this number looks the way it does
 */
public record ActionEvaluation(
        RecoveryAction action,
        Integer discountPercent,
        double successProbability,
        double baselineProbability,
        double incrementalLift,
        BigDecimal expectedRecovered,
        BigDecimal interventionCost,
        BigDecimal discountCost,
        BigDecimal riskPenalty,
        BigDecimal expectedNetValue,
        BigDecimal incrementalNetValue,
        double riskScore,
        double confidence,
        String reasoning
) {

    /** Display name, e.g. "OFFER_DISCOUNT (10%)" -> "10% Discount". */
    public String displayName() {
        String base = switch (action) {
            case OFFER_DISCOUNT -> "Discount";
            default -> action.name().replace('_', ' ');
        };
        return discountPercent == null ? toTitle(base) : discountPercent + "% " + toTitle(base);
    }

    private static String toTitle(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}

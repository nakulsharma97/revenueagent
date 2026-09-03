package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic multi-action probability model. Mirrors the conversion curves of the
 * mock payment/notification services so simulated expectations match actual outcomes
 * on average — but, unlike the mocks, every probability is a PURE function of the
 * {@link RecoveryCase} features (no randomness), so all batch paths and the simulator
 * always agree and the demo numbers are reproducible.
 *
 * <p>The model encodes product economics rather than flat tables:
 * <ul>
 *   <li>a <b>payment link</b> is strongest when the customer must act themselves
 *       (terminal card failures, method switch) or when intent is high (checkout);</li>
 *   <li>a <b>discount</b> is a lever for price-hesitant carts and (mildly) for
 *       insufficient-funds customers, and is nearly wasted on transient network/UPI
 *       outages or on customers who would pay anyway — so the optimizer rarely
 *       spends margin there;</li>
 *   <li>retries decay with each prior failed attempt;</li>
 *   <li>fatigue reduces discount effectiveness (customers tune out incentives).</li>
 * </ul>
 */
public class UpliftScoringService {

    private static final double SMS_COST = 0.35;
    private static final double EMAIL_COST = 0.05;

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean isTerminalCard(String mode) {
        return "CARD_EXPIRED".equals(mode) || "INVALID_CVV".equals(mode) || "CARD_STOLEN_FLAG".equals(mode);
    }

    private static boolean isTransient(String mode) {
        return "NETWORK_ERROR".equals(mode) || "BANK_SERVER_DOWN".equals(mode)
                || "UPI_TIMEOUT".equals(mode) || "BANK_SESSION_EXPIRED".equals(mode);
    }

    /** Raw "a plain retry of the same instrument succeeds" probability for payments. */
    public double retryProbability(RecoveryCase c) {
        String mode = c.failureMode() == null ? "" : c.failureMode();
        double base = switch (mode) {
            case "NETWORK_ERROR" -> 0.75;
            case "BANK_SERVER_DOWN" -> 0.60;
            case "INSUFFICIENT_FUNDS" -> 0.35;
            case "CARD_EXPIRED", "INVALID_CVV", "CARD_STOLEN_FLAG" -> 0.02;
            case "UPI_PIN_MISMATCH" -> 0.40;
            case "UPI_TIMEOUT" -> 0.55;
            case "VPA_INVALID" -> 0.05;
            case "BANK_SESSION_EXPIRED" -> 0.30;
            default -> 0.30;
        };
        double adjusted = base + (c.reliability() - 0.5) * 0.2;
        double decay = 1.0 - 0.06 * Math.min(3, c.retryCount());
        return clamp(adjusted * decay, 0.02, 0.95);
    }

    /** Natural (no-intervention) baseline probability of payment. */
    public double baselineProbability(RecoveryCase c) {
        return switch (c.sourceType()) {
            case "PAYMENT" -> clamp(0.05, 0.5, retryProbability(c) * 0.45 + c.reliability() * 0.18);
            case "CHECKOUT" -> clamp(0.05, 0.4, intent(c) * 0.22);
            default -> c.daysOverdue() <= 30 ? 0.15 : 0.08;
        };
    }

    /** Purchase intent implied by the checkout abandonment reason. */
    private double intent(RecoveryCase c) {
        String mode = c.failureMode() == null ? "" : c.failureMode();
        return switch (mode) {
            case "PRICE_HESITATION" -> 0.5;
            case "PAYMENT_METHOD_DECLINED" -> 0.72;
            case "DISTRACTED_NO_COMPLETION" -> 0.80;
            case "TECHNICAL_ERROR" -> 0.72;
            default -> 0.6;
        };
    }

    private double successProbability(RecoveryCase c, RecoveryAction action, Integer discountPct) {
        String mode = c.failureMode() == null ? "" : c.failureMode();
        switch (action) {
            case RETRY_SILENT:
            case RETRY_NOW:
            case RETRY_SCHEDULED:
                // Free/cheap retries of the same instrument.
                return c.retryable() ? retryProbability(c) : 0.0;
            case SEND_PAYMENT_LINK:
                if (c.sourceType().equals("CHECKOUT")) {
                    return clamp(0.05, 0.65, intent(c) * 0.55);
                }
                // Payments: a pay link lets the customer act themselves — strongest for
                // terminal causes (they must update their instrument) and moderate otherwise.
                double terminalBoost = isTerminalCard(mode) || "VPA_INVALID".equals(mode) ? 0.20 : 0.0;
                double retryableBoost = c.retryable() ? 0.05 : 0.0;
                return clamp(0.15, 0.60, 0.22 + 0.12 * c.reliability() + terminalBoost + retryableBoost);
            case OFFER_DISCOUNT: {
                if (c.sourceType().equals("CHECKOUT")) {
                    int pct = discountPct == null ? 10 : discountPct;
                    boolean priceHesitant = mode.equals("PRICE_HESITATION");
                    double sensitivity = priceHesitant ? 1.6 : 0.75;
                    return clamp(0.10, 0.72, (intent(c) * 0.30 + 0.12 + pct * 0.02) * sensitivity);
                }
                int pct = discountPct == null ? 10 : discountPct;
                double causeBonus = isTerminalCard(mode) ? 0.05
                        : "INSUFFICIENT_FUNDS".equals(mode) ? 0.10
                        : "UPI_PIN_MISMATCH".equals(mode) ? 0.05 : 0.0;
                double multiplier = isTerminalCard(mode) ? 0.65
                        : "VPA_INVALID".equals(mode) ? 0.70
                        : "UPI_PIN_MISMATCH".equals(mode) ? 0.80
                        : "INSUFFICIENT_FUNDS".equals(mode) ? 1.10
                        : isTransient(mode) ? 0.30
                        : 0.75;
                double p = (0.15 + 0.08 * c.reliability() + causeBonus + pct * 0.02) * multiplier;
                if (c.reliability() > 0.75) p *= 0.9;   // don't pay for customers who would pay anyway
                if (c.fatigue() >= 0.30) p *= 0.9;       // fatigued customers tune out incentives
                return clamp(0.05, 0.65, p);
            }
            case CHECKOUT_REMINDER:
                return clamp(0.05, 0.6, intent(c) * 0.45);
            case SEND_REMINDER:
                return c.daysOverdue() <= 30 ? 0.40 : 0.20;
            case OFFER_PAYMENT_PLAN:
                return 0.50;
            case PROMISE_FOLLOWUP:
                return c.promiseBroken() ? 0.45 : 0.30;
            default:
                // ESCALATE_TO_HUMAN / ABANDON / NO_ACTION produce no automatic payment.
                return 0.0;
        }
    }

    /** Fixed channel cost of an intervention. */
    private double interventionCost(RecoveryCase c, RecoveryAction action) {
        if (action == RecoveryAction.RETRY_SILENT) return 0.0;
        if (action == RecoveryAction.ESCALATE_TO_HUMAN || action == RecoveryAction.ABANDON
                || action == RecoveryAction.NO_ACTION) return 0.0;
        return c.sourceType().equals("PAYMENT") ? SMS_COST : EMAIL_COST;
    }

    /** 0..1 operational-risk estimate (higher when the customer is fatigued). */
    public double riskScore(RecoveryCase c, RecoveryAction action) {
        double fatigue = c.fatigue();
        return switch (action) {
            case RETRY_SILENT -> 0.02;
            case RETRY_NOW, RETRY_SCHEDULED -> 0.05 + fatigue * 0.2;
            case SEND_PAYMENT_LINK -> 0.15 + fatigue * 0.4;
            case OFFER_DISCOUNT -> 0.20 + fatigue * 0.4;
            case SEND_REMINDER, CHECKOUT_REMINDER, PROMISE_FOLLOWUP -> 0.10 + fatigue * 0.35;
            case OFFER_PAYMENT_PLAN -> 0.10 + fatigue * 0.3;
            default -> 0.0;
        };
    }

    /**
     * Full counterfactual evaluation of one action against the case. Pure — safe to call
     * from anywhere (engine, simulator, recording pass) with identical results.
     */
    public ActionEvaluation evaluate(RecoveryCase c, RecoveryAction action, Integer discountPct, double baseline) {
        double p = successProbability(c, action, discountPct);
        double amount = c.amountValue();

        int discountPctVal = discountPct == null ? 0 : discountPct;
        double intervention = interventionCost(c, action);
        double expectedDiscount = amount * (discountPctVal / 100.0) * p; // discount paid only on conversion
        double risk = riskScore(c, action);
        double riskPenalty = amount * risk * 0.03;

        double ev = amount * p;
        double expectedNet = ev - expectedDiscount - intervention - riskPenalty;
        double baselineNet = amount * baseline;
        double incrementalNet = expectedNet - baselineNet;

        String reasoning;
        if (action == RecoveryAction.ESCALATE_TO_HUMAN || action == RecoveryAction.ABANDON) {
            reasoning = "No automatic conversion — routed to a human reviewer.";
        } else if (action == RecoveryAction.NO_ACTION) {
            reasoning = "Natural recovery only — no intervention cost.";
        } else if (action == RecoveryAction.RETRY_SILENT) {
            reasoning = "Free background retry; model success " + pct(p) + "%.";
        } else if (action == RecoveryAction.OFFER_DISCOUNT) {
            reasoning = "Conversion model " + pct(p) + "% incl. " + discountPctVal
                    + "% incentive, minus expected discount cost ₹" + money(expectedDiscount) + ".";
        } else {
            reasoning = "Conversion model " + pct(p) + "%.";
        }
        if (action != RecoveryAction.NO_ACTION && p > baseline + 0.02) {
            reasoning += " Incremental lift over natural baseline " + signPct(p - baseline) + "pp.";
        }

        return new ActionEvaluation(
                action,
                discountPct,
                round4(p),
                round4(baseline),
                round4(p - baseline),
                money(ev),
                money(intervention),
                money(expectedDiscount),
                money(riskPenalty),
                money(expectedNet),
                money(incrementalNet),
                round4(risk),
                round4(clamp(0.4, 0.99, 0.5 + 0.35 * Math.min(1, Math.abs(p - baseline) * 3))),
                reasoning
        );
    }

    private static String pct(double v) {
        return String.format("%.0f", v * 100);
    }

    private static String signPct(double v) {
        return String.format("%+.0f", v * 100);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}

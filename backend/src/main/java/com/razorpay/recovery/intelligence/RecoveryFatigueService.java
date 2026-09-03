package com.razorpay.recovery.intelligence;

/**
 * Models customer recovery fatigue — how much further contact is likely to annoy
 * rather than persuade. Fatigue is driven by the number of prior interventions,
 * repeated failures and (for B2B) how long the receivable has been chased.
 *
 * <p>Severe fatigue suppresses intervention entirely; high fatigue drops the
 * expensive/contact-heavy actions (discounts, pay links); moderate fatigue prefers
 * low-cost actions. High-value customers are deliberately scored at half weight —
 * more revenue at stake justifies more touchpoints before they tire.</p>
 */
public class RecoveryFatigueService {

    /** 0.0 fresh → 1.0 severe. Pure function of the case features. */
    public double score(RecoveryCase c) {
        double raw;
        switch (c.sourceType()) {
            case "CHECKOUT" -> {
                int rem = c.reminderCount();
                raw = Math.min(1.0, rem / 4.0) * 0.45 + (rem >= 2 ? 0.2 : 0) + (rem >= 3 ? 0.3 : 0);
            }
            case "RECEIVABLE" -> {
                int rem = c.reminderCount();
                raw = Math.min(1.0, rem / 5.0) * 0.3 + (rem >= 3 ? 0.15 : 0) + (c.daysOverdue() >= 60 ? 0.1 : 0);
            }
            default -> {
                int r = c.retryCount();
                raw = Math.min(1.0, r / 6.0) * 0.5
                        + (r >= 2 ? 0.10 : 0) + (r >= 3 ? 0.25 : 0) + (r >= 4 ? 0.20 : 0);
            }
        }
        double score = clamp(raw, 0.0, 1.0);
        // High-value customers tolerate (and justify) more touchpoints.
        if (c.highValue()) score *= 0.5;
        return Math.round(score * 100.0) / 100.0;
    }

    public FatigueBand band(double score) {
        if (score >= 0.85) return FatigueBand.SEVERE;
        if (score >= 0.60) return FatigueBand.HIGH;
        if (score >= 0.30) return FatigueBand.MODERATE;
        return FatigueBand.LOW;
    }

    /** Suppresses actions that would aggravate a fatigued customer (stricter than RulesEngine). */
    public boolean blockedByFatigue(RecoveryCase c, double score, com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction action) {
        if (score >= 0.85) {
            // Severe: no automated customer-facing intervention. Only free silent retries
            // or a human hand-off remain.
            return action != com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.RETRY_SILENT
                    && action != com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.ESCALATE_TO_HUMAN
                    && action != com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.ABANDON;
        }
        if (score >= 0.60) {
            // High: drop incentives and direct pay-link nudges; keep cheap reminders/retries.
            return action == com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.OFFER_DISCOUNT
                    || action == com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.SEND_PAYMENT_LINK
                    || action == com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.PROMISE_FOLLOWUP
                    || action == com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.OFFER_PAYMENT_PLAN;
        }
        if (score >= 0.30) {
            // Moderate: prefer low-cost action — no discounts.
            return action == com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.OFFER_DISCOUNT;
        }
        return false;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

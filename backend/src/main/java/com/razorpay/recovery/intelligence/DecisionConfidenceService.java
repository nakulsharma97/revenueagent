package com.razorpay.recovery.intelligence;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Turns an action evaluation into a confidence score (0..1) and an automation policy:
 *
 * <ul>
 *   <li>confidence &ge; 0.85 → {@link Policy#AUTO_EXECUTE} — the system may act alone.</li>
 *   <li>0.60 &le; confidence &lt; 0.85 → {@link Policy#SAFE_ACTION_ONLY} — only low-risk actions.</li>
 *   <li>confidence &lt; 0.60 → {@link Policy#HUMAN_REVIEW} — a human must decide.</li>
 * </ul>
 *
 * Confidence rises with the clarity of the causal signal (|incremental lift|), the
 * richness of the data (known reliability, meaningful amount, several candidates) and
 * falls with fatigue.
 */
@Service
public class DecisionConfidenceService {

    public enum Policy {
        AUTO_EXECUTE, SAFE_ACTION_ONLY, HUMAN_REVIEW
    }

    public record Assessment(double score, Policy policy) {}

    /** Actions a machine may take on its own when the policy is SAFE_ACTION_ONLY. */
    public static final List<com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction> SAFE_ACTIONS = List.of(
            com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.RETRY_SILENT,
            com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.RETRY_NOW,
            com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.RETRY_SCHEDULED,
            com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.SEND_REMINDER,
            com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction.CHECKOUT_REMINDER
    );

    public Assessment assess(RecoveryCase c, ActionEvaluation chosen, double fatigue) {
        double clarity = Math.min(1.0, Math.abs(chosen.incrementalLift()) * 3.5);
        double dataRichness = 0.5 + 0.2 * c.reliability()
                + (c.amount() != null && c.amount().doubleValue() >= 500 ? 0.1 : 0.0)
                + (c.eligible() != null && c.eligible().size() >= 3 ? 0.05 : 0.0);
        double score = 0.40 + clarity * 0.45 + dataRichness * 0.15;
        if (fatigue >= 0.6) score -= 0.12;
        score = Math.max(0.30, Math.min(0.95, score));

        Policy policy;
        if (score >= 0.85) policy = Policy.AUTO_EXECUTE;
        else if (score >= 0.60) policy = Policy.SAFE_ACTION_ONLY;
        else policy = Policy.HUMAN_REVIEW;
        return new Assessment(Math.round(score * 100.0) / 100.0, policy);
    }
}

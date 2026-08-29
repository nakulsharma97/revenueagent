package com.razorpay.recovery.service;

import com.razorpay.recovery.dto.LlmDecision;
import com.razorpay.recovery.model.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The hard boundary of the "bounded recovery workflow" required by the brief.
 * Nothing downstream — LLM included — may act outside what this class allows.
 */
@Component
public class RulesEngine {

    @Value("${recovery.max-retries}")
    private int maxRetries;

    @Value("${recovery.max-discount-percent}")
    private int maxDiscountPercent;

    @Value("${recovery.min-amount-for-discount}")
    private BigDecimal minAmountForDiscount;

    /** Actions the transaction is currently allowed to take — the LLM must pick from this set. */
    public List<RecoveryAction> eligibleActions(Transaction tx) {
        List<RecoveryAction> eligible = new ArrayList<>();

        if (tx.getRetryCount() >= maxRetries) {
            // Retries exhausted: only a human or a payment-link nudge remains on the table.
            eligible.add(RecoveryAction.SEND_PAYMENT_LINK);
            eligible.add(RecoveryAction.ESCALATE_TO_HUMAN);
            eligible.add(RecoveryAction.ABANDON);
            return eligible;
        }

        if (tx.getFailureReason() != null && tx.getFailureReason().isRetryable()) {
            eligible.add(RecoveryAction.RETRY_NOW);
            eligible.add(RecoveryAction.RETRY_SCHEDULED);
        }

        eligible.add(RecoveryAction.SEND_PAYMENT_LINK);

        if (tx.getAmount() != null && tx.getAmount().compareTo(minAmountForDiscount) >= 0) {
            eligible.add(RecoveryAction.OFFER_DISCOUNT);
        }

        eligible.add(RecoveryAction.ESCALATE_TO_HUMAN);
        return eligible;
    }

    /**
     * Re-validates whatever the LLM proposed against the hard limits.
     * Returns a corrected/rejected decision rather than trusting the model.
     */
    public LlmDecision enforceBounds(Transaction tx, LlmDecision proposed) {
        Set<RecoveryAction> allowed = Set.copyOf(eligibleActions(tx));

        if (proposed == null || !allowed.contains(proposed.action())) {
            return safestFallback(tx, allowed);
        }

        if (proposed.action() == RecoveryAction.OFFER_DISCOUNT) {
            int pct = proposed.discountPercent() == null ? 0 : proposed.discountPercent();
            if (pct > maxDiscountPercent || pct <= 0) {
                return new LlmDecision(
                        RecoveryAction.OFFER_DISCOUNT,
                        proposed.reasoning() + " [capped by RulesEngine to policy max]",
                        proposed.confidence(),
                        maxDiscountPercent
                );
            }
        }

        return proposed;
    }

    private LlmDecision safestFallback(Transaction tx, Set<RecoveryAction> allowed) {
        RecoveryAction fallback = allowed.contains(RecoveryAction.SEND_PAYMENT_LINK)
                ? RecoveryAction.SEND_PAYMENT_LINK
                : RecoveryAction.ESCALATE_TO_HUMAN;
        return new LlmDecision(fallback, "Rules-engine fallback: proposed action was out of bounds.", 0.5, null);
    }
}

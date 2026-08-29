package com.razorpay.recovery.service;

import com.razorpay.recovery.dto.ActionBreakdown;
import com.razorpay.recovery.dto.BatchMetrics;
import com.razorpay.recovery.dto.FunnelData;
import com.razorpay.recovery.model.RecoveryAttempt;
import com.razorpay.recovery.model.RecoveryAttempt.AttemptOutcome;
import com.razorpay.recovery.model.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.model.Transaction;
import com.razorpay.recovery.model.Transaction.FailureReason;
import com.razorpay.recovery.model.Transaction.TransactionStatus;
import com.razorpay.recovery.repository.RecoveryAttemptRepository;
import com.razorpay.recovery.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Turns raw attempt/transaction rows into the "honest metrics" the brief asks for —
 * revenue recovered net of intervention cost, plus a simulated naive baseline.
 *
 * The baseline is NOT a hardcoded percentage — it runs the SAME probability model
 * that MockPaymentGatewayService uses, but with exactly ONE retry per transaction,
 * no discounts, no payment links, no LLM, and zero intervention cost. This makes
 * the agent-vs-baseline comparison apples-to-apples on the SAME batch.
 */
@Service
public class MetricsService {

    private final TransactionRepository transactionRepository;
    private final RecoveryAttemptRepository attemptRepository;

    public MetricsService(TransactionRepository transactionRepository, RecoveryAttemptRepository attemptRepository) {
        this.transactionRepository = transactionRepository;
        this.attemptRepository = attemptRepository;
    }

    public BatchMetrics currentMetrics() {
        List<Transaction> all = transactionRepository.findAll();
        List<RecoveryAttempt> attempts = attemptRepository.findAll();

        long totalAtRisk = all.size();
        // Only count real outcomes (SUCCESS/FAILED), not PENDING (cooldown-skip rows).
        long recoveredCount = attempts.stream()
                .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS).count();

        BigDecimal revenueRecovered = attempts.stream()
                .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS)
                .map(RecoveryAttempt::getAmountRecovered)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal interventionCost = attempts.stream()
                .map(RecoveryAttempt::getInterventionCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netRecovered = revenueRecovered.subtract(interventionCost);

        double recoveryRate = totalAtRisk == 0 ? 0
                : (recoveredCount * 100.0) / totalAtRisk;

        // Simulate the naive baseline: one retry per transaction, no discounts,
        // no LLM, same probability model as MockPaymentGatewayService.
        BaselineResult baseline = simulateBaseline(all);

        return new BatchMetrics(
                totalAtRisk,
                recoveredCount,
                revenueRecovered.setScale(2, RoundingMode.HALF_UP),
                interventionCost.setScale(2, RoundingMode.HALF_UP),
                netRecovered.setScale(2, RoundingMode.HALF_UP),
                Math.round(recoveryRate * 100.0) / 100.0,
                baseline.recovered.setScale(2, RoundingMode.HALF_UP),
                baseline.count
        );
    }

    /**
     * Simulates a naive "retry every transaction exactly once" baseline.
     * Uses the same success-probability model as MockPaymentGatewayService
     * but with a deterministic seed so results are reproducible across calls.
     * No discounts, no payment links, no intervention cost — just a raw retry.
     */
    BaselineResult simulateBaseline(List<Transaction> transactions) {
        // Deterministic seed: same batch always produces the same baseline.
        Random baselineRandom = new Random(42);
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            double successProbability = switch (tx.getFailureReason()) {
                case NETWORK_ERROR -> 0.75;
                case BANK_SERVER_DOWN -> 0.6;
                case INSUFFICIENT_FUNDS -> 0.35;
                case CARD_EXPIRED, INVALID_CVV, CARD_STOLEN_FLAG -> 0.02;
            };
            // Same reliability adjustment as MockPaymentGatewayService
            double adjusted = Math.min(0.95, successProbability
                    + (tx.getSubscription().getCustomer().getPaymentReliabilityScore() - 0.5) * 0.2);

            if (baselineRandom.nextDouble() < adjusted) {
                count++;
                total = total.add(tx.getAmount());
            }
        }

        return new BaselineResult(count, total);
    }

    /** Internal result of baseline simulation. */
    record BaselineResult(long count, BigDecimal recovered) {
    }

    /** Status distribution across the recovery pipeline. */
    public FunnelData funnelData() {
        List<Transaction> all = transactionRepository.findAll();
        List<RecoveryAttempt> attempts = attemptRepository.findAll();

        long atRisk = all.stream().filter(t -> t.getStatus() == TransactionStatus.AT_RISK).count();
        long inRecovery = all.stream().filter(t -> t.getStatus() == TransactionStatus.IN_RECOVERY).count();
        long recovered = all.stream().filter(t -> t.getStatus() == TransactionStatus.RECOVERED).count();
        long lost = all.stream().filter(t -> t.getStatus() == TransactionStatus.LOST).count();

        long pendingAttempts = attempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.PENDING).count();
        long succeededAttempts = attempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS).count();
        long failedAttempts = attempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.FAILED).count();

        return new FunnelData(atRisk, inRecovery, recovered, lost, pendingAttempts, succeededAttempts, failedAttempts);
    }

    /** Per-batch metrics history. */
    public List<Map<String, Object>> batchHistory() {
        List<RecoveryAttempt> attempts = attemptRepository.findAll();
        // Group by batchId
        Map<String, List<RecoveryAttempt>> byBatch = new LinkedHashMap<>();
        for (RecoveryAttempt a : attempts) {
            String bid = a.getBatchId();
            if (bid == null) bid = "unknown";
            byBatch.computeIfAbsent(bid, k -> new ArrayList<>()).add(a);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int batchNum = 0;
        for (Map.Entry<String, List<RecoveryAttempt>> entry : byBatch.entrySet()) {
            List<RecoveryAttempt> batchAttempts = entry.getValue();
            long total = batchAttempts.size();
            long succeeded = batchAttempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS).count();
            long failed = batchAttempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.FAILED).count();
            long skipped = batchAttempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.PENDING).count();
            BigDecimal recovered = batchAttempts.stream()
                    .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS)
                    .map(RecoveryAttempt::getAmountRecovered)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cost = batchAttempts.stream()
                    .map(RecoveryAttempt::getInterventionCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            batchNum++;
            result.add(Map.of(
                    "batchNumber", batchNum,
                    "batchId", entry.getKey(),
                    "totalAttempts", total,
                    "succeeded", succeeded,
                    "failed", failed,
                    "skipped", skipped,
                    "revenueRecovered", recovered.setScale(2, RoundingMode.HALF_UP),
                    "interventionCost", cost.setScale(2, RoundingMode.HALF_UP),
                    "netRecovered", recovered.subtract(cost).setScale(2, RoundingMode.HALF_UP)
            ));
        }
        return result;
    }

    /** Per-action success rate breakdown. */
    public List<ActionBreakdown> actionBreakdown() {
        List<RecoveryAttempt> attempts = attemptRepository.findAll();
        List<ActionBreakdown> result = new ArrayList<>();

        for (RecoveryAction action : RecoveryAction.values()) {
            List<RecoveryAttempt> actionAttempts = attempts.stream()
                    .filter(a -> a.getActionTaken() == action)
                    .toList();
            if (actionAttempts.isEmpty()) continue;

            long total = actionAttempts.size();
            long successes = actionAttempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS).count();
            long failures = actionAttempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.FAILED).count();
            double successRate = total == 0 ? 0 : Math.round((successes * 1000.0) / total) / 10.0;

            BigDecimal amountRecovered = actionAttempts.stream()
                    .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS)
                    .map(RecoveryAttempt::getAmountRecovered)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal interventionCost = actionAttempts.stream()
                    .map(RecoveryAttempt::getInterventionCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            result.add(new ActionBreakdown(
                    action.name(), total, successes, failures,
                    successRate, amountRecovered, interventionCost
            ));
        }
        return result;
    }
}

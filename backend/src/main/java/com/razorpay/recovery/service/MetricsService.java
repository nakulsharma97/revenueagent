package com.razorpay.recovery.service;

import com.razorpay.recovery.dto.ActionBreakdown;
import com.razorpay.recovery.dto.BatchMetrics;
import com.razorpay.recovery.dto.FunnelData;
import com.razorpay.recovery.model.RecoveryAttempt;
import com.razorpay.recovery.model.RecoveryAttempt.AttemptOutcome;
import com.razorpay.recovery.model.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.model.Transaction;
import com.razorpay.recovery.model.Transaction.TransactionStatus;
import com.razorpay.recovery.repository.RecoveryAttemptRepository;
import com.razorpay.recovery.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw attempt/transaction rows into the "honest metrics" the brief asks for —
 * revenue recovered net of intervention cost, plus a naive baseline for comparison.
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

        // Baseline: what a naive "retry every failure exactly once, no discounts, no LLM" policy
        // would have recovered — a flat 35% blended success rate, no intervention cost tracked.
        BigDecimal baselineRecovered = all.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(0.35))
                .setScale(2, RoundingMode.HALF_UP);

        return new BatchMetrics(
                totalAtRisk,
                recoveredCount,
                revenueRecovered.setScale(2, RoundingMode.HALF_UP),
                interventionCost.setScale(2, RoundingMode.HALF_UP),
                netRecovered.setScale(2, RoundingMode.HALF_UP),
                Math.round(recoveryRate * 100.0) / 100.0,
                baselineRecovered
        );
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

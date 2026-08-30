package com.razorpay.recovery.metrics;

import com.razorpay.recovery.metrics.ActionBreakdown;
import com.razorpay.recovery.metrics.BatchMetrics;
import com.razorpay.recovery.metrics.FunnelData;
import com.razorpay.recovery.customer.*;
import com.razorpay.recovery.subscription.*;
import com.razorpay.recovery.transaction.*;
import com.razorpay.recovery.checkout.*;
import com.razorpay.recovery.receivable.*;
import com.razorpay.recovery.recovery.*;
import com.razorpay.recovery.checkout.CheckoutSession.CheckoutStatus;
import com.razorpay.recovery.receivable.Receivable.ReceivableStatus;
import com.razorpay.recovery.recovery.RecoveryAttempt.AttemptOutcome;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.recovery.RecoveryAttempt.SourceType;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import com.razorpay.recovery.transaction.*;
import com.razorpay.recovery.customer.*;
import com.razorpay.recovery.subscription.*;
import com.razorpay.recovery.checkout.*;
import com.razorpay.recovery.receivable.*;
import com.razorpay.recovery.recovery.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Metrics across all three revenue sources: payment failures, checkout abandonment, receivables.
 */
@Service
public class MetricsService {

    private final TransactionRepository transactionRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ReceivableRepository receivableRepository;
    private final RecoveryAttemptRepository attemptRepository;

    public MetricsService(TransactionRepository transactionRepository,
                          CheckoutSessionRepository checkoutSessionRepository,
                          ReceivableRepository receivableRepository,
                          RecoveryAttemptRepository attemptRepository) {
        this.transactionRepository = transactionRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.receivableRepository = receivableRepository;
        this.attemptRepository = attemptRepository;
    }

    public BatchMetrics currentMetrics() {
        List<Transaction> allTx = transactionRepository.findAll();
        List<CheckoutSession> allSessions = checkoutSessionRepository.findAll();
        List<Receivable> allReceivables = receivableRepository.findAll();
        List<RecoveryAttempt> attempts = attemptRepository.findAll();

        long totalAtRisk = allTx.size() + allSessions.size() + allReceivables.size();

        long paymentAtRisk = allTx.size();
        long checkoutAtRisk = allSessions.size();
        long receivableAtRisk = allReceivables.size();

        long recoveredCount = attempts.stream()
                .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS).count();
        long paymentRecovered = attempts.stream()
                .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS && a.getSourceType() == SourceType.PAYMENT).count();
        long checkoutRecovered = attempts.stream()
                .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS && a.getSourceType() == SourceType.CHECKOUT).count();
        long receivableRecovered = attempts.stream()
                .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS && a.getSourceType() == SourceType.RECEIVABLE).count();

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

        // Simulate baseline across all three source types
        BaselineResult baseline = simulateBaseline(allTx, allSessions, allReceivables);

        // Source breakdown
        Map<String, Object> bySource = new LinkedHashMap<>();
        bySource.put("payment", Map.of(
                "atRisk", paymentAtRisk,
                "recovered", paymentRecovered,
                "label", "Payment Failures"
        ));
        bySource.put("checkout", Map.of(
                "atRisk", checkoutAtRisk,
                "recovered", checkoutRecovered,
                "label", "Checkout Abandonment"
        ));
        bySource.put("receivable", Map.of(
                "atRisk", receivableAtRisk,
                "recovered", receivableRecovered,
                "label", "Overdue Receivables"
        ));

        return new BatchMetrics(
                totalAtRisk,
                recoveredCount,
                revenueRecovered.setScale(2, RoundingMode.HALF_UP),
                interventionCost.setScale(2, RoundingMode.HALF_UP),
                netRecovered.setScale(2, RoundingMode.HALF_UP),
                Math.round(recoveryRate * 100.0) / 100.0,
                baseline.recovered.setScale(2, RoundingMode.HALF_UP),
                baseline.count,
                paymentAtRisk, checkoutAtRisk, receivableAtRisk,
                paymentRecovered, checkoutRecovered, receivableRecovered,
                bySource
        );
    }

    BaselineResult simulateBaseline(List<Transaction> txs, List<CheckoutSession> sessions, List<Receivable> receivables) {
        Random baselineRandom = new Random(42);
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;

        // Payment failures baseline
        for (Transaction tx : txs) {
            double prob = switch (tx.getFailureReason()) {
                case NETWORK_ERROR -> 0.75;
                case BANK_SERVER_DOWN -> 0.6;
                case INSUFFICIENT_FUNDS -> 0.35;
                case CARD_EXPIRED, INVALID_CVV, CARD_STOLEN_FLAG -> 0.02;
            };
            double adjusted = Math.min(0.95, prob
                    + (tx.getSubscription().getCustomer().getPaymentReliabilityScore() - 0.5) * 0.2);
            if (baselineRandom.nextDouble() < adjusted) {
                count++;
                total = total.add(tx.getAmount());
            }
        }

        // Checkout abandonment baseline — one reminder attempt
        for (CheckoutSession s : sessions) {
            double prob = 0.25; // flat checkout reminder conversion
            if (baselineRandom.nextDouble() < prob) {
                count++;
                total = total.add(s.getCartAmount());
            }
        }

        // Receivables baseline — one payment reminder
        for (Receivable r : receivables) {
            double prob = r.getDaysOverdue() <= 30 ? 0.40 : 0.20;
            if (baselineRandom.nextDouble() < prob) {
                count++;
                total = total.add(r.getInvoiceAmount());
            }
        }

        return new BaselineResult(count, total);
    }

    record BaselineResult(long count, BigDecimal recovered) {
    }

    public FunnelData funnelData() {
        List<Transaction> allTx = transactionRepository.findAll();
        List<CheckoutSession> allSessions = checkoutSessionRepository.findAll();
        List<Receivable> allReceivables = receivableRepository.findAll();
        List<RecoveryAttempt> attempts = attemptRepository.findAll();

        // Combined status counts
        long atRisk = allTx.stream().filter(t -> t.getStatus() == TransactionStatus.AT_RISK).count()
                + allSessions.stream().filter(s -> s.getStatus() == CheckoutStatus.ABANDONED).count()
                + allReceivables.stream().filter(r -> r.getStatus() == ReceivableStatus.OVERDUE).count();

        long inRecovery = allTx.stream().filter(t -> t.getStatus() == TransactionStatus.IN_RECOVERY).count()
                + allSessions.stream().filter(s -> s.getStatus() == CheckoutStatus.ABANDONED).count()  // still being worked
                + allReceivables.stream().filter(r -> r.getStatus() == ReceivableStatus.OVERDUE).count();

        long recovered = allTx.stream().filter(t -> t.getStatus() == TransactionStatus.RECOVERED).count()
                + allSessions.stream().filter(s -> s.getStatus() == CheckoutStatus.RECOVERED).count()
                + allReceivables.stream().filter(r -> r.getStatus() == ReceivableStatus.RECOVERED).count();

        long lost = allTx.stream().filter(t -> t.getStatus() == TransactionStatus.LOST).count()
                + allSessions.stream().filter(s -> s.getStatus() == CheckoutStatus.LOST).count()
                + allReceivables.stream().filter(r -> r.getStatus() == ReceivableStatus.WRITTEN_OFF).count();

        long pendingAttempts = attempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.PENDING).count();
        long succeededAttempts = attempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS).count();
        long failedAttempts = attempts.stream().filter(a -> a.getOutcome() == AttemptOutcome.FAILED).count();

        return new FunnelData(atRisk, inRecovery, recovered, lost, pendingAttempts, succeededAttempts, failedAttempts);
    }

    public List<Map<String, Object>> batchHistory() {
        List<RecoveryAttempt> attempts = attemptRepository.findAll();
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

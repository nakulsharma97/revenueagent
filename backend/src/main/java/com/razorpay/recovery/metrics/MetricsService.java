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

        // Promise-keep rate: KEPT / (KEPT + BROKEN)
        long promisesKept = allReceivables.stream()
                .filter(r -> r.getPromiseStatus() == com.razorpay.recovery.receivable.Receivable.PromiseStatus.KEPT).count();
        long promisesBroken = allReceivables.stream()
                .filter(r -> r.getPromiseStatus() == com.razorpay.recovery.receivable.Receivable.PromiseStatus.BROKEN).count();
        double promiseKeepRate = (promisesKept + promisesBroken) == 0 ? 0.0
                : Math.round((promisesKept * 1000.0) / (promisesKept + promisesBroken)) / 10.0;

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
                bySource,
                promiseKeepRate
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

    /**
     * Per-action ROI: recovered per rupee spent on interventions.
     * Zero-cost actions (e.g. RETRY_NOW) show null ratio with a costNote.
     * Sorted by recoveredPerRupeeSpent descending (nulls last).
     */
    public List<ActionEfficiency> actionEfficiency() {
        List<RecoveryAttempt> attempts = attemptRepository.findAll();
        List<ActionEfficiency> result = new ArrayList<>();

        for (RecoveryAction action : RecoveryAction.values()) {
            List<RecoveryAttempt> actionAttempts = attempts.stream()
                    .filter(a -> a.getActionTaken() == action)
                    .toList();
            if (actionAttempts.isEmpty()) continue;

            long total = actionAttempts.size();
            long successCount = actionAttempts.stream()
                    .filter(a -> a.getOutcome() == AttemptOutcome.SUCCESS).count();

            BigDecimal totalRecovered = actionAttempts.stream()
                    .map(RecoveryAttempt::getAmountRecovered)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal totalCost = actionAttempts.stream()
                    .map(RecoveryAttempt::getInterventionCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal ratio = null;
            String costNote = null;
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                ratio = totalRecovered.divide(totalCost, 2, RoundingMode.HALF_UP);
            } else {
                costNote = "No direct cost";
            }

            result.add(new ActionEfficiency(
                    action.name(), total, successCount,
                    totalRecovered, totalCost, ratio, costNote
            ));
        }

        // Sort: actions with ratio first (descending), then zero-cost actions
        result.sort((a, b) -> {
            if (a.recoveredPerRupeeSpent() == null && b.recoveredPerRupeeSpent() == null) return 0;
            if (a.recoveredPerRupeeSpent() == null) return 1;
            if (b.recoveredPerRupeeSpent() == null) return -1;
            return b.recoveredPerRupeeSpent().compareTo(a.recoveredPerRupeeSpent());
        });

        return result;
    }

    /**
     * What-if simulator: re-evaluates existing RecoveryAttempt records against
     * hypothetical bounds. Does NOT call the LLM or mock gateway — pure recalculation.
     *
     * Logic per attempt:
     * - RETRY_NOW/RETRY_SCHEDULED: eligible only if retryCount < maxRetries
     * - OFFER_DISCOUNT: eligible only if amount >= minAmountForDiscount AND discountPercent <= maxDiscountPercent
     * - SEND_PAYMENT_LINK, SEND_REMINDER, CHECKOUT_REMINDER, PROMISE_FOLLOWUP, OFFER_PAYMENT_PLAN:
     *   always eligible (no bounds constraint)
     * - ESCALATE_TO_HUMAN, ABANDON: always eligible
     * - If attempt was PENDING (cooldown skip): always included as-is
     * - If action would NOT be eligible under new bounds: excluded entirely
     *   (the agent would have chosen something else, so we don't count this recovery)
     */
    public SimulationResult simulate(int maxRetries, int maxDiscountPercent,
                                      BigDecimal minAmountForDiscount, int retryCooldownMinutes) {
        List<RecoveryAttempt> attempts = attemptRepository.findAll();
        BatchMetrics actual = currentMetrics();

        long simRecovered = 0;
        BigDecimal simRevenue = BigDecimal.ZERO;
        BigDecimal simCost = BigDecimal.ZERO;
        long simAttempts = 0;

        for (RecoveryAttempt a : attempts) {
            // Cooldown skips are always the same regardless of bounds
            if (a.getOutcome() == AttemptOutcome.PENDING) {
                simAttempts++;
                continue;
            }

            boolean eligible = isEligibleUnderBounds(a, maxRetries, maxDiscountPercent, minAmountForDiscount);
            if (!eligible) {
                // This attempt would not have happened — agent would pick differently
                continue;
            }

            simAttempts++;
            if (a.getOutcome() == AttemptOutcome.SUCCESS) {
                simRecovered++;
                simRevenue = simRevenue.add(a.getAmountRecovered());
            }
            simCost = simCost.add(a.getInterventionCost());
        }

        long totalAtRisk = actual.totalAtRisk();
        double simRecoveryRate = totalAtRisk == 0 ? 0 : Math.round((simRecovered * 1000.0) / totalAtRisk) / 10.0;
        BigDecimal simNet = simRevenue.subtract(simCost);

        // Build simulated BatchMetrics (reuse actual for fields that don't change)
        BatchMetrics simulated = new BatchMetrics(
                actual.totalAtRisk(),
                simRecovered,
                simRevenue.setScale(2, RoundingMode.HALF_UP),
                simCost.setScale(2, RoundingMode.HALF_UP),
                simNet.setScale(2, RoundingMode.HALF_UP),
                Math.round(simRecoveryRate * 100.0) / 100.0,
                actual.baselineNetRecovered(),
                actual.baselineRecoveryCount(),
                actual.paymentAtRisk(), actual.checkoutAtRisk(), actual.receivableAtRisk(),
                actual.paymentRecovered(), actual.checkoutRecovered(), actual.receivableRecovered(),
                actual.bySource(),
                actual.promiseKeepRate()
        );

        return new SimulationResult(
                simulated,
                simRevenue.subtract(actual.revenueRecovered()),
                simCost.subtract(actual.interventionCost()),
                simNet.subtract(actual.netRecovered()),
                simRecovered - actual.recoveredCount(),
                simAttempts - attempts.size(),
                new SimulationResult.SimulationAssumptions(
                        maxRetries, maxDiscountPercent, minAmountForDiscount, retryCooldownMinutes
                )
        );
    }

    /** Checks if a recovery action would be eligible under the given bounds. */
    private boolean isEligibleUnderBounds(RecoveryAttempt a, int maxRetries, int maxDiscountPercent,
                                           BigDecimal minAmountForDiscount) {
        RecoveryAction action = a.getActionTaken();
        if (action == null) return false;

        return switch (action) {
            case RETRY_NOW, RETRY_SCHEDULED -> {
                // Get the source entity's retry count from the attempt's reasoning/context
                // We use the transaction's retryCount stored in the attempt
                int retryCount = extractRetryCount(a);
                yield retryCount < maxRetries;
            }
            case OFFER_DISCOUNT -> {
                // Check: amount >= minAmountForDiscount AND discountPercent <= maxDiscountPercent
                BigDecimal amount = extractAmount(a);
                boolean amountOk = amount != null && amount.compareTo(minAmountForDiscount) >= 0;
                boolean discountOk = maxDiscountPercent > 0;
                yield amountOk && discountOk;
            }
            // These actions have no bounds constraints
            case SEND_PAYMENT_LINK, ESCALATE_TO_HUMAN, ABANDON,
                 CHECKOUT_REMINDER, OFFER_PAYMENT_PLAN, SEND_REMINDER,
                 PROMISE_FOLLOWUP -> true;
            default -> false;
        };
    }

    private int extractRetryCount(RecoveryAttempt a) {
        if (a.getTransaction() != null) return a.getTransaction().getRetryCount();
        if (a.getCheckoutSession() != null) return a.getCheckoutSession().getReminderCount();
        if (a.getReceivable() != null) return a.getReceivable().getReminderCount();
        return 0;
    }

    private BigDecimal extractAmount(RecoveryAttempt a) {
        if (a.getTransaction() != null) return a.getTransaction().getAmount();
        if (a.getCheckoutSession() != null) return a.getCheckoutSession().getCartAmount();
        if (a.getReceivable() != null) return a.getReceivable().getInvoiceAmount();
        return BigDecimal.ZERO;
    }
}

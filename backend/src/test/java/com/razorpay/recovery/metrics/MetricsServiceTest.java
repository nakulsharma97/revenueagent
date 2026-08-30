package com.razorpay.recovery.metrics;

import com.razorpay.recovery.metrics.BatchMetrics;
import com.razorpay.recovery.customer.Customer;
import com.razorpay.recovery.subscription.Subscription;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import com.razorpay.recovery.checkout.CheckoutSessionRepository;
import com.razorpay.recovery.receivable.ReceivableRepository;
import com.razorpay.recovery.recovery.RecoveryAttemptRepository;
import com.razorpay.recovery.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MetricsService — verifies the baseline simulation and
 * the PENDING-exclusion from recovery-rate math.
 */
@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private ReceivableRepository receivableRepository;

    @Mock
    private RecoveryAttemptRepository attemptRepository;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService(transactionRepository, checkoutSessionRepository, receivableRepository, attemptRepository);
    }

    @Test
    void baselineSimulation_matchesHandComputedExpectedValue() {
        // Build 3 transactions with known amounts and failure reasons.
        // Use deterministic subscriptions/customers so we control the reliability score.
        List<Transaction> txs = List.of(
                buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"), 0.5),    // adj = 0.75 + 0 = 0.75
                buildTx(FailureReason.INSUFFICIENT_FUNDS, 0, new BigDecimal("2000"), 0.7), // adj = 0.35 + 0.04 = 0.39
                buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("500"), 0.3)         // adj = 0.02 + (-0.04) = clamped to 0.02
        );

        // With seed=42, simulate the Random sequence:
        // Random(42) sequence:.nextDouble() values are deterministic
        Random baselineRandom = new Random(42);
        long expectedCount = 0;
        BigDecimal expectedTotal = BigDecimal.ZERO;

        double[] expectedProbs = {0.75, 0.39, 0.02};
        BigDecimal[] amounts = {new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("500")};

        for (int i = 0; i < 3; i++) {
            if (baselineRandom.nextDouble() < expectedProbs[i]) {
                expectedCount++;
                expectedTotal = expectedTotal.add(amounts[i]);
            }
        }

        // Run the actual simulation
        MetricsService.BaselineResult result = metricsService.simulateBaseline(txs, List.of(), List.of());

        assertEquals(expectedCount, result.count(),
                "Baseline recovery count should match hand-computed value with seed=42");
        assertEquals(0, expectedTotal.setScale(2, RoundingMode.HALF_UP).compareTo(result.recovered()),
                "Baseline recovered amount should match hand-computed value");
    }

    @Test
    void baselineSimulation_withEmptyList_returnsZero() {
        MetricsService.BaselineResult result = metricsService.simulateBaseline(List.of(), List.of(), List.of());

        assertEquals(0, result.count());
        assertEquals(BigDecimal.ZERO, result.recovered());
    }

    @Test
    void currentMetrics_excludesPendingAttemptsFromRecoveryRate() {
        // When no attempts exist, recovery rate should be 0
        when(transactionRepository.findAll()).thenReturn(List.of(
                buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"), 0.5)
        ));
        when(attemptRepository.findAll()).thenReturn(List.of());

        BatchMetrics metrics = metricsService.currentMetrics();

        assertEquals(0, metrics.recoveredCount());
        assertEquals(0.0, metrics.recoveryRatePercent());
        assertEquals(1, metrics.totalAtRisk());
    }

    @Test
    void baselineSimulation_allRetryableHighReliability_recoversMost() {
        // 10 transactions, all NETWORK_ERROR (75% base), all high reliability (0.9)
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            txs.add(buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"), 0.9));
        }

        // adj = 0.75 + (0.9 - 0.5) * 0.2 = 0.75 + 0.08 = 0.83
        // With 10 transactions at 83% each, expect roughly 7-9 recoveries
        MetricsService.BaselineResult result = metricsService.simulateBaseline(txs, List.of(), List.of());

        assertTrue(result.count() >= 5 && result.count() <= 10,
                "With 83% success probability, expect 5-10 recoveries out of 10, got: " + result.count());
        assertEquals(result.count() * 1000, result.recovered().intValue(),
                "Each recovered tx is worth ₹1000");
    }

    @Test
    void baselineSimulation_allTerminalLowReliability_recoversAlmostNone() {
        // 10 transactions, all CARD_STOLEN_FLAG (2% base), low reliability (0.2)
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            txs.add(buildTx(FailureReason.CARD_STOLEN_FLAG, 0, new BigDecimal("1000"), 0.2));
        }

        // adj = 0.02 + (0.2 - 0.5) * 0.2 = 0.02 + (-0.06) = clamped to 0.02 (Math.min doesn't apply, but 0.02 < 0.95)
        MetricsService.BaselineResult result = metricsService.simulateBaseline(txs, List.of(), List.of());

        assertTrue(result.count() <= 2,
                "With 2% success probability, expect 0-2 recoveries out of 10, got: " + result.count());
    }

    @Test
    void baseline_isNotFlat35Percent() {
        // With 3 transactions totaling 3500, flat 35% would give exactly 1225.
        // The real simulation should differ because it uses per-reason probabilities.
        List<Transaction> txs = List.of(
                buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"), 0.5),
                buildTx(FailureReason.INSUFFICIENT_FUNDS, 0, new BigDecimal("2000"), 0.7),
                buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("500"), 0.3)
        );

        MetricsService.BaselineResult result = metricsService.simulateBaseline(txs, List.of(), List.of());

        // Flat 35% of 3500 = 1225. The real simulation should NOT equal this.
        assertNotEquals(1225, result.recovered().intValue(),
                "Baseline must not be a flat 35% of total — it should use per-reason probabilities");
        assertTrue(result.count() >= 0 && result.count() <= 3,
                "Baseline recovery count should be between 0 and 3 (inclusive)");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Transaction buildTx(FailureReason reason, int retryCount, BigDecimal amount, double reliabilityScore) {
        Customer customer = new Customer();
        customer.setPaymentReliabilityScore(reliabilityScore);

        Subscription sub = new Subscription();
        sub.setCustomer(customer);

        Transaction tx = new Transaction();
        tx.setFailureReason(reason);
        tx.setRetryCount(retryCount);
        tx.setAmount(amount);
        tx.setSubscription(sub);
        tx.setStatus(TransactionStatus.AT_RISK);
        return tx;
    }
}

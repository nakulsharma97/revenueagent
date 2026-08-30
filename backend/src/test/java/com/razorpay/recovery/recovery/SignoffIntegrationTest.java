package com.razorpay.recovery.recovery;

import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.recovery.DecisionResult;
import com.razorpay.recovery.recovery.EnforcedDecision;
import com.razorpay.recovery.recovery.LlmDecision;
import com.razorpay.recovery.recovery.RecoveryAttempt;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import com.razorpay.recovery.checkout.CheckoutSessionRepository;
import com.razorpay.recovery.receivable.ReceivableRepository;
import com.razorpay.recovery.recovery.RecoveryAttemptRepository;
import com.razorpay.recovery.recovery.mocks.MockPaymentGatewayService;
import com.razorpay.recovery.recovery.mocks.MockNotificationService;
import com.razorpay.recovery.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration-style tests for the orchestrator's signoff flagging logic.
 * Mocks DecisionAgentService (which now exposes decideWithMeta) and verifies
 * that RecoveryAttempt fields are set correctly.
 */
@ExtendWith(MockitoExtension.class)
class SignoffIntegrationTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private ReceivableRepository receivableRepository;

    @Mock
    private RecoveryAttemptRepository attemptRepository;

    @Mock
    private DecisionAgentService decisionAgentService;

    @Mock
    private MockPaymentGatewayService paymentGateway;

    @Mock
    private MockNotificationService notificationService;

    private RecoveryOrchestratorService orchestrator;

    @BeforeEach
    void setUp() throws Exception {
        BoundsConfig boundsConfig = new BoundsConfig();
        boundsConfig.setMaxRetries(3);
        boundsConfig.setMaxDiscountPercent(15);
        boundsConfig.setMinAmountForDiscount(new BigDecimal("500"));
        boundsConfig.setRetryCooldownMinutes(60);
        orchestrator = new RecoveryOrchestratorService(
                transactionRepository, checkoutSessionRepository, receivableRepository,
                attemptRepository, decisionAgentService, paymentGateway, notificationService, boundsConfig);

        when(attemptRepository.save(any(RecoveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void thirdFailure_getsFlaggedForSignoff() throws Exception {
        Transaction tx = buildTx(FailureReason.INSUFFICIENT_FUNDS, 2, new BigDecimal("1000"));
        tx.setStatus(TransactionStatus.IN_RECOVERY);

        when(transactionRepository.findByStatusIn(any())).thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionResult mockResult = new DecisionResult(
                EnforcedDecision.ok(new LlmDecision(RecoveryAction.RETRY_NOW, "Retry one more time", 0.6, null)),
                false,
                true,
                "3rd consecutive failure — requires human review before final disposition.");
        when(decisionAgentService.decideWithMeta(tx)).thenReturn(mockResult);
        when(paymentGateway.attemptCharge(tx)).thenReturn(false);

        List<RecoveryAttempt> results = orchestrator.runBatch();

        assertEquals(1, results.size());
        RecoveryAttempt attempt = results.get(0);
        assertTrue(attempt.isRequiresHumanSignoff(),
                "3rd consecutive failure (retryCount=2, maxRetries=3) must be flagged for signoff");
        assertTrue(attempt.getSignoffReason().contains("3rd consecutive failure"));
    }

    @Test
    void firstFailure_doesNotGetFlagged() throws Exception {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));
        tx.setStatus(TransactionStatus.AT_RISK);

        when(transactionRepository.findByStatusIn(any())).thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionResult mockResult = new DecisionResult(
                EnforcedDecision.ok(new LlmDecision(RecoveryAction.RETRY_NOW, "First failure, retry", 0.6, null)),
                false);
        when(decisionAgentService.decideWithMeta(tx)).thenReturn(mockResult);
        when(paymentGateway.attemptCharge(tx)).thenReturn(true);

        List<RecoveryAttempt> results = orchestrator.runBatch();

        assertEquals(1, results.size());
        assertFalse(results.get(0).isRequiresHumanSignoff(),
                "First failure should NOT require signoff");
    }

    @Test
    void secondFailure_doesNotGetFlagged() throws Exception {
        Transaction tx = buildTx(FailureReason.BANK_SERVER_DOWN, 1, new BigDecimal("1000"));
        tx.setStatus(TransactionStatus.IN_RECOVERY);

        when(transactionRepository.findByStatusIn(any())).thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionResult mockResult = new DecisionResult(
                EnforcedDecision.ok(new LlmDecision(RecoveryAction.RETRY_SCHEDULED, "Schedule retry", 0.55, null)),
                false);
        when(decisionAgentService.decideWithMeta(tx)).thenReturn(mockResult);
        when(paymentGateway.attemptCharge(tx)).thenReturn(false);

        List<RecoveryAttempt> results = orchestrator.runBatch();

        assertEquals(1, results.size());
        assertFalse(results.get(0).isRequiresHumanSignoff(),
                "Second failure (retryCount=1) should NOT require signoff");
    }

    @Test
    void discountCap_getsFlaggedForSignoff() throws Exception {
        Transaction tx = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("2499"));
        tx.setStatus(TransactionStatus.AT_RISK);

        when(transactionRepository.findByStatusIn(any())).thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        EnforcedDecision capped = new EnforcedDecision(
                new LlmDecision(RecoveryAction.OFFER_DISCOUNT, "Discount [capped by RulesEngine to policy max]", 0.7, 15),
                true,
                "LLM proposed 20% discount, capped to policy max 15%"
        );
        DecisionResult mockResult = new DecisionResult(capped, true);
        when(decisionAgentService.decideWithMeta(tx)).thenReturn(mockResult);
        when(notificationService.sendDiscountOffer(tx, 15)).thenReturn(true);
        when(notificationService.costOf(true)).thenReturn(new BigDecimal("0.35"));

        List<RecoveryAttempt> results = orchestrator.runBatch();

        assertEquals(1, results.size());
        assertTrue(results.get(0).isRequiresHumanSignoff(),
                "Discount cap should trigger signoff");
        assertTrue(results.get(0).getSignoffReason().contains("capped to policy max"));
    }

    @Test
    void batchId_isSetOnAllAttempts() throws Exception {
        Transaction tx1 = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));
        tx1.setStatus(TransactionStatus.AT_RISK);
        Transaction tx2 = buildTx(FailureReason.CARD_EXPIRED, 0, new BigDecimal("2000"));
        tx2.setStatus(TransactionStatus.AT_RISK);

        when(transactionRepository.findByStatusIn(any())).thenReturn(List.of(tx1, tx2));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionResult result1 = new DecisionResult(
                EnforcedDecision.ok(new LlmDecision(RecoveryAction.RETRY_NOW, "Retry", 0.6, null)), false);
        DecisionResult result2 = new DecisionResult(
                EnforcedDecision.ok(new LlmDecision(RecoveryAction.SEND_PAYMENT_LINK, "Send link", 0.5, null)), false);

        when(decisionAgentService.decideWithMeta(tx1)).thenReturn(result1);
        when(decisionAgentService.decideWithMeta(tx2)).thenReturn(result2);
        when(paymentGateway.attemptCharge(tx1)).thenReturn(true);
        when(notificationService.sendPaymentLink(tx2)).thenReturn(true);
        when(notificationService.costOf(true)).thenReturn(new BigDecimal("0.05"));

        List<RecoveryAttempt> results = orchestrator.runBatch();

        assertEquals(2, results.size());
        assertNotNull(results.get(0).getBatchId());
        assertNotNull(results.get(1).getBatchId());
        assertEquals(results.get(0).getBatchId(), results.get(1).getBatchId(),
                "Both attempts in the same batch should share the same batchId");
    }

    @Test
    void cooldownSkip_persistsAndHasBatchId() throws Exception {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 1, new BigDecimal("1000"));
        tx.setStatus(TransactionStatus.IN_RECOVERY);
        tx.setLastAttemptAt(LocalDateTime.now().minusMinutes(10));

        when(transactionRepository.findByStatusIn(any())).thenReturn(List.of(tx));

        List<RecoveryAttempt> results = orchestrator.runBatch();

        assertEquals(1, results.size());
        assertEquals(RecoveryAttempt.AttemptOutcome.PENDING, results.get(0).getOutcome());
        assertNotNull(results.get(0).getBatchId(),
                "Skipped attempts should also have a batchId");
        verify(attemptRepository, atLeastOnce()).save(any(RecoveryAttempt.class));
    }

    @Test
    void llmDriven_flag_isPropagatedToAttempt() throws Exception {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));
        tx.setStatus(TransactionStatus.AT_RISK);

        when(transactionRepository.findByStatusIn(any())).thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionResult llmResult = new DecisionResult(
                EnforcedDecision.ok(new LlmDecision(RecoveryAction.RETRY_NOW, "LLM decision", 0.85, null)),
                true);
        when(decisionAgentService.decideWithMeta(tx)).thenReturn(llmResult);
        when(paymentGateway.attemptCharge(tx)).thenReturn(true);

        List<RecoveryAttempt> results = orchestrator.runBatch();

        assertEquals(1, results.size());
        assertTrue(results.get(0).isLlmDriven(),
                "llmDriven=true from DecisionResult must be propagated to the attempt");
    }

    @Test
    void heuristicDriven_flag_isPropagatedToAttempt() throws Exception {
        Transaction tx = buildTx(FailureReason.NETWORK_ERROR, 0, new BigDecimal("1000"));
        tx.setStatus(TransactionStatus.AT_RISK);

        when(transactionRepository.findByStatusIn(any())).thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionResult heuristicResult = new DecisionResult(
                EnforcedDecision.ok(new LlmDecision(RecoveryAction.RETRY_NOW, "Rules-only mode: first failure", 0.6, null)),
                false);
        when(decisionAgentService.decideWithMeta(tx)).thenReturn(heuristicResult);
        when(paymentGateway.attemptCharge(tx)).thenReturn(true);

        List<RecoveryAttempt> results = orchestrator.runBatch();

        assertEquals(1, results.size());
        assertFalse(results.get(0).isLlmDriven(),
                "llmDriven=false from DecisionResult must be propagated to the attempt");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Transaction buildTx(FailureReason reason, int retryCount, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setFailureReason(reason);
        tx.setRetryCount(retryCount);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.AT_RISK);
        return tx;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

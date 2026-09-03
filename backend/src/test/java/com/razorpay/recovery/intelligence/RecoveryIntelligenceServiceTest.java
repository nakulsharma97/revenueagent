package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.audit.AuditEvent;
import com.razorpay.recovery.audit.AuditService;
import com.razorpay.recovery.checkout.CheckoutSessionRepository;
import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.receivable.ReceivableRepository;
import com.razorpay.recovery.recovery.RecoveryAttempt;
import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import com.razorpay.recovery.recovery.RecoveryAttemptRepository;
import com.razorpay.recovery.recovery.RulesEngine;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import com.razorpay.recovery.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the transactional intelligence coordinator: per-attempt counterfactual
 * persistence, state/fatigue enrichment, anomaly & review-case routing, and human
 * resolution of review cases.
 */
@ExtendWith(MockitoExtension.class)
class RecoveryIntelligenceServiceTest {

    @Mock private CounterfactualDecisionRepository counterfactualRepository;
    @Mock private RecoveryOutcomeRepository outcomeRepository;
    @Mock private HumanReviewCaseRepository reviewRepository;
    @Mock private RecoveryAnomalyRepository anomalyRepository;
    @Mock private AuditService auditService;
    @Mock private RecoveryAttemptRepository attemptRepository;
    @Mock private RecoveryExperimentRepository experimentRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CheckoutSessionRepository checkoutSessionRepository;
    @Mock private ReceivableRepository receivableRepository;

    private RecoveryIntelligenceService service;

    @BeforeEach
    void setUp() {
        BoundsConfig bounds = new BoundsConfig();
        bounds.setMaxRetries(3);
        bounds.setMaxDiscountPercent(15);
        bounds.setMinAmountForDiscount(new BigDecimal("500"));
        bounds.setRetryCooldownMinutes(60);
        bounds.setHvMaxRetries(5);
        bounds.setHvMaxDiscountPercent(25);
        bounds.setHvMinAmountForDiscount(new BigDecimal("500"));
        service = new RecoveryIntelligenceService(counterfactualRepository, outcomeRepository,
                reviewRepository, anomalyRepository, auditService, bounds, new RulesEngine(bounds),
                attemptRepository, experimentRepository, transactionRepository,
                checkoutSessionRepository, receivableRepository);
    }

    private RecoveryAttempt paymentAttempt(int retries, boolean signoff) {
        Transaction tx = new Transaction();
        tx.setId(42L);
        tx.setAmount(new BigDecimal("2499"));
        tx.setRetryCount(retries);
        tx.setFailureReason(FailureReason.INSUFFICIENT_FUNDS);
        tx.setStatus(TransactionStatus.IN_RECOVERY);
        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setSourceType(RecoveryAttempt.SourceType.PAYMENT);
        attempt.setTransaction(tx);
        attempt.setActionTaken(RecoveryAction.SEND_PAYMENT_LINK);
        attempt.setOutcome(RecoveryAttempt.AttemptOutcome.FAILED);
        attempt.setRequiresHumanSignoff(signoff);
        if (signoff) attempt.setSignoffReason("3rd consecutive failure — requires human review.");
        return attempt;
    }

    @Test
    void recordDecision_enrichesAttemptAndPersistsCounterfactuals() {
        RecoveryAttempt attempt = paymentAttempt(1, false);

        service.recordDecision(attempt, "batch-x");

        assertTrue(attempt.getFatigueScore() >= 0, "Fatigue score must be enriched on the attempt");
        assertNotNull(attempt.getRecoveryState(), "Recovery state must be detected on the attempt");
        verify(counterfactualRepository, atLeastOnce()).save(any(CounterfactualDecision.class));
        // No sign-off → no review case.
        verify(reviewRepository, never()).save(any(HumanReviewCase.class));
    }

    @Test
    void recordDecision_signoffCase_routesIntoReviewQueue() {
        RecoveryAttempt attempt = paymentAttempt(2, true);

        service.recordDecision(attempt, "batch-x");

        verify(reviewRepository, atLeastOnce()).save(argThat(c ->
                c.getStatus() == HumanReviewCase.Status.PENDING
                        && c.getReason().contains("sign-off")));
        verify(auditService, atLeastOnce()).recordForBatch(eq("batch-x"), any(), eq(AuditEvent.EventType.REVIEW_CASE_CREATED),
                any(), any(), any());
    }

    @Test
    void recordDecision_repeatedFailures_detectAndPersistAnomaly() {
        RecoveryAttempt attempt = paymentAttempt(2, false);

        service.recordDecision(attempt, "batch-x");

        verify(anomalyRepository, atLeastOnce()).save(argThat(a ->
                "REPEATED_FAILURES".equals(a.getType())
                        && a.getSeverity() == RecoveryAnomaly.Severity.MEDIUM));
    }

    @Test
    void recordOutcome_skipsControlAndSkippedAttempts() {
        RecoveryAttempt skip = new RecoveryAttempt();
        skip.setActionTaken(RecoveryAction.NO_ACTION);
        skip.setOutcome(RecoveryAttempt.AttemptOutcome.SKIPPED);

        service.recordOutcome(skip);

        verifyNoInteractions(outcomeRepository);
    }

    @Test
    void recordOutcome_persistsTrainingRow() {
        RecoveryAttempt attempt = paymentAttempt(1, false);
        attempt.setOutcome(RecoveryAttempt.AttemptOutcome.SUCCESS);
        attempt.setAmountRecovered(new BigDecimal("2499"));
        attempt.setInterventionCost(new BigDecimal("0.35"));

        service.recordOutcome(attempt);

        verify(outcomeRepository, atLeastOnce()).save(argThat(o ->
                o.isSuccess() && o.getAction() == RecoveryAction.SEND_PAYMENT_LINK
                        && o.getAmountRecovered().compareTo(new BigDecimal("2499")) == 0));
    }

    @Test
    void resolveReview_approvalMarksLinkedAttemptApproved() {
        HumanReviewCase reviewCase = HumanReviewCase.pending("PAYMENT", 42L, 7L, new BigDecimal("2499"),
                RecoveryAction.OFFER_DISCOUNT, 10, 0.55, "Low confidence", HumanReviewCase.Priority.NORMAL);
        when(reviewRepository.findById(1L)).thenReturn(java.util.Optional.of(reviewCase));

        RecoveryAttempt linked = paymentAttempt(2, true);
        linked.setId(7L);
        when(attemptRepository.findById(7L)).thenReturn(java.util.Optional.of(linked));

        HumanReviewCase resolved = service.resolveReview(1L, HumanReviewCase.Status.APPROVED, null, "Agreed");

        assertEquals(HumanReviewCase.Status.APPROVED, resolved.getStatus());
        assertEquals(RecoveryAction.OFFER_DISCOUNT, resolved.getHumanDecision(),
                "Approval keeps the AI recommendation as the human decision");
        assertNotNull(resolved.getResolvedAt());
        verify(attemptRepository).save(argThat(a -> a.getSignoffStatus() == RecoveryAttempt.SignoffStatus.APPROVED));
    }

    @Test
    void resolveReview_overrideRecordsHumanAction() {
        HumanReviewCase reviewCase = HumanReviewCase.pending("CHECKOUT", 9L, null, new BigDecimal("4999"),
                RecoveryAction.OFFER_DISCOUNT, 10, 0.62, "Sign-off threshold", HumanReviewCase.Priority.NORMAL);
        when(reviewRepository.findById(2L)).thenReturn(java.util.Optional.of(reviewCase));

        HumanReviewCase resolved = service.resolveReview(2L, HumanReviewCase.Status.OVERRIDDEN,
                RecoveryAction.SEND_PAYMENT_LINK, "Customer responds to links, not discounts");

        assertEquals(RecoveryAction.SEND_PAYMENT_LINK, resolved.getHumanDecision());
        assertEquals("Customer responds to links, not discounts", resolved.getOverrideReason());
    }
}

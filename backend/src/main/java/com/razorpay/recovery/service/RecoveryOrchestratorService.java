package com.razorpay.recovery.service;

import com.razorpay.recovery.config.BoundsConfig;
import com.razorpay.recovery.dto.DecisionResult;
import com.razorpay.recovery.dto.LlmDecision;
import com.razorpay.recovery.model.RecoveryAttempt;
import com.razorpay.recovery.model.RecoveryAttempt.AttemptOutcome;
import com.razorpay.recovery.model.Transaction;
import com.razorpay.recovery.model.Transaction.TransactionStatus;
import com.razorpay.recovery.repository.RecoveryAttemptRepository;
import com.razorpay.recovery.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The end-to-end loop: Detection -> Diagnosis -> Decision -> Execution -> Ledger.
 * This is the class that answers "detects revenue at risk, determines the right
 * intervention, and executes a bounded recovery workflow" from the brief, one
 * transaction at a time.
 */
@Service
public class RecoveryOrchestratorService {

    private final TransactionRepository transactionRepository;
    private final RecoveryAttemptRepository attemptRepository;
    private final DecisionAgentService decisionAgentService;
    private final MockPaymentGatewayService paymentGateway;
    private final MockNotificationService notificationService;

    private final BoundsConfig boundsConfig;

    private int getCooldownMinutes() { return boundsConfig.getRetryCooldownMinutes(); }
    private int getMaxRetries() { return boundsConfig.getMaxRetries(); }

    /** Prevents two overlapping runBatch() calls from racing on the same transactions. */
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    public RecoveryOrchestratorService(TransactionRepository transactionRepository,
                                        RecoveryAttemptRepository attemptRepository,
                                        DecisionAgentService decisionAgentService,
                                        MockPaymentGatewayService paymentGateway,
                                        MockNotificationService notificationService,
                                        BoundsConfig boundsConfig) {
        this.transactionRepository = transactionRepository;
        this.attemptRepository = attemptRepository;
        this.decisionAgentService = decisionAgentService;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.boundsConfig = boundsConfig;
    }

    /**
     * Runs the full loop across every AT_RISK and IN_RECOVERY transaction.
     * Returns 409 if a batch is already in flight.
     */
    @Transactional
    public List<RecoveryAttempt> runBatch() {
        if (!batchRunning.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Batch already running");
        }
        try {
            String batchId = UUID.randomUUID().toString();
            List<Transaction> eligible = transactionRepository.findByStatusIn(
                    List.of(TransactionStatus.AT_RISK, TransactionStatus.IN_RECOVERY));
            return eligible.stream().map(tx -> processOne(tx, batchId)).toList();
        } finally {
            batchRunning.set(false);
        }
    }

    private RecoveryAttempt processOne(Transaction tx, String batchId) {
        // Cooldown enforcement: skip if last attempt was less than cooldownMinutes ago.
        if (tx.getLastAttemptAt() != null) {
            long minutesSinceLastAttempt = java.time.Duration.between(tx.getLastAttemptAt(), LocalDateTime.now()).toMinutes();
            if (minutesSinceLastAttempt < getCooldownMinutes()) {
                // Persist a no-op attempt so the ledger shows why this tx was skipped.
                RecoveryAttempt skip = new RecoveryAttempt();
                skip.setTransaction(tx);
                skip.setActionTaken(RecoveryAttempt.RecoveryAction.RETRY_SCHEDULED);
                skip.setReasoning("Skipped: cooldown period not elapsed (" + minutesSinceLastAttempt + "min / " + getCooldownMinutes() + "min).");
                skip.setConfidence(0.0);
                skip.setOutcome(RecoveryAttempt.AttemptOutcome.PENDING);
                skip.setExecutedAt(LocalDateTime.now());
                skip.setBatchId(batchId);
                return attemptRepository.save(skip);
            }
        }

        // [1] Detection already happened at ingestion (status == AT_RISK).
        // [2] Diagnosis is implicit in tx.failureReason (seeded/ingested with the transaction).
        // [3] Decision — bounded by RulesEngine inside DecisionAgentService.
        // Uses decideWithMeta() to get llmDriven flag and signoff metadata.
        DecisionResult result = decisionAgentService.decideWithMeta(tx);
        LlmDecision decision = result.decision();

        // [4] Execution
        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setTransaction(tx);
        attempt.setActionTaken(decision.action());
        attempt.setReasoning(decision.reasoning());
        attempt.setConfidence(decision.confidence());
        attempt.setLlmDriven(result.llmDriven());
        attempt.setBatchId(batchId);
        attempt.setExecutedAt(LocalDateTime.now());

        // Signoff: decideWithMeta() computes the full signal (discount cap + 3rd failure).
        attempt.setRequiresHumanSignoff(result.requiresHumanSignoff());
        attempt.setSignoffReason(result.signoffReason());

        boolean success = execute(tx, decision, attempt);

        attempt.setOutcome(success ? AttemptOutcome.SUCCESS : AttemptOutcome.FAILED);
        if (success) {
            attempt.setAmountRecovered(tx.getAmount());
            tx.setStatus(TransactionStatus.RECOVERED);
        } else {
            tx.setRetryCount(tx.getRetryCount() + 1);
            tx.setStatus(tx.getRetryCount() >= getMaxRetries() ? TransactionStatus.LOST : TransactionStatus.IN_RECOVERY);
        }
        tx.setLastAttemptAt(LocalDateTime.now());

        transactionRepository.save(tx);
        return attemptRepository.save(attempt);
    }

    private boolean execute(Transaction tx, LlmDecision decision, RecoveryAttempt attempt) {
        return switch (decision.action()) {
            case RETRY_NOW, RETRY_SCHEDULED -> paymentGateway.attemptCharge(tx);
            case SEND_PAYMENT_LINK -> {
                boolean paid = notificationService.sendPaymentLink(tx);
                attempt.setInterventionCost(notificationService.costOf(true));
                yield paid;
            }
            case OFFER_DISCOUNT -> {
                int pct = decision.discountPercent() == null ? 10 : decision.discountPercent();
                boolean paid = notificationService.sendDiscountOffer(tx, pct);
                BigDecimal discountValue = tx.getAmount()
                        .multiply(BigDecimal.valueOf(pct))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                attempt.setInterventionCost(notificationService.costOf(true).add(paid ? discountValue : BigDecimal.ZERO));
                yield paid;
            }
            case ESCALATE_TO_HUMAN, ABANDON -> false;
        };
    }
}

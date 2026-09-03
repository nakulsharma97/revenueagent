package com.razorpay.recovery.api;

import com.razorpay.recovery.checkout.CheckoutSessionRepository;
import com.razorpay.recovery.receivable.ReceivableRepository;
import com.razorpay.recovery.recovery.RecoveryAttempt;
import com.razorpay.recovery.recovery.RecoveryAttempt.AttemptOutcome;
import com.razorpay.recovery.recovery.RecoveryAttemptRepository;
import com.razorpay.recovery.recovery.RecoveryOrchestratorService;
import com.razorpay.recovery.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller-facing layer that keeps JPA entities out of API responses.
 *
 * <p>Every method maps entities to DTOs <b>inside</b> a service-layer transaction, so the
 * lazy associations (transaction → subscription → customer) are resolved before the session
 * closes. This is why `spring.jpa.open-in-view=false` is safe: nothing lazy is serialized.
 */
@Service
public class RecoveryApiService {

    private final RecoveryOrchestratorService orchestrator;
    private final TransactionRepository transactionRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ReceivableRepository receivableRepository;
    private final RecoveryAttemptRepository attemptRepository;

    public RecoveryApiService(RecoveryOrchestratorService orchestrator,
                              TransactionRepository transactionRepository,
                              CheckoutSessionRepository checkoutSessionRepository,
                              ReceivableRepository receivableRepository,
                              RecoveryAttemptRepository attemptRepository) {
        this.orchestrator = orchestrator;
        this.transactionRepository = transactionRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.receivableRepository = receivableRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> allTransactions() {
        return transactionRepository.findAll().stream().map(TransactionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReceivableDto> allReceivables() {
        return receivableRepository.findAll().stream().map(ReceivableDto::from).toList();
    }

    /** Persisted recovery attempts, newest first. Optional cap via {@code limit}. */
    @Transactional(readOnly = true)
    public List<AttemptDto> allAttempts(Integer limit) {
        List<RecoveryAttempt> all = new ArrayList<>(attemptRepository.findAll());
        all.sort(Comparator.comparing(RecoveryAttempt::getId).reversed());
        if (limit != null && limit > 0 && all.size() > limit) {
            all = new ArrayList<>(all.subList(0, limit));
        }
        return all.stream().map(AttemptDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttemptDto> pendingReview() {
        return attemptRepository.findByRequiresHumanSignoffTrueAndSignoffStatus(RecoveryAttempt.SignoffStatus.PENDING)
                .stream().map(AttemptDto::from).toList();
    }

    /** Blocking batch run — maps results to DTOs while the transaction is still open. */
    @Transactional
    public List<AttemptDto> runBatch() {
        return orchestrator.runBatch().stream().map(AttemptDto::from).toList();
    }

    /** Number of items the next batch will process (single source of truth for the SSE total). */
    public int countEligible() {
        return orchestrator.countEligible();
    }

    /**
     * Streaming batch run. Calls {@code onAttempt} with a DTO for each item as it completes,
     * then returns accurate processed/skipped/failed counts for the completion event.
     */
    @Transactional
    public BatchRunOutcome runBatchStreaming(Consumer<AttemptDto> onAttempt) {
        List<RecoveryAttempt> all = orchestrator.runBatchWithCallback(a -> onAttempt.accept(AttemptDto.from(a)));
        long skipped = all.stream().filter(a -> a.getOutcome() == AttemptOutcome.SKIPPED).count();
        long failed = all.stream().filter(a -> a.getOutcome() == AttemptOutcome.FAILED).count();
        return new BatchRunOutcome(all.size(), skipped, failed);
    }

    @Transactional
    public AttemptDto resolveSignoff(Long id, RecoveryAttempt.SignoffStatus status) {
        RecoveryAttempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));
        if (!attempt.isRequiresHumanSignoff()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This attempt does not require signoff");
        }
        attempt.setSignoffStatus(status);
        attempt.setSignoffResolvedAt(java.time.LocalDateTime.now());
        return AttemptDto.from(attemptRepository.save(attempt));
    }

    /** Accurate completion counts for the SSE 'done' event. */
    public record BatchRunOutcome(long processed, long skipped, long failed) {
        public long succeeded() {
            return processed - skipped - failed;
        }
    }
}
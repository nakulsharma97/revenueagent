package com.razorpay.recovery.recovery;

import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.TransactionRepository;
import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.receivable.ReceivableRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/recovery")
public class RecoveryController {

    private final RecoveryOrchestratorService orchestrator;
    private final TransactionRepository transactionRepository;
    private final ReceivableRepository receivableRepository;
    private final RecoveryAttemptRepository attemptRepository;

    public RecoveryController(RecoveryOrchestratorService orchestrator,
                              TransactionRepository transactionRepository,
                              ReceivableRepository receivableRepository,
                              RecoveryAttemptRepository attemptRepository) {
        this.orchestrator = orchestrator;
        this.transactionRepository = transactionRepository;
        this.receivableRepository = receivableRepository;
        this.attemptRepository = attemptRepository;
    }

    /** Kicks off the full detect -> diagnose -> decide -> execute loop across all at-risk transactions. */
    @PostMapping("/run-batch")
    public List<RecoveryAttempt> runBatch() {
        return orchestrator.runBatch();
    }

    /** Streaming batch: emits 'total' first, then attempts per-source for incremental progress. */
    @GetMapping(value = "/run-batch/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runBatchStream() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        new Thread(() -> {
            try {
                // 1. Count eligible items and send 'total' so the frontend progress bar is accurate
                int total = orchestrator.countEligible();
                emitter.send(SseEmitter.event().name("total").data(total));

                // 2. Run batch with per-item callback — events emitted after EACH item
                List<RecoveryAttempt> allResults = orchestrator.runBatchWithCallback(attempt -> {
                    try {
                        emitter.send(SseEmitter.event().name("attempt").data(attempt));
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(RecoveryController.class)
                                .error("SSE send failed", e);
                    }
                });

                // 3. Signal completion
                emitter.send(SseEmitter.event().name("done").data(allResults.size()));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    @GetMapping("/transactions")
    public List<Transaction> transactions() {
        return transactionRepository.findAll();
    }

    /** All receivables — exposes promise-to-pay status for the UI. */
    @GetMapping("/receivables")
    public List<Receivable> receivables() {
        return receivableRepository.findAll();
    }

    /** Returns the structured decision trace for a specific attempt. */
    @GetMapping("/attempts/{id}/trace")
    public DecisionTrace attemptTrace(@PathVariable Long id) {
        RecoveryAttempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Attempt not found"));
        return attempt.getDecisionTrace() != null ? attempt.getDecisionTrace() : new DecisionTrace();
    }

    /**
     * Attempts that require human review before execution — per the brief's bounded-workflow rule:
     * "anything above the discount ceiling, or a 3rd consecutive failure."
     */
    @GetMapping("/pending-review")
    public List<RecoveryAttempt> pendingReview() {
        return attemptRepository.findByRequiresHumanSignoffTrueAndSignoffStatus(
                RecoveryAttempt.SignoffStatus.PENDING);
    }

    /**
     * Resolve a signoff request — approve or reject.
     * Sets signoffStatus and signoffResolvedAt on the attempt.
     */
    @PutMapping("/attempts/{id}/signoff")
    public RecoveryAttempt resolveSignoff(@PathVariable Long id, @RequestBody SignoffRequest request) {
        RecoveryAttempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Attempt not found"));
        if (!attempt.isRequiresHumanSignoff()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "This attempt does not require signoff");
        }
        attempt.setSignoffStatus(request.status());
        attempt.setSignoffResolvedAt(java.time.LocalDateTime.now());
        return attemptRepository.save(attempt);
    }

    public record SignoffRequest(RecoveryAttempt.SignoffStatus status) {}

    /** Export all recovery attempts as CSV. */
    @GetMapping(value = "/export", produces = "text/csv")
    public String exportCsv() {
        List<RecoveryAttempt> attempts = attemptRepository.findAll();
        StringBuilder sw = new StringBuilder();
        sw.append("Source Type,Source ID,Batch ID,Action,Reasoning,Confidence,Outcome,Amount Recovered,Intervention Cost,LLM Driven,Requires Signoff,Signoff Reason\n");
        for (RecoveryAttempt a : attempts) {
            String sourceId = "";
            if (a.getTransaction() != null) sourceId = String.valueOf(a.getTransaction().getId());
            else if (a.getCheckoutSession() != null) sourceId = String.valueOf(a.getCheckoutSession().getId());
            else if (a.getReceivable() != null) sourceId = String.valueOf(a.getReceivable().getId());
            sw.append(String.format("%s,%s,%s,%s,\"%s\",%.2f,%s,%.2f,%.2f,%s,%s,\"%s\"\n",
                    a.getSourceType(),
                    sourceId,
                    a.getBatchId() != null ? a.getBatchId() : "",
                    a.getActionTaken(),
                    a.getReasoning() != null ? a.getReasoning().replace("\"", "\"\"") : "",
                    a.getConfidence(),
                    a.getOutcome(),
                    a.getAmountRecovered(),
                    a.getInterventionCost(),
                    a.isLlmDriven(),
                    a.isRequiresHumanSignoff(),
                    a.getSignoffReason() != null ? a.getSignoffReason().replace("\"", "\"\"") : ""
            ));
        }
        return sw.toString();
    }
}

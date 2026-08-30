package com.razorpay.recovery.recovery;

import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.TransactionRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/recovery")
@CrossOrigin(origins = "*")
public class RecoveryController {

    private final RecoveryOrchestratorService orchestrator;
    private final TransactionRepository transactionRepository;
    private final RecoveryAttemptRepository attemptRepository;

    public RecoveryController(RecoveryOrchestratorService orchestrator,
                              TransactionRepository transactionRepository,
                              RecoveryAttemptRepository attemptRepository) {
        this.orchestrator = orchestrator;
        this.transactionRepository = transactionRepository;
        this.attemptRepository = attemptRepository;
    }

    /** Kicks off the full detect -> diagnose -> decide -> execute loop across all at-risk transactions. */
    @PostMapping("/run-batch")
    public List<RecoveryAttempt> runBatch() {
        return orchestrator.runBatch();
    }

    /** Streaming batch: emits each recovery attempt as an SSE event as it completes. */
    @GetMapping(value = "/run-batch/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runBatchStream() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        new Thread(() -> {
            try {
                List<RecoveryAttempt> results = orchestrator.runBatch();
                for (RecoveryAttempt attempt : results) {
                    emitter.send(SseEmitter.event()
                            .name("attempt")
                            .data(attempt));
                }
                emitter.send(SseEmitter.event().name("done").data(results.size()));
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

    /**
     * Attempts that require human review before execution — per the brief's bounded-workflow rule:
     * "anything above the discount ceiling, or a 3rd consecutive failure."
     */
    @GetMapping("/pending-review")
    public List<RecoveryAttempt> pendingReview() {
        return attemptRepository.findByRequiresHumanSignoffTrue();
    }

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

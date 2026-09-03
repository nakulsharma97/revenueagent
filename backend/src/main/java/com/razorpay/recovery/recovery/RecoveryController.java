package com.razorpay.recovery.recovery;

import com.razorpay.recovery.api.AttemptDto;
import com.razorpay.recovery.api.RecoveryApiService;
import com.razorpay.recovery.api.ReceivableDto;
import com.razorpay.recovery.api.TransactionDto;
import com.razorpay.recovery.recovery.RecoveryAttempt.SignoffStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/recovery")
public class RecoveryController {

    private final RecoveryApiService apiService;
    private final RecoveryAttemptRepository attemptRepository;

    public RecoveryController(RecoveryApiService apiService,
                              RecoveryAttemptRepository attemptRepository) {
        this.apiService = apiService;
        this.attemptRepository = attemptRepository;
    }

    /** Blocking batch: processes all items and returns the full result list. Used by tests and fallback. */
    @PostMapping("/run-batch")
    public List<AttemptDto> runBatch() {
        return apiService.runBatch();
    }

    /** Streaming batch: emits 'total' first, then attempts for incremental progress, then accurate counts. */
    @GetMapping(value = "/run-batch/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runBatchStream() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        new Thread(() -> {
            try {
                // 1. Count eligible items and send 'total' so the frontend progress bar is accurate.
                //    countEligible uses the exact same worklist predicates as the batch itself,
                //    so total always equals the number of attempt events emitted.
                int total = apiService.countEligible();
                emitter.send(SseEmitter.event().name("total").data(total));

                // 2. Run batch with per-item callback — DTOs emitted after EACH item completes.
                RecoveryApiService.BatchRunOutcome outcome = apiService.runBatchStreaming(attempt -> {
                    try {
                        emitter.send(SseEmitter.event().name("attempt").data(attempt));
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(RecoveryController.class)
                                .error("SSE send failed", e);
                    }
                });

                // 3. Signal completion with accurate processed/skipped/failed counts.
                emitter.send(SseEmitter.event().name("done").data(outcome));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    /** Persisted transactions (DTOs — safe to serialize with OSIV disabled). */
    @GetMapping("/transactions")
    public List<TransactionDto> transactions() {
        return apiService.allTransactions();
    }

    /** All receivables — exposes promise-to-pay status for the UI. */
    @GetMapping("/receivables")
    public List<ReceivableDto> receivables() {
        return apiService.allReceivables();
    }

    /** Persisted recovery attempts, newest first — the Decision Ledger/Actions pages load these after refresh. */
    @GetMapping("/attempts")
    public List<AttemptDto> attempts(@RequestParam(required = false) Integer limit) {
        return apiService.allAttempts(limit);
    }

    /** The full DETECTION→ELIGIBILITY→PROPOSAL→BOUNDS_CHECK→EXECUTION trace for one attempt. */
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
    public List<AttemptDto> pendingReview() {
        return apiService.pendingReview();
    }

    /**
     * Resolve a signoff request — approve or reject.
     * Sets signoffStatus and signoffResolvedAt on the attempt.
     */
    @PutMapping("/attempts/{id}/signoff")
    public AttemptDto resolveSignoff(@PathVariable Long id, @RequestBody SignoffRequest request) {
        return apiService.resolveSignoff(id, request.status());
    }

    public record SignoffRequest(SignoffStatus status) {}

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
            sw.append(String.format("%s,%s,%s,%s,\"%s\",%.2f,%s,%.2f,%.2f,%s,%s,\"%s\"%n",
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
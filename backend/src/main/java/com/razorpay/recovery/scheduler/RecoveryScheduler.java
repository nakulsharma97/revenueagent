package com.razorpay.recovery.scheduler;

import com.razorpay.recovery.recovery.RecoveryOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs the recovery loop automatically on an interval, in addition to the
 * on-demand "Run batch" button exposed via the API — mirrors how a real
 * dunning job would run in production (e.g. hourly).
 *
 * <p>A scheduled run that collides with a manual/streaming batch is an expected
 * occurrence, not an error: the guard is checked before starting, and a racing
 * 409 from the orchestrator is treated as a skip with an informative log line.
 */
@Component
public class RecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecoveryScheduler.class);

    private final RecoveryOrchestratorService orchestrator;

    public RecoveryScheduler(RecoveryOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(initialDelayString = "${recovery.retry-cooldown-minutes}", fixedDelayString = "${recovery.retry-cooldown-minutes}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void scheduledRun() {
        if (orchestrator.isBatchRunning()) {
            log.info("Scheduled recovery run skipped — a batch is already in progress.");
            return;
        }
        try {
            orchestrator.runBatch();
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.info("Scheduled recovery run skipped — a batch started concurrently (409 conflict).");
            } else {
                log.warn("Scheduled recovery run failed: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Scheduled recovery run failed: {}", e.getMessage());
        }
    }
}
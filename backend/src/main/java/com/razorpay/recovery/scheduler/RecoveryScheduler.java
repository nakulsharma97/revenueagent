package com.razorpay.recovery.scheduler;

import com.razorpay.recovery.service.RecoveryOrchestratorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the recovery loop automatically on an interval, in addition to the
 * on-demand "Run batch" button exposed via the API — mirrors how a real
 * dunning job would run in production (e.g. hourly).
 */
@Component
public class RecoveryScheduler {

    private final RecoveryOrchestratorService orchestrator;

    public RecoveryScheduler(RecoveryOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(initialDelayString = "${recovery.retry-cooldown-minutes}", fixedDelayString = "${recovery.retry-cooldown-minutes}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void scheduledRun() {
        orchestrator.runBatch();
    }
}

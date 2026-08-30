package com.razorpay.recovery.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Logs startup completion. The auto-batch is run inside DataSeeder itself
 * (same transaction) to ensure seeded data is visible to the orchestrator.
 */
@Component
public class AutoBatchRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AutoBatchRunner.class);

    @Override
    public void run(String... args) {
        log.info("AutoBatchRunner: Startup complete — dashboard is ready.");
    }
}

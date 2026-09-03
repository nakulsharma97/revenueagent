package com.razorpay.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.recovery.audit.AuditEvent;
import com.razorpay.recovery.audit.AuditEventRepository;
import com.razorpay.recovery.api.RecoveryApiService;
import com.razorpay.recovery.checkout.CheckoutSessionRepository;
import com.razorpay.recovery.customer.Customer;
import com.razorpay.recovery.customer.CustomerRepository;
import com.razorpay.recovery.receivable.ReceivableRepository;
import com.razorpay.recovery.recovery.RecoveryAttempt;
import com.razorpay.recovery.recovery.RecoveryAttemptRepository;
import com.razorpay.recovery.recovery.RecoveryOrchestratorService;
import com.razorpay.recovery.scheduler.RecoveryScheduler;
import com.razorpay.recovery.subscription.Subscription;
import com.razorpay.recovery.subscription.SubscriptionRepository;
import com.razorpay.recovery.transaction.Transaction;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import com.razorpay.recovery.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context (@SpringBootTest) integration tests for the parts of the system that
 * only misbehave at runtime: OSIV-disabled lazy serialization, the segment-aware
 * retry limits, webhook idempotency at the database level, the audit pipeline,
 * scheduler collision handling, and SSE/blocking batch consistency.
 *
 * <p>Demo seeding is disabled via {@code recovery.seed-data=false} (see
 * src/test/resources/application.properties); each test seeds only its own rows and
 * asserts on rows it created, so tests are order-independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecoveryPipelineIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Autowired private CustomerRepository customerRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CheckoutSessionRepository checkoutSessionRepository;
    @Autowired private ReceivableRepository receivableRepository;
    @Autowired private RecoveryAttemptRepository attemptRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private RecoveryOrchestratorService orchestrator;
    @Autowired private RecoveryApiService apiService;
    @Autowired private RecoveryScheduler scheduler;

    // ── 1. Transactions endpoint: OSIV disabled, lazy chain serialized via DTOs ──

    @Test
    void transactionsEndpoint_worksWithOsivDisabled_andResolvesLazyChain() throws Exception {
        Transaction tx = seedPayment(
                "IT_TX_1", Customer.CustomerSegment.HIGH_VALUE, 0.9,
                new BigDecimal("2499"), FailureReason.NETWORK_ERROR, 0, TransactionStatus.AT_RISK);

        String body = mvc.perform(get("/api/recovery/transactions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode rows = mapper.readTree(body);
        JsonNode match = findTransaction(rows, "IT_TX_1");
        assertNotNull(match, "Seeded transaction must appear in /api/recovery/transactions");
        // Lazy subscription → customer resolved inside the DTO mapping transaction:
        assertEquals("HIGH_VALUE", match.get("customerSegment").asText());
        assertEquals("Pro", match.get("planName").asText()); // ₹2499 ⇒ Pro tier
        assertEquals(0.9, match.get("paymentReliabilityScore").asDouble(), 0.0001);
        assertEquals("AT_RISK", match.get("status").asText());
        assertNotNull(tx.getId());
    }

    // ── 2. HIGH_VALUE vs STANDARD retry limits are enforced by the batch ──

    @Test
    void highValueCustomers_areNotWrittenOffAtTheStandardRetryLimit() throws Exception {
        // Deterministic setup: CARD_STOLEN_FLAG (non-retryable) + sub-₹500 amount +
        // DO_NOT_DISTURB uplift segment ⇒ the only eligible action is ESCALATE_TO_HUMAN,
        // which always fails (no random gateway draw), so retryCount advances by exactly 1.
        seedPayment("IT_HV_SEG", Customer.CustomerSegment.HIGH_VALUE, 0.5,
                new BigDecimal("299"), FailureReason.CARD_STOLEN_FLAG, 3, TransactionStatus.IN_RECOVERY);
        seedPayment("IT_STD_SEG", Customer.CustomerSegment.STANDARD, 0.5,
                new BigDecimal("299"), FailureReason.CARD_STOLEN_FLAG, 2, TransactionStatus.IN_RECOVERY);

        runBatchViaApi();

        Transaction hv = transactionRepository.findByRazorpayPaymentId("IT_HV_SEG")
                .orElseGet(() -> transactionRepository.findAll().stream()
                        .filter(t -> "IT_HV_SEG".equals(t.getEventId())).findFirst().orElseThrow());
        Transaction std = transactionRepository.findAll().stream()
                .filter(t -> "IT_STD_SEG".equals(t.getEventId())).findFirst().orElseThrow();

        // STANDARD (limit 3): 3rd failed retry → written off
        assertEquals(3, std.getRetryCount());
        assertEquals(TransactionStatus.LOST, std.getStatus(),
                "STANDARD customer must be written off at its 3rd failure");

        // HIGH_VALUE (limit 5): 3rd failure is NOT terminal — still in recovery with retries left
        assertEquals(4, hv.getRetryCount());
        assertEquals(TransactionStatus.IN_RECOVERY, hv.getStatus(),
                "HIGH_VALUE customer must NOT be written off at the standard 3-retry limit");
    }

    // ── 3. Control group: no agent intervention, honest ledger entry ──

    @Test
    void controlGroup_members_getNoIntervention() throws Exception {
        Transaction tx = seedPayment(
                "IT_CTRL_1", Customer.CustomerSegment.STANDARD, 0.5,
                new BigDecimal("2499"), FailureReason.NETWORK_ERROR, 0, TransactionStatus.AT_RISK);
        tx.setControlGroup(true);
        transactionRepository.save(tx);

        runBatchViaApi();

        JsonNode attempts = fetchAttempts();
        JsonNode mine = findAttemptForEvent(attempts, "IT_CTRL_1");
        assertNotNull(mine, "Control-group member must produce a ledger entry");
        assertEquals("NO_ACTION", mine.get("actionTaken").asText(),
                "Control group must not receive a normal intervention");
        assertTrue(mine.get("reasoning").asText().contains("Control group"));
        assertFalse(mine.get("customerNotified").asBoolean(),
                "Control group members must never be notified");
        assertEquals(0, mine.get("interventionCost").asDouble(), "No intervention ⇒ zero cost");
    }

    // ── 4. Webhook idempotency: duplicate delivery cannot double-ingest ──

    @Test
    void webhook_duplicateDelivery_doesNotCreateDuplicateTransaction() throws Exception {
        String payload = webhookPayload("evt_WB_1", "pay_WB_1");
        String first = postWebhook(payload);
        assertEquals("ingested", mapper.readTree(first).get("status").asText());

        String second = postWebhook(payload);
        assertEquals("duplicate", mapper.readTree(second).get("status").asText(),
                "Duplicate webhook delivery must be answered as duplicate, not re-ingested");

        assertEquals(1, transactionRepository.findAll().stream()
                .filter(t -> "evt_WB_1".equals(t.getEventId())).count());
        assertEquals(1, transactionRepository.findAll().stream()
                .filter(t -> "pay_WB_1".equals(t.getRazorpayPaymentId())).count());
        assertTrue(transactionRepository.existsByEventId("evt_WB_1"));
    }

    @Test
    void webhook_missingProviderEventId_derivesDeterministicKey() throws Exception {
        // Same payment without event_id → controller must derive "pay_webhook_pay_WB_2"
        String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"pay_WB_2\",\"amount\":100000,\"method\":\"upi\",\"email\":\"wb2@example.com\","
                + "\"error_code\":\"GATEWAY_ERROR\",\"error_source\":\"network\"}}}}";
        String first = postWebhook(payload);
        assertEquals("ingested", mapper.readTree(first).get("status").asText());

        String second = postWebhook(payload);
        assertEquals("duplicate", mapper.readTree(second).get("status").asText());
        assertEquals(1, transactionRepository.findAll().stream()
                .filter(t -> "pay_WB_2".equals(t.getRazorpayPaymentId())).count(),
                "Second delivery of the same payment must not create a second row");
    }

    @Test
    void eventId_uniquenessIsEnforcedAtDatabaseLevel() {
        Transaction a = seedPayment("IT_UNIQ_A", Customer.CustomerSegment.STANDARD, 0.5,
                new BigDecimal("1000"), FailureReason.NETWORK_ERROR, 0, TransactionStatus.AT_RISK);
        Transaction b = new Transaction();
        b.setEventId(a.getEventId());
        b.setStatus(TransactionStatus.AT_RISK);
        b.setFailureReason(FailureReason.NETWORK_ERROR);
        b.setAmount(new BigDecimal("1000"));
        b.setCreatedAt(LocalDateTime.now());

        assertThrows(DataIntegrityViolationException.class,
                () -> transactionRepository.saveAndFlush(b),
                "eventId unique constraint must reject a duplicate at the database level");
    }

    // ── 5. Attempts persist and are returned newest-first after a fresh request ──

    @Test
    void persistedAttempts_areReturnedByTheApi_sortedNewestFirst() throws Exception {
        seedPayment("IT_ATT_1", Customer.CustomerSegment.STANDARD, 0.9,
                new BigDecimal("2499"), FailureReason.NETWORK_ERROR, 0, TransactionStatus.AT_RISK);
        seedPayment("IT_ATT_2", Customer.CustomerSegment.HIGH_VALUE, 0.9,
                new BigDecimal("7999"), FailureReason.NETWORK_ERROR, 0, TransactionStatus.AT_RISK);

        runBatchViaApi();

        JsonNode firstFetch = fetchAttempts();
        assertTrue(firstFetch.size() >= 2);

        JsonNode mine1 = findAttemptForEvent(firstFetch, "IT_ATT_1");
        JsonNode mine2 = findAttemptForEvent(firstFetch, "IT_ATT_2");
        assertNotNull(mine1, "Attempt for IT_ATT_1 must be persisted and retrievable after the batch HTTP request completes");
        assertNotNull(mine2);

        // Newest-first ordering: a second request returns the same set, ids non-increasing.
        JsonNode secondFetch = fetchAttempts();
        List<Long> ids = new ArrayList<>();
        secondFetch.forEach(n -> ids.add(n.get("id").asLong()));
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i - 1) >= ids.get(i), "Attempts must be sorted newest first");
        }
    }

    // ── 6. Audit pipeline receives batch events ──

    @Test
    void auditPipeline_recordsLifecycleEvents() throws Exception {
        Transaction tx = seedPayment("IT_AUD_1", Customer.CustomerSegment.STANDARD, 0.9,
                new BigDecimal("2499"), FailureReason.NETWORK_ERROR, 0, TransactionStatus.AT_RISK);

        runBatchViaApi();

        List<AuditEvent> forEntity = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByTimestampDesc("TRANSACTION", String.valueOf(tx.getId()));
        assertFalse(forEntity.isEmpty(), "Batch must write audit events for the processed transaction");
        assertTrue(forEntity.stream().anyMatch(e -> e.getEventType() == AuditEvent.EventType.AI_RECOMMENDATION_RECEIVED));
        assertTrue(forEntity.stream().anyMatch(e ->
                        e.getEventType() == AuditEvent.EventType.RECOVERY_ATTEMPT_EXECUTED
                                || e.getEventType() == AuditEvent.EventType.RECOVERY_ATTEMPT_SUCCEEDED
                                || e.getEventType() == AuditEvent.EventType.RECOVERY_ATTEMPT_FAILED));

        // Batch-level events recorded too
        List<AuditEvent> all = auditEventRepository.findRecentEvents();
        assertTrue(all.stream().anyMatch(e -> e.getEventType() == AuditEvent.EventType.BATCH_STARTED));
        assertTrue(all.stream().anyMatch(e -> e.getEventType() == AuditEvent.EventType.BATCH_COMPLETED));

        // Retrievable through the API
        String body = mvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(mapper.readTree(body).size() > 0);
    }

    // ── 7. Scheduler collision is an expected skip, not a 409 error ──

    @Test
    void scheduler_collidesWithRunningBatch_skipsGracefully() throws Exception {
        long attemptsBefore = attemptRepository.count();
        AtomicBoolean flag = (AtomicBoolean) getField(orchestrator, "batchRunning");

        flag.set(true);
        try {
            // Must not throw and must not process anything while a batch is running.
            scheduler.scheduledRun();
            assertEquals(attemptsBefore, attemptRepository.count(),
                    "Scheduled run must not process items while another batch is running");
        } finally {
            flag.set(false);
        }

        // And a normal scheduled run after the flag clears still works.
        seedPayment("IT_SCH_1", Customer.CustomerSegment.STANDARD, 0.9,
                new BigDecimal("1000"), FailureReason.NETWORK_ERROR, 0, TransactionStatus.AT_RISK);
        scheduler.scheduledRun();
        assertTrue(attemptRepository.count() > attemptsBefore,
                "Scheduled run should process data when no batch is in progress");
    }

    // ── 8. SSE/blocking consistency: total == actual worklist size; counts reconcile ──

    @Test
    void batchTotal_equalsWorklistSize_andCountsReconcile() {
        seedPayment("IT_SSE_1", Customer.CustomerSegment.STANDARD, 0.9,
                new BigDecimal("2499"), FailureReason.NETWORK_ERROR, 0, TransactionStatus.AT_RISK);
        seedPayment("IT_SSE_2", Customer.CustomerSegment.HIGH_VALUE, 0.7,
                new BigDecimal("7999"), FailureReason.BANK_SERVER_DOWN, 0, TransactionStatus.AT_RISK);
        // A cooldown-skip candidate: eligible by status but skipped during processing —
        // it still counts toward the worklist and must produce an (SKIPPED) event.
        Transaction cd = seedPayment("IT_SSE_3", Customer.CustomerSegment.STANDARD, 0.5,
                new BigDecimal("1500"), FailureReason.NETWORK_ERROR, 1, TransactionStatus.IN_RECOVERY);
        cd.setLastAttemptAt(LocalDateTime.now().minusMinutes(5));
        transactionRepository.save(cd);

        int total = apiService.countEligible();
        List<RecoveryAttempt> streamed = new ArrayList<>();
        List<RecoveryAttempt> blocking = orchestrator.runBatchWithCallback(streamed::add);

        // Total (sent ahead of the SSE stream) equals the exact number of events emitted.
        assertEquals(total, streamed.size(),
                "SSE 'total' must equal the number of attempt events actually emitted");
        assertEquals(streamed.size(), blocking.size(),
                "Streaming and blocking runs must report identical worklist sizes");

        long succeeded = blocking.stream().filter(a -> a.getOutcome() == RecoveryAttempt.AttemptOutcome.SUCCESS).count();
        long failed = blocking.stream().filter(a -> a.getOutcome() == RecoveryAttempt.AttemptOutcome.FAILED).count();
        long skipped = blocking.stream().filter(a -> a.getOutcome() == RecoveryAttempt.AttemptOutcome.SKIPPED).count();
        assertEquals(blocking.size(), succeeded + failed + skipped,
                "Every processed item must reconcile to succeeded/failed/skipped (progress reaches 100%)");

        assertTrue(skipped >= 1, "Cooldown-skip candidate must be recorded as SKIPPED, not as a scheduled retry");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Transaction seedPayment(String eventId, Customer.CustomerSegment segment, double reliability,
                                    BigDecimal amount, FailureReason reason, int retryCount,
                                    TransactionStatus status) {
        Customer customer = new Customer();
        customer.setName("IT-" + eventId);
        customer.setEmail(eventId + "@example.com");
        customer.setPaymentReliabilityScore(reliability);
        customer.setCustomerSegment(segment);
        customerRepository.save(customer);

        Subscription sub = new Subscription();
        sub.setCustomer(customer);
        sub.setPlanName(amount.compareTo(new BigDecimal("300")) < 0 ? "Starter"
                : amount.compareTo(new BigDecimal("1000")) < 0 ? "Growth" : "Pro");
        sub.setAmount(amount);
        sub.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
        subscriptionRepository.save(sub);

        Transaction tx = new Transaction();
        tx.setSubscription(sub);
        tx.setAmount(amount);
        tx.setStatus(status);
        tx.setFailureReason(reason);
        tx.setRetryCount(retryCount);
        tx.setCreatedAt(LocalDateTime.now().minusHours(2));
        tx.setRazorpayPaymentId(eventId); // synthetic external key for lookups
        tx.setEventId(eventId);
        return transactionRepository.save(tx);
    }

    private void runBatchViaApi() throws Exception {
        mvc.perform(post("/api/recovery/run-batch"))
                .andExpect(status().isOk());
    }

    private String postWebhook(String payload) throws Exception {
        return mvc.perform(post("/api/webhooks/razorpay/payment-failed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode fetchAttempts() throws Exception {
        String body = mvc.perform(get("/api/recovery/attempts"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private JsonNode findTransaction(JsonNode transactions, String eventId) {
        for (JsonNode n : transactions) {
            if (eventId.equals(n.get("eventId").asText())) return n;
        }
        return null;
    }

    private JsonNode findAttemptForEvent(JsonNode attempts, String eventId) {
        for (JsonNode n : attempts) {
            JsonNode tx = n.get("transaction");
            if (tx != null && tx.has("eventId") && eventId.equals(tx.get("eventId").asText())) return n;
            JsonNode cs = n.get("checkoutSession");
            if (cs != null && cs.has("eventId") && eventId.equals(cs.get("eventId").asText())) return n;
            JsonNode rv = n.get("receivable");
            if (rv != null && rv.has("eventId") && eventId.equals(rv.get("eventId").asText())) return n;
        }
        return null;
    }

    private String webhookPayload(String eventId, String paymentId) {
        return "{\"event\":\"payment.failed\",\"event_id\":\"" + eventId + "\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + paymentId + "\",\"amount\":249900,\"method\":\"card\","
                + "\"email\":\"wh@example.com\",\"contact\":\"9999999999\","
                + "\"error_code\":\"BAD_REQUEST_ERROR\",\"error_reason\":\"insufficient_funds\","
                + "\"error_source\":\"bank\",\"error_description\":\"Insufficient funds in account\"}}}}";
    }

    private static Object getField(Object target, String name) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

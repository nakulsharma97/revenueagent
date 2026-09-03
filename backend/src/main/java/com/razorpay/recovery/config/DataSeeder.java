package com.razorpay.recovery.config;

import com.razorpay.recovery.customer.*;
import com.razorpay.recovery.subscription.*;
import com.razorpay.recovery.transaction.*;
import com.razorpay.recovery.checkout.*;
import com.razorpay.recovery.receivable.*;
import com.razorpay.recovery.recovery.*;
import com.razorpay.recovery.transaction.Transaction.FailureReason;
import com.razorpay.recovery.checkout.CheckoutSession.AbandonmentReason;
import com.razorpay.recovery.receivable.Receivable.ReceivableStatus;
import com.razorpay.recovery.checkout.CheckoutSession.CheckoutStatus;
import com.razorpay.recovery.transaction.Transaction.TransactionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ReceivableRepository receivableRepository;
    private final RecoveryOrchestratorService orchestrator;
    private final com.razorpay.recovery.intelligence.RecoveryExperimentRepository experimentRepository;
    private final Random random = new Random(42);

    /** Set false in tests (src/test/resources/application.properties) so each test controls its own data. */
    @Value("${recovery.seed-data:true}")
    private boolean seedData = true;

    // Subscription tiers mirror typical Indian SaaS pricing (annual INR, monthly billing).
    // ₹299 Starter is deliberately below the ₹500 min-discount threshold to test
    // the RulesEngine's amount-gating logic in the demo.
    private static final String[] PLANS = {"Starter", "Growth", "Pro", "Enterprise"};
    private static final BigDecimal[] AMOUNTS = {
            new BigDecimal("299"),   // Starter: below discount threshold, can't offer discount
            new BigDecimal("999"),   // Growth: eligible for discount (15% = ₹149.85)
            new BigDecimal("2499"),  // Pro: mid-tier, discount makes financial sense
            new BigDecimal("7999")   // Enterprise: high-value, discount always worth recovering
    };
    // Realistic Indian payment failure distribution based on NPCI/FSSAI data:
    // UPI ~65% (majority rail in India), Card ~25%, Netbanking ~10%.
    // Within UPI: PIN mismatch is most common (fat-finger on mobile), timeout
    // is second (PSP routing congestion), VPA invalid is rare (typo in handle).
    // Within cards: insufficient funds dominates (salary-cycle timing),
    // network errors are common on mobile data, terminal failures are rare.
    private static final FailureReason[] REASON_POOL = {
            // UPI failures (65% of pool — 13 of 20 slots)
            FailureReason.UPI_PIN_MISMATCH, FailureReason.UPI_PIN_MISMATCH, FailureReason.UPI_PIN_MISMATCH,
            FailureReason.UPI_TIMEOUT, FailureReason.UPI_TIMEOUT, FailureReason.UPI_TIMEOUT,
            FailureReason.UPI_TIMEOUT,
            FailureReason.VPA_INVALID,
            // Card failures (25% of pool — 5 of 20 slots)
            FailureReason.INSUFFICIENT_FUNDS, FailureReason.INSUFFICIENT_FUNDS,
            FailureReason.NETWORK_ERROR,
            FailureReason.CARD_EXPIRED,
            FailureReason.INVALID_CVV,
            // Netbanking failures (10% of pool — 2 of 20 slots)
            FailureReason.BANK_SESSION_EXPIRED, FailureReason.BANK_SESSION_EXPIRED,
            // Terminal/rare (included for edge-case coverage)
            FailureReason.CARD_STOLEN_FLAG,
            FailureReason.BANK_SERVER_DOWN,
            FailureReason.INSUFFICIENT_FUNDS,
            FailureReason.NETWORK_ERROR
    };

    private static final AbandonmentReason[] ABANDON_REASONS = {
            AbandonmentReason.PRICE_HESITATION, AbandonmentReason.PRICE_HESITATION, AbandonmentReason.PRICE_HESITATION,
            AbandonmentReason.DISTRACTED_NO_COMPLETION, AbandonmentReason.DISTRACTED_NO_COMPLETION,
            AbandonmentReason.PAYMENT_METHOD_DECLINED,
            AbandonmentReason.TECHNICAL_ERROR
    };

    private static final String[] BUSINESS_NAMES = {
            "Acme Corp", "TechStart India", "GreenLeaf Foods", "Metro Logistics",
            "CloudFirst Solutions", "Bharat Manufacturing", "SwiftPay Services", "DataVista Analytics"
    };

    public DataSeeder(CustomerRepository customerRepository,
                       SubscriptionRepository subscriptionRepository,
                       TransactionRepository transactionRepository,
                       CheckoutSessionRepository checkoutSessionRepository,
                       ReceivableRepository receivableRepository,
                       RecoveryOrchestratorService orchestrator,
                       com.razorpay.recovery.intelligence.RecoveryExperimentRepository experimentRepository) {
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.receivableRepository = receivableRepository;
        this.orchestrator = orchestrator;
        this.experimentRepository = experimentRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedData) {
            org.slf4j.LoggerFactory.getLogger(DataSeeder.class)
                    .info("DataSeeder: seed-data=false — skipping demo data (test mode)");
            return;
        }

        // 1. Seed 200 payment failure transactions
        seedPaymentFailures(200);

        // 2. Assign customer segments: top 20% by transaction amount = HIGH_VALUE
        assignCustomerSegments();

        // 3. Seed 80 abandoned checkout sessions
        seedCheckoutAbandonment(80);

        // 4. Seed 40 overdue receivables
        seedReceivables(40);

        // 5. Seed deterministic, named demo scenarios (predictable Next-Best-Action outcomes)
        seedDemoScenarios();

        // 6. Seed declared experimentation policies
        seedExperiments();

        // 7. Auto-run a recovery batch so dashboard shows real data on first load
        try {
            var results = orchestrator.runBatch();
            org.slf4j.LoggerFactory.getLogger(DataSeeder.class)
                .info("DataSeeder: Auto batch complete — {} recovery attempts processed", results.size());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DataSeeder.class)
                .error("DataSeeder: Auto batch failed — {}", e.getMessage(), e);
        }
    }

    /**
     * Deterministic named scenarios for the hackathon walkthrough. Each entity is chosen
     * so the Next-Best-Action engine (pure, deterministic) produces a predictable,
     * explainable decision — see docs/DEMO_SCRIPT.md "Predictable demo scenarios".
     */
    private void seedDemoScenarios() {
        // S1 — LIKELY_TO_SELF_RECOVER: reliable customer, transient blip → free silent retry only.
        scenarioPayment("Riya Sharma", "riya@example.com", 0.88, new BigDecimal("9999"),
                FailureReason.NETWORK_ERROR, 0, Customer.CustomerSegment.STANDARD, "pay_scen_self_heal");

        // S2 — terminal card, customer must act → SEND_PAYMENT_LINK beats a wasted discount.
        scenarioPayment("Aarav Mehta", "aarav@example.com", 0.65, new BigDecimal("4999"),
                FailureReason.CARD_EXPIRED, 1, Customer.CustomerSegment.STANDARD, "pay_scen_new_card");

        // S3 — transient UPI timeout after one failed retry → retry inside the recovery window.
        scenarioPayment("Kabir Nair", "kabir@example.com", 0.55, new BigDecimal("2000"),
                FailureReason.UPI_TIMEOUT, 1, Customer.CustomerSegment.STANDARD, "pay_scen_retry_window");

        // S4 — HIGH_VALUE customer, insufficient funds, 2 failed attempts → careful incentive
        //      within the HIGH_VALUE ceiling (tiers up to 25% are simulated, > the 15% standard cap).
        scenarioPayment("Meera Iyer", "meera@example.com", 0.80, new BigDecimal("15999"),
                FailureReason.INSUFFICIENT_FUNDS, 2, Customer.CustomerSegment.HIGH_VALUE, "pay_scen_hv_care");

        // S5 — 3rd failure approaches the segment's retry limit → the final retry is flagged
        //      for human sign-off (review case created) rather than executed silently.
        scenarioPayment("Dev Rao", "dev@example.com", 0.30, new BigDecimal("899"),
                FailureReason.UPI_PIN_MISMATCH, 2, Customer.CustomerSegment.STANDARD, "pay_scen_signoff");

        // S6 — very large failed payment → anomaly HIGH + human-attention state + review case.
        scenarioPayment("Zoya Khan", "zoya@example.com", 0.50, new BigDecimal("150000"),
                FailureReason.CARD_STOLEN_FLAG, 1, Customer.CustomerSegment.STANDARD, "pay_scen_big_ticket");

        // Checkout — price-hesitant high-value cart → discount offer is the next best action.
        scenarioCheckout("Kavya Singh", "kavya@example.com", new BigDecimal("24999"),
                CheckoutSession.AbandonmentReason.PRICE_HESITATION, "chk_scen_price_hes");

        // Checkout — high-intent shopper who got distracted → direct payment link.
        scenarioCheckout("Nikhil Verma", "nikhil@example.com", new BigDecimal("9999"),
                CheckoutSession.AbandonmentReason.DISTRACTED_NO_COMPLETION, "chk_scen_link");

        // Receivable — long-overdue B2B invoice → payment-plan offer.
        scenarioReceivable("Metro Logistics", "metro@example.com", new BigDecimal("250000"), 60,
                false, "inv_scen_plan");

        // Receivable — broken promise-to-pay, still inside the no-plan window → follow-up call.
        scenarioReceivable("GreenLeaf Foods", "accounts@greenleaf.in", new BigDecimal("85000"), 10,
                true, "inv_scen_promise");

        org.slf4j.LoggerFactory.getLogger(DataSeeder.class)
                .info("DataSeeder: Seeded {} named demo scenarios for the RecoveryOS walkthrough", 10);
    }

    private void scenarioPayment(String name, String email, double reliability, BigDecimal amount,
                                 FailureReason reason, int retryCount,
                                 Customer.CustomerSegment segment, String eventId) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        customer.setPaymentReliabilityScore(reliability);
        customer.setCustomerSegment(segment);
        customerRepository.save(customer);

        Subscription sub = new Subscription();
        sub.setCustomer(customer);
        sub.setPlanName(amount.doubleValue() >= 10000 ? "Enterprise" : "Pro");
        sub.setAmount(amount);
        sub.setBillingCycle("MONTHLY");
        sub.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
        subscriptionRepository.save(sub);

        Transaction tx = new Transaction();
        tx.setSubscription(sub);
        tx.setAmount(amount);
        tx.setStatus(retryCount > 0 ? TransactionStatus.IN_RECOVERY : TransactionStatus.AT_RISK);
        tx.setFailureReason(reason);
        tx.setRetryCount(retryCount);
        tx.setCreatedAt(LocalDateTime.now().minusHours(5 + retryCount * 3));
        tx.setEventId(eventId);
        tx.setHeldOut(false);
        tx.setControlGroup(false);
        transactionRepository.save(tx);
    }

    private void scenarioCheckout(String name, String email, BigDecimal amount,
                                  CheckoutSession.AbandonmentReason reason, String eventId) {
        CheckoutSession session = new CheckoutSession();
        session.setCustomerId("SCN-" + eventId);
        session.setCustomerName(name);
        session.setCustomerEmail(email);
        session.setCartAmount(amount);
        session.setStartedAt(LocalDateTime.now().minusHours(6));
        session.setAbandonedAt(LocalDateTime.now().minusHours(3));
        session.setStatus(CheckoutStatus.ABANDONED);
        session.setAbandonmentReason(reason);
        session.setReminderCount(0);
        session.setEventId(eventId);
        session.setHeldOut(false);
        session.setControlGroup(false);
        checkoutSessionRepository.save(session);
    }

    private void scenarioReceivable(String business, String email, BigDecimal amount, int daysOverdue,
                                    boolean brokenPromise, String eventId) {
        Receivable receivable = new Receivable();
        receivable.setBusinessCustomerId("SCN-" + eventId);
        receivable.setBusinessName(business);
        receivable.setContactEmail(email);
        receivable.setInvoiceAmount(amount);
        receivable.setInvoiceNumber("INV-DEMO-" + eventId);
        receivable.setDueDate(LocalDate.now().minusDays(daysOverdue));
        receivable.setDaysOverdue(daysOverdue);
        receivable.setStatus(ReceivableStatus.OVERDUE);
        receivable.setReminderCount(brokenPromise ? 1 : 0);
        receivable.setEventId(eventId);
        receivable.setHeldOut(false);
        receivable.setControlGroup(false);
        if (brokenPromise) {
            receivable.setPromisedPaymentDate(LocalDate.now().minusDays(2));
            receivable.setPromiseStatus(com.razorpay.recovery.receivable.Receivable.PromiseStatus.BROKEN);
        }
        receivableRepository.save(receivable);
    }

    /** Seed declared experimentation policies (the control split itself lives in the data). */
    private void seedExperiments() {
        if (experimentRepository.count() > 0) return;
        LocalDate today = LocalDate.now();
        experimentRepository.save(experiment("Payment Link vs Reminder (Payments)",
                "Tests whether a direct pay-link out-earns a generic reminder on mid-value payment failures.",
                15.0, "SEND_PAYMENT_LINK vs SEND_REMINDER", "PAYMENT", "STANDARD", today.minusDays(7), today.plusDays(23)));
        experimentRepository.save(experiment("Discount Sensitivity (Checkout)",
                "Measures incremental lift of a 10% offer on price-hesitant abandoned carts.",
                20.0, "OFFER_DISCOUNT(10%) vs CHECKOUT_REMINDER", "CHECKOUT", "ALL", today.minusDays(3), today.plusDays(27)));
        experimentRepository.save(experiment("Payment-Plan Adoption (Receivables)",
                "Tests payment-plan offers against plain reminders for invoices overdue 15+ days.",
                15.0, "OFFER_PAYMENT_PLAN vs SEND_REMINDER", "RECEIVABLE", "ALL", today.minusDays(10), today.plusDays(20)));
        org.slf4j.LoggerFactory.getLogger(DataSeeder.class)
                .info("DataSeeder: Declared 3 recovery experiments");
    }

    private com.razorpay.recovery.intelligence.RecoveryExperiment experiment(String name, String description,
            double controlPct, String policy, String segment, String customerSegment,
            LocalDate start, LocalDate end) {
        com.razorpay.recovery.intelligence.RecoveryExperiment e = new com.razorpay.recovery.intelligence.RecoveryExperiment();
        e.setName(name);
        e.setDescription(description);
        e.setControlPercentage(controlPct);
        e.setTreatmentPolicy(policy);
        e.setTargetSegment(segment);
        e.setTargetCustomerSegment(customerSegment);
        e.setStatus(com.razorpay.recovery.intelligence.RecoveryExperiment.Status.ACTIVE);
        e.setStartDate(start);
        e.setEndDate(end);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    /**
     * Derive customer segments: top 20% by single-transaction amount = HIGH_VALUE.
     * A real deployment would compute this from actual LTV/tenure data.
     */
    private void assignCustomerSegments() {
        List<Transaction> allTx = transactionRepository.findAll();
        // Sort by amount descending
        List<Transaction> sorted = allTx.stream()
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .toList();
        int threshold = Math.max(1, (int)(sorted.size() * 0.20));
        java.util.Set<Long> highValueCustomerIds = new java.util.HashSet<>();
        for (int i = 0; i < threshold && i < sorted.size(); i++) {
            Transaction tx = sorted.get(i);
            if (tx.getSubscription() != null && tx.getSubscription().getCustomer() != null) {
                highValueCustomerIds.add(tx.getSubscription().getCustomer().getId());
            }
        }
        for (Customer c : customerRepository.findAll()) {
            if (highValueCustomerIds.contains(c.getId())) {
                c.setCustomerSegment(Customer.CustomerSegment.HIGH_VALUE);
            }
            customerRepository.save(c);
        }
        org.slf4j.LoggerFactory.getLogger(DataSeeder.class)
                .info("DataSeeder: Assigned {} HIGH_VALUE customers out of {}", highValueCustomerIds.size(), customerRepository.findAll().size());
    }

    private void seedPaymentFailures(int count) {
        for (int i = 0; i < count; i++) {
            Customer customer = new Customer();
            customer.setName("Customer " + (i + 1));
            customer.setEmail("customer" + (i + 1) + "@example.com");
            customer.setPaymentReliabilityScore(0.2 + random.nextDouble() * 0.7);
            customerRepository.save(customer);

            Subscription sub = new Subscription();
            sub.setCustomer(customer);
            sub.setPlanName(PLANS[random.nextInt(PLANS.length)]);
            sub.setAmount(AMOUNTS[random.nextInt(AMOUNTS.length)]);
            sub.setBillingCycle("MONTHLY");
            sub.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
            subscriptionRepository.save(sub);

            Transaction tx = new Transaction();
            tx.setSubscription(sub);
            tx.setAmount(sub.getAmount());
            tx.setStatus(TransactionStatus.AT_RISK);
            tx.setFailureReason(REASON_POOL[random.nextInt(REASON_POOL.length)]);
            tx.setRetryCount(0);
            tx.setCreatedAt(LocalDateTime.now().minusHours(random.nextInt(72)));
            tx.setEventId("pay_evt_" + (1000 + i));
            tx.setHeldOut(random.nextDouble() < 0.20);
            tx.setControlGroup(random.nextDouble() < 0.15); // ~15% control group for uplift measurement
            transactionRepository.save(tx);
        }
    }

    private void seedCheckoutAbandonment(int count) {
        BigDecimal[] CART_AMOUNTS = {
                new BigDecimal("499"), new BigDecimal("1299"), new BigDecimal("2999"),
                new BigDecimal("4999"), new BigDecimal("9999"), new BigDecimal("15999")
        };

        for (int i = 0; i < count; i++) {
            CheckoutSession session = new CheckoutSession();
            session.setCustomerId("CHK-" + (1000 + i));
            session.setCustomerName("Shopper " + (i + 1));
            session.setCustomerEmail("shopper" + (i + 1) + "@example.com");
            session.setCartAmount(CART_AMOUNTS[random.nextInt(CART_AMOUNTS.length)]);
            session.setStartedAt(LocalDateTime.now().minusHours(random.nextInt(48)));
            session.setAbandonedAt(LocalDateTime.now().minusHours(random.nextInt(24)));
            session.setStatus(CheckoutStatus.ABANDONED);
            session.setAbandonmentReason(ABANDON_REASONS[random.nextInt(ABANDON_REASONS.length)]);
            session.setReminderCount(0);
            session.setEventId("chk_sess_" + (3000 + i));
            session.setHeldOut(random.nextDouble() < 0.20);
            session.setControlGroup(random.nextDouble() < 0.15);
            checkoutSessionRepository.save(session);
        }
    }

    private void seedReceivables(int count) {
        BigDecimal[] INVOICE_AMOUNTS = {
                new BigDecimal("25000"), new BigDecimal("50000"), new BigDecimal("125000"),
                new BigDecimal("350000"), new BigDecimal("750000"), new BigDecimal("1500000")
        };

        for (int i = 0; i < count; i++) {
            Receivable receivable = new Receivable();
            receivable.setBusinessCustomerId("INV-" + (2000 + i));
            receivable.setBusinessName(BUSINESS_NAMES[random.nextInt(BUSINESS_NAMES.length)] + " " + (char)('A' + i % 26));
            receivable.setContactEmail("accounts@" + BUSINESS_NAMES[random.nextInt(BUSINESS_NAMES.length)].toLowerCase().replace(" ", "") + ".in");
            receivable.setInvoiceAmount(INVOICE_AMOUNTS[random.nextInt(INVOICE_AMOUNTS.length)]);
            receivable.setInvoiceNumber("INV-2026-" + String.format("%04d", i + 1));
            receivable.setDueDate(LocalDate.now().minusDays(10 + random.nextInt(60)));
            receivable.setDaysOverdue(10 + random.nextInt(60));
            receivable.setStatus(ReceivableStatus.OVERDUE);
            receivable.setReminderCount(0);
            receivable.setEventId("inv_" + receivable.getInvoiceNumber() + "_" + receivable.getDueDate());
            receivable.setHeldOut(random.nextDouble() < 0.20);
            receivable.setControlGroup(random.nextDouble() < 0.15);

            // ~30% of receivables get a promise-to-pay with various statuses
            if (random.nextDouble() < 0.30) {
                int daysOffset = random.nextInt(5) - 2; // -2 to +2 days from now
                receivable.setPromisedPaymentDate(LocalDate.now().plusDays(daysOffset));
                if (daysOffset < 0) {
                    // Promise date in the past -> BROKEN
                    receivable.setPromiseStatus(com.razorpay.recovery.receivable.Receivable.PromiseStatus.BROKEN);
                } else if (daysOffset == 0) {
                    // Due today -> PROMISED
                    receivable.setPromiseStatus(com.razorpay.recovery.receivable.Receivable.PromiseStatus.PROMISED);
                } else {
                    // Future date -> PROMISED
                    receivable.setPromiseStatus(com.razorpay.recovery.receivable.Receivable.PromiseStatus.PROMISED);
                }
            }

            receivableRepository.save(receivable);
        }
    }
}

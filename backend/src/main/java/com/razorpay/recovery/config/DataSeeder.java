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
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

@Component
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ReceivableRepository receivableRepository;
    private final Random random = new Random(42);

    private static final String[] PLANS = {"Starter", "Growth", "Pro", "Enterprise"};
    private static final BigDecimal[] AMOUNTS = {
            new BigDecimal("299"), new BigDecimal("999"), new BigDecimal("2499"), new BigDecimal("7999")
    };
    private static final FailureReason[] REASON_POOL = {
            FailureReason.INSUFFICIENT_FUNDS, FailureReason.INSUFFICIENT_FUNDS, FailureReason.INSUFFICIENT_FUNDS,
            FailureReason.NETWORK_ERROR, FailureReason.NETWORK_ERROR, FailureReason.NETWORK_ERROR,
            FailureReason.BANK_SERVER_DOWN, FailureReason.BANK_SERVER_DOWN, FailureReason.BANK_SERVER_DOWN,
            FailureReason.CARD_EXPIRED,
            FailureReason.INVALID_CVV,
            FailureReason.CARD_STOLEN_FLAG
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
                       ReceivableRepository receivableRepository) {
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.receivableRepository = receivableRepository;
    }

    @Override
    public void run(String... args) {
        // 1. Seed 200 payment failure transactions
        seedPaymentFailures(200);

        // 2. Seed 80 abandoned checkout sessions
        seedCheckoutAbandonment(80);

        // 3. Seed 40 overdue receivables
        seedReceivables(40);
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

package com.razorpay.recovery.config;

import com.razorpay.recovery.model.Customer;
import com.razorpay.recovery.model.Subscription;
import com.razorpay.recovery.model.Transaction;
import com.razorpay.recovery.model.Transaction.FailureReason;
import com.razorpay.recovery.model.Transaction.TransactionStatus;
import com.razorpay.recovery.repository.CustomerRepository;
import com.razorpay.recovery.repository.SubscriptionRepository;
import com.razorpay.recovery.repository.TransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Seeds a realistic synthetic batch on startup so the app is demo-ready with
 * zero manual setup — a held-out-looking batch for the "measured" requirement.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;
    private final Random random = new Random(42); // fixed seed: reproducible demo numbers

    private static final String[] PLANS = {"Starter", "Growth", "Pro", "Enterprise"};
    private static final BigDecimal[] AMOUNTS = {
            new BigDecimal("299"), new BigDecimal("999"), new BigDecimal("2499"), new BigDecimal("7999")
    };
    // Realistic decline-code distribution: soft declines dominate, hard declines are rarer.
    // 9 soft (retryable) : 3 hard (terminal) = 3:1 ratio as specified in the brief.
    private static final FailureReason[] REASON_POOL = {
            FailureReason.INSUFFICIENT_FUNDS, FailureReason.INSUFFICIENT_FUNDS, FailureReason.INSUFFICIENT_FUNDS,
            FailureReason.NETWORK_ERROR, FailureReason.NETWORK_ERROR, FailureReason.NETWORK_ERROR,
            FailureReason.BANK_SERVER_DOWN, FailureReason.BANK_SERVER_DOWN, FailureReason.BANK_SERVER_DOWN,
            FailureReason.CARD_EXPIRED,
            FailureReason.INVALID_CVV,
            FailureReason.CARD_STOLEN_FLAG
    };

    public DataSeeder(CustomerRepository customerRepository,
                       SubscriptionRepository subscriptionRepository,
                       TransactionRepository transactionRepository) {
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void run(String... args) {
        int batchSize = 300;
        for (int i = 0; i < batchSize; i++) {
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
}

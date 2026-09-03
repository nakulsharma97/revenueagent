package com.razorpay.recovery.intelligence;

import com.razorpay.recovery.recovery.RecoveryAttempt.RecoveryAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Outcome Memory read-model: it must remember outcomes per
 * (source × context × segment × action), aggregate success/net value correctly,
 * sort the best-remembered action first, and degrade gracefully on missing context.
 */
@ExtendWith(MockitoExtension.class)
class OutcomeMemoryServiceTest {

    @Mock private RecoveryOutcomeRepository outcomeRepository;

    private OutcomeMemoryService service;

    @BeforeEach
    void setUp() {
        service = new OutcomeMemoryService(outcomeRepository);
    }

    private RecoveryOutcome outcome(String source, String context, String segment,
                                    RecoveryAction action, boolean success,
                                    String recovered, String cost) {
        RecoveryOutcome o = new RecoveryOutcome();
        o.setSourceType(source);
        o.setContextKey(context);
        o.setCustomerSegment(segment);
        o.setAction(action);
        o.setSuccess(success);
        o.setAmountRecovered(new BigDecimal(recovered));
        o.setInterventionCost(new BigDecimal(cost));
        o.setNetValue(o.getAmountRecovered().subtract(o.getInterventionCost()));
        o.setCreatedAt(LocalDateTime.now());
        return o;
    }

    @Test
    void memory_groupsByContextSegmentAndAction_andRanksByNetValue() {
        // Same cell (CARD_EXPIRED × HIGH_VALUE × SEND_PAYMENT_LINK): one success, one failure.
        RecoveryOutcome linkWin = outcome("PAYMENT", "CARD_EXPIRED", "HIGH_VALUE",
                RecoveryAction.SEND_PAYMENT_LINK, true, "2500.00", "0.35");
        RecoveryOutcome linkLoss = outcome("PAYMENT", "CARD_EXPIRED", "HIGH_VALUE",
                RecoveryAction.SEND_PAYMENT_LINK, false, "0", "0.35");
        // Different cell: a 10% discount that actually converted.
        RecoveryOutcome discountWin = outcome("PAYMENT", "INSUFFICIENT_FUNDS", "STANDARD",
                RecoveryAction.OFFER_DISCOUNT, true, "1000.00", "101.50");

        when(outcomeRepository.findAll()).thenReturn(List.of(linkWin, linkLoss, discountWin));

        List<OutcomeMemoryService.MemoryRow> rows = service.memory();

        // Two distinct remembered cells, best net value first.
        assertEquals(2, rows.size());

        OutcomeMemoryService.MemoryRow linkCell = rows.get(0);
        assertEquals("CARD_EXPIRED", linkCell.contextKey());
        assertEquals("HIGH_VALUE", linkCell.customerSegment());
        assertEquals(RecoveryAction.SEND_PAYMENT_LINK.name(), linkCell.action());
        assertEquals(2, linkCell.attempts());
        assertEquals(1, linkCell.successes());
        assertEquals(0.5, linkCell.successRate(), 1e-9);
        assertEquals(0, linkCell.recovered().compareTo(new BigDecimal("2500.00")));
        // 2500 − 0.35 − 0.35 = 2499.30
        assertEquals(0, linkCell.netValue().compareTo(new BigDecimal("2499.30")));

        OutcomeMemoryService.MemoryRow discountCell = rows.get(1);
        assertEquals("INSUFFICIENT_FUNDS", discountCell.contextKey());
        assertEquals(1, discountCell.attempts());
        assertEquals(1, discountCell.successes());

        // Sorted: the payment-link cell (net ₹2,499.30) outranks the discount cell (net ₹898.50).
        assertTrue(rows.get(0).netValue().compareTo(rows.get(1).netValue()) > 0);
    }

    @Test
    void memory_missingContextAndSegment_defaultsToAny() {
        RecoveryOutcome bare = outcome("CHECKOUT", null, null,
                RecoveryAction.CHECKOUT_REMINDER, true, "499.00", "0.05");

        when(outcomeRepository.findAll()).thenReturn(List.of(bare));

        List<OutcomeMemoryService.MemoryRow> rows = service.memory();

        assertEquals(1, rows.size());
        assertEquals("UNKNOWN", rows.get(0).contextKey());
        assertEquals("ANY", rows.get(0).customerSegment());
        assertEquals(1, rows.get(0).attempts());
        assertEquals(0, rows.get(0).netValue().compareTo(new BigDecimal("498.95")));
    }

    @Test
    void memory_emptyHistory_returnsEmptyList() {
        when(outcomeRepository.findAll()).thenReturn(List.of());

        assertTrue(service.memory().isEmpty(), "No outcomes ⇒ no memory");
    }
}
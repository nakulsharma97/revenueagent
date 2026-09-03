package com.razorpay.recovery.intelligence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The system's <b>Outcome Memory</b>: a read-model over persisted {@link RecoveryOutcome}s
 * grouped by (source × context × customer segment × action). Unlike the per-action rollup
 * in {@link OutcomeLearningService}, memory keeps the <em>context</em> each outcome was
 * observed in — CARD_EXPIRED, PRICE_HESITATION, OVERDUE_60+, … — so the demo can show
 * what has historically worked for a specific situation ("for CARD_EXPIRED in HIGH_VALUE,
 * payment links converted 2 of 3 times for ₹X net"). Rows are ordered by net value so the
 * best-remembered actions float to the top.
 *
 * <p>This is a derived read-model, not a separate store: it always reflects the outcome
 * history exactly, and every batch run feeds it automatically via
 * {@link RecoveryIntelligenceService#recordOutcome(com.razorpay.recovery.recovery.RecoveryAttempt)}.
 */
@Service
public class OutcomeMemoryService {

    private final RecoveryOutcomeRepository outcomeRepository;

    public OutcomeMemoryService(RecoveryOutcomeRepository outcomeRepository) {
        this.outcomeRepository = outcomeRepository;
    }

    /** One remembered cell: a (source × context × segment × action) prior. */
    public record MemoryRow(
            String sourceType,
            String contextKey,
            String customerSegment,
            String action,
            long attempts,
            long successes,
            double successRate,
            BigDecimal recovered,
            BigDecimal netValue,
            LocalDateTime lastSeen
    ) {}

    /** All remembered cells, best (highest net value) first. */
    @Transactional(readOnly = true)
    public List<MemoryRow> memory() {
        Map<CellKey, Accumulator> cells = new LinkedHashMap<>();
        for (RecoveryOutcome o : outcomeRepository.findAll()) {
            CellKey key = new CellKey(
                    blankTo(o.getSourceType(), "ANY"),
                    blankTo(o.getContextKey(), "UNKNOWN"),
                    blankTo(o.getCustomerSegment(), "ANY"),
                    o.getAction() == null ? "?" : o.getAction().name());
            cells.computeIfAbsent(key, k -> new Accumulator()).add(o);
        }
        List<MemoryRow> rows = new ArrayList<>();
        cells.forEach((k, acc) -> rows.add(new MemoryRow(
                k.sourceType(), k.contextKey(), k.customerSegment(), k.action(),
                acc.attempts, acc.successes,
                acc.attempts == 0 ? 0.0 : (double) acc.successes / acc.attempts,
                acc.recovered, acc.netValue, acc.lastSeen)));
        rows.sort((a, b) -> b.netValue().compareTo(a.netValue()));
        return rows;
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private record CellKey(String sourceType, String contextKey, String customerSegment, String action) {}

    private static final class Accumulator {
        long attempts;
        long successes;
        BigDecimal recovered = BigDecimal.ZERO;
        BigDecimal netValue = BigDecimal.ZERO;
        LocalDateTime lastSeen;

        void add(RecoveryOutcome o) {
            attempts++;
            if (o.isSuccess()) successes++;
            if (o.getAmountRecovered() != null) recovered = recovered.add(o.getAmountRecovered());
            if (o.getNetValue() != null) netValue = netValue.add(o.getNetValue());
            if (o.getCreatedAt() != null && (lastSeen == null || o.getCreatedAt().isAfter(lastSeen))) {
                lastSeen = o.getCreatedAt();
            }
        }
    }
}
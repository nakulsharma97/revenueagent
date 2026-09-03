package com.razorpay.recovery.intelligence;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns raw persisted {@link RecoveryOutcome}s into per-action performance statistics
 * for the Action Performance Lab. Actions are ranked by <b>net</b> value (recovered
 * minus intervention costs), never by success rate alone — a cheap reminder that
 * recovers ₹8,000 at 90% cost outranks a ₹2,000-discount action that recovers ₹9,000.
 */
@Service
public class OutcomeLearningService {

    /** One row of the Action Performance Lab. */
    public record ActionPerformance(
            String action,
            long attempts,
            long successes,
            double successRate,
            BigDecimal recovered,
            BigDecimal interventionCost,
            BigDecimal netValue,
            double avgFatigueAtDecision
    ) {}

    /** Aggregate all outcomes by action and rank by net value descending. */
    public List<ActionPerformance> rankByNetValue(List<RecoveryOutcome> outcomes) {
        Map<String, List<RecoveryOutcome>> byAction = new LinkedHashMap<>();
        for (RecoveryOutcome o : outcomes) {
            byAction.computeIfAbsent(o.getAction() == null ? "UNKNOWN" : o.getAction().name(), k -> new ArrayList<>()).add(o);
        }
        List<ActionPerformance> rows = new ArrayList<>();
        byAction.forEach((action, list) -> {
            long successes = list.stream().filter(RecoveryOutcome::isSuccess).count();
            BigDecimal recovered = list.stream()
                    .map(o -> o.getAmountRecovered() == null ? BigDecimal.ZERO : o.getAmountRecovered())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cost = list.stream()
                    .map(o -> o.getInterventionCost() == null ? BigDecimal.ZERO : o.getInterventionCost())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            double avgFatigue = list.stream().mapToDouble(RecoveryOutcome::getFatigueBefore).average().orElse(0);
            rows.add(new ActionPerformance(
                    action,
                    list.size(),
                    successes,
                    list.isEmpty() ? 0 : Math.round(successes * 10000.0 / list.size()) / 100.0,
                    recovered,
                    cost,
                    recovered.subtract(cost).setScale(2, RoundingMode.HALF_UP),
                    Math.round(avgFatigue * 100.0) / 100.0
            ));
        });
        rows.sort(Comparator.comparing(ActionPerformance::netValue).reversed());
        return rows;
    }
}

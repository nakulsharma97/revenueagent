package com.razorpay.recovery.intelligence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects simple, explainable anomalies in the recovery pipeline. Every detection is a
 * documented rule over the case features; HIGH/CRITICAL findings are routed to the
 * human review queue alongside low-confidence decisions.
 */
public class AnomalyDetectionService {

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    /** An anomaly finding (pure value, persisted by RecoveryIntelligenceService). */
    public record Finding(String type, Severity severity, String description) {}

    public List<Finding> detect(RecoveryCase c) {
        List<Finding> out = new ArrayList<>();
        BigDecimal amount = c.amount();

        // Unusually large failed payment (>= ₹1L or 10x the median demo ticket of ₹2.5k).
        if (amount != null && amount.compareTo(new BigDecimal("100000")) >= 0) {
            out.add(new Finding("LARGE_FAILURE", Severity.HIGH,
                    "Revenue-at-risk of ₹" + amount + " exceeds the ₹1,00,000 anomaly threshold."));
        } else if (amount != null && amount.compareTo(new BigDecimal("25000")) >= 0) {
            out.add(new Finding("LARGE_FAILURE", Severity.MEDIUM,
                    "Revenue-at-risk of ₹" + amount + " is well above the typical ticket size."));
        }

        // Repeated failures from the same customer.
        if (c.retryCount() >= 2) {
            out.add(new Finding("REPEATED_FAILURES", c.retryCount() >= 3 ? Severity.HIGH : Severity.MEDIUM,
                    c.retryCount() + " consecutive payment failures for " + c.label() + "."));
        }

        // Customer close to giving up (fatigue) on a meaningful amount.
        if (c.fatigue() >= 0.6 && amount != null && amount.doubleValue() >= 2000) {
            out.add(new Finding("FATIGUE_RISK", c.fatigue() >= 0.85 ? Severity.HIGH : Severity.MEDIUM,
                    "Customer fatigue at " + String.format("%.0f", c.fatigue() * 100)
                            + "% while ₹" + amount + " remains at risk."));
        }

        // Stale receivable that has been chased several times without success.
        if (c.sourceType().equals("RECEIVABLE") && c.daysOverdue() >= 45 && c.reminderCount() >= 2) {
            out.add(new Finding("STALE_RECEIVABLE", Severity.MEDIUM,
                    "Receivable overdue " + c.daysOverdue() + " days after " + c.reminderCount() + " chases."));
        }

        return out;
    }
}

package com.razorpay.recovery.metrics;

import com.razorpay.recovery.metrics.ActionBreakdown;
import com.razorpay.recovery.metrics.BatchMetrics;
import com.razorpay.recovery.metrics.FunnelData;
import com.razorpay.recovery.metrics.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public BatchMetrics metrics(@RequestParam(required = false, defaultValue = "full") String scope) {
        return metricsService.currentMetrics("held-out".equals(scope));
    }

    /** Combined dashboard payload — single round-trip for initial page load. */
    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return metricsService.dashboardSummary();
    }

    @GetMapping("/funnel")
    public FunnelData funnel() {
        return metricsService.funnelData();
    }

    @GetMapping("/actions")
    public List<ActionBreakdown> actions() {
        return metricsService.actionBreakdown();
    }

    /** Per-batch metrics history — shows how each run performed. */
    @GetMapping("/batches")
    public List<Map<String, Object>> batches() {
        return metricsService.batchHistory();
    }

    /** Per-action ROI: recovered per rupee spent on interventions. */
    @GetMapping("/efficiency")
    public List<ActionEfficiency> efficiency() {
        return metricsService.actionEfficiency();
    }

    /**
     * What-if simulator: projects net-recovered impact of bounds changes
     * against the current batch's already-recorded outcomes.
     * Pure recalculation — no LLM calls, no mock gateway execution.
     */
    @GetMapping("/simulate")
    public SimulationResult simulate(
            @RequestParam(defaultValue = "3") int maxRetries,
            @RequestParam(defaultValue = "15") int maxDiscountPercent,
            @RequestParam(defaultValue = "500") java.math.BigDecimal minAmountForDiscount,
            @RequestParam(defaultValue = "60") int retryCooldownMinutes) {
        return metricsService.simulate(maxRetries, maxDiscountPercent, minAmountForDiscount, retryCooldownMinutes);
    }
}

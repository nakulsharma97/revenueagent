package com.razorpay.recovery.metrics;

import com.razorpay.recovery.metrics.ActionBreakdown;
import com.razorpay.recovery.metrics.BatchMetrics;
import com.razorpay.recovery.metrics.FunnelData;
import com.razorpay.recovery.metrics.MetricsService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public BatchMetrics metrics() {
        return metricsService.currentMetrics();
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
}

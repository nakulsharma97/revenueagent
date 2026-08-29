package com.razorpay.recovery.controller;

import com.razorpay.recovery.dto.ActionBreakdown;
import com.razorpay.recovery.dto.BatchMetrics;
import com.razorpay.recovery.dto.FunnelData;
import com.razorpay.recovery.service.MetricsService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}

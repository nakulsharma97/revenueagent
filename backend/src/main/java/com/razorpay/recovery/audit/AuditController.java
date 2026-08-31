package com.razorpay.recovery.audit;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for the audit trail.
 * GET /api/audit — recent audit events
 * GET /api/audit/entity/{type}/{id} — history for a specific entity
 * GET /api/audit/batch/{batchId} — history for a batch run
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEvent> recentEvents() {
        return auditService.getRecentEvents();
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public List<AuditEvent> entityHistory(@PathVariable String entityType, @PathVariable String entityId) {
        return auditService.getEntityHistory(entityType, entityId);
    }

    @GetMapping("/batch/{batchId}")
    public List<AuditEvent> batchHistory(@PathVariable String batchId) {
        return auditService.getBatchHistory(batchId);
    }
}

package com.razorpay.recovery.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Centralized audit service that records every important operation.
 * Audit logs are immutable — once written, they cannot be modified or deleted.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Record an audit event. Never throws — audit failures are logged but don't break the flow.
     */
    public AuditEvent record(AuditEvent event) {
        try {
            AuditEvent saved = auditEventRepository.save(event);
            log.debug("Audit: {} {} {} -> {}", event.getActor(), event.getEventType(), 
                     event.getEntityType(), event.getEntityId());
            return saved;
        } catch (Exception e) {
            log.error("Failed to record audit event: {} - {}", event.getEventType(), e.getMessage());
            return event;
        }
    }

    /**
     * Convenience method to record with state change.
     */
    public AuditEvent record(AuditEvent.Actor actor, AuditEvent.EventType eventType,
                              String entityType, String entityId,
                              String previousState, String newState, String reason) {
        AuditEvent event = AuditEvent.of(actor, eventType, entityType, entityId, reason)
                .withStateChange(previousState, newState);
        return record(event);
    }

    /**
     * Convenience method for batch events.
     */
    public AuditEvent recordForBatch(String batchId, AuditEvent.Actor actor, 
                                      AuditEvent.EventType eventType,
                                      String entityType, String entityId, String reason) {
        AuditEvent event = AuditEvent.of(actor, eventType, entityType, entityId, reason)
                .withBatchId(batchId);
        return record(event);
    }

    /**
     * Get all audit events for a specific entity.
     */
    public List<AuditEvent> getEntityHistory(String entityType, String entityId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    /**
     * Get all audit events for a batch.
     */
    public List<AuditEvent> getBatchHistory(String batchId) {
        return auditEventRepository.findByBatchIdOrderByTimestampAsc(batchId);
    }

    /**
     * Get all recent audit events.
     */
    public List<AuditEvent> getRecentEvents() {
        return auditEventRepository.findRecentEvents();
    }
}

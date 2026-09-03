package com.razorpay.recovery.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Immutable audit record for every important operation in the system.
 * Actors: SYSTEM, AI_AGENT, POLICY_ENGINE, HUMAN_USER
 * 
 * Every event captures: timestamp, actor, event type, entity type, entity id,
 * previous state, new state, reason, and metadata.
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entityType, entityId"),
    @Index(name = "idx_audit_actor", columnList = "actor"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Who performed the action: SYSTEM, AI_AGENT, POLICY_ENGINE, HUMAN_USER */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Actor actor;

    /** What happened: e.g., RECOVERY_ATTEMPT_CREATED, ACTION_EXECUTED, SIGNOFF_APPROVED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private EventType eventType;

    /** Entity type affected: e.g., RECOVERY_ATTEMPT, TRANSACTION, CHECKOUT_SESSION */
    @Column(length = 48)
    private String entityType;

    /** Entity ID affected */
    @Column(length = 64)
    private String entityId;

    /** Previous state before the change */
    @Column(length = 32)
    private String previousState;

    /** New state after the change */
    @Column(length = 32)
    private String newState;

    /** Human-readable reason for the change */
    @Column(length = 1000)
    private String reason;

    /** Additional context as JSON string */
    @Column(length = 2000)
    private String metadata;

    private LocalDateTime timestamp;

    /** Batch ID for grouping related audit events */
    @Column(length = 36)
    private String batchId;

    public enum Actor {
        SYSTEM, AI_AGENT, POLICY_ENGINE, HUMAN_USER
    }

    public enum EventType {
        // Recovery lifecycle
        RECOVERY_ATTEMPT_CREATED,
        RECOVERY_ATTEMPT_EXECUTED,
        RECOVERY_ATTEMPT_SUCCEEDED,
        RECOVERY_ATTEMPT_FAILED,
        RECOVERY_ATTEMPT_SKIPPED,
        RECOVERY_ATTEMPT_IDEMPOTENT_SKIP,

        // Decision pipeline
        AI_RECOMMENDATION_RECEIVED,
        AI_RECOMMENDATION_VALIDATED,
        AI_RECOMMENDATION_REJECTED,
        POLICY_CHECK_PASSED,
        POLICY_CHECK_FAILED,
        POLICY_BOUNDS_ENFORCED,

        // Approval workflow
        APPROVAL_REQUIRED,
        SIGNOFF_APPROVED,
        SIGNOFF_REJECTED,
        SIGNOFF_PENDING,

        // Configuration
        CONFIG_UPDATED,
        BOUNDS_CHANGED,

        // System events
        BATCH_STARTED,
        BATCH_COMPLETED,
        COOLDOWN_ACTIVE,

        // Entity lifecycle
        TRANSACTION_STATUS_CHANGED,
        CHECKOUT_STATUS_CHANGED,
        RECEIVABLE_STATUS_CHANGED,

        // Demo mode
        SIMULATION_EVENT_INJECTED,
        DUPLICATE_EVENT_DETECTED
    }

    /** Factory method for creating audit events */
    public static AuditEvent of(Actor actor, EventType eventType, String entityType, 
                                 String entityId, String reason) {
        AuditEvent event = new AuditEvent();
        event.setActor(actor);
        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setReason(reason);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    public AuditEvent withStateChange(String from, String to) {
        this.previousState = from;
        this.newState = to;
        return this;
    }

    public AuditEvent withMetadata(String metadata) {
        this.metadata = metadata;
        return this;
    }

    public AuditEvent withBatchId(String batchId) {
        this.batchId = batchId;
        return this;
    }
}

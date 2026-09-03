package com.razorpay.recovery.intelligence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A persisted anomaly finding (large failure, repeated failures, fatigue risk,
 * stale receivable). HIGH/CRITICAL findings automatically route a case to the
 * human review queue.
 */
@Entity
@Table(name = "recovery_anomalies", indexes = {
        @Index(name = "idx_anomaly_status", columnList = "status"),
        @Index(name = "idx_anomaly_source", columnList = "sourceType, sourceEntityId")
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RecoveryAnomaly {

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 48)
    private String type;

    @Column(length = 20)
    private String sourceType;

    private Long sourceEntityId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Severity severity;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    private LocalDateTime createdAt;

    public static RecoveryAnomaly from(AnomalyDetectionService.Finding f, String sourceType, Long entityId) {
        RecoveryAnomaly a = new RecoveryAnomaly();
        a.setType(f.type());
        a.setSourceType(sourceType);
        a.setSourceEntityId(entityId);
        a.setSeverity(switch (f.severity()) {
            case LOW -> Severity.LOW;
            case MEDIUM -> Severity.MEDIUM;
            case HIGH -> Severity.HIGH;
            case CRITICAL -> Severity.CRITICAL;
        });
        a.setDescription(f.description());
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }
}

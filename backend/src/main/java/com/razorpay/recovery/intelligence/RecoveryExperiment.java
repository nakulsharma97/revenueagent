package com.razorpay.recovery.intelligence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A declared experimentation policy. Control/treatment assignment itself stays in the
 * ingestion layer (the pre-existing isControlGroup split + uplift segmentation), so
 * experimentation logic never mixes into per-entity production decisions — an
 * experiment row only documents <em>what</em> is being tested, on which segment,
 * at what control percentage, and for how long.
 */
@Entity
@Table(name = "recovery_experiments")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RecoveryExperiment {

    public enum Status { ACTIVE, PAUSED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Share of the eligible population held in the control arm (0..100). */
    private double controlPercentage = 15.0;

    /** Human description of the treatment policy, e.g. "SEND_PAYMENT_LINK vs SEND_REMINDER". */
    @Column(length = 500)
    private String treatmentPolicy;

    /** Target segment, e.g. "PAYMENT", "CHECKOUT", "RECEIVABLE" or "ALL". */
    @Column(length = 40)
    private String targetSegment = "ALL";

    /** Segment bound to the experiment, e.g. "STANDARD"/"HIGH_VALUE"/"ALL". */
    @Column(length = 40)
    private String targetCustomerSegment = "ALL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    private LocalDate startDate;
    private LocalDate endDate;

    private LocalDateTime createdAt;
}

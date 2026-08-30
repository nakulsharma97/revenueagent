package com.razorpay.recovery.receivable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "receivables")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Receivable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String businessCustomerId;
    private String businessName;
    private String contactEmail;

    private BigDecimal invoiceAmount;
    private String invoiceNumber;

    private LocalDate dueDate;
    private int daysOverdue;

    @Enumerated(EnumType.STRING)
    private ReceivableStatus status;

    private int reminderCount = 0;
    private int paymentPlanInstallments = 0;

    // ── Promise-to-pay tracking ──

    /** Date the customer promised to pay by. Null if no promise has been made. */
    private LocalDate promisedPaymentDate;

    /** Status of the promise-to-pay: NONE (no promise), PROMISED (pending), KEPT (paid by date), BROKEN (missed date). */
    @Enumerated(EnumType.STRING)
    private PromiseStatus promiseStatus = PromiseStatus.NONE;

    public enum ReceivableStatus {
        DUE, OVERDUE, RECOVERED, WRITTEN_OFF
    }

    public enum PromiseStatus {
        NONE, PROMISED, KEPT, BROKEN
    }
}

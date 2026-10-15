package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.OffCyclePaymentMode;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.OffCyclePaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Payments outside the regular monthly run. HR-recorded rows have null
 * source_event_id; bonus-installment apply rows back-link to the event
 * and installment that produced them.
 *
 * <p>Lifecycle (V0056): HR creation is also HR authorization, so a row is
 * born {@link OffCyclePaymentStatus#HR_APPROVED}. HR may edit or cancel it
 * while it stays there; Accounts pays it. {@code PAID} and {@code
 * CANCELLED} are terminal and immutable — that rule keys on
 * {@link #status}, never on {@link #sourceEventId}.
 */
@Entity
@Table(name = "off_cycle_payments")
@Getter
@Setter
public class OffCyclePayment extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Internal transaction classification, stored in the legacy
     * {@code category} column. NOT the HR-facing type — that is
     * {@link #offCyclePaymentTypeId}. An HR-created row is always
     * {@code HR}, whatever its selected type happens to be called.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private OffCyclePaymentSource source;

    /**
     * Selected {@code employee.off_cycle_payment_type} row. Logical
     * cross-service reference — no FK, separate schema. Null on
     * system-generated rows, and on rows created before V0057 (HR never
     * made a selection for those).
     */
    @Column(name = "off_cycle_payment_type_id")
    private Long offCyclePaymentTypeId;

    /**
     * The master's {@code name} as HR saw it at the moment of recording.
     * Kept so a later rename or deactivation of the master leaves history
     * readable. For pre-V0057 rows this holds the original free-text
     * category string.
     */
    @Column(name = "off_cycle_payment_type_name", length = 100)
    private String offCyclePaymentTypeName;

    /** Nullable since V0024 dropped the NOT NULL — the JPA side said
     *  otherwise until now, which turned an omitted reason into a
     *  Hibernate PropertyValueException (500) instead of a saved row. */
    @Column(name = "reason")
    private String reason;

    /** Also nullable since V0024. HR may state the intended mode at
     *  creation; Accounts confirms the actual one at /pay. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 16)
    private OffCyclePaymentMode paymentMode;

    @Column(name = "reference_no", length = 64)
    private String referenceNo;

    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "source_installment_id")
    private Long sourceInstallmentId;

    // ========================================================
    // Lifecycle
    // ========================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OffCyclePaymentStatus status = OffCyclePaymentStatus.HR_APPROVED;

    /** User id of the HR user whose creation authorized this payment.
     *  Null on rows backfilled by V0056 — the old schema recorded no
     *  approval actor and V0056 does not invent one. */
    @Column(name = "hr_approved_by")
    private Long hrApprovedBy;

    @Column(name = "hr_approved_at")
    private LocalDateTime hrApprovedAt;

    /** User id of the Accounts user who executed the payment. Null until
     *  paid, and on V0056-backfilled rows. */
    @Column(name = "paid_by")
    private Long paidBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** HR can edit an approved payment while Accounts is preparing to pay
     *  it, so the two writers race on the same row. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

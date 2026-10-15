package com.forward.hrms.employee.entity.travel;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.travel.TravelAdvancePaymentMethodMode;
import com.forward.hrms.employee.enums.travel.TravelAdvanceStatus;
import com.forward.hrms.employee.enums.travel.TravelAdvanceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents an advance requested, approved, paid and eventually
 * settled against a Travel Request.
 *
 * <p>
 * A Travel Request may have multiple advances. Each advance is
 * associated with a specific participant who is responsible for
 * the amount.
 * </p>
 *
 * <p>
 * This entity tracks the Travel domain's business state of the
 * advance. Actual payment/accounting processing remains the
 * responsibility of the Payroll/Finance integration.
 * </p>
 *
 * <p>
 * Examples:
 * <ul>
 *     <li>Initial advance: PKR 30,000</li>
 *     <li>Additional advance: PKR 10,000</li>
 *     <li>Emergency advance during travel: PKR 5,000</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "travel_request_advances")
@Getter
@Setter
public class TravelRequestAdvance extends AuditedEntity {

    /**
     * Travel Request to which this advance belongs.
     *
     * <p>
     * This is a local database relationship represented by the
     * Travel Request ID.
     * </p>
     */
    @Column(name = "travel_request_id", nullable = false)
    private Long travelRequestId;

    /**
     * Participant who receives or is responsible for this advance.
     *
     * <p>
     * References travel_request_participants.id.
     * </p>
     */
    @Column(name = "travel_request_participant_id", nullable = false)
    private Long travelRequestParticipantId;

    /**
     * Human-readable unique advance number.
     *
     * <p>
     * Example:
     * ADV-2026-000125
     * </p>
     */
    @Column(name = "advance_number", nullable = false, length = 50, unique = true)
    private String advanceNumber;

    /**
     * Business type of the advance.
     *
     * <p>
     * Supported values:
     * INITIAL,
     * ADDITIONAL,
     * EMERGENCY,
     * ADJUSTMENT
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "advance_type_code", nullable = false, length = 30)
    private TravelAdvanceType advanceType;

    /**
     * Amount originally requested by the employee/requester.
     */
    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    /**
     * Currency of the requested amount.
     *
     * <p>
     * Example: PKR
     * </p>
     */
    @Column(name = "requested_currency_code", nullable = false, length = 3)
    private String requestedCurrencyCode;

    /**
     * Amount approved by the authorized approval process.
     *
     * <p>
     * May be lower than requestedAmount.
     * </p>
     */
    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    /**
     * Currency in which the advance was approved.
     */
    @Column(name = "approved_currency_code", length = 3)
    private String approvedCurrencyCode;

    /**
     * Amount actually released/paid to the employee.
     *
     * <p>
     * This value should normally be synchronized from the
     * Payroll/Finance payment process.
     * </p>
     */
    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;

    /**
     * Currency in which the payment was actually made.
     */
    @Column(name = "paid_currency_code", length = 3)
    private String paidCurrencyCode;

    /**
     * Current lifecycle state of the advance.
     *
     * <p>
     * Supported values:
     * DRAFT,
     * SUBMITTED,
     * UNDER_REVIEW,
     * APPROVED,
     * PARTIALLY_APPROVED,
     * REJECTED,
     * PAYMENT_PENDING,
     * PAID,
     * CANCELED,
     * SETTLED
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TravelAdvanceStatus status = TravelAdvanceStatus.DRAFT;

    /**
     * Business justification for requesting the advance.
     *
     * <p>
     * Example:
     * "Advance required for hotel, meals and local transport
     * during Lahore factory visit."
     * </p>
     */
    @Column(name = "purpose", nullable = false, columnDefinition = "TEXT")
    private String purpose;

    /**
     * Optional additional remarks supplied by the requester.
     */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * Reference to the existing Approval Module.
     *
     * <p>
     * This is intentionally stored as an ID/reference instead of
     * a JPA relationship because the Approval Module is a separate
     * application/module boundary.
     * </p>
     */
    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    /**
     * Timestamp at which the advance was approved.
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * User/employee ID who approved the advance.
     */
    @Column(name = "approved_by")
    private Long approvedBy;

    /**
     * Payment reference returned by Payroll/Finance.
     *
     * <p>
     * Example:
     * PAY-2026-008921
     * </p>
     */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /**
     * External payment/finance transaction identifier.
     */
    @Column(name = "external_payment_id", length = 150)
    private String externalPaymentId;

    /**
     * Timestamp when the advance payment was actually released.
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * Portion of the paid advance consumed/settled against
     * eligible travel expenses.
     *
     * <p>
     * Defaults to zero at database level.
     * </p>
     */
    @Column(
            name = "settled_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal settledAmount = BigDecimal.ZERO;

    /**
     * Amount returned by the employee to the company.
     *
     * <p>
     * Example:
     * <p>
     * Advance paid = PKR 35,000
     * Eligible expenses = PKR 31,000
     * Returned = PKR 4,000
     * </p>
     */
    @Column(
            name = "returned_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal returnedAmount = BigDecimal.ZERO;

    /**
     * Currency used for settlement/reconciliation.
     */
    @Column(name = "settlement_currency_code", nullable = false, length = 3)
    private String settlementCurrencyCode;

    /**
     * Payment method used by Payroll/Finance.
     *
     * <p>
     * Examples:
     * PAYROLL,
     * BANK_TRANSFER,
     * CASH,
     * OTHER
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_code", length = 30)
    private TravelAdvancePaymentMethodMode paymentMethod;

    /**
     * Reference to the employee's payment/bank account in the
     * external financial system.
     *
     * <p>
     * Sensitive banking details must NOT be stored here.
     * </p>
     */
    @Column(name = "payment_account_reference", length = 150)
    private String paymentAccountReference;

    /**
     * Indicates whether the payment has been reconciled with
     * the external Payroll/Finance system.
     */
    @Column(name = "is_payment_reconciled", nullable = false)
    private boolean paymentReconciled = false;

    /**
     * Timestamp at which payment reconciliation occurred.
     */
    @Column(name = "payment_reconciled_at")
    private LocalDateTime paymentReconciledAt;

    /**
     * User/system actor who performed the payment reconciliation.
     */
    @Column(name = "payment_reconciled_by")
    private Long paymentReconciledBy;

    /**
     * JPA/Hibernate optimistic locking version.
     *
     * <p>
     * Important:
     * <p>
     * This is NOT the same as the business advance number or
     * travel policy version number.
     * <p>
     * It protects the advance row from concurrent modifications.
     * </p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

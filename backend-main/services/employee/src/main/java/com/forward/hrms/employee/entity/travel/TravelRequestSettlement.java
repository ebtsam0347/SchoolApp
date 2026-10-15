package com.forward.hrms.employee.entity.travel;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.travel.TravelSettlementDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementStatus;
import com.forward.hrms.employee.enums.travel.TravelSettlementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Settlement header for a Travel Request participant. Reconciles
 * eligible/approved expenses against advances and adjustments into a
 * final payable/receivable balance.
 * <p>
 * Audit columns (created_at/by, updated_at/by, ip_address) come from
 * {@link AuditedEntity} and are populated by its entity listener — never
 * set them by hand.
 */
@Entity
@Table(name = "travel_request_settlements")
@Getter
@Setter
public class TravelRequestSettlement extends AuditedEntity {

    // ========================================================
    // Travel Request relationship
    // ========================================================

    /**
     * Travel Request being settled. Stored as a plain {@code Long} FK
     * (project convention); the DB foreign key
     * {@code fk_travel_settlements_request} lives in the migration.
     */
    @Column(name = "travel_request_id", nullable = false)
    private Long travelRequestId;

    /**
     * Participant whose financial responsibility is being settled.
     * Stored as a plain {@code Long} FK (project convention); the DB
     * foreign key {@code fk_travel_settlements_participant} lives in the
     * migration.
     */
    @Column(name = "travel_request_participant_id", nullable = false)
    private Long travelRequestParticipantId;

    // ========================================================
    // Settlement identification
    // ========================================================

    /**
     * Human-readable settlement reference, e.g. SET-2026-000125.
     */
    @Column(name = "settlement_number", nullable = false, length = 50, unique = true)
    private String settlementNumber;

    // ========================================================
    // Settlement type
    // ========================================================

    /**
     * FINAL / INTERIM / ADJUSTMENT.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type_code", nullable = false, length = 30)
    private TravelSettlementType settlementType = TravelSettlementType.FINAL;

    // ========================================================
    // Settlement lifecycle
    // ========================================================

    /**
     * DRAFT, SUBMITTED, HR_APPROVED, PAID, REJECTED, CANCELLED — the live
     * 2-stage HR-approve / Accounts-pay flow. UNDER_REVIEW, APPROVED,
     * PARTIALLY_APPROVED, PAYMENT_PENDING, RECOVERY_PENDING, RECOVERED,
     * SETTLED remain valid values but are unused going forward.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TravelSettlementStatus status = TravelSettlementStatus.DRAFT;

    // ========================================================
    // Expense totals
    // ========================================================

    /**
     * Total amount claimed through expense records included in this
     * settlement.
     */
    @Column(name = "total_claimed_expenses", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalClaimedExpenses = BigDecimal.ZERO;

    /**
     * Total amount eligible according to Travel Policy.
     */
    @Column(name = "total_eligible_expenses", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalEligibleExpenses = BigDecimal.ZERO;

    /**
     * Total amount finally approved by the reviewer.
     */
    @Column(name = "total_approved_expenses", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalApprovedExpenses = BigDecimal.ZERO;

    // ========================================================
    // Advance totals
    // ========================================================

    /**
     * Total amount actually paid to the employee as travel advance and
     * included in this settlement.
     */
    @Column(name = "total_advances_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAdvancesPaid = BigDecimal.ZERO;

    // ========================================================
    // Adjustment totals
    // ========================================================

    /**
     * Positive/negative business adjustments applied during settlement.
     */
    @Column(name = "total_adjustments", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAdjustments = BigDecimal.ZERO;

    // ========================================================
    // Final settlement
    // ========================================================

    /**
     * Final amount after reconciliation. The direction is represented by
     * {@link #settlementDirection}.
     */
    @Column(name = "final_settlement_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalSettlementAmount = BigDecimal.ZERO;

    /**
     * COMPANY_PAYABLE / EMPLOYEE_RECEIVABLE / NO_BALANCE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_direction", nullable = false, length = 30)
    private TravelSettlementDirection settlementDirection = TravelSettlementDirection.NO_BALANCE;

    // ========================================================
    // Currency
    // ========================================================

    /**
     * ISO 4217 currency code, e.g. PKR.
     */
    @Column(name = "settlement_currency_code", nullable = false, length = 3)
    private String settlementCurrencyCode;

    // ========================================================
    // Explanation
    // ========================================================

    /**
     * Human-readable explanation of the final settlement.
     */
    @Column(name = "settlement_summary", columnDefinition = "TEXT")
    private String settlementSummary;

    // ========================================================
    // Approval / review
    // ========================================================

    /**
     * Approval workflow request from the existing Approval Module.
     */
    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    /**
     * Timestamp when the settlement was approved.
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * User/employee who approved the settlement.
     */
    @Column(name = "approved_by")
    private Long approvedBy;

    // ========================================================
    // Payment / recovery
    // ========================================================

    /**
     * Reference of the final company payment OR employee recovery
     * transaction.
     */
    @Column(name = "settlement_payment_reference", length = 100)
    private String settlementPaymentReference;

    /**
     * External transaction identifier from Payroll/Finance.
     */
    @Column(name = "external_transaction_id", length = 150)
    private String externalTransactionId;

    /**
     * Date on which final settlement payment/recovery happened.
     */
    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    // ========================================================
    // Finalization
    // ========================================================

    /**
     * Indicates that this settlement has been finalized and should no
     * longer be modified as a normal draft record.
     */
    @Column(name = "is_finalized", nullable = false)
    private Boolean isFinalized = false;

    /**
     * Timestamp when the settlement was finalized.
     */
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    /**
     * User/system actor who finalized the settlement.
     */
    @Column(name = "finalized_by")
    private Long finalizedBy;

    // ========================================================
    // Remarks
    // ========================================================

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    // ========================================================
    // Optimistic locking
    // ========================================================

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

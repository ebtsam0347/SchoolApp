package com.forward.hrms.employee.entity.travel;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.travel.TravelExpenseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents one actual expense/claim incurred during a
 * Travel Request.
 * <p>
 * This entity stores:
 * <p>
 * "What did the employee actually spend?"
 * <p>
 * It is intentionally separate from TravelRequestEntitlement,
 * which stores:
 * <p>
 * "What was the employee allowed to claim?"
 * <p>
 * Example:
 * <p>
 * Travel Request:
 * TR-2026-000125
 * <p>
 * Participant:
 * Farhan Ali
 * <p>
 * Expense items:
 * <p>
 * HOTEL       = PKR 9,000
 * BREAKFAST   = PKR   700
 * LUNCH       = PKR 1,100
 * DINNER      = PKR 1,400
 * TOLL        = PKR   500
 * <p>
 * The final eligible/reimbursable amount is determined by the
 * Travel business logic and policy/entitlement rules.
 */
@Entity
@Table(name = "travel_expense_items")
@Getter
@Setter
public class TravelExpenseItem extends AuditedEntity {

    /**
     * Travel Request under which this expense was incurred.
     * <p>
     * Local database relationship:
     * <p>
     * travel_expense_items.travel_request_id
     * ->
     * travel_requests.id
     */
    @Column(name = "travel_request_id", nullable = false)
    private Long travelRequestId;

    /**
     * Participant who incurred/claimed this expense.
     * <p>
     * Local database relationship:
     * <p>
     * travel_expense_items.travel_request_participant_id
     * ->
     * travel_request_participants.id
     */
    @Column(name = "travel_request_participant_id", nullable = false)
    private Long travelRequestParticipantId;

    /**
     * Entitlement consumed/evaluated by this expense.
     * <p>
     * Nullable because some legitimate expenses may not be
     * governed by a specific entitlement.
     * <p>
     * Examples:
     * <p>
     * DINNER expense
     * -> DINNER entitlement
     * <p>
     * HOTEL expense
     * -> ACCOMMODATION entitlement
     * <p>
     * TOLL expense
     * -> may have no specific entitlement
     */
    @Column(name = "travel_request_entitlement_id")
    private Long travelRequestEntitlementId;

    /**
     * High-level business category of the expense.
     * <p>
     * Examples:
     * <p>
     * ACCOMMODATION
     * MEAL
     * TRANSPORTATION
     * TOLL
     * PARKING
     * OTHER
     */
    @Column(
            name = "expense_category_code",
            nullable = false,
            length = 50
    )
    private String expenseCategoryCode;

    /**
     * More specific expense classification.
     * <p>
     * Examples:
     * <p>
     * HOTEL
     * BREAKFAST
     * LUNCH
     * DINNER
     * TAXI
     * FUEL
     * TOLL
     * PARKING
     * TRAIN_TICKET
     * AIR_TICKET
     * OTHER
     */
    @Column(name = "expense_subcategory_code", length = 50)
    private String expenseSubcategoryCode;

    /**
     * Human-readable description of the expense.
     * <p>
     * Example:
     * <p>
     * "Dinner at Lahore during factory visit"
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Date/time when the actual expense occurred.
     */
    @Column(name = "expense_at", nullable = false)
    private LocalDateTime expenseAt;

    /**
     * Actual amount claimed/incurred by the employee.
     * <p>
     * The backend must validate this against applicable policy
     * and entitlement rules.
     */
    @Column(
            name = "claimed_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal claimedAmount;

    /**
     * Currency in which the expense was actually incurred.
     * <p>
     * Example:
     * <p>
     * PKR
     * USD
     * AED
     */
    @Column(
            name = "claimed_currency_code",
            nullable = false,
            length = 3
    )
    private String claimedCurrencyCode;

    /**
     * Maximum amount considered eligible for reimbursement.
     * <p>
     * Example:
     * <p>
     * Claimed amount = PKR 1,800
     * Allowed amount = PKR 1,500
     * <p>
     * eligibleAmount = PKR 1,500
     */
    @Column(
            name = "eligible_amount",
            precision = 15,
            scale = 2
    )
    private BigDecimal eligibleAmount;

    /**
     * Portion of the claim that is not reimbursable.
     * <p>
     * Example:
     * <p>
     * Claimed      = 1,800
     * Eligible     = 1,500
     * Non-eligible =   300
     */
    @Column(
            name = "non_reimbursable_amount",
            precision = 15,
            scale = 2
    )
    private BigDecimal nonReimbursableAmount;

    /**
     * Final amount approved by the authorized reviewer.
     * <p>
     * This can differ from eligibleAmount when a reviewer
     * partially approves or rejects an otherwise eligible claim.
     */
    @Column(
            name = "approved_amount",
            precision = 15,
            scale = 2
    )
    private BigDecimal approvedAmount;

    /**
     * Currency in which the expense will be settled/reimbursed.
     * <p>
     * Normally this is the same as claimedCurrencyCode.
     * <p>
     * It may differ when foreign currency conversion is supported.
     */
    @Column(
            name = "settlement_currency_code",
            nullable = false,
            length = 3
    )
    private String settlementCurrencyCode;

    /**
     * Exchange rate used when claimed currency and settlement
     * currency differ.
     * <p>
     * Example:
     * <p>
     * USD -> PKR
     * Exchange rate = 280
     */
    @Column(
            name = "exchange_rate",
            precision = 19,
            scale = 8
    )
    private BigDecimal exchangeRate;

    /**
     * Claimed amount converted into the settlement currency.
     */
    @Column(
            name = "converted_claimed_amount",
            precision = 15,
            scale = 2
    )
    private BigDecimal convertedClaimedAmount;

    /**
     * Indicates whether supporting receipt/evidence is required
     * for this expense.
     */
    @Column(name = "receipt_required", nullable = false)
    private Boolean receiptRequired = true;

    /**
     * Indicates whether the required receipt/evidence has been
     * supplied.
     */
    @Column(name = "receipt_attached", nullable = false)
    private Boolean receiptAttached = false;

    /**
     * External document/file identifier.
     * <p>
     * The actual file is stored in the company's document/file
     * storage system rather than inside this database table.
     */
    @Column(name = "receipt_document_id", length = 200)
    private String receiptDocumentId;

    /**
     * Receipt/invoice/reference number appearing on the
     * supporting document.
     */
    @Column(name = "receipt_reference", length = 100)
    private String receiptReference;

    /**
     * Current processing state of this expense item.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TravelExpenseStatus status = TravelExpenseStatus.DRAFT;

    /**
     * Reason supplied when the expense is rejected.
     */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * User/employee ID of the person who reviewed the expense.
     * <p>
     * This is intentionally an ID rather than a JPA relationship.
     */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    /**
     * Timestamp when the expense was reviewed.
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Timestamp when the expense was included in a payment or
     * final settlement.
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * External payroll/finance payment reference.
     * <p>
     * Example:
     * <p>
     * PAY-2026-004821
     */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /**
     * Additional employee comments or notes.
     */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * JPA/Hibernate optimistic-locking version.
     * <p>
     * Prevents concurrent users/processes from silently
     * overwriting each other's changes.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

package com.forward.hrms.employee.entity.travel;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores the actual entitlement snapshot applicable to a
 * participant for a specific Travel Request.
 * <p>
 * This entity represents:
 * <p>
 * "What is this employee entitled to?"
 * <p>
 * It does NOT represent:
 * <p>
 * "What did this employee actually spend?"
 * <p>
 * Actual expenses/claims are handled by the corresponding
 * Travel Expense entities.
 * <p>
 * The entitlement is generated from the applicable travel policy
 * and then retained as a snapshot so future policy changes do not
 * alter historical Travel Requests.
 * <p>
 * Example:
 * <p>
 * Policy Version 2:
 * E-4 / DINNER = PKR 1,500
 * <p>
 * Travel Request:
 * TR-2026-000125
 * <p>
 * Participant:
 * Farhan / E-4
 * <p>
 * Entitlement:
 * DINNER
 * Quantity = 3
 * Unit Limit = PKR 1,500
 * Total Limit = PKR 4,500
 * <p>
 * If Policy Version 3 later changes DINNER to PKR 2,000,
 * this entity remains unchanged.
 */
@Entity
@Table(name = "travel_request_entitlements")
@Getter
@Setter
public class TravelRequestEntitlement extends AuditedEntity {

    /**
     * Travel Request to which this entitlement belongs.
     * <p>
     * Stored as a plain {@code Long} FK (project convention); the DB
     * foreign key {@code fk_travel_request_entitlements_request} lives
     * in the migration.
     */
    @Column(name = "travel_request_id", nullable = false)
    private Long travelRequestId;

    /**
     * Specific participant receiving this entitlement.
     * <p>
     * Stored as a plain {@code Long} FK (project convention); the DB
     * foreign key {@code fk_travel_request_entitlements_participant}
     * lives in the migration.
     */
    @Column(name = "travel_request_participant_id", nullable = false)
    private Long travelRequestParticipantId;

    /**
     * Exact Travel Policy Version from which this entitlement
     * was derived.
     * <p>
     * Retained at the entitlement level even though TravelRequest also
     * references its policy version, so the entitlement is independently
     * traceable. Stored as a plain {@code Long} FK (project convention);
     * the DB foreign key {@code fk_travel_request_entitlements_policy}
     * lives in the migration.
     */
    @Column(name = "policy_version_id", nullable = false)
    private Long policyVersionId;

    /**
     * Exact policy-grade-limit rule from which this entitlement
     * was calculated/copied.
     * <p>
     * Traceability: Policy Version -> Grade Limit -> Request Entitlement.
     * Stored as a plain {@code Long} FK (project convention); the DB
     * foreign key {@code fk_travel_request_entitlements_policy_limit}
     * lives in the migration.
     */
    @Column(name = "policy_grade_limit_id", nullable = false)
    private Long policyGradeLimitId;

    /**
     * Employee identifier from the Employee domain.
     * <p>
     * This is intentionally NOT a JPA relationship because the
     * Employee entity is the owner of employee master data.
     * <p>
     * Keeping the employee ID here also makes reporting easier
     * without navigating through participant records.
     */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /**
     * Grade identifier captured when this entitlement was created.
     * <p>
     * This is a snapshot and is intentionally not a relationship
     * to the current Grade master record.
     */
    @Column(name = "grade_id_snapshot")
    private Long gradeIdSnapshot;

    /**
     * Grade business code captured at entitlement creation.
     * <p>
     * Example:
     * <p>
     * E-4
     */
    @Column(name = "grade_code_snapshot", length = 50)
    private String gradeCodeSnapshot;

    /**
     * Grade display name captured at entitlement creation.
     * <p>
     * Example:
     * <p>
     * Manager
     */
    @Column(name = "grade_name_snapshot", length = 150)
    private String gradeNameSnapshot;

    /**
     * Business code identifying the entitlement category.
     * <p>
     * Examples:
     * <p>
     * ACCOMMODATION
     * BREAKFAST
     * LUNCH
     * DINNER
     * OTHER
     * <p>
     * A code-based design allows new entitlement types to be
     * introduced without adding new database columns.
     */
    @Column(
            name = "entitlement_type_code",
            nullable = false,
            length = 50
    )
    private String entitlementTypeCode;

    /**
     * Number of units allowed by this entitlement.
     * <p>
     * Examples:
     * <p>
     * Accommodation = 2 nights
     * Breakfast     = 3 meals
     * Lunch         = 3 meals
     * Dinner        = 3 meals
     */
    @Column(
            name = "quantity_allowed",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityAllowed = BigDecimal.ZERO;

    /**
     * Maximum amount allowed for one unit.
     * <p>
     * Examples:
     * <p>
     * Dinner:
     * PKR 1,500 per meal
     * <p>
     * Accommodation:
     * PKR 10,000 per night
     */
    @Column(
            name = "unit_limit_amount",
            precision = 15,
            scale = 2
    )
    private BigDecimal unitLimitAmount;

    /**
     * Maximum total amount allowed for this entitlement.
     * <p>
     * Normally calculated as:
     * <p>
     * quantityAllowed × unitLimitAmount
     * <p>
     * Example:
     * <p>
     * 3 dinners × PKR 1,500
     * = PKR 4,500
     * <p>
     * This value should be calculated by the backend.
     */
    @Column(
            name = "total_limit_amount",
            precision = 15,
            scale = 2
    )
    private BigDecimal totalLimitAmount;

    /**
     * Currency used for this entitlement.
     * <p>
     * Example:
     * <p>
     * PKR
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * Explains how the entitlement was calculated.
     * <p>
     * Examples:
     * <p>
     * POLICY
     * POLICY_PER_DAY
     * POLICY_PER_MEAL
     * POLICY_PER_NIGHT
     * MANUAL_ADJUSTMENT
     */
    @Column(
            name = "calculation_basis_code",
            nullable = false,
            length = 50
    )
    private String calculationBasisCode;

    /**
     * Number of travel days used during entitlement calculation.
     * <p>
     * Example:
     * <p>
     * Three-day factory tour:
     * travelDays = 3
     * <p>
     * NULL for entitlement types that do not depend on duration.
     */
    @Column(
            name = "travel_days",
            precision = 12,
            scale = 3
    )
    private BigDecimal travelDays;

    /**
     * Original policy amount copied when this entitlement was
     * generated.
     * <p>
     * This gives an additional historical audit value.
     * <p>
     * Example:
     * <p>
     * Policy Grade Limit:
     * Dinner = PKR 1,500
     * <p>
     * policyUnitLimitAmount:
     * 1500.00
     */
    @Column(
            name = "policy_unit_limit_amount",
            precision = 15,
            scale = 2
    )
    private BigDecimal policyUnitLimitAmount;

    /**
     * Indicates whether this entitlement is the currently active
     * entitlement for the participant.
     * <p>
     * Normally TRUE.
     * <p>
     * When an entitlement is legitimately recalculated,
     * the previous row can be retained and marked inactive
     * instead of physically deleting historical information.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    /**
     * Timestamp from which this entitlement became applicable.
     */
    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    /**
     * Timestamp when this entitlement was superseded.
     * <p>
     * NULL means it has not been superseded.
     */
    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    /**
     * Explanation for why this entitlement was superseded.
     * <p>
     * Example:
     * <p>
     * "Travel dates changed from 3 days to 5 days."
     */
    @Column(name = "superseded_reason", columnDefinition = "TEXT")
    private String supersededReason;

    /**
     * JPA/Hibernate optimistic-locking version.
     * <p>
     * This is a technical database record version and is
     * different from the business policy version.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

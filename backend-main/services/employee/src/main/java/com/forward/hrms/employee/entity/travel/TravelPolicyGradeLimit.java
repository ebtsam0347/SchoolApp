package com.forward.hrms.employee.entity.travel;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.travel.TravelLimitType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Defines a grade-specific entitlement/expense limit for a
 * particular version of the Travel Policy.
 * <p>
 * Example:
 * <p>
 * Policy:
 * FACTORY_TRAVEL / Version 2
 * <p>
 * Grade:
 * E-4
 * <p>
 * Rules:
 * ACCOMMODATION -> 10,000 PKR / night
 * BREAKFAST     ->    800 PKR / day
 * LUNCH         ->  1,200 PKR / day
 * DINNER        ->  1,500 PKR / day
 * <p>
 * The design intentionally uses expenseTypeCode rather than
 * separate columns such as accommodationLimit, breakfastLimit,
 * lunchLimit, etc.
 * <p>
 * This allows new entitlement types to be introduced without
 * modifying the database schema.
 */
@Entity
@Table(name = "travel_policy_grade_limits")
@Getter
@Setter
public class TravelPolicyGradeLimit extends AuditedEntity {

    /**
     * Travel Policy Version to which this entitlement rule belongs.
     * <p>
     * Stored as a plain {@code Long} FK (project convention); the DB
     * foreign key {@code fk_travel_policy_grade_limits_policy_version}
     * lives in the migration. The service looks the policy version up
     * explicitly when it needs to validate or denormalize.
     */
    @Column(name = "policy_version_id", nullable = false)
    private Long policyVersionId;

    /**
     * Identifier of the employee grade.
     * <p>
     * Grade is owned by the Employee/Master Data domain, therefore
     * this is intentionally stored as an ID instead of a JPA
     * relationship.
     */
    @Column(name = "grade_id", nullable = false)
    private Long gradeId;

    /**
     * Grade code stored as a business/historical snapshot.
     * <p>
     * Example:
     * E-4
     */
    @Column(name = "grade_code", nullable = false, length = 50)
    private String gradeCode;

    /**
     * Human-readable grade name snapshot.
     * <p>
     * Example:
     * Executive Manager
     * <p>
     * This helps preserve historical policy configuration even if
     * the master grade name changes later.
     */
    @Column(name = "grade_name", length = 150)
    private String gradeName;

    /**
     * Business code of the entitlement/expense type.
     * <p>
     * Examples:
     * <p>
     * ACCOMMODATION
     * BREAKFAST
     * LUNCH
     * DINNER
     * MEAL_TOTAL
     * TA_TD
     * FUEL
     * TOLL
     */
    @Column(name = "expense_type_code", nullable = false, length = 50)
    private String expenseTypeCode;

    /**
     * Defines how the configured limit is applied.
     * <p>
     * PER_DAY
     * PER_NIGHT
     * PER_TRIP
     * PER_CLAIM
     * PER_KM
     * FIXED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 30)
    private TravelLimitType limitType;

    /**
     * Maximum amount allowed for this grade and entitlement type.
     * <p>
     * Example:
     * <p>
     * E-4
     * DINNER
     * 1500.00 PKR
     */
    @Column(
            name = "limit_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal limitAmount;

    /**
     * Currency in which the entitlement limit is defined.
     * <p>
     * Example:
     * PKR
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * Indicates whether this entitlement rule is currently usable
     * within this policy version.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Optional administrative explanation.
     */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * Technical optimistic-locking version.
     * <p>
     * This is different from TravelPolicyVersion.versionNo.
     * <p>
     * versionNo:
     * Business policy version.
     * <p>
     * version:
     * Technical record modification version.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

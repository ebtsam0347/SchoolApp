package com.forward.hrms.employee.dto.request.travel.response;

import com.forward.hrms.employee.entity.travel.TravelPolicyGradeLimit;
import com.forward.hrms.employee.entity.travel.TravelPolicyVersion;
import com.forward.hrms.employee.enums.travel.TravelLimitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO representing a grade-specific travel policy
 * entitlement/expense limit. Unlike the create request, the response
 * contains resolved information such as policy version, grade
 * identifier and grade name.
 */
public record TravelPolicyGradeLimitResponse(

        /* Database identifier of this policy-grade-limit record. */
        Long id,

        /* Identifier of the Travel Policy Version this rule belongs to. */
        Long policyVersionId,

        /* Business code of the Travel Policy, e.g. FACTORY_TRAVEL. */
        String policyCode,

        /*
         * Business version number of the Travel Policy - different from
         * the technical optimistic-locking version.
         */
        Integer policyVersionNo,

        /* Identifier of the employee grade (reference only). */
        Long gradeId,

        /* Business code identifying the employee grade, e.g. E-4. */
        String gradeCode,

        /* Human-readable name of the employee grade. */
        String gradeName,

        /*
         * Entitlement/expense type: ACCOMMODATION, BREAKFAST, LUNCH,
         * DINNER, FUEL, ...
         */
        String expenseTypeCode,

        /* Defines how the configured limit is applied. */
        TravelLimitType limitType,

        /* Maximum amount allowed by this rule. */
        BigDecimal limitAmount,

        /* Currency in which the policy limit is defined, e.g. PKR. */
        String currencyCode,

        /* Indicates whether this policy-grade-limit rule is active. */
        Boolean isActive,

        /* Optional administrative explanation. */
        String remarks,

        /* Timestamp when this record was created. */
        LocalDateTime createdAt,

        /* Timestamp when this record was last updated. */
        LocalDateTime updatedAt,

        /* Technical optimistic-locking version (not the business policy version). */
        Long version
) {

    public static TravelPolicyGradeLimitResponse of(TravelPolicyGradeLimit e) {
        return of(e, null);
    }

    /** Overload that also fills {@code policyCode} / {@code policyVersionNo} from the parent version. */
    public static TravelPolicyGradeLimitResponse of(TravelPolicyGradeLimit e, TravelPolicyVersion version) {
        return new TravelPolicyGradeLimitResponse(
                e.getId(), e.getPolicyVersionId(),
                version == null ? null : version.getPolicyCode(),
                version == null ? null : version.getVersionNo(),
                e.getGradeId(), e.getGradeCode(), e.getGradeName(),
                e.getExpenseTypeCode(), e.getLimitType(), e.getLimitAmount(), e.getCurrencyCode(),
                e.getIsActive(), e.getRemarks(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }
}

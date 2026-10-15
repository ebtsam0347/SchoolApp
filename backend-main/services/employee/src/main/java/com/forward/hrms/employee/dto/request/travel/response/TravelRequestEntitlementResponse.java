package com.forward.hrms.employee.dto.request.travel.response;

import com.forward.hrms.employee.entity.travel.TravelRequestEntitlement;
import com.forward.hrms.employee.enums.travel.CalculationBasis;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response representation of a Travel Request entitlement snapshot.
 * Unlike CreateTravelEntitlementRequest, this response contains the
 * policy-derived and historical snapshot information useful for display.
 */
public record TravelRequestEntitlementResponse(

        /* Database identifier of the entitlement. */
        Long id,

        /* Identifier of the Travel Request this entitlement belongs to. */
        Long travelRequestId,

        /* Business request number, e.g. TR-2026-000125. */
        String travelRequestNo,

        /* Identifier of the participant receiving this entitlement. */
        Long travelRequestParticipantId,

        /* Business employee code (preferred over the internal id for display). */
        String employeeCode,

        /* Employee identifier from the Employee domain. */
        Long employeeId,

        /* Employee name captured in the participant snapshot. */
        String employeeName,

        /* Employee grade identifier captured at entitlement creation. */
        Long gradeId,

        /* Employee grade business code captured at entitlement creation, e.g. E-4. */
        String gradeCode,

        /* Human-readable grade name captured at entitlement creation. */
        String gradeName,

        /* Identifier of the Travel Policy Version used to generate this entitlement. */
        Long policyVersionId,

        /* Business version number of the applicable Travel Policy. */
        Integer policyVersionNo,

        /* Identifier of the exact policy grade-limit rule used to calculate this entitlement. */
        Long policyGradeLimitId,

        /* Entitlement category: ACCOMMODATION, BREAKFAST, LUNCH, DINNER, ... */
        String entitlementTypeCode,

        /* Number of units allowed under this entitlement. */
        BigDecimal quantityAllowed,

        /* Maximum amount allowed for one entitlement unit. */
        BigDecimal unitLimitAmount,

        /* Maximum total amount allowed for this entitlement (quantityAllowed x unitLimitAmount). */
        BigDecimal totalLimitAmount,

        /* Currency used for this entitlement, e.g. PKR. */
        String currencyCode,

        /*
         * How the entitlement was calculated: POLICY, POLICY_PER_DAY,
         * POLICY_PER_MEAL, POLICY_PER_NIGHT, MANUAL_ADJUSTMENT.
         */
        CalculationBasis calculationBasisCode,

        /* Number of travel days used during entitlement calculation. */
        BigDecimal travelDays,

        /* Original policy unit-limit amount copied when the entitlement was generated. */
        BigDecimal policyUnitLimitAmount,

        /* Indicates whether this entitlement is currently active. */
        Boolean active,

        /* Timestamp from which this entitlement became effective. */
        LocalDateTime effectiveAt,

        /* Timestamp when this entitlement was superseded. NULL if not superseded. */
        LocalDateTime supersededAt,

        /* Explanation for why the entitlement was superseded. */
        String supersededReason,

        /* Technical optimistic-locking version. */
        Long version
) {

    /**
     * Maps the entity 1:1. Cross-aggregate display fields
     * ({@code travelRequestNo}, {@code employeeCode}, {@code employeeName},
     * {@code policyVersionNo}) are left null for the service to enrich.
     */
    public static TravelRequestEntitlementResponse of(TravelRequestEntitlement e) {
        return new TravelRequestEntitlementResponse(
                e.getId(), e.getTravelRequestId(), null, e.getTravelRequestParticipantId(),
                null, e.getEmployeeId(), null,
                e.getGradeIdSnapshot(), e.getGradeCodeSnapshot(), e.getGradeNameSnapshot(),
                e.getPolicyVersionId(), null, e.getPolicyGradeLimitId(),
                e.getEntitlementTypeCode(), e.getQuantityAllowed(), e.getUnitLimitAmount(),
                e.getTotalLimitAmount(), e.getCurrencyCode(),
                parseBasis(e.getCalculationBasisCode()), e.getTravelDays(),
                e.getPolicyUnitLimitAmount(), e.getActive(),
                e.getEffectiveAt(), e.getSupersededAt(), e.getSupersededReason(), e.getVersion());
    }

    private static CalculationBasis parseBasis(String code) {
        if (code == null) {
            return null;
        }
        try {
            return CalculationBasis.valueOf(code);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

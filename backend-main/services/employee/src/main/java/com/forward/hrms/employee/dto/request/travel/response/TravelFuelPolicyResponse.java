package com.forward.hrms.employee.dto.request.travel.response;

import com.forward.hrms.employee.entity.travel.TravelFuelPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for a Transport fuel/petrol issuance policy.
 */
public record TravelFuelPolicyResponse(

        /* Database identifier of the fuel policy. */
        Long id,

        /* The vehicle-source code this policy is for: COMPANY, EMPLOYEE, RENTAL, PUBLIC, OTHER. */
        String code,

        /* Human-readable name. */
        String name,

        /* Fuel average in kilometres per liter. Zero means no petrol is issued. */
        BigDecimal fuelAverage,

        /* Date from which this fuel policy becomes applicable. */
        LocalDate effectiveFrom,

        /* Date until which this fuel policy remains applicable. Null means no defined end date. */
        LocalDate effectiveTo,

        /* Whether this policy can currently be applied by Transport. */
        Boolean isActive,

        /* Timestamp when this record was created. */
        LocalDateTime createdAt,

        /* Timestamp when this record was last updated. */
        LocalDateTime updatedAt,

        /* Optimistic-locking version. */
        Long version
) {

    public static TravelFuelPolicyResponse of(TravelFuelPolicy e) {
        return new TravelFuelPolicyResponse(
                e.getId(), e.getCode(), e.getName(), e.getFuelAverage(),
                e.getEffectiveFrom(), e.getEffectiveTo(), e.getIsActive(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }
}

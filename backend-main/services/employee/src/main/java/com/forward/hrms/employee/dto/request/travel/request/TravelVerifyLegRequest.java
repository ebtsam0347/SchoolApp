package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One leg's Transport verification, supplied inside
 * {@link TransportVerifyRequest} at the first (TRANSPORT) stage of the
 * {@code TRAVEL_REQUEST} approval chain.
 *
 * <p>Transport may set {@code actualDistanceKm} (the validated distance) and
 * confirm the company-vehicle fields. It never touches
 * {@code plannedDistanceKm} — the requester's original figure is preserved
 * for audit. An {@code actualDistanceKm} of {@code 0} or {@code null} is
 * treated as "not supplied": the effective distance stays the planned
 * distance.
 *
 * <p>There is no separate fuel-policy identifier: {@code vehicleSourceCode}
 * both confirms the leg's vehicle source AND is the lookup key the backend
 * uses to resolve the applicable {@code travel_fuel_policies} row
 * ({@code code = vehicleSourceCode}) — the fuel average is always resolved
 * server-side, never supplied by the caller. A leg left with no vehicle
 * source (e.g. air/train/public-transport legs) has no fuel issuance.
 */
public record TravelVerifyLegRequest(

        /* I'd of the leg being verified. Must belong to the travel request. */
        @NotNull(message = "Leg id is required")
        Long legId,

        /*
         * Validated / actual distance for this leg in kilometres. Null or 0
         * means "not verified" and leaves the planned distance effective.
         */
        @PositiveOrZero(message = "Actual distance cannot be negative")
        BigDecimal actualDistanceKm,

        /*
         * Confirmed vehicle source: COMPANY, EMPLOYEE, RENTAL, PUBLIC, OTHER.
         * Optional — a leg omitted here keeps its current vehicle source
         * (typically the requester's own selection at creation). Also the
         * key used to resolve the applicable fuel policy.
         */
        @Size(max = 50, message = "Vehicle source code must not exceed 50 characters")
        String vehicleSourceCode,

        /* Confirmed vehicle registration/identification number. Optional. */
        @Size(max = 100, message = "Vehicle number must not exceed 100 characters")
        String vehicleNumber,

        /* Confirmed vehicle/transport description. Optional. */
        @Size(max = 250, message = "Vehicle description must not exceed 250 characters")
        String vehicleDescription
) {
}

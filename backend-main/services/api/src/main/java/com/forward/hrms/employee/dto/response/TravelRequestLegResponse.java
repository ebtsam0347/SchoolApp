package com.forward.hrms.employee.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response representation of one journey segment belonging to a Travel
 * Request. Exposes business-friendly names rather than persistence
 * snapshot field names.
 *
 * <p>Lives in the shared {@code api} module (alongside {@link TravelRequestResponse})
 * so the BFF's approvals-inbox composite can consume it directly, same as
 * {@link FactoryTourDto}; the entity-to-DTO mapping itself stays in
 * employee-service's {@code TravelRequestService}.
 */
public record TravelRequestLegResponse(

        /* Database identifier of the travel leg. */
        Long id,

        /* Identifier of the parent Travel Request. */
        Long travelRequestId,

        /* Sequential position of this leg within the Travel Request. */
        Integer legSequence,

        /* Business code identifying the origin location, e.g. SIALKOT. */
        String originLocationCode,

        /* Human-readable origin location name (snapshot at leg creation). */
        String originLocationName,

        /* Business code identifying the destination location, e.g. LAHORE. */
        String destinationLocationCode,

        /* Human-readable destination location name (snapshot at leg creation). */
        String destinationLocationName,

        /* Planned departure date/time. */
        LocalDateTime plannedDepartureAt,

        /* Planned arrival date/time. Null when not yet planned. */
        LocalDateTime plannedArrivalAt,

        /* Actual departure date/time. Null until the journey starts. */
        LocalDateTime actualDepartureAt,

        /* Actual arrival date/time. Null until the journey completes. */
        LocalDateTime actualArrivalAt,

        /* Lifecycle status of the leg: PLANNED, IN_PROGRESS, COMPLETED, CANCELLED. */
        String legStatus,

        /* Source/provider of the vehicle: COMPANY, EMPLOYEE, RENTAL, PUBLIC, OTHER. */
        String vehicleSourceCode,

        /* Vehicle registration/identification number. Null for air/train. */
        String vehicleNumber,

        /*
         * Descriptive information about the vehicle or transportation,
         * e.g. "Toyota Corolla", "Company Coaster", "PIA Flight".
         */
        String vehicleDescription,

        /* Planned distance in kilometres. */
        BigDecimal plannedDistanceKm,

        /* Actual distance traveled in kilometres. */
        BigDecimal actualDistanceKm,

        /*
         * Distance used for distance-based calculations: the actual distance
         * when Transport has verified one (> 0), otherwise the planned
         * distance.
         */
        BigDecimal effectiveDistanceKm,

        /*
         * Fuel policy Transport applied to this leg. Null when this leg has
         * no fuel issuance (e.g. air/train/public transport).
         */
        Long travelFuelPolicyId,

        /*
         * Petrol quantity issued for this leg, calculated at Transport
         * verification from actualDistanceKm (never plannedDistanceKm).
         */
        BigDecimal petrolIssued,

        /* Additional instructions or information specific to this leg. */
        String remarks,

        /* Timestamp when this record was created. */
        LocalDateTime createdAt,

        /* Timestamp when this record was last updated. */
        LocalDateTime updatedAt,

        /* Optimistic-locking version. */
        Long version
) {
}

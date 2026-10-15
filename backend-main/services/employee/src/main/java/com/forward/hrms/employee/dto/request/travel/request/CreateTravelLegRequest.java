package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Nested journey-leg information supplied by the client.
 *
 * <p>
 * The backend will create the corresponding TravelRequestLeg entity
 * after validating the route and transportation information.
 */
public record CreateTravelLegRequest(

        /*
         * Sequential position of the leg (1 = Sialkot -> Lahore,
         * 2 = Lahore -> Faisalabad, ...).
         */
        @NotNull(message = "Leg sequence is required")
        Integer legSequence,

        /* Business code of the origin location, e.g. "SIALKOT". */
        @NotBlank(message = "Origin location code is required")
        @Size(max = 100, message = "Origin location code must not exceed 100 characters")
        String originLocationCode,

        /* Business code of the destination location, e.g. "LAHORE". */
        @NotBlank(message = "Destination location code is required")
        @Size(max = 100, message = "Destination location code must not exceed 100 characters")
        String destinationLocationCode,

        /* Planned departure date/time for this leg. */
        @NotNull(message = "Planned departure date/time is required")
        LocalDateTime plannedDepartureAt,

        /* Planned arrival date/time. May be omitted when not known. */
        LocalDateTime plannedArrivalAt,

        /*
         * Transportation method: COMPANY_VEHICLE, PERSONAL_VEHICLE,
         * HIRED_VEHICLE, PUBLIC_TRANSPORT, AIR, TRAIN.
         */
        @NotBlank(message = "Transportation type code is required")
        @Size(max = 50, message = "Transportation type code must not exceed 50 characters")
        String transportationTypeCode,

        /*
         * Who provides the vehicle/transportation: COMPANY, EMPLOYEE,
         * RENTAL, PUBLIC, OTHER. Optional - may not be relevant for all
         * transportation types.
         */
        @Size(max = 50, message = "Vehicle source code must not exceed 50 characters")
        String vehicleSourceCode,

        /* Vehicle registration/identification number, e.g. "LEB-1234". */
        @Size(max = 100, message = "Vehicle number must not exceed 100 characters")
        String vehicleNumber,

        /*
         * Optional description of the vehicle or transportation, e.g.
         * "Toyota Corolla", "Company Coaster", "PIA Flight".
         */
        @Size(max = 250, message = "Vehicle description must not exceed 250 characters")
        String vehicleDescription,

        /*
         * Planned distance for this leg in kilometres. Should normally be
         * calculated or validated by the backend.
         */
        BigDecimal plannedDistanceKm,

        /* Additional instructions or information for this leg. */
        String remarks
) {

    /**
     * Validates that the arrival time, when supplied, is not before the
     * departure time.
     */
    @AssertTrue(message = "Planned arrival must be after or equal to planned departure")
    public boolean isDateRangeValid() {
        if (plannedDepartureAt == null || plannedArrivalAt == null) {
            return true;
        }
        return !plannedArrivalAt.isBefore(plannedDepartureAt);
    }

    /**
     * Validates that the distance, when supplied, is not negative.
     */
    @AssertTrue(message = "Planned distance cannot be negative")
    public boolean isDistanceValid() {
        if (plannedDistanceKm == null) {
            return true;
        }
        return plannedDistanceKm.signum() >= 0;
    }
}

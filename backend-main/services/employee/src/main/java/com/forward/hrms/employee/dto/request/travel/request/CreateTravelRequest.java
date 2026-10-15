package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request payload used to create a new Travel Request.
 *
 * <p>
 * This follows the nested creation approach where the Travel Request
 * and its initial participants and journey legs can be created
 * together in a single API request.
 *
 * <p>
 * Example endpoint:
 *
 * <pre>
 * POST /travel/organization/requests
 * </pre>
 *
 * <p>
 * Policy version, requester employee ID, estimated amount,
 * participant snapshots and entitlement records are intentionally
 * not supplied directly by the client. These values should be
 * resolved/calculated by the Travel Service.
 */
public record CreateTravelRequest(

        /*
         * Type of travel, and the selector for the entitlement policy:
         * LOCAL resolves the LOCAL_FACTORY_TRAVEL family,
         * INTERNATIONAL the INTERNATIONAL_FACTORY_TRAVEL family. Must be a
         * TravelType value; anything else is rejected rather than
         * resolving an arbitrary policy.
         */
        @NotBlank(message = "Travel type code is required")
        @Size(max = 50, message = "Travel type code must not exceed 50 characters")
        String travelTypeCode,

        /*
         * Currency used for the Travel Request, e.g. "PKR". Resolved /
         * validated against the company's supported currencies.
         */
        @NotBlank(message = "Currency code is required")
        @Size(min = 3, max = 3, message = "Currency code must contain exactly 3 characters")
        String currencyCode,

        /* Overall business purpose/reason for the travel. */
        @NotBlank(message = "Travel purpose is required")
        String purpose,

        /* Overall planned departure/start date and time. */
        @NotNull(message = "Travel start date/time is required")
        LocalDateTime startDatetime,

        /* Overall planned return/end date and time. */
        @NotNull(message = "Travel end date/time is required")
        LocalDateTime endDatetime,

        /*
         * Employees participating in this Travel Request, identified by
         * employeeCode. The Travel Service resolves each code through the
         * Employee domain and creates the participant snapshot.
         */
        @NotEmpty(message = "At least one participant is required")
        @Valid
        List<CreateTravelParticipantRequest> participants,

        /*
         * Journey legs belonging to this Travel Request. A Travel Request
         * may contain one or more legs.
         */
        @NotEmpty(message = "At least one leg is required")
        @Valid
        List<CreateTravelLegRequest> legs
) {
}

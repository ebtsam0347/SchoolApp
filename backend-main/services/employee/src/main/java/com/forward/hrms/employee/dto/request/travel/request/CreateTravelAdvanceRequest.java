package com.forward.hrms.employee.dto.request.travel.request;

import com.forward.hrms.employee.enums.travel.TravelAdvanceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload used to create a new Travel Advance.
 *
 * <p>
 * A Travel Advance belongs to an existing Travel Request and is
 * requested on behalf of a specific participant.
 *
 * <p>
 * The client supplies only the information required to request
 * the advance. Fields related to approval, payment, settlement,
 * reconciliation and system-generated references are intentionally
 * not accepted from the client.
 *
 * <p>
 * Example endpoint:
 *
 * <pre>
 * POST /travel/requests/{travelRequestId}/advances
 * </pre>
 *
 * <p>
 * The backend is responsible for resolving the employee code,
 * validating that the employee is a participant of the Travel
 * Request, generating the advance number, setting the initial
 * status and handling all approval/payment/settlement information.
 */
public record CreateTravelAdvanceRequest(

        /*
         * Business employee code of the participant who will receive or
         * be responsible for the advance, e.g. "EMP-100245". The backend
         * resolves this code through the Employee domain and identifies
         * the corresponding TravelRequestParticipant.
         */
        @NotBlank(message = "Employee code is required")
        @Size(max = 50, message = "Employee code must not exceed 50 characters")
        String employeeCode,

        /*
         * Business type of the advance: INITIAL, ADDITIONAL, EMERGENCY or
         * ADJUSTMENT.
         */
        @NotNull(message = "Advance type is required")
        TravelAdvanceType advanceType,

        /*
         * Amount requested by the employee/requester - not the approved
         * or actually paid amount. Approval may result in a lower amount.
         */
        @NotNull(message = "Requested amount is required")
        @DecimalMin(value = "0.01", message = "Requested amount must be greater than zero")
        BigDecimal requestedAmount,

        /* Currency of the requested advance, e.g. "PKR". */
        @NotBlank(message = "Requested currency code is required")
        @Size(min = 3, max = 3, message = "Requested currency code must contain exactly 3 characters")
        String requestedCurrencyCode,

        /*
         * Business justification for requesting the advance, e.g. "Advance
         * required for hotel, meals and local transport during Lahore
         * factory visit."
         */
        @NotBlank(message = "Advance purpose is required")
        String purpose,

        /* Optional additional remarks supplied by the requester. */
        String remarks
) {
}

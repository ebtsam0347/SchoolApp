package com.forward.hrms.employee.dto.request.travel.request;

import com.forward.hrms.employee.enums.travel.TravelSettlementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload used to create a Travel Request Settlement.
 *
 * <p>
 * A settlement reconciles the financial position of a participant
 * against the expenses incurred and advances received during a Travel
 * Request. The settlement and its settlement items can be created
 * together in a single API request.
 *
 * <p>
 * Example endpoint:
 *
 * <pre>
 * POST /travel/requests/{travelRequestId}/settlements
 * </pre>
 *
 * <p>
 * Calculated financial totals, settlement direction, approval
 * information, payment information, audit information and the
 * settlement number are intentionally not supplied by the client.
 */
public record CreateTravelSettlementRequest(

        /*
         * Participant whose travel expenses and advances are being
         * settled - refers to the local TravelRequestParticipant record,
         * not an employee id.
         */
        @NotNull(message = "Participant is required")
        Long participantId,

        /* Type of settlement: FINAL, INTERIM or ADJUSTMENT. */
        @NotNull(message = "Settlement type is required")
        TravelSettlementType settlementType,

        /* ISO 4217 currency code used for the final settlement, e.g. "PKR". */
        @NotNull(message = "Settlement currency code is required")
        @Size(min = 3, max = 3, message = "Settlement currency code must contain exactly 3 characters")
        String settlementCurrencyCode,

        /* Human-readable explanation of the settlement. */
        @Size(max = 5000, message = "Settlement summary must not exceed 5000 characters")
        String settlementSummary,

        /*
         * Financial records included in this settlement. Each item may
         * represent an expense, advance or approved adjustment.
         */
        @NotEmpty(message = "At least one settlement item is required")
        @Valid
        List<CreateTravelSettlementItemRequest> items,

        /* Additional remarks supplied by the requester. */
        @Size(max = 5000, message = "Remarks must not exceed 5000 characters")
        String remarks
) {
}

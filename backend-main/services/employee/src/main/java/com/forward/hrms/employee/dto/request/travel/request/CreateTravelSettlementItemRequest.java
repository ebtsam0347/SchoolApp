package com.forward.hrms.employee.dto.request.travel.request;

import com.forward.hrms.employee.enums.travel.TravelSettlementItemDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemSourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Nested settlement-item information supplied by the client. Represents
 * one financial record included in a Travel Request Settlement.
 *
 * <p>
 * Supported source types:
 * <ul>
 *     <li>EXPENSE - references a TravelExpenseItem</li>
 *     <li>ADVANCE - references a TravelRequestAdvance</li>
 *     <li>ADJUSTMENT - references an approved adjustment record</li>
 * </ul>
 *
 * <p>
 * The settlement itself is not supplied here - the parent
 * CreateTravelSettlementRequest owns it and the backend associates each
 * item with it. Amounts are validated against the corresponding source
 * record by the Travel Service; the client is not trusted to determine
 * eligibility.
 */
public record CreateTravelSettlementItemRequest(

        /* Type of financial record being included: EXPENSE, ADVANCE, ADJUSTMENT. */
        @NotNull(message = "Settlement item source type is required")
        TravelSettlementItemSourceType sourceType,

        /*
         * ID of the source financial record. Its meaning depends on
         * sourceType: EXPENSE -> TravelExpenseItem.id, ADVANCE ->
         * TravelRequestAdvance.id, ADJUSTMENT -> approved adjustment
         * reference. The backend verifies the record belongs to the same
         * Travel Request/participant and is eligible.
         */
        @NotNull(message = "Source record ID is required")
        Long sourceRecordId,

        /*
         * Original amount of the source record. Informational from the
         * client - the backend resolves the authoritative amount from the
         * source record.
         */
        @NotNull(message = "Source amount is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "Source amount cannot be negative")
        @Digits(integer = 13, fraction = 2,
                message = "Source amount must have at most 13 integer digits and 2 decimal places")
        BigDecimal sourceAmount,

        /*
         * Amount to be included in this settlement. May be lower than the
         * source amount. The backend validates it against the source
         * record, applicable entitlement, previous settlements and the
         * overall settlement rules.
         */
        @NotNull(message = "Settlement amount is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "Settlement amount cannot be negative")
        @Digits(integer = 13, fraction = 2,
                message = "Settlement amount must have at most 13 integer digits and 2 decimal places")
        BigDecimal settlementAmount,

        /*
         * ISO 4217 currency code of the settlement item, e.g. "PKR".
         * Validated against the source record and settlement
         * currency/conversion rules.
         */
        @NotNull(message = "Currency code is required")
        @Size(min = 3, max = 3, message = "Currency code must contain exactly 3 characters")
        String currencyCode,

        /*
         * Financial direction: PAYABLE increases the amount payable to
         * the employee; RECEIVABLE increases the amount recoverable from
         * the employee.
         */
        @NotNull(message = "Settlement item direction is required")
        TravelSettlementItemDirection direction,

        /* Optional explanation of why this item is included. */
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description
) {
}

package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for the single-reviewer approve action on a travel expense.
 *
 * <p>{@code approvedAmount} is optional — when omitted the reviewer
 * accepts the policy-derived eligible amount in full. When supplied it
 * must be between zero and the claimed amount; a value below the eligible
 * amount moves the expense to {@code PARTIALLY_APPROVED}.
 */
public record TravelExpenseReviewRequest(

        @DecimalMin(value = "0.00", inclusive = true, message = "Approved amount cannot be negative")
        @Digits(integer = 13, fraction = 2,
                message = "Approved amount must have at most 13 integer digits and 2 decimal places")
        BigDecimal approvedAmount,

        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note
) {
}

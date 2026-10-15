package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request payload used to submit a Travel Expense Item - the
 * information an employee supplies when claiming an actual expense
 * incurred during a Travel Request.
 *
 * <p>
 * The backend is responsible for resolving the employee, participant,
 * applicable entitlement, eligibility, reimbursement amount, currency
 * conversion and workflow status. Fields such as eligibleAmount,
 * approvedAmount, exchangeRate, status, reviewedBy/reviewedAt, paidAt,
 * paymentReference and version are not accepted from the client.
 */
public record CreateTravelExpenseItemRequest(

        /*
         * Business employee code of the employee claiming the expense,
         * e.g. "EMP-100245". The backend resolves it to the corresponding
         * TravelRequestParticipant on the Travel Request.
         */
        @NotBlank(message = "Employee code is required")
        @Size(max = 50, message = "Employee code must not exceed 50 characters")
        String employeeCode,

        /*
         * Optional entitlement this expense belongs to, e.g. "DINNER".
         * The backend still validates that the entitlement belongs to the
         * participant and is applicable.
         */
        @Size(max = 50, message = "Entitlement type code must not exceed 50 characters")
        String entitlementTypeCode,

        /*
         * High-level category: ACCOMMODATION, MEAL, TRANSPORTATION, TOLL,
         * PARKING, OTHER.
         */
        @NotBlank(message = "Expense category code is required")
        @Size(max = 50, message = "Expense category code must not exceed 50 characters")
        String expenseCategoryCode,

        /*
         * More specific classification: HOTEL, BREAKFAST, LUNCH, DINNER,
         * TAXI, FUEL, TOLL, PARKING, TRAIN_TICKET, AIR_TICKET, ...
         */
        @Size(max = 50, message = "Expense subcategory code must not exceed 50 characters")
        String expenseSubcategoryCode,

        /* Human-readable description of the expense. */
        String description,

        /* Date/time when the expense occurred. */
        @NotNull(message = "Expense date/time is required")
        LocalDateTime expenseAt,

        /*
         * Actual amount incurred/claimed by the employee. The backend
         * uses this to calculate the eligible and reimbursable amount.
         */
        @NotNull(message = "Claimed amount is required")
        @DecimalMin(value = "0.01", message = "Claimed amount must be greater than zero")
        BigDecimal claimedAmount,

        /* Currency in which the expense was incurred, e.g. "PKR". */
        @NotBlank(message = "Claimed currency code is required")
        @Size(min = 3, max = 3, message = "Claimed currency code must contain exactly 3 characters")
        String claimedCurrencyCode,

        /* Receipt/invoice/reference number on the supporting document. */
        @Size(max = 100, message = "Receipt reference must not exceed 100 characters")
        String receiptReference,

        /*
         * External document/file identifier for the receipt. The file
         * itself is uploaded through the document storage mechanism; this
         * holds only its resulting id.
         */
        @Size(max = 200, message = "Receipt document ID must not exceed 200 characters")
        String receiptDocumentId,

        /* Additional employee comments or notes. */
        String remarks
) {

    /**
     * Validates that the claimed amount is positive - intentionally
     * defensive in addition to {@link DecimalMin}.
     */
    @AssertTrue(message = "Claimed amount must be greater than zero")
    public boolean isClaimedAmountValid() {
        return claimedAmount == null || claimedAmount.signum() > 0;
    }
}

package com.forward.hrms.employee.dto.request.travel.request;

import com.forward.hrms.employee.enums.travel.TravelLimitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload used to create a grade-specific travel
 * entitlement/expense limit. Represents one rule within a Travel
 * Policy Version.
 *
 * <p>
 * Example: {@code FACTORY_TRAVEL / Version 2}, grade {@code E-4}, rule
 * {@code DINNER / PER_DAY / PKR 1,500}.
 *
 * <p>
 * The policy version is resolved from the request context/path, not
 * supplied as a raw id. Likewise {@code gradeId}/{@code gradeName} are
 * resolved by the backend from the supplied {@code gradeCode} against
 * the Employee/Master Data domain.
 */
public record CreateTravelPolicyGradeLimitRequest(

        /*
         * Business code identifying the employee grade, e.g. "E-4". The
         * backend resolves it to the corresponding grade id and grade
         * information.
         */
        @NotBlank(message = "Grade code is required")
        @Size(max = 50, message = "Grade code must not exceed 50 characters")
        String gradeCode,

        /*
         * Entitlement/expense type the limit applies to: ACCOMMODATION,
         * BREAKFAST, LUNCH, DINNER, TA_TD, FUEL, TOLL, ...
         * Code-based so new types need no schema change.
         */
        @NotBlank(message = "Expense type code is required")
        @Size(max = 50, message = "Expense type code must not exceed 50 characters")
        String expenseTypeCode,

        /*
         * How the limit is applied: PER_DAY, PER_NIGHT, PER_TRIP,
         * PER_CLAIM, PER_KM, FIXED.
         */
        @NotNull(message = "Limit type is required")
        TravelLimitType limitType,

        /*
         * Maximum amount allowed by this rule, e.g. E-4 / DINNER / PER_DAY
         * = 1500.00 PKR. Must be >= 0.
         */
        @NotNull(message = "Limit amount is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "Limit amount cannot be negative")
        BigDecimal limitAmount,

        /* Currency in which the limit is defined, e.g. "PKR". */
        @NotBlank(message = "Currency code is required")
        @Size(min = 3, max = 3, message = "Currency code must contain exactly 3 characters")
        String currencyCode,

        /*
         * Whether this rule is active immediately after creation.
         * Defaults to true; the backend may override when policy
         * lifecycle rules require it to stay inactive until the policy
         * version becomes active.
         */
        Boolean isActive,

        /* Optional administrative explanation or note for this rule. */
        String remarks
) {

    public CreateTravelPolicyGradeLimitRequest {
        if (isActive == null) {
            isActive = true;
        }
    }
}

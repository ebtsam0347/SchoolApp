package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload to create/update a Transport fuel/petrol issuance
 * policy (master data). {@code code} IS the vehicle-source code — one of
 * {@code VehicleSource}'s values (COMPANY, EMPLOYEE, RENTAL, PUBLIC,
 * OTHER); Transport verification resolves a leg's fuel policy by looking
 * this up directly against the leg's {@code vehicleSourceCode}, so there is
 * no separate fuel-policy identifier to supply. {@code fuelAverage} is
 * kilometres per liter; {@code 0.00} is a valid value meaning no petrol is
 * issued under this policy — it is never rejected as an error.
 */
public record CreateTravelFuelPolicyRequest(

        /* The vehicle-source code this policy is for: COMPANY, EMPLOYEE, RENTAL, PUBLIC, OTHER. */
        @NotBlank(message = "Code is required")
        @Size(max = 50, message = "Code must not exceed 50 characters")
        String code,

        /* Human-readable name of the fuel policy. */
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must not exceed 200 characters")
        String name,

        /*
         * Fuel average in kilometres per liter. Must be >= 0; zero means no
         * petrol is issued under this policy.
         */
        @NotNull(message = "Fuel average is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "Fuel average cannot be negative")
        BigDecimal fuelAverage,

        /* Date from which this fuel policy becomes applicable. */
        @NotNull(message = "Effective-from date is required")
        LocalDate effectiveFrom,

        /* Date until which this fuel policy remains applicable. Optional. */
        LocalDate effectiveTo,

        /* Whether this policy is usable immediately after creation. Defaults to true. */
        Boolean isActive
) {

    public CreateTravelFuelPolicyRequest {
        if (isActive == null) {
            isActive = true;
        }
    }
}

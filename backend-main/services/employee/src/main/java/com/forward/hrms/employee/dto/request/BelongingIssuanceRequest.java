package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.belonging.ActivationStatus;
import com.forward.hrms.employee.enums.belonging.BelongingCondition;
import com.forward.hrms.employee.enums.belonging.ClearanceStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BelongingIssuanceRequest(
        @NotNull
        Long belongingMasterId,

        @NotNull
        Long employeeId,

        @Size(max = 100)
        String assetModel,

        Short assetYear,

        @DecimalMin("0.00")
        BigDecimal assetPrice,

        LocalDate issuedDate,

        LocalDate expiryDate,

        @DecimalMin("0.00")
        @DecimalMax("100.00")
        BigDecimal buyBackPercEmployee,

        @DecimalMin("0.00")
        @DecimalMax("100.00")
        BigDecimal buyBackPercEmployer,

        BelongingCondition condition,

        @NotNull
        ActivationStatus activationStatus,

        @NotNull
        LocalDate activationDate,

        @Size(max = 500)
        String description,

        ClearanceStatus clearanceStatus,

        @Size(max = 50)
        String clearanceId,

        LocalDate clearanceDate
) {
}

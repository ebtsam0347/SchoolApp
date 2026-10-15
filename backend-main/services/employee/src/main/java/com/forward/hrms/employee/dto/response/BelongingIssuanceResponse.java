package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.belonging.ActivationStatus;
import com.forward.hrms.employee.enums.belonging.BelongingCondition;
import com.forward.hrms.employee.enums.belonging.ClearanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BelongingIssuanceResponse(
        Long id,

        Long belongingMasterId,

        String belongingName,

        Long employeeId,

        String assetModel,

        Short assetYear,

        BigDecimal assetPrice,

        LocalDate issuedDate,

        LocalDate expiryDate,

        BigDecimal buyBackPercEmployee,

        BigDecimal buyBackPercEmployer,

        BelongingCondition condition,

        ActivationStatus activationStatus,

        LocalDate activationDate,

        String description,

        ClearanceStatus clearanceStatus,

        String clearanceId,

        LocalDate clearanceDate
) {
}

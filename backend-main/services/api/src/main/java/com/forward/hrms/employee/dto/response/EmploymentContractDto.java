package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "EmploymentContractDto", description = "One row of the employee's contract history.")
public record EmploymentContractDto(
        long id,
        long employeeId,
        ContractType contractType,
        LocalDate startDate,
        @Schema(nullable = true) LocalDate endDate,
        ContractStatus status,
        @Schema(description = "Only present on PROBATION contracts.", nullable = true) Integer probationDays,
        boolean extended,
        @Schema(nullable = true) String documentKey,
        @Schema(description = "Short-lived signed GET URL for the contract document. " +
                "Null when no document is attached or the storage call failed.", nullable = true)
        String documentUrl
) {}

package com.forward.hrms.bff.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeResolvedLabels;
import com.forward.hrms.employee.dto.response.EmploymentContractDto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "AccountProfile",
        description = "ESS counterpart to /bff/employee/{id} — single round-trip for the " +
                "signed-in employee's /account/profile tab.")
public record AccountProfile(
        EmployeeDto employee,
        EmployeeResolvedLabels resolved,
        @Schema(description = "Active contract row.", nullable = true)
        EmploymentContractDto activeContract,
        @Schema(description = "Direct-reports preview — empty when the caller has no team.")
        List<EmployeeDetail.ReportSummary> team,
        @Schema(description = "Probation end date when one is active, null otherwise.", nullable = true)
        LocalDate upcomingProbationDeadline
) {}

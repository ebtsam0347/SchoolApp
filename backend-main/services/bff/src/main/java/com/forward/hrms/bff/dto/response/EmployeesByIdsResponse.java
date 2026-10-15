package com.forward.hrms.bff.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "EmployeesByIdsResponse",
        description = "Resolver result. Order matches the request id order; ids the caller cannot " +
                "see (or that don't exist) are silently dropped — clients should compare lengths " +
                "if exact-match matters.")
public record EmployeesByIdsResponse(
        List<EmployeeSummary> employees
) {}

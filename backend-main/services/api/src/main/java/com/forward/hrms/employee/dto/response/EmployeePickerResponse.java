package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "EmployeePickerResponse",
        description = "Response envelope for `/employees/picker` — name + code typeahead.")
public record EmployeePickerResponse(List<EmployeePickerCandidate> items) {}

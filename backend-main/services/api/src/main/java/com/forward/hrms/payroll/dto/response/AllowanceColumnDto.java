package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AllowanceColumnDto",
        description = "An allowance paid apart from salary — an active catalog component with calculateWithSalary = false.")
public record AllowanceColumnDto(
        @Schema(description = "Catalog component id.") long componentId,
        @Schema(description = "Catalog code; the key of every `amounts` map.", example = "TRAVEL") String code,
        @Schema(description = "Display name.", example = "Travel") String name
) {}

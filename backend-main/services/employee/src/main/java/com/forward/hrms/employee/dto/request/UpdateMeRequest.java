package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateMeRequest",
        description = "Self-serve patch under /employees/me. Limited to contact + address fields.")
public record UpdateMeRequest(
        @Schema(description = "Mobile number.", maxLength = 32) @Size(max = 32) String phone,
        @Schema(description = "Personal email (work email stays HR-managed).") @Email String personalEmail,
        @Schema(description = "Permanent (home) address — street + area.") String permanentAddress,
        @Schema(description = "Current (residential) address.") String currentAddress,
        @Schema(description = "City id (provinces resolved via the city join).") Long cityId
) {}

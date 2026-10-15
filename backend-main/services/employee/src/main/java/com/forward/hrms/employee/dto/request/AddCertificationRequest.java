package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "AddCertificationRequest")
public record AddCertificationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String issuingBody,
        LocalDate issueDate,
        @Schema(description = "Null = no expiry.") LocalDate expiryDate,
        @Size(max = 128) String credentialId,
        @Size(max = 512) String documentKey
) {}

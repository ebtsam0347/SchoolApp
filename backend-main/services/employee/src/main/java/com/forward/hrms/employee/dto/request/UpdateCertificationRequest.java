package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "UpdateCertificationRequest", description = "Patch. Null fields are left unchanged.")
public record UpdateCertificationRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String issuingBody,
        LocalDate issueDate,
        LocalDate expiryDate,
        @Size(max = 128) String credentialId,
        @Size(max = 512) String documentKey
) {}

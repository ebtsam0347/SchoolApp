package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "ProfilePictureFilenameRequest")
public record ProfilePictureFilenameRequest(
        @Schema(description = "Original filename (used to compute the object key suffix).",
                example = "avatar.jpg",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String filename,
        @Schema(description = "Declared object size in bytes. Rejected at pre-sign if above " +
                "`hrms.employee.documents.avatar-max-size-bytes` (default 2 MB).")
        @Positive Long bytes
) {}

package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DocumentType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "UploadUrlRequest",
        description = "Ask for a pre-signed PUT URL. The service crafts the object key from " +
                "employeeId / docType / a UUID; the client uploads to the URL and then POSTs " +
                "the metadata back.")
public record UploadUrlRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DocumentType docType,
        @Schema(description = "Original filename — used to compute the suffix on the object key.",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String filename,
        @Schema(description = "Optional content-type hint stored alongside the metadata.",
                maxLength = 128) @Size(max = 128) String contentType,
        @Schema(description = "Declared object size in bytes. The server rejects pre-sign requests above " +
                "`hrms.employee.documents.max-size-bytes` (default 10 MB) and re-checks the actual size at record-time.")
        @Positive Long bytes
) {}

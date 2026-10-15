package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DocumentType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "RecordDocumentRequest",
        description = "Posted after the client finishes uploading to the pre-signed URL. " +
                "Service verifies the object exists in MinIO before persisting.")
public record RecordDocumentRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DocumentType docType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String title,
        @Schema(description = "Object key returned from /upload-url.",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 512)
        @NotBlank @Size(max = 512) String objectKey,
        @Schema(maxLength = 128) @Size(max = 128) String contentType
) {}

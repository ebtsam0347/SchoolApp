package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "UploadUrlResponse",
        description = "Pre-signed PUT URL the client uploads the file directly to. After the upload " +
                "completes, post the metadata to /documents (or /profile-picture) referencing this objectKey.")
public record UploadUrlResponse(
        @Schema(description = "Bucket the URL targets.", example = "employee-docs") String bucket,
        @Schema(description = "Object key the URL is signed for. " +
                "Pass this back when recording metadata.") String objectKey,
        @Schema(description = "Pre-signed PUT URL.") String uploadUrl,
        @Schema(description = "When the URL stops working (UTC).") LocalDateTime expiresAt
) {}

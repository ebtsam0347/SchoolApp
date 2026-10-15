package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "SetProfilePictureRequest",
        description = "Posted after the client finishes uploading to the pre-signed profile-picture URL.")
public record SetProfilePictureRequest(
        @Schema(description = "Object key returned from /profile-picture/upload-url.",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 512)
        @NotBlank @Size(max = 512) String objectKey
) {}

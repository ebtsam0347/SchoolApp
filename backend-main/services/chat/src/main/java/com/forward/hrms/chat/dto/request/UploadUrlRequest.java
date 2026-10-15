package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "ChatUploadUrlRequest",
        description = "Request a pre-signed PUT URL for a chat attachment. Server validates the content-type whitelist and size cap before signing.")
public record UploadUrlRequest(
        @Schema(description = "MIME type the client will PUT.",
                example = "image/png", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String contentType,

        @Schema(description = "Size in bytes the client will PUT. Used to enforce the per-attachment size cap.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long sizeBytes,

        @Schema(description = "Original filename — preserved on the metadata row.",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String originalName
) {}

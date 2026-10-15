package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "AttachmentRef",
        description = "Reference to an already-uploaded MinIO object. The server stat()s each before persisting; missing objects yield 422.")
public record AttachmentRef(
        @Schema(description = "Key returned by /chat/attachments/upload-url.", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 512)
        @NotBlank @Size(max = 512) String objectKey,

        @Schema(description = "Content-type from the upload-url request.", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String contentType,

        @Schema(description = "Size in bytes; the server still verifies against MinIO.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long sizeBytes,

        @Schema(description = "Original filename for display.", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String originalName
) {}

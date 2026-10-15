package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "EditMessageRequest", description = "Edit your own message within the edit window (15 minutes).")
public record EditMessageRequest(
        @Schema(description = "New message body.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 8000) String body
) {}

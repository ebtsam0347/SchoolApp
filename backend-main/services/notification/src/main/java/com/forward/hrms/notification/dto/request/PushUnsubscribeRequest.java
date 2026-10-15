package com.forward.hrms.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The subscription endpoint to forget.")
public record PushUnsubscribeRequest(
        @NotBlank @Size(max = 1024) String endpoint
) {
}

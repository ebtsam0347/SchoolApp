package com.forward.hrms.notification.dto.request;

import com.forward.hrms.notification.enums.NotificationCategory;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateTemplateRequest")
public record CreateTemplateRequest(
        @Schema(description = "Canonical type key.", example = "LEAVE_APPROVED",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64)
        @NotBlank @Size(max = 64) String type,

        @Schema(description = "Human-readable label for preference toggles and admin lists.",
                example = "Leave fully approved", maxLength = 128)
        @Size(max = 128) String label,

        @Schema(description = "One-line explanation of when this notification fires.")
        String description,

        @Schema(description = "Title template.", example = "Your leave was approved",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String titleTpl,

        @Schema(description = "Body template.")
        String bodyTpl,

        @Schema(description = "Link template.", maxLength = 512)
        @Size(max = 512) String linkTpl,

        @Schema(description = "Coarse grouping for the bell UI.")
        NotificationCategory category
) {}

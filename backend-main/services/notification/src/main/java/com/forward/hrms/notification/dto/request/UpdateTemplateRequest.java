package com.forward.hrms.notification.dto.request;

import com.forward.hrms.notification.enums.NotificationCategory;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateTemplateRequest", description = "Patch — only non-null fields apply.")
public record UpdateTemplateRequest(
        @Schema(description = "New label.", maxLength = 128)
        @Size(max = 128) String label,

        @Schema(description = "New description.")
        String description,

        @Schema(description = "New title template.")
        String titleTpl,

        @Schema(description = "New body template.")
        String bodyTpl,

        @Schema(description = "New link template.", maxLength = 512)
        @Size(max = 512) String linkTpl,

        @Schema(description = "New category.")
        NotificationCategory category,

        @Schema(description = "Activate/deactivate.")
        Boolean active
) {}

package com.forward.hrms.notification.dto.response;

import com.forward.hrms.notification.entity.NotificationTemplate;
import com.forward.hrms.notification.enums.NotificationCategory;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TemplateDto", description = "Renderable template for a notification type.")
public record TemplateDto(
        @Schema(description = "Internal id.") long id,
        @Schema(description = "Canonical type key.", example = "LEAVE_APPROVED") String type,
        @Schema(description = "Human-readable label for preference toggles and admin lists.",
                example = "Leave fully approved", nullable = true) String label,
        @Schema(description = "One-line explanation of when this notification fires.", nullable = true)
        String description,
        @Schema(description = "Title template (Mustache-style {{placeholders}}).") String titleTpl,
        @Schema(description = "Body template.", nullable = true) String bodyTpl,
        @Schema(description = "Link template.", nullable = true) String linkTpl,
        @Schema(description = "Coarse grouping.", nullable = true) NotificationCategory category,
        @Schema(description = "Inactive templates are skipped at dispatch time.") boolean active
) {
    public static TemplateDto of(NotificationTemplate t) {
        return new TemplateDto(
                t.getId(),
                t.getType(),
                t.getLabel(),
                t.getDescription(),
                t.getTitleTpl(),
                t.getBodyTpl(),
                t.getLinkTpl(),
                t.getCategory(),
                t.isActive());
    }
}

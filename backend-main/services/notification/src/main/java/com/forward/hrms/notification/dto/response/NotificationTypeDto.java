package com.forward.hrms.notification.dto.response;

import com.forward.hrms.notification.entity.NotificationTemplate;
import com.forward.hrms.notification.enums.NotificationCategory;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "NotificationTypeDto",
        description = "Type catalog entry — what the FE preferences page and admin templates list render. "
                + "Backed by the active row in notification_templates for that type.")
public record NotificationTypeDto(
        @Schema(description = "Canonical type key.", example = "LEAVE_APPROVED") String type,
        @Schema(description = "Human-readable label for the toggle row.", example = "Leave fully approved")
        String label,
        @Schema(description = "One-line explanation of when this notification fires.", nullable = true)
        String description,
        @Schema(description = "Coarse grouping for category filters.", nullable = true) NotificationCategory category
) {
    public static NotificationTypeDto of(NotificationTemplate t) {
        // Fall back to the type itself if a row predates V0003 and was not backfilled.
        String label = t.getLabel() == null || t.getLabel().isBlank() ? t.getType() : t.getLabel();
        return new NotificationTypeDto(t.getType(), label, t.getDescription(), t.getCategory());
    }
}

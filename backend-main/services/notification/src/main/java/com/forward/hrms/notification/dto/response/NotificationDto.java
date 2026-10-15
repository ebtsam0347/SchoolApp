package com.forward.hrms.notification.dto.response;

import com.forward.hrms.notification.entity.Notification;
import com.forward.hrms.notification.enums.NotificationCategory;
import com.forward.hrms.notification.enums.NotificationPriority;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "NotificationDto", description = "A single bell-feed entry for a user.")
public record NotificationDto(
        @Schema(description = "Internal id.", example = "10421") long id,
        @Schema(description = "Recipient user id.", example = "42") long userId,
        @Schema(description = "Canonical type, e.g. LEAVE_APPROVED.", example = "LEAVE_APPROVED") String type,
        @Schema(description = "Rendered title.") String title,
        @Schema(description = "Rendered body.", nullable = true) String body,
        @Schema(description = "Deep link path the web app navigates to.", nullable = true) String link,
        @Schema(description = "Opaque JSON payload.", nullable = true) String payload,
        @Schema(description = "Coarse grouping for the bell UI.", nullable = true) NotificationCategory category,
        @Schema(description = "Priority hint; defaults to NORMAL.") NotificationPriority priority,
        @Schema(description = "When the user marked it read; null if unread.", nullable = true) LocalDateTime readAt,
        @Schema(description = "When the row was created.") LocalDateTime createdAt
) {
    public static NotificationDto of(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getUserId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getLink(),
                n.getPayload(),
                n.getCategory(),
                n.getPriority(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}

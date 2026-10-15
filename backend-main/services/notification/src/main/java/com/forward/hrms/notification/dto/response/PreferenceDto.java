package com.forward.hrms.notification.dto.response;

import com.forward.hrms.notification.entity.NotificationPreference;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PreferenceDto", description = "Per-user-per-type opt-out toggle.")
public record PreferenceDto(
        @Schema(description = "Notification type.", example = "LEAVE_APPROVED") String type,
        @Schema(description = "True if the user wants this type delivered.") boolean enabled
) {
    public static PreferenceDto of(NotificationPreference p) {
        return new PreferenceDto(p.getType(), p.isEnabled());
    }
}

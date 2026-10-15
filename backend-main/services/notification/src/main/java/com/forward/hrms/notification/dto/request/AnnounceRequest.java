package com.forward.hrms.notification.dto.request;

import com.forward.hrms.notification.enums.NotificationPriority;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "AnnounceRequest",
        description = "Admin broadcast. The list of recipient user ids is resolved by the caller — typically "
                + "the BFF's department/role-based picker. The notification service just fans out N rows + N publishes.")
public record AnnounceRequest(
        @Schema(description = "Recipient user ids.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty List<Long> userIds,

        @Schema(description = "Title shown to recipients.", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String title,

        @Schema(description = "Body shown to recipients.")
        String body,

        @Schema(description = "Optional deep link.", maxLength = 512)
        @Size(max = 512) String link,

        @Schema(description = "Priority hint; defaults to NORMAL.")
        NotificationPriority priority
) {}

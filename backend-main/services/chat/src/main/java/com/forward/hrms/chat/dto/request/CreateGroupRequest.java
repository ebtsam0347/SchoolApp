package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "CreateGroupRequest", description = "Create an ad-hoc group conversation. Caller becomes OWNER.")
public record CreateGroupRequest(
        @Schema(description = "Group title shown in the conversation list.", example = "Q3 launch room",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "Initial member user ids (excluding caller — caller is auto-added as OWNER).",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty List<Long> memberUserIds
) {}

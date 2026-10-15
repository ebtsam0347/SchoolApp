package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(name = "AddMemberRequest", description = "Add a member to a group conversation.")
public record AddMemberRequest(
        @Schema(description = "User id to add.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long userId
) {}

package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(name = "CreateDmRequest", description = "Get-or-create a 1:1 DM with another employee.")
public record CreateDmRequest(
        @Schema(description = "User id of the other participant.", example = "203", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long otherUserId
) {}

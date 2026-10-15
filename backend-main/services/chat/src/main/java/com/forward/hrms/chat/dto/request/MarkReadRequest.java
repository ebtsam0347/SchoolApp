package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(name = "MarkReadRequest", description = "Update my last-read pointer for a conversation.")
public record MarkReadRequest(
        @Schema(description = "Highest message id I've read in this conversation.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Long lastReadMessageId
) {}

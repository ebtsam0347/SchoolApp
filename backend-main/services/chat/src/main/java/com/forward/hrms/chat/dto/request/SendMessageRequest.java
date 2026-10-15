package com.forward.hrms.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "SendMessageRequest",
        description = "Send a message to a conversation. At least one of body/attachments must be present.")
public record SendMessageRequest(
        @Schema(description = "Message body. Empty string allowed if at least one attachment is present.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 8000) String body,

        @Schema(description = "User ids the client tagged with @mentions. Server validates each is a member.",
                example = "[203, 204]", nullable = true)
        List<Long> mentionedUserIds,

        @Schema(description = "Attachments to attach to this message. Each must already be uploaded via /chat/attachments/upload-url.",
                nullable = true)
        @Valid List<AttachmentRef> attachments
) {}

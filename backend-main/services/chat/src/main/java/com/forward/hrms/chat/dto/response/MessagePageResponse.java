package com.forward.hrms.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "MessagePageResponse",
        description = "A page of messages for a conversation, newest first. Use the lowest id as `beforeId` to fetch the next older page.")
public record MessagePageResponse(
        List<MessageDto> items,
        @Schema(description = "True when more older messages exist before the lowest id in `items`.") boolean hasMore
) {}

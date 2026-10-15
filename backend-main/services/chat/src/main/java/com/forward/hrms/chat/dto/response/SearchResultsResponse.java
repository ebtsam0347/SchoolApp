package com.forward.hrms.chat.dto.response;

import com.forward.hrms.chat.enums.ConversationType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SearchResultsResponse",
        description = "Search hits inside a single conversation. The conversation header is embedded so " +
                "the FE can render thread title + type without an extra fetch.")
public record SearchResultsResponse(
        @Schema(description = "Conversation header for the searched thread.")
        ConversationHeader conversation,
        @Schema(description = "Matching messages, newest-first within the configured limit.")
        List<MessageDto> messages
) {
    @Schema(name = "SearchConversationHeader",
            description = "Lightweight conversation identity — id, title, type — for search-result grouping.")
    public record ConversationHeader(
            long id,
            ConversationType type,
            @Schema(nullable = true, description = "Null for DMs (no group title).") String title
    ) {}
}

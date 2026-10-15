package com.forward.hrms.bff.dto.response;

import com.forward.hrms.chat.dto.response.ConversationDto;
import com.forward.hrms.chat.dto.response.PresenceDto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Cold-load shape for the chat surface. Bundles the conversation list, " +
        "identity resolution for every member, and a presence snapshot — the first paint of the " +
        "chat UI now needs one round-trip instead of ~5+N.")
public record ChatInboxResponse(
        @Schema(description = "Caller's conversations (DMs, groups, broadcast).")
        List<ConversationDto> conversations,
        @Schema(description = "userId → identity, for every member of every conversation. " +
                "Null avatar for admin-only users with no employee row.")
        Map<Long, UserIdentitySummary> resolved,
        @Schema(description = "Presence snapshot for the same set of userIds.")
        List<PresenceDto> presence
) {}

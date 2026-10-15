package com.forward.hrms.chat.dto.response;

import com.forward.hrms.chat.enums.ConversationType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(name = "ConversationDto", description = "Conversation header. Members and last-message preview included.")
public record ConversationDto(
        long id,
        ConversationType type,
        @Schema(nullable = true) String title,
        long createdByUser,
        @Schema(nullable = true) Long lastMessageId,
        @Schema(nullable = true) LocalDateTime lastMessageAt,
        List<Long> memberUserIds,
        long unreadCount,
        @Schema(description = "Per-member read cursors: userId → lastReadMessageId. Empty for BROADCAST " +
                "(no member rows; ✓✓ is noisy on org-wide channels). Lets the FE draw `seen by` / blue " +
                "ticks without an extra fetch.")
        Map<Long, Long> lastReadByUser
) {}

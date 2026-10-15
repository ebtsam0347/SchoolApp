package com.forward.hrms.chat.dto.response;

import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.dto.response.ConversationDtoMapper;

import java.util.List;
import java.util.Map;

public final class ConversationDtoMapper {

    private ConversationDtoMapper() {}

    public static ConversationDto of(Conversation c, List<Long> memberUserIds, long unreadCount,
                                     Map<Long, Long> lastReadByUser) {
        return new ConversationDto(
                c.getId(),
                c.getType(),
                c.getTitle(),
                c.getCreatedByUser(),
                c.getLastMessageId(),
                c.getLastMessageAt(),
                memberUserIds,
                unreadCount,
                lastReadByUser == null ? Map.of() : lastReadByUser);
    }
}

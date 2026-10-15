package com.forward.hrms.chat.service;

import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.enums.ConversationType;
import com.forward.hrms.commons.core.context.CurrentUserHolder;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transient typing-indicator pub/sub. No DB writes — purely a Redis hint
 * that the SPA renders for ~5 seconds, then auto-clears unless refreshed.
 */
@Service
@RequiredArgsConstructor
public class TypingService {

    private final ConversationService conversationService;
    private final ChatPublisher publisher;

    public void typing(long callerUserId, long conversationId, boolean stopped) {
        Conversation c = conversationService.requireMember(callerUserId, conversationId);
        // BROADCAST is a one-way channel — typing indicators don't make sense.
        if (c.getType() == ConversationType.BROADCAST) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId);
        data.put("userId", callerUserId);
        data.put("userName", CurrentUserHolder.usernameOrSystem());
        data.put("stopped", stopped);

        List<Long> recipients = conversationService.memberUserIds(conversationId).stream()
                .filter(uid -> uid != callerUserId)
                .toList();
        publisher.publishToUsers(recipients, "chat.typing", data);
    }
}

package com.forward.hrms.chat.service;

import com.forward.hrms.chat.config.ChatProperties;
import com.forward.hrms.chat.dto.response.AttachmentDto;
import com.forward.hrms.chat.dto.response.MessageDto;
import com.forward.hrms.chat.dto.response.SearchResultsResponse;
import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.entity.Message;
import com.forward.hrms.chat.enums.ConversationType;
import com.forward.hrms.chat.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Member-scoped full-text search inside a single conversation. Backed by
 * the {@code body_tsv} GIN index in chat schema; queries go through
 * {@code plainto_tsquery('simple', q)} so the client doesn't need lexer
 * syntax.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final MessageRepository messages;
    private final ConversationService conversationService;
    private final AttachmentService attachments;
    private final MessageService messageService;
    private final ChatProperties props;

    @Transactional(readOnly = true)
    public SearchResultsResponse search(long callerUserId, long conversationId, String q, int limit) {
        Conversation c = conversationService.require(conversationId);
        if (c.getType() != ConversationType.BROADCAST) {
            conversationService.requireMember(callerUserId, conversationId);
        }
        SearchResultsResponse.ConversationHeader header =
                new SearchResultsResponse.ConversationHeader(c.getId(), c.getType(), c.getTitle());
        if (q == null || q.isBlank()) return new SearchResultsResponse(header, List.of());
        int safeLimit = Math.min(Math.max(limit, 1), props.messagePageMax());

        List<Message> rows = messages.searchInConversation(conversationId, q, safeLimit);
        if (rows.isEmpty()) return new SearchResultsResponse(header, List.of());

        List<Long> ids = new ArrayList<>(rows.size());
        for (Message m : rows) ids.add(m.getId());
        Map<Long, List<AttachmentDto>> attachmentsByMsg = attachments.dtosByMessageIds(ids);
        Map<Long, List<Long>> mentionsByMsg = messageService.mentionsByMessageIds(ids);

        List<MessageDto> out = new ArrayList<>(rows.size());
        for (Message m : rows) {
            out.add(MessageDto.of(m,
                    attachmentsByMsg.getOrDefault(m.getId(), List.of()),
                    mentionsByMsg.getOrDefault(m.getId(), List.of())));
        }
        return new SearchResultsResponse(header, out);
    }
}

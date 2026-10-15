package com.forward.hrms.chat.service;

import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.entity.Message;
import com.forward.hrms.chat.entity.MessageDelivery;
import com.forward.hrms.chat.entity.MessageDeliveryId;
import com.forward.hrms.chat.enums.ChatErrorCode;
import com.forward.hrms.chat.enums.ConversationType;
import com.forward.hrms.chat.repository.ConversationMemberRepository;
import com.forward.hrms.chat.repository.ConversationRepository;
import com.forward.hrms.chat.repository.MessageDeliveryRepository;
import com.forward.hrms.chat.repository.MessageRepository;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.tx.Tx;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-recipient delivery acknowledgements. Inserted by the BFF after a
 * successful SSE forward; idempotent (PK conflict → no-op). On every
 * fresh delivery, publishes {@code chat.message.delivered} to the
 * sender's Redis channel so their UI flips ✓ → ✓✓.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final MessageDeliveryRepository deliveries;
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final ConversationService conversationService;
    private final ChatPublisher publisher;

    @Transactional
    public void markDelivered(long callerUserId, long messageId) {
        Message m = messages.findById(messageId)
                .orElseThrow(() -> new NotFoundError(ChatErrorCode.MESSAGE_NOT_FOUND, "message not found"));

        // Senders can't mark their own message as delivered to themselves.
        if (m.getSenderUserId() == callerUserId) return;

        // §7.chat — caller must be a member of this conversation (or the
        // conversation is BROADCAST, which is org-wide visible).
        Conversation c = conversations.findById(m.getConversationId())
                .orElseThrow(() -> new NotFoundError(ChatErrorCode.CONVERSATION_NOT_FOUND, "conversation not found"));
        if (c.getType() != ConversationType.BROADCAST
                && !members.existsByIdConversationIdAndIdUserId(m.getConversationId(), callerUserId)) {
            throw new ForbiddenError(ChatErrorCode.NOT_A_MEMBER, "not a member of this conversation");
        }

        MessageDeliveryId id = new MessageDeliveryId(messageId, callerUserId);
        if (deliveries.existsById(id)) return;
        LocalDateTime now = LocalDateTime.now();
        MessageDelivery d = new MessageDelivery();
        d.setId(id);
        d.setDeliveredAt(now);
        try {
            deliveries.save(d);
        } catch (DataIntegrityViolationException race) {
            // Another concurrent ACK won — already delivered.
            return;
        }

        long total = deliveries.countByIdMessageId(messageId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", m.getConversationId());
        data.put("messageId", messageId);
        data.put("deliveredByUserId", callerUserId);
        data.put("deliveredAt", now.toString());
        data.put("totalDelivered", total);
        // Publish only after the delivery row commits — a rollback
        // would otherwise flip ✓ → ✓✓ on the sender's UI for an
        // ack that didn't persist.
        long senderId = m.getSenderUserId();
        Tx.afterCommit(() -> publisher.publishToUser(senderId, "chat.message.delivered", data));
    }
}

package com.forward.hrms.chat.events;

import com.forward.hrms.chat.enums.ConversationType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbound event payload shapes — the {@code payload} field of an
 * {@link com.forward.hrms.commons.kafka.event.EventEnvelope EventEnvelope}.
 * Topic: {@code chat.events}. Consumed by notification-service to drop
 * bell entries on @mentions / 1:1 DMs.
 */
public final class ChatEventPayloads {

    private ChatEventPayloads() {}

    public static final String TYPE_MESSAGE_SENT = "chat.message.sent";
    public static final String TYPE_MESSAGE_EDITED = "chat.message.edited";
    public static final String TYPE_MESSAGE_DELETED = "chat.message.deleted";

    public record MessageSent(
            long conversationId,
            ConversationType conversationType,
            long messageId,
            long senderUserId,
            String senderUsername,
            /** Empty for BROADCAST — recipients are inferred as "all active users" by consumers. */
            List<Long> recipientUserIds,
            List<Long> mentionedUserIds,
            String preview,
            LocalDateTime createdAt
    ) {}

    public record MessageEdited(
            long conversationId,
            long messageId,
            long senderUserId,
            LocalDateTime editedAt
    ) {}

    public record MessageDeleted(
            long conversationId,
            long messageId,
            long senderUserId,
            LocalDateTime deletedAt,
            String deleteReason
    ) {}
}

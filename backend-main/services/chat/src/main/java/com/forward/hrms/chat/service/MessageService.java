package com.forward.hrms.chat.service;

import com.forward.hrms.chat.config.ChatProperties;
import com.forward.hrms.chat.dto.request.EditMessageRequest;
import com.forward.hrms.chat.dto.request.SendMessageRequest;
import com.forward.hrms.chat.dto.response.AttachmentDto;
import com.forward.hrms.chat.dto.response.MessageDto;
import com.forward.hrms.chat.dto.response.MessagePageResponse;
import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.entity.Message;
import com.forward.hrms.chat.entity.MessageAttachment;
import com.forward.hrms.chat.entity.MessageMention;
import com.forward.hrms.chat.entity.MessageMentionId;
import com.forward.hrms.chat.enums.ChatErrorCode;
import com.forward.hrms.chat.enums.ConversationType;
import com.forward.hrms.chat.enums.DeleteReason;
import com.forward.hrms.chat.events.ChatEventPayloads;
import com.forward.hrms.chat.repository.ConversationRepository;
import com.forward.hrms.chat.repository.MessageMentionRepository;
import com.forward.hrms.chat.repository.MessageRepository;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.event.KafkaTopics;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Send/edit/delete + paginated message reads. The send path is a single
 * transaction that:
 *
 * <ol>
 *   <li>persists the message,</li>
 *   <li>bumps the conversation's last-message denormalized columns,</li>
 *   <li>emits a {@code chat.message.sent} domain event via the outbox
 *       (Kafka, durable),</li>
 *   <li>emits an audit event (metadata only — no body) onto
 *       {@code chat.audit},</li>
 *   <li>and registers an {@code afterCommit
 *       afterCommit} hook to publish {@code chat.message} to each
 *       recipient's Redis pub/sub channel for live SSE delivery.</li>
 * </ol>
 *
 * <p>Edit/delete follow the same shape with their respective event
 * types so SPA caches and bell badges stay coherent across all open
 * tabs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int PREVIEW_CHARS = 140;
    private static final String SERVICE_NAME = "chat";

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ConversationService conversationService;
    private final AttachmentService attachments;
    private final MessageMentionRepository mentions;
    private final ChatProperties props;
    private final ChatPublisher publisher;
    private final Outbox outbox;
    private final AuditEmitter audit;

    /* ------------------------------------------------------------------ */
    /* Send                                                                */
    /* ------------------------------------------------------------------ */

    @Transactional
    public MessageDto send(long callerUserId, long conversationId, SendMessageRequest req) {
        return sendInternal(callerUserId, conversationId, req, false);
    }

    /**
     * Broadcast variant: caller posts into the org-wide BROADCAST conversation.
     * Permission is enforced upstream ({@code chat.broadcast.write}).
     */
    @Transactional
    public MessageDto sendBroadcast(long callerUserId, long broadcastConversationId, SendMessageRequest req) {
        return sendInternal(callerUserId, broadcastConversationId, req, true);
    }

    private MessageDto sendInternal(long callerUserId, long conversationId,
                                     SendMessageRequest req, boolean broadcastPath) {
        Conversation c = broadcastPath
                ? conversationService.require(conversationId)
                : conversationService.requireMember(callerUserId, conversationId);
        if (broadcastPath && c.getType() != ConversationType.BROADCAST) {
            throw new ConflictError(ChatErrorCode.INVALID_DELETE_TARGET,
                    "broadcast endpoint can only post to the BROADCAST conversation");
        }

        boolean hasBody = req.body() != null && !req.body().isBlank();
        boolean hasAttachments = req.attachments() != null && !req.attachments().isEmpty();
        if (!hasBody && !hasAttachments) {
            throw new ConflictError(ChatErrorCode.MESSAGE_BODY_REQUIRED,
                    "message must have a body, attachments, or both");
        }
        // §7.chat — bound the body + attachment count. Bodies bigger than
        // the cap waste log + audit space; attachment counts >10 are
        // almost always a UI mis-construction.
        if (hasBody && req.body().length() > props.messageBodyMaxChars()) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    ChatErrorCode.MESSAGE_BODY_TOO_LONG,
                    "message body exceeds " + props.messageBodyMaxChars() + " chars");
        }
        if (hasAttachments && req.attachments().size() > props.messageAttachmentsMax()) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    ChatErrorCode.ATTACHMENT_COUNT_EXCEEDED,
                    "message has more than " + props.messageAttachmentsMax() + " attachments");
        }

        Message m = new Message();
        m.setConversationId(conversationId);
        m.setSenderUserId(callerUserId);
        m.setBody(hasBody ? req.body() : "");
        Message saved = messages.save(m);

        // Attachments persisted in the same tx.
        List<MessageAttachment> attachmentRows = attachments.persist(saved, req.attachments());
        List<AttachmentDto> attachmentDtos = attachmentRows.isEmpty()
                ? List.of()
                : attachments.dtosFor(saved.getId());

        // Mentions: validated against members for DM/GROUP. For BROADCAST any
        // user id is accepted — the audience is "everyone".
        List<Long> mentionedUsers = persistMentions(saved, conversationId, c.getType(), req.mentionedUserIds());

        c.setLastMessageId(saved.getId());
        c.setLastMessageAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now());
        conversations.save(c);

        List<Long> recipients = c.getType() == ConversationType.BROADCAST
                ? List.of()
                : conversationService.memberUserIds(conversationId).stream()
                        .filter(uid -> uid != callerUserId).toList();

        // Domain event for notification-service. Audit topic carries
        // metadata only (no body) — bodies stay in our DB.
        ChatEventPayloads.MessageSent payload = new ChatEventPayloads.MessageSent(
                conversationId,
                c.getType(),
                saved.getId(),
                callerUserId,
                CurrentUserHolder.usernameOrSystem(),
                recipients,
                mentionedUsers,
                preview(saved.getBody()),
                saved.getCreatedAt());
        outbox.publish(KafkaTopics.events(SERVICE_NAME),
                "conv:" + conversationId,
                ChatEventPayloads.TYPE_MESSAGE_SENT,
                payload);

        audit.emit("chat_message", String.valueOf(saved.getId()), "sent",
                null,
                Map.of(
                        "conversationId", conversationId,
                        "conversationType", c.getType().name(),
                        "senderUserId", callerUserId,
                        "bodyLength", saved.getBody().length(),
                        "mentions", mentionedUsers.size()),
                false);

        // Live push: per-user channels for DM/GROUP, single global channel
        // for BROADCAST so a single publish reaches every connected user.
        Map<String, Object> envelope = baseDeliveryEnvelope(saved, c.getType(), attachmentDtos, mentionedUsers);
        if (c.getType() == ConversationType.BROADCAST) {
            afterCommitBroadcastFanOut("chat.message", envelope);
        } else {
            afterCommitFanOut(recipients, "chat.message", envelope);
        }

        return MessageDto.of(saved, attachmentDtos);
    }

    private List<Long> persistMentions(Message message, long conversationId,
                                        ConversationType convType, List<Long> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        List<Long> distinct = requested.stream()
                .filter(java.util.Objects::nonNull)
                .filter(uid -> uid > 0)
                .distinct()
                .toList();
        if (distinct.isEmpty()) return List.of();

        // For DM/GROUP, mentions must be members of the conversation.
        // For BROADCAST, any user id is accepted (audience is everyone).
        if (convType != ConversationType.BROADCAST) {
            java.util.Set<Long> memberSet = new java.util.HashSet<>(conversationService.memberUserIds(conversationId));
            for (Long uid : distinct) {
                if (!memberSet.contains(uid)) {
                    throw new ConflictError(ChatErrorCode.NOT_A_MEMBER,
                            "mentioned user " + uid + " is not a member of this conversation");
                }
            }
        }

        for (Long uid : distinct) {
            MessageMention mm = new MessageMention();
            mm.setId(new MessageMentionId(message.getId(), uid));
            mentions.save(mm);
        }
        return distinct;
    }

    /* ------------------------------------------------------------------ */
    /* Edit                                                                */
    /* ------------------------------------------------------------------ */

    @Transactional
    public MessageDto edit(long callerUserId, long messageId, EditMessageRequest req) {
        Message m = require(messageId);
        if (m.getSenderUserId() != callerUserId) {
            throw new ForbiddenError(ChatErrorCode.MESSAGE_NOT_OWNED, "can only edit your own messages");
        }
        if (m.isDeleted()) {
            throw new ConflictError(ChatErrorCode.INVALID_DELETE_TARGET, "cannot edit a deleted message");
        }
        LocalDateTime created = m.getCreatedAt() != null ? m.getCreatedAt() : LocalDateTime.now();
        if (LocalDateTime.now().isAfter(created.plus(props.editWindow()))) {
            throw new ConflictError(ChatErrorCode.MESSAGE_EDIT_WINDOW_EXPIRED,
                    "edit window of " + props.editWindow().toMinutes() + " min has passed");
        }

        m.setBody(req.body());
        m.setEditedAt(LocalDateTime.now());
        Message saved = messages.save(m);

        outbox.publish(KafkaTopics.events(SERVICE_NAME),
                "conv:" + m.getConversationId(),
                ChatEventPayloads.TYPE_MESSAGE_EDITED,
                new ChatEventPayloads.MessageEdited(
                        m.getConversationId(),
                        saved.getId(),
                        callerUserId,
                        saved.getEditedAt()));

        audit.emit("chat_message", String.valueOf(saved.getId()), "edited",
                null, Map.of("conversationId", saved.getConversationId(),
                        "bodyLength", saved.getBody().length()), false);

        Map<String, Object> envelope = Map.of(
                "conversationId", saved.getConversationId(),
                "messageId", saved.getId(),
                "senderUserId", saved.getSenderUserId(),
                "body", saved.getBody(),
                "editedAt", saved.getEditedAt().toString());
        // Look up the conversation for type-aware fanout (broadcast vs members).
        Conversation conv = conversationService.require(saved.getConversationId());
        if (conv.getType() == ConversationType.BROADCAST) {
            afterCommitBroadcastFanOut("chat.message.edited", envelope);
        } else {
            afterCommitFanOut(conversationService.memberUserIds(saved.getConversationId()),
                    "chat.message.edited", envelope);
        }
        return MessageDto.of(saved, attachments.dtosFor(saved.getId()));
    }

    /* ------------------------------------------------------------------ */
    /* Delete                                                              */
    /* ------------------------------------------------------------------ */

    @Transactional
    public void deleteOwn(long callerUserId, long messageId) {
        Message m = require(messageId);
        if (m.getSenderUserId() != callerUserId) {
            throw new ForbiddenError(ChatErrorCode.MESSAGE_NOT_OWNED, "can only delete your own messages");
        }
        applySoftDelete(m, callerUserId, DeleteReason.SELF);
    }

    @Transactional
    public void deleteAsAdmin(long callerUserId, long messageId) {
        Message m = require(messageId);
        applySoftDelete(m, callerUserId, DeleteReason.ADMIN);
    }

    private void applySoftDelete(Message m, long actorUserId, DeleteReason reason) {
        if (m.isDeleted()) {
            throw new ConflictError(ChatErrorCode.INVALID_DELETE_TARGET, "message already deleted");
        }
        LocalDateTime now = LocalDateTime.now();
        m.setDeletedAt(now);
        m.setDeletedByUser(actorUserId);
        m.setDeleteReason(reason);
        Message saved = messages.save(m);

        // If this was the last message in the conversation, recompute.
        Conversation c = conversationService.require(saved.getConversationId());
        if (c.getLastMessageId() != null && c.getLastMessageId().equals(saved.getId())) {
            recomputeLastMessage(c);
            conversations.save(c);
        }

        outbox.publish(KafkaTopics.events(SERVICE_NAME),
                "conv:" + saved.getConversationId(),
                ChatEventPayloads.TYPE_MESSAGE_DELETED,
                new ChatEventPayloads.MessageDeleted(
                        saved.getConversationId(),
                        saved.getId(),
                        saved.getSenderUserId(),
                        saved.getDeletedAt(),
                        reason.name()));

        audit.emit("chat_message", String.valueOf(saved.getId()),
                reason == DeleteReason.SELF ? "deleted" : "admin_deleted",
                null,
                Map.of(
                        "conversationId", saved.getConversationId(),
                        "deletedByUser", actorUserId,
                        "reason", reason.name()),
                reason == DeleteReason.ADMIN);

        Map<String, Object> envelope = Map.of(
                "conversationId", saved.getConversationId(),
                "messageId", saved.getId(),
                "senderUserId", saved.getSenderUserId(),
                "deletedAt", saved.getDeletedAt().toString(),
                "reason", reason.name());
        if (c.getType() == ConversationType.BROADCAST) {
            afterCommitBroadcastFanOut("chat.message.deleted", envelope);
        } else {
            afterCommitFanOut(conversationService.memberUserIds(saved.getConversationId()),
                    "chat.message.deleted", envelope);
        }
    }

    private void recomputeLastMessage(Conversation c) {
        List<Message> latest = messages.findLatestNonDeleted(c.getId(), PageRequest.of(0, 1));
        if (latest.isEmpty()) {
            c.setLastMessageId(null);
            c.setLastMessageAt(null);
        } else {
            Message m = latest.get(0);
            c.setLastMessageId(m.getId());
            c.setLastMessageAt(m.getCreatedAt());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Reads                                                               */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public MessagePageResponse page(long callerUserId, long conversationId, Long beforeId, int limit) {
        Conversation c = conversationService.require(conversationId);
        if (c.getType() != ConversationType.BROADCAST) {
            conversationService.requireMember(callerUserId, conversationId);
        }
        int safeLimit = Math.min(Math.max(limit, 1), props.messagePageMax());
        long before = beforeId == null ? Long.MAX_VALUE : beforeId;
        // pageNewestFirst sorts already
        List<Message> rows = messages.pageNewestFirst(conversationId, before, PageRequest.of(0, safeLimit + 1));

        boolean hasMore = rows.size() > safeLimit;
        if (hasMore) rows = rows.subList(0, safeLimit);

        List<Long> ids = new ArrayList<>(rows.size());
        for (Message m : rows) ids.add(m.getId());
        Map<Long, List<AttachmentDto>> attachmentsByMsg = attachments.dtosByMessageIds(ids);
        Map<Long, List<Long>> mentionsByMsg = mentionsByMessageIds(ids);

        List<MessageDto> items = new ArrayList<>(rows.size());
        for (Message m : rows) {
            items.add(MessageDto.of(m,
                    attachmentsByMsg.getOrDefault(m.getId(), List.of()),
                    mentionsByMsg.getOrDefault(m.getId(), List.of())));
        }
        return new MessagePageResponse(items, hasMore);
    }

    Map<Long, List<Long>> mentionsByMessageIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, List<Long>> grouped = new java.util.HashMap<>();
        for (var row : mentions.findAllByMessageIdIn(ids)) {
            grouped.computeIfAbsent(row.getMessageId(), k -> new ArrayList<>()).add(row.getMentionedUser());
        }
        return grouped;
    }

    /**
     * Admin variant of {@link #page} — skips the member check. Caller must
     * hold {@code chat.conversations.read}; the controller enforces it.
     */
    @Transactional(readOnly = true)
    public MessagePageResponse adminPage(long conversationId, Long beforeId, int limit) {
        conversationService.require(conversationId);
        int safeLimit = Math.min(Math.max(limit, 1), props.messagePageMax());
        long before = beforeId == null ? Long.MAX_VALUE : beforeId;
        List<Message> rows = messages.pageNewestFirst(conversationId, before, PageRequest.of(0, safeLimit + 1));

        boolean hasMore = rows.size() > safeLimit;
        if (hasMore) rows = rows.subList(0, safeLimit);

        List<Long> ids = new ArrayList<>(rows.size());
        for (Message m : rows) ids.add(m.getId());
        Map<Long, List<AttachmentDto>> attachmentsByMsg = attachments.dtosByMessageIds(ids);
        Map<Long, List<Long>> mentionsByMsg = mentionsByMessageIds(ids);

        List<MessageDto> items = new ArrayList<>(rows.size());
        for (Message m : rows) {
            items.add(MessageDto.of(m,
                    attachmentsByMsg.getOrDefault(m.getId(), List.of()),
                    mentionsByMsg.getOrDefault(m.getId(), List.of())));
        }
        return new MessagePageResponse(items, hasMore);
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private Message require(long id) {
        return messages.findById(id)
                .orElseThrow(() -> new NotFoundError(ChatErrorCode.MESSAGE_NOT_FOUND, "message not found"));
    }

    private static Map<String, Object> baseDeliveryEnvelope(Message m, ConversationType type,
                                                             List<AttachmentDto> attachmentDtos,
                                                             List<Long> mentionedUserIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", m.getConversationId());
        data.put("conversationType", type.name());
        data.put("messageId", m.getId());
        data.put("senderUserId", m.getSenderUserId());
        data.put("senderUsername", m.getCreatedBy());
        data.put("body", m.getBody());
        data.put("createdAt", (m.getCreatedAt() == null ? LocalDateTime.now() : m.getCreatedAt()).toString());
        if (attachmentDtos != null && !attachmentDtos.isEmpty()) {
            data.put("attachments", attachmentDtos);
        }
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            data.put("mentionedUserIds", mentionedUserIds);
        }
        return data;
    }

    private void afterCommitFanOut(List<Long> recipients, String type, Map<String, Object> data) {
        if (recipients == null || recipients.isEmpty()) return;
        com.forward.hrms.commons.core.tx.Tx.afterCommit(() -> {
            try {
                publisher.publishToUsers(recipients, type, data);
            } catch (RuntimeException ex) {
                log.warn("Redis publish ({}) failed for {} recipients: {}",
                        type, recipients.size(), ex.getMessage());
            }
        });
    }

    private void afterCommitBroadcastFanOut(String type, Map<String, Object> data) {
        com.forward.hrms.commons.core.tx.Tx.afterCommit(() -> {
            try {
                publisher.publishToChannel(ChatPublisher.BROADCAST_CHANNEL, type, data);
            } catch (RuntimeException ex) {
                log.warn("Redis broadcast publish ({}) failed: {}", type, ex.getMessage());
            }
        });
    }

    private static String preview(String body) {
        if (body == null) return "";
        String trimmed = body.strip();
        return trimmed.length() <= PREVIEW_CHARS
                ? trimmed
                : trimmed.substring(0, PREVIEW_CHARS - 1) + "…";
    }
}

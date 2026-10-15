package com.forward.hrms.chat.service;

import com.forward.hrms.chat.config.ChatProperties;
import com.forward.hrms.chat.dto.response.ConversationDto;
import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.entity.ConversationMember;
import com.forward.hrms.chat.entity.ConversationMemberId;
import com.forward.hrms.chat.entity.ConversationRead;
import com.forward.hrms.chat.entity.ConversationReadId;
import com.forward.hrms.chat.enums.ChatErrorCode;
import com.forward.hrms.chat.enums.ConversationType;
import com.forward.hrms.chat.enums.MemberRole;
import com.forward.hrms.chat.repository.ConversationMemberRepository;
import com.forward.hrms.chat.repository.ConversationReadRepository;
import com.forward.hrms.chat.repository.ConversationRepository;
import com.forward.hrms.chat.repository.MessageRepository;
import com.forward.hrms.chat.dto.response.ConversationDtoMapper;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final ConversationReadRepository reads;
    private final MessageRepository messages;
    private final ChatProperties props;
    private final AuditEmitter audit;
    private final ChatPublisher publisher;

    /* ------------------------------------------------------------------ */
    /* DM                                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Get-or-create a DM between caller and {@code otherUserId}. Race-safe:
     * the {@code uk_conversations_dm_key} partial unique index makes
     * concurrent creators converge on the same row.
     */
    @Transactional
    public Conversation getOrCreateDm(long callerUserId, long otherUserId) {
        if (callerUserId == otherUserId) {
            throw new ConflictError(ChatErrorCode.DM_SELF_NOT_ALLOWED, "cannot DM yourself");
        }
        String key = Conversation.dmKey(callerUserId, otherUserId);

        return conversations.findByDmKey(key).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setType(ConversationType.DM);
            c.setCreatedByUser(callerUserId);
            c.setDmKey(key);
            try {
                Conversation saved = conversations.save(c);
                addMember(saved.getId(), callerUserId, MemberRole.MEMBER);
                addMember(saved.getId(), otherUserId, MemberRole.MEMBER);
                audit.emit("chat_conversation", String.valueOf(saved.getId()), "created",
                        null, Map.of("type", "DM", "members", List.of(callerUserId, otherUserId)), false);
                return saved;
            } catch (DataIntegrityViolationException race) {
                // Another caller raced us. Re-read and use theirs.
                return conversations.findByDmKey(key)
                        .orElseThrow(() -> race);
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Group                                                               */
    /* ------------------------------------------------------------------ */

    @Transactional
    public Conversation createGroup(long callerUserId, String title, List<Long> memberUserIds) {
        if (title == null || title.isBlank()) {
            throw new ConflictError(ChatErrorCode.GROUP_TITLE_REQUIRED, "group title is required");
        }
        if (memberUserIds == null || memberUserIds.isEmpty()) {
            throw new ConflictError(ChatErrorCode.GROUP_MEMBERS_REQUIRED, "at least one member required");
        }

        // Caller is OWNER — always added, regardless of whether the client
        // included them in memberUserIds.
        Set<Long> distinct = new HashSet<>(memberUserIds);
        distinct.add(callerUserId);
        if (distinct.size() > props.groupMemberCap()) {
            throw new ConflictError(ChatErrorCode.GROUP_MEMBER_CAP_EXCEEDED,
                    "group exceeds member cap of " + props.groupMemberCap());
        }

        Conversation c = new Conversation();
        c.setType(ConversationType.GROUP);
        c.setTitle(title.trim());
        c.setCreatedByUser(callerUserId);
        Conversation saved = conversations.save(c);

        for (Long uid : distinct) {
            addMember(saved.getId(), uid, uid == callerUserId ? MemberRole.OWNER : MemberRole.MEMBER);
        }
        audit.emit("chat_conversation", String.valueOf(saved.getId()), "created",
                null, Map.of("type", "GROUP", "title", title, "members", new ArrayList<>(distinct)), false);
        return saved;
    }

    @Transactional
    public void addMember(long callerUserId, long conversationId, long userId) {
        Conversation c = requireOwner(callerUserId, conversationId);
        if (c.getType() != ConversationType.GROUP) {
            throw new ConflictError(ChatErrorCode.NOT_AN_OWNER, "only group conversations support add-member");
        }
        if (members.existsByIdConversationIdAndIdUserId(conversationId, userId)) {
            throw new ConflictError(ChatErrorCode.MEMBER_ALREADY_PRESENT, "user is already a member");
        }
        if (members.countByIdConversationId(conversationId) >= props.groupMemberCap()) {
            throw new ConflictError(ChatErrorCode.GROUP_MEMBER_CAP_EXCEEDED,
                    "group exceeds member cap of " + props.groupMemberCap());
        }
        addMember(conversationId, userId, MemberRole.MEMBER);
        audit.emit("chat_conversation", String.valueOf(conversationId), "member_added",
                null, Map.of("userId", userId), false);
    }

    @Transactional
    public void removeMember(long callerUserId, long conversationId, long userId) {
        Conversation c = require(conversationId);
        if (c.getType() != ConversationType.GROUP) {
            throw new ConflictError(ChatErrorCode.NOT_AN_OWNER, "only group conversations support remove-member");
        }

        ConversationMember target = members.findByIdConversationIdAndIdUserId(conversationId, userId)
                .orElseThrow(() -> new NotFoundError(ChatErrorCode.MEMBER_NOT_PRESENT, "user is not a member"));

        boolean self = callerUserId == userId;
        if (!self) {
            // Owners can remove anyone; non-owners can only remove themselves.
            ConversationMember caller = members.findByIdConversationIdAndIdUserId(conversationId, callerUserId)
                    .orElseThrow(() -> new ForbiddenError(ChatErrorCode.NOT_AN_OWNER, "not a member"));
            if (caller.getRole() != MemberRole.OWNER) {
                throw new ForbiddenError(ChatErrorCode.NOT_AN_OWNER, "owner role required");
            }
        }

        // Don't allow the last OWNER to leave a non-empty group — would
        // leave it without an admin. Member can promote first or another
        // owner can remove them.
        if (target.getRole() == MemberRole.OWNER) {
            long owners = members.countByIdConversationIdAndRole(conversationId, MemberRole.OWNER);
            if (owners <= 1 && members.countByIdConversationId(conversationId) > 1) {
                throw new ConflictError(ChatErrorCode.LAST_OWNER_CANNOT_LEAVE,
                        "cannot remove the last owner of a non-empty group");
            }
        }

        members.delete(target);
        audit.emit("chat_conversation", String.valueOf(conversationId),
                self ? "member_left" : "member_removed",
                null, Map.of("userId", userId), false);
    }

    /* ------------------------------------------------------------------ */
    /* Reads                                                               */
    /* ------------------------------------------------------------------ */

    @Transactional
    public void markRead(long callerUserId, long conversationId, long lastReadMessageId) {
        Conversation c = require(conversationId);
        // BROADCAST is visible to every authenticated user without a
        // conversation_members row, so don't require membership here —
        // matches `get` / `listForUser`.
        if (c.getType() != ConversationType.BROADCAST
                && !members.existsByIdConversationIdAndIdUserId(conversationId, callerUserId)) {
            throw new ForbiddenError(ChatErrorCode.NOT_A_MEMBER, "not a member of this conversation");
        }
        ConversationReadId id = new ConversationReadId(conversationId, callerUserId);
        ConversationRead r = reads.findById(id).orElseGet(() -> {
            ConversationRead fresh = new ConversationRead();
            fresh.setId(id);
            fresh.setLastReadMessageId(0L);
            return fresh;
        });
        // Read pointer only ever moves forward.
        boolean advanced = lastReadMessageId > r.getLastReadMessageId();
        if (advanced) {
            r.setLastReadMessageId(lastReadMessageId);
        }
        r.setUpdatedAt(LocalDateTime.now());
        reads.save(r);

        // Publish chat.read so other members can flip ✓✓ → "Read".
        // Only when the cursor actually advanced — no need to re-publish
        // the same value.
        if (!advanced) return;
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("conversationId", conversationId);
        data.put("userId", callerUserId);
        data.put("lastReadMessageId", lastReadMessageId);
        data.put("updatedAt", r.getUpdatedAt().toString());
        if (c.getType() == ConversationType.BROADCAST) {
            // Don't fan out per-recipient read events for org-wide broadcasts —
            // ✓✓ on a broadcast is noise.
            return;
        }
        List<Long> recipients = members.findUserIdsByConversationId(conversationId).stream()
                .filter(uid -> uid != callerUserId)
                .toList();
        // Publish only after the read-cursor row commits — a rollback
        // would otherwise leave other members showing ✓✓ for a read
        // that never persisted.
        com.forward.hrms.commons.core.tx.Tx.afterCommit(() ->
                publisher.publishToUsers(recipients, "chat.read", data));
    }

    /* ------------------------------------------------------------------ */
    /* Reads — list + lookup                                               */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public List<ConversationDto> listForUser(long userId) {
        List<Long> conversationIds = new ArrayList<>(members.findConversationIdsByUserId(userId));

        // The org-wide BROADCAST is visible to every authenticated user, even
        // though they aren't in conversation_members for it. Splice it in
        // when present.
        conversations.findFirstByType(ConversationType.BROADCAST)
                .map(Conversation::getId)
                .filter(id -> !conversationIds.contains(id))
                .ifPresent(conversationIds::add);

        if (conversationIds.isEmpty()) return List.of();

        Map<Long, Long> lastRead = readMap(userId, conversationIds);
        Map<Long, Map<Long, Long>> readsByConv = readsByConversation(conversationIds);

        List<Conversation> convs = conversations.findAllById(conversationIds);
        // Order by last_message_at desc nulls last
        convs.sort((a, b) -> {
            LocalDateTime ai = a.getLastMessageAt();
            LocalDateTime bi = b.getLastMessageAt();
            if (ai == null && bi == null) return 0;
            if (ai == null) return 1;
            if (bi == null) return -1;
            return bi.compareTo(ai);
        });

        List<ConversationDto> out = new ArrayList<>(convs.size());
        for (Conversation c : convs) {
            List<Long> memberIds = members.findUserIdsByConversationId(c.getId());
            long lr = lastRead.getOrDefault(c.getId(), 0L);
            long unread = messages.countUnread(c.getId(), lr, userId);
            Map<Long, Long> peerReads = c.getType() == ConversationType.BROADCAST
                    ? Map.of()
                    : readsByConv.getOrDefault(c.getId(), Map.of());
            out.add(ConversationDtoMapper.of(c, memberIds, unread, peerReads));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ConversationDto get(long callerUserId, long conversationId) {
        Conversation c = require(conversationId);
        boolean isMember = members.existsByIdConversationIdAndIdUserId(conversationId, callerUserId);
        if (!isMember && c.getType() != ConversationType.BROADCAST) {
            throw new ForbiddenError(ChatErrorCode.NOT_A_MEMBER, "not a member of this conversation");
        }
        List<Long> memberIds = members.findUserIdsByConversationId(conversationId);
        Map<Long, Long> lr = readMap(callerUserId, List.of(conversationId));
        long unread = messages.countUnread(conversationId, lr.getOrDefault(conversationId, 0L), callerUserId);
        Map<Long, Long> peerReads = c.getType() == ConversationType.BROADCAST
                ? Map.of()
                : readsByConversation(List.of(conversationId)).getOrDefault(conversationId, Map.of());
        return ConversationDtoMapper.of(c, memberIds, unread, peerReads);
    }

    /**
     * Admin variant — returns the conversation without enforcing membership.
     * Caller must hold {@code chat.conversations.read}; the controller enforces it.
     * Unread count is reported from {@code targetUserId}'s perspective.
     */
    @Transactional(readOnly = true)
    public ConversationDto adminGet(long targetUserId, long conversationId) {
        Conversation c = require(conversationId);
        List<Long> memberIds = members.findUserIdsByConversationId(conversationId);
        Map<Long, Long> lr = readMap(targetUserId, List.of(conversationId));
        long unread = messages.countUnread(conversationId, lr.getOrDefault(conversationId, 0L), targetUserId);
        Map<Long, Long> peerReads = c.getType() == ConversationType.BROADCAST
                ? Map.of()
                : readsByConversation(List.of(conversationId)).getOrDefault(conversationId, Map.of());
        return ConversationDtoMapper.of(c, memberIds, unread, peerReads);
    }

    /* ------------------------------------------------------------------ */
    /* Helpers (also consumed by MessageService)                           */
    /* ------------------------------------------------------------------ */

    public Conversation require(long conversationId) {
        return conversations.findById(conversationId)
                .orElseThrow(() -> new NotFoundError(ChatErrorCode.CONVERSATION_NOT_FOUND, "conversation not found"));
    }

    public Conversation requireMember(long callerUserId, long conversationId) {
        Conversation c = require(conversationId);
        if (!members.existsByIdConversationIdAndIdUserId(conversationId, callerUserId)) {
            throw new ForbiddenError(ChatErrorCode.NOT_A_MEMBER, "not a member of this conversation");
        }
        return c;
    }

    public Conversation requireOwner(long callerUserId, long conversationId) {
        Conversation c = require(conversationId);
        ConversationMember m = members.findByIdConversationIdAndIdUserId(conversationId, callerUserId)
                .orElseThrow(() -> new ForbiddenError(ChatErrorCode.NOT_A_MEMBER, "not a member"));
        if (m.getRole() != MemberRole.OWNER) {
            throw new ForbiddenError(ChatErrorCode.NOT_AN_OWNER, "owner role required");
        }
        return c;
    }

    public List<Long> memberUserIds(long conversationId) {
        return members.findUserIdsByConversationId(conversationId);
    }

    private void addMember(long conversationId, long userId, MemberRole role) {
        ConversationMember m = new ConversationMember();
        m.setId(new ConversationMemberId(conversationId, userId));
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.now());
        m.setMuted(false);
        members.save(m);
    }

    private Map<Long, Long> readMap(long userId, List<Long> conversationIds) {
        Map<Long, Long> out = new java.util.HashMap<>();
        for (ConversationRead r : reads.findAllByIdUserIdAndIdConversationIdIn(userId, conversationIds)) {
            out.put(r.getId().getConversationId(), r.getLastReadMessageId());
        }
        return out;
    }

    /** convId → (userId → lastReadMessageId). One query for the whole batch. */
    private Map<Long, Map<Long, Long>> readsByConversation(List<Long> conversationIds) {
        Map<Long, Map<Long, Long>> out = new java.util.HashMap<>();
        for (ConversationRead r : reads.findAllByIdConversationIdIn(conversationIds)) {
            out.computeIfAbsent(r.getId().getConversationId(), k -> new java.util.HashMap<>())
                    .put(r.getId().getUserId(), r.getLastReadMessageId());
        }
        return out;
    }
}

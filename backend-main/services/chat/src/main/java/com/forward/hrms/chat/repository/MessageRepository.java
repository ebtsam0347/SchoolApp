package com.forward.hrms.chat.repository;

import com.forward.hrms.chat.entity.Message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Newest-first window for a conversation. Pass {@code beforeId = Long.MAX_VALUE}
     * for the head of the timeline.
     */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
              and m.id < :beforeId
            order by m.id desc
            """)
    List<Message> pageNewestFirst(
            @Param("conversationId") Long conversationId,
            @Param("beforeId") Long beforeId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Latest non-deleted message in a conversation. Used to recompute
     * {@code last_message_id}/{@code last_message_at} after a soft-delete.
     */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
              and m.deletedAt is null
            order by m.id desc
            """)
    List<Message> findLatestNonDeleted(
            @Param("conversationId") Long conversationId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Unread count for a member. We exclude soft-deleted rows AND messages
     * the user themselves sent (you don't count your own messages as
     * unread).
     */
    @Query("""
            select count(m) from Message m
            where m.conversationId = :conversationId
              and m.id > :lastReadMessageId
              and m.deletedAt is null
              and m.senderUserId <> :userId
            """)
    long countUnread(
            @Param("conversationId") Long conversationId,
            @Param("lastReadMessageId") Long lastReadMessageId,
            @Param("userId") Long userId);

    Optional<Message> findFirstByIdAndConversationId(Long id, Long conversationId);

    /**
     * tsvector + GIN scan inside a single conversation. Uses
     * {@code plainto_tsquery('simple', :q)} so the client doesn't need to
     * know lexer syntax — multi-word queries are AND-combined. Soft-deleted
     * rows are excluded.
     */
    @org.springframework.data.jpa.repository.Query(value = """
            select * from chat.messages m
            where m.conversation_id = :conversationId
              and m.deleted_at is null
              and m.body_tsv @@ plainto_tsquery('simple', :q)
            order by m.id desc
            limit :limit
            """, nativeQuery = true)
    List<Message> searchInConversation(
            @Param("conversationId") Long conversationId,
            @Param("q") String q,
            @Param("limit") int limit);
}

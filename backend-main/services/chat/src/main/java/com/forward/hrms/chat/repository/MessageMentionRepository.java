package com.forward.hrms.chat.repository;

import com.forward.hrms.chat.entity.MessageMention;
import com.forward.hrms.chat.entity.MessageMentionId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageMentionRepository extends JpaRepository<MessageMention, MessageMentionId> {

    @Query("select mm.id.mentionedUser from MessageMention mm where mm.id.messageId = :messageId")
    List<Long> findMentionedUsersByMessageId(@Param("messageId") Long messageId);

    @Query("""
            select mm.id.messageId as messageId, mm.id.mentionedUser as mentionedUser
            from MessageMention mm
            where mm.id.messageId in :messageIds
            """)
    List<MentionRow> findAllByMessageIdIn(@Param("messageIds") List<Long> messageIds);

    interface MentionRow {
        Long getMessageId();
        Long getMentionedUser();
    }
}

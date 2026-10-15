package com.forward.hrms.chat.repository;

import com.forward.hrms.chat.entity.ConversationRead;
import com.forward.hrms.chat.entity.ConversationReadId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationReadRepository extends JpaRepository<ConversationRead, ConversationReadId> {

    List<ConversationRead> findAllByIdUserIdAndIdConversationIdIn(Long userId, List<Long> conversationIds);

    /**
     * All read cursors for the given conversations, across every member.
     * Used to build the per-recipient map on ConversationDto so the FE
     * can render ✓✓-style "everyone has read" ticks without a side fetch.
     */
    List<ConversationRead> findAllByIdConversationIdIn(List<Long> conversationIds);
}

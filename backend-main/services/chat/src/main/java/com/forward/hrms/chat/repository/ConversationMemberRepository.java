package com.forward.hrms.chat.repository;

import com.forward.hrms.chat.entity.ConversationMember;
import com.forward.hrms.chat.entity.ConversationMemberId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    List<ConversationMember> findAllByIdConversationId(Long conversationId);

    long countByIdConversationId(Long conversationId);

    long countByIdConversationIdAndRole(Long conversationId, com.forward.hrms.chat.enums.MemberRole role);

    @Query("select cm.id.userId from ConversationMember cm where cm.id.conversationId = :conversationId")
    List<Long> findUserIdsByConversationId(@Param("conversationId") Long conversationId);

    @Query("select cm.id.conversationId from ConversationMember cm where cm.id.userId = :userId")
    List<Long> findConversationIdsByUserId(@Param("userId") Long userId);

    Optional<ConversationMember> findByIdConversationIdAndIdUserId(Long conversationId, Long userId);

    boolean existsByIdConversationIdAndIdUserId(Long conversationId, Long userId);

    @Query("""
            select distinct peer.id.userId
              from ConversationMember mine
              join ConversationMember peer
                on peer.id.conversationId = mine.id.conversationId
             where mine.id.userId = :callerId
               and peer.id.userId in :candidateIds
            """)
    List<Long> findVisiblePeerIds(@Param("callerId") Long callerId,
                                  @Param("candidateIds") Collection<Long> candidateIds);
}

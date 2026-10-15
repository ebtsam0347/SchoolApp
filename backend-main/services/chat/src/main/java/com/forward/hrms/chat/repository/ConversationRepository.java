package com.forward.hrms.chat.repository;

import com.forward.hrms.chat.entity.Conversation;
import com.forward.hrms.chat.enums.ConversationType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByDmKey(String dmKey);

    Optional<Conversation> findFirstByType(ConversationType type);
}

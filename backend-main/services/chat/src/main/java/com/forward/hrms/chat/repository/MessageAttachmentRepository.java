package com.forward.hrms.chat.repository;

import com.forward.hrms.chat.entity.MessageAttachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

    List<MessageAttachment> findAllByMessageIdInOrderByIdAsc(List<Long> messageIds);

    List<MessageAttachment> findAllByMessageIdOrderByIdAsc(Long messageId);
}

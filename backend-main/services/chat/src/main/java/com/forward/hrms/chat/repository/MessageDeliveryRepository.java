package com.forward.hrms.chat.repository;

import com.forward.hrms.chat.entity.MessageDelivery;
import com.forward.hrms.chat.entity.MessageDeliveryId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageDeliveryRepository extends JpaRepository<MessageDelivery, MessageDeliveryId> {

    long countByIdMessageId(Long messageId);

    @Query("select md.id.userId from MessageDelivery md where md.id.messageId = :messageId")
    List<Long> findUserIdsByMessageId(@Param("messageId") Long messageId);
}

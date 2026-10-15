package com.forward.hrms.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_deliveries")
@Getter
@Setter
public class MessageDelivery {

    @EmbeddedId
    private MessageDeliveryId id;

    @Column(name = "delivered_at", nullable = false)
    private LocalDateTime deliveredAt;
}

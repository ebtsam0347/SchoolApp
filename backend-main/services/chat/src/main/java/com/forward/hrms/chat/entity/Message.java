package com.forward.hrms.chat.entity;

import com.forward.hrms.chat.enums.DeleteReason;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
public class Message extends AuditedEntity {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by_user")
    private Long deletedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "delete_reason", length = 32)
    private DeleteReason deleteReason;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

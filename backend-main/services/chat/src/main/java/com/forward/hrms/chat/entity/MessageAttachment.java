package com.forward.hrms.chat.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "message_attachments")
@Getter
@Setter
public class MessageAttachment extends AuditedEntity {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
}

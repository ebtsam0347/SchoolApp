package com.forward.hrms.notification.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.notification.enums.NotificationCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification_templates")
@Getter
@Setter
public class NotificationTemplate extends AuditedEntity {

    @Column(name = "type", nullable = false, length = 64, unique = true)
    private String type;

    @Column(name = "title_tpl", nullable = false, columnDefinition = "text")
    private String titleTpl;

    @Column(name = "body_tpl", columnDefinition = "text")
    private String bodyTpl;

    @Column(name = "link_tpl", length = 512)
    private String linkTpl;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32)
    private NotificationCategory category;

    @Column(name = "label", length = 128)
    private String label;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}

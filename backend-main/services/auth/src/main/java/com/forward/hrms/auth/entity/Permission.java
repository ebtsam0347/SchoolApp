package com.forward.hrms.auth.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 128, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;
}

package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "skills")
@Getter
@Setter
public class Skill extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** TECH | LANGUAGE | TRADE | SPORT | OTHER — soft enum, validated at edges. */
    @Column(name = "category", length = 32)
    private String category;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}

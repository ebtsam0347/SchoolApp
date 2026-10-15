package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "qualifications")
@Getter
@Setter
public class Qualification extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** MATRIC | INTERMEDIATE | DIPLOMA | BACHELORS | MASTERS | MPHIL | PHD | OTHER. */
    @Column(name = "level", nullable = false, length = 32)
    private String level;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}

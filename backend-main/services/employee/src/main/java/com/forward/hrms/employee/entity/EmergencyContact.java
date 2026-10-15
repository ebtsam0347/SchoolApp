package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "emergency_contacts")
@Getter
@Setter
public class EmergencyContact extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "relation", length = 64)
    private String relation;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;
}

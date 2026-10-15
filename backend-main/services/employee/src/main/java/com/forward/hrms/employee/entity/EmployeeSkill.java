package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.Proficiency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "employee_skills",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_employee_skill", columnNames = {"employee_id", "skill_id"}))
@Getter
@Setter
public class EmployeeSkill extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency", nullable = false, length = 16)
    private Proficiency proficiency;

    @Column(name = "years", precision = 4, scale = 1)
    private BigDecimal years;

    @Column(name = "notes")
    private String notes;
}

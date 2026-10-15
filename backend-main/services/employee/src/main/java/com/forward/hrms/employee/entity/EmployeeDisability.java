package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.DisabilitySeverity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "employee_disabilities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_employee_disability", columnNames = {"employee_id", "disability_id"}))
@Getter
@Setter
public class EmployeeDisability extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "disability_id", nullable = false)
    private Long disabilityId;

    /** Free-text descriptor (e.g. "Right arm polio"). */
    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16)
    private DisabilitySeverity severity;

    @Column(name = "document_key", length = 512)
    private String documentKey;
}

package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.DisciplinaryCategory;
import com.forward.hrms.employee.enums.DisciplinaryDecision;
import com.forward.hrms.employee.enums.DisciplinaryLevel;
import com.forward.hrms.employee.enums.DisciplinaryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "disciplinary_actions")
@Getter
@Setter
public class DisciplinaryAction extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 24)
    private DisciplinaryLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 24)
    private DisciplinaryCategory category;

    @Column(name = "incident_date")
    private LocalDate incidentDate;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 16)
    private DisciplinaryDecision decision;

    @Column(name = "decision_notes")
    private String decisionNotes;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DisciplinaryStatus status = DisciplinaryStatus.ISSUED;
}

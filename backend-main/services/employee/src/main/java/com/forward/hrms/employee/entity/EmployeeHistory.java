package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.HistoryChangeReason;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employee_history")
@Getter
@Setter
public class EmployeeHistory extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** {@code null} = current row. Partial unique index enforces "at most one current". */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "designation_id")
    private Long designationId;

    @Column(name = "grade_id")
    private Long gradeId;

    @Column(name = "manager_id")
    private Long managerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_reason", length = 32)
    private HistoryChangeReason changeReason;

    @Column(name = "notes")
    private String notes;
}

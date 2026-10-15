package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "employee_qualifications")
@Getter
@Setter
public class EmployeeQualification extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "qualification_id", nullable = false)
    private Long qualificationId;

    @Column(name = "institution", nullable = false, length = 255)
    private String institution;

    @Column(name = "board_or_university", length = 255)
    private String boardOrUniversity;

    @Column(name = "year_of_completion")
    private Integer yearOfCompletion;

    @Column(name = "grade", length = 32)
    private String grade;

    @Column(name = "document_key", length = 512)
    private String documentKey;
}

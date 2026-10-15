package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employee_prior_experience")
@Getter
@Setter
public class EmployeePriorExperience extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employer_name", nullable = false, length = 255)
    private String employerName;

    @Column(name = "designation", length = 255)
    private String designation;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "responsibilities")
    private String responsibilities;

    @Column(name = "reason_for_leaving", length = 255)
    private String reasonForLeaving;
}

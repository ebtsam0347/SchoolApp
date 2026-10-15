package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.EmploymentType;
import com.forward.hrms.employee.enums.RequisitionStatus;
import com.forward.hrms.employee.enums.RequisitionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_requisitions")
@Getter
@Setter
public class EmployeeRequisition extends AuditedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private RequisitionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "minimum_education")
    private String minimumEducation;

    @Column(name = "minimum_experience")
    private Long minimumExperience;

    @Column(name = "minimum_skill")
    private String minimumSkill;

    @Column(name = "job_description")
    private String jobDescription;

    @Column(name = "total_positions_hod")
    private Long totalPositionsHod;

    @Column(name = "total_positions_lean")
    private Long totalPositionsLean;

    @Column(name = "request_reason")
    private String requestReason;

    @Column(name = "hod_notes")
    private String hodNotes;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "reject_reason")
    private String rejectReason;
}
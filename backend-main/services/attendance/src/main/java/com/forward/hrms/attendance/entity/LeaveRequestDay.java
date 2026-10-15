package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.LeaveDayStatus;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "leave_request_days")
@Getter
@Setter
public class LeaveRequestDay extends AuditedEntity {

    @Column(name = "leave_request_id", nullable = false)
    private Long leaveRequestId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LeaveDayStatus status;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_at_stage")
    private Integer decidedAtStage;

    @Column(name = "rejection_reason")
    private String rejectionReason;
}

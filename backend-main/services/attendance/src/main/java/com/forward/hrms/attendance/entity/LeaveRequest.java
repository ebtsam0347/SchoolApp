package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.HalfDayPart;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
public class LeaveRequest extends AuditedEntity {

    @Column(name = "request_code", nullable = false, length = 20, updatable = false)
    private String requestCode;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "half_day_part", length = 8)
    private HalfDayPart halfDayPart;

    @Column(name = "days", nullable = false, precision = 5, scale = 2)
    private BigDecimal days;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LeaveRequestStatus status;
}

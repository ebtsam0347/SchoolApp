package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.ShortLeaveStatus;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Sub-day leave: employee leaves work for {@code (start_time, end_time)}
 * during {@code leave_date} with HR approval. Distinct from regularization
 * ("forgot to punch") and from {@link LeaveRequest} (full or half day).
 * Approval uses the shared {@link com.forward.hrms.commons.approval.entity.ApprovalStep}
 * chain with {@code request_type = SHORT_LEAVE}.
 */
@Entity
@Table(name = "short_leave_requests")
@Getter
@Setter
public class ShortLeaveRequest extends AuditedEntity {

    @Column(name = "request_code", nullable = false, length = 20, updatable = false)
    private String requestCode;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ShortLeaveStatus status;

    /** Derived from the (start_time, end_time) window. */
    @Transient
    public int getTotalMinutes() {
        if (startTime == null || endTime == null) return 0;
        return Math.toIntExact(Duration.between(startTime, endTime).toMinutes());
    }
}

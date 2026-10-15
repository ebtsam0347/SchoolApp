package com.forward.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Historical attendance snapshot for one Employee Final Settlement.
 * Persisted only when the settlement is actually created — never for a
 * preview call — from the same {@code attendanceSummary} the preview
 * already returned. Linked to its settlement via {@code settlement_id}
 * (the real ownership relationship); {@code employeeId} is retained only
 * as a snapshot/reference field, matching the settlement's own
 * employee-snapshot columns.
 *
 * <p>Deliberately not a live reference to attendance-service's own
 * tables — later attendance corrections must not change what HR already
 * approved in this settlement.
 */
@Entity
@Table(name = "employee_final_settlement_attendance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeFinalSettlementAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private EmployeeFinalSettlement settlement;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "present_days", nullable = false)
    @Builder.Default
    private Long presentDays = 0L;

    @Column(name = "half_days", nullable = false)
    @Builder.Default
    private Long halfDays = 0L;

    @Column(name = "leave_days", nullable = false)
    @Builder.Default
    private Long leaveDays = 0L;

    @Column(name = "paid_leave_days", nullable = false)
    @Builder.Default
    private Long paidLeaveDays = 0L;

    @Column(name = "unpaid_leave_days", nullable = false)
    @Builder.Default
    private Long unpaidLeaveDays = 0L;

    @Column(name = "absent_days", nullable = false)
    @Builder.Default
    private Long absentDays = 0L;

    @Column(name = "weekend_days", nullable = false)
    @Builder.Default
    private Long weekendDays = 0L;

    @Column(name = "holiday_days", nullable = false)
    @Builder.Default
    private Long holidayDays = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

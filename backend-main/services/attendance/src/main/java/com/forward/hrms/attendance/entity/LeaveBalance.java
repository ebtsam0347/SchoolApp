package com.forward.hrms.attendance.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "leave_balances")
@Getter
@Setter
public class LeaveBalance extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    /**
     * Calendar year the cycle starts in (= {@code cycleStart.getYear()}).
     * Kept as a denormalized grouping label for the yearly reports /
     * analytics; the authoritative cycle identity is the
     * {@code cycleStart}/{@code cycleEnd} window below.
     */
    @Column(name = "year", nullable = false)
    private int year;

    /** Inclusive first day of this entitlement cycle. */
    @Column(name = "cycle_start", nullable = false)
    private LocalDate cycleStart;

    /** Inclusive last day of this entitlement cycle. */
    @Column(name = "cycle_end", nullable = false)
    private LocalDate cycleEnd;

    @Column(name = "allocated", nullable = false, precision = 5, scale = 2)
    private BigDecimal allocated = BigDecimal.ZERO;

    @Column(name = "used", nullable = false, precision = 5, scale = 2)
    private BigDecimal used = BigDecimal.ZERO;

    @Column(name = "pending", nullable = false, precision = 5, scale = 2)
    private BigDecimal pending = BigDecimal.ZERO;

    @Column(name = "carried", nullable = false, precision = 5, scale = 2)
    private BigDecimal carried = BigDecimal.ZERO;

    /**
     * Days encashed when this cycle closed (encashable types only).
     * Null until the cycle is closed at renewal; stamped for the audit
     * trail alongside the {@code leave.encashed} event.
     */
    @Column(name = "encashed_days", precision = 5, scale = 2)
    private BigDecimal encashedDays;

    @Column(name = "is_annual_leave_unpaid_carry")
    private Boolean isAnnualLeaveUnpaidCarry = false;

    @Column(name = "annual_leave_transaction_id")
    private Long annualLeaveTransactionId;
}

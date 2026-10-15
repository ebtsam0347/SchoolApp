package com.forward.hrms.attendance.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One canteen (meal) register entry — the successor to legacy
 * {@code PayRoll_meal}. Written by the canteen clerk through
 * {@code /canteen/attendance}, not by the device poller: the raw
 * CANTEEN-device feed lands in {@link DeviceEventLog} and stays
 * immutable so payroll's canteen deduction has a stable source.
 *
 * <p>{@code employeeId} is the employee-schema row id, validated
 * against the local {@link AttendanceEmployee} mirror on write.
 */
@Entity
@Table(name = "attendance_canteen")
@Getter
@Setter
public class CanteenAttendance extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "attendance_time", nullable = false)
    private LocalTime attendanceTime;

    /**
     * IP of the canteen terminal the entry came from. Null on manual
     * clerk entries — reserved for the canteen device poller.
     */
    @Column(name = "device_ip", length = 45)
    private String deviceIp;

    /** Meals this entry covers. 1 for a normal single meal. */
    @Column(name = "count", nullable = false)
    private Integer count = 1;
}

package com.forward.hrms.attendance.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Attendance-side snapshot of {@code employee.employees}, kept in sync
 * by the {@code employee.created} / {@code employee.updated} consumer.
 * The snapshot is the canonical {@code employee_code → employee_id}
 * lookup for biometric punch ingest — the device's user_id field IS
 * the employee_code, so matching at ingest happens against this
 * table, not via the per-device {@link AttendanceDeviceUser} bridge.
 *
 * <p>{@code employee_id} is the upstream-system row id, kept as a
 * unique column so attendance rows reference it as a stable FK while
 * the table still follows the project-wide bigint identity PK rule.
 */
@Entity
@Table(name = "attendance_employees")
@Getter
@Setter
public class AttendanceEmployee extends AuditedEntity {

    @Column(name = "employee_id", nullable = false, unique = true)
    private Long employeeId;

    @Column(name = "employee_code", nullable = false, length = 32, unique = true)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    /**
     * Employment status mirrored from the employee schema, kept current by
     * the employee.* event consumer. Drives the nightly close's "active
     * employee" selection without an HTTP call to employee-service. Stored
     * as the {@code EmploymentStatus} enum name (ACTIVE / ON_NOTICE /
     * ON_LEAVE / SUSPENDED / TERMINATED). Defaults to ACTIVE so a row
     * inserted on employee.created (or out-of-order) is correct.
     */
    @Column(name = "employment_status", nullable = false, length = 16)
    private String employmentStatus = "ACTIVE";

    /**
     * Joining date mirrored from the employee schema (carried on the
     * {@code employee.created} event). Drives JOINING-anchored leave
     * cycles (annual-leave anniversaries) and FISCAL proration without an
     * HTTP call to employee-service. Nullable: employees mirrored before
     * this column existed backfill lazily on their next leave action.
     */
    @Column(name = "joining_date")
    private java.time.LocalDate joiningDate;
}

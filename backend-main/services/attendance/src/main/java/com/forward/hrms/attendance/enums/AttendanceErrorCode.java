package com.forward.hrms.attendance.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

public enum AttendanceErrorCode implements ErrorCode {
    // generic
    EMPLOYEE_NOT_FOUND,
    UPSTREAM_EMPLOYEE_FAILED,

    // shifts
    SHIFT_NOT_FOUND,
    SHIFT_CODE_TAKEN,
    SHIFT_ASSIGNMENT_INVALID,

    // shift overrides
    OVERRIDE_NOT_FOUND,
    OVERRIDE_OVERLAP,
    OVERRIDE_RANGE_INVALID,

    // punches
    PUNCH_LIMIT,
    PUNCH_DUPLICATE,
    PUNCH_FUTURE,
    PUNCH_NOT_FOUND,
    AUTO_ATTENDANCE_RANGE_INVALID,

    // devices
    DEVICE_NOT_FOUND,
    DEVICE_CODE_TAKEN,

    // device sync
    SYNC_TASK_NOT_FOUND,
    SYNC_TASK_NOT_FAILED,
    DEVICE_USER_NOT_FOUND,
    TEMPLATE_NOT_FOUND,
    TEMPLATE_KIND_INVALID,
    UNKNOWN_DEVICE_USER,
    /** Explicit push requested for an employee with no captured fingerprint templates yet. */
    TEMPLATES_NOT_FOUND,
    /** Push target device is not active — no poller drains its queue. */
    DEVICE_INACTIVE,

    // device snapshots
    DEVICE_SNAPSHOT_NOT_FOUND,
    DEVICE_SNAPSHOT_NOT_PENDING,
    DEVICE_SNAPSHOT_NOT_COMPLETED,

    // attendance
    ATTENDANCE_NOT_FOUND,

    // holidays
    HOLIDAY_NOT_FOUND,
    HOLIDAY_DATE_TAKEN,

    // leaves
    LEAVE_TYPE_NOT_FOUND,
    LEAVE_TYPE_CODE_TAKEN,
    LEAVE_REQUEST_NOT_FOUND,
    LEAVE_BALANCE_INSUFFICIENT,
    LEAVE_RANGE_INVALID,
    LEAVE_RANGE_EMPTY,
    LEAVE_OVERLAPS_EXISTING,
    LEAVE_NOT_PENDING,
    LEAVE_NOT_CANCELLABLE,
    LEAVE_DAY_NOT_FOUND,
    STAGE_PER_DAY_NOT_ALLOWED,
    LEAVE_BALANCE_NOT_FOUND,

    // short leave
    SHORT_LEAVE_NOT_FOUND,
    SHORT_LEAVE_RANGE_INVALID,
    SHORT_LEAVE_OVERLAPS_EXISTING,
    SHORT_LEAVE_NOT_CANCELLABLE,

    // overtime
    OVERTIME_NOT_FOUND,
    OVERTIME_DUPLICATE,
    OVERTIME_NO_PUNCH_FOR_DATE,
    OVERTIME_NOT_PENDING,
    OVERTIME_BATCH_NOT_FOUND,
    OVERTIME_DEPARTMENT_MISMATCH,
    OVERTIME_TYPE_NOT_ALLOWED,

    // regularization
    REGULARIZATION_NOT_FOUND,
    REGULARIZATION_INVALID,
    REGULARIZATION_NOT_PENDING,

    // approvals
    STEP_NOT_PENDING,
    NOT_AUTHORIZED,
    NO_PENDING_STAGE,
    /**
     * Caller passed `onBehalfOf` for an employee they are neither
     * the direct manager of nor have `attendance.approval.override` for.
     */
    ON_BEHALF_NOT_AUTHORIZED,

    // delegation
    DELEGATION_NOT_FOUND,
    DELEGATION_OVERLAP,
    DELEGATION_INVALID,

    // approval workflow config
    /** A request type was submitted with no active workflow stages configured. */
    WORKFLOW_NOT_CONFIGURED,
    /** A PERMISSION-kind stage was saved without an approve permission, or a stage is otherwise malformed. */
    WORKFLOW_STAGE_INVALID,

    // §7.attendance round 2 — request-side validation gaps.
    /** Generic 422 when a leave/short-leave/regularization fromDate is in the past or future-window violation. */
    REQUEST_DATE_OUT_OF_RANGE,
    /** Caller is not ACTIVE (SUSPENDED/TERMINATED/ON_NOTICE-with-no-policy) and tried to submit a request. */
    EMPLOYEE_NOT_ACTIVE,
    /** Empty employeeIds on a bulk endpoint (shift override, etc.). */
    EMPLOYEES_REQUIRED,
    /** Shift template start/end pair is invalid. */
    SHIFT_TIMES_INVALID,

    // reports
    /** Generic 422 for report-input validation (date range, period, etc.). */
    REPORT_VALIDATION_FAILED,

    // period locks
    PERIOD_NOT_LOCKED,
    PERIOD_INVALID,
    /** Attempt to lock a period whose month has not fully elapsed (e.g. locking April before May 1). */
    PERIOD_NOT_ELAPSED,
    /** Attempt to create/amend/approve attendance for a period that is locked. */
    PERIOD_LOCKED,

    ANNUAL_LEAVE_BALANCE_NOT_FOUND,
    LEAVE_BALANCE_ALREADY_EXISTS,
    LEAVE_TYPE_INACTIVE,

    // canteen (meal register)
    CANTEEN_ENTRY_NOT_FOUND,
    /** An entry already exists for the same employee at the same date + time. */
    CANTEEN_ENTRY_DUPLICATE,
    /** Canteen date range is inverted or wider than the listing cap. */
    CANTEEN_RANGE_INVALID,

    CANTEEN_UPLOAD_INVALID
}

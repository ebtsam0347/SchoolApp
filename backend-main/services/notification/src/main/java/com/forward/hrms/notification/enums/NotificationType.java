package com.forward.hrms.notification.enums;

/**
 * Canonical type strings for every notification kind the service emits.
 * Stored as plain {@code varchar(64)} on {@code notifications.type} —
 * not bound to this enum at the DB layer — so unknown future types from
 * a newer template can pass through without code changes here.
 */
public final class NotificationType {

    private NotificationType() {}

    // Leave
    public static final String LEAVE_APPLIED = "LEAVE_APPLIED";
    public static final String LEAVE_STAGE_APPROVED = "LEAVE_STAGE_APPROVED";
    public static final String LEAVE_STAGE_REJECTED = "LEAVE_STAGE_REJECTED";
    public static final String LEAVE_APPROVED = "LEAVE_APPROVED";
    public static final String LEAVE_PARTIALLY_APPROVED = "LEAVE_PARTIALLY_APPROVED";
    public static final String LEAVE_REJECTED = "LEAVE_REJECTED";

    // Overtime
    public static final String OT_REQUESTED = "OT_REQUESTED";
    public static final String OT_STAGE_APPROVED = "OT_STAGE_APPROVED";
    public static final String OT_STAGE_REJECTED = "OT_STAGE_REJECTED";
    public static final String OT_APPROVED = "OT_APPROVED";
    public static final String OT_REJECTED = "OT_REJECTED";

    // Regularization
    public static final String REGULARIZATION_REQUESTED = "REGULARIZATION_REQUESTED";
    public static final String REGULARIZATION_STAGE_APPROVED = "REGULARIZATION_STAGE_APPROVED";
    public static final String REGULARIZATION_STAGE_REJECTED = "REGULARIZATION_STAGE_REJECTED";
    public static final String REGULARIZATION_APPROVED = "REGULARIZATION_APPROVED";
    public static final String REGULARIZATION_REJECTED = "REGULARIZATION_REJECTED";

    // Payroll
    public static final String PAYSLIP_READY = "PAYSLIP_READY";

    // Auth
    public static final String PASSWORD_RESET_BY_ADMIN = "PASSWORD_RESET_BY_ADMIN";
    public static final String SESSION_REVOKED_BY_ADMIN = "SESSION_REVOKED_BY_ADMIN";

    // Employee
    public static final String EMPLOYEE_MANAGER_CHANGED_EMPLOYEE = "EMPLOYEE_MANAGER_CHANGED_EMPLOYEE";
    public static final String EMPLOYEE_MANAGER_CHANGED_OLD_MANAGER = "EMPLOYEE_MANAGER_CHANGED_OLD_MANAGER";
    public static final String EMPLOYEE_MANAGER_CHANGED_NEW_MANAGER = "EMPLOYEE_MANAGER_CHANGED_NEW_MANAGER";
    public static final String EMPLOYEE_SUSPENDED = "EMPLOYEE_SUSPENDED";
    public static final String EMPLOYEE_SUSPENSION_LIFTED = "EMPLOYEE_SUSPENSION_LIFTED";
    public static final String EMPLOYEE_SUSPENSION_LIFTED_MANAGER = "EMPLOYEE_SUSPENSION_LIFTED_MANAGER";

    // Attendance
    public static final String SHIFT_OVERRIDDEN = "SHIFT_OVERRIDDEN";
    public static final String SHIFT_OVERRIDE_REVERTED = "SHIFT_OVERRIDE_REVERTED";

    // Broadcast
    public static final String ANNOUNCEMENT = "ANNOUNCEMENT";

    // Chat
    public static final String CHAT_DM = "CHAT_DM";
    public static final String CHAT_MENTION = "CHAT_MENTION";
}

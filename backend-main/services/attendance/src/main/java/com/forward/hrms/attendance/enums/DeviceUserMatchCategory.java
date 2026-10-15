package com.forward.hrms.attendance.enums;

/**
 * How a device-enrolled user reconciles against the employee mirror.
 * Drives the post-adoption cleanup: MATCHED_INACTIVE and UNMATCHED are the
 * un-enroll candidates; MATCHED_ACTIVE stays.
 */
public enum DeviceUserMatchCategory {
    /** Device user_id matches an ACTIVE employee. Keep. */
    MATCHED_ACTIVE,
    /** Matches an employee whose employment_status is not ACTIVE (e.g. TERMINATED). */
    MATCHED_INACTIVE,
    /** No employee_code matches this device user_id — stray enrollment. */
    UNMATCHED
}

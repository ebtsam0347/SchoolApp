package com.forward.hrms.attendance.enums;

/**
 * Overtime category chosen by the applicant, one active request per type per
 * day. SPECIAL is a Sunday / holiday's whole worked day; on a working day
 * NORMAL is the first two hours past shift end and EXTRA the rest. A bucket on
 * {@code attendance_records} is credited only when its type is approved. See
 * {@code AttendanceRecomputeService.recomputeOvertime}.
 */
public enum OvertimeType {
    NORMAL, EXTRA, SPECIAL
}

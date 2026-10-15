package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.AttendanceRecord;

public final class AttendanceRecordDtoMapper {

    private AttendanceRecordDtoMapper() {}

    public static AttendanceRecordDto of(AttendanceRecord r) {
        return new AttendanceRecordDto(
                r.getId(), r.getEmployeeId(), r.getAttendanceDate(),
                r.getCheckIn(), r.getCheckOut(),
                r.getEffectiveShift(),
                r.getShiftStartTime(), r.getShiftEndTime(),
                r.getShiftStartDateTime(), r.getShiftEndDateTime(),
                r.getShiftBreakStartTime(), r.getShiftBreakEndTime(),
                r.getShiftBreakMinutes(), r.getShiftGraceMinutes(),
                r.getStatus().name(),
                r.getLateMinutes(), r.getEarlyMinutes(), r.getWorkedMinutes(),
                r.getOtNormalMinutes(), r.getOtExtraMinutes(), r.getOtSpecialMinutes(),
                r.getSource() == null ? null : r.getSource().name(),
                r.getNotes(),
                r.getPaid());
    }
}

package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.AttendanceShiftOverride;

public final class ShiftOverrideDtoMapper {

    private ShiftOverrideDtoMapper() {}

    public static ShiftOverrideDto of(AttendanceShiftOverride o) {
        return new ShiftOverrideDto(o.getId(), o.getEmployeeId(),
                o.getFromDate(), o.getToDate(),
                o.getShiftStartTime(), o.getShiftEndTime(),
                o.getShiftBreakStartTime(), o.getShiftBreakEndTime(),
                o.getShiftBreakMinutes(), o.getShiftGraceMinutes(),
                o.getReason());
    }
}

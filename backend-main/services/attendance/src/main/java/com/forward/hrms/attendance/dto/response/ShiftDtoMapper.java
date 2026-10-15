package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.Shift;

public final class ShiftDtoMapper {

    private ShiftDtoMapper() {}

    public static ShiftDto of(Shift s) {
        return new ShiftDto(s.getId(), s.getCode(), s.getName(),
                s.getStartTime(), s.getEndTime(),
                s.getBreakStartTime(), s.getBreakEndTime(),
                s.getBreakMinutes(), s.getGraceMinutes(),
                s.getFridayExtraMinutes(),
                s.isActive(),
                s.getDisplayOrder(),
                s.getPeriod().name());
    }
}

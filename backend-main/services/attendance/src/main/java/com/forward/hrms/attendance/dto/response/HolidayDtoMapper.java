package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.Holiday;

public final class HolidayDtoMapper {

    private HolidayDtoMapper() {}

    public static HolidayDto of(Holiday h) {
        return new HolidayDto(h.getId(), h.getHolidayDate(), h.getName(), h.getType().name(), h.getDescription());
    }
}

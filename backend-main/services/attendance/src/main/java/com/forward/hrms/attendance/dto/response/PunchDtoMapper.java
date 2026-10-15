package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.AttendancePunch;

public final class PunchDtoMapper {

    private PunchDtoMapper() {}

    public static PunchDto of(AttendancePunch p) {
        return new PunchDto(
                p.getId(), p.getEmployeeId(), p.getPunchedAt(), p.getAttendanceDate(),
                p.getSource().name(), p.getDeviceId(), p.getReason(),
                p.getDirection() == null ? null : p.getDirection().name());
    }
}

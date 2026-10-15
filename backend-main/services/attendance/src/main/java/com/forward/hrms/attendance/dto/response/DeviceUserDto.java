package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.AttendanceDeviceUser;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "DeviceUserDto",
        description = "An employee enrolled on a device — view of the attendance_device_users " +
                "bridge row. The employee's identity on the device is the employee_code; look it " +
                "up via the employee snapshot.")
public record DeviceUserDto(
        long id,
        long deviceId,
        long employeeId,
        short privilege,
        boolean hasFingerprint,
        boolean hasFace,
        @Schema(nullable = true,
                description = "Timestamp of the last successful UPSERT_USER for this row. " +
                        "Null when the row has never been pushed.")
        LocalDateTime syncedAt
) {
    public static DeviceUserDto of(AttendanceDeviceUser u) {
        return new DeviceUserDto(
                u.getId(), u.getDeviceId(), u.getEmployeeId(),
                u.getPrivilege(),
                u.isHasFingerprint(), u.isHasFace(), u.getSyncedAt());
    }
}

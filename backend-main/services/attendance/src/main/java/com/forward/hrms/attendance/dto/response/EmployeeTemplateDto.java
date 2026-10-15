package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.AttendanceEmployeeTemplate;
import com.forward.hrms.attendance.enums.TemplateKind;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Base64;

@Schema(name = "EmployeeTemplateDto",
        description = "A captured biometric template. Bytes are base64 in transit so this DTO is JSON-safe.")
public record EmployeeTemplateDto(
        long id,
        long employeeId,
        TemplateKind kind,
        @Schema(nullable = true, description = "0..9 for FINGERPRINT; null for FACE.") Short fingerId,
        short format,
        @Schema(description = "Base64-encoded template bytes.") String bytes,
        long capturedDeviceId,
        LocalDateTime capturedAt
) {
    public static EmployeeTemplateDto of(AttendanceEmployeeTemplate t) {
        return new EmployeeTemplateDto(
                t.getId(), t.getEmployeeId(), t.getKind(), t.getFingerId(),
                t.getTemplateFormat(),
                Base64.getEncoder().encodeToString(t.getTemplate()),
                t.getCapturedDeviceId(), t.getCapturedAt());
    }
}

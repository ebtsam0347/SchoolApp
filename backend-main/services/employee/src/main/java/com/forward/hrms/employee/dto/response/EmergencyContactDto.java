package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmergencyContact;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmergencyContactDto")
public record EmergencyContactDto(
        long id,
        long employeeId,
        String name,
        @Schema(nullable = true) String relation,
        String phone,
        boolean primary
) {
    public static EmergencyContactDto of(EmergencyContact c) {
        return new EmergencyContactDto(
                c.getId(), c.getEmployeeId(),
                c.getName(), c.getRelation(), c.getPhone(),
                c.isPrimary());
    }
}

package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmployeePriorExperience;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "PriorExperienceDto", description = "Pre-Forward-Sports employment history.")
public record PriorExperienceDto(
        long id,
        long employeeId,
        String employerName,
        @Schema(nullable = true) String designation,
        LocalDate startDate,
        LocalDate endDate,
        @Schema(nullable = true) String responsibilities,
        @Schema(nullable = true) String reasonForLeaving
) {
    public static PriorExperienceDto of(EmployeePriorExperience p) {
        return new PriorExperienceDto(
                p.getId(), p.getEmployeeId(),
                p.getEmployerName(), p.getDesignation(),
                p.getStartDate(), p.getEndDate(),
                p.getResponsibilities(), p.getReasonForLeaving());
    }
}

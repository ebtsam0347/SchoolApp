package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Disability;
import com.forward.hrms.employee.entity.EmployeeDisability;
import com.forward.hrms.employee.enums.DisabilitySeverity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmployeeDisabilityDto")
public record EmployeeDisabilityDto(
        long id,
        long employeeId,
        long disabilityId,
        @Schema(description = "Catalog code (denormalized for the read.)") String disabilityCode,
        @Schema(description = "Catalog name.") String disabilityName,
        @Schema(nullable = true) String description,
        @Schema(nullable = true) DisabilitySeverity severity,
        @Schema(nullable = true) String documentKey
) {
    public static EmployeeDisabilityDto of(EmployeeDisability link, Disability cat) {
        return new EmployeeDisabilityDto(
                link.getId(), link.getEmployeeId(), link.getDisabilityId(),
                cat == null ? null : cat.getCode(),
                cat == null ? null : cat.getName(),
                link.getDescription(), link.getSeverity(), link.getDocumentKey());
    }
}

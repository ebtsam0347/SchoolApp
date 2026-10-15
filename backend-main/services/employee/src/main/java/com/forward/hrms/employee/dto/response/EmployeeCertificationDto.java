package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeCertification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "EmployeeCertificationDto",
        description = "Free-form per-employee certification (no global catalog).")
public record EmployeeCertificationDto(
        long id,
        long employeeId,
        @Schema(description = "Denormalized employee code; only filled on the global expiring queue.",
                nullable = true) String employeeCode,
        @Schema(description = "Denormalized employee name; only filled on the global expiring queue.",
                nullable = true) String fullName,
        String name,
        @Schema(nullable = true) String issuingBody,
        @Schema(nullable = true) LocalDate issueDate,
        @Schema(description = "Null = no expiry.", nullable = true) LocalDate expiryDate,
        @Schema(nullable = true) String credentialId,
        @Schema(nullable = true) String documentKey,
        @Schema(description = "Short-lived signed GET URL for the certificate scan. " +
                "Null when no document is attached or the storage call failed.", nullable = true)
        String documentUrl
) {
    public static EmployeeCertificationDto of(EmployeeCertification c) {
        return of(c, null, null);
    }

    public static EmployeeCertificationDto of(EmployeeCertification c, String documentUrl) {
        return of(c, null, documentUrl);
    }

    public static EmployeeCertificationDto of(EmployeeCertification c, Employee e) {
        return of(c, e, null);
    }

    public static EmployeeCertificationDto of(EmployeeCertification c, Employee e, String documentUrl) {
        return new EmployeeCertificationDto(
                c.getId(), c.getEmployeeId(),
                e == null ? null : e.getEmployeeCode(),
                e == null ? null : e.getFullName(),
                c.getName(), c.getIssuingBody(),
                c.getIssueDate(), c.getExpiryDate(),
                c.getCredentialId(), c.getDocumentKey(),
                documentUrl);
    }
}

package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.EmploymentType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "UpdateEmployeeRequest",
        description = "HR patch on the employee record. Null fields are left unchanged.")
public record UpdateEmployeeRequest(
        @Size(max = 128) String fullName,
        // §7.employee #48 — Pakistan CNIC format (5-7-1 digits, dashes optional).
        // Empty allowed because applyIfPresent skips null and we want
        // "leave unchanged" semantics on the patch.
        @Size(max = 15) @Pattern(regexp = "^(\\d{5}-?\\d{7}-?\\d)?$",
                message = "CNIC must be 13 digits (dashes optional, format 5-7-1)") String cnic,
        LocalDate cnicIssueDate,
        LocalDate cnicExpiryDate,
        // §7.employee #48 — NTN: 7-9 digits. Empty allowed for the same reason.
        @Size(max = 32) @Pattern(regexp = "^(\\d{7,9})?$",
                message = "NTN must be 7-9 digits") String ntn,
        @Size(max = 64) String insuranceNumber,
        @Size(max = 64) String sscNumber,
        @Size(max = 32) String eobiNumber,
        LocalDate dateOfBirth,
        String gender,
        String maritalStatus,
        @Size(max = 128) String fatherName,
        @Size(max = 128) String motherName,
        @Size(max = 128) String spouseName,
        @Size(max = 32) String religion,
        @Size(max = 8) String bloodGroup,
        @Email String personalEmail,
        @Email String workEmail,
        @Size(max = 32) String phone,
        String permanentAddress,
        String currentAddress,
        Long cityId,
        EmploymentType employmentType,
        @Schema(description = "Avatar object key in MinIO.") String profilePictureKey,
        String referenceBy
) {}

package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.EmploymentType;
import com.forward.hrms.employee.enums.EssProvisioningStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(name = "EmployeeDto", description = "Employee record — admin lists, get-by-id, post-mutation responses.")
public record EmployeeDto(
        @Schema(description = "Internal id.", example = "42") long id,
        @Schema(description = "Linked auth user id (null when ESS not granted).", nullable = true) Long userId,
        @Schema(description = "Employee code.", example = "FS-2026-0042") String employeeCode,
        @Schema(description = "Display name.", example = "Ahmed Raza") String fullName,
        @Schema(description = "13-digit CNIC.", nullable = true) String cnic,
        @Schema(nullable = true) LocalDate cnicIssueDate,
        @Schema(nullable = true) LocalDate cnicExpiryDate,
        @Schema(nullable = true) String ntn,
        @Schema(nullable = true) String insuranceNumber,
        @Schema(nullable = true) String sscNumber,
        @Schema(nullable = true) String eobiNumber,
        @Schema(nullable = true) LocalDate dateOfBirth,
        @Schema(nullable = true) String gender,
        @Schema(nullable = true) String maritalStatus,
        @Schema(nullable = true) String fatherName,
        @Schema(nullable = true) String motherName,
        @Schema(nullable = true) String spouseName,
        @Schema(nullable = true) String religion,
        @Schema(nullable = true, example = "B+") String bloodGroup,
        @Schema(nullable = true) String personalEmail,
        @Schema(nullable = true) String workEmail,
        @Schema(nullable = true) String phone,
        @Schema(nullable = true) String permanentAddress,
        @Schema(nullable = true) String currentAddress,
        @Schema(nullable = true) Long cityId,
        @Schema(example = "PK") String country,
        LocalDate joiningDate,
        @Schema(nullable = true) LocalDate confirmationDate,
        EmploymentStatus employmentStatus,
        @Schema(nullable = true) EmploymentType employmentType,
        @Schema(nullable = true) Long departmentId,
        @Schema(nullable = true) Long sectionId,
        @Schema(nullable = true) Long designationId,
        @Schema(nullable = true) Long gradeId,
        @Schema(nullable = true) Long managerId,
        @Schema(description = "Linked auth user id of the manager. Null when the manager has no ESS user, "
                + "or when the source path didn't resolve it (mutation responses leave it null).",
                nullable = true) Long managerUserId,
        @Schema(nullable = true) String profilePictureKey,
        @Schema(description = "Short-lived signed GET URL for the profile picture. " +
                "Null when no picture is set or the storage call failed.", nullable = true)
        String profilePictureUrl,
        @Schema(description = "True iff ESS is provisioned.") boolean hasEss,
        @Schema(description = "ESS provisioning state. On mutation responses reflects only the "
                + "linked-user check (COMPLETED / NOT_REQUESTED). On read paths the assembler "
                + "looks up the onboarding saga row to surface RUNNING / FAILED.")
        EssProvisioningStatus essProvisioningStatus,
        @Schema(description = "Number of non-terminated employees who report to this employee. " +
                "Populated on read endpoints; null on post-mutation responses.", nullable = true)
        Integer directReportsCount,
        @Schema(description = "Active-probation summary. Populated only on probation queries " +
                "(probationDueWithin / probationOverdue) and detail reads.", nullable = true)
        ActiveProbationDto activeProbation,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

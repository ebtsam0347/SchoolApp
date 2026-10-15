package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DisabilitySeverity;
import com.forward.hrms.employee.enums.EmploymentType;
import com.forward.hrms.employee.enums.Proficiency;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "OnboardEmployeeRequest",
        description = "HR payload to onboard a new employee. Quick-add modals send only the " +
                "required identity + assignment fields; the complete-form page additionally " +
                "supplies salary, shift, and per-row arrays (qualifications, experience, " +
                "skills, certifications, emergency contacts, disabilities). Sub-resources owned " +
                "by employee-service are persisted in the kickoff transaction; salary, " +
                "salary-components, bank, and shift sit in the saga payload and are applied " +
                "by ONBOARDING saga steps with bounded retry. ESS provisioning runs as the " +
                "first saga step when `provisionEss=true`.")
public record OnboardEmployeeRequest(
        @Schema(description = "Display name.", example = "Ahmed Raza",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String fullName,

        // §7.employee #48 — Pakistan CNIC is 13 digits in 5-7-1 chunks; dashes
        // optional. Pattern is permissive on dashes so HR can enter either
        // form without re-formatting.
        @Schema(description = "13-digit CNIC, dashes optional.", example = "35202-1234567-1", maxLength = 15)
        @Size(max = 15) @Pattern(regexp = "^(\\d{5}-?\\d{7}-?\\d)?$",
                message = "CNIC must be 13 digits (dashes optional, format 5-7-1)") String cnic,
        @Schema(nullable = true) LocalDate cnicIssueDate,
        @Schema(nullable = true) LocalDate cnicExpiryDate,
        // §7.employee #48 — NTN: 7-9 digits. Empty string allowed (column is nullable).
        @Schema(nullable = true, maxLength = 32, description = "Pakistan NTN (7-9 digits).")
        @Size(max = 32) @Pattern(regexp = "^(\\d{7,9})?$",
                message = "NTN must be 7-9 digits") String ntn,
        @Schema(nullable = true, maxLength = 64) @Size(max = 64) String insuranceNumber,
        @Schema(nullable = true, maxLength = 64) @Size(max = 64) String sscNumber,
        @Schema(nullable = true, maxLength = 32) @Size(max = 32) String eobiNumber,

        @Schema(nullable = true) LocalDate dateOfBirth,
        @Schema(nullable = true) String gender,
        @Schema(nullable = true) String maritalStatus,
        @Schema(nullable = true, maxLength = 128) @Size(max = 128) String fatherName,
        @Schema(nullable = true, maxLength = 128) @Size(max = 128) String motherName,
        @Schema(nullable = true, maxLength = 128) @Size(max = 128) String spouseName,
        @Schema(nullable = true, maxLength = 32) @Size(max = 32) String religion,
        @Schema(nullable = true, maxLength = 8, example = "B+") @Size(max = 8) String bloodGroup,
        @Schema(nullable = true) @Email String personalEmail,
        @Schema(nullable = true) @Email String workEmail,
        @Schema(nullable = true, maxLength = 32) @Size(max = 32) String phone,
        @Schema(nullable = true) String permanentAddress,
        @Schema(nullable = true) String currentAddress,
        @Schema(nullable = true) Long cityId,

        @Schema(description = "First day on the job.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate joiningDate,

        @Schema(nullable = true) EmploymentType employmentType,
        @Schema(nullable = true) Long departmentId,
        @Schema(nullable = true) Long sectionId,
        @Schema(nullable = true) Long designationId,
        @Schema(nullable = true) Long gradeId,
        @Schema(description = "Initial direct manager.", nullable = true) Long managerId,
        @Schema(nullable = true) String referenceBy,

        @Schema(description = "Probation length in days. Allowed: 30, 60, 90 (default 90), 120, 180.",
                example = "90") Integer probationDays,

        @Schema(description = "If true, employee-service calls auth-service to create an ESS user.")
        boolean provisionEss,
        @Schema(description = "Login name for the ESS user. Required when `provisionEss=true`.")
        String username,
        @Schema(description = "Email for the ESS user. Optional; uses workEmail when omitted.")
        String email,
        @Schema(description = "Temporary password for the ESS user. Required when `provisionEss=true`.")
        String temporaryPassword,

        // -------- Complete-form sections (all optional) --------

        @Schema(description = "Per-employee qualifications. Persisted in the kickoff tx.",
                nullable = true)
        @Valid List<QualificationLine> qualifications,

        @Schema(description = "Prior employment history. Persisted in the kickoff tx.",
                nullable = true)
        @Valid List<ExperienceLine> experiences,

        @Schema(description = "Skills + proficiency. Persisted in the kickoff tx.", nullable = true)
        @Valid List<SkillLine> skills,

        @Schema(description = "Professional certifications. Persisted in the kickoff tx.", nullable = true)
        @Valid List<CertificationLine> certifications,

        @Schema(description = "Emergency contacts. Persisted in the kickoff tx. " +
                "Recommend at least one for compliance.", nullable = true)
        @Valid List<EmergencyContactLine> emergencyContacts,

        @Schema(description = "Registered disabilities. Persisted in the kickoff tx.", nullable = true)
        @Valid List<DisabilityLine> disabilities,

        @Schema(description = "Salary, allowance/deduction lines, and bank. Applied by " +
                "SET_BASIC_SALARY / ASSIGN_SALARY_COMPONENTS / SET_BANK_DETAILS saga steps. " +
                "Omit to skip — payroll-service does not auto-seed; HR sets basic later via " +
                "POST /salary/employees/{id}. Until then the employee surfaces as a " +
                "NO_BASIC_SALARY skip row on each compute attempt.",
                nullable = true)
        @Valid SalarySetup salary,

        @Schema(description = "Initial shift assignment. Applied by the ASSIGN_SHIFT saga step. " +
                "Omit to skip — HR can assign later via /shifts/employees/{id}.", nullable = true)
        @Valid ShiftAssignment shift
) {

    @Schema(name = "OnboardSalarySetup",
            description = "Basic salary + selected allowance/deduction lines + bank for disbursement. " +
                    "All amounts are paisa (1 PKR = 100 paisa).")
    public record SalarySetup(
            @Schema(description = "Monthly basic in paisa. Replaces the auto-seeded placeholder.",
                    requiredMode = Schema.RequiredMode.REQUIRED, example = "5000000")
            @NotNull @PositiveOrZero Long basic,

            @Schema(description = "Salary components from the catalog (allowances + deductions). " +
                    "Income tax should be entered here as a deduction line with code INCOME_TAX.",
                    nullable = true)
            @Valid List<ComponentLine> components,

            @Schema(description = "Bank details for salary disbursement. Optional — can be added later.",
                    nullable = true)
            @Valid BankDetails bank
    ) {}

    @Schema(name = "OnboardComponentLine",
            description = "One row in employee_salary_components.")
    public record ComponentLine(
            @Schema(description = "FK into salary_components catalog.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long componentId,

            @Schema(description = "Override the catalog default amount (paisa). Null = use default.",
                    nullable = true, example = "1500000")
            @PositiveOrZero Long amount,

            @Schema(nullable = true) String notes
    ) {}

    @Schema(name = "OnboardBankDetails",
            description = "Pakistan IBAN + bank catalog reference for salary disbursement.")
    public record BankDetails(
            @Schema(description = "FK into banks catalog.", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long bankId,
            @Schema(nullable = true, maxLength = 128) @Size(max = 128) String branch,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
            @NotBlank @Size(max = 128) String accountTitle,
            // §7.employee #49 — Pakistan IBAN is exactly 24 chars,
            // PK + 2 check digits + 4-letter bank code + 16 alphanumerics.
            // Match payroll's IBAN pattern so a malformed payload fails at
            // employee onboarding instead of slipping through to payroll.
            @Schema(description = "Pakistan IBAN, 24 chars (PK + 2 digits + 4 letters + 16 alphanumerics).",
                    requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 34)
            @NotBlank @Size(max = 34)
            @Pattern(regexp = "^PK\\d{2}[A-Z]{4}[0-9A-Z]{16}$",
                    message = "IBAN must be a 24-char Pakistan IBAN: PK + 2 digits + 4 letters + 16 alphanumerics")
            String iban,
            @Schema(nullable = true, maxLength = 64) @Size(max = 64) String accountNumber
    ) {}

    @Schema(name = "OnboardShiftAssignment",
            description = "Initial shift the employee follows from joiningDate.")
    public record ShiftAssignment(
            @Schema(description = "FK into shifts catalog (attendance-service).",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long shiftId
    ) {}

    @Schema(name = "OnboardQualificationLine")
    public record QualificationLine(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long qualificationId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
            @NotBlank @Size(max = 255) String institution,
            @Schema(nullable = true, maxLength = 255) @Size(max = 255) String boardOrUniversity,
            @Schema(nullable = true) Integer yearOfCompletion,
            @Schema(nullable = true, maxLength = 32) @Size(max = 32) String grade,
            @Schema(nullable = true, maxLength = 512) @Size(max = 512) String documentKey
    ) {}

    @Schema(name = "OnboardExperienceLine")
    public record ExperienceLine(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
            @NotBlank @Size(max = 255) String employerName,
            @Schema(nullable = true, maxLength = 255) @Size(max = 255) String designation,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
            @Schema(nullable = true) String responsibilities,
            @Schema(nullable = true, maxLength = 255) @Size(max = 255) String reasonForLeaving
    ) {}

    @Schema(name = "OnboardSkillLine")
    public record SkillLine(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long skillId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Proficiency proficiency,
            @Schema(nullable = true) BigDecimal years,
            @Schema(nullable = true) String notes
    ) {}

    @Schema(name = "OnboardCertificationLine")
    public record CertificationLine(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
            @NotBlank @Size(max = 255) String name,
            @Schema(nullable = true, maxLength = 255) @Size(max = 255) String issuingBody,
            @Schema(nullable = true) LocalDate issueDate,
            @Schema(description = "Null = no expiry.", nullable = true) LocalDate expiryDate,
            @Schema(nullable = true, maxLength = 128) @Size(max = 128) String credentialId,
            @Schema(nullable = true, maxLength = 512) @Size(max = 512) String documentKey
    ) {}

    @Schema(name = "OnboardEmergencyContactLine")
    public record EmergencyContactLine(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
            @NotBlank @Size(max = 128) String name,
            @Schema(nullable = true, maxLength = 64) @Size(max = 64) String relation,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
            @NotBlank @Size(max = 32) String phone,
            @Schema(description = "Mark as the primary contact. The first row with primary=true wins; " +
                    "the rest are stored as non-primary regardless of flag value.")
            boolean primary
    ) {}

    @Schema(name = "OnboardDisabilityLine")
    public record DisabilityLine(
            @Schema(description = "FK into disabilities catalog.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long disabilityId,
            @Schema(nullable = true, maxLength = 255) @Size(max = 255) String description,
            @Schema(nullable = true) DisabilitySeverity severity,
            @Schema(nullable = true, maxLength = 512) @Size(max = 512) String documentKey
    ) {}
}

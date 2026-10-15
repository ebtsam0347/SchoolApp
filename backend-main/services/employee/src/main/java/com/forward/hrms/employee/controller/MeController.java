package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.employee.dto.request.CreateEmergencyContactRequest;
import com.forward.hrms.employee.dto.request.ProfilePictureFilenameRequest;
import com.forward.hrms.employee.dto.request.SetProfilePictureRequest;
import com.forward.hrms.employee.dto.request.UpdateEmergencyContactRequest;
import com.forward.hrms.employee.dto.request.UpdateMeRequest;
import com.forward.hrms.employee.dto.response.DisciplinaryActionListResponse;
import com.forward.hrms.employee.dto.response.EmergencyContactDto;
import com.forward.hrms.employee.dto.response.EmployeeCertificationDto;
import com.forward.hrms.employee.dto.response.EmployeeDocumentDto;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeHistoryDto;
import com.forward.hrms.employee.dto.response.EmployeeQualificationDto;
import com.forward.hrms.employee.dto.response.EmployeeSkillDto;
import com.forward.hrms.employee.dto.response.EmploymentContractDto;
import com.forward.hrms.employee.dto.response.ExitDto;
import com.forward.hrms.employee.dto.response.PriorExperienceDto;
import com.forward.hrms.employee.dto.response.SuspensionDto;
import com.forward.hrms.employee.dto.response.UploadUrlResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.service.ContractService;
import com.forward.hrms.employee.service.DisciplinaryActionService;
import com.forward.hrms.employee.service.EmergencyContactService;
import com.forward.hrms.employee.service.EmployeeCertificationService;
import com.forward.hrms.employee.service.EmployeeDocumentService;
import com.forward.hrms.employee.service.EmployeeQualificationService;
import com.forward.hrms.employee.service.EmployeeService;
import com.forward.hrms.employee.service.EmployeeSkillService;
import com.forward.hrms.employee.service.ExitService;
import com.forward.hrms.employee.service.PriorExperienceService;
import com.forward.hrms.employee.service.SuspensionService;
import com.forward.hrms.employee.dto.response.EmployeeDtoMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Self-serve",
        description = "Endpoints for the signed-in employee — view own record, patch contact fields, " +
                "list direct reports, manage own emergency contacts and profile picture, and read-only " +
                "views over the side tables HR populates (skills, qualifications, certifications, " +
                "prior experience, contracts, suspensions, exit and history).")
@RestController
@RequestMapping("/employees/me")
@RequiredArgsConstructor
public class MeController {

    private final EmployeeService employees;
    private final EmployeeRepository repo;
    private final DisciplinaryActionService disciplinary;
    private final EmergencyContactService emergencyContacts;
    private final EmployeeDocumentService documents;
    private final EmployeeSkillService skills;
    private final EmployeeQualificationService qualifications;
    private final EmployeeCertificationService certifications;
    private final PriorExperienceService priorExperience;
    private final ContractService contracts;
    private final SuspensionService suspensions;
    private final ExitService exits;

    // -------- core --------

    @Operation(summary = "Get my employee record",
            description = "Resolves the employee linked to the caller's user id.")
    @GetMapping
    public EmployeeDto me() {
        return employees.getByUserId(currentUserId());
    }

    @Operation(summary = "Patch a small set of contact fields on my record",
            description = "Phone, personal email, addresses, city. Name / CNIC / identity numbers stay HR-only.")
    @PatchMapping
    public EmployeeDto updateMe(@Valid @RequestBody UpdateMeRequest req) {
        return employees.updateMe(currentUserId(), req);
    }

    @Operation(summary = "List my direct reports",
            description = "Paginated; default 25 rows. Optional `q` matches the same fields as " +
                    "`/employees` (fullName, employeeCode, workEmail, phone, cnic — case-insensitive " +
                    "substring + pg_trgm fuzzy on name/code). Each row carries the standard read " +
                    "enrichments (profilePictureUrl, directReportsCount).")
    @GetMapping("/team")
    public PageResponse<EmployeeDto> myTeam(
            @Parameter(description = "Case-insensitive substring search across fullName, employeeCode, " +
                    "workEmail, phone and cnic.")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, 25, 200,
                org.springframework.data.domain.Sort.by("employeeCode").ascending());
        return employees.team(currentEmployeeId(), q, p);
    }

    @Operation(summary = "List employees in my department",
            description = "Paginated; same response shape as `/employees/me/team`. Optional `q` matches " +
                    "fullName, employeeCode, workEmail, phone and cnic. Optional `sectionId` narrows to " +
                    "one section (a section outside the caller's department -> empty); both filters " +
                    "combine.")
    @GetMapping("/department/employees")
    public PageResponse<EmployeeDto> myDepartmentEmployees(
            @Parameter(description = "Case-insensitive substring search across fullName, employeeCode, " +
                    "workEmail, phone and cnic.")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @Parameter(description = "One section of the caller's department.")
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sectionId,
            @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, 25, 200,
                org.springframework.data.domain.Sort.by("employeeCode").ascending());
        return employees.myDepartmentEmployees(currentUserId(), q, sectionId, p);
    }

    @Operation(summary = "Get my manager",
            description = "Returns the caller's manager. 404 if the caller has no manager " +
                    "assigned. Open to any authenticated employee — the ESS profile screen " +
                    "needs it to render the manager card without granting `employee.read`.")
    @GetMapping("/manager")
    public EmployeeDto myManager() {
        return employees.myManager(currentUserId());
    }

    @Operation(summary = "My disciplinary-action history (read-only)",
            description = "Includes WITHDRAWN rows for full transparency. WARNING counts in the response are " +
                    "UPHELD-only and bounded to the requested fiscal year.")
    @GetMapping("/disciplinary-actions")
    public DisciplinaryActionListResponse myDisciplinaryActions() {
        return disciplinary.listForEmployee(currentEmployeeId(), null, null, null);
    }

    // -------- emergency contacts (self-serve) --------

    @Operation(summary = "List my emergency contacts (primary first)")
    @GetMapping("/emergency-contacts")
    public List<EmergencyContactDto> myEmergencyContacts() {
        return emergencyContacts.list(currentEmployeeId());
    }

    @Operation(summary = "Add an emergency contact for myself")
    @PostMapping("/emergency-contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyContactDto addMyEmergencyContact(@Valid @RequestBody CreateEmergencyContactRequest req) {
        return emergencyContacts.create(currentEmployeeId(), req);
    }

    @Operation(summary = "Patch one of my emergency contacts")
    @PatchMapping("/emergency-contacts/{contactId}")
    public EmergencyContactDto updateMyEmergencyContact(@PathVariable long contactId,
                                                        @Valid @RequestBody UpdateEmergencyContactRequest req) {
        return emergencyContacts.update(currentEmployeeId(), contactId, req);
    }

    @Operation(summary = "Delete one of my emergency contacts")
    @DeleteMapping("/emergency-contacts/{contactId}")
    public ResponseEntity<Void> deleteMyEmergencyContact(@PathVariable long contactId) {
        emergencyContacts.delete(currentEmployeeId(), contactId);
        return ResponseEntity.noContent().build();
    }

    // -------- documents + profile picture (self-serve) --------

    @Operation(summary = "List my own documents (read-only)",
            description = "Paginated. Each row carries a short-lived signed download URL.")
    @GetMapping("/documents")
    public PageResponse<EmployeeDocumentDto> myDocuments(
            @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, 50, 200,
                org.springframework.data.domain.Sort.by("id").descending());
        return documents.list(currentEmployeeId(), p);
    }

    @Operation(summary = "Mint a pre-signed PUT URL for my profile picture")
    @PostMapping("/profile-picture/upload-url")
    public UploadUrlResponse profilePictureUploadUrl(@Valid @RequestBody ProfilePictureFilenameRequest req) {
        return documents.signProfilePictureUploadUrl(currentEmployeeId(), req.filename(), req.bytes());
    }

    @Operation(summary = "Set my profile picture once the upload completes")
    @PostMapping("/profile-picture")
    public EmployeeDto setMyProfilePicture(@Valid @RequestBody SetProfilePictureRequest req) {
        Employee e = documents.setProfilePicture(currentEmployeeId(), req.objectKey());
        return EmployeeDtoMapper.of(e);
    }

    // -------- read-only ESS side views (E1 + E2) --------

    @Operation(summary = "List my skills (read-only)")
    @GetMapping("/skills")
    public List<EmployeeSkillDto> mySkills() {
        return skills.list(currentEmployeeId());
    }

    @Operation(summary = "List my qualifications (read-only)")
    @GetMapping("/qualifications")
    public List<EmployeeQualificationDto> myQualifications() {
        return qualifications.list(currentEmployeeId());
    }

    @Operation(summary = "List my certifications (read-only)")
    @GetMapping("/certifications")
    public List<EmployeeCertificationDto> myCertifications() {
        return certifications.list(currentEmployeeId());
    }

    @Operation(summary = "List my prior employment (read-only)")
    @GetMapping("/prior-experience")
    public List<PriorExperienceDto> myPriorExperience() {
        return priorExperience.list(currentEmployeeId());
    }

    @Operation(summary = "My contract history (read-only)",
            description = "Most-recent first. The current ACTIVE contract sits at the top.")
    @GetMapping("/contracts")
    public List<EmploymentContractDto> myContracts() {
        return contracts.list(currentEmployeeId());
    }

    @Operation(summary = "My suspension history (read-only)",
            description = "Includes lifted rows for full transparency.")
    @GetMapping("/suspensions")
    public List<SuspensionDto> mySuspensions() {
        return suspensions.list(currentEmployeeId());
    }

    @Operation(summary = "My current or historical exit row (read-only)",
            description = "Returns 404 when the employee has never had an exit row.")
    @GetMapping("/exit")
    public ExitDto myExit() {
        return exits.get(currentEmployeeId());
    }

    @Operation(summary = "My org-assignment history (paginated, read-only)",
            description = "Most-recent first. Each row is `effective_from`..`effective_to` (null = current).")
    @GetMapping("/history")
    public PageResponse<EmployeeHistoryDto> myHistory(@Parameter(hidden = true) Pageable pageable) {
        return employees.history(currentEmployeeId(), pageable);
    }

    // -------- helpers --------

    private long currentEmployeeId() {
        long userId = currentUserId();
        return repo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NO_ESS, "no employee linked to this user"))
                .getId();
    }

    private long currentUserId() {
        CurrentUser u = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError(
                        com.forward.hrms.commons.core.error.CommonErrorCode.UNAUTHENTICATED,
                        "no authenticated user"));
        return u.userId();
    }
}

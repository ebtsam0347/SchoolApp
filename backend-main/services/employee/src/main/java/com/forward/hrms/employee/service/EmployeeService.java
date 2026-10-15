package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.request.AssignmentChangeRequest;
import com.forward.hrms.employee.dto.request.UpdateEmployeeRequest;
import com.forward.hrms.employee.dto.request.UpdateMeRequest;
import com.forward.hrms.employee.dto.response.*;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.HistoryChangeReason;
import com.forward.hrms.employee.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employees;
    private final EmployeeHistoryRepository historyRepo;
    private final EmployeeHistoryService history;
    private final CatalogValidationService catalogs;
    private final ManagerCycleChecker cycles;
    private final EmployeeDtoAssembler assembler;
    private final SectionRepository sectionRepo;
    private final DepartmentRepository departmentRepo;
    private final DesignationRepository designationRepo;
    private final GradeRepository gradeRepo;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final DepartmentService departmentService;
    private final DesignationService designationService;
    private final GradeService gradeService;
    private final SectionService sectionService;
    private final StorageUrlService storageUrls;

    /**
     * Picker hard caps — the FE caps its typeahead well below this.
     */
    private static final int PICKER_DEFAULT_LIMIT = 20;
    private static final int PICKER_MAX_LIMIT = 100;

    // ----- reads -----

    @Transactional(readOnly = true)
    public EmployeeDto get(long id) {
        return assembler.toDto(load(id), true);
    }


    @Transactional(readOnly = true)
    public List<EmployeeStatusDto> statusesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return employees.findStatusesByIdIn(ids);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> byIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<Long, Employee> byId = new LinkedHashMap<>();
        for (Employee e : employees.findAllById(ids)) byId.put(e.getId(), e);
        List<Employee> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return assembler.toDtos(ordered, false);
    }


    @Transactional(readOnly = true)
    public EmployeePickerResponse picker(String q, Integer limit) {
        int n = (limit == null || limit <= 0)
                ? PICKER_DEFAULT_LIMIT
                : Math.min(limit, PICKER_MAX_LIMIT);
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        Pageable p = (trimmed == null)
                ? PageRequest.of(0, n, org.springframework.data.domain.Sort.by("employeeCode").ascending())
                : PageRequest.of(0, n);
        Page<Employee> page = employees.findAll(
                EmployeeSpecifications.filters(EmploymentStatus.ACTIVE, null,
                        null, null, null, null, null,
                        null, trimmed, false),
                p);
        if (page.isEmpty()) return new EmployeePickerResponse(List.of());

        Map<Long, String> depNames = new java.util.HashMap<>();
        for (Department d : departmentRepo.findAllById(
                page.getContent().stream()
                        .map(Employee::getDepartmentId)
                        .filter(Objects::nonNull)
                        .toList())) {
            depNames.put(d.getId(), d.getName());
        }
        Map<Long, String> designationTitles = new java.util.HashMap<>();
        for (Designation d : designationRepo.findAllById(
                page.getContent().stream()
                        .map(Employee::getDesignationId)
                        .filter(Objects::nonNull)
                        .toList())) {
            designationTitles.put(d.getId(), d.getTitle());
        }
        Map<Long, String> sectionNames = new java.util.HashMap<>();
        for (Section s : sectionRepo.findAllById(
                page.getContent().stream()
                        .map(Employee::getSectionId)
                        .filter(Objects::nonNull)
                        .toList())) {
            sectionNames.put(s.getId(), s.getName());
        }
        List<EmployeePickerCandidate> rows = new java.util.ArrayList<>(page.getNumberOfElements());
        for (Employee e : page) {
            rows.add(new EmployeePickerCandidate(
                    e.getId(), e.getFullName(), e.getEmployeeCode(),
                    e.getDepartmentId() == null ? null : depNames.get(e.getDepartmentId()),
                    e.getDesignationId() == null ? null : designationTitles.get(e.getDesignationId()),
                    e.getSectionId() == null ? null : sectionNames.get(e.getSectionId()),
                    storageUrls.signAvatarGet(e.getProfilePictureKey())));
        }
        return new EmployeePickerResponse(rows);
    }

    @Transactional(readOnly = true)
    public EmployeeDto getByUserId(long userId) {
        Employee e = employees.findByUserId(userId)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
        return assembler.toDto(e, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDto> list(EmploymentStatus status,
                                          com.forward.hrms.employee.enums.EmploymentType employmentType,
                                          Long departmentId, Long sectionId, Long managerId,
                                          Long gradeId, Long designationId,
                                          Boolean hasEss, String q,
                                          Integer probationDueWithin, Boolean probationOverdue,
                                          boolean includeTerminated,
                                          Pageable pageable) {

        if (probationDueWithin != null && Boolean.TRUE.equals(probationOverdue)) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    EmployeeErrorCode.LIST_FILTER_CONFLICT,
                    "probationDueWithin and probationOverdue are mutually exclusive");
        }
        if (probationOverdue != null && probationOverdue) {
            LocalDate today = LocalDate.now();
            Page<Employee> page = employees.findByProbationDue(null, today.minusDays(1), pageable);
            return PageResponse.from(page, assembler.toDtos(page.getContent(), true));
        }
        if (probationDueWithin != null) {
            LocalDate today = LocalDate.now();
            // doc: "overdue counted as ≤0" — so we always include past-due rows in this
            // filter. minEndDate stays null; maxEndDate is today + N days.
            Page<Employee> page = employees
                    .findByProbationDue(null, today.plusDays(probationDueWithin), pageable);
            return PageResponse.from(page, assembler.toDtos(page.getContent(), true));
        }
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();

        Page<Employee> page = employees.findAll(
                EmployeeSpecifications.filters(status, employmentType,
                        departmentId, sectionId, managerId, gradeId, designationId,
                        hasEss, trimmed, includeTerminated),
                pageable);
        return PageResponse.from(page, assembler.toDtos(page.getContent(), false));
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDto> team(long employeeId, String q, Pageable pageable) {
        if (!employees.existsById(employeeId)) {
            throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found");
        }
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        Page<Employee> page = employees.findByManagerId(employeeId, trimmed, pageable);
        return PageResponse.from(page, assembler.toDtos(page.getContent(), false));
    }

    /**
     * ESS endpoint — returns the caller's manager, or 404 if there isn't one.
     */
    @Transactional(readOnly = true)
    public EmployeeDto myManager(long userId) {
        Employee me = employees.findByUserId(userId)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
        if (me.getManagerId() == null) {
            throw new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "no manager assigned");
        }
        Employee mgr = employees.findById(me.getManagerId())
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "manager not found"));
        return assembler.toDto(mgr, true);
    }

    /**
     * The caller's department, optionally narrowed to one {@code sectionId}
     * (ANDed with the department, so a foreign section yields an empty page).
     */
    @Transactional(readOnly = true)
    public PageResponse<EmployeeDto> myDepartmentEmployees(long userId, String q, Long sectionId,
                                                           Pageable pageable) {
        Employee me = employees.findByUserId(userId)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NO_ESS, "no employee linked to this user"));
        if (me.getDepartmentId() == null) {
            return PageResponse.from(Page.empty(pageable), List.of());
        }
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        Page<Employee> page = employees.findAll(
                EmployeeSpecifications.filters(null, null,
                        me.getDepartmentId(), sectionId, null, null, null,
                        null, trimmed, false),
                pageable);
        return PageResponse.from(page, assembler.toDtos(page.getContent(), false));
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeHistoryDto> history(long id, Pageable pageable) {
        // Existence check; the history list itself is paginated.
        load(id);
        return PageResponse.from(historyRepo
                .findAllByEmployeeIdOrderByEffectiveFromDesc(id, pageable)
                .map(EmployeeHistoryDtoMapper::of));
    }

    // ----- HR patch -----

    @Transactional
    public EmployeeDto update(long id, UpdateEmployeeRequest req) {
        Employee e = load(id);
        Map<String, Object> changes = new LinkedHashMap<>();

        java.time.LocalDate effectiveIssue = req.cnicIssueDate() != null
                ? req.cnicIssueDate() : e.getCnicIssueDate();
        java.time.LocalDate effectiveExpiry = req.cnicExpiryDate() != null
                ? req.cnicExpiryDate() : e.getCnicExpiryDate();
        if (effectiveIssue != null && effectiveExpiry != null
                && !effectiveExpiry.isAfter(effectiveIssue)) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    EmployeeErrorCode.VALIDATION_FAILED,
                    "CNIC expiry must be after issue date (post-update)");
        }

        applyIfPresent(changes, "fullName", req.fullName(), e.getFullName(), e::setFullName);
        applyIfPresent(changes, "cnic", req.cnic(), e.getCnic(), e::setCnic);
        applyIfPresent(changes, "cnicIssueDate", req.cnicIssueDate(), e.getCnicIssueDate(), e::setCnicIssueDate);
        applyIfPresent(changes, "cnicExpiryDate", req.cnicExpiryDate(), e.getCnicExpiryDate(), e::setCnicExpiryDate);
        applyIfPresent(changes, "ntn", req.ntn(), e.getNtn(), e::setNtn);
        applyIfPresent(changes, "insuranceNumber", req.insuranceNumber(), e.getInsuranceNumber(), e::setInsuranceNumber);
        applyIfPresent(changes, "sscNumber", req.sscNumber(), e.getSscNumber(), e::setSscNumber);
        applyIfPresent(changes, "eobiNumber", req.eobiNumber(), e.getEobiNumber(), e::setEobiNumber);
        applyIfPresent(changes, "dateOfBirth", req.dateOfBirth(), e.getDateOfBirth(), e::setDateOfBirth);
        applyIfPresent(changes, "gender", req.gender(), e.getGender(), e::setGender);
        applyIfPresent(changes, "maritalStatus", req.maritalStatus(), e.getMaritalStatus(), e::setMaritalStatus);
        applyIfPresent(changes, "fatherName", req.fatherName(), e.getFatherName(), e::setFatherName);
        applyIfPresent(changes, "motherName", req.motherName(), e.getMotherName(), e::setMotherName);
        applyIfPresent(changes, "spouseName", req.spouseName(), e.getSpouseName(), e::setSpouseName);
        applyIfPresent(changes, "religion", req.religion(), e.getReligion(), e::setReligion);
        applyIfPresent(changes, "bloodGroup", req.bloodGroup(), e.getBloodGroup(), e::setBloodGroup);
        applyIfPresent(changes, "personalEmail", req.personalEmail(), e.getPersonalEmail(), e::setPersonalEmail);
        applyIfPresent(changes, "workEmail", req.workEmail(), e.getWorkEmail(), e::setWorkEmail);
        applyIfPresent(changes, "phone", req.phone(), e.getPhone(), e::setPhone);
        applyIfPresent(changes, "permanentAddress", req.permanentAddress(), e.getPermanentAddress(), e::setPermanentAddress);
        applyIfPresent(changes, "currentAddress", req.currentAddress(), e.getCurrentAddress(), e::setCurrentAddress);
        applyIfPresent(changes, "profilePictureKey", req.profilePictureKey(), e.getProfilePictureKey(), e::setProfilePictureKey);
        applyIfPresent(changes, "referenceBy", req.referenceBy(), e.getReferenceBy(), e::setReferenceBy);
        if (req.employmentType() != null && req.employmentType() != e.getEmploymentType()) {
            changes.put("employmentType", req.employmentType().name());
            e.setEmploymentType(req.employmentType());
        }
        if (req.cityId() != null && !req.cityId().equals(e.getCityId())) {
            catalogs.validateAssignment(req.cityId(), null, null, null, null);
            changes.put("cityId", req.cityId());
            e.setCityId(req.cityId());
        }
        if (changes.isEmpty()) return EmployeeDtoMapper.of(e);

        publishUpdated(e, changes);
        audit.emit("employee", String.valueOf(e.getId()), "updated",
                null, changes, true);
        return EmployeeDtoMapper.of(e);
    }

    @Transactional
    public EmployeeDto updateMe(long userId, UpdateMeRequest req) {
        Employee e = employees.findByUserId(userId)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NO_ESS, "no employee linked to this user"));

        Map<String, Object> changes = new LinkedHashMap<>();
        applyIfPresent(changes, "phone", req.phone(), e.getPhone(), e::setPhone);
        applyIfPresent(changes, "personalEmail", req.personalEmail(), e.getPersonalEmail(), e::setPersonalEmail);
        applyIfPresent(changes, "permanentAddress", req.permanentAddress(),
                e.getPermanentAddress(), e::setPermanentAddress);
        applyIfPresent(changes, "currentAddress", req.currentAddress(),
                e.getCurrentAddress(), e::setCurrentAddress);
        if (req.cityId() != null && !req.cityId().equals(e.getCityId())) {
            catalogs.validateAssignment(req.cityId(), null, null, null, null);
            changes.put("cityId", req.cityId());
            e.setCityId(req.cityId());
        }

        if (changes.isEmpty()) return EmployeeDtoMapper.of(e);

        publishUpdated(e, changes);
        audit.emit("employee", String.valueOf(e.getId()), "self_updated",
                null, changes, true);
        return EmployeeDtoMapper.of(e);
    }

    // ----- assignment changes -----

    @Transactional
    public EmployeeDto changeManager(long id, AssignmentChangeRequest req) {

        employees.lockManagerChain();

        Employee e = load(id);
        Long oldId = e.getManagerId();
        Long newId = clear(req.targetId());
        if (Objects.equals(oldId, newId)) return EmployeeDtoMapper.of(e);

        if (newId != null) {
            Employee mgr = employees.findById(newId)
                    .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.MANAGER_NOT_FOUND, "manager not found"));

            if (mgr.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
                throw new ConflictError(EmployeeErrorCode.MANAGER_TERMINATED,
                        "manager " + newId + " is TERMINATED");
            }
        }
        cycles.verify(id, newId);
        e.setManagerId(newId);
        history.recordChange(e, HistoryChangeReason.MANAGER_CHANGE, req.notes());
        outbox.publish(EmployeeEvents.TOPIC, "employee:" + id,
                EmployeeEvents.EMPLOYEE_MANAGER_CHANGED,
                new EmployeeEvents.EmployeeManagerChanged(
                        id, oldId, newId, LocalDate.now()));
        audit.emit("employee", String.valueOf(id), "manager_changed",
                Map.of("managerId", String.valueOf(oldId)),
                Map.of("managerId", String.valueOf(newId)), true);
        return EmployeeDtoMapper.of(e);
    }

    @Transactional
    public EmployeeDto changeDepartment(long id, AssignmentChangeRequest req) {
        Employee e = load(id);
        Long oldDeptId = e.getDepartmentId();
        Long newDeptId = clear(req.targetId());
        if (Objects.equals(oldDeptId, newDeptId)) return EmployeeDtoMapper.of(e);

        catalogs.validateAssignment(null, newDeptId, null, null, null);
        e.setDepartmentId(newDeptId);
        Long oldSectionId = e.getSectionId();
        boolean sectionCleared = false;
        if (oldSectionId != null && (newDeptId == null || sectionMovedAway(oldSectionId, newDeptId))) {
            e.setSectionId(null);
            sectionCleared = true;
        }

        history.recordChange(e, HistoryChangeReason.DEPARTMENT_CHANGE, req.notes());
        outbox.publish(EmployeeEvents.TOPIC, "employee:" + id,
                EmployeeEvents.EMPLOYEE_DEPARTMENT_CHANGED,
                new EmployeeEvents.EmployeeDepartmentChanged(
                        id, oldDeptId, newDeptId, LocalDate.now()));
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("departmentId", String.valueOf(oldDeptId));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("departmentId", String.valueOf(newDeptId));
        if (sectionCleared) {
            before.put("sectionId", String.valueOf(oldSectionId));
            after.put("sectionId", "null");
            outbox.publish(EmployeeEvents.TOPIC, "employee:" + id,
                    EmployeeEvents.EMPLOYEE_SECTION_CHANGED,
                    new EmployeeEvents.EmployeeSectionChanged(
                            id, oldSectionId, null, LocalDate.now()));
        }
        audit.emit("employee", String.valueOf(id), "department_changed", before, after, true);
        return EmployeeDtoMapper.of(e);
    }

    @Transactional
    public EmployeeDto changeSection(long id, AssignmentChangeRequest req) {
        Employee e = load(id);
        Long oldId = e.getSectionId();
        Long newId = clear(req.targetId());
        if (Objects.equals(oldId, newId)) return EmployeeDtoMapper.of(e);

        // Section must be valid + match employee's current department.
        catalogs.validateAssignment(null, e.getDepartmentId(), newId, null, null);
        e.setSectionId(newId);
        history.recordChange(e, HistoryChangeReason.SECTION_CHANGE, req.notes());
        outbox.publish(EmployeeEvents.TOPIC, "employee:" + id,
                EmployeeEvents.EMPLOYEE_SECTION_CHANGED,
                new EmployeeEvents.EmployeeSectionChanged(
                        id, oldId, newId, LocalDate.now()));
        audit.emit("employee", String.valueOf(id), "section_changed",
                Map.of("sectionId", String.valueOf(oldId)),
                Map.of("sectionId", String.valueOf(newId)), true);
        return EmployeeDtoMapper.of(e);
    }

    @Transactional
    public EmployeeDto changeDesignation(long id, AssignmentChangeRequest req) {
        Employee e = load(id);
        Long oldId = e.getDesignationId();
        Long newId = clear(req.targetId());
        if (Objects.equals(oldId, newId)) return EmployeeDtoMapper.of(e);

        catalogs.validateAssignment(null, null, null, newId, null);
        e.setDesignationId(newId);
        history.recordChange(e, HistoryChangeReason.PROMOTION, req.notes());
        outbox.publish(EmployeeEvents.TOPIC, "employee:" + id,
                EmployeeEvents.EMPLOYEE_DESIGNATION_CHANGED,
                new EmployeeEvents.EmployeeDesignationChanged(
                        id, oldId, newId, LocalDate.now()));
        audit.emit("employee", String.valueOf(id), "designation_changed",
                Map.of("designationId", String.valueOf(oldId)),
                Map.of("designationId", String.valueOf(newId)), true,
                "Designation changed");
        return EmployeeDtoMapper.of(e);
    }

    @Transactional
    public EmployeeDto changeGrade(long id, AssignmentChangeRequest req) {
        Employee e = load(id);
        Long oldId = e.getGradeId();
        Long newId = clear(req.targetId());
        if (Objects.equals(oldId, newId)) return EmployeeDtoMapper.of(e);

        catalogs.validateAssignment(null, null, null, null, newId);
        e.setGradeId(newId);
        history.recordChange(e, HistoryChangeReason.PROMOTION, req.notes());
        outbox.publish(EmployeeEvents.TOPIC, "employee:" + id,
                EmployeeEvents.EMPLOYEE_GRADE_CHANGED,
                new EmployeeEvents.EmployeeGradeChanged(
                        id, oldId, newId, LocalDate.now()));
        audit.emit("employee", String.valueOf(id), "grade_changed",
                Map.of("gradeId", String.valueOf(oldId)),
                Map.of("gradeId", String.valueOf(newId)), true,
                "Grade changed");
        return EmployeeDtoMapper.of(e);
    }

    // ----- helpers -----

    Employee load(long id) {
        return employees.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
    }

    private void publishUpdated(Employee e, Map<String, Object> changes) {
        outbox.publish(EmployeeEvents.TOPIC, "employee:" + e.getId(),
                EmployeeEvents.EMPLOYEE_UPDATED,
                new EmployeeEvents.EmployeeUpdated(e.getId(), e.getUserId(), changes));
    }


    private boolean sectionMovedAway(Long sectionId, Long newDepartmentId) {
        if (sectionId == null || newDepartmentId == null) return false;
        return sectionRepo.findById(sectionId)
                .map(s -> !Objects.equals(s.getDepartment().getId(), newDepartmentId))
                .orElse(true);
    }

    private static Long clear(Long id) {
        return id != null && id == 0 ? null : id;
    }

    private static <T> void applyIfPresent(Map<String, Object> changes, String name,
                                           T newValue, T oldValue,
                                           java.util.function.Consumer<T> setter) {
        if (newValue == null) return;
        if (Objects.equals(newValue, oldValue)) return;
        setter.accept(newValue);
        changes.put(name, newValue);
    }

    public EmployeeTerminationInfoDto getForFinalTerminationSettlement(long employeeId) {
        Employee employee = load(employeeId);
        var departmentName = departmentService.loadActive(employee.getDepartmentId()).getName();
        var designationName = designationService.loadActive(employee.getDesignationId()).getTitle();
        var gradeName = gradeService.loadActive(employee.getGradeId()).getName();
        var gradeType = gradeService.loadActive(employee.getGradeId()).getGradeType();
        var sectionName = sectionService.loadActive(employee.getSectionId()).getName();

        return new EmployeeTerminationInfoDto(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                departmentName,
                sectionName,
                designationName,
                gradeName,
                gradeType,
                employee.getJoiningDate()
        );
    }
}

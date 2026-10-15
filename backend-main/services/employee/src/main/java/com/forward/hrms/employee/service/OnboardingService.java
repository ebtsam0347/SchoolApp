package com.forward.hrms.employee.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.employeeevents.EmployeeEvents;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.commons.saga.SagaState;
import com.forward.hrms.employee.client.AuthClient;
import com.forward.hrms.employee.dto.request.OnboardEmployeeRequest;
import com.forward.hrms.employee.dto.response.EmployeeDtoMapper;
import com.forward.hrms.employee.dto.response.OnboardEmployeeResponse;
import com.forward.hrms.employee.entity.EmergencyContact;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeCertification;
import com.forward.hrms.employee.entity.EmployeeDisability;
import com.forward.hrms.employee.entity.EmployeePriorExperience;
import com.forward.hrms.employee.entity.EmployeeQualification;
import com.forward.hrms.employee.entity.EmployeeSkill;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.HistoryChangeReason;
import com.forward.hrms.employee.repository.DisabilityRepository;
import com.forward.hrms.employee.repository.EmergencyContactRepository;
import com.forward.hrms.employee.repository.EmployeeCertificationRepository;
import com.forward.hrms.employee.repository.EmployeeDisabilityRepository;
import com.forward.hrms.employee.repository.EmployeePriorExperienceRepository;
import com.forward.hrms.employee.repository.EmployeeQualificationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmployeeSkillRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;
import com.forward.hrms.employee.repository.QualificationRepository;
import com.forward.hrms.employee.repository.SkillRepository;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class OnboardingService {

    private static final Set<Integer> ALLOWED_PROBATION = Set.of(30, 60, 90);
    private static final java.time.Period JOINING_DATE_FUTURE_BOUND = java.time.Period.ofDays(30);
    private static final java.time.Period JOINING_DATE_PAST_BOUND = java.time.Period.ofYears(5);
    private static final int MIN_AGE_AT_JOINING = 18;

    private final EmployeeRepository employees;
    private final EmploymentContractRepository contracts;
    private final EmployeeQualificationRepository employeeQualifications;
    private final EmployeePriorExperienceRepository priorExperience;
    private final EmployeeSkillRepository employeeSkills;
    private final EmployeeCertificationRepository certifications;
    private final EmergencyContactRepository emergencyContacts;
    private final EmployeeDisabilityRepository employeeDisabilities;
    private final QualificationRepository qualificationCatalog;
    private final SkillRepository skillCatalog;
    private final DisabilityRepository disabilityCatalog;
    private final EmployeeCodeService codes;
    private final EmployeeHistoryService history;
    private final CatalogValidationService catalogs;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final SagaService sagaService;
    private final ObjectMapper sagaJson;
    private final EmployeeLifecycleMetrics lifecycle;

    public OnboardingService(EmployeeRepository employees,
                             EmploymentContractRepository contracts,
                             EmployeeQualificationRepository employeeQualifications,
                             EmployeePriorExperienceRepository priorExperience,
                             EmployeeSkillRepository employeeSkills,
                             EmployeeCertificationRepository certifications,
                             EmergencyContactRepository emergencyContacts,
                             EmployeeDisabilityRepository employeeDisabilities,
                             QualificationRepository qualificationCatalog,
                             SkillRepository skillCatalog,
                             DisabilityRepository disabilityCatalog,
                             EmployeeCodeService codes,
                             EmployeeHistoryService history,
                             CatalogValidationService catalogs,
                             Outbox outbox,
                             AuditEmitter audit,
                             SagaService sagaService,
                             EmployeeLifecycleMetrics lifecycle,
                             @Qualifier("sagaObjectMapper") ObjectMapper sagaJson) {
        this.employees = employees;
        this.contracts = contracts;
        this.employeeQualifications = employeeQualifications;
        this.priorExperience = priorExperience;
        this.employeeSkills = employeeSkills;
        this.certifications = certifications;
        this.emergencyContacts = emergencyContacts;
        this.employeeDisabilities = employeeDisabilities;
        this.qualificationCatalog = qualificationCatalog;
        this.skillCatalog = skillCatalog;
        this.disabilityCatalog = disabilityCatalog;
        this.codes = codes;
        this.history = history;
        this.catalogs = catalogs;
        this.outbox = outbox;
        this.audit = audit;
        this.sagaService = sagaService;
        this.sagaJson = sagaJson;
        this.lifecycle = lifecycle;
    }

    @Transactional
    public OnboardEmployeeResponse onboard(OnboardEmployeeRequest req) {
        validate(req);

        Employee e = new Employee();
        e.setEmployeeCode(codes.allocate());
        e.setFullName(req.fullName());
        e.setCnic(req.cnic());
        e.setCnicIssueDate(req.cnicIssueDate());
        e.setCnicExpiryDate(req.cnicExpiryDate());
        e.setNtn(req.ntn());
        e.setInsuranceNumber(req.insuranceNumber());
        e.setSscNumber(req.sscNumber());
        e.setEobiNumber(req.eobiNumber());
        e.setDateOfBirth(req.dateOfBirth());
        e.setGender(req.gender());
        e.setMaritalStatus(req.maritalStatus());
        e.setFatherName(req.fatherName());
        e.setMotherName(req.motherName());
        e.setSpouseName(req.spouseName());
        e.setReligion(req.religion());
        e.setBloodGroup(req.bloodGroup());
        e.setPersonalEmail(req.personalEmail());
        e.setWorkEmail(req.workEmail());
        e.setPhone(req.phone());
        e.setPermanentAddress(req.permanentAddress());
        e.setCurrentAddress(req.currentAddress());
        e.setCityId(req.cityId());
        e.setJoiningDate(req.joiningDate());
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setEmploymentType(req.employmentType());
        e.setDepartmentId(req.departmentId());
        e.setSectionId(req.sectionId());
        e.setDesignationId(req.designationId());
        e.setGradeId(req.gradeId());
        e.setManagerId(req.managerId());
        e.setReferenceBy(req.referenceBy());
        employees.save(e);

        int probationDays = req.probationDays() == null ? 90 : req.probationDays();
        EmploymentContract c = new EmploymentContract();
        c.setEmployeeId(e.getId());
        c.setContractType(ContractType.PROBATION);
        c.setStartDate(req.joiningDate());
        c.setEndDate(req.joiningDate().plusDays(probationDays));
        c.setStatus(ContractStatus.ACTIVE);
        c.setProbationDays(probationDays);
        c.setExtended(false);
        contracts.save(c);

        history.seed(e, HistoryChangeReason.ONBOARD, null);

        persistOptionalArrays(e.getId(), req);

        outbox.publish(EmployeeEvents.TOPIC, "employee:" + e.getId(),
                EmployeeEvents.EMPLOYEE_CREATED,
                new EmployeeEvents.EmployeeCreated(
                        e.getId(), e.getUserId(), e.getEmployeeCode(), e.getFullName(),
                        e.getJoiningDate(), e.getDepartmentId(), e.getManagerId(),
                        req.provisionEss()));

        audit.emit("employee", String.valueOf(e.getId()), "onboarded",
                null,
                Map.of(
                        "employeeCode", e.getEmployeeCode(),
                        "fullName", e.getFullName(),
                        "joiningDate", e.getJoiningDate().toString(),
                        "essRequested", req.provisionEss(),
                        "salaryProvided", req.salary() != null,
                        "shiftProvided", req.shift() != null),
                true);

        Long sagaId = sagaNeeded(req) ? startOnboardingSaga(e, req) : null;
        if (sagaId == null) lifecycle.onboardStarted();

        return OnboardEmployeeResponse.of(EmployeeDtoMapper.of(e), sagaId);
    }

    private void persistOptionalArrays(long employeeId, OnboardEmployeeRequest req) {
        if (req.qualifications() != null) {
            for (var q : req.qualifications()) {
                EmployeeQualification eq = new EmployeeQualification();
                eq.setEmployeeId(employeeId);
                eq.setQualificationId(q.qualificationId());
                eq.setInstitution(q.institution());
                eq.setBoardOrUniversity(q.boardOrUniversity());
                eq.setYearOfCompletion(q.yearOfCompletion());
                eq.setGrade(q.grade());
                eq.setDocumentKey(q.documentKey());
                employeeQualifications.save(eq);
            }
        }
        if (req.experiences() != null) {
            for (var x : req.experiences()) {
                EmployeePriorExperience ex = new EmployeePriorExperience();
                ex.setEmployeeId(employeeId);
                ex.setEmployerName(x.employerName());
                ex.setDesignation(x.designation());
                ex.setStartDate(x.startDate());
                ex.setEndDate(x.endDate());
                ex.setResponsibilities(x.responsibilities());
                ex.setReasonForLeaving(x.reasonForLeaving());
                priorExperience.save(ex);
            }
        }
        if (req.skills() != null) {
            for (var s : req.skills()) {
                EmployeeSkill es = new EmployeeSkill();
                es.setEmployeeId(employeeId);
                es.setSkillId(s.skillId());
                es.setProficiency(s.proficiency());
                es.setYears(s.years());
                es.setNotes(s.notes());
                employeeSkills.save(es);
            }
        }
        if (req.certifications() != null) {
            for (var c : req.certifications()) {
                EmployeeCertification ec = new EmployeeCertification();
                ec.setEmployeeId(employeeId);
                ec.setName(c.name());
                ec.setIssuingBody(c.issuingBody());
                ec.setIssueDate(c.issueDate());
                ec.setExpiryDate(c.expiryDate());
                ec.setCredentialId(c.credentialId());
                ec.setDocumentKey(c.documentKey());
                certifications.save(ec);
            }
        }
        if (req.emergencyContacts() != null) {
            boolean primaryAssigned = false;
            for (var ec : req.emergencyContacts()) {
                EmergencyContact row = new EmergencyContact();
                row.setEmployeeId(employeeId);
                row.setName(ec.name());
                row.setRelation(ec.relation());
                row.setPhone(ec.phone());
                // First requested-primary wins; other primary flags get demoted so we
                // never insert two primary rows for the same employee.
                boolean isPrimary = ec.primary() && !primaryAssigned;
                row.setPrimary(isPrimary);
                if (isPrimary) primaryAssigned = true;
                emergencyContacts.save(row);
            }
        }
        if (req.disabilities() != null) {
            for (var d : req.disabilities()) {
                EmployeeDisability ed = new EmployeeDisability();
                ed.setEmployeeId(employeeId);
                ed.setDisabilityId(d.disabilityId());
                ed.setDescription(d.description());
                ed.setSeverity(d.severity());
                ed.setDocumentKey(d.documentKey());
                employeeDisabilities.save(ed);
            }
        }
    }

    private boolean sagaNeeded(OnboardEmployeeRequest req) {
        return req.provisionEss() || req.salary() != null || req.shift() != null;
    }

    private Long startOnboardingSaga(Employee e, OnboardEmployeeRequest req) {
        ObjectNode payload = sagaJson.createObjectNode();
        payload.put("employeeId", e.getId());
        payload.put("fullName", e.getFullName());
        payload.put("joiningDate", e.getJoiningDate().toString());

        java.util.Map<String, String> secrets = java.util.Map.of();
        if (req.provisionEss()) {
            payload.put("provisionEss", true);
            payload.put("username", req.username());
            payload.put("email", req.email() != null ? req.email() : req.workEmail());
            secrets = java.util.Map.of("temporaryPassword", req.temporaryPassword());
        } else {
            payload.put("provisionEss", false);
        }

        if (req.salary() != null) {
            ObjectNode salary = payload.putObject("salary");
            salary.put("basic", req.salary().basic());
            if (req.salary().components() != null && !req.salary().components().isEmpty()) {
                ArrayNode comps = salary.putArray("components");
                for (var c : req.salary().components()) {
                    ObjectNode line = comps.addObject();
                    line.put("componentId", c.componentId());
                    if (c.amount() != null) line.put("amount", c.amount());
                    if (c.notes() != null) line.put("notes", c.notes());
                }
            }
            if (req.salary().bank() != null) {
                ObjectNode bank = salary.putObject("bank");
                bank.put("bankId", req.salary().bank().bankId());
                if (req.salary().bank().branch() != null) bank.put("branch", req.salary().bank().branch());
                bank.put("accountTitle", req.salary().bank().accountTitle());
                bank.put("iban", req.salary().bank().iban());
                if (req.salary().bank().accountNumber() != null) {
                    bank.put("accountNumber", req.salary().bank().accountNumber());
                }
            }
        }

        if (req.shift() != null) {
            ObjectNode shift = payload.putObject("shift");
            shift.put("shiftId", req.shift().shiftId());
        }

        SagaContext.writeIdentity(payload, AuthClient.currentRequestIdentity());

        SagaState saga = secrets.isEmpty()
                ? sagaService.start(OnboardingSaga.NAME, "employee:" + e.getId(), payload)
                : sagaService.startWithSecrets(OnboardingSaga.NAME, "employee:" + e.getId(),
                        payload, secrets, null);
        lifecycle.onboardStarted();
        return saga.getId();
    }



    private void validate(OnboardEmployeeRequest req) {
        if (req.joiningDate() == null) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED, "joiningDate is required");
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        if (req.joiningDate().isAfter(today.plus(JOINING_DATE_FUTURE_BOUND))
                || req.joiningDate().isBefore(today.minus(JOINING_DATE_PAST_BOUND))) {
            throw new UnprocessableError(EmployeeErrorCode.JOINING_DATE_OUT_OF_RANGE,
                    "joiningDate must be within 30 days in the future and 5 years in the past");
        }
        if (req.dateOfBirth() != null) {
            int ageAtJoining = java.time.Period.between(req.dateOfBirth(), req.joiningDate()).getYears();
            if (ageAtJoining < MIN_AGE_AT_JOINING) {
                throw new UnprocessableError(EmployeeErrorCode.MIN_AGE_NOT_MET,
                        "employee must be at least " + MIN_AGE_AT_JOINING + " on joiningDate");
            }
        }
        if (req.cnicIssueDate() != null && req.cnicExpiryDate() != null
                && !req.cnicExpiryDate().isAfter(req.cnicIssueDate())) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "CNIC expiry must be after issue date");
        }
        if (req.probationDays() != null && !ALLOWED_PROBATION.contains(req.probationDays())) {
            throw new UnprocessableError(EmployeeErrorCode.PROBATION_DAYS_INVALID,
                    "probationDays must be one of 30, 60, 90");
        }
        catalogs.validateAssignment(req.cityId(), req.departmentId(), req.sectionId(),
                req.designationId(), req.gradeId());
        if (req.managerId() != null) {
            Employee mgr = employees.findById(req.managerId())
                    .orElseThrow(() -> new ConflictError(
                            EmployeeErrorCode.MANAGER_NOT_FOUND, "manager not found"));
            if (mgr.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
                throw new ConflictError(EmployeeErrorCode.MANAGER_TERMINATED,
                        "manager " + req.managerId() + " is TERMINATED");
            }
        }
        if (req.provisionEss()) {
            if (req.username() == null || req.username().isBlank()) {
                throw new UnprocessableError(EmployeeErrorCode.UPSTREAM_AUTH_FAILED,
                        "username is required when provisionEss=true");
            }
            if (req.temporaryPassword() == null || req.temporaryPassword().isBlank()) {
                throw new UnprocessableError(EmployeeErrorCode.UPSTREAM_AUTH_FAILED,
                        "temporaryPassword is required when provisionEss=true");
            }

        }
        validateQualifications(req);
        validateExperiences(req);
        validateSkills(req);
        validateDisabilities(req);
        validateEmergencyContacts(req);
    }

    private void validateQualifications(OnboardEmployeeRequest req) {
        if (req.qualifications() == null || req.qualifications().isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        for (var q : req.qualifications()) {
            if (!ids.add(q.qualificationId())) {
                throw new ConflictError(EmployeeErrorCode.QUALIFICATION_DUPLICATE,
                        "duplicate qualification in request");
            }
        }
        Set<Long> found = new HashSet<>();
        qualificationCatalog.findAllById(ids).forEach(qc -> {
            if (!qc.isActive()) {
                throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "qualification is inactive");
            }
            found.add(qc.getId());
        });
        if (!found.containsAll(ids)) {
            throw new NotFoundError(EmployeeErrorCode.QUALIFICATION_NOT_FOUND, "qualification not found");
        }
    }

    private void validateExperiences(OnboardEmployeeRequest req) {
        if (req.experiences() == null) return;
        for (var x : req.experiences()) {
            if (x.endDate().isBefore(x.startDate())) {
                throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                        "prior experience endDate must be on or after startDate");
            }
        }
    }


    private void validateEmergencyContacts(OnboardEmployeeRequest req) {
        if (req.emergencyContacts() == null || req.emergencyContacts().isEmpty()) return;
        Set<String> seen = new HashSet<>();
        for (var ec : req.emergencyContacts()) {
            String key = (ec.name() == null ? "" : ec.name().trim().toLowerCase(java.util.Locale.ROOT))
                    + "|" + (ec.phone() == null ? "" : ec.phone().trim());
            if (!seen.add(key)) {
                throw new ConflictError(EmployeeErrorCode.EMERGENCY_CONTACT_DUPLICATE,
                        "duplicate emergency contact (same name + phone) in request");
            }
        }
    }

    private void validateSkills(OnboardEmployeeRequest req) {
        if (req.skills() == null || req.skills().isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        List<Long> dupCheck = new ArrayList<>();
        for (var s : req.skills()) {
            ids.add(s.skillId());
            dupCheck.add(s.skillId());
        }
        if (dupCheck.size() != ids.size()) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_SKILL_DUPLICATE,
                    "duplicate skill in request");
        }
        Set<Long> found = new HashSet<>();
        skillCatalog.findAllById(ids).forEach(sc -> {
            if (!sc.isActive()) {
                throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "skill is inactive");
            }
            found.add(sc.getId());
        });
        if (!found.containsAll(ids)) {
            throw new NotFoundError(EmployeeErrorCode.SKILL_NOT_FOUND, "skill not found");
        }
    }

    private void validateDisabilities(OnboardEmployeeRequest req) {
        if (req.disabilities() == null || req.disabilities().isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        List<Long> dupCheck = new ArrayList<>();
        for (var d : req.disabilities()) {
            ids.add(d.disabilityId());
            dupCheck.add(d.disabilityId());
        }
        if (dupCheck.size() != ids.size()) {
            throw new ConflictError(EmployeeErrorCode.EMPLOYEE_DISABILITY_DUPLICATE,
                    "duplicate disability in request");
        }
        Set<Long> found = new HashSet<>();
        disabilityCatalog.findAllById(ids).forEach(dc -> {
            if (!dc.isActive()) {
                throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, "disability is inactive");
            }
            found.add(dc.getId());
        });
        if (!found.containsAll(ids)) {
            throw new NotFoundError(EmployeeErrorCode.DISABILITY_NOT_FOUND, "disability not found");
        }
    }
}

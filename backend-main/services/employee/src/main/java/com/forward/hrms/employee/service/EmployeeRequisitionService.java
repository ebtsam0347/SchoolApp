package com.forward.hrms.employee.service;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.service.ApprovalInboxQueries;
import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.request.*;
import com.forward.hrms.employee.dto.response.RequisitionDto;
import com.forward.hrms.employee.entity.*;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.RequisitionStatus;
import com.forward.hrms.employee.enums.RequisitionType;
import com.forward.hrms.employee.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeRequisitionService {

    private final EmployeeRequisitionRepository employeeRequisitionRepository;
    private final EmployeeRequisitionDetailRepository employeeRequisitionDetailRepository;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final SectionRepository sectionRepository;
    private final DesignationRepository designationRepository;
    private final GradeRepository gradeRepository;
    private final ApprovalService approvals;
    private final ApprovalInboxService approvalInbox;
    private final AuditEmitter audit;

    @Transactional
    public RequisitionDto createNewHiring(EmployeeRequisitionRequestNewHiring req) {
        CurrentUser caller = requireCaller();
        if (!caller.hasRole("MANAGER") && !caller.hasRole("TEAM_REQUEST_GENERATION")) {
            throw new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "MANAGER or TEAM_REQUEST_GENERATION role is required to generate requisitions");
        }
        Employee actorEmployee = employees.findByUserId(caller.userId())
                .orElseThrow(() -> new ForbiddenError(
                        EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "current user is not linked with an employee profile"));
        //check user has team request permission and has same department as it sent in the request body
        if (!Objects.equals(actorEmployee.getDepartmentId(), req.departmentId())) {
            throw new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "request.departmentId must match the current user's department");
        }

        //check details list is given or not
        List<EmployeeRequisitionNewHiringRequestDetail> details = requireDetailsNewHiring(req.details());

        //validating these value must be given in every row of detail for new hiring requisition
        details.forEach(d -> {
            if (d.sectionId() == null) {
                throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "sectionId is required for each requisition detail");
            }
            if(d.designationId() == null) {
                throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "designationId is required for each requisition detail");
            }
            if(d.gradeId() == null) {
                throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "gradeId is required for each requisition detail");
            }
        });

        //validating sections. it must belong to same department
        Set<Long> sectionIdsForDepartment = sectionRepository.findAllByDepartmentIdOrderByNameAsc(req.departmentId())
                .stream()
                .map(Section::getId)
                .collect(Collectors.toSet());

        details.forEach(d -> {
            if (!sectionIdsForDepartment.contains(d.sectionId())) {
                throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "sectionId: " + d.sectionId() + " does not belongs to department: id= " + req.departmentId());
            }
        });


        //checking provided sectionIds are valid
        Set<Long> sectionIds = sectionRepository.findAllSectionIds();
        details.forEach(d -> {
            if (!sectionIds.contains(d.sectionId())) {
                throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "sectionId: " + d.sectionId() +" does not exist.");
            }
        });

        //check provided designationId are valid
        Set<Long> designationIds = designationRepository.findAllDesignationIds();
        details.forEach(d -> {
            if (!designationIds.contains(d.designationId())) {
                throw new UnprocessableError(EmployeeErrorCode.DESIGNATION_NOT_FOUND,
                        "designationId: " + d.designationId() +" does not exist.");
            }
        });

        //check all provided gradeId are valid
        Set<Long> gradeIds = gradeRepository.findAllGradeIds();
        details.forEach(d -> {
            if (!gradeIds.contains(d.gradeId())) {
                throw new UnprocessableError(EmployeeErrorCode.GRADE_NOT_FOUND,
                        "gradeId: " + d.gradeId() +" does not exist.");
            }
        });

        //will check all provided shifts are valid in future if needed

        long totalPositions = totalPositionsNewHiring(details);

        EmployeeRequisition r = new EmployeeRequisition();
        r.setType(RequisitionType.NEW_HIRE);
        r.setStatus(RequisitionStatus.DRAFT);
        r.setDepartmentId(req.departmentId());
        r.setMinimumEducation(req.minimumEducation());
        r.setMinimumExperience(req.minimumExperience());
        r.setMinimumSkill(req.minimumSkill());
        r.setJobDescription(req.jobDescription());
        r.setTotalPositionsHod(totalPositions);
        r.setRequestReason(req.requestReason());
        r.setHodNotes(req.hodNotes());
        r.setRequestedByUserId(caller.userId());
        employeeRequisitionRepository.save(r);
        long requisitionId = r.getId();

        for (EmployeeRequisitionNewHiringRequestDetail detail : details) {
            long count = detail.noOfPosition() == null ? 0 : detail.noOfPosition();
            if (count < 1) continue;
            for (long i = 0; i < count; i++) {
                EmployeeRequisitionDetail row = new EmployeeRequisitionDetail();
                row.setRequisitionHeadId(requisitionId);
                row.setSectionId(detail.sectionId());
                row.setDesignationId(detail.designationId());
                row.setGradeId(detail.gradeId());
                row.setEmploymentType(detail.employmentType());
                row.setEmployeeCategory(detail.employeeCategory());
                row.setRequiredUntil(detail.requiredDate());
                row.setEmployeeGender(detail.employeeGender());
                row.setShiftId(detail.shiftId());
                employeeRequisitionDetailRepository.save(row);
            }
        }

        audit.emit("requisition", String.valueOf(r.getId()), "created",
                null, snapshot(r), true);

        //adding feature to open a approval step for this request
        EmployeeRequisition employeeRequisition = requireDraft(r.getId());
        Long callerEmployeeId = employees.findByUserId(caller.userId())
                        .map(Employee::getId)
                                .orElse(null);
        if(callerEmployeeId == null){
            throw new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "current user is not linked with an employee profile");
        }
        r.setStatus(RequisitionStatus.PENDING_APPROVAL);
        approvals.openChain(requestTypeFor(employeeRequisition.getType()), employeeRequisition.getId(),  callerEmployeeId);
        return dto(r);
    }

    @Transactional
    public RequisitionDto createReplacement(EmployeeRequisitionRequestReplacement req) {
        CurrentUser caller = requireCaller();
        if (!caller.hasRole("MANAGER") && !caller.hasRole("TEAM_REQUEST_GENERATION")) {
            throw new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "MANAGER or TEAM_REQUEST_GENERATION role is required to generate requisitions");
        }

        Employee actorEmployee = employees.findByUserId(caller.userId())
                .orElseThrow(() -> new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "current user is not linked with an employee profile"));
        Long actorEmployeeDepartmentId = actorEmployee.getDepartmentId();


        List<EmployeeRequisitionReplacementRequestDetail> details = requireDetailsReplacement(req.details());
        long totalPositions = totalPositionsReplacement(details);
        Map<Long, Employee> replacingEmployees = validateReplacementDetails(details, actorEmployeeDepartmentId);

        EmployeeRequisition r = new EmployeeRequisition();
        r.setType(RequisitionType.REPLACEMENT);
        r.setStatus(RequisitionStatus.DRAFT);
        r.setDepartmentId(actorEmployeeDepartmentId);
        r.setMinimumEducation(req.minimumEducation());
        r.setMinimumExperience(req.minimumExperience());
        r.setMinimumSkill(req.minimumSkill());
        r.setJobDescription(req.jobDescription());
        r.setTotalPositionsHod(totalPositions);
        r.setRequestReason(req.requestReason());
        r.setHodNotes(req.hodNotes());
        r.setRequestedByUserId(caller.userId());
        employeeRequisitionRepository.save(r);
        long requisitionId = r.getId();

        for (EmployeeRequisitionReplacementRequestDetail detail : details) {
            Employee replacingEmployee = replacingEmployees.get(detail.replacingEmployeeId());

            EmployeeRequisitionDetail row = new EmployeeRequisitionDetail();
            row.setRequisitionHeadId(requisitionId);
            row.setReplacingEmployeeId(detail.replacingEmployeeId());
            row.setSectionId(replacingEmployee.getSectionId());
            row.setDesignationId(replacingEmployee.getDesignationId());
            row.setGradeId(replacingEmployee.getGradeId());
            row.setEmploymentType(detail.employmentType());
            row.setEmployeeCategory(detail.employeeCategory());
            row.setRequiredUntil(detail.requiredDate());
            row.setEmployeeGender(detail.employeeGender());
            row.setShiftId(detail.shiftId());
            employeeRequisitionDetailRepository.save(row);

        }

        audit.emit("requisition", String.valueOf(r.getId()), "created",
                null, snapshot(r), true);

        //adding feature to open a approval step for this request
        EmployeeRequisition employeeRequisition = requireDraft(r.getId());
        Long callerEmployeeId = employees.findByUserId(caller.userId())
                .map(Employee::getId)
                .orElse(null);
        if(callerEmployeeId == null){
            throw new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "current user is not linked with an employee profile");
        }
        r.setStatus(RequisitionStatus.PENDING_APPROVAL);
        approvals.openChain(requestTypeFor(employeeRequisition.getType()), employeeRequisition.getId(),  callerEmployeeId);
        return dto(r);
    }


    @Transactional
    public RequisitionDto update(long id, UpdateRequisitionRequest req) {
        EmployeeRequisition r = requireDraft(id);
        Map<String, Object> before = snapshot(r);

        if (req.departmentId() != null) r.setDepartmentId(req.departmentId());
        if (req.reason() != null && !req.reason().isBlank()) r.setRequestReason(req.reason());
        if (req.notes() != null) r.setHodNotes(req.notes());

        audit.emit("requisition", String.valueOf(r.getId()), "updated",
                before, snapshot(r), false);
        return dto(r);
    }

    /**
     * Approve the current pending stage. The OutcomeHandler flips the
     * requisition status to APPROVED only when this is the final stage.
     *
     * <p>Approving stage 1 closes the Manufacturing Excellence lean review, so
     * {@code total_positions_lean} is settled here as the count of detail rows
     * still standing. That covers every case in one place: a NEW_HIRE the
     * reviewer never trimmed keeps its full HOD total, a trimmed one keeps what
     * survived, and a REPLACEMENT — which can never be trimmed — always keeps
     * all of its rows. Stages 2 and 3 neither add nor remove positions, so the
     * total is untouched from there on.
     */
    @Transactional
    public RequisitionDto approve(long id) {
        EmployeeRequisition r = requireStatus(id, RequisitionStatus.PENDING_APPROVAL);
        String requestType = requestTypeFor(r.getType());
        boolean closingLeanReview = approvals.currentPending(requestType, id)
                .map(step -> step.getStageNo() == LEAN_REVIEW_STAGE_NO)
                .orElse(false);

        approvals.approve(requestType, id, null);

        if (closingLeanReview) {
            r.setTotalPositionsLean(employeeRequisitionDetailRepository.countRemainingAfterLean(id));
        }
        return dto(find(id));
    }

    /**
     * Reject at the current pending stage. The OutcomeHandler flips status to REJECTED.
     */
    @Transactional
    public RequisitionDto reject(long id, RejectRequisitionRequest req) {
        EmployeeRequisition r = requireStatus(id, RequisitionStatus.PENDING_APPROVAL);
        approvals.reject(requestTypeFor(r.getType()), id, req.reason());
        return dto(find(id));
    }

    @Transactional
    public RequisitionDto cancel(long id) {
        EmployeeRequisition r = find(id);
        if (r.getStatus() != RequisitionStatus.DRAFT
                && r.getStatus() != RequisitionStatus.PENDING_APPROVAL) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "only DRAFT or PENDING_APPROVAL requisitions can be cancelled");
        }
        CurrentUser caller = requireCaller();
        if (!caller.hasPermission("employee.admin")
                && !Objects.equals(caller.userId(), r.getRequestedByUserId())) {
            throw new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "only the requester or an admin can cancel this requisition");
        }
        if (r.getStatus() == RequisitionStatus.PENDING_APPROVAL) {
            approvals.cancelChain(requestTypeFor(r.getType()), id);
        }
        r.setStatus(RequisitionStatus.CANCELLED);
        audit.emit("requisition", String.valueOf(r.getId()), "cancelled",
                null, Map.of("status", "CANCELLED"), true);
        return dto(r);
    }

    @Transactional
    public RequisitionDto fulfill(long id) {
        EmployeeRequisition r = requireStatus(id, RequisitionStatus.APPROVED);
        r.setStatus(RequisitionStatus.FULFILLED);
        audit.emit("requisition", String.valueOf(r.getId()), "fulfilled",
                null, Map.of("status", "FULFILLED"), true);
        return dto(r);
    }

    @Transactional(readOnly = true)
    public RequisitionDto get(long id) {
        return dto(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<RequisitionDto> list(RequisitionStatus status,
                                             RequisitionType type,
                                             Long departmentId,
                                             Pageable pageable) {
        return PageResponse.from(
                employeeRequisitionRepository.search(status, type, departmentId, pageable).map(this::dto));
    }

    @Transactional(readOnly = true)
    public PageResponse<RequisitionDto> requisitionListSelfOrWithInDepartment(RequisitionStatus status,
                                             RequisitionType type,
                                             Pageable pageable) {
        CurrentUser currentUser = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required"));
        Long currentUserId = currentUser.userId();
        Long callerDeptId = employees.findByUserId(currentUserId)
                .map(Employee::getDepartmentId)
                .orElseThrow(() -> new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "current user is not linked with an employee profile"));

        return PageResponse.from(
                employeeRequisitionRepository.getRequisitionListSelfOrWithInDepartment(status, type, callerDeptId, currentUserId, pageable).map(this::dto));
    }


    @Transactional(readOnly = true)
    public PageResponse<RequisitionDto> requisitionListHr(RequisitionStatus status, RequisitionType type, Long departmentId,
                                                          Long id, String jobDescription, Pageable pageable) {
        return PageResponse.from(
                employeeRequisitionRepository.hrListRequisition(status, type, departmentId, id, jobDescription, pageable)
                .map(this::dto));
    }

    /**
     * Pending-approval inbox for the caller — stage-aware, same shape as
     * LeaveService/OvertimeService/RegularizationService. Requisitions run
     * two chains (NEW_HIRE/REPLACEMENT), so the inbox ids are merged across
     * both types before paginating — same pattern as FactoryTourService.
     * Date filter windows on createdAt (requisitions have no natural
     * from/to span).
     */
    @Transactional(readOnly = true)
    public PageResponse<RequisitionDto> pendingApprovals(LocalDate from, LocalDate to, String q,
                                                         RequisitionType type, Long departmentId,
                                                         Pageable pageable) {
        CurrentUser caller = requireCaller();
        Set<Long> ids = new LinkedHashSet<>();
        if (type != RequisitionType.REPLACEMENT) {
            ids.addAll(approvalInbox.requestIdsInInbox("REQUISITION_NEW_HIRE", caller));
        }
        if (type != RequisitionType.NEW_HIRE) {
            ids.addAll(approvalInbox.requestIdsInInbox("REQUISITION_REPLACEMENT", caller));
        }
        return ApprovalInboxQueries.findPending(
                List.copyOf(ids),
                idList -> filterInbox(employeeRequisitionRepository.findAllById(idList), q, departmentId),
                EmployeeRequisition::getId,
                r -> r.getCreatedAt().toLocalDate(), r -> r.getCreatedAt().toLocalDate(),
                from, to, pageable, this::dto);
    }

    /**
     * Inbox filter: {@code q} matches the request id exactly (a non-numeric
     * {@code q} matches nothing — requisitions carry no code or employee to
     * search); {@code departmentId} is exact. Both optional, ANDed.
     */
    private static List<EmployeeRequisition> filterInbox(List<EmployeeRequisition> rows, String q,
                                                         Long departmentId) {
        String needle = q == null ? "" : q.trim();
        if (needle.isEmpty() && departmentId == null) return rows;
        boolean numeric = !needle.isEmpty() && needle.length() <= 18
                && needle.chars().allMatch(Character::isDigit);
        if (!needle.isEmpty() && !numeric) return List.of();
        Long idNeedle = numeric ? Long.valueOf(needle) : null;
        return rows.stream()
                .filter(r -> idNeedle == null || idNeedle.equals(r.getId()))
                .filter(r -> departmentId == null || departmentId.equals(r.getDepartmentId()))
                .toList();
    }

    /**
     * Entity -> DTO mapping lives here rather than on the record: the DTO
     * moved to services/api so BFF can deserialize it as a typed response,
     * and api can't depend on this service's JPA entity. Instance method
     * (not static) because it needs {@link #approvals} to pull the real
     * commons-approval chain — same currentStage/steps/actions shape as
     * leave/overtime/regularization/factory-tours.
     * <p>The two fill counts are derived from the detail rows, not stored on the
     * header — nothing writes them, they follow whatever the assign endpoint recorded.
     */
    private RequisitionDto dto(EmployeeRequisition r) {
        List<ApprovalStep> chain = approvals.findChain(requestTypeFor(r.getType()), r.getId());
        return new RequisitionDto(
                r.getId(), r.getType(), r.getStatus(),
                r.getDepartmentId(),
                r.getMinimumEducation(),
                r.getMinimumExperience(),
                r.getMinimumSkill(),
                r.getJobDescription(),
                r.getTotalPositionsHod(),
                r.getTotalPositionsLean(),
                employeeRequisitionDetailRepository.countWithNewEmployeeCode(r.getId()),
                employeeRequisitionDetailRepository.countWithTemporaryEmployeeReferenceOnly(r.getId()),
                r.getRequestReason(),
                r.getHodNotes(),
                r.getRequestedByUserId(),
                r.getApprovedByUserId(), r.getApprovedAt(),
                r.getRejectReason(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getCreatedBy(),
                currentStage(chain), stepsDto(chain), actionsForCaller(r, chain));
    }

    private CurrentStageDto currentStage(List<ApprovalStep> chain) {
        return chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst()
                .map(s -> new CurrentStageDto(s.getStageNo(), s.getLabel(), s.getAssignedTo(), null))
                .orElse(null);
    }

    private List<ApprovalStepDto> stepsDto(List<ApprovalStep> chain) {
        List<ApprovalStepDto> out = new ArrayList<>(chain.size());
        for (ApprovalStep s : chain) {
            out.add(new ApprovalStepDto(
                    s.getStageNo(), s.getLabel(), s.getAssignedTo(), null,
                    s.getStatus().name(), s.getDecidedBy(), null, s.getDecidedAt(), s.getNote()));
        }
        return out;
    }

    private List<String> actionsForCaller(EmployeeRequisition r, List<ApprovalStep> chain) {
        return CurrentUserHolder.get().map(user -> actionsFor(r, chain, user)).orElse(List.of());
    }

    private List<String> actionsFor(EmployeeRequisition r, List<ApprovalStep> chain, CurrentUser user) {
        boolean ownerOrAdmin = user.hasPermission("employee.admin")
                || Objects.equals(user.userId(), r.getRequestedByUserId());
        List<String> out = new ArrayList<>(3);
        if (r.getStatus() == RequisitionStatus.DRAFT && ownerOrAdmin) {
            out.add("SUBMIT");
        }
        if (r.getStatus() == RequisitionStatus.PENDING_APPROVAL) {
            ApprovalStep current = chain.stream()
                    .filter(s -> s.getStatus() == StepStatus.PENDING).findFirst().orElse(null);
            if (current != null && approvals.canAct(requestTypeFor(r.getType()), current, user)) {
                out.add("APPROVE");
                out.add("REJECT");
            }
        }
        if ((r.getStatus() == RequisitionStatus.DRAFT || r.getStatus() == RequisitionStatus.PENDING_APPROVAL)
                && ownerOrAdmin) {
            out.add("CANCEL");
        }
        if (r.getStatus() == RequisitionStatus.APPROVED && user.hasPermission("employee.admin")) {
            out.add("FULFILL");
        }
        return out;
    }

    private EmployeeRequisition find(long id) {
        return employeeRequisitionRepository.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.REQUISITION_NOT_FOUND, "requisition not found"));
    }

    private EmployeeRequisition requireDraft(long id) {
        EmployeeRequisition r = find(id);
        if (r.getStatus() != RequisitionStatus.DRAFT) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "requisition is not in DRAFT status");
        }
        return r;
    }

    private EmployeeRequisition requireStatus(long id, RequisitionStatus expected) {
        EmployeeRequisition r = find(id);
        if (r.getStatus() != expected) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "requisition status is " + r.getStatus() + ", expected " + expected);
        }
        return r;
    }

    /**
     * Stage 1 of both requisition chains is the Manufacturing Excellence lean
     * review (see V0016) — the only stage at which the position count can change.
     */
    static final int LEAN_REVIEW_STAGE_NO = 1;

    /** REPLACEMENT and NEW_HIRE run different approval chains — see V0016. */
    static String requestTypeFor(RequisitionType type) {
        return type == RequisitionType.REPLACEMENT ? "REQUISITION_REPLACEMENT" : "REQUISITION_NEW_HIRE";
    }

    private static CurrentUser requireCaller() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required"));
    }

    private static Map<String, Object> snapshot(EmployeeRequisition r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", r.getType());
        m.put("status", r.getStatus());
        m.put("departmentId", r.getDepartmentId());
        m.put("totalPositionsHod", r.getTotalPositionsHod());
        m.put("requestReason", r.getRequestReason());
        return m;
    }

    private static List<EmployeeRequisitionNewHiringRequestDetail> requireDetailsNewHiring(List<EmployeeRequisitionNewHiringRequestDetail> details) {
        if (details == null || details.isEmpty()) {
            throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "at least one requisition detail is required");
        }
        return details;
    }

    private static List<EmployeeRequisitionReplacementRequestDetail> requireDetailsReplacement(List<EmployeeRequisitionReplacementRequestDetail> details) {
        if (details == null || details.isEmpty()) {
            throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "at least one requisition detail is required");
        }
        return details;
    }

    private static long totalPositionsNewHiring(List<EmployeeRequisitionNewHiringRequestDetail> details) {
        long total = details.stream().mapToLong(EmployeeRequisitionNewHiringRequestDetail::noOfPosition).sum();
        if (total < 1) {
            throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "total requested positions must be at least 1");
        }
        return total;
    }

    private static long totalPositionsReplacement(List<EmployeeRequisitionReplacementRequestDetail> details) {
        long total = details.size();
        if (total < 1) {
            throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "total replacement employee positions must be at least 1");
        }
        return total;
    }

    private Map<Long, Employee> validateReplacementDetails(List<EmployeeRequisitionReplacementRequestDetail> details,
                                                           Long actorEmployeeDepartmentId) {
        Map<Long, Employee> replacingEmployees = new LinkedHashMap<>();
        for (EmployeeRequisitionReplacementRequestDetail detail : details) {
            if (detail.replacingEmployeeId() == null) {
                throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "replacingEmployeeId is required for each REPLACEMENT detail");
            }

            //checking if already added replacement for same employee
            EmployeeRequisitionDetail alreadyReplacementAdded =  employeeRequisitionDetailRepository.findByReplacementEmployeeId(
                    detail.replacingEmployeeId());
            if(alreadyReplacementAdded != null){
                employees.findById(alreadyReplacementAdded.getReplacingEmployeeId()).ifPresent(employee -> {
                    throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                            "employee: " + employee.getFullName() + " having card: " +
                                    " "  + employee.getEmployeeCode() + " is already added as a replacement in replacement" +
                                    " that request id: " + alreadyReplacementAdded.getRequisitionHeadId());
                });
            }

            //get the employee info of replacing employee
            Employee replacingEmployee = employees.findById(detail.replacingEmployeeId())
                    .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                            "employee being replaced not found: id=" + detail.replacingEmployeeId()));
            if (replacingEmployee.getSectionId() == null
                    || replacingEmployee.getDesignationId() == null
                    || replacingEmployee.getGradeId() == null) {
                throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "replacing employee card must have sectionId, designationId, and gradeId");
            }

            //checking entered employeeId are belong to departmentId of actoreEmployee
            if (!Objects.equals(replacingEmployee.getDepartmentId(), actorEmployeeDepartmentId)) {
                throw new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "replacing employee: " + replacingEmployee.getFullName() + " having card: " +
                                " "  + replacingEmployee.getEmployeeCode() + " does not belong to the current user's department");
            }


            //checking if employee is terminated or not
            if (replacingEmployee.getEmploymentStatus() != EmploymentStatus.TERMINATED) {
                throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "employee: " + replacingEmployee.getFullName() + " having card: "
                                + replacingEmployee.getEmployeeCode()
                                + " must be terminated before to generate requisition");
            }

            replacingEmployees.put(detail.replacingEmployeeId(), replacingEmployee);
        }
        return replacingEmployees;
    }
}

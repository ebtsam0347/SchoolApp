package com.forward.hrms.employee.service;

import com.forward.hrms.attendance.dto.response.ShiftDto;
import com.forward.hrms.attendance.dto.response.ShiftDto;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.client.AttendanceClient;
import com.forward.hrms.employee.dto.request.AssignRequisitionDetailEmployeeRequest;
import com.forward.hrms.employee.dto.request.LeanRemoveRequisitionDetailRequest;
import com.forward.hrms.employee.dto.response.RequisitionDetailDto;
import com.forward.hrms.employee.dto.response.RequisitionDto;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeRequisition;
import com.forward.hrms.employee.entity.EmployeeRequisitionDetail;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.RequisitionStatus;
import com.forward.hrms.employee.enums.RequisitionType;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmployeeRequisitionDetailRepository;
import com.forward.hrms.employee.repository.EmployeeRequisitionRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeRequisitionDetailService {

    private final EmployeeRequisitionRepository employeeRequisitionRepository;
    private final EmployeeRequisitionDetailRepository employeeRequisitionDetailRepository;
    private final EmployeeRepository employees;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final AttendanceClient attendanceClient;
    private final EmployeeRequisitionService employeeRequisitionService;
    private final ApprovalService approvals;
    private final AuditEmitter audit;

    /**
     * Detail rows of a requisition, oldest row first. Catalog ids and the
     * replaced employee are resolved in batch lookups rather than per row.
     *
     * @param leanRemoved {@code null} for every row, {@code true} for only the
     *                    positions struck off during lean review, {@code false}
     *                    for only the ones still standing.
     */
    @Transactional(readOnly = true)
    public List<RequisitionDetailDto> listByRequisitionId(long requisitionId, Boolean leanRemoved) {
        if (!employeeRequisitionRepository.existsById(requisitionId)) {
            throw new NotFoundError(EmployeeErrorCode.REQUISITION_NOT_FOUND,
                    "requisition not found: id=" + requisitionId);
        }

        return toDtos(leanRemoved == null
                ? employeeRequisitionDetailRepository.findAllByRequisitionHeadIdOrderByIdAsc(requisitionId)
                : employeeRequisitionDetailRepository
                        .findAllByRequisitionHeadIdAndLeanRemoved(requisitionId, leanRemoved));
    }

    /** Batch-resolves the catalog and replaced-employee labels for the given rows. */
    private List<RequisitionDetailDto> toDtos(List<EmployeeRequisitionDetail> rows) {
        if (rows.isEmpty()) return List.of();

        Map<Long, Section> sectionById =
                byId(sections.findAllById(ids(rows, EmployeeRequisitionDetail::getSectionId)), Section::getId);
        Map<Long, Designation> designationById =
                byId(designations.findAllById(ids(rows, EmployeeRequisitionDetail::getDesignationId)), Designation::getId);
        Map<Long, Grade> gradeById =
                byId(grades.findAllById(ids(rows, EmployeeRequisitionDetail::getGradeId)), Grade::getId);
        Map<Long, Employee> replacedById =
                byId(employees.findAllById(ids(rows, EmployeeRequisitionDetail::getReplacingEmployeeId)), Employee::getId);
        Map<Long, ShiftDto> shiftById = byId(attendanceClient.listShifts(), ShiftDto::id);

        return rows.stream()
                .map(r -> toDto(r, sectionById, designationById, gradeById, replacedById, shiftById))
                .toList();
    }

    /**
     * Strike one requested position off a NEW_HIRE requisition during
     * Manufacturing Excellence review: flags the row {@code is_lean_removed},
     * records the remark and the acting user, then re-derives
     * {@code employee_requisitions.total_positions_lean} as the count of rows
     * still standing. The approval chain is untouched — ME calls
     * {@code POST /requisitions/{id}/approve} once it is done trimming, which
     * settles the lean total for good and moves the request to stage 2.
     *
     * <p>REPLACEMENT requisitions are rejected outright: a replacement backfills
     * someone who has already left, so the position is never optional. Their lean
     * total is simply every row, settled by the stage-1 approval.
     */
    @Transactional
    public RequisitionDto leanRemove(long requisitionId, long detailId,
                                     LeanRemoveRequisitionDetailRequest req) {
        CurrentUser caller = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required"));

        EmployeeRequisition head = employeeRequisitionRepository.findById(requisitionId)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.REQUISITION_NOT_FOUND,
                        "requisition not found: id=" + requisitionId));
        if (head.getStatus() != RequisitionStatus.PENDING_APPROVAL) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "requisition status is " + head.getStatus()
                            + ", positions can only be removed while it is PENDING_APPROVAL");
        }

        if (head.getType() == RequisitionType.REPLACEMENT) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "positions cannot be removed from a REPLACEMENT requisition - a replacement is always required");
        }

        // Only NEW_HIRE reaches here, so the chain is known.
        String requestType = "REQUISITION_NEW_HIRE";
        ApprovalStep current = approvals.currentPending(requestType, requisitionId)
                .orElseThrow(() -> new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                        "requisition has no pending approval stage"));
        if (current.getStageNo() != EmployeeRequisitionService.LEAN_REVIEW_STAGE_NO) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "requisition is at stage " + current.getStageNo() + " (" + current.getLabel()
                            + "); positions can only be removed during the Manufacturing Excellence review");
        }
        if (!approvals.canAct(requestType, current, caller)) {
            throw new ForbiddenError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "only the Manufacturing Excellence reviewer can remove positions at this stage");
        }

        EmployeeRequisitionDetail row = employeeRequisitionDetailRepository.findById(detailId)
                .filter(d -> Objects.equals(d.getRequisitionHeadId(), requisitionId))
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.REQUISITION_NOT_FOUND,
                        "requisition detail not found: id=" + detailId
                                + " on requisition id=" + requisitionId));
        if (row.isLeanRemoved()) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "requisition detail id=" + detailId + " is already lean-removed");
        }

        Map<String, Object> before = snapshot(head, row);
        row.setLeanRemoved(true);
        row.setLeanRemovedRemarks(req.leanRemovedRemarks());
        row.setLeanRemovedBy(caller.userId());

        head.setTotalPositionsLean(
                employeeRequisitionDetailRepository.countRemainingAfterLean(requisitionId));

        audit.emit("requisition", String.valueOf(requisitionId), "lean-position-removed",
                before, snapshot(head, row), true);
        return employeeRequisitionService.get(requisitionId);
    }

    private static Map<String, Object> snapshot(EmployeeRequisition head, EmployeeRequisitionDetail row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("detailId", row.getId());
        m.put("leanRemoved", row.isLeanRemoved());
        m.put("leanRemovedRemarks", row.getLeanRemovedRemarks());
        m.put("leanRemovedBy", row.getLeanRemovedBy());
        m.put("totalPositionsHod", head.getTotalPositionsHod());
        m.put("totalPositionsLean", head.getTotalPositionsLean());
        return m;
    }

    /**
     * Record who was hired against one approved position. Legal only once the
     * requisition is APPROVED with its whole chain decided — the position count
     * is settled by then, so filling a row can no longer change what was approved.
     * A null field is left as-is, so the temporary reference can be captured first
     * and the real employee code written over it later.
     */
    @Transactional
    public RequisitionDetailDto assignEmployeeReference(long requisitionId, long detailId,
                                                        AssignRequisitionDetailEmployeeRequest req) {
        boolean hasCode = req.newEmployeeCode() != null && !req.newEmployeeCode().isBlank();
        boolean hasReference = req.temporaryEmployeeReference() != null
                && !req.temporaryEmployeeReference().isBlank();
        if (!hasCode && !hasReference) {
            throw new UnprocessableError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "newEmployeeCode or temporaryEmployeeReference is required");
        }

        EmployeeRequisition head = employeeRequisitionRepository.findById(requisitionId)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.REQUISITION_NOT_FOUND,
                        "requisition not found: id=" + requisitionId));
        if (head.getStatus() != RequisitionStatus.APPROVED) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "requisition status is " + head.getStatus()
                            + ", employee references can only be recorded on an APPROVED requisition");
        }
        if (!approvals.isFinalized(EmployeeRequisitionService.requestTypeFor(head.getType()), requisitionId)) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "requisition still has an undecided approval stage");
        }

        EmployeeRequisitionDetail row = employeeRequisitionDetailRepository.findById(detailId)
                .filter(d -> Objects.equals(d.getRequisitionHeadId(), requisitionId))
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.REQUISITION_NOT_FOUND,
                        "requisition detail not found: id=" + detailId
                                + " on requisition id=" + requisitionId));
        if (row.isLeanRemoved()) {
            throw new ConflictError(EmployeeErrorCode.REQUISITION_INVALID_STATE,
                    "requisition detail id=" + detailId
                            + " was removed during lean review and cannot be filled");
        }

        Map<String, Object> before = referenceSnapshot(row);
        if (hasCode) row.setNewEmployeeCode(req.newEmployeeCode().trim());
        if (hasReference) row.setTemporaryEmployeeReference(req.temporaryEmployeeReference().trim());

        audit.emit("requisition", String.valueOf(requisitionId), "detail-employee-assigned",
                before, referenceSnapshot(row), true);
        return toDtos(List.of(row)).get(0);
    }

    private static Map<String, Object> referenceSnapshot(EmployeeRequisitionDetail row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("detailId", row.getId());
        m.put("newEmployeeCode", row.getNewEmployeeCode());
        m.put("temporaryEmployeeReference", row.getTemporaryEmployeeReference());
        return m;
    }

    private static RequisitionDetailDto toDto(EmployeeRequisitionDetail r,
                                              Map<Long, Section> sectionById,
                                              Map<Long, Designation> designationById,
                                              Map<Long, Grade> gradeById,
                                              Map<Long, Employee> replacedById,
                                              Map<Long, ShiftDto> shiftById) {
        Section section = r.getSectionId() == null ? null : sectionById.get(r.getSectionId());
        Designation designation = r.getDesignationId() == null ? null : designationById.get(r.getDesignationId());
        Grade grade = r.getGradeId() == null ? null : gradeById.get(r.getGradeId());
        Employee replaced = r.getReplacingEmployeeId() == null ? null : replacedById.get(r.getReplacingEmployeeId());
        ShiftDto shift = r.getShiftId() == null ? null : shiftById.get(r.getShiftId());

        return new RequisitionDetailDto(
                r.getId(),
                r.getRequisitionHeadId(),
                r.getReplacingEmployeeId(),
                replaced == null ? null : replaced.getEmployeeCode(),
                replaced == null ? null : replaced.getFullName(),
                r.getNewEmployeeCode(),
                r.getTemporaryEmployeeReference(),
                r.getSectionId(),
                section == null ? null : section.getCode(),
                section == null ? null : section.getName(),
                r.getDesignationId(),
                designation == null ? null : designation.getCode(),
                designation == null ? null : designation.getTitle(),
                r.getGradeId(),
                grade == null ? null : grade.getCode(),
                grade == null ? null : grade.getName(),
                r.getEmploymentType(),
                r.getEmployeeCategory(),
                r.getRequiredUntil(),
                r.getEmployeeGender(),
                r.getShiftId(),
                shift == null ? null : shift.name(),
                r.isLeanRemoved(),
                r.getLeanRemovedRemarks(),
                r.getLeanRemovedBy(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getUpdatedBy());
    }

    private static Set<Long> ids(Collection<EmployeeRequisitionDetail> rows,
                                 Function<EmployeeRequisitionDetail, Long> extractor) {
        return rows.stream().map(extractor).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
    }

    private static <T> Map<Long, T> byId(Collection<T> values, Function<T, Long> idOf) {
        if (values.isEmpty()) return Collections.emptyMap();
        return values.stream().collect(Collectors.toMap(idOf, Function.identity()));
    }
}

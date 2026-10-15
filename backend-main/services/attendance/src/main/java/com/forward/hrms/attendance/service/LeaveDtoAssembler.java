package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.attendance.dto.response.LeaveRequestDto;
import com.forward.hrms.attendance.dto.response.OvertimeRequestDto;
import com.forward.hrms.attendance.dto.response.RegularizationRequestDto;
import com.forward.hrms.attendance.dto.response.ShortLeaveRequestDto;
import com.forward.hrms.attendance.entity.LeaveRequest;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.entity.OvertimeRequest;
import com.forward.hrms.attendance.entity.RegularizationRequest;
import com.forward.hrms.attendance.entity.ShortLeaveRequest;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;
import com.forward.hrms.attendance.enums.OvertimeStatus;
import com.forward.hrms.attendance.enums.RegularizationStatus;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.enums.ShortLeaveStatus;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.attendance.dto.response.ApprovalStepDtoMapper;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds approval-DTO shapes (steps, currentStage, actions[]) — shared
 * by leave / overtime / regularization controllers so the JSON shape on
 * the wire matches the Approval DTO contract in the doc.
 */
@Component
@RequiredArgsConstructor
public class LeaveDtoAssembler {

    private final ApprovalService approvals;
    private final LeaveTypeRepository leaveTypes;
    private final CallerContext caller;

    public LeaveRequestDto leaveDto(LeaveRequest r) {
        List<ApprovalStep> chain = approvals.findChain(RequestType.LEAVE.name(), r.getId());
        Map<Long, String> userNames = lookupUserNames(chain);
        return new LeaveRequestDto(
                r.getId(), r.getRequestCode(), r.getEmployeeId(), r.getLeaveTypeId(),
                leaveTypeCode(r.getLeaveTypeId()),
                r.getFromDate(), r.getToDate(),
                r.isHalfDay(), r.getHalfDayPart() == null ? null : r.getHalfDayPart().name(),
                r.getDays(), r.getReason(),
                r.getStatus().name(),
                currentStage(chain, userNames),
                stepsDto(chain, userNames),
                actionsForCaller(RequestType.LEAVE, chain, true,
                        () -> leaveCancellable(r)
                                && callerOwnsEmployee(r.getEmployeeId())),
                r.getCreatedAt());
    }

    public OvertimeRequestDto overtimeDto(OvertimeRequest r) {
        List<ApprovalStep> chain = approvals.findChain(RequestType.OVERTIME.name(), r.getId());
        Map<Long, String> userNames = lookupUserNames(chain);
        return new OvertimeRequestDto(
                r.getId(), r.getRequestCode(), r.getEmployeeId(), r.getAttendanceDate(),
                r.getOvertimeType().name(),
                r.getFactoryCode() == null ? null : r.getFactoryCode().code(),
                r.getReason(), r.getBulkBatchId(),
                r.getStatus().name(),
                currentStage(chain, userNames),
                stepsDto(chain, userNames),
                actionsForCaller(RequestType.OVERTIME, chain, false,
                        () -> overtimeCancellable(r)
                                && callerOwnsEmployee(r.getEmployeeId())),
                r.getCreatedAt());
    }

    public ShortLeaveRequestDto shortLeaveDto(ShortLeaveRequest r) {
        List<ApprovalStep> chain = approvals.findChain(RequestType.SHORT_LEAVE.name(), r.getId());
        Map<Long, String> userNames = lookupUserNames(chain);
        return new ShortLeaveRequestDto(
                r.getId(), r.getRequestCode(), r.getEmployeeId(), r.getLeaveDate(),
                r.getStartTime(), r.getEndTime(), r.getTotalMinutes(),
                r.getReason(),
                r.getStatus().name(),
                currentStage(chain, userNames),
                stepsDto(chain, userNames),
                actionsForCaller(RequestType.SHORT_LEAVE, chain, false,
                        () -> shortLeaveCancellable(r)
                                && callerOwnsEmployee(r.getEmployeeId())),
                r.getCreatedAt());
    }

    public RegularizationRequestDto regularizationDto(RegularizationRequest r) {
        List<ApprovalStep> chain = approvals.findChain(RequestType.REGULARIZATION.name(), r.getId());
        Map<Long, String> userNames = lookupUserNames(chain);
        return new RegularizationRequestDto(
                r.getId(), r.getRequestCode(), r.getEmployeeId(), r.getAttendanceDate(),
                r.getRequestedCheckIn(), r.getRequestedCheckOut(),
                r.getReason(),
                r.getStatus().name(),
                currentStage(chain, userNames),
                stepsDto(chain, userNames),
                actionsForCaller(RequestType.REGULARIZATION, chain, false,
                        () -> regularizationCancellable(r)
                                && callerOwnsEmployee(r.getEmployeeId())),
                r.getCreatedAt());
    }

    private CurrentStageDto currentStage(List<ApprovalStep> chain, Map<Long, String> userNames) {
        return chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst()
                .map(s -> new CurrentStageDto(
                        s.getStageNo(), s.getLabel(),
                        s.getAssignedTo(),
                        s.getAssignedTo() == null ? null : userNames.get(s.getAssignedTo())))
                .orElse(null);
    }

    private List<ApprovalStepDto> stepsDto(List<ApprovalStep> chain, Map<Long, String> userNames) {
        return chain.stream().map(s -> ApprovalStepDtoMapper.of(s, userNames)).toList();
    }

    private List<String> actionsForCaller(RequestType type, List<ApprovalStep> chain, boolean perDay,
                                          java.util.function.BooleanSupplier cancellableForCaller) {
        return CurrentUserHolder.get()
                .map(user -> actionsFor(type, chain, user, perDay, cancellableForCaller))
                .orElse(List.of());
    }

    private List<String> actionsFor(RequestType type, List<ApprovalStep> chain, CurrentUser user, boolean perDay,
                                    java.util.function.BooleanSupplier cancellableForCaller) {
        List<String> out = new ArrayList<>(4);
        ApprovalStep current = chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING).findFirst().orElse(null);
        if (current != null && approvals.canAct(type.name(), current, user)) {
            out.add("APPROVE");
            out.add("REJECT");
            // Per-day decisions are the MANAGER's call — HR (FINAL) ratifies the
            // per-day mix as a bundle but does not redo per-day decisions.
            if (perDay && current.getResolverKind() == ResolverKind.MANAGER) out.add("DECIDE_PER_DAY");
        }
        // CANCEL is the submitter's action and is independent of the approver chain.
        // Cancel-eligibility per request type is encapsulated in the caller-supplied
        // predicate so the FE doesn't have to duplicate the rule.
        if (cancellableForCaller.getAsBoolean()) out.add("CANCEL");
        return out;
    }

    private boolean callerOwnsEmployee(long employeeId) {
        EmployeeClient.EmployeeView me = caller.currentEmployee().orElse(null);
        return me != null && me.id() == employeeId;
    }

    private static boolean leaveCancellable(LeaveRequest r) {
        LeaveRequestStatus s = r.getStatus();
        if (s == LeaveRequestStatus.PENDING) return true;
        if ((s == LeaveRequestStatus.APPROVED || s == LeaveRequestStatus.PARTIALLY_APPROVED)
                && r.getFromDate().isAfter(LocalDate.now())) return true;
        return false;
    }

    private static boolean overtimeCancellable(OvertimeRequest r) {
        // PENDING any time, or APPROVED before the work date (mirrors leave behavior).
        if (r.getStatus() == OvertimeStatus.PENDING) return true;
        if (r.getStatus() == OvertimeStatus.APPROVED
                && r.getAttendanceDate().isAfter(LocalDate.now())) return true;
        return false;
    }

    private static boolean shortLeaveCancellable(ShortLeaveRequest r) {
        // PENDING any time, or APPROVED before the leave date.
        if (r.getStatus() == ShortLeaveStatus.PENDING) return true;
        if (r.getStatus() == ShortLeaveStatus.APPROVED
                && r.getLeaveDate().isAfter(LocalDate.now())) return true;
        return false;
    }

    private static boolean regularizationCancellable(RegularizationRequest r) {
        return r.getStatus() == RegularizationStatus.PENDING;
    }

    private Map<Long, String> lookupUserNames(List<ApprovalStep> chain) {
        // employee-service round-trip per step is expensive — leaving null
        // as a v1 placeholder; the BFF can re-enrich names on its own
        // when assembling the inbox view. We still return the empty map
        // so DTO assembly works.
        Map<Long, String> map = new HashMap<>();
        for (ApprovalStep s : chain) {
            if (s.getAssignedTo() != null) map.putIfAbsent(s.getAssignedTo(), null);
            if (s.getDecidedBy() != null) map.putIfAbsent(s.getDecidedBy(), null);
        }
        return map;
    }

    private String leaveTypeCode(Long id) {
        if (id == null) return null;
        return leaveTypes.findById(id).map(LeaveType::getCode).orElse(null);
    }

    /** Used by event payload builders — convert chain to event stage snapshots. */
    public List<com.forward.hrms.attendance.events.AttendanceEvents.StageSnapshot> stageSnapshots(List<ApprovalStep> chain) {
        return chain.stream()
                .map(s -> new com.forward.hrms.attendance.events.AttendanceEvents.StageSnapshot(
                        s.getStageNo(), s.getLabel(), s.getAssignedTo()))
                .toList();
    }

    /** Used by event payloads describing the next stage after a non-final approve. */
    public com.forward.hrms.attendance.events.AttendanceEvents.NextStage nextStageEvent(ApprovalStep next) {
        if (next == null) return null;
        return new com.forward.hrms.attendance.events.AttendanceEvents.NextStage(
                next.getStageNo(), next.getLabel(), next.getAssignedTo());
    }
}

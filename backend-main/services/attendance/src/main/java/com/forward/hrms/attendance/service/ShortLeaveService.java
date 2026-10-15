package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.ApproveRequest;
import com.forward.hrms.attendance.dto.request.CreateShortLeaveRequest;
import com.forward.hrms.attendance.dto.request.RejectRequest;
import com.forward.hrms.attendance.dto.response.ShortLeaveRequestDto;
import com.forward.hrms.attendance.entity.ShortLeaveRequest;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.enums.ShortLeaveStatus;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.ShortLeaveRequestRepository;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.service.ApprovalInboxQueries;
import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShortLeaveService {

    private final ShortLeaveRequestRepository shortLeaves;
    private final ApprovalService approvals;
    private final ApprovalInboxService inbox;
    private final LeaveDtoAssembler assembler;
    private final CallerContext caller;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final AttendancePeriodLockService periodLocks;
    private final RequestCodeService requestCodes;
    private final OnBehalfResolver onBehalf;
    private final ApprovalEventPublisher approvalEvents;
    private final EmployeeClient employees;
    private final com.forward.hrms.commons.cache.EmployeeDisplayCache displayCache;
    private final DepartmentScope departmentScope;
    private final InboxSearch inboxSearch;

    @Transactional
    public ShortLeaveRequestDto submit(long callerEmployeeId, CreateShortLeaveRequest req) {
        OnBehalfResolver.Resolution resolution =
                onBehalf.resolve(callerEmployeeId, req.onBehalfOf(), OnBehalfResolver.TEAM_GEN_SHORT_LEAVE);
        long employeeId = resolution.targetEmployeeId();
        if (!req.endTime().isAfter(req.startTime())) {
            throw new UnprocessableError(AttendanceErrorCode.SHORT_LEAVE_RANGE_INVALID,
                    "endTime must be after startTime");
        }
        // §7.attendance #62 — short leave is for the current day or later;
        // back-dating doesn't fit the workflow.
        if (req.leaveDate() == null || req.leaveDate().isBefore(java.time.LocalDate.now())) {
            throw new UnprocessableError(AttendanceErrorCode.REQUEST_DATE_OUT_OF_RANGE,
                    "leaveDate cannot be in the past");
        }
        for (ShortLeaveRequest existing : shortLeaves.findActiveForDate(employeeId, req.leaveDate())) {
            if (windowsOverlap(existing.getStartTime(), existing.getEndTime(),
                    req.startTime(), req.endTime())) {
                throw new ConflictError(AttendanceErrorCode.SHORT_LEAVE_OVERLAPS_EXISTING,
                        "an active short leave already overlaps that window");
            }
        }
        ShortLeaveRequest sl = new ShortLeaveRequest();
        sl.setRequestCode(requestCodes.allocate("SL"));
        sl.setEmployeeId(employeeId);
        sl.setLeaveDate(req.leaveDate());
        sl.setStartTime(req.startTime());
        sl.setEndTime(req.endTime());
        sl.setReason(req.reason());
        sl.setStatus(ShortLeaveStatus.PENDING);
        shortLeaves.save(sl);

        List<ApprovalStep> chain = approvals.openChain(RequestType.SHORT_LEAVE.name(), sl.getId(),
                resolution.approvalAnchorEmployeeId());
        outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + sl.getId(),
                AttendanceEvents.SHORT_LEAVE_REQUESTED,
                new AttendanceEvents.ShortLeaveRequested(
                        sl.getId(), employeeId, req.leaveDate(),
                        req.startTime(), req.endTime(), sl.getTotalMinutes(),
                        assembler.stageSnapshots(chain)));
        audit.emit("short_leave_request", String.valueOf(sl.getId()), "submitted",
                null, Map.of("leaveDate", req.leaveDate().toString(),
                        "startTime", req.startTime().toString(),
                        "endTime", req.endTime().toString()), true);

        if (resolution.autoApproves()) {
            return autoApproveOnBehalf(sl, resolution.mode());
        }
        return assembler.shortLeaveDto(sl);
    }

    /**
     * Manager-on-behalf submissions skip the manager re-approval step:
     * the manager has already implicitly approved by submitting. HR
     * override clears every stage. TEAM_ON_BEHALF never reaches here —
     * a team-request generator has no approval authority, so the chain
     * stays PENDING on the target's own manager.
     *
     * <p>{@code closeCurrentStage} bypasses {@link ShortLeaveApprovalOutcomeHandler},
     * so the terminal status flip, the approved event and the audit row
     * are applied inline here — same shape as OvertimeService.
     */
    private ShortLeaveRequestDto autoApproveOnBehalf(ShortLeaveRequest sl, OnBehalfResolver.Mode mode) {
        CurrentUser actor = caller.requireUser();
        String managerNote = mode == OnBehalfResolver.Mode.HR_OVERRIDE
                ? "submitted on behalf by HR (override)"
                : "submitted on behalf by manager";
        ApprovalService.Decision managerStage = approvals.closeCurrentStage(
                RequestType.SHORT_LEAVE.name(), sl.getId(), StepStatus.APPROVED, actor, managerNote);
        approvalEvents.stageApproved(RequestType.SHORT_LEAVE, sl.getId(), sl.getEmployeeId(),
                managerStage.step().getStageNo(), managerStage.step().getLabel(),
                actor.userId(), assembler.nextStageEvent(managerStage.nextPending()));
        if (mode != OnBehalfResolver.Mode.HR_OVERRIDE) return assembler.shortLeaveDto(sl);

        if (managerStage.nextPending() != null) {
            approvals.closeCurrentStage(RequestType.SHORT_LEAVE.name(), sl.getId(),
                    StepStatus.APPROVED, actor, "submitted on behalf by HR (override)");
            sl.setStatus(ShortLeaveStatus.APPROVED);
            outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + sl.getId(),
                    AttendanceEvents.SHORT_LEAVE_APPROVED,
                    new AttendanceEvents.ShortLeaveApproved(
                            sl.getId(), sl.getEmployeeId(), sl.getLeaveDate(),
                            sl.getStartTime(), sl.getEndTime(), sl.getTotalMinutes(),
                            actor.userId()));
            audit.emit("short_leave_request", String.valueOf(sl.getId()), "approved",
                    null, Map.of("leaveDate", sl.getLeaveDate().toString()), true);
        }
        return assembler.shortLeaveDto(sl);
    }

    @Transactional
    public ShortLeaveRequestDto approve(long id, ApproveRequest body) {
        ShortLeaveRequest sl = require(id);
        periodLocks.assertOpen(sl.getLeaveDate());
        approvals.approve(RequestType.SHORT_LEAVE.name(), id, body == null ? null : body.note());
        return assembler.shortLeaveDto(sl);
    }

    @Transactional
    public ShortLeaveRequestDto reject(long id, RejectRequest body) {
        ShortLeaveRequest sl = require(id);
        approvals.reject(RequestType.SHORT_LEAVE.name(), id, body.reason());
        return assembler.shortLeaveDto(sl);
    }

    @Transactional
    public ShortLeaveRequestDto cancel(long id) {
        ShortLeaveRequest sl = require(id);
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        if (me.id() != sl.getEmployeeId()) {
            throw new ForbiddenError(AttendanceErrorCode.NOT_AUTHORIZED,
                    "only the submitter can cancel");
        }
        boolean cancellable = sl.getStatus() == ShortLeaveStatus.PENDING
                || (sl.getStatus() == ShortLeaveStatus.APPROVED
                        && sl.getLeaveDate().isAfter(LocalDate.now()));
        if (!cancellable) {
            throw new ConflictError(AttendanceErrorCode.SHORT_LEAVE_NOT_CANCELLABLE,
                    "short leave cannot be cancelled in its current state. Current request status is: " + sl.getStatus());
        }
        approvals.cancelChain(RequestType.SHORT_LEAVE.name(), id);
        sl.setStatus(ShortLeaveStatus.CANCELLED);
        outbox.publish(AttendanceEvents.TOPIC, "short_leave:" + id,
                AttendanceEvents.SHORT_LEAVE_CANCELLED,
                new AttendanceEvents.ShortLeaveCancelled(id, sl.getEmployeeId(), sl.getLeaveDate()));
        audit.emit("short_leave_request", String.valueOf(id), "cancelled",
                null, Map.of("leaveDate", sl.getLeaveDate().toString()), true);
        return assembler.shortLeaveDto(sl);
    }

    @Transactional(readOnly = true)
    public ShortLeaveRequestDto get(long id) {
        return assembler.shortLeaveDto(require(id));
    }

    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<ShortLeaveRequestDto> searchForEmployee(
            long employeeId, ShortLeaveStatus status,
            LocalDate from, LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        var page = shortLeaves.search(employeeId, status, from, to, pageable);
        List<ShortLeaveRequestDto> content = page.getContent().stream()
                .map(assembler::shortLeaveDto).toList();
        return com.forward.hrms.commons.web.page.PageResponse.from(page, content);
    }

    /**
     * Short leaves of the caller's direct reports, whoever submitted them —
     * the report, or the manager on their behalf. Empty page for a caller
     * who manages nobody. Optional {@code employeeId} narrows to one report
     * (anyone else yields an empty page).
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<ShortLeaveRequestDto> searchForTeam(
            ShortLeaveStatus status, LocalDate from, LocalDate to, Long employeeId,
            org.springframework.data.domain.Pageable pageable) {
        List<Long> reportIds = employees.myDirectReportIds();
        if (employeeId != null) {
            reportIds = reportIds.contains(employeeId) ? List.of(employeeId) : List.of();
        }
        if (reportIds.isEmpty()) {
            return com.forward.hrms.commons.web.page.PageResponse.from(
                    org.springframework.data.domain.Page.empty(pageable), List.of());
        }
        var page = shortLeaves.searchForEmployees(reportIds, status, from, to, pageable);
        List<ShortLeaveRequestDto> content = page.getContent().stream()
                .map(assembler::shortLeaveDto).toList();
        return com.forward.hrms.commons.web.page.PageResponse.from(page, content);
    }

    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<ShortLeaveRequestDto> pendingApprovals(
            LocalDate from, LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        return pendingApprovals(from, to, null, pageable);
    }

    /**
     * Inbox with an optional {@code q} filter: matches the request id exactly
     * when numeric, or a case-insensitive substring of the request code,
     * employee code, or employee full name.
     */
    public com.forward.hrms.commons.web.page.PageResponse<ShortLeaveRequestDto> pendingApprovals(
            LocalDate from, LocalDate to, String q,
            org.springframework.data.domain.Pageable pageable) {
        CurrentUser user = caller.requireUser();
        return ApprovalInboxQueries.findPending(
                inbox, RequestType.SHORT_LEAVE.name(), user,
                shortLeaves::findAllById,
                rows -> inboxSearch.filter(rows, q, ShortLeaveRequest::getId,
                        ShortLeaveRequest::getRequestCode, ShortLeaveRequest::getEmployeeId),
                ShortLeaveRequest::getId,
                ShortLeaveRequest::getLeaveDate, ShortLeaveRequest::getLeaveDate,
                from, to, pageable, assembler::shortLeaveDto);
    }

    private ShortLeaveRequest require(long id) {
        return shortLeaves.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.SHORT_LEAVE_NOT_FOUND,
                        "short leave not found"));
    }

    private static boolean windowsOverlap(java.time.LocalTime aStart, java.time.LocalTime aEnd,
                                          java.time.LocalTime bStart, java.time.LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /**
     * Short leaves the caller filed on someone else's behalf (a manager for a
     * direct report, a team-request generator for a colleague). Their own
     * self-submissions are excluded. Mirrors
     * {@code RegularizationService#filedOnBehalf}: matched on
     * {@code created_by}, each row paired with the target's summary from the
     * shared display cache (the callers hold no {@code employee.read}).
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.ShortLeaveRow> filedOnBehalf(
            com.forward.hrms.attendance.enums.ShortLeaveStatus status,
            java.time.LocalDate filedFrom, java.time.LocalDate filedTo,
            org.springframework.data.domain.Pageable pageable) {
        var actor = caller.requireUser();
        var me = caller.requireEmployee();
        var page = shortLeaves.searchFiledOnBehalf(actor.username(), me.id(), status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        var displays = displayCache.getMany(page.getContent().stream()
                .map(com.forward.hrms.attendance.entity.ShortLeaveRequest::getEmployeeId).distinct().toList());
        return com.forward.hrms.commons.web.page.PageResponse.from(page,
                page.getContent().stream()
                        .map(r -> {
                            var d = displays.get(r.getEmployeeId());
                            return new com.forward.hrms.attendance.dto.response.ApprovalsInbox.ShortLeaveRow(
                                    assembler.shortLeaveDto(r),
                                    d == null ? null
                                            : com.forward.hrms.commons.cache.EmployeeSummaryMapper.toSummary(d));
                        })
                        .toList());
    }

    /**
     * Every short leave in the caller's department, whoever filed it —
     * for a team-request generator, or for a manager (MANAGER role, or anyone
     * with direct reports) looking past their own team. Optional {@code employeeId} narrows to one colleague (an id outside the
     * department yields an empty page). Rows carry the target's summary from
     * the display cache, like {@link #filedOnBehalf}.
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.ShortLeaveRow> forDepartment(
            com.forward.hrms.attendance.enums.ShortLeaveStatus status,
            java.time.LocalDate filedFrom, java.time.LocalDate filedTo, Long employeeId,
            org.springframework.data.domain.Pageable pageable) {
        CurrentUser user = caller.requireUser();
        if (!user.hasPermission("team.request.generation.shortLeave")
                && !user.hasRole("MANAGER")
                && employees.myDirectReportIds().isEmpty()) {
            throw new com.forward.hrms.commons.core.error.ForbiddenError(
                    "Only managers and team-request generators can list their department's short leaves");
        }
        java.util.List<Long> ids = departmentScope.employeeIds(employeeId);
        if (ids.isEmpty()) {
            return com.forward.hrms.commons.web.page.PageResponse.from(
                    org.springframework.data.domain.Page.empty(pageable), java.util.List.of());
        }
        var page = shortLeaves.searchDepartment(ids, status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        var displays = displayCache.getMany(page.getContent().stream()
                .map(com.forward.hrms.attendance.entity.ShortLeaveRequest::getEmployeeId).distinct().toList());
        return com.forward.hrms.commons.web.page.PageResponse.from(page,
                page.getContent().stream()
                        .map(r -> {
                            var d = displays.get(r.getEmployeeId());
                            return new com.forward.hrms.attendance.dto.response.ApprovalsInbox.ShortLeaveRow(
                                    assembler.shortLeaveDto(r),
                                    d == null ? null
                                            : com.forward.hrms.commons.cache.EmployeeSummaryMapper.toSummary(d));
                        })
                        .toList());
    }
}

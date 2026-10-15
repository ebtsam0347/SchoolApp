package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.ApproveRequest;
import com.forward.hrms.attendance.dto.request.CreateLeaveRequest;
import com.forward.hrms.attendance.dto.request.PerDayDecideRequest;
import com.forward.hrms.attendance.dto.request.RejectRequest;
import com.forward.hrms.attendance.dto.response.LeaveBalanceDto;
import com.forward.hrms.attendance.dto.response.LeaveDayDto;
import com.forward.hrms.attendance.dto.response.LeavePreviewDto;
import com.forward.hrms.attendance.dto.response.LeaveRequestDto;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.entity.LeaveRequest;
import com.forward.hrms.attendance.entity.LeaveRequestDay;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.HalfDayPart;
import com.forward.hrms.attendance.enums.LeaveDayStatus;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.HolidayRepository;
import com.forward.hrms.attendance.repository.LeaveRequestDayRepository;
import com.forward.hrms.attendance.repository.LeaveRequestRepository;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.commons.approval.service.ApprovalInboxQueries;
import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The leave-side state machine. Wraps {@link ApprovalService} for the
 * shared chain mechanics + adds leave-specific behavior:
 * <ul>
 *   <li>per-day expansion at submit (Sundays + holidays excluded);</li>
 *   <li>balance hold / consume / release;</li>
 *   <li>per-day decisions only at the MANAGER stage (HR ratifies the bundle);</li>
 *   <li>parent-status rollup (PARTIALLY_APPROVED when mixed);</li>
 *   <li>attendance row update on approved days.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository requests;
    private final LeaveRequestDayRepository days;
    private final LeaveTypeRepository leaveTypes;
    private final HolidayRepository holidays;
    private final AttendanceRecordRepository attendance;
    private final ApprovalService approvals;
    private final ApprovalInboxService inbox;
    private final LeaveBalanceService balances;
    private final LeaveDtoAssembler assembler;
    private final AttendanceRecomputeService recompute;
    private final CallerContext caller;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final AttendancePeriodLockService periodLocks;
    private final RequestCodeService requestCodes;
    private final OnBehalfResolver onBehalf;
    private final ApprovalEventPublisher approvalEvents;
    private final com.forward.hrms.commons.cache.EmployeeDisplayCache displayCache;
    private final DepartmentScope departmentScope;
    private final InboxSearch inboxSearch;
    private final EmployeeClient employees;

    /**
     * §7.attendance #9 — refuse very old back-dated leaves. HR has a
     * regularization endpoint for legitimate back-fills; a leave request
     * spanning a year ago is almost always a typo or a confused employee.
     * Tunable later if HR pushes back.
     */
    private static final java.time.Period LEAVE_PAST_BOUND = java.time.Period.ofDays(90);

    /**
     * Every leave type's balance for a colleague in the caller's own
     * department — the counterpart of {@code GET /leaves/me/balances} for the
     * team-request-generation tab, where a clerk picks the employee they are
     * about to raise a leave request for.
     *
     * <p>Delegates to {@link LeaveBalanceService#myBalances(long, Integer)}
     * rather than reimplementing the query, so the list a clerk sees for a
     * colleague is exactly the list that colleague sees for themselves — same
     * active-cycle selection, same MATERNITY exclusion. Divergence there would
     * be a support ticket about two screens disagreeing.
     *
     * <p>Lives here rather than on {@link LeaveBalanceService} because this
     * class already holds both collaborators; putting it there would add
     * {@link OnBehalfResolver} to a pure balance-math service.
     */
    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> balancesForDepartmentEmployee(long callerEmployeeId, long employeeId,
                                                               Integer year) {
        onBehalf.requireSameDepartmentAsCaller(callerEmployeeId, employeeId);
        return balances.myBalances(employeeId, year);
    }

    @Transactional
    public LeaveRequestDto submit(long callerEmployeeId, CreateLeaveRequest req) {
        OnBehalfResolver.Resolution resolution = onBehalf.resolve(callerEmployeeId, req.onBehalfOf(), OnBehalfResolver.TEAM_GEN_LEAVE);
        long employeeId = resolution.targetEmployeeId();
        LeaveType type = leaveTypes.findById(req.leaveTypeId())
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.LEAVE_TYPE_NOT_FOUND, "leave type not found"));
        validateRange(req);
        periodLocks.assertOpen(req.fromDate(), req.toDate());
        // §7.attendance #9 — fromDate must not be too far in the past, and
        // the employee must be in a status that can take leave. SUSPENDED /
        // TERMINATED can't submit; ON_NOTICE is a separate HR call.
        java.time.LocalDate today = java.time.LocalDate.now();
        if (req.fromDate().isBefore(today.minus(LEAVE_PAST_BOUND))) {
            throw new UnprocessableError(AttendanceErrorCode.REQUEST_DATE_OUT_OF_RANGE,
                    "fromDate is too far in the past — use regularization for back-fills");
        }
        // SELF path: a regular employee submitting their own leave doesn't
        // hold employee.read, so we can't call GET /employees/{id}. Reuse
        // the EmployeeView already resolved by the controller via
        // /employees/me (request-scoped cache in CallerContext).
        // ON_BEHALF paths: OnBehalfResolver already verified the target
        // exists and is ACTIVE / ON_NOTICE — no need to re-fetch.
        EmployeeClient.EmployeeView emp = resolution.mode() == OnBehalfResolver.Mode.SELF
                ? caller.requireEmployee()
                : resolution.target();
        String status = emp.employmentStatus();
        if (!"ACTIVE".equals(status) && !"ON_NOTICE".equals(status)) {
            throw new ConflictError(AttendanceErrorCode.EMPLOYEE_NOT_ACTIVE,
                    "employee status " + status + " cannot submit leave");
        }
        // Backfill the joining date onto the attendance mirror if it's
        // missing (employee mirrored before the column existed) — keeps
        // JOINING-anchored (annual) cycle math correct for legacy rows.
        balances.captureJoiningDate(employeeId, emp.joiningDate());
        if (!requests.findActiveOverlapping(employeeId, req.fromDate(), req.toDate()).isEmpty()) {
            throw new ConflictError(AttendanceErrorCode.LEAVE_OVERLAPS_EXISTING,
                    "request overlaps an existing non-cancelled leave");
        }

        List<LocalDate> workDates = expandWorkingDays(req.fromDate(), req.toDate());
        if (workDates.isEmpty()) {
            throw new UnprocessableError(AttendanceErrorCode.LEAVE_RANGE_EMPTY,
                    "no working days in the requested range");
        }
        BigDecimal totalDays = computeDays(workDates, req);

        if (type.isPaid() && balances.availableFor(employeeId, type.getId(), workDates.getFirst())
                .compareTo(totalDays) < 0) {
            throw new ConflictError(AttendanceErrorCode.LEAVE_BALANCE_INSUFFICIENT,
                    "insufficient leave balance");
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setRequestCode(requestCodes.allocate("LR"));
        lr.setEmployeeId(employeeId);
        lr.setLeaveTypeId(type.getId());
        lr.setFromDate(req.fromDate());
        lr.setToDate(req.toDate());
        lr.setHalfDay(req.halfDay());
        lr.setHalfDayPart(req.halfDayPart());
        lr.setDays(totalDays);
        lr.setReason(req.reason());
        lr.setStatus(LeaveRequestStatus.PENDING);
        requests.save(lr);

        for (LocalDate d : workDates) {
            LeaveRequestDay day = new LeaveRequestDay();
            day.setLeaveRequestId(lr.getId());
            day.setAttendanceDate(d);
            day.setStatus(LeaveDayStatus.PENDING);
            days.save(day);
        }

        balances.hold(employeeId, type.getId(), workDates.getFirst(), totalDays);

        List<ApprovalStep> chain = approvals.openChain(RequestType.LEAVE.name(), lr.getId(),
                resolution.approvalAnchorEmployeeId());

        outbox.publish(AttendanceEvents.TOPIC, "leave:" + lr.getId(),
                AttendanceEvents.LEAVE_APPLIED,
                new AttendanceEvents.LeaveApplied(
                        lr.getId(), employeeId, type.getCode(),
                        lr.getFromDate(), lr.getToDate(), totalDays.doubleValue(),
                        workDates, assembler.stageSnapshots(chain)));
        audit.emit("leave_request", String.valueOf(lr.getId()), "applied",
                null, Map.of(
                        "leaveType", type.getCode(),
                        "fromDate", lr.getFromDate().toString(),
                        "toDate", lr.getToDate().toString(),
                        "days", totalDays.toString()),
                true);

        if (resolution.autoApproves()) {
            autoApproveOnBehalf(lr, resolution.mode());
        }
        return assembler.leaveDto(lr);
    }

    /**
     * Manager-on-behalf submissions skip the manager re-approval step:
     * the manager has already implicitly approved by submitting. HR
     * override goes further and clears the HR stage too, landing the
     * request in terminal-APPROVED with every PENDING day APPROVED.
     */
    private void autoApproveOnBehalf(LeaveRequest lr, OnBehalfResolver.Mode mode) {
        CurrentUser actor = caller.requireUser();
        String managerNote = mode == OnBehalfResolver.Mode.HR_OVERRIDE
                ? "submitted on behalf by HR (override)"
                : "submitted on behalf by manager";
        ApprovalService.Decision managerStage = approvals.closeCurrentStage(
                RequestType.LEAVE.name(), lr.getId(), StepStatus.APPROVED, actor, managerNote);
        approvalEvents.stageApproved(RequestType.LEAVE, lr.getId(), lr.getEmployeeId(),
                managerStage.step().getStageNo(), managerStage.step().getLabel(),
                actor.userId(), assembler.nextStageEvent(managerStage.nextPending()));
        if (mode != OnBehalfResolver.Mode.HR_OVERRIDE) return;

        // HR override: also close the HR stage and ratify every PENDING day.
        if (managerStage.nextPending() != null) {
            ApprovalService.Decision hrStage = approvals.closeCurrentStage(
                    RequestType.LEAVE.name(), lr.getId(), StepStatus.APPROVED, actor,
                    "submitted on behalf by HR (override)");
            applyTerminalDayDecisionsToAll(lr, hrStage.step().getStageNo(), actor,
                    LeaveDayStatus.APPROVED, null);
            finalizeStatus(lr);
        }
    }

    @Transactional
    public LeaveRequestDto approve(long id, ApproveRequest body) {
        LeaveRequest lr = requireRequest(id);
        periodLocks.assertOpen(lr.getFromDate(), lr.getToDate());
        ApprovalService.Decision decision = approvals.approve(RequestType.LEAVE.name(), id, body == null ? null : body.note());
        boolean isFinalStage = decision.nextPending() == null;

        if (!isFinalStage) {
            approvalEvents.stageApproved(RequestType.LEAVE, id, lr.getEmployeeId(),
                    decision.step().getStageNo(), decision.step().getLabel(),
                    decision.actor().userId(), assembler.nextStageEvent(decision.nextPending()));
            return assembler.leaveDto(lr);
        }

        // Final-stage full approval: every PENDING day → APPROVED.
        applyTerminalDayDecisionsToAll(lr, decision.step().getStageNo(), decision.actor(), LeaveDayStatus.APPROVED, null);
        finalizeStatus(lr);
        return assembler.leaveDto(lr);
    }

    @Transactional
    public LeaveRequestDto reject(long id, RejectRequest body) {
        LeaveRequest lr = requireRequest(id);
        ApprovalService.Decision decision = approvals.reject(RequestType.LEAVE.name(), id, body.reason());

        // Release only the balance still on hold for PENDING days. Days the
        // MANAGER already APPROVED are CONSUMED and stay that way (don't
        // release back to the bank). Days the MANAGER REJECTED were already
        // released at that stage. Bug fix: previously released `lr.getDays()`
        // — the full request total — which over-released after a manager
        // partial-approve at the HR stage and corrupted the balance bank.
        BigDecimal pendingPortion = sumPendingPortion(id, lr);

        // Cascade every PENDING day → REJECTED. After per-day decisions the
        // PENDING set is empty (HR-stage rejections after manager partial),
        // so this loop is a no-op in that path.
        for (LeaveRequestDay d : days.findAllByLeaveRequestIdAndStatus(id, LeaveDayStatus.PENDING)) {
            d.setStatus(LeaveDayStatus.REJECTED);
            d.setDecidedBy(decision.actor().userId());
            d.setDecidedAt(LocalDateTime.now());
            d.setDecidedAtStage(decision.step().getStageNo());
            d.setRejectionReason(body.reason());
            outbox.publish(AttendanceEvents.TOPIC, "leave:" + id,
                    AttendanceEvents.LEAVE_DAY_REJECTED,
                    new AttendanceEvents.LeaveDayRejected(
                            id, lr.getEmployeeId(), d.getAttendanceDate(),
                            decision.actor().userId(), decision.step().getStageNo(),
                            body.reason()));
        }

        approvalEvents.stageRejected(RequestType.LEAVE, id, lr.getEmployeeId(),
                decision.step().getStageNo(), decision.step().getLabel(),
                decision.actor().userId(), body.reason());

        if (pendingPortion.signum() > 0) {
            balances.release(lr.getEmployeeId(), lr.getLeaveTypeId(),
                    lr.getFromDate(), pendingPortion);
        }

        finalizeStatus(lr);
        return assembler.leaveDto(lr);
    }

    @Transactional
    public LeaveRequestDto decidePerDay(long id, PerDayDecideRequest body) {
        LeaveRequest lr = requireRequest(id);
        periodLocks.assertOpen(lr.getFromDate(), lr.getToDate());
        if (!approvals.isAtResolverKind(RequestType.LEAVE.name(), id, ResolverKind.MANAGER)) {
            throw new ConflictError(AttendanceErrorCode.STAGE_PER_DAY_NOT_ALLOWED,
                    "per-day decisions allowed only at the MANAGER stage");
        }
        ApprovalStep current = approvals.currentPending(RequestType.LEAVE.name(), id)
                .orElseThrow(() -> new ConflictError(AttendanceErrorCode.NO_PENDING_STAGE, "no pending stage"));
        CurrentUser user = caller.requireUser();
        if (!approvals.canAct(RequestType.LEAVE.name(), current, user)) {
            throw new ConflictError(AttendanceErrorCode.NOT_AUTHORIZED, "not authorized");
        }

        java.util.Map<LocalDate, LeaveRequestDay> dayByDate = new java.util.LinkedHashMap<>();
        for (LeaveRequestDay row : days.findAllByLeaveRequestIdOrderByAttendanceDateAsc(id)) {
            dayByDate.put(row.getAttendanceDate(), row);
        }
        for (PerDayDecideRequest.Decision d : body.decisions()) {
            LeaveRequestDay day = dayByDate.get(d.workDate());
            if (day == null) {
                throw new NotFoundError(AttendanceErrorCode.LEAVE_DAY_NOT_FOUND,
                        "no day row for " + d.workDate());
            }
            if (day.getStatus() != LeaveDayStatus.PENDING) continue;
            applyTerminalDayDecision(lr, day, current.getStageNo(), user, d);
        }

        // If every day is decided, close the MANAGER stage and route on:
        //   any day APPROVED → stage APPROVED, advance to HR for ratification
        //   all days REJECTED → stage REJECTED, skip later stages
        boolean allDecided = days.findAllByLeaveRequestIdAndStatus(id, LeaveDayStatus.PENDING).isEmpty();
        if (allDecided) {
            boolean anyApproved = !days.findAllByLeaveRequestIdAndStatus(id, LeaveDayStatus.APPROVED).isEmpty();
            StepStatus outcome = anyApproved ? StepStatus.APPROVED : StepStatus.REJECTED;
            ApprovalService.Decision decision = approvals.closeCurrentStage(RequestType.LEAVE.name(), id, outcome, user);

            if (outcome == StepStatus.APPROVED) {
                approvalEvents.stageApproved(RequestType.LEAVE, id, lr.getEmployeeId(),
                        current.getStageNo(), current.getLabel(),
                        user.userId(), assembler.nextStageEvent(decision.nextPending()));
            } else {
                approvalEvents.stageRejected(RequestType.LEAVE, id, lr.getEmployeeId(),
                        current.getStageNo(), current.getLabel(),
                        user.userId(), "all days rejected at per-day decision");
            }
            finalizeStatus(lr);
        }
        return assembler.leaveDto(lr);
    }

    @Transactional
    public LeaveRequestDto cancel(long id) {
        LeaveRequest lr = requireRequest(id);
        var me = caller.requireEmployee();
        if (me.id() != lr.getEmployeeId()) {
            throw new com.forward.hrms.commons.core.error.ForbiddenError(
                    AttendanceErrorCode.NOT_AUTHORIZED, "only the submitter can cancel");
        }
        boolean cancellable = lr.getStatus() == LeaveRequestStatus.PENDING
                || (lr.getStatus() == LeaveRequestStatus.APPROVED && lr.getFromDate().isAfter(LocalDate.now()))
                || (lr.getStatus() == LeaveRequestStatus.PARTIALLY_APPROVED
                        && lr.getFromDate().isAfter(LocalDate.now()));
        if (!cancellable) {
            throw new ConflictError(AttendanceErrorCode.LEAVE_NOT_CANCELLABLE,
                    "request cannot be cancelled in its current state");
        }
        BigDecimal pendingHeld = sumOfPendingDays(id);
        BigDecimal usedToReverse = sumOfApprovedDays(id);
        balances.release(lr.getEmployeeId(), lr.getLeaveTypeId(), lr.getFromDate(), pendingHeld);
        balances.unconsume(lr.getEmployeeId(), lr.getLeaveTypeId(), lr.getFromDate(), usedToReverse);

        approvals.cancelChain(RequestType.LEAVE.name(), id);
        for (LeaveRequestDay d : days.findAllByLeaveRequestIdOrderByAttendanceDateAsc(id)) {
            if (d.getStatus() == LeaveDayStatus.PENDING) {
                d.setStatus(LeaveDayStatus.REJECTED);
            }
        }
        lr.setStatus(LeaveRequestStatus.CANCELLED);

        outbox.publish(AttendanceEvents.TOPIC, "leave:" + id,
                AttendanceEvents.LEAVE_CANCELLED,
                new AttendanceEvents.LeaveCancelled(id, lr.getEmployeeId(), null));
        audit.emit("leave_request", String.valueOf(id), "cancelled",
                null, Map.of("status", lr.getStatus().name()), true);
        return assembler.leaveDto(lr);
    }

    @Transactional(readOnly = true)
    public LeaveRequestDto get(long id) {
        return assembler.leaveDto(requireRequest(id));
    }

    @Transactional(readOnly = true)
    public List<LeaveDayDto> listDays(long id) {
        if (!requests.existsById(id)) {
            throw new NotFoundError(AttendanceErrorCode.LEAVE_REQUEST_NOT_FOUND, "leave request not found");
        }
        return days.findAllByLeaveRequestIdOrderByAttendanceDateAsc(id).stream()
                .map(LeaveDayDto::of).toList();
    }

    @Transactional
    public LeavePreviewDto preview(long employeeId, CreateLeaveRequest req) {
        validateRange(req);
        LeaveType type = leaveTypes.findById(req.leaveTypeId())
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.LEAVE_TYPE_NOT_FOUND, "leave type not found"));

        int sundaysSkipped = 0;
        int holidaysSkipped = 0;
        int workingCount = 0;
        for (LocalDate d = req.fromDate(); !d.isAfter(req.toDate()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) sundaysSkipped++;
            else if (holidays.existsByHolidayDate(d)) holidaysSkipped++;
            else workingCount++;
        }
        BigDecimal workingDays = req.halfDay() && workingCount == 1
                ? new BigDecimal("0.5")
                : BigDecimal.valueOf(workingCount);

        List<String> warnings = new ArrayList<>();
        if (workingCount == 0) warnings.add("LEAVE_RANGE_EMPTY");
        if (req.fromDate().getYear() != req.toDate().getYear()) warnings.add("CROSSES_FY");

        BigDecimal balanceAfter = null;
        if (type.isPaid() && workingCount > 0) {
            BigDecimal available = balances.availableFor(employeeId, type.getId(), req.fromDate());
            balanceAfter = available.subtract(workingDays);
            if (balanceAfter.signum() < 0) warnings.add("NEGATIVE_BALANCE");
        }

        if (!requests.findActiveOverlapping(employeeId, req.fromDate(), req.toDate()).isEmpty()) {
            warnings.add("OVERLAPS_EXISTING");
        }
        return new LeavePreviewDto(workingDays, sundaysSkipped, holidaysSkipped, balanceAfter, warnings);
    }

    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<LeaveRequestDto> searchForEmployee(
            long employeeId, LeaveRequestStatus status, Long leaveTypeId,
            LocalDate from, LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<LeaveRequest> page =
                requests.search(employeeId, status, leaveTypeId, from, to, pageable);
        List<LeaveRequestDto> content = page.getContent().stream().map(assembler::leaveDto).toList();
        return com.forward.hrms.commons.web.page.PageResponse.from(page, content);
    }

    /**
     * Leaves of the caller's direct reports, whoever submitted them — the
     * report, or the manager on their behalf. Empty page for a caller who
     * manages nobody. Optional {@code employeeId} narrows to one report
     * (anyone else yields an empty page). Rows carry the target's summary
     * from the display cache, like {@link #forDepartment}.
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow> searchForTeam(
            LeaveRequestStatus status, Long leaveTypeId, LocalDate from, LocalDate to,
            Long employeeId, org.springframework.data.domain.Pageable pageable) {
        List<Long> reportIds = employees.myDirectReportIds();
        if (employeeId != null) {
            reportIds = reportIds.contains(employeeId) ? List.of(employeeId) : List.of();
        }
        if (reportIds.isEmpty()) {
            return com.forward.hrms.commons.web.page.PageResponse.from(
                    org.springframework.data.domain.Page.empty(pageable), List.of());
        }
        var page = requests.searchForEmployees(reportIds, status, leaveTypeId, from, to, pageable);
        return withEmployeeSummaries(page);
    }

    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<LeaveRequestDto> pendingApprovals(
            LocalDate from, LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        return pendingApprovals(from, to, null, null, pageable);
    }

    /**
     * Inbox with optional filters. {@code q} matches (case-insensitive) the
     * request id exactly when numeric, or a substring of the request code,
     * employee code, or employee full name. {@code leaveTypeId} is exact.
     */
    public com.forward.hrms.commons.web.page.PageResponse<LeaveRequestDto> pendingApprovals(
            LocalDate from, LocalDate to, String q, Long leaveTypeId,
            org.springframework.data.domain.Pageable pageable) {
        CurrentUser user = caller.requireUser();
        return ApprovalInboxQueries.findPending(
                inbox, RequestType.LEAVE.name(), user,
                requests::findAllById, rows -> filterInbox(rows, q, leaveTypeId),
                LeaveRequest::getId,
                LeaveRequest::getFromDate, LeaveRequest::getToDate,
                from, to, pageable, assembler::leaveDto);
    }

    private List<LeaveRequest> filterInbox(List<LeaveRequest> rows, String q, Long leaveTypeId) {
        List<LeaveRequest> typed = leaveTypeId == null ? rows
                : rows.stream().filter(r -> leaveTypeId.equals(r.getLeaveTypeId())).toList();
        return inboxSearch.filter(typed, q,
                LeaveRequest::getId, LeaveRequest::getRequestCode, LeaveRequest::getEmployeeId);
    }

    private void applyTerminalDayDecisionsToAll(LeaveRequest lr, int finalStageNo, CurrentUser user,
                                                LeaveDayStatus newStatus, String reason) {
        for (LeaveRequestDay d : days.findAllByLeaveRequestIdOrderByAttendanceDateAsc(lr.getId())) {
            if (d.getStatus() != LeaveDayStatus.PENDING) continue;
            applyTerminalDayDecision(lr, d, finalStageNo, user,
                    new PerDayDecideRequest.Decision(d.getAttendanceDate(),
                            newStatus == LeaveDayStatus.APPROVED
                                    ? com.forward.hrms.attendance.enums.ApproveAction.APPROVE
                                    : com.forward.hrms.attendance.enums.ApproveAction.REJECT,
                            reason));
        }
    }

    private void applyTerminalDayDecision(LeaveRequest lr, LeaveRequestDay day, int finalStageNo,
                                          CurrentUser user, PerDayDecideRequest.Decision d) {
        boolean approve = d.action() == com.forward.hrms.attendance.enums.ApproveAction.APPROVE;
        BigDecimal portion = portionFor(lr, day);
        if (approve) {
            day.setStatus(LeaveDayStatus.APPROVED);
            balances.consume(lr.getEmployeeId(), lr.getLeaveTypeId(), day.getAttendanceDate(), portion);
            boolean paid = leaveTypes.findById(lr.getLeaveTypeId()).map(LeaveType::isPaid).orElse(true);
            stampLeaveOnAttendance(lr.getEmployeeId(), day.getAttendanceDate(), paid);
            outbox.publish(AttendanceEvents.TOPIC, "leave:" + lr.getId(),
                    AttendanceEvents.LEAVE_DAY_APPROVED,
                    new AttendanceEvents.LeaveDayApproved(
                            lr.getId(), lr.getEmployeeId(),
                            leaveTypes.findById(lr.getLeaveTypeId()).map(LeaveType::getCode).orElse(null),
                            day.getAttendanceDate(), user.userId(), finalStageNo));
        } else {
            day.setStatus(LeaveDayStatus.REJECTED);
            balances.release(lr.getEmployeeId(), lr.getLeaveTypeId(), day.getAttendanceDate(), portion);
            day.setRejectionReason(d.reason());
            outbox.publish(AttendanceEvents.TOPIC, "leave:" + lr.getId(),
                    AttendanceEvents.LEAVE_DAY_REJECTED,
                    new AttendanceEvents.LeaveDayRejected(
                            lr.getId(), lr.getEmployeeId(), day.getAttendanceDate(),
                            user.userId(), finalStageNo, d.reason()));
        }
        day.setDecidedBy(user.userId());
        day.setDecidedAt(LocalDateTime.now());
        day.setDecidedAtStage(finalStageNo);
    }

    private BigDecimal portionFor(LeaveRequest lr, LeaveRequestDay day) {
        if (!lr.isHalfDay()) return BigDecimal.ONE;
        // Half-day applies to the whole request (single-day half-days).
        return new BigDecimal("0.5");
    }

    private void stampLeaveOnAttendance(long employeeId, LocalDate date, boolean paid) {
        AttendanceRecord rec = attendance.findByEmployeeIdAndAttendanceDate(employeeId, date)
                .orElseGet(() -> recompute.ensureAbsentRow(employeeId, date));
        rec.setStatus(AttendanceStatus.LEAVE);
        // §7.payroll #23 — record the paid flag from the leave type so
        // the daily projection alone tells payroll whether this LEAVE
        // counts toward paid days or LOP. No payroll-side join needed.
        rec.setPaid(paid);
        attendance.save(rec);
    }

    private void finalizeStatus(LeaveRequest lr) {
        boolean anyPending = days.findAllByLeaveRequestIdAndStatus(lr.getId(), LeaveDayStatus.PENDING).size() > 0
                || approvals.findChain(RequestType.LEAVE.name(), lr.getId()).stream()
                        .anyMatch(s -> s.getStatus() == StepStatus.PENDING);
        if (anyPending) return;

        boolean anyRejectedStage = approvals.findChain(RequestType.LEAVE.name(), lr.getId()).stream()
                .anyMatch(s -> s.getStatus() == StepStatus.REJECTED);
        long approvedCount = days.findAllByLeaveRequestIdAndStatus(lr.getId(), LeaveDayStatus.APPROVED).size();
        long rejectedCount = days.findAllByLeaveRequestIdAndStatus(lr.getId(), LeaveDayStatus.REJECTED).size();

        LeaveRequestStatus finalStatus;
        if (anyRejectedStage && approvedCount == 0) finalStatus = LeaveRequestStatus.REJECTED;
        else if (approvedCount > 0 && rejectedCount == 0) finalStatus = LeaveRequestStatus.APPROVED;
        else if (approvedCount == 0) finalStatus = LeaveRequestStatus.REJECTED;
        else finalStatus = LeaveRequestStatus.PARTIALLY_APPROVED;
        lr.setStatus(finalStatus);

        List<LocalDate> approvedDates = days.findAllByLeaveRequestIdAndStatus(lr.getId(), LeaveDayStatus.APPROVED)
                .stream().map(LeaveRequestDay::getAttendanceDate).toList();
        List<LocalDate> rejectedDates = days.findAllByLeaveRequestIdAndStatus(lr.getId(), LeaveDayStatus.REJECTED)
                .stream().map(LeaveRequestDay::getAttendanceDate).toList();

        outbox.publish(AttendanceEvents.TOPIC, "leave:" + lr.getId(),
                AttendanceEvents.LEAVE_REQUEST_FINALIZED,
                new AttendanceEvents.LeaveRequestFinalized(
                        lr.getId(), lr.getEmployeeId(), finalStatus.name(), approvedDates, rejectedDates));
    }

    private BigDecimal sumOfPendingDays(long requestId) {
        long count = days.findAllByLeaveRequestIdAndStatus(requestId, LeaveDayStatus.PENDING).size();
        return BigDecimal.valueOf(count);
    }

    private BigDecimal sumOfApprovedDays(long requestId) {
        long count = days.findAllByLeaveRequestIdAndStatus(requestId, LeaveDayStatus.APPROVED).size();
        return BigDecimal.valueOf(count);
    }

    /**
     * Pending balance still held for {@code requestId}, accounting for
     * half-day requests (single day = 0.5 portion). Used to release the
     * correct slice on HR-stage rejection after a manager partial-approve
     * — releasing {@code lr.getDays()} would over-release.
     */
    private BigDecimal sumPendingPortion(long requestId, LeaveRequest lr) {
        long count = days.findAllByLeaveRequestIdAndStatus(requestId, LeaveDayStatus.PENDING).size();
        if (lr.isHalfDay() && count == 1) return new BigDecimal("0.5");
        return BigDecimal.valueOf(count);
    }

    private void validateRange(CreateLeaveRequest req) {
        if (req.toDate().isBefore(req.fromDate())) {
            throw new UnprocessableError(AttendanceErrorCode.LEAVE_RANGE_INVALID,
                    "to_date must be on or after from_date");
        }
        if (req.halfDay() && (req.halfDayPart() == null
                || (req.halfDayPart() != HalfDayPart.AM && req.halfDayPart() != HalfDayPart.PM))) {
            throw new UnprocessableError(AttendanceErrorCode.LEAVE_RANGE_INVALID,
                    "half_day_part is required for half-day leaves");
        }
    }

    private List<LocalDate> expandWorkingDays(LocalDate from, LocalDate to) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (holidays.existsByHolidayDate(d)) continue;
            out.add(d);
        }
        return out;
    }

    private BigDecimal computeDays(List<LocalDate> workDates, CreateLeaveRequest req) {
        if (req.halfDay() && workDates.size() == 1) return new BigDecimal("0.5");
        return BigDecimal.valueOf(workDates.size());
    }

    private LeaveRequest requireRequest(long id) {
        return requests.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.LEAVE_REQUEST_NOT_FOUND,
                        "leave request not found"));
    }

    /**
     * Leaves the caller filed on someone else's behalf (a manager for a
     * direct report, a team-request generator for a colleague). Their own
     * self-submissions are excluded. Mirrors
     * {@code RegularizationService#filedOnBehalf}: matched on
     * {@code created_by}, each row paired with the target's summary from the
     * shared display cache (the callers hold no {@code employee.read}).
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow> filedOnBehalf(
            com.forward.hrms.attendance.enums.LeaveRequestStatus status,
            java.time.LocalDate filedFrom, java.time.LocalDate filedTo,
            org.springframework.data.domain.Pageable pageable) {
        var actor = caller.requireUser();
        var me = caller.requireEmployee();
        var page = requests.searchFiledOnBehalf(actor.username(), me.id(), status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        var displays = displayCache.getMany(page.getContent().stream()
                .map(com.forward.hrms.attendance.entity.LeaveRequest::getEmployeeId).distinct().toList());
        return com.forward.hrms.commons.web.page.PageResponse.from(page,
                page.getContent().stream()
                        .map(r -> {
                            var d = displays.get(r.getEmployeeId());
                            return new com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow(
                                    assembler.leaveDto(r),
                                    d == null ? null
                                            : com.forward.hrms.commons.cache.EmployeeSummaryMapper.toSummary(d));
                        })
                        .toList());
    }

    /**
     * Every leave in the caller's department, whoever filed it — for a
     * team-request generator, or for a manager (MANAGER role, or anyone with
     * direct reports) looking past their own team. Optional {@code employeeId} narrows to one colleague (an id outside the
     * department yields an empty page). Rows carry the target's summary from
     * the display cache, like {@link #filedOnBehalf}.
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow> forDepartment(
            com.forward.hrms.attendance.enums.LeaveRequestStatus status,
            java.time.LocalDate filedFrom, java.time.LocalDate filedTo, Long employeeId,
            org.springframework.data.domain.Pageable pageable) {
        CurrentUser user = caller.requireUser();
        if (!user.hasPermission("team.request.generation.leave")
                && !user.hasRole("MANAGER")
                && employees.myDirectReportIds().isEmpty()) {
            throw new com.forward.hrms.commons.core.error.ForbiddenError(
                    "Only managers and team-request generators can list their department's leaves");
        }
        java.util.List<Long> ids = departmentScope.employeeIds(employeeId);
        if (ids.isEmpty()) {
            return com.forward.hrms.commons.web.page.PageResponse.from(
                    org.springframework.data.domain.Page.empty(pageable), java.util.List.of());
        }
        var page = requests.searchDepartment(ids, status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        return withEmployeeSummaries(page);
    }

    /** Pairs each leave with the target's summary from the display cache. */
    private com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow> withEmployeeSummaries(
            org.springframework.data.domain.Page<LeaveRequest> page) {
        var displays = displayCache.getMany(page.getContent().stream()
                .map(com.forward.hrms.attendance.entity.LeaveRequest::getEmployeeId).distinct().toList());
        return com.forward.hrms.commons.web.page.PageResponse.from(page,
                page.getContent().stream()
                        .map(r -> {
                            var d = displays.get(r.getEmployeeId());
                            return new com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow(
                                    assembler.leaveDto(r),
                                    d == null ? null
                                            : com.forward.hrms.commons.cache.EmployeeSummaryMapper.toSummary(d));
                        })
                        .toList());
    }
}

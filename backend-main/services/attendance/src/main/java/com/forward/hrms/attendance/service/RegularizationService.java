package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.ApproveRequest;
import com.forward.hrms.attendance.dto.request.RegularizationRequestDto;
import com.forward.hrms.attendance.dto.request.RejectRequest;
import com.forward.hrms.attendance.entity.RegularizationRequest;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.RegularizationStatus;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.RegularizationRequestRepository;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.StepStatus;
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

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegularizationService {

    private final RegularizationRequestRepository regularizations;
    private final ApprovalService approvals;
    private final ApprovalInboxService inbox;
    private final LeaveDtoAssembler assembler;
    private final PunchService punches;
    private final CallerContext caller;
    private final Outbox outbox;
    private final AuditEmitter auditEmitter;
    private final AttendancePeriodLockService periodLocks;
    private final RequestCodeService requestCodes;
    private final OnBehalfResolver onBehalf;
    private final com.forward.hrms.commons.cache.EmployeeDisplayCache displayCache;
    private final DepartmentScope departmentScope;
    private final InboxSearch inboxSearch;

    /**
     * §7.attendance #15 — regularization is for missing or amended punches
     * on past dates. Future workDate makes no business sense; cap to a
     * sane backfill window so an operator typo doesn't queue a
     * regularization for the year-ago date.
     */
    private static final java.time.Period REGULARIZATION_BACKFILL_BOUND = java.time.Period.ofDays(30);

    @Transactional
    public com.forward.hrms.attendance.dto.response.RegularizationRequestDto submit(
            long callerEmployeeId, RegularizationRequestDto req) {
        OnBehalfResolver.Resolution resolution = onBehalf.resolve(callerEmployeeId, req.onBehalfOf(), OnBehalfResolver.TEAM_GEN_REGULARIZATION);
        long employeeId = resolution.targetEmployeeId();
        if (req.checkIn() == null && req.checkOut() == null) {
            throw new UnprocessableError(AttendanceErrorCode.REGULARIZATION_INVALID,
                    "at least one of checkIn / checkOut is required");
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        if (req.workDate() == null || req.workDate().isAfter(today)) {
            throw new UnprocessableError(AttendanceErrorCode.REQUEST_DATE_OUT_OF_RANGE,
                    "workDate cannot be in the future");
        }
        if (req.workDate().isBefore(today.minus(REGULARIZATION_BACKFILL_BOUND))) {
            throw new UnprocessableError(AttendanceErrorCode.REQUEST_DATE_OUT_OF_RANGE,
                    "workDate too far in the past — backfill window is "
                            + REGULARIZATION_BACKFILL_BOUND.getDays() + " days");
        }
        periodLocks.assertOpen(req.workDate());
        RegularizationRequest r = new RegularizationRequest();
        r.setRequestCode(requestCodes.allocate("REG"));
        r.setEmployeeId(employeeId);
        r.setAttendanceDate(req.workDate());
        r.setRequestedCheckIn(req.checkIn());
        r.setRequestedCheckOut(req.checkOut());
        r.setReason(req.reason());
        r.setStatus(RegularizationStatus.PENDING);
        regularizations.save(r);

        approvals.openChain(RequestType.REGULARIZATION.name(), r.getId(),
                resolution.approvalAnchorEmployeeId());
        auditEmitter.emit("regularization_request", String.valueOf(r.getId()), "applied",
                null, Map.of("workDate", req.workDate().toString()), true);

        if (resolution.autoApproves()) {
            autoApproveOnBehalf(r, resolution.mode());
        }
        return assembler.regularizationDto(r);
    }

    /**
     * Manager-on-behalf submissions skip the manager re-approval step:
     * the manager has already implicitly approved by submitting. HR
     * override clears every stage.
     */
    private void autoApproveOnBehalf(RegularizationRequest r, OnBehalfResolver.Mode mode) {
        CurrentUser actor = caller.requireUser();
        String managerNote = mode == OnBehalfResolver.Mode.HR_OVERRIDE
                ? "submitted on behalf by HR (override)"
                : "submitted on behalf by manager";
        ApprovalService.Decision managerStage = approvals.closeCurrentStage(
                RequestType.REGULARIZATION.name(), r.getId(),
                StepStatus.APPROVED, actor, managerNote);
        outbox.publish(AttendanceEvents.TOPIC, "regularization:" + r.getId(),
                AttendanceEvents.REGULARIZATION_STAGE_APPROVED,
                new AttendanceEvents.RegularizationStageApproved(
                        r.getId(), r.getEmployeeId(),
                        managerStage.step().getStageNo(), managerStage.step().getLabel(),
                        actor.userId(), assembler.nextStageEvent(managerStage.nextPending())));
        if (mode != OnBehalfResolver.Mode.HR_OVERRIDE) return;

        if (managerStage.nextPending() != null) {
            approvals.closeCurrentStage(
                    RequestType.REGULARIZATION.name(), r.getId(),
                    StepStatus.APPROVED, actor,
                    "submitted on behalf by HR (override)");
            r.setStatus(RegularizationStatus.APPROVED);
            boolean wroteAny = false;
            if (r.getRequestedCheckIn() != null) {
                wroteAny |= punches.writeRegularizedPunchNoRecompute(r.getEmployeeId(), r.getRequestedCheckIn(),
                        r.getAttendanceDate(), r.getId()).isPresent();
            }
            if (r.getRequestedCheckOut() != null) {
                wroteAny |= punches.writeRegularizedPunchNoRecompute(r.getEmployeeId(), r.getRequestedCheckOut(),
                        r.getAttendanceDate(), r.getId()).isPresent();
            }
            if (wroteAny) {
                punches.recomputeForDate(r.getEmployeeId(), r.getAttendanceDate());
            }
            outbox.publish(AttendanceEvents.TOPIC, "regularization:" + r.getId(),
                    AttendanceEvents.REGULARIZATION_APPROVED,
                    new AttendanceEvents.RegularizationApproved(
                            r.getId(), r.getEmployeeId(), r.getAttendanceDate(), actor.userId()));
            auditEmitter.emit("regularization_request", String.valueOf(r.getId()), "approved",
                    null, Map.of("workDate", r.getAttendanceDate().toString()), true);
        }
    }

    @Transactional
    public com.forward.hrms.attendance.dto.response.RegularizationRequestDto approve(long id, ApproveRequest body) {
        RegularizationRequest r = require(id);
        periodLocks.assertOpen(r.getAttendanceDate());
        approvals.approve(RequestType.REGULARIZATION.name(), id, body == null ? null : body.note());
        return assembler.regularizationDto(r);
    }

    @Transactional
    public com.forward.hrms.attendance.dto.response.RegularizationRequestDto reject(long id, RejectRequest body) {
        RegularizationRequest r = require(id);
        approvals.reject(RequestType.REGULARIZATION.name(), id, body.reason());
        return assembler.regularizationDto(r);
    }

    @Transactional
    public com.forward.hrms.attendance.dto.response.RegularizationRequestDto cancel(long id) {
        RegularizationRequest r = require(id);
        var me = caller.requireEmployee();
        if (me.id() != r.getEmployeeId()) {
            throw new com.forward.hrms.commons.core.error.ForbiddenError(
                    AttendanceErrorCode.NOT_AUTHORIZED, "only the submitter can cancel");
        }
        if (r.getStatus() != RegularizationStatus.PENDING) {
            throw new ConflictError(AttendanceErrorCode.REGULARIZATION_NOT_PENDING,
                    "regularization can only be cancelled while PENDING");
        }
        approvals.cancelChain(RequestType.REGULARIZATION.name(), id);
        r.setStatus(RegularizationStatus.CANCELLED);
        outbox.publish(AttendanceEvents.TOPIC, "regularization:" + id,
                AttendanceEvents.REGULARIZATION_CANCELLED,
                new AttendanceEvents.RegularizationCancelled(id, r.getEmployeeId(), r.getAttendanceDate()));
        auditEmitter.emit("regularization_request", String.valueOf(id), "cancelled",
                null, Map.of("workDate", r.getAttendanceDate().toString()), true);
        return assembler.regularizationDto(r);
    }

    /**
     * Regularizations the caller filed on someone else's behalf (a manager
     * for a direct report, a team-request generator for a colleague). Their
     * own self-submissions are excluded.
     *
     * <p>Each row carries the target's {@code employee} summary from the
     * shared display cache — the callers (managers, team-request generators)
     * hold no {@code employee.read}, so they can't resolve names themselves.
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.RegularizationRow> filedOnBehalf(
            RegularizationStatus status, java.time.LocalDate from, java.time.LocalDate to,
            java.time.LocalDate filedFrom, java.time.LocalDate filedTo,
            org.springframework.data.domain.Pageable pageable) {
        CurrentUser actor = caller.requireUser();
        var me = caller.requireEmployee();
        var page = regularizations.searchFiledOnBehalf(
                actor.username(), me.id(), status, from, to,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        var displays = displayCache.getMany(page.getContent().stream()
                .map(RegularizationRequest::getEmployeeId).distinct().toList());
        return com.forward.hrms.commons.web.page.PageResponse.from(page,
                page.getContent().stream()
                        .map(r -> {
                            var d = displays.get(r.getEmployeeId());
                            return new com.forward.hrms.attendance.dto.response.ApprovalsInbox.RegularizationRow(
                                    assembler.regularizationDto(r),
                                    d == null ? null
                                            : com.forward.hrms.commons.cache.EmployeeSummaryMapper.toSummary(d));
                        })
                        .toList());
    }

    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.RegularizationRequestDto> pendingApprovals(
            java.time.LocalDate from, java.time.LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        return pendingApprovals(from, to, null, pageable);
    }

    /** Inbox with an optional {@code q} filter — see {@link InboxSearch} for the match rules. */
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.RegularizationRequestDto> pendingApprovals(
            java.time.LocalDate from, java.time.LocalDate to, String q,
            org.springframework.data.domain.Pageable pageable) {
        CurrentUser actor = caller.requireUser();
        return ApprovalInboxQueries.findPending(
                inbox, RequestType.REGULARIZATION.name(), actor,
                regularizations::findAllById,
                rows -> inboxSearch.filter(rows, q, RegularizationRequest::getId,
                        RegularizationRequest::getRequestCode, RegularizationRequest::getEmployeeId),
                RegularizationRequest::getId,
                RegularizationRequest::getAttendanceDate, RegularizationRequest::getAttendanceDate,
                from, to, pageable, assembler::regularizationDto);
    }

    private RegularizationRequest require(long id) {
        return regularizations.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.REGULARIZATION_NOT_FOUND,
                        "regularization not found"));
    }

    /**
     * Every regularization in the caller's department, whoever filed it —
     * the team-request generator's counterpart of a manager's team view.
     * Optional {@code employeeId} narrows to one colleague (an id outside the
     * department yields an empty page). Rows carry the target's summary from
     * the display cache, like {@link #filedOnBehalf}.
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.RegularizationRow> forDepartment(
            com.forward.hrms.attendance.enums.RegularizationStatus status,
            java.time.LocalDate filedFrom, java.time.LocalDate filedTo, Long employeeId,
            org.springframework.data.domain.Pageable pageable) {
        java.util.List<Long> ids = departmentScope.employeeIds(employeeId);
        if (ids.isEmpty()) {
            return com.forward.hrms.commons.web.page.PageResponse.from(
                    org.springframework.data.domain.Page.empty(pageable), java.util.List.of());
        }
        var page = regularizations.searchDepartment(ids, status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        var displays = displayCache.getMany(page.getContent().stream()
                .map(com.forward.hrms.attendance.entity.RegularizationRequest::getEmployeeId).distinct().toList());
        return com.forward.hrms.commons.web.page.PageResponse.from(page,
                page.getContent().stream()
                        .map(r -> {
                            var d = displays.get(r.getEmployeeId());
                            return new com.forward.hrms.attendance.dto.response.ApprovalsInbox.RegularizationRow(
                                    assembler.regularizationDto(r),
                                    d == null ? null
                                            : com.forward.hrms.commons.cache.EmployeeSummaryMapper.toSummary(d));
                        })
                        .toList());
    }
}

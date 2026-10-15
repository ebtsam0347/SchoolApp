package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.ApproveRequest;
import com.forward.hrms.attendance.dto.request.BulkOvertimeRequest;
import com.forward.hrms.attendance.dto.request.CreateOvertimeRequest;
import com.forward.hrms.attendance.dto.request.RejectRequest;
import com.forward.hrms.attendance.dto.response.BulkOvertimeResponse;
import com.forward.hrms.attendance.dto.response.OvertimeRequestDto;
import com.forward.hrms.attendance.entity.OvertimeRequest;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.OvertimeStatus;
import com.forward.hrms.attendance.enums.OvertimeType;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.service.ApprovalInboxQueries;
import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.attendance.repository.AttendancePunchRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.OvertimeRequestRepository;
import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OvertimeService {

    private static final List<OvertimeStatus> ACTIVE_STATUSES =
            List.of(OvertimeStatus.PENDING, OvertimeStatus.APPROVED);

    private final OvertimeRequestRepository overtimes;
    private final AttendancePunchRepository punches;
    private final AttendanceRecordRepository records;
    private final ApprovalService approvals;
    private final ApprovalInboxService inbox;
    private final LeaveDtoAssembler assembler;
    private final AttendanceRecomputeService recompute;
    private final EmployeeClient employees;
    private final CallerContext caller;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final AttendancePeriodLockService periodLocks;
    private final RequestCodeService requestCodes;
    private final BatchRunner batch;
    private final OnBehalfResolver onBehalf;
    private final com.forward.hrms.commons.cache.EmployeeDisplayCache displayCache;
    private final DepartmentScope departmentScope;
    private final InboxSearch inboxSearch;

    @Transactional
    public OvertimeRequestDto selfApply(long callerEmployeeId, CreateOvertimeRequest req) {
        OnBehalfResolver.Resolution resolution = onBehalf.resolve(callerEmployeeId, req.onBehalfOf(), OnBehalfResolver.TEAM_GEN_OVERTIME);
        long employeeId = resolution.targetEmployeeId();
        if (punches.findAllByEmployeeIdAndAttendanceDateOrderByPunchedAtAsc(employeeId, req.workDate()).isEmpty()) {
            throw new UnprocessableError(AttendanceErrorCode.OVERTIME_NO_PUNCH_FOR_DATE,
                    "no punch for that date");
        }
        OvertimeRequestDto dto = submitOne(employeeId, resolution.approvalAnchorEmployeeId(),
                req.workDate(), req.overtimeType(), req.factoryCode(), req.reason(), null);
        if (resolution.autoApproves()) {
            dto = autoApproveOnBehalf(dto.id(), resolution.mode());
        }
        return dto;
    }

    /**
     * Manager-on-behalf submissions skip the manager re-approval step:
     * the manager has already implicitly approved by submitting. HR
     * override clears every stage in the chain.
     */
    private OvertimeRequestDto autoApproveOnBehalf(long requestId, OnBehalfResolver.Mode mode) {
        CurrentUser actor = caller.requireUser();
        OvertimeRequest ot = require(requestId);
        String managerNote = mode == OnBehalfResolver.Mode.HR_OVERRIDE
                ? "submitted on behalf by HR (override)"
                : "submitted on behalf by manager";
        ApprovalService.Decision managerStage = approvals.closeCurrentStage(
                RequestType.OVERTIME.name(), requestId, StepStatus.APPROVED,
                actor, managerNote);
        outbox.publish(AttendanceEvents.TOPIC, "overtime:" + requestId,
                AttendanceEvents.OVERTIME_STAGE_APPROVED,
                new AttendanceEvents.OvertimeStageApproved(
                        requestId, ot.getEmployeeId(),
                        managerStage.step().getStageNo(), managerStage.step().getLabel(),
                        actor.userId(), assembler.nextStageEvent(managerStage.nextPending())));
        if (mode != OnBehalfResolver.Mode.HR_OVERRIDE) return assembler.overtimeDto(ot);

        if (managerStage.nextPending() != null) {
            approvals.closeCurrentStage(
                    RequestType.OVERTIME.name(), requestId, StepStatus.APPROVED,
                    actor, "submitted on behalf by HR (override)");
            ot.setStatus(OvertimeStatus.APPROVED);
            // Recompute the day's OT minute buckets — gates on APPROVED OT.
            recompute.recompute(ot.getEmployeeId(), ot.getAttendanceDate());
            outbox.publish(AttendanceEvents.TOPIC, "overtime:" + requestId,
                    AttendanceEvents.OVERTIME_APPROVED,
                    new AttendanceEvents.OvertimeApproved(requestId, ot.getEmployeeId(),
                            ot.getAttendanceDate(), actor.userId()));
            audit.emit("overtime_request", String.valueOf(requestId), "approved",
                    null, Map.of("workDate", ot.getAttendanceDate().toString()), true);
        }
        return assembler.overtimeDto(ot);
    }

    /**
     * §7.attendance #11/#12 — per-row partial. Each row runs in its own
     * {@code REQUIRES_NEW} transaction via {@link BatchRunner}, so a
     * single failure (department mismatch is now a skip, not a throw;
     * any other unexpected error rolls back just that row) doesn't
     * abort the batch.
     */
    public BulkOvertimeResponse bulk(BulkOvertimeRequest req) {
        CurrentUser actor = caller.requireUser();
        assertTypeFitsDay(req.workDate(), req.overtimeType());
        EmployeeClient.EmployeeView callerEmp = employees.findByUserId(actor.userId())
                .orElseThrow(() -> new ConflictError(AttendanceErrorCode.EMPLOYEE_NOT_FOUND,
                        "no employee linked to caller"));
        Long callerDept = callerEmp.departmentId();

        UUID batchId = UUID.randomUUID();
        List<OvertimeRequestDto> created = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();

        BatchOutcomes<Long, OvertimeRequestDto> outcomes = batch.runWithPartial(req.employeeIds(), employeeId -> {
            EmployeeClient.EmployeeView ev = employees.getByIdOptional(employeeId).orElse(null);
            if (ev == null) return null;
            // Department mismatch is a skip, not an abort — used to be a throw.
            if (callerDept != null && ev.departmentId() != null && !callerDept.equals(ev.departmentId())) return null;
            if (punches.findAllByEmployeeIdAndAttendanceDateOrderByPunchedAtAsc(employeeId, req.workDate()).isEmpty()) return null;
            if (overtimes.existsByEmployeeIdAndAttendanceDateAndOvertimeTypeAndStatusIn(
                    employeeId, req.workDate(), req.overtimeType(), ACTIVE_STATUSES)) return null;
            // Bulk OT is a manager acting on their own department, not an
            // on-behalf submission — the chain anchors on each target as before.
            return submitOne(employeeId, employeeId, req.workDate(), req.overtimeType(),
                    req.factoryCode(), req.reason(), batchId);
        });
        for (var s : outcomes.succeeded()) {
            if (s.result() == null) skipped.add(s.item());
            else created.add(s.result());
        }
        for (var f : outcomes.failed()) {
            log.warn("overtime bulk: employee {} failed code={} msg={}", f.item(), f.errorCode(), f.message(), f.cause());
            skipped.add(f.item());
        }
        return new BulkOvertimeResponse(batchId, created, skipped);
    }

    @Transactional
    public OvertimeRequestDto approve(long id, ApproveRequest body) {
        OvertimeRequest ot = require(id);
        periodLocks.assertOpen(ot.getAttendanceDate());
        approvals.approve(RequestType.OVERTIME.name(), id, body == null ? null : body.note());
        return assembler.overtimeDto(ot);
    }

    @Transactional
    public OvertimeRequestDto reject(long id, RejectRequest body) {
        OvertimeRequest ot = require(id);
        approvals.reject(RequestType.OVERTIME.name(), id, body.reason());
        return assembler.overtimeDto(ot);
    }

    /**
     * §7.attendance #13 — per-row partial via {@link BatchRunner}. A row
     * the caller can't act on (Forbidden) or one that throws any other
     * business error rolls back just that row's transaction; the rest
     * of the batch keeps going.
     */
    public List<OvertimeRequestDto> approveBatch(UUID batchId, ApproveRequest body) {
        List<OvertimeRequest> rows = overtimes.findAllByBulkBatchIdOrderByEmployeeIdAsc(batchId);
        if (rows.isEmpty()) {
            throw new NotFoundError(AttendanceErrorCode.OVERTIME_BATCH_NOT_FOUND, "batch not found");
        }
        BatchOutcomes<OvertimeRequest, OvertimeRequestDto> outcomes = batch.runWithPartial(rows, r -> approve(r.getId(), body));
        for (var f : outcomes.failed()) {
            log.warn("overtime approveBatch: ot {} failed code={} msg={}",
                    f.item().getId(), f.errorCode(), f.message());
        }
        return outcomes.succeeded().stream().map(BatchOutcomes.Success::result).toList();
    }

    public List<OvertimeRequestDto> rejectBatch(UUID batchId, RejectRequest body) {
        List<OvertimeRequest> rows = overtimes.findAllByBulkBatchIdOrderByEmployeeIdAsc(batchId);
        if (rows.isEmpty()) {
            throw new NotFoundError(AttendanceErrorCode.OVERTIME_BATCH_NOT_FOUND, "batch not found");
        }
        BatchOutcomes<OvertimeRequest, OvertimeRequestDto> outcomes = batch.runWithPartial(rows, r -> reject(r.getId(), body));
        for (var f : outcomes.failed()) {
            log.warn("overtime rejectBatch: ot {} failed code={} msg={}",
                    f.item().getId(), f.errorCode(), f.message());
        }
        return outcomes.succeeded().stream().map(BatchOutcomes.Success::result).toList();
    }

    @Transactional
    public OvertimeRequestDto cancel(long id) {
        OvertimeRequest ot = require(id);
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        if (me.id() != ot.getEmployeeId()) {
            throw new com.forward.hrms.commons.core.error.ForbiddenError(
                    AttendanceErrorCode.NOT_AUTHORIZED, "only the submitter can cancel");
        }
        boolean cancellable = ot.getStatus() == OvertimeStatus.PENDING
                || (ot.getStatus() == OvertimeStatus.APPROVED
                        && ot.getAttendanceDate().isAfter(java.time.LocalDate.now()));
        if (!cancellable) {
            throw new ConflictError(AttendanceErrorCode.OVERTIME_NOT_PENDING,
                    "overtime cannot be cancelled in its current state");
        }
        boolean wasApproved = ot.getStatus() == OvertimeStatus.APPROVED;
        approvals.cancelChain(RequestType.OVERTIME.name(), id);
        ot.setStatus(OvertimeStatus.CANCELLED);
        // Roll back the OT minute buckets if the request had been approved.
        if (wasApproved) recompute.recompute(ot.getEmployeeId(), ot.getAttendanceDate());
        outbox.publish(AttendanceEvents.TOPIC, "overtime:" + id,
                AttendanceEvents.OVERTIME_CANCELLED,
                new AttendanceEvents.OvertimeCancelled(id, ot.getEmployeeId(), ot.getAttendanceDate()));
        audit.emit("overtime_request", String.valueOf(id), "cancelled",
                null, Map.of("workDate", ot.getAttendanceDate().toString()), true);
        return assembler.overtimeDto(ot);
    }

    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<OvertimeRequestDto> searchForEmployee(
            long employeeId, OvertimeStatus status,
            java.time.LocalDate from, java.time.LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        var page = overtimes.search(employeeId, status, from, to, pageable);
        List<OvertimeRequestDto> content = page.getContent().stream()
                .map(assembler::overtimeDto).toList();
        return com.forward.hrms.commons.web.page.PageResponse.from(page, content);
    }

    /**
     * Overtime of the caller's direct reports, whoever submitted it — the
     * report, or the manager on their behalf. Empty page for a caller who
     * manages nobody. Optional {@code employeeId} narrows to one report
     * (anyone else yields an empty page). Rows carry the target's summary
     * from the display cache, like {@link #forDepartment}.
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeRow> searchForTeam(
            OvertimeStatus status, java.time.LocalDate from, java.time.LocalDate to,
            Long employeeId, org.springframework.data.domain.Pageable pageable) {
        List<Long> reportIds = employees.myDirectReportIds();
        if (employeeId != null) {
            reportIds = reportIds.contains(employeeId) ? List.of(employeeId) : List.of();
        }
        if (reportIds.isEmpty()) {
            return com.forward.hrms.commons.web.page.PageResponse.from(
                    org.springframework.data.domain.Page.empty(pageable), List.of());
        }
        return withEmployeeSummaries(
                overtimes.searchForEmployees(reportIds, status, from, to, pageable));
    }

    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<OvertimeRequestDto> pendingApprovals(
            java.time.LocalDate from, java.time.LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        return pendingApprovals(from, to, null, null, pageable);
    }

    /**
     * Inbox with optional filters: {@code q} per {@link InboxSearch}'s match
     * rules, {@code overtimeType} exact.
     */
    public com.forward.hrms.commons.web.page.PageResponse<OvertimeRequestDto> pendingApprovals(
            java.time.LocalDate from, java.time.LocalDate to, String q,
            com.forward.hrms.attendance.enums.OvertimeType overtimeType,
            org.springframework.data.domain.Pageable pageable) {
        CurrentUser actor = caller.requireUser();
        return ApprovalInboxQueries.findPending(
                inbox, RequestType.OVERTIME.name(), actor,
                overtimes::findAllById, rows -> filterInbox(rows, q, overtimeType),
                OvertimeRequest::getId,
                OvertimeRequest::getAttendanceDate, OvertimeRequest::getAttendanceDate,
                from, to, pageable, assembler::overtimeDto);
    }

    private List<OvertimeRequest> filterInbox(List<OvertimeRequest> rows, String q,
                                              com.forward.hrms.attendance.enums.OvertimeType overtimeType) {
        List<OvertimeRequest> typed = overtimeType == null ? rows
                : rows.stream().filter(r -> r.getOvertimeType() == overtimeType).toList();
        return inboxSearch.filter(typed, q,
                OvertimeRequest::getId, OvertimeRequest::getRequestCode, OvertimeRequest::getEmployeeId);
    }

    /** SPECIAL only on a Sunday / holiday; NORMAL and EXTRA only on a working day. */
    private void assertTypeFitsDay(java.time.LocalDate workDate, OvertimeType type) {
        boolean offDay = recompute.isSundayOrHoliday(workDate);
        if (offDay != (type == OvertimeType.SPECIAL)) {
            throw new UnprocessableError(AttendanceErrorCode.OVERTIME_TYPE_NOT_ALLOWED, offDay
                    ? workDate + " is a Sunday or holiday; only SPECIAL overtime applies"
                    : "SPECIAL overtime applies only on a Sunday or holiday");
        }
    }

    /**
     * @param employeeId       the employee the request is for
     * @param anchorEmployeeId the employee whose manager takes the MANAGER stage;
     *                         differs from {@code employeeId} only for
     *                         TEAM_ON_BEHALF submissions
     */
    private OvertimeRequestDto submitOne(long employeeId, long anchorEmployeeId,
                                         java.time.LocalDate workDate,
                                         com.forward.hrms.attendance.enums.OvertimeType type,
                                         com.forward.hrms.attendance.enums.FactoryCode factoryCode,
                                         String reason, UUID batchId) {
        periodLocks.assertOpen(workDate);
        assertTypeFitsDay(workDate, type);
        // One active request per type per day: NORMAL and EXTRA on the same
        // day is how a long evening is split (see recomputeOvertime).
        if (overtimes.existsByEmployeeIdAndAttendanceDateAndOvertimeTypeAndStatusIn(
                employeeId, workDate, type, ACTIVE_STATUSES)) {
            throw new ConflictError(AttendanceErrorCode.OVERTIME_DUPLICATE,
                    "an active " + type + " overtime request already exists for that day");
        }
        OvertimeRequest ot = new OvertimeRequest();
        ot.setRequestCode(requestCodes.allocate("OT"));
        ot.setEmployeeId(employeeId);
        ot.setAttendanceDate(workDate);
        ot.setOvertimeType(type);
        ot.setFactoryCode(factoryCode);
        ot.setReason(reason);
        ot.setBulkBatchId(batchId);
        ot.setStatus(OvertimeStatus.PENDING);
        overtimes.save(ot);
        List<ApprovalStep> chain = approvals.openChain(RequestType.OVERTIME.name(), ot.getId(), anchorEmployeeId);
        outbox.publish(AttendanceEvents.TOPIC, "overtime:" + ot.getId(),
                AttendanceEvents.OVERTIME_REQUESTED,
                new AttendanceEvents.OvertimeRequested(
                        ot.getId(), employeeId, workDate, batchId,
                        assembler.stageSnapshots(chain)));
        return assembler.overtimeDto(ot);
    }

    /** One request — backs GET /overtime/{id} (and the BFF Activity probe). */
    @Transactional(readOnly = true)
    public OvertimeRequestDto get(long id) {
        return assembler.overtimeDto(require(id));
    }

    private OvertimeRequest require(long id) {
        return overtimes.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.OVERTIME_NOT_FOUND, "overtime not found"));
    }

    /**
     * Overtime requests the caller filed on someone else's behalf (a manager for a
     * direct report, a team-request generator for a colleague). Their own
     * self-submissions are excluded. Mirrors
     * {@code RegularizationService#filedOnBehalf}: matched on
     * {@code created_by}, each row paired with the target's summary from the
     * shared display cache (the callers hold no {@code employee.read}).
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeRow> filedOnBehalf(
            com.forward.hrms.attendance.enums.OvertimeStatus status,
            java.time.LocalDate filedFrom, java.time.LocalDate filedTo,
            org.springframework.data.domain.Pageable pageable) {
        var actor = caller.requireUser();
        var me = caller.requireEmployee();
        var page = overtimes.searchFiledOnBehalf(actor.username(), me.id(), status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        return withEmployeeSummaries(page);
    }

    /**
     * Every overtime request in the caller's department, whoever filed it —
     * for a team-request generator, or for a manager (MANAGER role, or anyone
     * with direct reports) looking past their own team. Optional {@code employeeId} narrows to one colleague (an id outside the
     * department yields an empty page). Rows carry the target's summary from
     * the display cache, like {@link #filedOnBehalf}.
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeRow> forDepartment(
            com.forward.hrms.attendance.enums.OvertimeStatus status,
            java.time.LocalDate filedFrom, java.time.LocalDate filedTo, Long employeeId,
            org.springframework.data.domain.Pageable pageable) {
        CurrentUser user = caller.requireUser();
        if (!user.hasPermission("team.request.generation.overtime")
                && !user.hasRole("MANAGER")
                && employees.myDirectReportIds().isEmpty()) {
            throw new com.forward.hrms.commons.core.error.ForbiddenError(
                    "Only managers and team-request generators can list their department's overtime");
        }
        java.util.List<Long> ids = departmentScope.employeeIds(employeeId);
        if (ids.isEmpty()) {
            return com.forward.hrms.commons.web.page.PageResponse.from(
                    org.springframework.data.domain.Page.empty(pageable), java.util.List.of());
        }
        return withEmployeeSummaries(overtimes.searchDepartment(ids, status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable));
    }

    /**
     * Pairs each overtime request with the target's summary from the display
     * cache and the work date's punch-derived minutes (one batch read over
     * the page's employees and date span, like {@code /overtime/me/with-records}).
     */
    private com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeRow> withEmployeeSummaries(
            org.springframework.data.domain.Page<com.forward.hrms.attendance.entity.OvertimeRequest> page) {
        var employeeIds = page.getContent().stream()
                .map(com.forward.hrms.attendance.entity.OvertimeRequest::getEmployeeId).distinct().toList();
        var displays = displayCache.getMany(employeeIds);
        var minutes = minutesByEmployeeDate(employeeIds,
                page.getContent().stream().map(OvertimeRequest::getAttendanceDate).toList());
        return com.forward.hrms.commons.web.page.PageResponse.from(page,
                page.getContent().stream()
                        .map(r -> {
                            var d = displays.get(r.getEmployeeId());
                            return new com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeRow(
                                    assembler.overtimeDto(r),
                                    d == null ? null
                                            : com.forward.hrms.commons.cache.EmployeeSummaryMapper.toSummary(d),
                                    minutes.getOrDefault(minutesKey(r.getEmployeeId(), r.getAttendanceDate()),
                                            com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeMinutes.NONE));
                        })
                        .toList());
    }

    /**
     * The attendance minutes behind overtime rows, keyed by
     * {@link #minutesKey}: one batch read over the employees and the span of
     * {@code dates}. Rows with no attendance record are absent — callers fall
     * back to {@code OvertimeMinutes.NONE}.
     */
    @Transactional(readOnly = true)
    public Map<String, com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeMinutes> minutesByEmployeeDate(
            java.util.Collection<Long> employeeIds, java.util.Collection<java.time.LocalDate> dates) {
        if (employeeIds.isEmpty() || dates.isEmpty()) return Map.of();
        java.time.LocalDate from = java.util.Collections.min(dates);
        java.time.LocalDate to = java.util.Collections.max(dates);
        Map<String, com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeMinutes> out = new java.util.HashMap<>();
        for (var rec : records.findAllByEmployeeIdInAndAttendanceDateBetweenOrderByEmployeeIdAscAttendanceDateAsc(
                employeeIds, from, to)) {
            out.put(minutesKey(rec.getEmployeeId(), rec.getAttendanceDate()),
                    new com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeMinutes(
                            rec.getOtNormalMinutes(), rec.getOtExtraMinutes(),
                            rec.getOtSpecialMinutes(), rec.getWorkedMinutes(),
                            recompute.overtimeMinutes(rec, rec.getAttendanceDate())));
        }
        return out;
    }

    public static String minutesKey(Long employeeId, java.time.LocalDate date) {
        return employeeId + "|" + date;
    }
}

package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.attendance.entity.LeaveRequest;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.entity.OvertimeRequest;
import com.forward.hrms.attendance.entity.RegularizationRequest;
import com.forward.hrms.attendance.entity.ShortLeaveRequest;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.attendance.repository.LeaveRequestRepository;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.attendance.repository.OvertimeRequestRepository;
import com.forward.hrms.attendance.repository.RegularizationRequestRepository;
import com.forward.hrms.attendance.repository.ShortLeaveRequestRepository;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.repository.ApprovalStepRepository;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.web.page.PageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * §12.2 — "approvals I have decided" history view. Returns the rows the
 * current caller closed (APPROVED or REJECTED) at any approval stage,
 * unified across leave / OT / regularization / short leave. Backed by
 * {@code idx_steps_by_decider}.
 *
 * <p>Single-service composite — every fan-out hits attendance — so it
 * lives here, not in BFF (per the doctrine in CLAUDE.md). Employee
 * enrichment is one round-trip per distinct id; small at FS scale and
 * collapses to nothing once §11.3 lands denormalized columns.
 */
@Service
@RequiredArgsConstructor
public class ApprovalHistoryService {

    /**
     * Window guard mirrors §6.7's audit search: a "show me everything I
     * ever approved" query is almost always either a UI bug or a
     * misunderstanding. Forces the caller to chunk by year.
     */
    private static final int MAX_WINDOW_DAYS = 366;

    private final ApprovalStepRepository steps;
    private final LeaveRequestRepository leaves;
    private final LeaveTypeRepository leaveTypes;
    private final OvertimeRequestRepository overtimes;
    private final RegularizationRequestRepository regularizations;
    private final ShortLeaveRequestRepository shortLeaves;
    private final EmployeeClient employeeClient;
    private final CallerContext caller;

    @Transactional(readOnly = true)
    public PageResponse<ApprovalDecisionRow> history(
            LocalDate from, LocalDate to,
            RequestType type, StepStatus decision,
            Pageable pageable) {
        if (from == null || to == null) {
            throw new UnprocessableError(AttendanceErrorCode.REQUEST_DATE_OUT_OF_RANGE,
                    "from and to are required");
        }
        if (to.isBefore(from)) {
            throw new UnprocessableError(AttendanceErrorCode.REQUEST_DATE_OUT_OF_RANGE,
                    "to must be on or after from");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_WINDOW_DAYS) {
            throw new UnprocessableError(AttendanceErrorCode.REQUEST_DATE_OUT_OF_RANGE,
                    "search window must be at most " + MAX_WINDOW_DAYS + " days");
        }
        if (decision != null && decision != StepStatus.APPROVED && decision != StepStatus.REJECTED) {
            throw new UnprocessableError(AttendanceErrorCode.REQUEST_DATE_OUT_OF_RANGE,
                    "decision must be APPROVED or REJECTED");
        }

        CurrentUser actor = caller.requireUser();

        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        Page<ApprovalStep> page = steps.findDecisionsByDecider(
                actor.userId(), fromTs, toExclusive, type == null ? null : type.name(), decision, pageable);
        if (page.isEmpty()) {
            return PageResponse.from(page, List.of());
        }

        // Bucket request ids by type, bulk-load in one round trip per type.
        Set<Long> leaveIds = new HashSet<>();
        Set<Long> otIds = new HashSet<>();
        Set<Long> regIds = new HashSet<>();
        Set<Long> shortLeaveIds = new HashSet<>();
        for (ApprovalStep s : page.getContent()) {
            switch (s.getRequestType()) {
                case "LEAVE" -> leaveIds.add(s.getRequestId());
                case "OVERTIME" -> otIds.add(s.getRequestId());
                case "REGULARIZATION" -> regIds.add(s.getRequestId());
                case "SHORT_LEAVE" -> shortLeaveIds.add(s.getRequestId());
                default -> { /* unreachable — RequestType is exhaustive */ }
            }
        }
        Map<Long, LeaveRequest> leaveMap = indexById(leaves.findAllById(leaveIds), LeaveRequest::getId);
        Map<Long, OvertimeRequest> otMap = indexById(overtimes.findAllById(otIds), OvertimeRequest::getId);
        Map<Long, RegularizationRequest> regMap = indexById(regularizations.findAllById(regIds), RegularizationRequest::getId);
        Map<Long, ShortLeaveRequest> shortLeaveMap = indexById(shortLeaves.findAllById(shortLeaveIds), ShortLeaveRequest::getId);

        Set<Long> leaveTypeIds = new HashSet<>();
        for (LeaveRequest lr : leaveMap.values()) leaveTypeIds.add(lr.getLeaveTypeId());
        Map<Long, LeaveType> leaveTypeMap = indexById(leaveTypes.findAllById(leaveTypeIds), LeaveType::getId);

        Set<Long> employeeIds = new HashSet<>();
        for (LeaveRequest lr : leaveMap.values()) employeeIds.add(lr.getEmployeeId());
        for (OvertimeRequest ot : otMap.values()) employeeIds.add(ot.getEmployeeId());
        for (RegularizationRequest r : regMap.values()) employeeIds.add(r.getEmployeeId());
        for (ShortLeaveRequest sl : shortLeaveMap.values()) employeeIds.add(sl.getEmployeeId());
        Map<Long, EmployeeClient.EmployeeView> empMap = bulkLoadEmployees(employeeIds);

        List<ApprovalDecisionRow> rows = page.getContent().stream()
                .map(s -> toRow(s, leaveMap, otMap, regMap, shortLeaveMap, leaveTypeMap, empMap))
                .filter(java.util.Objects::nonNull)
                .toList();
        return PageResponse.from(page, rows);
    }

    private ApprovalDecisionRow toRow(ApprovalStep s,
                                      Map<Long, LeaveRequest> leaveMap,
                                      Map<Long, OvertimeRequest> otMap,
                                      Map<Long, RegularizationRequest> regMap,
                                      Map<Long, ShortLeaveRequest> shortLeaveMap,
                                      Map<Long, LeaveType> leaveTypeMap,
                                      Map<Long, EmployeeClient.EmployeeView> empMap) {
        return switch (s.getRequestType()) {
            case "LEAVE" -> {
                LeaveRequest lr = leaveMap.get(s.getRequestId());
                if (lr == null) yield null;
                EmployeeClient.EmployeeView emp = empMap.get(lr.getEmployeeId());
                LeaveType lt = leaveTypeMap.get(lr.getLeaveTypeId());
                yield new ApprovalDecisionRow(
                        "LEAVE",
                        lr.getId(),
                        lr.getRequestCode(),
                        lr.getEmployeeId(),
                        emp == null ? null : emp.employeeCode(),
                        emp == null ? null : emp.fullName(),
                        s.getDecidedAt(),
                        lr.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        lr.getFromDate(),
                        lr.getToDate(),
                        lt == null ? null : lt.getCode(),
                        lr.getDays(),
                        lr.getReason());
            }
            case "OVERTIME" -> {
                OvertimeRequest ot = otMap.get(s.getRequestId());
                if (ot == null) yield null;
                EmployeeClient.EmployeeView emp = empMap.get(ot.getEmployeeId());
                yield new ApprovalDecisionRow(
                        "OVERTIME",
                        ot.getId(),
                        ot.getRequestCode(),
                        ot.getEmployeeId(),
                        emp == null ? null : emp.employeeCode(),
                        emp == null ? null : emp.fullName(),
                        s.getDecidedAt(),
                        ot.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        ot.getAttendanceDate(),
                        null,
                        null,
                        null,
                        ot.getReason());
            }
            case "REGULARIZATION" -> {
                RegularizationRequest r = regMap.get(s.getRequestId());
                if (r == null) yield null;
                EmployeeClient.EmployeeView emp = empMap.get(r.getEmployeeId());
                yield new ApprovalDecisionRow(
                        "REGULARIZATION",
                        r.getId(),
                        r.getRequestCode(),
                        r.getEmployeeId(),
                        emp == null ? null : emp.employeeCode(),
                        emp == null ? null : emp.fullName(),
                        s.getDecidedAt(),
                        r.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        r.getAttendanceDate(),
                        null,
                        null,
                        null,
                        r.getReason());
            }
            case "SHORT_LEAVE" -> {
                ShortLeaveRequest sl = shortLeaveMap.get(s.getRequestId());
                if (sl == null) yield null;
                EmployeeClient.EmployeeView emp = empMap.get(sl.getEmployeeId());
                yield new ApprovalDecisionRow(
                        "SHORT_LEAVE",
                        sl.getId(),
                        sl.getRequestCode(),
                        sl.getEmployeeId(),
                        emp == null ? null : emp.employeeCode(),
                        emp == null ? null : emp.fullName(),
                        s.getDecidedAt(),
                        sl.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        sl.getLeaveDate(),
                        null,
                        null,
                        null,
                        sl.getReason());
            }
            default -> null;
        };
    }

    private Map<Long, EmployeeClient.EmployeeView> bulkLoadEmployees(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, EmployeeClient.EmployeeView> out = new HashMap<>(ids.size());
        for (EmployeeClient.EmployeeView v : employeeClient.getByIds(ids)) {
            out.put(v.id(), v);
        }
        return out;
    }

    private static <T> Map<Long, T> indexById(Iterable<T> rows, java.util.function.ToLongFunction<T> idOf) {
        Map<Long, T> out = new HashMap<>();
        for (T row : rows) out.put(idOf.applyAsLong(row), row);
        return out;
    }
}

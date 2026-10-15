package com.forward.hrms.payroll.service;

import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.repository.ApprovalStepRepository;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvanceBatch;
import com.forward.hrms.payroll.enums.RequestType;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceBatchRepository;

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
 * "Approvals I have decided" history, mirroring attendance's
 * {@code ApprovalHistoryService} — unified across the two advance types
 * (PERMANENT_ADVANCE, SHORT_TERM_ADVANCE). Short-term advance batches span
 * N employees (no single subject employee), so their rows carry
 * {@code employeeId = 0} / null employeeCode/fullName — same convention
 * {@code ShortTermAdvanceService} already uses for the MANAGER-stage
 * subject id.
 */
@Service
@RequiredArgsConstructor
public class ApprovalHistoryService {

    private static final int MAX_WINDOW_DAYS = 366;

    private final ApprovalStepRepository steps;
    private final PermanentAdvanceRepository permanentAdvances;
    private final ShortTermAdvanceBatchRepository shortTermBatches;
    private final EmployeeClient employeeClient;

    @Transactional(readOnly = true)
    public PageResponse<ApprovalDecisionRow> history(
            LocalDate from, LocalDate to,
            RequestType type, StepStatus decision,
            Pageable pageable) {
        if (from == null || to == null) {
            throw new UnprocessableError(PayrollErrorCode.APPROVAL_HISTORY_DATE_RANGE_INVALID,
                    "from and to are required");
        }
        if (to.isBefore(from)) {
            throw new UnprocessableError(PayrollErrorCode.APPROVAL_HISTORY_DATE_RANGE_INVALID,
                    "to must be on or after from");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_WINDOW_DAYS) {
            throw new UnprocessableError(PayrollErrorCode.APPROVAL_HISTORY_DATE_RANGE_INVALID,
                    "search window must be at most " + MAX_WINDOW_DAYS + " days");
        }

        CurrentUser actor = currentUser();
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        Page<ApprovalStep> page = steps.findDecisionsByDecider(
                actor.userId(), fromTs, toExclusive, type == null ? null : type.name(), decision, pageable);
        if (page.isEmpty()) {
            return PageResponse.from(page, List.of());
        }

        Set<Long> permanentIds = new HashSet<>();
        Set<Long> shortTermIds = new HashSet<>();
        for (ApprovalStep s : page.getContent()) {
            switch (s.getRequestType()) {
                case "PERMANENT_ADVANCE" -> permanentIds.add(s.getRequestId());
                case "SHORT_TERM_ADVANCE" -> shortTermIds.add(s.getRequestId());
                default -> { /* unreachable — this service only decides these two types */ }
            }
        }
        Map<Long, PermanentAdvance> permanentMap = indexById(permanentAdvances.findAllById(permanentIds), PermanentAdvance::getId);
        Map<Long, ShortTermAdvanceBatch> shortTermMap = indexById(shortTermBatches.findAllById(shortTermIds), ShortTermAdvanceBatch::getId);

        Set<Long> employeeIds = new HashSet<>();
        for (PermanentAdvance a : permanentMap.values()) employeeIds.add(a.getEmployeeId());
        Map<Long, EmployeeClient.EmployeeBrief> empMap = employeeIds.isEmpty()
                ? Map.of() : employeeClient.getMany(employeeIds);

        List<ApprovalDecisionRow> rows = page.getContent().stream()
                .map(s -> toRow(s, permanentMap, shortTermMap, empMap))
                .filter(java.util.Objects::nonNull)
                .toList();
        return PageResponse.from(page, rows);
    }

    private ApprovalDecisionRow toRow(ApprovalStep s,
                                      Map<Long, PermanentAdvance> permanentMap,
                                      Map<Long, ShortTermAdvanceBatch> shortTermMap,
                                      Map<Long, EmployeeClient.EmployeeBrief> empMap) {
        return switch (s.getRequestType()) {
            case "PERMANENT_ADVANCE" -> {
                PermanentAdvance a = permanentMap.get(s.getRequestId());
                if (a == null) yield null;
                EmployeeClient.EmployeeBrief emp = empMap.get(a.getEmployeeId());
                yield new ApprovalDecisionRow(
                        "PERMANENT_ADVANCE",
                        a.getId(),
                        a.getRequestCode(),
                        a.getEmployeeId(),
                        emp == null ? null : emp.employeeCode(),
                        emp == null ? null : emp.fullName(),
                        s.getDecidedAt(),
                        a.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        null,
                        null,
                        null,
                        null,
                        a.getDescription());
            }
            case "SHORT_TERM_ADVANCE" -> {
                ShortTermAdvanceBatch b = shortTermMap.get(s.getRequestId());
                if (b == null) yield null;
                yield new ApprovalDecisionRow(
                        "SHORT_TERM_ADVANCE",
                        b.getId(),
                        b.getRequestCode(),
                        0,
                        null,
                        null,
                        s.getDecidedAt(),
                        b.getCreatedAt(),
                        s.getStatus().name(),
                        s.getStageNo(),
                        s.getLabel(),
                        s.getNote(),
                        null,
                        null,
                        null,
                        null,
                        b.getNotes());
            }
            default -> null;
        };
    }

    private static <T> Map<Long, T> indexById(Iterable<T> rows, java.util.function.ToLongFunction<T> idOf) {
        Map<Long, T> out = new HashMap<>();
        for (T row : rows) out.put(idOf.applyAsLong(row), row);
        return out;
    }

    private CurrentUser currentUser() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
    }
}

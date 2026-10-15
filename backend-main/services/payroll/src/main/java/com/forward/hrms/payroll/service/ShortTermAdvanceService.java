package com.forward.hrms.payroll.service;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.AdvanceDecisionRequest;
import com.forward.hrms.payroll.dto.request.AdvanceRejectRequest;
import com.forward.hrms.payroll.dto.request.CreateShortTermAdvanceBatchRequest;
import com.forward.hrms.payroll.dto.request.ShortTermAdvanceLineInput;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceBatchDto;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceInboxRow;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceLineDto;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvanceBatch;
import com.forward.hrms.payroll.enums.AdvanceApprovalStage;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.ShortTermAdvanceBatchRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShortTermAdvanceService {

    private static final String REQUEST_TYPE = "SHORT_TERM_ADVANCE";

    private final ShortTermAdvanceBatchRepository batches;
    private final ShortTermAdvanceRepository lines;
    private final PayrollRequestCodeService requestCodes;
    private final AuditEmitter audit;
    private final com.forward.hrms.commons.batch.BatchRunner batch;
    private final ApprovalService approvals;
    private final ApprovalInboxService approvalInbox;
    private final EmployeeClient employeeClient;
    private final com.forward.hrms.commons.cache.EmployeeDisplayCache displayCache;

    @Transactional(readOnly = true)
    public PageResponse<ShortTermAdvanceBatchDto> search(AdvanceStatus status, Integer year, Integer month,
                                                         Long departmentId, Pageable pageable) {
        Page<ShortTermAdvanceBatch> page = batches.search(status, year, month, departmentId, pageable);
        List<ShortTermAdvanceBatchDto> content = page.getContent().stream()
                .map(b -> dtoOf(b, null))
                .toList();
        return PageResponse.from(page, content);
    }

    @Transactional(readOnly = true)
    public ShortTermAdvanceBatchDto get(long id) {
        ShortTermAdvanceBatch b = require(id);
        return dtoOf(b, fetchLines(id));
    }

    @Transactional(readOnly = true)
    public List<ShortTermAdvanceLineDto> listLines(long batchId) {
        require(batchId);
        return fetchLines(batchId);
    }

    /**
     * Self-serve per-employee batch list. A batch can span many employees'
     * lines (HR's bulk-apply flow), so "batches for employee X" is a
     * batch ⋈ line join, not a direct batch field. When the caller got in
     * via the self-match path (not payroll.read), the DTO is rescoped to
     * just the caller's own line — otherwise a batch-mate's line, amount,
     * and headcount would leak through the ESS "my requests" view.
     */
    @Transactional(readOnly = true)
    public PageResponse<ShortTermAdvanceBatchDto> listForEmployee(long employeeId, AdvanceStatus status, Pageable pageable) {
        boolean selfServe = checkSelfOrPayrollRead(employeeId);
        Page<ShortTermAdvanceBatch> page = batches.searchForEmployee(employeeId, status, pageable);
        List<ShortTermAdvanceBatchDto> content = page.getContent().stream()
                .map(b -> selfServe ? dtoForEmployeeScoped(b, employeeId) : dtoOf(b, fetchLines(b.getId())))
                .toList();
        return PageResponse.from(page, content);
    }



    /**
     * Pending-approval inbox for the caller — merges both review stages into
     * one list (not separate buckets), backed by the real commons-approval
     * chain via {@link ApprovalInboxService}. Mirrors
     * {@code PermanentAdvanceService.pendingApprovals}.
     */
    @Transactional(readOnly = true)
    public PageResponse<ShortTermAdvanceInboxRow> pendingApprovals(String q, Pageable pageable) {
        CurrentUser caller = currentUser();
        List<Long> ids = approvalInbox.requestIdsInInbox(REQUEST_TYPE, caller);
        Map<Long, ShortTermAdvanceBatch> byId = new LinkedHashMap<>();
        for (ShortTermAdvanceBatch b : batches.findAllById(ids)) byId.put(b.getId(), b);
        List<ShortTermAdvanceBatch> rows = ids.stream().map(byId::get).filter(Objects::nonNull)
                .filter(inboxMatcher(q))
                .sorted((x, y) -> Long.compare(y.getId(), x.getId())).toList();
        return InboxPage.of(rows, pageable, b -> new ShortTermAdvanceInboxRow(dtoOf(b, null), stageOf(b)));
    }

    /**
     * Inbox {@code q}: matches the batch id exactly (numeric {@code q} only)
     * or a case-insensitive substring of its request code. Null/blank
     * matches everything.
     */
    private static java.util.function.Predicate<ShortTermAdvanceBatch> inboxMatcher(String q) {
        if (q == null || q.isBlank()) return b -> true;
        String needle = q.trim().toLowerCase(java.util.Locale.ROOT);
        Long idNeedle = needle.chars().allMatch(Character::isDigit) && needle.length() <= 18
                ? Long.parseLong(needle) : null;
        return b -> (idNeedle != null && idNeedle.equals(b.getId()))
                || (b.getRequestCode() != null
                    && b.getRequestCode().toLowerCase(java.util.Locale.ROOT).contains(needle));
    }

    /**
     * §7.payroll #40 — orchestrator (no outer @Transactional) that
     * persists the batch row in its own tx, then per-line REQUIRES_NEW
     * via {@link com.forward.hrms.commons.batch.BatchRunner}, then
     * finalizes totals based on actual successful inserts. A failing
     * line surfaces as a per-row entry in the audit row's {@code failed}
     * list rather than rolling back the whole batch.
     */
    public ShortTermAdvanceBatchDto create(CreateShortTermAdvanceBatchRequest req) {
        boolean managerOnBehalf = checkCreateAccess(req.lines());
        // §7.payroll #41 — short-term advances are deducted from a *future*
        // payroll run. A target period at or before the current month either
        // already has a posted run (no place to deduct) or is the current
        // month (HR is racing the run). Refuse with a clear code.
        java.time.YearMonth target = java.time.YearMonth.of(req.targetYear(), req.targetMonth());
        java.time.YearMonth nextMonth = java.time.YearMonth.now().plusMonths(1);
        if (target.isBefore(nextMonth)) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    PayrollErrorCode.SHORT_TERM_BATCH_PERIOD_INVALID,
                    "targetYear/targetMonth must be next month or later (got " + target + ")");
        }
        Set<Long> seen = new HashSet<>();
        for (ShortTermAdvanceLineInput line : req.lines()) {
            if (!seen.add(line.employeeId())) {
                throw new ConflictError(PayrollErrorCode.SHORT_TERM_BATCH_DUPLICATE_EMPLOYEE,
                        "duplicate employee in batch: " + line.employeeId());
            }
        }
        requireNoActiveAdvanceInMonth(seen, req.targetYear(), req.targetMonth());

        // Step 1 — persist the batch in its own tx so per-line
        // REQUIRES_NEW txs (different connection) can FK to it.
        long batchId = batch.runInNewTx(() -> {
            ShortTermAdvanceBatch b = new ShortTermAdvanceBatch();
            b.setRequestCode(requestCodes.allocate("STA"));
            b.setDepartmentId(req.departmentId());
            b.setSectionId(req.sectionId());
            b.setTargetYear(req.targetYear());
            b.setTargetMonth(req.targetMonth());
            b.setNotes(req.notes());
            b.setStatus(AdvanceStatus.PENDING);
            b.setTotalAmount(java.math.BigDecimal.ZERO);
            b.setTotalEmployees(0);
            batches.save(b);
            // A batch spans N employees — no single employee to resolve the
            // MANAGER stage against, so it's the caller's (department head's)
            // own manager, not any line's employee.
            Long callerEmployeeId = employeeClient.meEmployeeIdOrNull();
            approvals.openChain(REQUEST_TYPE, b.getId(), callerEmployeeId == null ? 0 : callerEmployeeId);
            return b.getId();
        });

        // Step 2 — per-line REQUIRES_NEW. Outcomes carry per-row
        // success/failure with the original input as the item.
        com.forward.hrms.commons.batch.BatchOutcomes<ShortTermAdvanceLineInput, Void> outcomes =
                batch.runWithPartialVoid(req.lines(), in -> {
                    ShortTermAdvance s = new ShortTermAdvance();
                    s.setBatchId(batchId);
                    s.setEmployeeId(in.employeeId());
                    s.setAmount(in.amount());
                    s.setTargetYear(req.targetYear());
                    s.setTargetMonth(req.targetMonth());
                    s.setStatus(ShortTermAdvanceStatus.PENDING);
                    lines.save(s);
                });

        // Step 3 — finalize totals + audit in a closing tx.
        return batch.runInNewTx(() -> {
            ShortTermAdvanceBatch b = require(batchId);
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (var ok : outcomes.succeeded()) {
                total = total.add(ok.item().amount());
            }
            b.setTotalAmount(total);
            b.setTotalEmployees(outcomes.succeededCount());

            Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("total", total);
            auditAfter.put("employees", outcomes.succeededCount());
            if (!outcomes.failed().isEmpty()) {
                auditAfter.put("failedCount", outcomes.failedCount());
                List<Map<String, Object>> sample = new java.util.ArrayList<>();
                int max = Math.min(20, outcomes.failed().size());
                for (int i = 0; i < max; i++) {
                    var f = outcomes.failed().get(i);
                    sample.add(Map.of("employeeId", f.item().employeeId(),
                            "errorCode", f.errorCode(),
                            "message", f.message()));
                }
                auditAfter.put("failed", sample);
            }
            audit.emit("short_term_advance_batch", String.valueOf(b.getId()), "created",
                    null, auditAfter, true);
            if (managerOnBehalf) autoApproveManagerStage(b);
            return dtoOf(b, fetchLines(b.getId()));
        });
    }

    /**
     * One short-term advance per employee per target month. A line still
     * pending approval, approved, or already deducted blocks a new one; a
     * REJECTED line (batch rejected or cancelled) does not. Refuses the whole
     * request, naming every blocked employee, rather than failing those lines
     * one by one — a self-apply would otherwise leave an empty batch behind.
     */
    private void requireNoActiveAdvanceInMonth(Set<Long> employeeIds, int year, int month) {
        List<ShortTermAdvance> active = lines.findActiveForEmployeesInMonth(employeeIds, year, month);
        if (active.isEmpty()) return;
        Map<Long, EmployeeDisplay> people = displayCache.getMany(
                active.stream().map(ShortTermAdvance::getEmployeeId).distinct().toList());
        String blocked = active.stream().map(s -> {
            EmployeeDisplay p = people.get(s.getEmployeeId());
            String who = p == null ? "employee " + s.getEmployeeId() : p.employeeCode() + " (" + p.fullName() + ")";
            String state = s.getStatus() == ShortTermAdvanceStatus.PENDING ? "pending approval" : "already approved";
            return who + " — " + state + " (batch " + s.getBatchId() + ")";
        }).collect(java.util.stream.Collectors.joining("; "));
        throw new ConflictError(PayrollErrorCode.SHORT_TERM_ADVANCE_ALREADY_APPLIED,
                "short-term advance already applied for " + java.time.YearMonth.of(year, month) + ": " + blocked);
    }

    /**
     * A manager filing for their own direct reports has approved by filing,
     * so the Manager stage closes here and the batch moves on to HR — same
     * rule as attendance's MANAGER_ON_BEHALF. {@code closeCurrentStage}
     * bypasses {@link com.forward.hrms.payroll.approval.ShortTermAdvanceApprovalOutcomeHandler},
     * so the status flip and audit row it would write are applied inline.
     */
    private void autoApproveManagerStage(ShortTermAdvanceBatch b) {
        if (!approvals.isAtResolverKind(REQUEST_TYPE, b.getId(), ResolverKind.MANAGER)) return;
        CurrentUser actor = currentUser();
        approvals.closeCurrentStage(REQUEST_TYPE, b.getId(), StepStatus.APPROVED, actor,
                "submitted on behalf by manager");
        b.setStatus(AdvanceStatus.MANAGER_APPROVED);
        audit.emit("short_term_advance_batch", String.valueOf(b.getId()), "manager_approved",
                null, Map.of("by", actor.userId(), "onBehalf", true), true);
    }

    @Transactional
    public ShortTermAdvanceBatchDto managerApprove(long id, AdvanceDecisionRequest body) {
        ShortTermAdvanceBatch b = require(id);
        requireStatus(b, AdvanceStatus.PENDING);
        approvals.approve(REQUEST_TYPE, id, body == null ? null : body.note());
        return dtoOf(require(id), fetchLines(id));
    }

    @Transactional
    public ShortTermAdvanceBatchDto hrApprove(long id, AdvanceDecisionRequest body) {
        ShortTermAdvanceBatch b = require(id);
        requireStatus(b, AdvanceStatus.MANAGER_APPROVED);
        approvals.approve(REQUEST_TYPE, id, body == null ? null : body.note());
        return dtoOf(require(id), fetchLines(id));
    }

    @Transactional
    public ShortTermAdvanceBatchDto accountsApprove(long id, AdvanceDecisionRequest body) {
        ShortTermAdvanceBatch b = require(id);
        requireStatus(b, AdvanceStatus.HR_APPROVED);
        approvals.approve(REQUEST_TYPE, id, body == null ? null : body.note());
        return dtoOf(require(id), fetchLines(id));
    }

    @Transactional
    public ShortTermAdvanceBatchDto reject(long id, AdvanceRejectRequest body) {
        ShortTermAdvanceBatch b = require(id);
        if (b.getStatus() != AdvanceStatus.PENDING && b.getStatus() != AdvanceStatus.MANAGER_APPROVED
                && b.getStatus() != AdvanceStatus.HR_APPROVED) {
            throw new ConflictError(PayrollErrorCode.SHORT_TERM_BATCH_INVALID_STATE,
                    "batch can only be rejected before disbursement");
        }
        approvals.reject(REQUEST_TYPE, id, body.reason());
        return dtoOf(require(id), fetchLines(id));
    }

    @Transactional
    public ShortTermAdvanceBatchDto cancel(long id) {
        ShortTermAdvanceBatch b = require(id);
        if (b.getStatus() == AdvanceStatus.REJECTED || b.getStatus() == AdvanceStatus.CANCELLED) {
            throw new ConflictError(PayrollErrorCode.SHORT_TERM_BATCH_INVALID_STATE,
                    "batch is no longer cancellable");
        }
        approvals.cancelChain(REQUEST_TYPE, id);
        b.setStatus(AdvanceStatus.CANCELLED);
        for (ShortTermAdvance row : lines.findAllByBatchIdOrderByEmployeeIdAsc(id)) {
            // Lines already recovered by a final settlement keep their state.
            if (row.getFinalSettlementId() == null) {
                row.setStatus(ShortTermAdvanceStatus.CANCELLED);
            }
        }
        audit.emit("short_term_advance_batch", String.valueOf(id), "cancelled", null, Map.of(), true);
        return dtoOf(b, fetchLines(id));
    }

    /**
     * Read-side gate mirroring {@code PermanentAdvanceService.checkSelfOrPayrollRead}.
     * Returns true when the caller was let in via the self-match path (not
     * payroll.read) — the signal {@link #listForEmployee} uses to decide
     * whether the DTO needs rescoping down to the caller's own line.
     */
    private boolean checkSelfOrPayrollRead(long employeeId) {
        CurrentUser caller = CurrentUserHolder.get().orElse(null);
//        if (caller != null && caller.hasPermission("payroll.read")) return false;
        if (caller != null && caller.hasRole("MANAGER")) return false;
        Long callerEmployeeId = employeeClient.meEmployeeIdOrNull();
        if (callerEmployeeId != null && callerEmployeeId == employeeId) return true;
        throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
//                "Missing required permission: payroll.read (or self-access)");
                "Missing required permission: Manager (or self-access)");

    }

    /**
     * The inbox ({@code requestIdsInInbox}) only returns ids that have a
     * live PENDING step, so an empty result here means the chain and the
     * inbox query have gone out of sync — a bug, not a normal case. Fail
     * fast instead of guessing so it gets caught rather than mislabeling
     * the row in the FE.
     */
    private AdvanceApprovalStage stageOf(ShortTermAdvanceBatch b) {
        ApprovalStep step = approvals.currentPending(REQUEST_TYPE, b.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "short-term advance batch " + b.getId() + " is in the pending-approvals inbox but has no PENDING step"));
        return stageOfLabel(step);
    }

    private static AdvanceApprovalStage stageOfLabel(ApprovalStep s) {
        return switch (s.getLabel()) {
            case "Manager Approval" -> AdvanceApprovalStage.MANAGER_APPROVAL;
            case "HR Approval" -> AdvanceApprovalStage.HR_APPROVAL;
            case "Accounts Approval" -> AdvanceApprovalStage.ACCOUNTS_APPROVAL;
            default -> throw new IllegalStateException("unrecognized approval step label: " + s.getLabel());
        };
    }

    /**
     * Write-side gate, same rules as {@code PermanentAdvanceService.resolveFiling}.
     * The chain is always opened on the caller (see {@link #create}), so:
     * <ul>
     *   <li>self-apply — every line is the caller's own employeeId; goes to
     *       the caller's manager, even for a MANAGER (no self-approval);</li>
     *   <li>MANAGER — every line is a direct report or in their department;
     *       the Manager stage is auto-approved, so it goes straight to HR;</li>
     *   <li>TEAM_REQUEST_GENERATION — every line is in their department;
     *       goes to the caller's own manager, not auto-approved.</li>
     * </ul>
     * Returns {@code true} for the MANAGER case, whose Manager stage
     * {@link #create} then auto-approves.
     */
    private boolean checkCreateAccess(List<ShortTermAdvanceLineInput> lines) {
        CurrentUser caller = CurrentUserHolder.get().orElse(null);
        Long callerEmployeeId = employeeClient.meEmployeeIdOrNull();
        if (callerEmployeeId != null && lines.stream().allMatch(l -> callerEmployeeId.equals(l.employeeId()))) {
            return false;
        }
        if (caller != null && caller.hasRole("MANAGER")) {
            Set<Long> covered = new HashSet<>(employeeClient.myDirectReportIds());
            covered.addAll(employeeClient.myDepartmentEmployeeIds());
            // A manager's own line in an on-behalf batch would be self-approved.
            if (callerEmployeeId != null) covered.remove(callerEmployeeId);
            if (lines.stream().allMatch(l -> covered.contains(l.employeeId()))) return true;
        }
        if (caller != null && caller.hasRole("TEAM_REQUEST_GENERATION")) {
            Set<Long> department = employeeClient.myDepartmentEmployeeIds();
            if (lines.stream().allMatch(l -> department.contains(l.employeeId()))) return false;
        }
        throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                "short-term advances may be raised only for yourself, your direct reports or department (MANAGER), "
                        + "or your own department (TEAM_REQUEST_GENERATION)");
    }

    private List<ShortTermAdvanceLineDto> fetchLines(long batchId) {
        List<ShortTermAdvance> rows = lines.findAllByBatchIdOrderByEmployeeIdAsc(batchId);
        Map<Long, EmployeeDisplay> people = displayCache.getMany(
                rows.stream().map(ShortTermAdvance::getEmployeeId).distinct().toList());
        return rows.stream().map(s -> lineDtoOf(s, EmployeeSummaryMapper.toSummary(people.getOrDefault(
                s.getEmployeeId(), EmployeeDisplay.placeholder(s.getEmployeeId()))))).toList();
    }


    /**
     * Self-serve rescoping for {@link #listForEmployee}: lines filtered to
     * the caller's own, totalAmount/totalEmployees recomputed off that
     * filtered set rather than the batch entity's whole-batch totals — see
     * class doctrine on why the ESS "my requests" view can't show
     * batch-mate data.
     */
    private ShortTermAdvanceBatchDto dtoForEmployeeScoped(ShortTermAdvanceBatch b, long employeeId) {
        return dtoScopedTo(b, Set.of(employeeId));
    }

    /** Same rescoping as {@link #dtoForEmployeeScoped}, over a set of employees (a manager's team). */
    private ShortTermAdvanceBatchDto dtoScopedTo(ShortTermAdvanceBatch b, Set<Long> employeeIds) {
        List<ShortTermAdvanceLineDto> own = fetchLines(b.getId()).stream()
                .filter(l -> employeeIds.contains(l.employeeId()))
                .toList();
        BigDecimal ownTotal = own.stream().map(ShortTermAdvanceLineDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return dtoOf(b, own, ownTotal, own.size());
    }


    /**
     * Entity -> DTO mapping lives here rather than on the record: both DTOs
     * moved to services/api so BFF can deserialize them as typed responses
     * (see ShortTermAdvanceInboxRow), and api can't depend on this service's
     * JPA entities. Instance method (not static) because it needs
     * {@link #approvals} to pull the real commons-approval chain — same
     * currentStage/steps/actions shape as leave/overtime/regularization/
     * requisitions/factory-tours.
     */
    private ShortTermAdvanceBatchDto dtoOf(ShortTermAdvanceBatch b, List<ShortTermAdvanceLineDto> lines) {
        return dtoOf(b, lines, b.getTotalAmount(), b.getTotalEmployees());
    }

    private ShortTermAdvanceBatchDto dtoOf(ShortTermAdvanceBatch b, List<ShortTermAdvanceLineDto> lines,
                                           BigDecimal totalAmount, int totalEmployees) {
        List<ApprovalStep> chain = approvals.findChain(REQUEST_TYPE, b.getId());
        List<ApprovalStepDto> steps = stepsDto(chain);
        ApprovalStep hrStep = chain.stream().filter(s -> "HR Approval".equals(s.getLabel())).findFirst().orElse(null);
        ApprovalStep accountsStep = chain.stream().filter(s -> "Accounts Approval".equals(s.getLabel())).findFirst().orElse(null);
        return new ShortTermAdvanceBatchDto(
                b.getId(), b.getRequestCode(),
                b.getDepartmentId(), b.getSectionId(),
                b.getTargetYear(), b.getTargetMonth(),
                totalAmount, totalEmployees,
                b.getStatus(),
                hrStep == null ? null : hrStep.getDecidedBy(),
                hrStep == null ? null : hrStep.getDecidedAt(),
                hrStep == null ? null : hrStep.getNote(),
                accountsStep == null ? null : accountsStep.getDecidedBy(),
                accountsStep == null ? null : accountsStep.getDecidedAt(),
                accountsStep == null ? null : accountsStep.getNote(),
                b.getRejectionReason(), b.getNotes(),
                lines, b.getCreatedAt(),
                currentStage(chain), steps, actionsForCaller(b, chain));
    }

    private static List<ApprovalStepDto> stepsDto(List<ApprovalStep> chain) {
        List<ApprovalStepDto> out = new ArrayList<>(chain.size());
        for (ApprovalStep s : chain) {
            out.add(new ApprovalStepDto(
                    s.getStageNo(), s.getLabel(), s.getAssignedTo(), null,
                    s.getStatus().name(), s.getDecidedBy(), null, s.getDecidedAt(), s.getNote()));
        }
        return out;
    }

    private static CurrentStageDto currentStage(List<ApprovalStep> chain) {
        return chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING)
                .findFirst()
                .map(s -> new CurrentStageDto(s.getStageNo(), s.getLabel(), s.getAssignedTo(), null))
                .orElse(null);
    }

    private List<String> actionsForCaller(ShortTermAdvanceBatch b, List<ApprovalStep> chain) {
        return CurrentUserHolder.get().map(user -> actionsFor(b, chain, user)).orElse(List.of());
    }

    private List<String> actionsFor(ShortTermAdvanceBatch b, List<ApprovalStep> chain, CurrentUser user) {
        List<String> out = new ArrayList<>(3);
        ApprovalStep current = chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING).findFirst().orElse(null);
        if (current != null && approvals.canAct(REQUEST_TYPE, current, user)) {
            out.add("APPROVE");
            out.add("REJECT");
        }
        boolean cancellable = b.getStatus() == AdvanceStatus.PENDING
                || b.getStatus() == AdvanceStatus.MANAGER_APPROVED
                || b.getStatus() == AdvanceStatus.HR_APPROVED
                || b.getStatus() == AdvanceStatus.APPROVED;
        if (cancellable && user.hasPermission("payroll.write")) {
            out.add("CANCEL");
        }
        return out;
    }

    private static ShortTermAdvanceLineDto lineDtoOf(ShortTermAdvance s, EmployeeSummary employee) {
        return new ShortTermAdvanceLineDto(
                s.getId(), s.getBatchId(), s.getEmployeeId(), employee,
                s.getAmount(), s.getTargetYear(), s.getTargetMonth(),
                s.getStatus(), s.getRejectionReason(), s.getDeductedInRunId());
    }

    private ShortTermAdvanceBatch require(long id) {
        return batches.findById(id)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.SHORT_TERM_BATCH_NOT_FOUND,
                        "short-term advance batch not found"));
    }

    private void requireStatus(ShortTermAdvanceBatch b, AdvanceStatus expected) {
        if (b.getStatus() != expected) {
            throw new ConflictError(PayrollErrorCode.SHORT_TERM_BATCH_INVALID_STATE,
                    "batch must be in " + expected + " (was " + b.getStatus() + ")");
        }
    }

    private CurrentUser currentUser() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
    }

    /**
     * Batches the caller raised (a team-request generator's New batch, or a
     * single row filed from the team-request page). Lines are included so
     * the caller can see who each batch covers.
     */
    @Transactional(readOnly = true)
    public PageResponse<ShortTermAdvanceBatchDto> filedByMe(AdvanceStatus status, java.time.LocalDate filedFrom,
                                                        java.time.LocalDate filedTo, Pageable pageable) {
        CurrentUser caller = currentUser();
        Page<ShortTermAdvanceBatch> page = batches.searchCreatedBy(caller.username(), status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        List<ShortTermAdvanceBatchDto> content = page.getContent().stream()
                .map(b -> dtoOf(b, fetchLines(b.getId())))
                .toList();
        return PageResponse.from(page, content);
    }

    /**
     * Every batch with a line for someone in the caller's department, whoever
     * raised it. Optional {@code employeeId} narrows to batches covering one
     * colleague (outside the department → empty page).
     */
    @Transactional(readOnly = true)
    public PageResponse<ShortTermAdvanceBatchDto> forDepartment(AdvanceStatus status, java.time.LocalDate filedFrom,
                                                                java.time.LocalDate filedTo, Long employeeId,
                                                                Pageable pageable) {
        java.util.Set<Long> ids = employeeClient.myDepartmentEmployeeIds();
        if (employeeId != null) ids = ids.contains(employeeId) ? java.util.Set.of(employeeId) : java.util.Set.of();
        if (ids.isEmpty()) {
            return PageResponse.from(org.springframework.data.domain.Page.empty(pageable), List.of());
        }
        Page<ShortTermAdvanceBatch> page = batches.searchDepartment(ids, status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        List<ShortTermAdvanceBatchDto> content = page.getContent().stream()
                .map(b -> dtoOf(b, fetchLines(b.getId())))
                .toList();
        return PageResponse.from(page, content);
    }

    /**
     * Batches carrying a line for the caller, rescoped to that one line —
     * batch-mates never show (same rule as the self-serve
     * {@link #listForEmployee}). Empty when the caller has no employee record.
     */
    @Transactional(readOnly = true)
    public PageResponse<ShortTermAdvanceBatchDto> mine(AdvanceStatus status, java.time.LocalDate filedFrom,
                                                       java.time.LocalDate filedTo, Pageable pageable) {
        currentUser();
        Long me = employeeClient.meEmployeeIdOrNull();
        return scopedSearch(me == null ? Set.of() : Set.of(me), status, filedFrom, filedTo, pageable);
    }

    /**
     * Batches carrying a line for any of the caller's direct reports, each
     * rescoped to just those reports' lines. Empty for a caller who manages
     * nobody.
     */
    @Transactional(readOnly = true)
    public PageResponse<ShortTermAdvanceBatchDto> team(AdvanceStatus status, java.time.LocalDate filedFrom,
                                                       java.time.LocalDate filedTo, Pageable pageable) {
        currentUser();
        return scopedSearch(employeeClient.myDirectReportIds(), status, filedFrom, filedTo, pageable);
    }

    private PageResponse<ShortTermAdvanceBatchDto> scopedSearch(Set<Long> employeeIds, AdvanceStatus status,
                                                                java.time.LocalDate filedFrom,
                                                                java.time.LocalDate filedTo, Pageable pageable) {
        if (employeeIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable), List.of());
        }
        Page<ShortTermAdvanceBatch> page = batches.searchDepartment(employeeIds, status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        List<ShortTermAdvanceBatchDto> content = page.getContent().stream()
                .map(b -> dtoScopedTo(b, employeeIds))
                .toList();
        return PageResponse.from(page, content);
    }
}

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
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.AdjustPermanentAdvanceRequest;
import com.forward.hrms.payroll.dto.request.AdvanceDecisionRequest;
import com.forward.hrms.payroll.dto.request.AdvanceRejectRequest;
import com.forward.hrms.payroll.dto.request.CreatePermanentAdvanceRequest;
import com.forward.hrms.payroll.dto.request.RecordPermanentAdvanceDeductionRequest;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceDeductionDto;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceDto;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceInboxRow;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.enums.AdvanceApprovalStage;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PermanentAdvanceService {

    private static final String REQUEST_TYPE = "PERMANENT_ADVANCE";

    private final PermanentAdvanceRepository advances;
    private final PermanentAdvanceDeductionRepository deductions;
    private final PayrollRequestCodeService requestCodes;
    private final AuditEmitter audit;
    private final EmployeeClient employeeClient;
    private final com.forward.hrms.commons.cache.EmployeeDisplayCache displayCache;
    private final ApprovalService approvals;
    private final ApprovalInboxService approvalInbox;
    private final GratuityService gratuity;

    @Transactional(readOnly = true)
    public PageResponse<PermanentAdvanceDto> search(Long employeeId, AdvanceStatus status, Pageable pageable) {
        Page<PermanentAdvance> page = advances.search(employeeId, status, pageable);
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employeeClient.getMany(
                page.getContent().stream().map(PermanentAdvance::getEmployeeId).toList());
        Map<Long, EmployeeSummary> people = summaries(
                page.getContent().stream().map(PermanentAdvance::getEmployeeId).toList());
        Map<Long, BigDecimal> recovered = recoveredByAdvance(page.getContent());
        List<PermanentAdvanceDto> content = page.getContent().stream()
                .map(a -> dtoOf(a, recovered.get(a.getId()),
                        cardNo(briefs.get(a.getEmployeeId())), people.get(a.getEmployeeId())))
                .toList();
        return PageResponse.from(page, content);
    }

    @Transactional(readOnly = true)
    public PageResponse<PermanentAdvanceDto> listForEmployee(long employeeId, AdvanceStatus status, Pageable pageable) {
        checkSelfOrPayrollRead(employeeId);
        Page<PermanentAdvance> page = advances.search(employeeId, status, pageable);
        Long cardNo = cardNo(employeeClient.get(employeeId));
        EmployeeSummary employee = summary(employeeId);
        Map<Long, BigDecimal> recovered = recoveredByAdvance(page.getContent());
        List<PermanentAdvanceDto> content = page.getContent().stream()
                .map(a -> dtoOf(a, recovered.get(a.getId()), cardNo, employee))
                .toList();
        return PageResponse.from(page, content);
    }

    /**
     * Pending-approval inbox for the caller — merges both review stages into
     * one list (not separate buckets), backed by the real commons-approval
     * chain via {@link ApprovalInboxService}. A caller holding both
     * payroll.advance.hr and payroll.advance.accounts sees a single combined,
     * newest-first list; each row is tagged with {@link AdvanceApprovalStage}
     * so a consumer can still tell which stage — and which action — applies.
     */
    @Transactional(readOnly = true)
    public PageResponse<PermanentAdvanceInboxRow> pendingApprovals(String q, Pageable pageable) {
        CurrentUser caller = currentUser();
        List<Long> ids = approvalInbox.requestIdsInInbox(REQUEST_TYPE, caller);
        Map<Long, PermanentAdvance> byId = new LinkedHashMap<>();
        for (PermanentAdvance a : advances.findAllById(ids)) byId.put(a.getId(), a);
        List<PermanentAdvance> all = ids.stream().map(byId::get).filter(Objects::nonNull)
                .sorted((x, y) -> Long.compare(y.getId(), x.getId())).toList();
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employeeClient.getMany(
                all.stream().map(PermanentAdvance::getEmployeeId).toList());
        List<PermanentAdvance> rows = all.stream().filter(inboxMatcher(q, briefs)).toList();
        Map<Long, EmployeeSummary> people = summaries(rows.stream().map(PermanentAdvance::getEmployeeId).toList());
        Map<Long, BigDecimal> recovered = recoveredByAdvance(rows);
        return InboxPage.of(rows, pageable, a -> new PermanentAdvanceInboxRow(
                dtoOf(a, recovered.get(a.getId()), cardNo(briefs.get(a.getEmployeeId())),
                        people.get(a.getEmployeeId())),
                stageOf(a)));
    }

    /**
     * Inbox {@code q}: matches the advance id exactly (numeric {@code q}
     * only), or a case-insensitive substring of its request code, employee
     * code, or employee name. Null/blank matches everything.
     */
    private static java.util.function.Predicate<PermanentAdvance> inboxMatcher(
            String q, Map<Long, EmployeeClient.EmployeeBrief> briefs) {
        if (q == null || q.isBlank()) return a -> true;
        String needle = q.trim().toLowerCase(java.util.Locale.ROOT);
        Long idNeedle = needle.chars().allMatch(Character::isDigit) && needle.length() <= 18
                ? Long.parseLong(needle) : null;
        return a -> {
            if (idNeedle != null && idNeedle.equals(a.getId())) return true;
            if (containsIgnoreCase(a.getRequestCode(), needle)) return true;
            EmployeeClient.EmployeeBrief p = briefs.get(a.getEmployeeId());
            return p != null && (containsIgnoreCase(p.employeeCode(), needle)
                    || containsIgnoreCase(p.fullName(), needle));
        };
    }

    private static boolean containsIgnoreCase(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(java.util.Locale.ROOT).contains(lowerNeedle);
    }

    /**
     * The inbox ({@code requestIdsInInbox}) only returns ids that have a
     * live PENDING step, so an empty result here means the chain and the
     * inbox query have gone out of sync — a bug, not a normal case. Fail
     * fast instead of guessing so it gets caught rather than mislabeling
     * the row in the FE.
     */
    private AdvanceApprovalStage stageOf(PermanentAdvance a) {
        ApprovalStep step = approvals.currentPending(REQUEST_TYPE, a.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "permanent advance " + a.getId() + " is in the pending-approvals inbox but has no PENDING step"));
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
     * Self-serve gate for per-employee advance reads. Allows the call when
     * the caller holds {@code payroll.read} OR when the path id is the
     * caller's own employee id (resolved via employee-service /me).
     * Mirrors {@code PayslipService.checkSelfOrPayrollRead}.
     */
    private void checkSelfOrPayrollRead(long employeeId) {
        CurrentUser caller = CurrentUserHolder.get().orElse(null);
        if (caller != null && caller.hasPermission("payroll.read")) return;
        Long callerEmployeeId = employeeClient.meEmployeeIdOrNull();
        if (callerEmployeeId != null && callerEmployeeId == employeeId) return;
        throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                "Missing required permission: payroll.read (or self-access)");
    }

    /**
     * How a create was filed: whose manager the chain's Manager stage
     * resolves to, and whether that stage is auto-approved.
     */
    private record Filing(long approvalAnchorEmployeeId, boolean managerOnBehalf) {}

    /**
     * Write-side gate, in order:
     * <ul>
     *   <li>self-apply — normal chain on the caller, so a MANAGER filing for
     *       themselves goes to their own manager, not auto-approved;</li>
     *   <li>MANAGER — their direct reports or anyone in their department;
     *       the Manager stage is auto-approved, so it goes straight to HR;</li>
     *   <li>TEAM_REQUEST_GENERATION — anyone in their department; normal
     *       chain, but the Manager stage is anchored on the <em>caller</em>,
     *       so the caller's own manager approves it.</li>
     * </ul>
     */
    private Filing resolveFiling(long employeeId) {
        CurrentUser caller = CurrentUserHolder.get().orElse(null);
        Long callerEmployeeId = employeeClient.meEmployeeIdOrNull();
        if (callerEmployeeId != null && callerEmployeeId == employeeId) {
            return new Filing(employeeId, false);
        }
        if (caller != null && caller.hasRole("MANAGER")
                && (employeeClient.myDirectReportIds().contains(employeeId)
                    || employeeClient.myDepartmentEmployeeIds().contains(employeeId))) {
            return new Filing(employeeId, true);
        }
        if (caller != null && callerEmployeeId != null && caller.hasRole("TEAM_REQUEST_GENERATION")
                && employeeClient.myDepartmentEmployeeIds().contains(employeeId)) {
            return new Filing(callerEmployeeId, false);
        }
        throw new ForbiddenError(CommonErrorCode.FORBIDDEN,
                "permanent advances may be raised only for yourself, your direct reports or department "
                        + "(MANAGER), or your own department (TEAM_REQUEST_GENERATION)");
    }

    /**
     * One permanent advance per employee per calendar month, counted by
     * when it was filed. A REJECTED or CANCELLED one doesn't count, so the
     * employee may apply again.
     */
    private void requireNoAdvanceFiledThisMonth(long employeeId) {
        java.time.YearMonth month = java.time.YearMonth.now();
        if (advances.existsActiveFiledBetween(employeeId, month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay())) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_ALREADY_APPLIED,
                    "a permanent advance has already been applied for employee " + employeeId + " in " + month);
        }
    }

    /**
     * A manager filing on behalf has approved by filing, so the Manager
     * stage closes here and the advance moves on to HR — same rule as
     * attendance's MANAGER_ON_BEHALF. {@code closeCurrentStage} bypasses
     * {@link com.forward.hrms.payroll.approval.PermanentAdvanceApprovalOutcomeHandler},
     * so the status flip and audit row it would write are applied inline.
     */
    private void autoApproveManagerStage(PermanentAdvance a) {
        if (!approvals.isAtResolverKind(REQUEST_TYPE, a.getId(), ResolverKind.MANAGER)) return;
        CurrentUser actor = currentUser();
        approvals.closeCurrentStage(REQUEST_TYPE, a.getId(), StepStatus.APPROVED, actor,
                "submitted on behalf by manager");
        a.setStatus(AdvanceStatus.MANAGER_APPROVED);
        audit.emit("permanent_advance", String.valueOf(a.getId()), "manager_approved",
                null, Map.of("by", actor.userId(), "onBehalf", true), true);
    }

    @Transactional(readOnly = true)
    public PermanentAdvanceDto get(long id) {
        PermanentAdvance a = require(id);
        return dtoOf(a, recoveredByAdvance(List.of(a)).get(id), cardNo(a.getEmployeeId()),
                summary(a.getEmployeeId()));
    }

    /**
     * Bulk P.Adv Balance for a page of employees (legacy payslip's
     * "P.Adv Balance" line): APPROVED advances − all deductions. Employees
     * with nothing outstanding are absent from the map — callers default to zero.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> outstandingBalances(java.util.Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) return Map.of();
        Map<Long, BigDecimal> deducted = new java.util.HashMap<>();
        for (var r : deductions.totalDeductedByEmployeeIds(employeeIds)) deducted.put(r.getEmployeeId(), r.getAppliedAmount());
        Map<Long, BigDecimal> out = new java.util.HashMap<>();
        for (var r : advances.approvedTotalsByEmployeeIds(employeeIds)) {
            BigDecimal balance = r.getAppliedAmount().subtract(deducted.getOrDefault(r.getEmployeeId(), BigDecimal.ZERO));
            if (balance.signum() > 0) out.put(r.getEmployeeId(), balance);
        }
        return out;
    }

    @Transactional
    public PermanentAdvanceDto create(CreatePermanentAdvanceRequest req) {
        Filing filing = resolveFiling(req.employeeId());
        EmployeeDto employee = requireActiveEmployee(req.employeeId());
        requireNoAdvanceFiledThisMonth(req.employeeId());
        if (req.amount().compareTo(req.monthlyDeduction()) < 0) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_AMOUNT_INVALID,
                    "monthly deduction cannot exceed principal");
        }
        // §7.payroll #35 — monthly = 0 created an advance that would never
        // amortise (the cron would skip it forever). amount = 0 is also a
        // no-op. Both are typos worth catching at create time.
        if (req.monthlyDeduction().signum() <= 0) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_AMOUNT_INVALID,
                    "monthlyDeduction must be > 0");
        }
        if (req.amount().signum() <= 0) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_AMOUNT_INVALID,
                    "amount must be > 0");
        }
        PermanentAdvance a = new PermanentAdvance();
        a.setRequestCode(requestCodes.allocate("PA"));
        a.setEmployeeId(req.employeeId());
        a.setJoiningDate(employee.joiningDate());
        a.setAmount(req.amount());
        a.setMonthlyDeduction(req.monthlyDeduction());
        a.setBonusOffset(req.bonusOffset() == null ? java.math.BigDecimal.ZERO : req.bonusOffset());
        a.setAnnualLeaveOffset(req.annualLeaveOffset() == null ? java.math.BigDecimal.ZERO : req.annualLeaveOffset());
        // Collateral snapshot, computed here rather than taken from the client.
        // Throws 404 if the employee has no payroll settings, or is eligible
        // but has no salary row — an advance can't be sized without either.
        a.setGratuityAtRequest(gratuity.compute(req.employeeId(), java.time.LocalDate.now()).gratuity());
        a.setReceivingDate(req.receivingDate());
        a.setDescription(req.description());
        a.setStatus(AdvanceStatus.PENDING);
        a.setFiscalYearCode(req.financialYearCode());
        a.setBonusOffset2(req.bonusOffset2() == null ? java.math.BigDecimal.ZERO : req.bonusOffset2());
        if(req.annualLeaveOffset() != null && req.annualLeaveMonth() == null){
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_AMOUNT_INVALID,
                    "annualLeaveMonth must be provided when annualLeaveOffset is non-zero");
        }
        if (req.annualLeaveMonth() != null) {
            a.setAnnualLeaveMonth(req.annualLeaveMonth().withDayOfMonth(1));
        }
        advances.save(a);
        approvals.openChain(REQUEST_TYPE, a.getId(), filing.approvalAnchorEmployeeId());
        audit.emit("permanent_advance", String.valueOf(a.getId()), "created",
                null, Map.of("employeeId", req.employeeId(), "amount", req.amount()), true);
        if (filing.managerOnBehalf()) autoApproveManagerStage(a);
        return dtoOf(a, java.math.BigDecimal.ZERO, cardNo(a.getEmployeeId()), summary(a.getEmployeeId()));
    }

    @Transactional
    public PermanentAdvanceDto managerApprove(long id, AdvanceDecisionRequest body) {
        PermanentAdvance a = require(id);
        requireStatus(a, AdvanceStatus.PENDING);
        approvals.approve(REQUEST_TYPE, id, body == null ? null : body.note());
        return dto(require(id));
    }

    @Transactional
    public PermanentAdvanceDto hrApprove(long id, AdvanceDecisionRequest body) {
        PermanentAdvance a = require(id);
        requireStatus(a, AdvanceStatus.MANAGER_APPROVED);
        approvals.approve(REQUEST_TYPE, id, body == null ? null : body.note());
        return dto(require(id));
    }

    /**
     * HR revises {@code amount} / {@code monthlyDeduction} while the advance
     * waits at the HR stage (MANAGER_APPROVED), before approving it. The
     * approval chain is untouched — HR still approves separately.
     */
    @Transactional
    public PermanentAdvanceDto hrAdjustBeforeApproval(long id, AdjustPermanentAdvanceRequest req) {
        PermanentAdvance a = require(id);
        requireStatus(a, AdvanceStatus.MANAGER_APPROVED);
        if (req.monthlyDeduction().compareTo(req.amount()) > 0) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_AMOUNT_INVALID,
                    "monthly deduction cannot exceed principal");
        }
        Map<String, Object> before = adjustSnapshot(a);
        a.setAmount(req.amount());
        a.setMonthlyDeduction(req.monthlyDeduction());
        Map<String, Object> after = new LinkedHashMap<>(adjustSnapshot(a));
        if (req.note() != null) after.put("note", req.note());
        audit.emit("permanent_advance", String.valueOf(id), "hr_adjusted", before, after, true);
        return dto(a);
    }

    private static Map<String, Object> adjustSnapshot(PermanentAdvance a) {
        return Map.of("amount", a.getAmount(), "monthlyDeduction", a.getMonthlyDeduction());
    }

    /** HR_APPROVED advances (awaiting Accounts), optionally for one employee — HR's advance-slip list. */
    @Transactional(readOnly = true)
    public PageResponse<PermanentAdvanceDto> hrApprovedReceivingList(Long employeeId, Pageable pageable) {
        return search(employeeId, AdvanceStatus.HR_APPROVED, pageable);
    }

    @Transactional
    public PermanentAdvanceDto accountsApprove(long id, AdvanceDecisionRequest body) {
        PermanentAdvance a = require(id);
        requireStatus(a, AdvanceStatus.HR_APPROVED);
        approvals.approve(REQUEST_TYPE, id, body == null ? null : body.note());
        return dto(require(id));
    }

    @Transactional
    public PermanentAdvanceDto reject(long id, AdvanceRejectRequest body) {
        PermanentAdvance a = require(id);
        if (a.getStatus() != AdvanceStatus.PENDING && a.getStatus() != AdvanceStatus.MANAGER_APPROVED
                && a.getStatus() != AdvanceStatus.HR_APPROVED) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_INVALID_STATE,
                    "advance can only be rejected before disbursement");
        }
        approvals.reject(REQUEST_TYPE, id, body.reason());
        return dto(require(id));
    }

    @Transactional
    public PermanentAdvanceDto cancel(long id) {
        PermanentAdvance a = require(id);
        // APPROVED is the live state — payroll deducts against it — so it
        // is no longer cancellable.
        if (a.getStatus() == AdvanceStatus.APPROVED
                || a.getStatus() == AdvanceStatus.REJECTED || a.getStatus() == AdvanceStatus.CANCELLED) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_INVALID_STATE,
                    "advance is no longer cancellable");
        }
        approvals.cancelChain(REQUEST_TYPE, id);
        a.setStatus(AdvanceStatus.CANCELLED);
        audit.emit("permanent_advance", String.valueOf(id), "cancelled",
                null, Map.of(), true);
        return dto(a);
    }

    @Transactional(readOnly = true)
    public List<PermanentAdvanceDeductionDto> listDeductions(long advanceId) {
        require(advanceId);
        // Deductions belong to the employee, not one advance: this is the
        // advance holder's whole permanent-advance ledger.
        return deductions.findAllByEmployeeIdOrderByTransactionDateDescIdDesc(require(advanceId).getEmployeeId()).stream()
                .map(PermanentAdvanceDeductionDto::of).toList();
    }

    @Transactional
    public PermanentAdvanceDeductionDto recordDeduction(long advanceId,
                                                        RecordPermanentAdvanceDeductionRequest req) {
        PermanentAdvance a = require(advanceId);
        if (a.getStatus() != AdvanceStatus.APPROVED) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_INVALID_STATE,
                    "deductions are only allowed against an APPROVED advance");
        }
        // Balance is per employee, same as the payroll run's installment:
        // all APPROVED advances minus every deduction in the ledger.
        java.math.BigDecimal taken = advances.employeeTotalPermanentAdvance(a.getEmployeeId());
        java.math.BigDecimal alreadyDeducted = deductions.employeeTotalPermanentAdvanceDeduction(a.getEmployeeId());
        java.math.BigDecimal headroom = (taken == null ? java.math.BigDecimal.ZERO : taken)
                .subtract(alreadyDeducted).max(java.math.BigDecimal.ZERO);
        if (req.deductedAmount() == null || req.deductedAmount().signum() <= 0) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_AMOUNT_INVALID,
                    "deductedAmount must be > 0");
        }
        // §7.payroll #36 — refuse to silently truncate the requested amount
        // down to the remaining headroom. HR who meant to deduct exactly
        // {req.deductedAmount} should know it can't fit. Headroom == 0 is
        // already caught here (req > 0 from the prior check, headroom == 0
        // ⇒ req > headroom).
        if (req.deductedAmount().compareTo(headroom) > 0) {
            throw new ConflictError(
                    PayrollErrorCode.PERMANENT_ADVANCE_DEDUCTION_EXCEEDS_REMAINING,
                    "deductedAmount " + req.deductedAmount()
                            + " exceeds remaining balance " + headroom);
        }
        java.math.BigDecimal capped = req.deductedAmount();
        java.math.BigDecimal remaining = headroom.subtract(capped);

        // The advance stays APPROVED even at zero balance — status never
        // marks repayment; the employee-level sum does.
        PermanentAdvanceDeduction d = new PermanentAdvanceDeduction();
        d.setEmployeeId(a.getEmployeeId());
        d.setSource(com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource.MANUAL);
        d.setDeductedAmount(capped);
        d.setRemainingBalance(remaining);
        d.setTransactionDate(req.transactionDate());
        d.setDescription(req.description() == null || req.description().isBlank()
                ? "Manual deduction for " + employeeCodeOf(a.getEmployeeId())
                : req.description());
        deductions.save(d);

        audit.emit("permanent_advance_deduction", String.valueOf(d.getId()), "recorded",
                null, Map.of("advanceId", advanceId, "deducted", capped, "remaining", remaining), true);
        return PermanentAdvanceDeductionDto.of(d);
    }

    /**
     * Delete a deduction entered by mistake. Only MANUAL rows: every other
     * source has already been applied to a payslip, bonus or settlement, so
     * it is removed by undoing that source (payroll run reset, settlement
     * reject/cancel, …), which keeps the ledger and the paid amounts in step.
     */
    @Transactional
    public void deleteDeduction(long advanceId, long deductionId) {
        long employeeId = require(advanceId).getEmployeeId();
        PermanentAdvanceDeduction d = deductions.findById(deductionId)
                .filter(x -> java.util.Objects.equals(x.getEmployeeId(), employeeId))
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.PERMANENT_ADVANCE_DEDUCTION_NOT_FOUND,
                        "deduction " + deductionId + " not found for this advance's employee"));
        if (d.getSource() != com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource.MANUAL) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_DEDUCTION_NOT_MANUAL,
                    d.getSource() + " deductions are removed by undoing their source, not deleted directly");
        }
        Map<String, Object> before = new java.util.LinkedHashMap<>();
        before.put("employeeId", d.getEmployeeId());
        before.put("deductedAmount", d.getDeductedAmount());
        before.put("transactionDate", d.getTransactionDate());
        before.put("description", d.getDescription());
        deductions.delete(d);
        audit.emit("permanent_advance_deduction", String.valueOf(deductionId), "deleted", before, null, true);
    }

    /**
     * Amount recovered per advance id. Deductions carry no advance, so each
     * employee's total is applied to their APPROVED advances oldest-first
     * ({@link PermanentAdvanceBalances}); non-APPROVED advances recover 0.
     */
    private Map<Long, BigDecimal> recoveredByAdvance(java.util.Collection<PermanentAdvance> shown) {
        Map<Long, BigDecimal> out = new java.util.HashMap<>();
        for (PermanentAdvance a : shown) out.put(a.getId(), BigDecimal.ZERO);
        List<Long> employeeIds = shown.stream().map(PermanentAdvance::getEmployeeId).distinct().toList();
        if (employeeIds.isEmpty()) return out;
        Map<Long, BigDecimal> deducted = new java.util.HashMap<>();
        for (var r : deductions.totalDeductedByEmployeeIds(employeeIds)) deducted.put(r.getEmployeeId(), r.getAppliedAmount());
        Map<Long, List<PermanentAdvance>> approved = advances.findByEmployeeIdInAndStatus(employeeIds, AdvanceStatus.APPROVED)
                .stream().collect(java.util.stream.Collectors.groupingBy(PermanentAdvance::getEmployeeId));
        approved.forEach((emp, list) -> out.putAll(PermanentAdvanceBalances.recoveredPerAdvance(
                list, deducted.getOrDefault(emp, BigDecimal.ZERO))));
        return out;
    }

    /** Display code from the shared cache; falls back to the id if the employee isn't resolvable. */
    private String employeeCodeOf(long employeeId) {
        EmployeeDisplay e = displayCache.getMany(List.of(employeeId)).get(employeeId);
        return e != null && e.employeeCode() != null ? e.employeeCode() : String.valueOf(employeeId);
    }

    private PermanentAdvanceDto dto(PermanentAdvance a) {
        return dtoOf(a, recoveredByAdvance(List.of(a)).get(a.getId()), cardNo(a.getEmployeeId()),
                summary(a.getEmployeeId()));
    }

    /**
     * An advance may be filed only for an ACTIVE employee — not one on
     * notice, on leave, suspended or terminated (same rule as bonus
     * computation). Goes through {@code /employees/by-ids}, which fails
     * closed: employee-service down is a 502, never a silent pass.
     */
    private EmployeeDto requireActiveEmployee(long employeeId) {
        EmployeeDto e = employeeClient.getDetails(List.of(employeeId)).get(employeeId);
        if (e == null) {
            throw new UnprocessableError(PayrollErrorCode.EMPLOYEE_NOT_FOUND,
                    "employee " + employeeId + " not found");
        }
        if (e.employmentStatus() != EmploymentStatus.ACTIVE) {
            throw new UnprocessableError(PayrollErrorCode.EMPLOYEE_INACTIVE,
                    "advance can be filed only for an ACTIVE employee; employee " + employeeId + " is "
                            + e.employmentStatus());
        }
        return e;
    }

    /** Display summaries from the shared employee cache; unknown ids get a placeholder. */
    private Map<Long, EmployeeSummary> summaries(List<Long> employeeIds) {
        Map<Long, EmployeeDisplay> hits = displayCache.getMany(employeeIds.stream().distinct().toList());
        Map<Long, EmployeeSummary> out = new java.util.HashMap<>();
        for (Long id : employeeIds) {
            out.computeIfAbsent(id, k -> EmployeeSummaryMapper.toSummary(
                    hits.getOrDefault(k, EmployeeDisplay.placeholder(k))));
        }
        return out;
    }

    private EmployeeSummary summary(long employeeId) {
        return summaries(List.of(employeeId)).get(employeeId);
    }

    /**
     * Entity -> DTO mapping lives here rather than on the record: the DTO
     * moved to services/api so BFF can deserialize it as a typed response
     * (see PermanentAdvanceInboxRow), and api can't depend on this service's
     * JPA entity. Instance method (not static) because it needs
     * {@link #approvals} to pull the real commons-approval chain — same
     * currentStage/steps/actions shape as leave/overtime/regularization/
     * requisitions/factory-tours.
     */
    private PermanentAdvanceDto dtoOf(PermanentAdvance a, BigDecimal deductedTotal, Long cardNo,
                                          EmployeeSummary employee) {
        BigDecimal remaining = a.getAmount().subtract(deductedTotal).max(BigDecimal.ZERO);
        List<ApprovalStep> chain = approvals.findChain(REQUEST_TYPE, a.getId());
        List<ApprovalStepDto> steps = stepsDto(chain);
        ApprovalStep hrStep = chain.stream().filter(s -> "HR Approval".equals(s.getLabel())).findFirst().orElse(null);
        ApprovalStep accountsStep = chain.stream().filter(s -> "Accounts Approval".equals(s.getLabel())).findFirst().orElse(null);
        return new PermanentAdvanceDto(
                a.getId(), a.getRequestCode(), a.getEmployeeId(),
                employee, a.getJoiningDate(),
                a.getAmount(), a.getMonthlyDeduction(),
                a.getBonusOffset(), a.getAnnualLeaveOffset(),
                a.getGratuityAtRequest(), a.getReceivingDate(),
                a.getDescription(), a.getStatus(),
                deductedTotal, remaining,
                hrStep == null ? null : hrStep.getDecidedBy(),
                hrStep == null ? null : hrStep.getDecidedAt(),
                hrStep == null ? null : hrStep.getNote(),
                accountsStep == null ? null : accountsStep.getDecidedBy(),
                accountsStep == null ? null : accountsStep.getDecidedAt(),
                accountsStep == null ? null : accountsStep.getNote(),
                a.getRejectionReason(),
                a.getDisbursedAt(), a.getDisbursedBy(),
                a.getClosedAt(),
                a.getCreatedAt(),
                cardNo,
                currentStage(chain),
                steps,
                actionsForCaller(a, chain),
                a.getFiscalYearCode(),
                a.getBonusOffset2(),
                a.getAnnualLeaveMonth()
        );
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

    private List<String> actionsForCaller(PermanentAdvance a, List<ApprovalStep> chain) {
        return CurrentUserHolder.get().map(user -> actionsFor(a, chain, user)).orElse(List.of());
    }

    private List<String> actionsFor(PermanentAdvance a, List<ApprovalStep> chain, CurrentUser user) {
        List<String> out = new ArrayList<>(3);
        ApprovalStep current = chain.stream()
                .filter(s -> s.getStatus() == StepStatus.PENDING).findFirst().orElse(null);
        if (current != null && approvals.canAct(REQUEST_TYPE, current, user)) {
            out.add("APPROVE");
            out.add("REJECT");
        }
        boolean cancellable = a.getStatus() == AdvanceStatus.PENDING
                || a.getStatus() == AdvanceStatus.MANAGER_APPROVED
                || a.getStatus() == AdvanceStatus.HR_APPROVED;
        if (cancellable && user.hasPermission("payroll.write")) {
            out.add("CANCEL");
        }
        return out;
    }

    /**
     * §V0004(employee) — legacy `cardno` (RFID/punch card) maps to
     * {@code employees.employee_code}; there is no separate card number
     * column. employeeCode is purely numeric for this purpose, so a
     * non-numeric code (or a placeholder from a down employee-service)
     * resolves to {@code null} rather than failing the read.
     */
    private Long cardNo(long employeeId) {
        return cardNo(employeeClient.get(employeeId));
    }

    private static Long cardNo(EmployeeClient.EmployeeBrief brief) {
        if (brief == null || brief.employeeCode() == null) return null;
        try {
            return Long.valueOf(brief.employeeCode().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private PermanentAdvance require(long id) {
        return advances.findById(id)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.PERMANENT_ADVANCE_NOT_FOUND,
                        "permanent advance not found"));
    }

    private void requireStatus(PermanentAdvance a, AdvanceStatus expected) {
        if (a.getStatus() != expected) {
            throw new ConflictError(PayrollErrorCode.PERMANENT_ADVANCE_INVALID_STATE,
                    "advance must be in " + expected + " (was " + a.getStatus() + ")");
        }
    }

    private CurrentUser currentUser() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
    }

    /**
     * Advances the caller filed on someone else's behalf (a team-request
     * generator for a colleague, HR for anyone). Their own self-applications
     * are excluded. Each row carries its approval {@code steps} /
     * {@code currentStage}; names are left to the caller (the team-request
     * page already holds its department roster).
     */
    @Transactional(readOnly = true)
    public PageResponse<PermanentAdvanceDto> filedOnBehalf(AdvanceStatus status, java.time.LocalDate filedFrom,
                                                          java.time.LocalDate filedTo, Pageable pageable) {
        CurrentUser caller = currentUser();
        Long me = employeeClient.meEmployeeIdOrNull();
        Page<PermanentAdvance> page = advances.searchFiledOnBehalf(caller.username(), me, status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employeeClient.getMany(
                page.getContent().stream().map(PermanentAdvance::getEmployeeId).toList());
        Map<Long, EmployeeSummary> people = summaries(
                page.getContent().stream().map(PermanentAdvance::getEmployeeId).toList());
        Map<Long, BigDecimal> recovered = recoveredByAdvance(page.getContent());
        List<PermanentAdvanceDto> content = page.getContent().stream()
                .map(a -> dtoOf(a, recovered.get(a.getId()),
                        cardNo(briefs.get(a.getEmployeeId())), people.get(a.getEmployeeId())))
                .toList();
        return PageResponse.from(page, content);
    }

    /**
     * Every advance in the caller's department, whoever filed it — the
     * team-request generator's department view. Optional {@code employeeId}
     * narrows to one colleague (outside the department → empty page).
     */
    @Transactional(readOnly = true)
    public PageResponse<PermanentAdvanceDto> forDepartment(AdvanceStatus status, java.time.LocalDate filedFrom,
                                                           java.time.LocalDate filedTo, Long employeeId,
                                                           Pageable pageable) {
        java.util.Set<Long> ids = employeeClient.myDepartmentEmployeeIds();
        if (employeeId != null) ids = ids.contains(employeeId) ? java.util.Set.of(employeeId) : java.util.Set.of();
        if (ids.isEmpty()) {
            return PageResponse.from(org.springframework.data.domain.Page.empty(pageable), List.of());
        }
        Page<PermanentAdvance> page = advances.searchDepartment(ids, status,
                filedFrom == null ? null : filedFrom.atStartOfDay(),
                filedTo == null ? null : filedTo.plusDays(1).atStartOfDay(), pageable);
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employeeClient.getMany(
                page.getContent().stream().map(PermanentAdvance::getEmployeeId).toList());
        Map<Long, EmployeeSummary> people = summaries(
                page.getContent().stream().map(PermanentAdvance::getEmployeeId).toList());
        Map<Long, BigDecimal> recovered = recoveredByAdvance(page.getContent());
        List<PermanentAdvanceDto> content = page.getContent().stream()
                .map(a -> dtoOf(a, recovered.get(a.getId()),
                        cardNo(briefs.get(a.getEmployeeId())), people.get(a.getEmployeeId())))
                .toList();
        return PageResponse.from(page, content);
    }
}

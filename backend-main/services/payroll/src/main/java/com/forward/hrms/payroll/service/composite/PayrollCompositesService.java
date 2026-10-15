package com.forward.hrms.payroll.service.composite;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.cache.EmployeeSummaryMapper;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.dto.attendace.EmployeeLeavePayslipSummaryDto;
import java.time.YearMonth;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipReconciliation;
import com.forward.hrms.payroll.client.dto.attendace.PayrollAttendanceAuditDto;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipAdvanceInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipAdvances;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import com.forward.hrms.payroll.repository.projection.EmployeeAppliedAdvanceProjection;
import com.forward.hrms.payroll.repository.projection.PayslipProvenanceProjection;
import com.forward.hrms.payroll.dto.response.BonusEventAmountDto;
import com.forward.hrms.payroll.dto.response.BonusEventDto;
import com.forward.hrms.payroll.dto.response.EmployeeBankDetailsDto;
import com.forward.hrms.payroll.dto.response.EmployeePayrollSettingsDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.dto.response.GratuityDto;
import com.forward.hrms.payroll.dto.response.IncrementBatchDto;
import com.forward.hrms.payroll.dto.response.IncrementBatchLineDto;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDto;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.dto.response.PayrollRunTotalsDto;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.SalaryComponentDto;
import com.forward.hrms.payroll.dto.response.PdfUrlResponse;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.BonusEventAmountWithEmployee;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.BonusEventDetail;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.EmployeeSalaryTab;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.IncrementBatchDetail;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.IncrementBatchLineWithEmployee;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.MyPayslipRow;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.MyPayslipsResponse;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.OffCyclePaymentRowWithEmployee;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayrollRunDetail;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayrollRunSummary;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayrollRunsListItem;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayrollRunsListResponse;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipQueueResponse;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipQueueRow;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipWithEmployee;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipAnnualLeaveInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipAttendanceInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipBankInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipDeductions;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipEarnings;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipEmployeeInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLeaveInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLeaveLine;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLineItem;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipSalaryInfo;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.service.BonusEventService;
import com.forward.hrms.payroll.service.EmployeeBankDetailsService;
import com.forward.hrms.payroll.service.EmployeePayrollSettingsService;
import com.forward.hrms.payroll.service.EmployeeSalaryComponentService;
import com.forward.hrms.payroll.service.EmployeeSalaryService;
import com.forward.hrms.payroll.service.GratuityService;
import com.forward.hrms.payroll.service.IncrementBatchService;
import com.forward.hrms.payroll.service.OffCyclePaymentService;
import com.forward.hrms.payroll.service.PayrollRunService;
import com.forward.hrms.payroll.service.PayslipService;
import com.forward.hrms.payroll.service.PermanentAdvanceService;
import com.forward.hrms.payroll.service.SalaryComponentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Composite read endpoints for the payroll surfaces. Replaces what
 * lived in BFF's {@code PayrollBffService}: the data still belongs to
 * payroll, the cache (now reachable in-process) provides employee +
 * catalog enrichment, so the BFF wrapper has nothing to add.
 *
 * <p>Each method ports one endpoint from the prior BFF service. The
 * BFF version fanned out via a virtual-thread executor because every
 * downstream call was HTTP. Here all reads are in-process JPA against
 * one Hikari pool — parallelism has no overlap to harvest, so the
 * sequential version is cleaner and at least as fast.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollCompositesService {

    private final PayslipService payslips;
    private final PayrollRunService runs;
    private final IncrementBatchService incrementBatches;
    private final BonusEventService bonusEvents;
    private final OffCyclePaymentService offCyclePayments;
    private final EmployeeSalaryService salaries;
    private final EmployeeSalaryComponentService salaryComponents;
    private final SalaryComponentService components;
    private final EmployeePayrollSettingsService settings;
    private final EmployeeBankDetailsService bankDetails;
    private final GratuityService gratuity;
    private final EmployeeClient employees;
    private final AttendanceClient attendance;
    private final PermanentAdvanceService permanentAdvances;
    private final PayslipLineRepository payslipLines;
    private final PayslipRepository payslipRepository;
    private final ShortTermAdvanceRepository shortTermAdvances;
    private final PermanentAdvanceRepository permanentAdvanceRepository;
    private final PermanentAdvanceDeductionRepository permanentAdvanceDeductions;

    private final EmployeeDisplayCache displayCache;

    // ===== /payslips/me?year= =====

    public MyPayslipsResponse myPayslips(int year, Pageable pageable) {
        Long meId = employees.meEmployeeIdOrNull();
        if (meId == null) {
            throw new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED, "no employee linked to this user");
        }
        PageResponse<PayslipDto> page = payslips.listForEmployeeYear(meId, year, pageable);
        List<MyPayslipRow> rows = mintPdfUrlsAndMap(page.content());
        PageResponse<MyPayslipRow> body = new PageResponse<>(rows, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.sort());
        return new MyPayslipsResponse(meId, year, body);
    }

    // ===== /payroll/runs/{id}/detail =====

    public PayrollRunDetail runDetail(long runId, PayslipStatus status, String q, Pageable pageable) {
        PayrollRunDto run = runs.get(runId);
        PayrollRunSummary summary = summary(runs.totals(runId, status, q));
        PageResponse<PayslipDto> page = runs.payslips(runId, status, q, pageable);
        List<PayslipDto> slice = page.content();

        List<PayslipWithEmployee> rows = enrichLegacyPayslips(slice, null);

        PageResponse<PayslipWithEmployee> body = new PageResponse<>(
                rows, page.page(), page.size(), page.totalElements(), page.totalPages(), page.sort());
        return new PayrollRunDetail(run, summary, body);
    }

    /**
     * Every payslip of a run as the same {@link PayslipWithEmployee} row
     * {@code /payroll/runs/{id}/detail} returns, unpaged and ordered by
     * employee code — the payslip PDF's data source, so the PDF and the
     * run-detail screen can never disagree. Pass the kickoff caller's
     * {@code X-User-*} snapshot on the saga thread; {@code null} on a
     * request thread (the commons-web interceptor forwards identity).
     */
    public List<PayslipWithEmployee> runDetailRows(long runId, Map<String, String> identity) {
        List<PayslipDto> all = runs.payslips(runId, null, null,
                Pageable.unpaged(Sort.by("employeeCode"))).content();
        return enrichLegacyPayslips(all, identity);
    }

    /**
     * Builds the legacy-style payslip row for a page of {@link PayslipDto}s.
     * The salary figures are the persisted {@code payslips} / {@code payslip_lines}
     * snapshot, mapped as-is — nothing is recalculated here. Every
     * cross-service/cross-table lookup is bulked once for the whole page
     * (employee display cache, employee profile, grade catalog, bank
     * details, permanent-advance balances, payslip lines, salary-component
     * catalog, and attendance/leave per payroll period), so the cost does not
     * grow per employee.
     */
    private List<PayslipWithEmployee> enrichLegacyPayslips(List<PayslipDto> slice, Map<String, String> identity) {
        if (slice.isEmpty()) return List.of();

        Set<Long> employeeIds = slice.stream().map(PayslipDto::employeeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, EmployeeDisplay> displays = displayCache.getMany(employeeIds);
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(employeeIds, identity);
        Map<Long, String> gradeNames = employees.getAllGrades(identity).stream()
                .collect(Collectors.toMap(GradeDto::id, GradeDto::name, (a, b) -> a));
        Map<Long, EmployeeBankDetailsDto> banks = bankDetails.getMany(employeeIds);
        Map<Long, BigDecimal> advanceBalances = permanentAdvances.outstandingBalances(employeeIds);

        // Standard earning/deduction heads catalog — the "source of truth" a
        // legacy-style payslip zero-fills against (every active head shown,
        // even at 0, rather than only the heads this payslip happens to
        // have a line for). Bulk-fetched once per page, not per row.
        List<SalaryComponentDto> earningCatalog = components.list(ComponentType.EARNING, true);
        List<SalaryComponentDto> deductionCatalog = components.list(ComponentType.DEDUCTION, true);

        List<Long> payslipIds = slice.stream().map(PayslipDto::id).toList();
        Map<Long, List<PayslipLine>> linesByPayslip = payslipLines
                .findAllByPayslipIdInOrderByPayslipIdAscDisplayOrderAsc(payslipIds).stream()
                .collect(Collectors.groupingBy(PayslipLine::getPayslipId));

        // Advances block (display only). All read-only, bulked per distinct period / run of the page.
        Map<Long, String> createdByByPayslip = new HashMap<>();
        for (PayslipProvenanceProjection pp : payslipRepository.findProvenanceByIdIn(payslipIds)) {
            createdByByPayslip.put(pp.getId(), pp.getCreatedBy());
        }
        Map<YearMonth, Map<Long, List<ShortTermAdvance>>> shortTermByPeriod = new HashMap<>();
        Map<YearMonth, Map<Long, BigDecimal>> permanentAppliedByPeriod = new HashMap<>();
        Map<Long, Map<Long, BigDecimal>> ledgerByRun = new HashMap<>();
        for (PayslipDto p : slice) {
            YearMonth ym = YearMonth.of(p.periodYear(), p.periodMonth());
            shortTermByPeriod.computeIfAbsent(ym, k -> shortTermAdvances
                    .findAppliedForEmployeesInMonth(employeeIds, k.getYear(), k.getMonthValue()).stream()
                    .collect(Collectors.groupingBy(ShortTermAdvance::getEmployeeId)));
            permanentAppliedByPeriod.computeIfAbsent(ym, k -> sumByEmployee(
                    permanentAdvanceRepository.appliedTotalsByEmployeeIds(employeeIds, k.atEndOfMonth())));
            ledgerByRun.computeIfAbsent(p.payrollRunId(), k -> sumByEmployee(
                    permanentAdvanceDeductions.deductedInRunByEmployeeIds(employeeIds, k)));
        }

        // One bulk attendance-service call per distinct payroll period on the
        // page (a run detail page has exactly one).
        Map<YearMonth, Map<Long, EmployeeLeavePayslipSummaryDto>> attendanceByPeriod = new HashMap<>();
        for (PayslipDto p : slice) {
            YearMonth ym = YearMonth.of(p.periodYear(), p.periodMonth());
            attendanceByPeriod.computeIfAbsent(ym,
                    k -> attendance.payslipSummaries(employeeIds, k.atDay(1), k.atEndOfMonth(), identity));
        }

        List<PayslipWithEmployee> out = new ArrayList<>(slice.size());
        for (PayslipDto p : slice) {
            EmployeeLeavePayslipSummaryDto att = attendanceByPeriod
                    .get(YearMonth.of(p.periodYear(), p.periodMonth())).get(p.employeeId());
            YearMonth ym = YearMonth.of(p.periodYear(), p.periodMonth());
            List<PayslipLine> payslipLineRows = linesByPayslip.getOrDefault(p.id(), List.of());
            PayslipAdvances advances = advances(p,
                    PayslipProvenance.isLegacyImport(createdByByPayslip.get(p.id())),
                    shortTermByPeriod.get(ym).getOrDefault(p.employeeId(), List.of()),
                    permanentAppliedByPeriod.get(ym).getOrDefault(p.employeeId(), BigDecimal.ZERO),
                    ledgerByRun.get(p.payrollRunId()).getOrDefault(p.employeeId(), BigDecimal.ZERO),
                    payslipLineRows);
            out.add(buildLegacyPayslip(p, displays.get(p.employeeId()), briefs.get(p.employeeId()),
                    gradeNames, banks.get(p.employeeId()), advanceBalances.get(p.employeeId()),
                    payslipLineRows, earningCatalog, deductionCatalog, att, advances));
        }
        return out;
    }

    private PayslipWithEmployee buildLegacyPayslip(PayslipDto p, EmployeeDisplay display,
            EmployeeClient.EmployeeBrief brief, Map<Long, String> gradeNames,
            EmployeeBankDetailsDto bank, BigDecimal advanceBalance, List<PayslipLine> lines,
            List<SalaryComponentDto> earningCatalog, List<SalaryComponentDto> deductionCatalog,
            EmployeeLeavePayslipSummaryDto att, PayslipAdvances advances) {

        EmployeeClient.EmployeeBrief b = brief != null ? brief
                : EmployeeClient.EmployeeBrief.placeholder(p.employeeId());
        String fullName = display != null ? display.fullName() : b.fullName();
        String employeeCode = display != null && display.employeeCode() != null
                ? display.employeeCode() : p.employeeCode();
        String department = display == null ? null : display.departmentName();
        String designation = display == null ? null : display.designationTitle();
        String profilePictureUrl = display == null ? null : display.profilePictureUrl();
        String grade = b.gradeId() == null ? null : gradeNames.get(b.gradeId());

        PayslipEmployeeInfo employeeInfo = new PayslipEmployeeInfo(
                p.employeeId(), employeeCode, fullName, b.fatherName(),
                designation, department, grade, b.joiningDate(), profilePictureUrl);

        PayslipBankInfo bankInfo = bank == null ? null
                : new PayslipBankInfo(bank.bankName(), bank.branch(), bank.accountNumber());

        List<PayslipLineItem> earningLines = legacyEarningLines(p, earningCatalog, lines);
        List<PayslipLineItem> deductionLines = withAdvanceLines(
                legacyDeductionLines(p, deductionCatalog, lines), advances);

        PayslipEarnings earnings = new PayslipEarnings(earningLines, p.gross());
        // Totals are read straight off the persisted Payslip columns, never
        // re-derived by summing the lines above. The reconciliation block
        // reports any gap between the two rather than hiding it: for a
        // payroll run computed by this codebase they agree exactly; for
        // bulk-imported payslips the historical deduction breakdown was
        // never stored as payslip_lines rows, only as this aggregate.
        BigDecimal totalDeduction = p.deductions().add(p.tax()).add(p.eobi());
        PayslipDeductions deductions = new PayslipDeductions(deductionLines, totalDeduction);

        PayslipSalaryInfo salary = new PayslipSalaryInfo(p.basic(), p.gross(), totalDeduction, p.net(),
                p.tax(), p.eobi(), p.pessiEmployer(), advanceBalance);

        return new PayslipWithEmployee(
                p.id(), p.periodYear(), p.periodMonth(), p.fiscalYearCode(),
                p.status() == null ? null : p.status().name(), p.heldReason(), p.generatedAt(),
                employeeInfo, bankInfo, salary, earnings, deductions,
                attendanceInfo(att == null ? null : att.attendance()),
                leaveInfo(att),
                reconcile(p, earningLines, deductionLines, totalDeduction),
                advances);
    }

    private static Map<Long, BigDecimal> sumByEmployee(List<EmployeeAppliedAdvanceProjection> rows) {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (EmployeeAppliedAdvanceProjection r : rows) out.put(r.getEmployeeId(), r.getAppliedAmount());
        return out;
    }

    /** Provenance of a payslip row, read from its persisted {@code created_by}. */
    public static final class PayslipProvenance {
        /** {@code created_by} written by {@code migration/06_payroll_transactions.sql}. */
        static final String LEGACY_CREATED_BY = "legacy-migration";

        private PayslipProvenance() {}

        public static boolean isLegacyImport(String createdBy) {
            return LEGACY_CREATED_BY.equals(createdBy);
        }
    }

    /**
     * The display-only advances block. Nothing here feeds any salary or net figure.
     *
     * <p>{@code appliedAmount} is the short-term advance approved for the payslip's month and the principal of the
     * employee's open permanent advances. {@code deductedAmount} is reported only when the persisted payslip
     * actually contains it, so the block can never disagree with the net: bulk-imported (legacy) payslips include
     * short-term and permanent advance deductions inside {@code payslips.deductions}, while engine-computed
     * payslips carry them as their own {@code SHORT_TERM_ADVANCE} / {@code PERMANENT_ADVANCE} lines. For legacy payslips the short-term amount is the approved advance of the
     * month and the permanent amount is the ledger amount recorded against the run; together they are capped at
     * the room {@code payslips.deductions} has left after its other recorded lines, so a displayed deduction can
     * never exceed what the persisted deductions could hold.
     */
    public static PayslipAdvances advances(PayslipDto p, boolean legacyImport, List<ShortTermAdvance> shortTermRows,
                                    BigDecimal permanentApplied, BigDecimal ledgerDeducted,
                                    List<PayslipLine> lines) {
        BigDecimal shortApplied = BigDecimal.ZERO;
        BigDecimal shortDeducted = BigDecimal.ZERO;
        for (ShortTermAdvance s : shortTermRows) {
            shortApplied = shortApplied.add(s.getAmount());
            if (legacyImport && s.getStatus() == ShortTermAdvanceStatus.APPROVED && s.getFinalSettlementId() == null) {
                shortDeducted = shortDeducted.add(s.getAmount());
            }
        }

        BigDecimal permDeducted = BigDecimal.ZERO;
        if (legacyImport) {
            BigDecimal otherRecorded = BigDecimal.ZERO;
            for (PayslipLine l : lines) {
                // INCOME_TAX mirrors payslips.tax and the legacy OTHER line is EOBI: neither is inside deductions
                if (l.getType() == ComponentType.DEDUCTION && !"INCOME_TAX".equals(l.getCode())
                        && !"OTHER".equals(l.getCode())) {
                    otherRecorded = otherRecorded.add(l.getAmount());
                }
            }
            BigDecimal room = p.deductions().subtract(otherRecorded).max(BigDecimal.ZERO);
            shortDeducted = shortDeducted.min(room);
            permDeducted = ledgerDeducted.min(room.subtract(shortDeducted));
        } else {
            shortDeducted = BigDecimal.ZERO;
            for (PayslipLine l : lines) {
                if ("SHORT_TERM_ADVANCE".equals(l.getCode())) shortDeducted = shortDeducted.add(l.getAmount());
                if ("PERMANENT_ADVANCE".equals(l.getCode())) permDeducted = permDeducted.add(l.getAmount());
            }
        }
        return new PayslipAdvances(
                new PayslipAdvanceInfo(permanentApplied, permDeducted),
                new PayslipAdvanceInfo(shortApplied, shortDeducted));
    }

    /**
     * Adds a Permanent Advance and a Short Term Advance deduction line when the payslip has none of its own.
     * Display only: {@code totalDeduction} and the net are read from the persisted columns and are untouched, so
     * these lines cannot be deducted twice.
     */
    static List<PayslipLineItem> withAdvanceLines(List<PayslipLineItem> deductionLines, PayslipAdvances a) {
        List<PayslipLineItem> out = new ArrayList<>(deductionLines);
        if (out.stream().noneMatch(i -> "PERMANENT_ADVANCE".equals(i.code()))) {
            out.add(new PayslipLineItem("PERMANENT_ADVANCE", "Permanent Advance",
                    a.permanentAdvance().deductedAmount()));
        }
        if (out.stream().noneMatch(i -> "SHORT_TERM_ADVANCE".equals(i.code()))) {
            out.add(new PayslipLineItem("SHORT_TERM_ADVANCE", "Short Term Advance",
                    a.shortTermAdvance().deductedAmount()));
        }
        return out;
    }

    // Package-private + static (touch no instance state) so tests can
    // exercise the merge / reconciliation / audit rules directly without
    // standing up the whole service and its ~15 collaborators.

    /**
     * Earnings section: {@code BASIC} and {@code OT} always appear (they are
     * their own Payslip columns), then every active EARNING catalog head in
     * catalog order with the SUM of all of this payslip's lines under that
     * code (0 when there are none), then every remaining real EARNING line
     * individually. Nothing is de-duplicated away: two lines with the same
     * code (e.g. two {@code HELD_SALARY_RELEASE} carry-overs, or a component
     * revised mid-month) each keep their amount, so the lines always add up
     * to what payroll actually paid.
     */
    static List<PayslipLineItem> legacyEarningLines(PayslipDto p, List<SalaryComponentDto> earningCatalog,
                                                    List<PayslipLine> lines) {
        return mergeLines(ComponentType.EARNING, earningCatalog, lines,
                List.of(new PayslipLineItem("BASIC", "Basic", p.basic()),
                        new PayslipLineItem("OT", "Overtime", p.otAmount())),
                List.of());
    }

    /**
     * Deductions section: every active DEDUCTION catalog head in catalog
     * order (sum of this payslip's lines under that code, else 0), then
     * {@code EOBI} (its own Payslip column), then every remaining real
     * DEDUCTION line individually. Employer-only PESSI is deliberately not
     * a deduction line.
     */
    static List<PayslipLineItem> legacyDeductionLines(PayslipDto p, List<SalaryComponentDto> deductionCatalog,
                                                      List<PayslipLine> lines) {
        return mergeLines(ComponentType.DEDUCTION, deductionCatalog, lines,
                List.of(),
                List.of(new PayslipLineItem("EOBI", "EOBI", p.eobi())));
    }

    private static List<PayslipLineItem> mergeLines(ComponentType type, List<SalaryComponentDto> catalog,
                                                    List<PayslipLine> lines,
                                                    List<PayslipLineItem> head, List<PayslipLineItem> tail) {
        Set<String> synthetic = new HashSet<>();
        head.forEach(i -> synthetic.add(i.code()));
        tail.forEach(i -> synthetic.add(i.code()));

        Map<String, BigDecimal> sumByCode = new HashMap<>();
        for (PayslipLine l : lines) {
            if (l.getType() == type && !synthetic.contains(l.getCode())) {
                sumByCode.merge(l.getCode(), l.getAmount(), BigDecimal::add);
            }
        }

        List<PayslipLineItem> out = new ArrayList<>(head);
        Set<String> catalogCodes = new HashSet<>();
        for (SalaryComponentDto c : catalog) {
            out.add(new PayslipLineItem(c.code(), c.name(), sumByCode.getOrDefault(c.code(), BigDecimal.ZERO)));
            catalogCodes.add(c.code());
        }
        out.addAll(tail);
        for (PayslipLine l : lines) {
            if (l.getType() == type && !synthetic.contains(l.getCode()) && !catalogCodes.contains(l.getCode())) {
                out.add(new PayslipLineItem(l.getCode(), l.getName(), l.getAmount()));
            }
        }
        return out;
    }

    /**
     * Ties the displayed lines back to the persisted totals. Differences are
     * reported in {@code notes}, never corrected.
     */
    static PayslipReconciliation reconcile(PayslipDto p, List<PayslipLineItem> earningLines,
                                           List<PayslipLineItem> deductionLines, BigDecimal totalDeduction) {
        BigDecimal earnSum = sum(earningLines);
        BigDecimal dedSum = sum(deductionLines);
        BigDecimal earnDiff = p.gross().subtract(earnSum);
        BigDecimal dedDiff = totalDeduction.subtract(dedSum);
        BigDecimal netExpected = p.gross().subtract(totalDeduction);
        BigDecimal netDiff = p.net().subtract(netExpected);

        List<String> notes = new ArrayList<>();
        if (earnDiff.signum() != 0) {
            notes.add("earning lines total " + earnSum.toPlainString() + " but persisted gross is "
                    + p.gross().toPlainString() + " (difference " + earnDiff.toPlainString() + ")");
        }
        if (dedDiff.signum() != 0) {
            notes.add("deduction lines total " + dedSum.toPlainString() + " but persisted total deduction is "
                    + totalDeduction.toPlainString() + " (difference " + dedDiff.toPlainString()
                    + ") - the line-level breakdown is incomplete for this payslip");
        }
        if (netDiff.signum() != 0) {
            notes.add("persisted net " + p.net().toPlainString() + " differs from gross - total deduction "
                    + netExpected.toPlainString() + " (difference " + netDiff.toPlainString() + ")");
        }
        return new PayslipReconciliation(earnSum, earnDiff, dedSum, dedDiff, netExpected, netDiff,
                notes.isEmpty(), notes);
    }

    private static BigDecimal sum(List<PayslipLineItem> items) {
        return items.stream().map(PayslipLineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Attendance for the payroll period as derived by attendance-service (raw attendance records, holiday
     * calendar, approved leave, joining date). {@code preJoiningDays} is intentionally not exposed. Null when
     * attendance-service did not answer, so the row degrades instead of failing.
     */
    static PayslipAttendanceInfo attendanceInfo(PayrollAttendanceAuditDto a) {
        if (a == null) return null;
        return new PayslipAttendanceInfo(
                a.weeklyOffDays(), a.holidayDays(), a.workingDays(),
                a.presentDays(), a.paidLeaveDays(), a.unpaidLeaveDays(),
                a.absentDays(), a.unrecordedAbsentDays(),
                a.halfDayRecords(), a.presentOnOffDays(), a.leaveConflictDays(),
                a.lopDays(), a.paidDays());
    }

    static PayslipLeaveInfo leaveInfo(EmployeeLeavePayslipSummaryDto s) {
        if (s == null) return null;
        List<PayslipLeaveLine> leaves = s.leaves() == null ? List.of() : s.leaves().stream()
                .map(l -> new PayslipLeaveLine(l.code(), l.name(), l.availedThisMonth(), l.availedThisCycle()))
                .toList();
        PayslipAnnualLeaveInfo annual = s.annualLeave() == null ? null
                : new PayslipAnnualLeaveInfo(s.annualLeave().total(), s.annualLeave().availed(),
                        s.annualLeave().paid(), s.annualLeave().balance());
        return new PayslipLeaveInfo(leaves, annual);
    }

    // ===== /payroll/runs/with-summary?fiscalYear= =====

    public PayrollRunsListResponse runsForFiscalYear(String fiscalYearCode) {
        List<PayrollRunDto> rows = runs.listByFiscalYear(fiscalYearCode);
        if (rows.isEmpty()) return new PayrollRunsListResponse(fiscalYearCode, List.of());

        List<PayrollRunsListItem> items = new ArrayList<>(rows.size());
        for (PayrollRunDto r : rows) {
            items.add(new PayrollRunsListItem(r, summary(runs.totals(r.id(), null, null))));
        }
        return new PayrollRunsListResponse(fiscalYearCode, items);
    }

    // ===== /salary/increment-batches/{id}/detail =====

    public IncrementBatchDetail incrementBatch(long id) {
        IncrementBatchDto b = incrementBatches.get(id);
        List<IncrementBatchLineDto> lines = b.lines() == null ? List.of() : b.lines();
        Map<Long, EmployeeSummary> idx = resolveEmployees(
                lines.stream().map(IncrementBatchLineDto::employeeId).toList());

        List<IncrementBatchLineWithEmployee> resolved = lines.stream()
                .map(l -> new IncrementBatchLineWithEmployee(
                        l.id(),
                        idx.getOrDefault(l.employeeId(), missing(l.employeeId(), null)),
                        l.oldBasic(), l.newBasic(), l.oldIncomeTax(), l.newIncomeTax(),
                        l.effectiveFrom(), l.status() == null ? null : l.status().name(), l.errorMessage()))
                .toList();
        return new IncrementBatchDetail(b.id(), b.name(),
                b.status() == null ? null : b.status().name(), b.effectiveFrom(),
                b.notes(), b.appliedAt(), b.appliedBy(), resolved);
    }

    // ===== /bonuses/events/{id}/detail =====

    public BonusEventDetail bonusEvent(long id) {
        BonusEventDto e = bonusEvents.get(id);
        List<BonusEventAmountDto> amounts = e.amounts() == null ? List.of() : e.amounts();
        Map<Long, EmployeeSummary> idx = resolveEmployees(
                amounts.stream().map(BonusEventAmountDto::employeeId).toList());

        List<BonusEventAmountWithEmployee> resolved = amounts.stream()
                .map(a -> new BonusEventAmountWithEmployee(
                        a.id(),
                        idx.getOrDefault(a.employeeId(), missing(a.employeeId(), null)),
                        a.hireDate(), a.serviceDays(), a.basicSnapshot(), a.fullBonus()))
                .toList();
        return new BonusEventDetail(e.id(), e.fiscalYearCode(), e.name(),
                e.eventType() == null ? null : e.eventType().name(),
                e.referenceDate(), e.fullServiceDaysThreshold(),
                e.status() == null ? null : e.status().name(), e.notes(),
                e.activatedAt(), e.activatedBy(),
                e.installments() == null ? List.of() : e.installments(),
                resolved);
    }

    // ===== /off-cycle-payments/with-employee =====

    public PageResponse<OffCyclePaymentRowWithEmployee> offCyclePaymentsWithEmployee(
            Long employeeId, LocalDate from, LocalDate to,
            OffCyclePaymentSource source, Pageable pageable) {
        PageResponse<OffCyclePaymentDto> page =
                offCyclePayments.filter(employeeId, from, to, source, null, pageable);
        List<OffCyclePaymentDto> rows = page.content();
        Map<Long, EmployeeSummary> idx = resolveEmployees(
                rows.stream().map(OffCyclePaymentDto::employeeId).toList());

        List<OffCyclePaymentRowWithEmployee> enriched = rows.stream()
                .map(p -> new OffCyclePaymentRowWithEmployee(
                        p.id(),
                        idx.getOrDefault(p.employeeId(), missing(p.employeeId(), null)),
                        p.paidOn(), p.amount(),
                        p.reason(),
                        p.paymentMode() == null ? null : p.paymentMode().name(),
                        p.referenceNo(), p.sourceEventId(), p.sourceInstallmentId(),
                        p.source() == null ? null : p.source().name(),
                        p.offCyclePaymentTypeId(), p.offCyclePaymentTypeName(),
                        p.status() == null ? null : p.status().name()))
                .toList();
        return new PageResponse<>(enriched, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.sort());
    }

    // ===== /people/{id}/salary =====

    public EmployeeSalaryTab salaryTab(long employeeId) {
        LocalDate today = LocalDate.now();
        LocalDate yearAgo = today.minusYears(1);
        return new EmployeeSalaryTab(
                employeeId,
                orNull(() -> salaries.current(employeeId)),
                safeList(() -> salaries.history(employeeId)),
                safeList(() -> salaryComponents.listCurrent(employeeId, null)),
                components.list(null, true),
                orNull(() -> settings.get(employeeId)),
                orNull(() -> bankDetails.get(employeeId)),
                orNull(() -> gratuity.compute(employeeId, today)),
                safeList(() -> offCyclePayments.listForEmployee(employeeId, yearAgo, today)));
    }

    // ===== /payslips/queue?status= =====

    public PayslipQueueResponse payslipQueue(PayslipStatus status, Pageable pageable) {
        PageResponse<PayslipDto> page = payslips.filterByStatus(status, pageable);
        List<PayslipDto> rows = page.content();
        Map<Long, EmployeeSummary> idx = resolveEmployees(
                rows.stream().map(PayslipDto::employeeId).toList());

        List<PayslipQueueRow> enriched = rows.stream()
                .map(p -> new PayslipQueueRow(
                        p.id(),
                        idx.getOrDefault(p.employeeId(), missing(p.employeeId(), p.employeeCode())),
                        p.periodYear(), p.periodMonth(), p.fiscalYearCode(),
                        p.net(), p.status() == null ? null : p.status().name(),
                        p.heldReason(), p.generatedAt()))
                .toList();
        PageResponse<PayslipQueueRow> body = new PageResponse<>(enriched, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.sort());
        return new PayslipQueueResponse(status == null ? null : status.name(), body);
    }

    // ===== shared helpers =====

    private List<MyPayslipRow> mintPdfUrlsAndMap(List<PayslipDto> rows) {
        if (rows.isEmpty()) return List.of();
        List<MyPayslipRow> out = new ArrayList<>(rows.size());
        for (PayslipDto p : rows) {
            String url = null;
            if (p.pdfObjectKey() != null) {
                try {
                    PdfUrlResponse u = payslips.pdfUrl(p.id());
                    url = u == null ? null : u.url();
                } catch (NotFoundError ignored) { /* missing PDF — leave null */ }
            }
            out.add(MyPayslipRow.of(p, url));
        }
        return out;
    }

    private static PayrollRunSummary summary(PayrollRunTotalsDto t) {
        return new PayrollRunSummary(
                (int) t.employeeCount(),
                t.basic(), t.allowances(),
                t.gross(), t.deductions(), t.tax(), t.eobi(), t.pessiEmployer(), t.net(),
                (int) t.heldCount(), (int) t.releasePendingCount());
    }

    private Map<Long, EmployeeSummary> resolveEmployees(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Set<Long> distinct = new LinkedHashSet<>(ids);
        Map<Long, EmployeeDisplay> hits = displayCache.getMany(distinct);
        Map<Long, EmployeeSummary> out = new HashMap<>(hits.size());
        for (var en : hits.entrySet()) out.put(en.getKey(), EmployeeSummaryMapper.toSummary(en.getValue()));
        return out;
    }

    private static EmployeeSummary missing(long id, String employeeCode) {
        return EmployeeSummary.placeholder(id, employeeCode);
    }

    private static <T> T orNull(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (NotFoundError ex) {
            return null;
        }
    }

    private static <T> List<T> safeList(Supplier<List<T>> supplier) {
        try {
            return supplier.get();
        } catch (NotFoundError ex) {
            return List.of();
        }
    }

}

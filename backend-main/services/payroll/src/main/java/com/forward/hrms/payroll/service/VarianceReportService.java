package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;

import lombok.RequiredArgsConstructor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Variance vs previous run — every employee whose net (or any line item)
 * differs from the prior run, with per-component delta. The "prior" run
 * is either the caller-supplied id or the immediate predecessor on the
 * same fiscal year. Output groups by employee and lists only the
 * components that changed plus the totals row.
 */
@Service
@RequiredArgsConstructor
public class VarianceReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final PayslipLineRepository lines;
    private final EmployeeClient employees;
    private final AuditEmitter audit;

    @Transactional
    public void writeXlsx(OutputStream out, long currentRunId, Long priorRunId) throws IOException {
        PayrollRun current = runs.findById(currentRunId).orElseThrow(() ->
                new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND, "current run not found: " + currentRunId));
        PayrollRun prior = priorRunId == null
                ? findPriorRun(current)
                : runs.findById(priorRunId).orElseThrow(() ->
                        new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND, "prior run not found: " + priorRunId));
        if (prior.getId().equals(current.getId())) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "current and prior runs must differ");
        }

        Map<Long, Payslip> currentByEmp = byEmployee(payslips.findAllByPayrollRunId(current.getId(), Pageable.unpaged()).getContent());
        Map<Long, Payslip> priorByEmp = byEmployee(payslips.findAllByPayrollRunId(prior.getId(), Pageable.unpaged()).getContent());
        Set<Long> allIds = new HashSet<>();
        allIds.addAll(currentByEmp.keySet());
        allIds.addAll(priorByEmp.keySet());

        Set<Long> currentPayslipIds = collect(currentByEmp.values(), Payslip::getId);
        Set<Long> priorPayslipIds = collect(priorByEmp.values(), Payslip::getId);
        Map<Long, List<PayslipLine>> currentLines = groupLines(currentPayslipIds);
        Map<Long, List<PayslipLine>> priorLines = groupLines(priorPayslipIds);
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(allIds);

        BigDecimal grandDelta = BigDecimal.ZERO;
        int variedEmployees = 0;

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            SXSSFSheet sheet = wb.createSheet("Variance");
            Styles s = Styles.create(wb);

            int idx = 0;
            Row title = sheet.createRow(idx++);
            cell(title, 0,
                    "Variance — current run #%d (%d-%02d) vs prior run #%d (%d-%02d)".formatted(
                            current.getId(), current.getPeriodYear(), current.getPeriodMonth(),
                            prior.getId(), prior.getPeriodYear(), prior.getPeriodMonth()),
                    s.title);
            idx++;

            Row header = sheet.createRow(idx++);
            cell(header, 0, "Employee Code", s.header);
            cell(header, 1, "Name", s.header);
            cell(header, 2, "Component", s.header);
            cell(header, 3, "Prior", s.header);
            cell(header, 4, "Current", s.header);
            cell(header, 5, "Delta", s.header);

            List<Long> employeeIds = allIds.stream().sorted().toList();
            for (Long id : employeeIds) {
                Payslip cp = currentByEmp.get(id);
                Payslip pp = priorByEmp.get(id);

                Map<String, BigDecimal> currentByCode = sumByCode(currentLines.getOrDefault(cp == null ? -1L : cp.getId(), List.of()));
                Map<String, BigDecimal> priorByCode = sumByCode(priorLines.getOrDefault(pp == null ? -1L : pp.getId(), List.of()));
                addStandardComponents(currentByCode, cp);
                addStandardComponents(priorByCode, pp);

                Set<String> codes = new TreeSet<>();
                codes.addAll(currentByCode.keySet());
                codes.addAll(priorByCode.keySet());

                List<DiffRow> diffs = new ArrayList<>();
                for (String code : codes) {
                    BigDecimal a = priorByCode.getOrDefault(code, ZERO);
                    BigDecimal b = currentByCode.getOrDefault(code, ZERO);
                    BigDecimal delta = b.subtract(a);
                    if (delta.signum() != 0) {
                        diffs.add(new DiffRow(code, a, b, delta));
                    }
                }
                if (diffs.isEmpty()) continue;

                EmployeeClient.EmployeeBrief brief = briefs.getOrDefault(id, EmployeeClient.EmployeeBrief.placeholder(id));
                String code = cp != null ? cp.getEmployeeCode()
                        : pp != null ? pp.getEmployeeCode()
                        : brief.employeeCode();
                variedEmployees++;
                BigDecimal employeeDelta = ZERO;
                for (DiffRow d : diffs) {
                    Row r = sheet.createRow(idx++);
                    cell(r, 0, code, s.text);
                    cell(r, 1, brief.fullName(), s.text);
                    cell(r, 2, d.code, s.text);
                    money(r, 3, d.prior, s.money);
                    money(r, 4, d.current, s.money);
                    money(r, 5, d.delta, s.money);
                    employeeDelta = employeeDelta.add(d.delta);
                }
                grandDelta = grandDelta.add(employeeDelta);
            }

            sheet.setColumnWidth(0, 4500);
            sheet.setColumnWidth(1, 8000);
            sheet.setColumnWidth(2, 5500);
            sheet.setColumnWidth(3, 4500);
            sheet.setColumnWidth(4, 4500);
            sheet.setColumnWidth(5, 4500);

            wb.write(out);
            wb.dispose();
        }

        audit.emit("report", "0", "variance.run", null,
                Map.of("currentRunId", currentRunId,
                        "priorRunId", prior.getId(),
                        "variedEmployees", variedEmployees,
                        "grandDelta", grandDelta),
                false);
    }

    private PayrollRun findPriorRun(PayrollRun current) {
        return runs.findAllByFiscalYearCodeOrderByPeriodYearDescPeriodMonthDesc(current.getFiscalYearCode())
                .stream()
                .filter(r -> !r.getId().equals(current.getId()))
                .filter(r -> r.getPeriodYear() < current.getPeriodYear()
                        || (r.getPeriodYear() == current.getPeriodYear()
                                && r.getPeriodMonth() < current.getPeriodMonth()))
                .findFirst()
                .orElseThrow(() -> new ConflictError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND,
                        "no prior run in fiscal year " + current.getFiscalYearCode()));
    }

    private Map<Long, Payslip> byEmployee(List<Payslip> all) {
        Map<Long, Payslip> out = new HashMap<>(all.size());
        for (Payslip p : all) out.put(p.getEmployeeId(), p);
        return out;
    }

    private static <T> Set<Long> collect(java.util.Collection<T> rows, java.util.function.Function<T, Long> idFn) {
        Set<Long> ids = new HashSet<>(rows.size());
        for (T t : rows) ids.add(idFn.apply(t));
        return ids;
    }

    private Map<Long, List<PayslipLine>> groupLines(Set<Long> payslipIds) {
        if (payslipIds.isEmpty()) return Map.of();
        Map<Long, List<PayslipLine>> out = new HashMap<>(payslipIds.size());
        for (PayslipLine l : lines.findAllByPayslipIdInOrderByPayslipIdAscDisplayOrderAsc(payslipIds)) {
            out.computeIfAbsent(l.getPayslipId(), k -> new ArrayList<>()).add(l);
        }
        return out;
    }

    private static Map<String, BigDecimal> sumByCode(List<PayslipLine> ls) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (PayslipLine l : ls) {
            out.merge(l.getCode(), l.getAmount(), BigDecimal::add);
        }
        return out;
    }

    /**
     * Add the totals (basic / gross / tax / EOBI / PESSI / net) so the
     * variance picks up changes that aren't a component line — e.g. a
     * basic-salary increment that flows to gross + tax + net.
     */
    private static void addStandardComponents(Map<String, BigDecimal> map, Payslip p) {
        if (p == null) return;
        map.merge("__BASIC__", orZero(p.getBasic()), BigDecimal::add);
        map.merge("__GROSS__", orZero(p.getGross()), BigDecimal::add);
        map.merge("__TAX__", orZero(p.getTax()), BigDecimal::add);
        map.merge("__EOBI__", orZero(p.getEobi()), BigDecimal::add);
        map.merge("__PESSI_EMP__", orZero(p.getPessiEmployer()), BigDecimal::add);
        map.merge("__NET__", orZero(p.getNet()), BigDecimal::add);
    }

    private static BigDecimal orZero(BigDecimal v) { return v == null ? ZERO : v; }

    private record DiffRow(String code, BigDecimal prior, BigDecimal current, BigDecimal delta) {}

    private static void cell(Row r, int idx, String value, CellStyle style) {
        Cell c = r.createCell(idx);
        c.setCellValue(value == null ? "" : value);
        if (style != null) c.setCellStyle(style);
    }

    private static void money(Row r, int idx, BigDecimal value, CellStyle style) {
        Cell c = r.createCell(idx);
        if (value != null) c.setCellValue(value.doubleValue());
        if (style != null) c.setCellStyle(style);
    }

    private record Styles(CellStyle title, CellStyle header, CellStyle text, CellStyle money) {
        static Styles create(SXSSFWorkbook wb) {
            DataFormat fmt = wb.createDataFormat();
            short moneyFmt = fmt.getFormat("#,##0.00");

            Font bold = wb.createFont(); bold.setBold(true);
            Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 12);

            CellStyle title = wb.createCellStyle(); title.setFont(titleFont);
            CellStyle header = wb.createCellStyle(); header.setFont(bold);
            CellStyle text = wb.createCellStyle();
            CellStyle money = wb.createCellStyle(); money.setDataFormat(moneyFmt);
            return new Styles(title, header, text, money);
        }
    }
}

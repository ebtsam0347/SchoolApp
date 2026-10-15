package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.FiscalYearRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
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

import java.io.ByteArrayOutputStream;
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

/**
 * Income-tax deducted report — per employee, period total + fiscal-year
 * cumulative. Period defaults to the caller-supplied (year, month) range
 * inside one fiscal year; cumulative reads every payroll run from the
 * fiscal year's start through the period end.
 *
 * <p>Excel-only because finance pivot-tables this; if a PDF is later
 * needed for archive, add a Jasper template that reads the same row
 * shape.
 */
@Service
@RequiredArgsConstructor
public class IncomeTaxDeductedReportService {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final FiscalYearRepository fiscalYears;
    private final EmployeeClient employees;
    private final AuditEmitter audit;

    /**
     * Stream the report to {@code out}. Period is inclusive; both bounds
     * must lie inside the same fiscal year (ENG-side guard, not a
     * regulatory rule — keeps the cumulative arithmetic well-defined).
     */
    @Transactional
    public void writeXlsx(OutputStream out, int fromYear, int fromMonth, int toYear, int toMonth) throws IOException {
        validateRange(fromYear, fromMonth, toYear, toMonth);

        FiscalYear fy = pickFiscalYear(fromYear, fromMonth, toYear, toMonth);
        List<PayrollRun> fyRuns = runs.findAllByFiscalYearCodeOrderByPeriodYearDescPeriodMonthDesc(fy.getCode());

        Set<Long> selectedRunIds = new HashSet<>();
        Set<Long> cumulativeRunIds = new HashSet<>();
        for (PayrollRun r : fyRuns) {
            int rk = key(r.getPeriodYear(), r.getPeriodMonth());
            int from = key(fromYear, fromMonth);
            int to = key(toYear, toMonth);
            if (rk >= from && rk <= to) selectedRunIds.add(r.getId());
            if (rk <= to) cumulativeRunIds.add(r.getId());
        }

        Map<Long, Aggregate> byEmployee = new HashMap<>();
        for (PayrollRun r : fyRuns) {
            if (!cumulativeRunIds.contains(r.getId())) continue;
            boolean inPeriod = selectedRunIds.contains(r.getId());
            for (Payslip p : payslips.findAllByPayrollRunId(r.getId(), Pageable.unpaged()).getContent()) {
                Aggregate a = byEmployee.computeIfAbsent(p.getEmployeeId(),
                        id -> new Aggregate(id, p.getEmployeeCode(), BigDecimal.ZERO, BigDecimal.ZERO));
                BigDecimal tax = p.getTax() == null ? BigDecimal.ZERO : p.getTax();
                a.cumulative = a.cumulative.add(tax);
                if (inPeriod) a.period = a.period.add(tax);
                a.code = p.getEmployeeCode(); // refresh from latest payslip
            }
        }

        List<Aggregate> ordered = byEmployee.values().stream()
                .filter(a -> a.cumulative.signum() != 0 || a.period.signum() != 0)
                .sorted(Comparator.comparing(Aggregate::code))
                .toList();

        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(
                ordered.stream().map(Aggregate::employeeId).toList());

        BigDecimal totalPeriod = BigDecimal.ZERO;
        BigDecimal totalCumulative = BigDecimal.ZERO;

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            SXSSFSheet sheet = wb.createSheet("Income tax deducted");
            Styles styles = Styles.create(wb);

            int rowIdx = 0;
            writeTitle(sheet, rowIdx++, fy, fromYear, fromMonth, toYear, toMonth, styles);
            rowIdx++;
            writeHeader(sheet, rowIdx++, styles);

            int serial = 0;
            for (Aggregate a : ordered) {
                EmployeeClient.EmployeeBrief b = briefs.getOrDefault(a.employeeId(),
                        EmployeeClient.EmployeeBrief.placeholder(a.employeeId()));
                Row r = sheet.createRow(rowIdx++);
                int c = 0;
                cell(r, c++, ++serial, styles.intCell);
                cell(r, c++, a.code, styles.text);
                cell(r, c++, b.fullName(), styles.text);
                cell(r, c++, nullToBlank(b.cnic()), styles.text);
                cell(r, c++, nullToBlank(b.ntn()), styles.text);
                money(r, c++, a.period, styles.money);
                money(r, c, a.cumulative, styles.money);
                totalPeriod = totalPeriod.add(a.period);
                totalCumulative = totalCumulative.add(a.cumulative);
            }
            writeFooter(sheet, rowIdx, totalPeriod, totalCumulative, styles);

            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 4500);
            sheet.setColumnWidth(2, 8000);
            sheet.setColumnWidth(3, 5000);
            sheet.setColumnWidth(4, 4500);
            sheet.setColumnWidth(5, 4500);
            sheet.setColumnWidth(6, 4500);

            wb.write(out);
            wb.dispose();
        }

        audit.emit("report", "0", "income-tax-deducted.run", null,
                Map.of("fiscalYear", fy.getCode(),
                        "fromYear", fromYear, "fromMonth", fromMonth,
                        "toYear", toYear, "toMonth", toMonth,
                        "rowCount", ordered.size(),
                        "totalPeriodTax", totalPeriod,
                        "totalCumulativeTax", totalCumulative),
                true);
    }

    // ----- range helpers -----

    private void validateRange(int fromYear, int fromMonth, int toYear, int toMonth) {
        if (fromYear < 2000 || toYear < 2000) {
            throw new ConflictError(PayrollErrorCode.INVALID_PERIOD, "year must be ≥ 2000");
        }
        if (fromMonth < 1 || fromMonth > 12 || toMonth < 1 || toMonth > 12) {
            throw new ConflictError(PayrollErrorCode.INVALID_PERIOD, "month must be 1..12");
        }
        if (key(fromYear, fromMonth) > key(toYear, toMonth)) {
            throw new ConflictError(PayrollErrorCode.INVALID_PERIOD,
                    "from must be ≤ to (got " + fromYear + "-" + fromMonth + " > "
                            + toYear + "-" + toMonth + ")");
        }
    }

    private FiscalYear pickFiscalYear(int fromYear, int fromMonth, int toYear, int toMonth) {
        FiscalYear from = fyForPeriod(fromYear, fromMonth);
        FiscalYear to = fyForPeriod(toYear, toMonth);
        if (!from.getCode().equals(to.getCode())) {
            throw new ConflictError(PayrollErrorCode.INVALID_PERIOD,
                    "from and to must lie in the same fiscal year (got "
                            + from.getCode() + " vs " + to.getCode() + ")");
        }
        return from;
    }

    private FiscalYear fyForPeriod(int year, int month) {
        // Mid-month (15th) sits comfortably inside whichever fiscal year
        // the period belongs to — start-of-month at the boundary would
        // miss a row whose end_date is the prior month's last day.
        java.time.LocalDate sample = java.time.LocalDate.of(year, month, 15);
        return fiscalYears.findCovering(sample).orElseThrow(() ->
                new ConflictError(PayrollErrorCode.FISCAL_YEAR_NOT_FOUND,
                        "no fiscal year row covering " + year + "-" + month));
    }

    private static int key(int year, int month) { return year * 12 + (month - 1); }

    private static String nullToBlank(String s) { return s == null ? "" : s; }

    // ----- writing -----

    private void writeTitle(SXSSFSheet sheet, int idx, FiscalYear fy,
                            int fromYear, int fromMonth, int toYear, int toMonth, Styles s) {
        Row r = sheet.createRow(idx);
        cell(r, 0,
                "Income tax deducted — %d-%02d to %d-%02d (FY %s)".formatted(
                        fromYear, fromMonth, toYear, toMonth, fy.getCode()),
                s.title);
    }

    private void writeHeader(SXSSFSheet sheet, int idx, Styles s) {
        Row r = sheet.createRow(idx);
        cell(r, 0, "S.No", s.header);
        cell(r, 1, "Employee Code", s.header);
        cell(r, 2, "Name", s.header);
        cell(r, 3, "CNIC", s.header);
        cell(r, 4, "NTN", s.header);
        cell(r, 5, "Tax (period)", s.header);
        cell(r, 6, "Tax (cumulative FY)", s.header);
    }

    private void writeFooter(SXSSFSheet sheet, int idx, BigDecimal totalPeriod, BigDecimal totalCumulative, Styles s) {
        Row r = sheet.createRow(idx);
        cell(r, 0, "TOTAL", s.footerText);
        money(r, 5, totalPeriod, s.footerMoney);
        money(r, 6, totalCumulative, s.footerMoney);
    }

    private static void cell(Row r, int idx, String value, CellStyle style) {
        Cell c = r.createCell(idx);
        c.setCellValue(value == null ? "" : value);
        if (style != null) c.setCellStyle(style);
    }

    private static void cell(Row r, int idx, int value, CellStyle style) {
        Cell c = r.createCell(idx);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private static void money(Row r, int idx, BigDecimal value, CellStyle style) {
        Cell c = r.createCell(idx);
        if (value != null) c.setCellValue(value.doubleValue());
        if (style != null) c.setCellStyle(style);
    }

    private static final class Aggregate {
        final long employeeId;
        String code;
        BigDecimal period;
        BigDecimal cumulative;

        Aggregate(long employeeId, String code, BigDecimal period, BigDecimal cumulative) {
            this.employeeId = employeeId;
            this.code = code;
            this.period = period;
            this.cumulative = cumulative;
        }

        long employeeId() { return employeeId; }
        String code() { return code; }
    }

    private record Styles(CellStyle title, CellStyle header, CellStyle text,
                          CellStyle intCell, CellStyle money,
                          CellStyle footerText, CellStyle footerMoney) {
        static Styles create(SXSSFWorkbook wb) {
            DataFormat fmt = wb.createDataFormat();
            short moneyFmt = fmt.getFormat("#,##0.00");

            Font bold = wb.createFont();
            bold.setBold(true);

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);

            CellStyle title = wb.createCellStyle();
            title.setFont(titleFont);

            CellStyle header = wb.createCellStyle();
            header.setFont(bold);

            CellStyle text = wb.createCellStyle();
            CellStyle intCell = wb.createCellStyle();
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(moneyFmt);
            CellStyle footerText = wb.createCellStyle();
            footerText.setFont(bold);
            CellStyle footerMoney = wb.createCellStyle();
            footerMoney.setFont(bold);
            footerMoney.setDataFormat(moneyFmt);

            return new Styles(title, header, text, intCell, money, footerText, footerMoney);
        }
    }
}

package com.forward.hrms.payroll.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.entity.ReportJob;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pay-register report — every payslip in a pay run, every component as a
 * column, footer row with column totals. Async because a 600-employee
 * register × 15 components is enough rendering work to risk holding a
 * Hikari connection.
 *
 * <p>Output sits at {@code reports/payroll/pay-register/<run-id>/<job-id>.xlsx}.
 * Streamed via {@link SXSSFWorkbook} with a 200-row sliding window so a
 * 5000-employee run still fits the 1.5g heap.
 */
@Slf4j
@Service
public class PayRegisterReportService implements ReportJobHandler {

    public static final String TYPE = "PAY_REGISTER";

    private static final String BUCKET = "reports";

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final PayslipLineRepository lines;
    private final EmployeeClient employees;
    private final ObjectStorage storage;
    private final ObjectMapper json;

    public PayRegisterReportService(PayrollRunRepository runs,
                                    PayslipRepository payslips,
                                    PayslipLineRepository lines,
                                    EmployeeClient employees,
                                    ObjectStorage storage,
                                    @Qualifier("outboxObjectMapper") ObjectMapper json) {
        this.runs = runs;
        this.payslips = payslips;
        this.lines = lines;
        this.employees = employees;
        this.storage = storage;
        this.json = json;
    }

    @Override public String type() { return TYPE; }

    @Override
    public Output handle(ReportJob job) {
        long runId = parseRunId(job);
        byte[] bytes = renderForRun(runId);
        String key = "payroll/pay-register/%d/%d.xlsx".formatted(runId, job.getId());
        storage.upload(BUCKET, key,
                new ByteArrayInputStream(bytes), bytes.length,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null);
        return new Output(key, bytes.length);
    }

    @Transactional(readOnly = true)
    public byte[] renderForRun(long runId) {
        PayrollRun run = runs.findById(runId).orElseThrow(() ->
                new IllegalStateException("payroll run not found: " + runId));
        List<Payslip> all = payslips.findAllByPayrollRunId(runId, Pageable.unpaged()).getContent();

        Set<Long> payslipIds = new HashSet<>(all.size());
        Set<Long> employeeIds = new HashSet<>(all.size());
        for (Payslip p : all) {
            payslipIds.add(p.getId());
            employeeIds.add(p.getEmployeeId());
        }
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(employeeIds);
        Map<Long, List<PayslipLine>> linesByPayslip = groupLines(payslipIds);

        // Stable column order: union of component codes across all lines,
        // partitioned earnings-first then deductions, alphabetised within
        // each bucket so different runs come out comparable.
        ColumnOrder cols = buildColumnOrder(linesByPayslip.values());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            wb.setCompressTempFiles(true);
            SXSSFSheet sheet = wb.createSheet("Pay register");
            Styles styles = Styles.create(wb);

            int rowIdx = 0;
            writeTitleRow(sheet, rowIdx++, run, styles);
            rowIdx++;
            writeHeaderRow(sheet, rowIdx++, cols, styles);

            BigDecimal[] totals = new BigDecimal[7 + cols.totalSize()];
            for (int i = 0; i < totals.length; i++) totals[i] = BigDecimal.ZERO;

            for (Payslip p : all) {
                EmployeeClient.EmployeeBrief b = briefs.getOrDefault(p.getEmployeeId(),
                        EmployeeClient.EmployeeBrief.placeholder(p.getEmployeeId()));
                List<PayslipLine> ls = linesByPayslip.getOrDefault(p.getId(), List.of());
                writeBodyRow(sheet, rowIdx++, p, b, ls, cols, styles, totals);
            }
            writeFooterRow(sheet, rowIdx, cols, styles, totals);

            sheet.setColumnWidth(0, 4500);
            sheet.setColumnWidth(1, 8000);

            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Pay register Excel render failed for run " + runId, ex);
        }
    }

    private long parseRunId(ReportJob job) {
        try {
            JsonNode parsed = json.readTree(job.getParams());
            JsonNode id = parsed.get("payrollRunId");
            if (id == null || !id.canConvertToLong()) {
                throw new IllegalArgumentException(
                        "PAY_REGISTER params missing payrollRunId (got " + job.getParams() + ")");
            }
            return id.asLong();
        } catch (IOException ex) {
            throw new IllegalArgumentException("PAY_REGISTER params not parseable: " + job.getParams(), ex);
        }
    }

    private Map<Long, List<PayslipLine>> groupLines(Collection<Long> payslipIds) {
        if (payslipIds.isEmpty()) return Map.of();
        Map<Long, List<PayslipLine>> out = new HashMap<>(payslipIds.size());
        for (PayslipLine l : lines.findAllByPayslipIdInOrderByPayslipIdAscDisplayOrderAsc(payslipIds)) {
            out.computeIfAbsent(l.getPayslipId(), k -> new ArrayList<>()).add(l);
        }
        return out;
    }

    private ColumnOrder buildColumnOrder(Collection<List<PayslipLine>> all) {
        Map<String, String> earningsByCode = new LinkedHashMap<>();
        Map<String, String> deductionsByCode = new LinkedHashMap<>();
        for (List<PayslipLine> ls : all) {
            for (PayslipLine l : ls) {
                Map<String, String> bucket = l.getType() == ComponentType.EARNING ? earningsByCode : deductionsByCode;
                bucket.putIfAbsent(l.getCode(), l.getName());
            }
        }
        List<String[]> earnings = sortedEntries(earningsByCode);
        List<String[]> deductions = sortedEntries(deductionsByCode);
        return new ColumnOrder(earnings, deductions);
    }

    private static List<String[]> sortedEntries(Map<String, String> by) {
        return by.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new String[]{e.getKey(), e.getValue()})
                .toList();
    }

    // ----- writing -----

    private void writeTitleRow(SXSSFSheet sheet, int idx, PayrollRun run, Styles s) {
        Row r = sheet.createRow(idx);
        cell(r, 0, "Pay register — %d-%02d (FY %s, run #%d)".formatted(
                run.getPeriodYear(), run.getPeriodMonth(), run.getFiscalYearCode(), run.getId()), s.title);
    }

    private void writeHeaderRow(SXSSFSheet sheet, int idx, ColumnOrder cols, Styles s) {
        Row r = sheet.createRow(idx);
        int c = 0;
        cell(r, c++, "Employee Code", s.header);
        cell(r, c++, "Employee Name", s.header);
        cell(r, c++, "Basic", s.header);
        for (String[] code : cols.earnings) cell(r, c++, code[1], s.header);
        cell(r, c++, "Gross", s.header);
        for (String[] code : cols.deductions) cell(r, c++, code[1], s.header);
        cell(r, c++, "Tax", s.header);
        cell(r, c++, "EOBI", s.header);
        cell(r, c++, "PESSI (employer)", s.header);
        cell(r, c, "Net", s.header);
    }

    private void writeBodyRow(SXSSFSheet sheet, int idx, Payslip p,
                              EmployeeClient.EmployeeBrief brief, List<PayslipLine> ls,
                              ColumnOrder cols, Styles s, BigDecimal[] totals) {
        Row r = sheet.createRow(idx);
        Map<String, BigDecimal> byCode = new HashMap<>();
        for (PayslipLine l : ls) byCode.merge(l.getCode(), l.getAmount(), BigDecimal::add);
        int c = 0;
        cell(r, c++, p.getEmployeeCode(), s.text);
        cell(r, c++, brief.fullName(), s.text);
        money(r, c, p.getBasic(), s.money); totals[0] = totals[0].add(orZero(p.getBasic())); c++;
        int t = 7;
        for (String[] code : cols.earnings) {
            BigDecimal v = byCode.get(code[0]);
            money(r, c, v, s.money);
            totals[t] = totals[t].add(orZero(v));
            c++; t++;
        }
        money(r, c, p.getGross(), s.money); totals[1] = totals[1].add(orZero(p.getGross())); c++;
        for (String[] code : cols.deductions) {
            BigDecimal v = byCode.get(code[0]);
            money(r, c, v, s.money);
            totals[t] = totals[t].add(orZero(v));
            c++; t++;
        }
        money(r, c, p.getTax(), s.money); totals[2] = totals[2].add(orZero(p.getTax())); c++;
        money(r, c, p.getEobi(), s.money); totals[3] = totals[3].add(orZero(p.getEobi())); c++;
        money(r, c, p.getPessiEmployer(), s.money); totals[4] = totals[4].add(orZero(p.getPessiEmployer())); c++;
        money(r, c, p.getNet(), s.money); totals[5] = totals[5].add(orZero(p.getNet()));
    }

    private void writeFooterRow(SXSSFSheet sheet, int idx, ColumnOrder cols, Styles s, BigDecimal[] totals) {
        Row r = sheet.createRow(idx);
        cell(r, 0, "TOTAL", s.footerText);
        cell(r, 1, "", s.footerText);
        money(r, 2, totals[0], s.footerMoney);
        int c = 3;
        int t = 7;
        for (int i = 0; i < cols.earnings.size(); i++) money(r, c++, totals[t++], s.footerMoney);
        money(r, c++, totals[1], s.footerMoney);
        for (int i = 0; i < cols.deductions.size(); i++) money(r, c++, totals[t++], s.footerMoney);
        money(r, c++, totals[2], s.footerMoney);
        money(r, c++, totals[3], s.footerMoney);
        money(r, c++, totals[4], s.footerMoney);
        money(r, c, totals[5], s.footerMoney);
    }

    private static BigDecimal orZero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

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

    private record ColumnOrder(List<String[]> earnings, List<String[]> deductions) {
        int totalSize() { return earnings.size() + deductions.size(); }
    }

    private record Styles(CellStyle title, CellStyle header, CellStyle text,
                          CellStyle money, CellStyle footerText, CellStyle footerMoney) {
        static Styles create(SXSSFWorkbook wb) {
            DataFormat fmt = wb.createDataFormat();
            short moneyFmt = fmt.getFormat("#,##0.00");

            Font bold = wb.createFont();
            bold.setBold(true);

            CellStyle title = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);
            title.setFont(titleFont);

            CellStyle header = wb.createCellStyle();
            header.setFont(bold);

            CellStyle text = wb.createCellStyle();
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(moneyFmt);

            CellStyle footerText = wb.createCellStyle();
            footerText.setFont(bold);
            CellStyle footerMoney = wb.createCellStyle();
            footerMoney.setFont(bold);
            footerMoney.setDataFormat(moneyFmt);

            return new Styles(title, header, text, money, footerText, footerMoney);
        }
    }
}

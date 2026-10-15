package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;

import lombok.RequiredArgsConstructor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Salary advances outstanding — open permanent advances (APPROVED with
 * remaining > 0) plus approved short-term advances not yet deducted in a
 * payroll run. Two sheets in one workbook so finance can process them
 * separately.
 */
@Service
@RequiredArgsConstructor
public class AdvancesOutstandingReportService {

    private final PermanentAdvanceRepository permanent;
    private final PermanentAdvanceDeductionRepository deductions;
    private final ShortTermAdvanceRepository shortTerm;
    private final EmployeeClient employees;
    private final AuditEmitter audit;

    @Transactional
    public void writeXlsx(OutputStream out) throws IOException {
        // Permanent: status APPROVED → remaining = amount - sum(deductions).
        // The .search() takes a status; reuse it with a wide page.
        List<PermanentAdvance> permActive = permanent
                .search(null, AdvanceStatus.APPROVED, PageRequest.of(0, 5000))
                .getContent();
        // Short-term approved with no deducted_in_run_id.
        List<ShortTermAdvance> shortPending = shortTerm.findAll().stream()
                .filter(s -> s.getStatus() == ShortTermAdvanceStatus.APPROVED
                        && s.getDeductedInRunId() == null)
                .sorted(Comparator
                        .comparingInt(ShortTermAdvance::getTargetYear)
                        .thenComparingInt(ShortTermAdvance::getTargetMonth)
                        .thenComparingLong(ShortTermAdvance::getEmployeeId))
                .toList();

        Set<Long> employeeIds = new HashSet<>();
        for (PermanentAdvance a : permActive) employeeIds.add(a.getEmployeeId());
        for (ShortTermAdvance s : shortPending) employeeIds.add(s.getEmployeeId());
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(employeeIds);

        // Deductions belong to the employee: apply each employee's total to
        // their APPROVED advances oldest-first to get a per-advance figure.
        Map<Long, BigDecimal> deductedByAdvance = new HashMap<>(permActive.size());
        Set<Long> permEmployees = new HashSet<>();
        for (PermanentAdvance a : permActive) permEmployees.add(a.getEmployeeId());
        Map<Long, BigDecimal> deductedByEmployee = new HashMap<>();
        if (!permEmployees.isEmpty()) {
            for (var r : deductions.totalDeductedByEmployeeIds(permEmployees)) {
                deductedByEmployee.put(r.getEmployeeId(), r.getAppliedAmount());
            }
        }
        permActive.stream().collect(java.util.stream.Collectors.groupingBy(PermanentAdvance::getEmployeeId))
                .forEach((emp, list) -> deductedByAdvance.putAll(PermanentAdvanceBalances.recoveredPerAdvance(
                        list, deductedByEmployee.getOrDefault(emp, BigDecimal.ZERO))));

        BigDecimal permTotal = BigDecimal.ZERO;
        BigDecimal shortTotal = BigDecimal.ZERO;

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);

            // ---- Permanent advances sheet ----
            SXSSFSheet perm = wb.createSheet("Permanent advances");
            int idx = 0;
            cell(perm.createRow(idx++), 0, "Permanent advances — outstanding (APPROVED, remaining > 0)", s.title);
            idx++;
            Row ph = perm.createRow(idx++);
            cell(ph, 0, "Request Code", s.header);
            cell(ph, 1, "Employee Code", s.header);
            cell(ph, 2, "Name", s.header);
            cell(ph, 3, "Original Amount", s.header);
            cell(ph, 4, "Recovered", s.header);
            cell(ph, 5, "Remaining", s.header);
            cell(ph, 6, "Monthly Deduction", s.header);
            cell(ph, 7, "Receiving Date", s.header);

            for (PermanentAdvance a : permActive) {
                BigDecimal recovered = deductedByAdvance.getOrDefault(a.getId(), BigDecimal.ZERO);
                BigDecimal remaining = a.getAmount().subtract(recovered);
                if (remaining.signum() <= 0) continue;
                EmployeeClient.EmployeeBrief b = briefs.getOrDefault(a.getEmployeeId(),
                        EmployeeClient.EmployeeBrief.placeholder(a.getEmployeeId()));
                Row r = perm.createRow(idx++);
                cell(r, 0, a.getRequestCode(), s.text);
                cell(r, 1, b.employeeCode(), s.text);
                cell(r, 2, b.fullName(), s.text);
                money(r, 3, a.getAmount(), s.money);
                money(r, 4, recovered, s.money);
                money(r, 5, remaining, s.money);
                money(r, 6, a.getMonthlyDeduction(), s.money);
                cell(r, 7, a.getReceivingDate() == null ? "" : a.getReceivingDate().toString(), s.text);
                permTotal = permTotal.add(remaining);
            }
            Row pf = perm.createRow(idx);
            cell(pf, 0, "TOTAL", s.footerText);
            money(pf, 5, permTotal, s.footerMoney);

            for (int c = 0; c < 8; c++) perm.setColumnWidth(c, 4500);

            // ---- Short-term advances sheet ----
            SXSSFSheet st = wb.createSheet("Short-term advances");
            int sIdx = 0;
            cell(st.createRow(sIdx++), 0, "Short-term advances — APPROVED, not yet deducted", s.title);
            sIdx++;
            Row sh = st.createRow(sIdx++);
            cell(sh, 0, "Employee Code", s.header);
            cell(sh, 1, "Name", s.header);
            cell(sh, 2, "Amount", s.header);
            cell(sh, 3, "Target Period", s.header);
            cell(sh, 4, "Status", s.header);

            for (ShortTermAdvance sa : shortPending) {
                EmployeeClient.EmployeeBrief b = briefs.getOrDefault(sa.getEmployeeId(),
                        EmployeeClient.EmployeeBrief.placeholder(sa.getEmployeeId()));
                Row r = st.createRow(sIdx++);
                cell(r, 0, b.employeeCode(), s.text);
                cell(r, 1, b.fullName(), s.text);
                money(r, 2, sa.getAmount(), s.money);
                cell(r, 3, "%d-%02d".formatted(sa.getTargetYear(), sa.getTargetMonth()), s.text);
                cell(r, 4, sa.getStatus().name(), s.text);
                shortTotal = shortTotal.add(sa.getAmount());
            }
            Row sf = st.createRow(sIdx);
            cell(sf, 0, "TOTAL", s.footerText);
            money(sf, 2, shortTotal, s.footerMoney);
            for (int c = 0; c < 5; c++) st.setColumnWidth(c, 4500);

            wb.write(out);
            wb.dispose();
        }

        audit.emit("report", "0", "advances-outstanding.run", null,
                Map.of("permanentCount", permActive.size(),
                        "shortTermCount", shortPending.size(),
                        "permanentRemaining", permTotal,
                        "shortTermPending", shortTotal),
                false);
    }

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

    private record Styles(CellStyle title, CellStyle header, CellStyle text,
                          CellStyle money, CellStyle footerText, CellStyle footerMoney) {
        static Styles create(SXSSFWorkbook wb) {
            DataFormat fmt = wb.createDataFormat();
            short moneyFmt = fmt.getFormat("#,##0.00");
            Font bold = wb.createFont(); bold.setBold(true);
            Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 12);
            CellStyle title = wb.createCellStyle(); title.setFont(titleFont);
            CellStyle header = wb.createCellStyle(); header.setFont(bold);
            CellStyle text = wb.createCellStyle();
            CellStyle money = wb.createCellStyle(); money.setDataFormat(moneyFmt);
            CellStyle footerText = wb.createCellStyle(); footerText.setFont(bold);
            CellStyle footerMoney = wb.createCellStyle(); footerMoney.setFont(bold); footerMoney.setDataFormat(moneyFmt);
            return new Styles(title, header, text, money, footerText, footerMoney);
        }
    }
}

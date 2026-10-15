package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.OffCyclePayment;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.OffCyclePaymentStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.OffCyclePaymentRepository;

import lombok.RequiredArgsConstructor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Off-cycle payments log — bonuses, ad-hoc adjustments, leave
 * encashments paid outside the regular monthly run. Filtered by date
 * range; optional category filter.
 */
@Service
@RequiredArgsConstructor
public class OffCyclePaymentsLogReportService {

    private final OffCyclePaymentRepository payments;
    private final EmployeeClient employees;
    private final AuditEmitter audit;

    @Transactional
    public void writeXlsx(OutputStream out, LocalDate from, LocalDate to,
                          OffCyclePaymentSource source) throws IOException {
        if (from == null || to == null) {
            throw new ConflictError(PayrollErrorCode.INVALID_PERIOD, "from and to are required");
        }
        if (from.isAfter(to)) {
            throw new ConflictError(PayrollErrorCode.INVALID_PERIOD, "from must be ≤ to");
        }

        // One bounded query for both cases. findForReport handles the
        // no-filter case itself through `(:source is null or ...)` with a
        // String bind, so the old findAll()-and-filter-in-heap branch is
        // gone: it loaded the whole table for the common (unfiltered)
        // report and would OOM the pod as the table grows.
        List<OffCyclePayment> rows = payments.findForReport(
                from, to,
                source == null ? null : source.name(),
                PageRequest.of(0, 50000, Sort.by(Sort.Direction.DESC, "paidOn"))).getContent();

        Set<Long> employeeIds = new HashSet<>();
        for (OffCyclePayment p : rows) employeeIds.add(p.getEmployeeId());
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(employeeIds);

        BigDecimal total = BigDecimal.ZERO;
        int paidCount = 0;

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Off-cycle payments");
            int idx = 0;

            cell(sheet.createRow(idx++), 0,
                    "Off-cycle payments log — %s to %s%s".formatted(
                            from, to, source == null ? "" : " (" + source.name() + ")"),
                    s.title);
            idx++;

            Row h = sheet.createRow(idx++);
            cell(h, 0, "Paid On", s.header);
            cell(h, 1, "Employee Code", s.header);
            cell(h, 2, "Name", s.header);
            cell(h, 3, "Payment Type", s.header);
            cell(h, 4, "Amount", s.header);
            cell(h, 5, "Payment Mode", s.header);
            cell(h, 6, "Reference", s.header);
            cell(h, 7, "Source", s.header);
            cell(h, 8, "Status", s.header);
            cell(h, 9, "Reason", s.header);

            for (OffCyclePayment p : rows) {
                EmployeeClient.EmployeeBrief b = briefs.getOrDefault(p.getEmployeeId(),
                        EmployeeClient.EmployeeBrief.placeholder(p.getEmployeeId()));
                Row r = sheet.createRow(idx++);
                cell(r, 0, p.getPaidOn().toString(), s.text);
                cell(r, 1, b.employeeCode(), s.text);
                cell(r, 2, b.fullName(), s.text);
                // The HR-selected type as it read at the time, not the
                // master's current name — a rename must not rewrite a
                // historical financial report. Pre-V0057 rows carry their
                // original free-text category in the same column.
                cell(r, 3, p.getOffCyclePaymentTypeName(), s.text);
                money(r, 4, p.getAmount(), s.money);
                cell(r, 5, p.getPaymentMode() == null ? "" : p.getPaymentMode().name(), s.text);
                cell(r, 6, p.getReferenceNo() == null ? "" : p.getReferenceNo(), s.text);
                cell(r, 7, p.getSource() == null ? "" : p.getSource().name(), s.text);
                cell(r, 8, p.getStatus() == null ? "" : p.getStatus().name(), s.text);
                cell(r, 9, p.getReason(), s.text);
                // Record visibility and financial aggregation are different
                // questions: an HR_APPROVED row is authorized but unpaid and a
                // CANCELLED one was withdrawn, so both stay listed for the
                // audit trail while only PAID money reaches the total.
                if (p.getStatus() == OffCyclePaymentStatus.PAID) {
                    total = total.add(p.getAmount());
                    paidCount++;
                }
            }
            Row foot = sheet.createRow(idx);
            cell(foot, 0, "TOTAL PAID (" + paidCount + " of " + rows.size() + " rows)", s.footerText);
            money(foot, 4, total, s.footerMoney);

            sheet.setColumnWidth(0, 3500);
            sheet.setColumnWidth(1, 4000);
            sheet.setColumnWidth(2, 8000);
            sheet.setColumnWidth(3, 4500);
            sheet.setColumnWidth(4, 4000);
            sheet.setColumnWidth(5, 4500);
            sheet.setColumnWidth(6, 5000);
            sheet.setColumnWidth(7, 4000);
            sheet.setColumnWidth(8, 3500);
            sheet.setColumnWidth(9, 12000);

            wb.write(out);
            wb.dispose();
        }

        audit.emit("report", "0", "off-cycle-payments.run", null,
                Map.of("from", from, "to", to,
                        "source", source == null ? "" : source.name(),
                        "rowCount", rows.size(),
                        "paidRowCount", paidCount,
                        "totalPaid", total),
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

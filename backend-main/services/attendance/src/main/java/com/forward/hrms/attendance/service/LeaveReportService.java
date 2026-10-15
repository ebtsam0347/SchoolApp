package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.LeaveBalance;
import com.forward.hrms.attendance.entity.LeaveRequest;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.LeaveBalanceRepository;
import com.forward.hrms.attendance.repository.LeaveRequestRepository;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Standard leave reports — balance snapshot, leave taken, application
 * log, and year-end carry-forward forecast. All sync XLSX. Lives in
 * attendance-service since the leaves tables are there.
 */
@Service
@RequiredArgsConstructor
public class LeaveReportService {

    private final LeaveBalanceRepository balances;
    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository leaveTypes;
    private final AttendanceEmployeeRepository attendanceEmployees;
    private final AuditEmitter audit;

    @Transactional
    public void writeBalanceXlsx(OutputStream out, int year) throws IOException {
        validateYear(year);
        Map<Long, LeaveType> typesById = typesById();
        // No `findAllByYear` exists; iterate in-memory. Volume = employees × types.
        List<LeaveBalance> all = balances.findAll().stream()
                .filter(b -> b.getYear() == year)
                .toList();
        Set<Long> employeeIds = new HashSet<>();
        for (LeaveBalance b : all) employeeIds.add(b.getEmployeeId());
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(employeeIds);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Leave balance");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Leave balance — " + year, s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Employee Code", "Name", "Leave Type",
                    "Allocated", "Used", "Pending", "Carried", "Remaining"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);

            all.stream()
                    .sorted(Comparator
                            .comparing((LeaveBalance b) -> codeOf(empById.get(b.getEmployeeId())))
                            .thenComparing(LeaveBalance::getLeaveTypeId))
                    .forEach(b -> {
                        AttendanceEmployee ae = empById.get(b.getEmployeeId());
                        LeaveType type = typesById.get(b.getLeaveTypeId());
                        BigDecimal remaining = b.getAllocated()
                                .add(b.getCarried())
                                .subtract(b.getUsed())
                                .subtract(b.getPending());
                        int row = sheet.getLastRowNum() + 1;
                        Row r = sheet.createRow(row);
                        cell(r, 0, codeOf(ae), s.text);
                        cell(r, 1, ae == null ? "" : ae.getFullName(), s.text);
                        cell(r, 2, type == null ? String.valueOf(b.getLeaveTypeId()) : type.getName(), s.text);
                        money(r, 3, b.getAllocated(), s.money);
                        money(r, 4, b.getUsed(), s.money);
                        money(r, 5, b.getPending(), s.money);
                        money(r, 6, b.getCarried(), s.money);
                        money(r, 7, remaining, s.money);
                    });
            standardWidths(sheet, 8);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "leave-balance.run", null,
                Map.of("year", year, "rowCount", all.size()), false);
    }

    @Transactional
    public void writeTakenXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        // Approved requests overlapping the range.
        List<LeaveRequest> all = requests.findAll().stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED
                        || r.getStatus() == LeaveRequestStatus.PARTIALLY_APPROVED)
                .filter(r -> !r.getFromDate().isAfter(to) && !r.getToDate().isBefore(from))
                .sorted(Comparator.comparing(LeaveRequest::getFromDate)
                        .thenComparing(LeaveRequest::getEmployeeId))
                .toList();
        Map<Long, LeaveType> typesById = typesById();
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(
                all.stream().map(LeaveRequest::getEmployeeId).toList());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Leave taken");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Leave taken — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Employee Code", "Name", "Leave Type", "From", "To", "Days", "Status", "Reason"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);
            for (LeaveRequest r : all) {
                AttendanceEmployee ae = empById.get(r.getEmployeeId());
                LeaveType lt = typesById.get(r.getLeaveTypeId());
                Row row = sheet.createRow(idx++);
                cell(row, 0, codeOf(ae), s.text);
                cell(row, 1, ae == null ? "" : ae.getFullName(), s.text);
                cell(row, 2, lt == null ? "" : lt.getName(), s.text);
                cell(row, 3, r.getFromDate().toString(), s.text);
                cell(row, 4, r.getToDate().toString(), s.text);
                money(row, 5, r.getDays(), s.money);
                cell(row, 6, r.getStatus().name(), s.text);
                cell(row, 7, r.getReason() == null ? "" : r.getReason(), s.text);
            }
            standardWidths(sheet, 8);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "leave-taken.run", null,
                Map.of("from", from, "to", to, "rowCount", all.size()), false);
    }

    @Transactional
    public void writeApplicationLogXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        // Every request submitted (createdAt) within the window, regardless of
        // status. Use overlap on the fromDate/toDate as a cheap approximation
        // since the audit trail's 'submittedAt' is on the audit table — for
        // an HR-facing report this is good enough.
        List<LeaveRequest> all = requests.findAll().stream()
                .filter(r -> !r.getFromDate().isAfter(to) && !r.getToDate().isBefore(from))
                .sorted(Comparator.comparing(LeaveRequest::getCreatedAt))
                .toList();
        Map<Long, LeaveType> typesById = typesById();
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(
                all.stream().map(LeaveRequest::getEmployeeId).toList());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Leave applications");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Leave application log — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Request Code", "Employee Code", "Name", "Leave Type",
                    "From", "To", "Days", "Status", "Submitted At"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);
            for (LeaveRequest r : all) {
                AttendanceEmployee ae = empById.get(r.getEmployeeId());
                LeaveType lt = typesById.get(r.getLeaveTypeId());
                Row row = sheet.createRow(idx++);
                cell(row, 0, r.getRequestCode(), s.text);
                cell(row, 1, codeOf(ae), s.text);
                cell(row, 2, ae == null ? "" : ae.getFullName(), s.text);
                cell(row, 3, lt == null ? "" : lt.getName(), s.text);
                cell(row, 4, r.getFromDate().toString(), s.text);
                cell(row, 5, r.getToDate().toString(), s.text);
                money(row, 6, r.getDays(), s.money);
                cell(row, 7, r.getStatus().name(), s.text);
                cell(row, 8, r.getCreatedAt() == null ? "" : r.getCreatedAt().toString(), s.text);
            }
            standardWidths(sheet, 9);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "leave-application-log.run", null,
                Map.of("from", from, "to", to, "rowCount", all.size()), false);
    }

    @Transactional
    public void writeCarryForwardXlsx(OutputStream out, int year) throws IOException {
        validateYear(year);
        Map<Long, LeaveType> typesById = typesById();
        List<LeaveBalance> all = balances.findAll().stream()
                .filter(b -> b.getYear() == year)
                .toList();
        Set<Long> employeeIds = new HashSet<>();
        for (LeaveBalance b : all) employeeIds.add(b.getEmployeeId());
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(employeeIds);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Carry-forward forecast");
            int idx = 0;
            cell(sheet.createRow(idx++), 0,
                    "Year-end carry-forward forecast — into " + (year + 1), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Employee Code", "Name", "Leave Type",
                    "Remaining", "Carry Cap", "Forecast Carry", "Forfeited"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);

            all.stream()
                    .sorted(Comparator
                            .comparing((LeaveBalance b) -> codeOf(empById.get(b.getEmployeeId())))
                            .thenComparing(LeaveBalance::getLeaveTypeId))
                    .forEach(b -> {
                        AttendanceEmployee ae = empById.get(b.getEmployeeId());
                        LeaveType type = typesById.get(b.getLeaveTypeId());
                        BigDecimal remaining = b.getAllocated()
                                .add(b.getCarried())
                                .subtract(b.getUsed())
                                .subtract(b.getPending());
                        BigDecimal cap = type == null || type.getCarryForwardMax() == null
                                ? BigDecimal.ZERO
                                : type.getCarryForwardMax();
                        BigDecimal carry = remaining.signum() <= 0
                                ? BigDecimal.ZERO
                                : remaining.min(cap);
                        BigDecimal forfeit = remaining.signum() <= 0
                                ? BigDecimal.ZERO
                                : remaining.subtract(carry);
                        int row = sheet.getLastRowNum() + 1;
                        Row r = sheet.createRow(row);
                        cell(r, 0, codeOf(ae), s.text);
                        cell(r, 1, ae == null ? "" : ae.getFullName(), s.text);
                        cell(r, 2, type == null ? "" : type.getName(), s.text);
                        money(r, 3, remaining, s.money);
                        money(r, 4, cap, s.money);
                        money(r, 5, carry, s.money);
                        money(r, 6, forfeit, s.money);
                    });
            standardWidths(sheet, 7);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "leave-carry-forward.run", null,
                Map.of("year", year, "rowCount", all.size()), false);
    }

    // ---------- helpers ----------

    private void validateYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new ConflictError(com.forward.hrms.attendance.enums.AttendanceErrorCode.REPORT_VALIDATION_FAILED,
                    "year must be 2000..2100");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ConflictError(com.forward.hrms.attendance.enums.AttendanceErrorCode.REPORT_VALIDATION_FAILED,
                    "from and to are required");
        }
        if (from.isAfter(to)) {
            throw new ConflictError(com.forward.hrms.attendance.enums.AttendanceErrorCode.REPORT_VALIDATION_FAILED,
                    "from must be ≤ to");
        }
    }

    private Map<Long, LeaveType> typesById() {
        Map<Long, LeaveType> out = new HashMap<>();
        for (LeaveType t : leaveTypes.findAll()) out.put(t.getId(), t);
        return out;
    }

    private Map<Long, AttendanceEmployee> empSnapshotsFor(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Set<Long> distinct = new HashSet<>(ids);
        Map<Long, AttendanceEmployee> out = new HashMap<>(distinct.size());
        for (AttendanceEmployee ae : attendanceEmployees.findAllByEmployeeIdIn(distinct)) {
            out.put(ae.getEmployeeId(), ae);
        }
        return out;
    }

    private static String codeOf(AttendanceEmployee ae) {
        return ae == null ? "" : ae.getEmployeeCode();
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

    private static void standardWidths(SXSSFSheet sheet, int count) {
        for (int i = 0; i < count; i++) sheet.setColumnWidth(i, 4500);
        sheet.setColumnWidth(0, 3500);
        if (count > 1) sheet.setColumnWidth(1, 8000);
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

package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.entity.EmployeeShift;
import com.forward.hrms.attendance.entity.OvertimeRequest;
import com.forward.hrms.attendance.entity.RegularizationRequest;
import com.forward.hrms.attendance.entity.Shift;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.OvertimeStatus;
import com.forward.hrms.attendance.enums.RegularizationStatus;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.EmployeeShiftRepository;
import com.forward.hrms.attendance.repository.OvertimeRequestRepository;
import com.forward.hrms.attendance.repository.RegularizationRequestRepository;
import com.forward.hrms.attendance.repository.ShiftRepository;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * All attendance-owned standard reports rolled into one bean. Each
 * public {@code writeXxxXlsx} streams to the supplied {@link OutputStream}
 * (the controller wraps it in a {@code StreamingResponseBody}).
 *
 * <p>The reports share the same employee-name resolution path
 * ({@link AttendanceEmployeeRepository}, populated via the
 * {@code employee.created/updated} consumer) and the same Excel
 * scaffolding, so collapsing them into one class keeps the per-report
 * footprint to ~30 lines.
 *
 * <p>Async outputs aren't wired here — payroll owns the
 * {@code report_jobs} queue. The "monthly summary" listed as async in
 * docs/reports.md is shipped sync; the worst-case cell count
 * (1000 employees × 31 days × 5 columns) sits comfortably in the
 * SXSSF window.
 */
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceEmployeeRepository attendanceEmployees;
    private final AttendanceRecordRepository records;
    private final OvertimeRequestRepository overtime;
    private final RegularizationRequestRepository regularizations;
    private final EmployeeShiftRepository employeeShifts;
    private final ShiftRepository shifts;
    private final AuditEmitter audit;

    // ---------- 1. Daily attendance sheet (Excel for now; PDF later if needed) ----------

    /**
     * One day, every employee's record. Rendered as Excel rather than the
     * PDF the doc nominally calls for — same row shape, easier to filter.
     */
    @Transactional
    public void writeDailyXlsx(OutputStream out, LocalDate date) throws IOException {
        if (date == null) throw new ConflictError(
                com.forward.hrms.attendance.enums.AttendanceErrorCode.REPORT_VALIDATION_FAILED,
                "date is required");
        List<AttendanceRecord> rows = records.findAllByAttendanceDate(date);
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(rows.stream().map(AttendanceRecord::getEmployeeId).toList());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Daily attendance");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Daily attendance — " + date, s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Employee Code", "Name", "Status", "Check-in", "Check-out",
                    "Late (min)", "Early (min)", "Worked (min)", "OT (min)", "Notes"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);

            rows.sort(Comparator.comparing(r -> {
                AttendanceEmployee e = empById.get(r.getEmployeeId());
                return e == null ? "" : e.getEmployeeCode();
            }));
            for (AttendanceRecord r : rows) {
                AttendanceEmployee e = empById.get(r.getEmployeeId());
                Row row = sheet.createRow(idx++);
                cell(row, 0, e == null ? String.valueOf(r.getEmployeeId()) : e.getEmployeeCode(), s.text);
                cell(row, 1, e == null ? "" : e.getFullName(), s.text);
                cell(row, 2, r.getStatus() == null ? "" : r.getStatus().name(), s.text);
                cell(row, 3, r.getCheckIn() == null ? "" : DATETIME_FMT.format(r.getCheckIn()), s.text);
                cell(row, 4, r.getCheckOut() == null ? "" : DATETIME_FMT.format(r.getCheckOut()), s.text);
                cell(row, 5, r.getLateMinutes(), s.intCell);
                cell(row, 6, r.getEarlyMinutes(), s.intCell);
                cell(row, 7, r.getWorkedMinutes(), s.intCell);
                cell(row, 8, r.getOtNormalMinutes() + r.getOtExtraMinutes() + r.getOtSpecialMinutes(), s.intCell);
                cell(row, 9, r.getNotes() == null ? "" : r.getNotes(), s.text);
            }
            standardWidths(sheet, 10);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "attendance-daily.run", null,
                Map.of("date", date, "rowCount", rows.size()), false);
    }

    // ---------- 2. Monthly summary ----------

    /** Per employee × per status counts over a month. */
    @Transactional
    public void writeMonthlySummaryXlsx(OutputStream out, int year, int month) throws IOException {
        if (month < 1 || month > 12) throw new ConflictError(
                com.forward.hrms.attendance.enums.AttendanceErrorCode.REPORT_VALIDATION_FAILED,
                "month must be 1..12");
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        List<AttendanceRecord> all = recordsBetween(from, to);
        Map<Long, EmployeeStats> stats = new HashMap<>();
        for (AttendanceRecord r : all) {
            EmployeeStats es = stats.computeIfAbsent(r.getEmployeeId(), id -> new EmployeeStats());
            switch (r.getStatus()) {
                case PRESENT -> es.present++;
                case ABSENT -> es.absent++;
                case LEAVE -> es.leave++;
                case HOLIDAY -> es.holiday++;
                case WEEKEND -> es.weekend++;
                default -> es.other++;
            }
            if (r.getLateMinutes() > 0) es.lateDays++;
            es.otMinutes += r.getOtNormalMinutes() + r.getOtExtraMinutes() + r.getOtSpecialMinutes();
        }
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(stats.keySet());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Monthly summary");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Monthly attendance summary — %d-%02d".formatted(year, month), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Employee Code", "Name", "Present", "Absent", "Leave",
                    "Holiday", "Weekend", "Other", "Late Days", "OT (min)"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);

            stats.entrySet().stream()
                    .sorted((a, b) -> codeOf(empById.get(a.getKey())).compareTo(codeOf(empById.get(b.getKey()))))
                    .forEach(e -> {
                        AttendanceEmployee ae = empById.get(e.getKey());
                        EmployeeStats st = e.getValue();
                        int row = sheet.getLastRowNum() + 1;
                        Row r = sheet.createRow(row);
                        cell(r, 0, codeOf(ae), s.text);
                        cell(r, 1, ae == null ? "" : ae.getFullName(), s.text);
                        cell(r, 2, st.present, s.intCell);
                        cell(r, 3, st.absent, s.intCell);
                        cell(r, 4, st.leave, s.intCell);
                        cell(r, 5, st.holiday, s.intCell);
                        cell(r, 6, st.weekend, s.intCell);
                        cell(r, 7, st.other, s.intCell);
                        cell(r, 8, st.lateDays, s.intCell);
                        cell(r, 9, st.otMinutes, s.intCell);
                    });
            standardWidths(sheet, 10);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "attendance-monthly-summary.run", null,
                Map.of("year", year, "month", month, "employeeCount", stats.size()), false);
    }

    // ---------- 3. Late comers ----------

    @Transactional
    public void writeLateComersXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        List<AttendanceRecord> all = recordsBetween(from, to).stream()
                .filter(r -> r.getLateMinutes() > 0)
                .toList();
        Map<Long, Integer> countsByEmp = new HashMap<>();
        Map<Long, Integer> minutesByEmp = new HashMap<>();
        for (AttendanceRecord r : all) {
            countsByEmp.merge(r.getEmployeeId(), 1, Integer::sum);
            minutesByEmp.merge(r.getEmployeeId(), r.getLateMinutes(), Integer::sum);
        }
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(countsByEmp.keySet());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Late comers");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Late comers — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            for (int c = 0; c < 5; c++) cell(h, c,
                    new String[]{"Employee Code", "Name", "Late Days", "Total Late (min)", "Avg Late (min)"}[c],
                    s.header);

            countsByEmp.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        AttendanceEmployee ae = empById.get(entry.getKey());
                        int row = sheet.getLastRowNum() + 1;
                        int days = entry.getValue();
                        int total = minutesByEmp.getOrDefault(entry.getKey(), 0);
                        Row r = sheet.createRow(row);
                        cell(r, 0, codeOf(ae), s.text);
                        cell(r, 1, ae == null ? "" : ae.getFullName(), s.text);
                        cell(r, 2, days, s.intCell);
                        cell(r, 3, total, s.intCell);
                        cell(r, 4, days == 0 ? 0 : total / days, s.intCell);
                    });
            standardWidths(sheet, 5);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "late-comers.run", null,
                Map.of("from", from, "to", to, "employeeCount", countsByEmp.size()), false);
    }

    // ---------- 4. Absentee log ----------

    @Transactional
    public void writeAbsenteeLogXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        List<AttendanceRecord> all = recordsBetween(from, to).stream()
                .filter(r -> r.getStatus() == AttendanceStatus.ABSENT)
                .sorted(Comparator.comparing(AttendanceRecord::getAttendanceDate)
                        .thenComparing(AttendanceRecord::getEmployeeId))
                .toList();
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(
                all.stream().map(AttendanceRecord::getEmployeeId).toList());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Absentee log");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Absentee log — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            for (int c = 0; c < 4; c++) cell(h, c,
                    new String[]{"Date", "Employee Code", "Name", "Notes"}[c], s.header);
            for (AttendanceRecord r : all) {
                AttendanceEmployee ae = empById.get(r.getEmployeeId());
                Row row = sheet.createRow(idx++);
                cell(row, 0, r.getAttendanceDate().toString(), s.text);
                cell(row, 1, codeOf(ae), s.text);
                cell(row, 2, ae == null ? "" : ae.getFullName(), s.text);
                cell(row, 3, r.getNotes() == null ? "" : r.getNotes(), s.text);
            }
            standardWidths(sheet, 4);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "absentee-log.run", null,
                Map.of("from", from, "to", to, "rowCount", all.size()), false);
    }

    // ---------- 5. OT register ----------

    @Transactional
    public void writeOvertimeRegisterXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        // OT requests within the window. Match by attendance_date — the stored
        // shape is simpler than overlapping a request range. Caller-side
        // pagination not needed: months × employees stays small.
        List<OvertimeRequest> all = overtime.findAll().stream()
                .filter(o -> !o.getAttendanceDate().isBefore(from) && !o.getAttendanceDate().isAfter(to))
                .sorted(Comparator.comparing(OvertimeRequest::getAttendanceDate)
                        .thenComparing(OvertimeRequest::getEmployeeId))
                .toList();
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(
                all.stream().map(OvertimeRequest::getEmployeeId).toList());
        Map<Long, AttendanceRecord> recordByKey = new HashMap<>();
        for (OvertimeRequest o : all) {
            records.findByEmployeeIdAndAttendanceDate(o.getEmployeeId(), o.getAttendanceDate())
                    .ifPresent(r -> recordByKey.put(o.getId(), r));
        }

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("OT register");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "OT register — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            for (int c = 0; c < 7; c++) cell(h, c,
                    new String[]{"Date", "Employee Code", "Name", "Status", "Request Code",
                            "OT Minutes", "Reason"}[c], s.header);
            for (OvertimeRequest o : all) {
                AttendanceEmployee ae = empById.get(o.getEmployeeId());
                AttendanceRecord rec = recordByKey.get(o.getId());
                int otMin = rec == null ? 0
                        : rec.getOtNormalMinutes() + rec.getOtExtraMinutes() + rec.getOtSpecialMinutes();
                Row row = sheet.createRow(idx++);
                cell(row, 0, o.getAttendanceDate().toString(), s.text);
                cell(row, 1, codeOf(ae), s.text);
                cell(row, 2, ae == null ? "" : ae.getFullName(), s.text);
                cell(row, 3, o.getStatus().name(), s.text);
                cell(row, 4, o.getRequestCode(), s.text);
                cell(row, 5, otMin, s.intCell);
                cell(row, 6, o.getReason() == null ? "" : o.getReason(), s.text);
            }
            standardWidths(sheet, 7);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "ot-register.run", null,
                Map.of("from", from, "to", to, "rowCount", all.size()), false);
    }

    // ---------- 6. Regularization log ----------

    @Transactional
    public void writeRegularizationLogXlsx(OutputStream out, LocalDate from, LocalDate to,
                                           RegularizationStatus status) throws IOException {
        validateRange(from, to);
        List<RegularizationRequest> all = regularizations.findAll().stream()
                .filter(r -> !r.getAttendanceDate().isBefore(from) && !r.getAttendanceDate().isAfter(to))
                .filter(r -> status == null || r.getStatus() == status)
                .sorted(Comparator.comparing(RegularizationRequest::getAttendanceDate)
                        .thenComparing(RegularizationRequest::getEmployeeId))
                .toList();
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(
                all.stream().map(RegularizationRequest::getEmployeeId).toList());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Regularization log");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Regularization log — %s to %s%s".formatted(
                    from, to, status == null ? "" : " (" + status + ")"), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            for (int c = 0; c < 7; c++) cell(h, c,
                    new String[]{"Date", "Employee Code", "Name", "Status", "Request Code",
                            "Requested Check-in", "Requested Check-out"}[c], s.header);
            for (RegularizationRequest r : all) {
                AttendanceEmployee ae = empById.get(r.getEmployeeId());
                Row row = sheet.createRow(idx++);
                cell(row, 0, r.getAttendanceDate().toString(), s.text);
                cell(row, 1, codeOf(ae), s.text);
                cell(row, 2, ae == null ? "" : ae.getFullName(), s.text);
                cell(row, 3, r.getStatus().name(), s.text);
                cell(row, 4, r.getRequestCode(), s.text);
                cell(row, 5, r.getRequestedCheckIn() == null ? "" : DATETIME_FMT.format(r.getRequestedCheckIn()), s.text);
                cell(row, 6, r.getRequestedCheckOut() == null ? "" : DATETIME_FMT.format(r.getRequestedCheckOut()), s.text);
            }
            standardWidths(sheet, 7);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "regularization-log.run", null,
                Map.of("from", from, "to", to, "status", status == null ? "" : status.name(),
                        "rowCount", all.size()), false);
    }

    // ---------- 7. Missing-punch log ----------

    @Transactional
    public void writeMissingPunchXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        List<AttendanceRecord> all = recordsBetween(from, to).stream()
                .filter(r -> r.getStatus() == AttendanceStatus.PRESENT)
                .filter(r -> r.getCheckIn() == null || r.getCheckOut() == null)
                .sorted(Comparator.comparing(AttendanceRecord::getAttendanceDate)
                        .thenComparing(AttendanceRecord::getEmployeeId))
                .toList();
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(
                all.stream().map(AttendanceRecord::getEmployeeId).toList());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Missing punches");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Missing-punch log — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            for (int c = 0; c < 5; c++) cell(h, c,
                    new String[]{"Date", "Employee Code", "Name", "Check-in", "Check-out"}[c], s.header);
            for (AttendanceRecord r : all) {
                AttendanceEmployee ae = empById.get(r.getEmployeeId());
                Row row = sheet.createRow(idx++);
                cell(row, 0, r.getAttendanceDate().toString(), s.text);
                cell(row, 1, codeOf(ae), s.text);
                cell(row, 2, ae == null ? "" : ae.getFullName(), s.text);
                cell(row, 3, r.getCheckIn() == null ? "—" : DATETIME_FMT.format(r.getCheckIn()), s.text);
                cell(row, 4, r.getCheckOut() == null ? "—" : DATETIME_FMT.format(r.getCheckOut()), s.text);
            }
            standardWidths(sheet, 5);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "missing-punch-log.run", null,
                Map.of("from", from, "to", to, "rowCount", all.size()), false);
    }

    // ---------- 8. Shift roster export ----------

    @Transactional
    public void writeShiftRosterXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        // Active assignments overlap with [from, to].
        List<EmployeeShift> assignments = employeeShifts.findAll().stream()
                .filter(es -> !es.getEffectiveFrom().isAfter(to))
                .filter(es -> es.getEffectiveTo() == null || !es.getEffectiveTo().isBefore(from))
                .toList();
        Map<Long, Shift> shiftById = new HashMap<>();
        for (Shift sh : shifts.findAll()) shiftById.put(sh.getId(), sh);
        Map<Long, AttendanceEmployee> empById = empSnapshotsFor(
                assignments.stream().map(EmployeeShift::getEmployeeId).toList());

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Shift roster");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Shift roster — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            for (int c = 0; c < 7; c++) cell(h, c,
                    new String[]{"Employee Code", "Name", "Shift Code", "Shift Name",
                            "Start", "End", "Effective"}[c], s.header);
            assignments.stream()
                    .sorted(Comparator.comparing((EmployeeShift es) ->
                                    codeOf(empById.get(es.getEmployeeId())))
                            .thenComparing(EmployeeShift::getEffectiveFrom))
                    .forEach(es -> {
                        AttendanceEmployee ae = empById.get(es.getEmployeeId());
                        Shift sh = shiftById.get(es.getShiftId());
                        int row = sheet.getLastRowNum() + 1;
                        Row r = sheet.createRow(row);
                        cell(r, 0, codeOf(ae), s.text);
                        cell(r, 1, ae == null ? "" : ae.getFullName(), s.text);
                        cell(r, 2, sh == null ? "" : sh.getCode(), s.text);
                        cell(r, 3, sh == null ? "" : sh.getName(), s.text);
                        cell(r, 4, sh == null ? "" : TIME_FMT.format(sh.getStartTime()), s.text);
                        cell(r, 5, sh == null ? "" : TIME_FMT.format(sh.getEndTime()), s.text);
                        String range = es.getEffectiveFrom() + " → " + (es.getEffectiveTo() == null ? "—" : es.getEffectiveTo().toString());
                        cell(r, 6, range, s.text);
                    });
            standardWidths(sheet, 7);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "shift-roster.run", null,
                Map.of("from", from, "to", to, "rowCount", assignments.size()), false);
    }

    // ---------- helpers ----------

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

    private List<AttendanceRecord> recordsBetween(LocalDate from, LocalDate to) {
        // Native page-walk to keep the result bounded; the org-wide search
        // queries we have all require employeeIds. For a date-range slice
        // of the whole workforce we read the records table directly via
        // a single query that doesn't need filters beyond the range.
        List<AttendanceRecord> out = new ArrayList<>();
        // No "find by date between" exists; reuse search() with a wide page.
        org.springframework.data.domain.Page<AttendanceRecord> page =
                records.search(from, to, null, null, false, false,
                        org.springframework.data.domain.PageRequest.of(0, 50_000));
        out.addAll(page.getContent());
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

    private static void cell(Row r, int idx, int value, CellStyle style) {
        Cell c = r.createCell(idx);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private static void standardWidths(SXSSFSheet sheet, int count) {
        for (int i = 0; i < count; i++) sheet.setColumnWidth(i, 4500);
        sheet.setColumnWidth(0, 3500);
        if (count > 1) sheet.setColumnWidth(1, 8000);
    }

    private static final class EmployeeStats {
        int present, absent, leave, holiday, weekend, other;
        int lateDays;
        int otMinutes;
    }

    private record Styles(CellStyle title, CellStyle header, CellStyle text, CellStyle intCell) {
        static Styles create(SXSSFWorkbook wb) {
            Font bold = wb.createFont(); bold.setBold(true);
            Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 12);
            CellStyle title = wb.createCellStyle(); title.setFont(titleFont);
            CellStyle header = wb.createCellStyle(); header.setFont(bold);
            CellStyle text = wb.createCellStyle();
            CellStyle intCell = wb.createCellStyle();
            return new Styles(title, header, text, intCell);
        }
    }
}

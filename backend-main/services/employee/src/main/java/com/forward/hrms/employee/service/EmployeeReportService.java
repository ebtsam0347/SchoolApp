package com.forward.hrms.employee.service;

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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeCertification;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeCertificationRepository;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

/**
 * Employee-service standard reports: headcount snapshot (PDF),
 * joiners / leavers / contract / document / certification expiries
 * (Excel), confirmations due (Excel), service letter / experience
 * letter (PDF). Sync only — none of these are big enough to need the
 * async substrate.
 */
@Service
@RequiredArgsConstructor
public class EmployeeReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LETTER_DATE =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", java.util.Locale.ENGLISH);
    private static final String COMPANY_NAME = "The Spirit of Islam School";

    private final EmployeeRepository employees;
    private final EmployeeExitRepository exits;
    private final EmploymentContractRepository contracts;
    private final EmployeeCertificationRepository certifications;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;
    private final LetterSettingsService letterSettings;

    // ---------- Headcount snapshot (PDF) ----------

    @Transactional
    public byte[] renderHeadcountSnapshotPdf(LocalDate asOf) {
        LocalDate effectiveAsOf = asOf == null ? LocalDate.now() : asOf;
        List<Employee> activeAll = employees.findAll().stream()
                .filter(e -> e.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                .toList();
        Map<String, Integer> byDept = new TreeMap<>();
        Map<String, Integer> byGrade = new TreeMap<>();
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<Long, Grade> gradeById = byId(grades.findAll(), Grade::getId);

        for (Employee e : activeAll) {
            String d = e.getDepartmentId() == null ? "(unassigned)"
                    : deptById.getOrDefault(e.getDepartmentId(), null) == null
                        ? "(missing)" : deptById.get(e.getDepartmentId()).getName();
            byDept.merge(d, 1, Integer::sum);
            String g = e.getGradeId() == null ? "(unassigned)"
                    : gradeById.getOrDefault(e.getGradeId(), null) == null
                        ? "(missing)" : gradeById.get(e.getGradeId()).getCode();
            byGrade.merge(g, 1, Integer::sum);
        }

        List<Map<String, ?>> rows = new ArrayList<>();
        int serial = 0;
        for (Map.Entry<String, Integer> e : byDept.entrySet()) {
            rows.add(Map.of("serial", ++serial,
                    "category", "Department",
                    "key", e.getKey(),
                    "count", e.getValue()));
        }
        for (Map.Entry<String, Integer> e : byGrade.entrySet()) {
            rows.add(Map.of("serial", ++serial,
                    "category", "Grade",
                    "key", e.getKey(),
                    "count", e.getValue()));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("companyName", COMPANY_NAME);
        params.put("asOf", DATE.format(effectiveAsOf));
        params.put("totalActive", activeAll.size());

        byte[] pdf = jasper.renderPdf("/jasper/headcount-snapshot.jrxml",
                params, new JRMapCollectionDataSource(rows));
        audit.emit("report", "0", "headcount-snapshot.run", null,
                Map.of("asOf", effectiveAsOf, "totalActive", activeAll.size()),
                false);
        return pdf;
    }

    // ---------- Joiners ----------

    @Transactional
    public void writeJoinersXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        List<Employee> rows = employees.findAll().stream()
                .filter(e -> e.getJoiningDate() != null
                        && !e.getJoiningDate().isBefore(from)
                        && !e.getJoiningDate().isAfter(to))
                .sorted(Comparator.comparing(Employee::getJoiningDate))
                .toList();
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<Long, Designation> desigById = byId(designations.findAll(), Designation::getId);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Joiners");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Joiners — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Joining Date", "Employee Code", "Name", "Department",
                    "Designation", "Employment Type", "Work Email", "Phone"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);
            for (Employee e : rows) {
                Row r = sheet.createRow(idx++);
                cell(r, 0, e.getJoiningDate().toString(), s.text);
                cell(r, 1, e.getEmployeeCode(), s.text);
                cell(r, 2, e.getFullName(), s.text);
                cell(r, 3, deptName(deptById, e.getDepartmentId()), s.text);
                cell(r, 4, desigName(desigById, e.getDesignationId()), s.text);
                cell(r, 5, e.getEmploymentType() == null ? "" : e.getEmploymentType().name(), s.text);
                cell(r, 6, nz(e.getWorkEmail()), s.text);
                cell(r, 7, nz(e.getPhone()), s.text);
            }
            standardWidths(sheet, cols.length);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "joiners.run", null,
                Map.of("from", from, "to", to, "rowCount", rows.size()), false);
    }

    // ---------- Leavers ----------

    @Transactional
    public void writeLeaversXlsx(OutputStream out, LocalDate from, LocalDate to) throws IOException {
        validateRange(from, to);
        List<EmployeeExit> rows = exits.findAllByStatusAndLastWorkingDayLessThanEqual(ExitStatus.COMPLETED, to)
                .stream()
                .filter(x -> !x.getLastWorkingDay().isBefore(from))
                .sorted(Comparator.comparing(EmployeeExit::getLastWorkingDay))
                .toList();
        Set<Long> ids = new HashSet<>();
        for (EmployeeExit x : rows) ids.add(x.getEmployeeId());
        Map<Long, Employee> empById = byId(employees.findAllByIdIn(ids), Employee::getId);
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Leavers");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Leavers — %s to %s".formatted(from, to), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Last Working Day", "Employee Code", "Name", "Department",
                    "Exit Type", "Reason"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);
            for (EmployeeExit x : rows) {
                Employee e = empById.get(x.getEmployeeId());
                Row r = sheet.createRow(idx++);
                cell(r, 0, x.getLastWorkingDay() == null ? "" : x.getLastWorkingDay().toString(), s.text);
                cell(r, 1, e == null ? "" : e.getEmployeeCode(), s.text);
                cell(r, 2, e == null ? "" : e.getFullName(), s.text);
                cell(r, 3, e == null ? "" : deptName(deptById, e.getDepartmentId()), s.text);
                cell(r, 4, x.getExitType() == null ? "" : x.getExitType().name(), s.text);
                cell(r, 5, "", s.text);
            }
            standardWidths(sheet, cols.length);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "leavers.run", null,
                Map.of("from", from, "to", to, "rowCount", rows.size()), false);
    }

    // ---------- Contract expiries ----------

    @Transactional
    public void writeContractExpiriesXlsx(OutputStream out, int days) throws IOException {
        validateWindow(days);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);
        List<EmploymentContract> rows = contracts.findAll().stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .filter(c -> c.getEndDate() != null
                        && !c.getEndDate().isBefore(today)
                        && !c.getEndDate().isAfter(horizon))
                .sorted(Comparator.comparing(EmploymentContract::getEndDate))
                .toList();
        Map<Long, Employee> empById = byId(
                employees.findAllByIdIn(rows.stream().map(EmploymentContract::getEmployeeId).toList()),
                Employee::getId);
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Contract expiries");
            int idx = 0;
            cell(sheet.createRow(idx++), 0,
                    "Contract expiries — within %d day(s) (today → %s)".formatted(days, horizon), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"End Date", "Days Left", "Employee Code", "Name",
                    "Department", "Contract Type"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);
            for (EmploymentContract c : rows) {
                Employee e = empById.get(c.getEmployeeId());
                Row r = sheet.createRow(idx++);
                cell(r, 0, c.getEndDate().toString(), s.text);
                cell(r, 1, daysBetween(today, c.getEndDate()), s.intCell);
                cell(r, 2, e == null ? "" : e.getEmployeeCode(), s.text);
                cell(r, 3, e == null ? "" : e.getFullName(), s.text);
                cell(r, 4, e == null ? "" : deptName(deptById, e.getDepartmentId()), s.text);
                cell(r, 5, c.getContractType() == null ? "" : c.getContractType().name(), s.text);
            }
            standardWidths(sheet, cols.length);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "contract-expiries.run", null,
                Map.of("days", days, "rowCount", rows.size()), false);
    }

    // ---------- Document expiries (CNIC + certifications) ----------

    @Transactional
    public void writeDocumentExpiriesXlsx(OutputStream out, int days) throws IOException {
        validateWindow(days);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);
        List<Employee> active = employees.findAll().stream()
                .filter(e -> e.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                .toList();

        record Hit(String type, String description, LocalDate expiresOn, Employee e) {}
        List<Hit> hits = new ArrayList<>();
        for (Employee e : active) {
            if (e.getCnicExpiryDate() != null
                    && !e.getCnicExpiryDate().isBefore(today)
                    && !e.getCnicExpiryDate().isAfter(horizon)) {
                hits.add(new Hit("CNIC", "CNIC " + nz(e.getCnic()), e.getCnicExpiryDate(), e));
            }
        }
        for (EmployeeCertification ec : certifications.findAll()) {
            if (ec.getExpiryDate() == null) continue;
            if (ec.getExpiryDate().isBefore(today) || ec.getExpiryDate().isAfter(horizon)) continue;
            // Map back to employee — narrow, no N+1 worry.
            employees.findById(ec.getEmployeeId()).ifPresent(e ->
                    hits.add(new Hit("Certification", ec.getName(), ec.getExpiryDate(), e)));
        }
        hits.sort(Comparator.comparing(Hit::expiresOn));

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Document expiries");
            int idx = 0;
            cell(sheet.createRow(idx++), 0,
                    "Document expiries — within %d day(s) (today → %s)".formatted(days, horizon), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Expiry Date", "Days Left", "Employee Code", "Name",
                    "Document Type", "Document"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);
            for (Hit hit : hits) {
                Row r = sheet.createRow(idx++);
                cell(r, 0, hit.expiresOn().toString(), s.text);
                cell(r, 1, daysBetween(today, hit.expiresOn()), s.intCell);
                cell(r, 2, hit.e().getEmployeeCode(), s.text);
                cell(r, 3, hit.e().getFullName(), s.text);
                cell(r, 4, hit.type(), s.text);
                cell(r, 5, hit.description(), s.text);
            }
            standardWidths(sheet, cols.length);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "document-expiries.run", null,
                Map.of("days", days, "rowCount", hits.size()), false);
    }

    // ---------- Confirmations due ----------

    @Transactional
    public void writeConfirmationsDueXlsx(OutputStream out, int days) throws IOException {
        validateWindow(days);
        LocalDate horizon = LocalDate.now().plusDays(days);
        // Active probation contracts ending within the window.
        List<EmploymentContract> rows = contracts.findAll().stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .filter(c -> c.getContractType() == ContractType.PROBATION)
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isAfter(horizon))
                .sorted(Comparator.comparing(EmploymentContract::getEndDate))
                .toList();
        Map<Long, Employee> empById = byId(
                employees.findAllByIdIn(rows.stream().map(EmploymentContract::getEmployeeId).toList()),
                Employee::getId);
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            wb.setCompressTempFiles(true);
            Styles s = Styles.create(wb);
            SXSSFSheet sheet = wb.createSheet("Confirmations due");
            int idx = 0;
            cell(sheet.createRow(idx++), 0, "Confirmations due — next %d day(s)".formatted(days), s.title);
            idx++;
            Row h = sheet.createRow(idx++);
            String[] cols = {"Probation End", "Employee Code", "Name", "Department",
                    "Joining Date", "Probation Days", "Extended"};
            for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], s.header);
            for (EmploymentContract c : rows) {
                Employee e = empById.get(c.getEmployeeId());
                Row r = sheet.createRow(idx++);
                cell(r, 0, c.getEndDate().toString(), s.text);
                cell(r, 1, e == null ? "" : e.getEmployeeCode(), s.text);
                cell(r, 2, e == null ? "" : e.getFullName(), s.text);
                cell(r, 3, e == null ? "" : deptName(deptById, e.getDepartmentId()), s.text);
                cell(r, 4, e == null || e.getJoiningDate() == null ? "" : e.getJoiningDate().toString(), s.text);
                cell(r, 5, c.getProbationDays() == null ? 0 : c.getProbationDays(), s.intCell);
                cell(r, 6, c.isExtended() ? "Yes" : "No", s.text);
            }
            standardWidths(sheet, cols.length);
            wb.write(out);
            wb.dispose();
        }
        audit.emit("report", "0", "confirmations-due.run", null,
                Map.of("days", days, "rowCount", rows.size()), false);
    }

    // ---------- Service letter (current employee) ----------

    @Transactional
    public byte[] renderServiceLetter(long employeeId) {
        Employee e = employees.findById(employeeId).orElseThrow(() ->
                new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
        if (e.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED,
                    "service letter is for current employees; use experience letter for terminated");
        }
        LetterSettings settings = letterSettings.current();
        Map<String, Object> params = baseLetterParams(e, "Service Letter", settings);
        params.put("body", buildServiceLetterBody(e, settings.getCompanyLegalName()));
        byte[] pdf = jasper.renderPdf("/jasper/employee-letter.jrxml",
                params, new net.sf.jasperreports.engine.JREmptyDataSource(1));
        audit.emit("report", String.valueOf(employeeId), "service-letter.run",
                null, Map.of("employeeId", employeeId), true);
        return pdf;
    }

    // ---------- Experience letter (terminated employee) ----------

    @Transactional
    public byte[] renderExperienceLetter(long employeeId) {
        Employee e = employees.findById(employeeId).orElseThrow(() ->
                new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
        if (e.getEmploymentStatus() != EmploymentStatus.TERMINATED) {
            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED,
                    "experience letter is for terminated employees only");
        }
        // employee_exits is historical (V0621) — the employee's current
        // (most recent) exit row is the one relevant to a termination
        // just checked above via employmentStatus.
        EmployeeExit exit = exits.findFirstByEmployeeIdOrderByIdDesc(employeeId).orElse(null);
        LetterSettings settings = letterSettings.current();
        Map<String, Object> params = baseLetterParams(e, "Experience Letter", settings);
        params.put("body", buildExperienceLetterBody(e, exit, settings.getCompanyLegalName()));
        byte[] pdf = jasper.renderPdf("/jasper/employee-letter.jrxml",
                params, new net.sf.jasperreports.engine.JREmptyDataSource(1));
        audit.emit("report", String.valueOf(employeeId), "experience-letter.run",
                null, Map.of("employeeId", employeeId), true);
        return pdf;
    }

    /** Letterhead, signatory and footer from letter settings (shared with the appointment letter) + this letter's fields. */
    private Map<String, Object> baseLetterParams(Employee e, String letterTitle, LetterSettings settings) {
        Map<Long, Designation> desigById = byId(designations.findAll(), Designation::getId);
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<String, Object> p = letterSettings.letterheadParams(settings, null, null);
        p.put("letterTitle", letterTitle);
        p.put("letterDate", LETTER_DATE.format(LocalDate.now()));
        p.put("employeeName", e.getFullName());
        p.put("employeeCode", e.getEmployeeCode());
        p.put("designation", desigName(desigById, e.getDesignationId()));
        p.put("department", deptName(deptById, e.getDepartmentId()));
        return p;
    }

    // Signatory and sign-off come from the template's signature block (letter settings), not the body.
    private String buildServiceLetterBody(Employee e, String companyName) {
        StringBuilder sb = new StringBuilder();
        sb.append("To Whom It May Concern,\n\n");
        sb.append("This is to certify that ").append(e.getFullName())
                .append(" (Employee Code ").append(e.getEmployeeCode())
                .append(") has been employed with ").append(companyName)
                .append(" since ").append(LETTER_DATE.format(e.getJoiningDate()))
                .append(".\n\n");
        sb.append("This letter is issued at the request of the employee for record-keeping purposes.");
        return sb.toString();
    }

    private String buildExperienceLetterBody(Employee e, EmployeeExit exit, String companyName) {
        StringBuilder sb = new StringBuilder();
        sb.append("To Whom It May Concern,\n\n");
        sb.append("This is to certify that ").append(e.getFullName())
                .append(" (Employee Code ").append(e.getEmployeeCode())
                .append(") was employed with ").append(companyName)
                .append(" from ").append(LETTER_DATE.format(e.getJoiningDate()));
        if (exit != null && exit.getLastWorkingDay() != null) {
            sb.append(" to ").append(LETTER_DATE.format(exit.getLastWorkingDay()));
        }
        sb.append(".\n\n");
        sb.append("During this tenure, ").append(e.getFullName())
                .append(" demonstrated professionalism and dedication. " +
                        "We wish them every success in their future endeavours.");
        return sb.toString();
    }

    // ---------- helpers ----------

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED, "from and to are required");
        }
        if (from.isAfter(to)) {
            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED, "from must be ≤ to");
        }
    }

    private void validateWindow(int days) {
        if (days <= 0 || days > 730) {
            throw new ConflictError(EmployeeErrorCode.VALIDATION_FAILED,
                    "days must be in 1..730 (got " + days + ")");
        }
    }

    private static <T> Map<Long, T> byId(java.util.Collection<T> rows, java.util.function.Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(idFn.apply(t), t);
        return m;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String deptName(Map<Long, Department> by, Long id) {
        if (id == null) return "";
        Department d = by.get(id);
        return d == null ? "" : d.getName();
    }

    private static String desigName(Map<Long, Designation> by, Long id) {
        if (id == null) return "";
        Designation d = by.get(id);
        return d == null ? "" : d.getTitle();
    }

    private static long daysBetween(LocalDate from, LocalDate to) {
        return java.time.temporal.ChronoUnit.DAYS.between(from, to);
    }

    private static void cell(Row r, int idx, String value, CellStyle style) {
        Cell c = r.createCell(idx);
        c.setCellValue(value == null ? "" : value);
        if (style != null) c.setCellStyle(style);
    }

    private static void cell(Row r, int idx, long value, CellStyle style) {
        Cell c = r.createCell(idx);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private static void standardWidths(SXSSFSheet sheet, int count) {
        for (int i = 0; i < count; i++) sheet.setColumnWidth(i, 4500);
        if (count > 2) sheet.setColumnWidth(2, 8000);
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

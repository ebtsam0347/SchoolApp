package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.AppointmentLetterRequest;
import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.ChangeLogField;
import com.forward.hrms.employee.enums.DepartmentWiseGroupBy;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmployeeListColumns;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.service.AppointmentLetterService;
import com.forward.hrms.employee.service.ChangeLogReportService;
import com.forward.hrms.employee.service.DepartmentWiseReportService;
import com.forward.hrms.employee.service.EmployeeListReportService;
import com.forward.hrms.employee.service.EmployeeRegistersReportService;
import com.forward.hrms.employee.service.EmploymentLetterService;
import com.forward.hrms.employee.service.DepartmentStrengthReportService;
import com.forward.hrms.employee.service.EmployeeDirectoryReportService;
import com.forward.hrms.employee.service.EmployeeCardService;
import com.forward.hrms.employee.service.EmployeeProfileReportService;
import com.forward.hrms.employee.service.EmployeeReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Standard report endpoints in employee-service. Sync streaming via
 * {@link StreamingResponseBody}; async (job-backed) reports land in a
 * sibling controller once the {@code report_jobs} table ships in payroll.
 *
 * <p>Scope rules: HR / ADMIN see the whole directory; a manager
 * (no HR / ADMIN role on their token) sees only their own reportees,
 * resolved server-side from the caller's userId. The same rule will be
 * reused by every list-shaped employee report — keeping it in the
 * controller keeps the report service caller-agnostic.
 */
@Tag(name = "Employee reports",
        description = "Standard exportable reports owned by employee-service. " +
                "Requires `employee.reports.run`. Manager callers see only their reportees.")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportsController {

    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final EmployeeDirectoryReportService directory;
    private final EmployeeReportService reports;
    private final AppointmentLetterService appointmentLetters;
    private final EmployeeProfileReportService profiles;
    private final EmployeeCardService cards;
    private final EmployeeListReportService employeeList;
    private final DepartmentWiseReportService departmentWise;
    private final ChangeLogReportService changeLog;
    private final EmploymentLetterService employmentLetters;
    private final EmployeeRegistersReportService registers;
    private final DepartmentStrengthReportService strength;
    private final EmployeeRepository employees;

    @Operation(summary = "Active-employee directory (CSV)",
            description = "One row per non-terminated employee with identity, contact, and " +
                    "current-assignment columns. Streams as UTF-8 CSV with a BOM so Excel reads " +
                    "non-ASCII names correctly. HR / ADMIN callers get every employee; a MANAGER " +
                    "sees only their direct reports.")
    @ApiResponse(responseCode = "200", description = "CSV stream.")
    @GetMapping(value = "/employees/directory", produces = "text/csv;charset=UTF-8")
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> directoryCsv() {
        Long restrictToManagerId = resolveManagerScope();
        String filename = "employee-directory-" + LocalDate.now() + ".csv";
        StreamingResponseBody body = out -> directory.writeCsv(out, restrictToManagerId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    @Operation(summary = "Headcount snapshot — PDF",
            description = "Active headcount as of a date, broken down by department and grade. " +
                    "Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/headcount-snapshot", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> headcountSnapshot(
            @Parameter(description = "As-of date; defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        byte[] pdf = reports.renderHeadcountSnapshotPdf(asOf);
        String filename = "headcount-" + (asOf == null ? LocalDate.now() : asOf) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "Department strength — PDF",
            description = "Current headcount by department → section → designation, split by gender, " +
                    "with section and department subtotals. HR / ADMIN see the whole company; a " +
                    "MANAGER sees only their direct reports. Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/department-strength", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> departmentStrength() {
        byte[] pdf = strength.render(resolveManagerScope());
        return pdfResponse(pdf, "department-strength-" + LocalDate.now() + ".pdf");
    }

    @Operation(summary = "Employee profile — PDF",
            description = "Printable personnel file for one employee: photo, personal, contact, " +
                    "employment, statutory numbers, emergency contacts, contracts, career history, " +
                    "qualifications, experience, certifications, skills, documents on file, " +
                    "disciplinary record, suspensions and company belongings. A MANAGER may print " +
                    "only a direct report's profile. Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "404", description = "Employee not found, or not a direct report of a manager caller.")
    @GetMapping(value = "/employees/{id}/profile", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> employeeProfile(@PathVariable long id) {
        byte[] pdf = profiles.render(id, resolveManagerScope());
        return pdfResponse(pdf, "employee-profile-%d.pdf".formatted(id));
    }

    @Operation(summary = "Employee ID card — PDF",
            description = "One CR80 card (85.6 x 54 mm) per page: letterhead, photo, card number, name, " +
                    "parentage, department, section, designation, joining date and CNIC. A MANAGER may " +
                    "print only a direct report's card. Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "404", description = "Employee not found, or not a direct report of a manager caller.")
    @GetMapping(value = "/employees/{id}/id-card", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> employeeCard(@PathVariable long id) {
        byte[] pdf = cards.render(id, resolveManagerScope());
        return pdfResponse(pdf, "employee-card-%d.pdf".formatted(id));
    }

    @Operation(summary = "Employee list — Excel",
            description = "One row per employee matching the common filter (status, department, section, " +
                    "designation, card range, card list). `columns=BASIC` (default) or `FULL` (adds family, " +
                    "contact, CNIC dates, addresses, statutory numbers, age and total service). " +
                    "`missingPhoto=true` keeps only employees without a photo. Salary and bank columns live in " +
                    "payroll's list. A MANAGER sees only direct reports. Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/list", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> employeeList(
            @ParameterObject @Valid EmployeeReportFilter filter,
            @Parameter(description = "BASIC (default) or FULL.") @RequestParam(required = false) EmployeeListColumns columns,
            @Parameter(description = "Only employees without a photo.") @RequestParam(defaultValue = "false") boolean missingPhoto) {
        Long scope = resolveManagerScope();
        return xlsx("employee-list-%s.xlsx".formatted(LocalDate.now()),
                out -> employeeList.writeXlsx(out, filter, columns, missingPhoto, scope));
    }

    @Operation(summary = "Department wise — PDF",
            description = "Each department's headcount broken down by one attribute (`groupBy`: GRADE (default), " +
                    "EMPLOYMENT_TYPE, CATEGORY, GENDER, MARITAL_STATUS, RELIGION, BLOOD_GROUP, QUALIFICATION, " +
                    "AGE_BAND, SERVICE_BAND), split by gender with each value's share, plus a company-wide block. " +
                    "Takes the common employee filter. Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/department-wise", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> departmentWise(
            @ParameterObject @Valid EmployeeReportFilter filter,
            @Parameter(description = "Attribute to break departments down by.") @RequestParam(required = false) DepartmentWiseGroupBy groupBy) {
        byte[] pdf = departmentWise.render(filter, groupBy, resolveManagerScope());
        return pdfResponse(pdf, "department-wise-%s-%s.pdf".formatted(
                (groupBy == null ? DepartmentWiseGroupBy.GRADE : groupBy).name().toLowerCase(), LocalDate.now()));
    }

    @Operation(summary = "Change log — Excel",
            description = "Department / section / designation / grade changes from the employee history, with " +
                    "old and new value, reason and who recorded it. `field` limits to one of them; `from`/`to` " +
                    "(effective date) default to the last year. Takes the common employee filter. " +
                    "Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "422", description = "`from` is after `to`.")
    @GetMapping(value = "/employees/change-log", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> changeLog(
            @ParameterObject @Valid EmployeeReportFilter filter,
            @Parameter(description = "DEPARTMENT, SECTION, DESIGNATION or GRADE; omit for all.") @RequestParam(required = false) ChangeLogField field,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long scope = resolveManagerScope();
        return xlsx("change-log-%s.xlsx".formatted(LocalDate.now()),
                out -> changeLog.writeXlsx(out, filter, field, from, to, scope));
    }

    @Operation(summary = "Employee ID cards (bulk) — PDF",
            description = "One CR80 card per page for every employee matching the common filter, in card-number " +
                    "order. At most 500 cards per run. Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "422", description = "No employee matches, or more than 500 do — narrow the filter.")
    @GetMapping(value = "/employees/id-cards", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> employeeCards(@ParameterObject @Valid EmployeeReportFilter filter) {
        byte[] pdf = cards.renderMany(filter, resolveManagerScope());
        return pdfResponse(pdf, "employee-cards-%s.pdf".formatted(LocalDate.now()));
    }

    @Operation(summary = "Confirmation letter — PDF",
            description = "Services confirmed from the employee's confirmation date, on the company letterhead. " +
                    "A MANAGER may print only a direct report's letter. Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "404", description = "Employee not found, or not a direct report of a manager caller.")
    @ApiResponse(responseCode = "422", description = "No confirmation date on file (`missingFields`).")
    @GetMapping(value = "/employees/{id}/confirmation-letter", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> confirmationLetter(@PathVariable long id) {
        byte[] pdf = employmentLetters.confirmationLetter(id, resolveManagerScope());
        return pdfResponse(pdf, "confirmation-letter-%d.pdf".formatted(id));
    }

    @Operation(summary = "Confirmation letters for a month — PDF",
            description = "One confirmation letter per page for every employee matching the common filter whose " +
                    "confirmation date falls in `month` (yyyy-MM). At most 300 per run. Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "422", description = "`month` missing, nobody confirmed that month, or more than 300 match.")
    @GetMapping(value = "/employees/confirmation-letters", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> confirmationLetters(
            @Parameter(description = "Month of confirmation, yyyy-MM.", example = "2026-09")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @ParameterObject @Valid EmployeeReportFilter filter) {
        byte[] pdf = employmentLetters.confirmationLetters(month, filter, resolveManagerScope());
        return pdfResponse(pdf, "confirmation-letters-%s.pdf".formatted(month));
    }

    @Operation(summary = "Relieving letter — PDF",
            description = "For an employee who has left: relieved from service on the last working day of the most " +
                    "recent exit, with service period and exit reason. Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "404", description = "Employee not found, or not a direct report of a manager caller.")
    @ApiResponse(responseCode = "422", description = "Employee is still in service, or no exit with a last working day.")
    @GetMapping(value = "/employees/{id}/relieving-letter", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> relievingLetter(@PathVariable long id) {
        byte[] pdf = employmentLetters.relievingLetter(id, resolveManagerScope());
        return pdfResponse(pdf, "relieving-letter-%d.pdf".formatted(id));
    }

    @Operation(summary = "Character certificate — PDF",
            description = "Good conduct during service, for a current or left employee. Refused while any " +
                    "disciplinary action is upheld or undecided (withdrawn ones don't count). A MANAGER may print " +
                    "only a direct report's certificate. Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "404", description = "Employee not found, or not a direct report of a manager caller.")
    @ApiResponse(responseCode = "422", description = "Disciplinary record on file; `disciplinaryActions` lists the ids.")
    @GetMapping(value = "/employees/{id}/character-certificate", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> characterCertificate(@PathVariable long id) {
        byte[] pdf = employmentLetters.characterCertificate(id, resolveManagerScope());
        return pdfResponse(pdf, "character-certificate-%d.pdf".formatted(id));
    }

    @Operation(summary = "Disability list — Excel",
            description = "Every recorded disability of employees matching the common filter: disability, severity, " +
                    "description, whether a certificate is on file. Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/disabilities", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> disabilities(@ParameterObject @Valid EmployeeReportFilter filter) {
        Long scope = resolveManagerScope();
        return xlsx("disability-list-%s.xlsx".formatted(LocalDate.now()),
                out -> registers.writeDisabilitiesXlsx(out, filter, scope));
    }

    @Operation(summary = "Rejoining list — Excel",
            description = "Reinstatements effective in the period (default: last year) with the exit they reversed " +
                    "and the gap in days. Takes the common employee filter. Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "422", description = "`from` is after `to`.")
    @GetMapping(value = "/employees/rejoinings", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> rejoinings(
            @ParameterObject @Valid EmployeeReportFilter filter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long scope = resolveManagerScope();
        return xlsx("rejoining-list-%s.xlsx".formatted(LocalDate.now()),
                out -> registers.writeRejoiningsXlsx(out, filter, from, to, scope));
    }

    @Operation(summary = "Joiners — Excel",
            description = "Employees whose joining_date falls in the date range. " +
                    "Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/joiners", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> joiners(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("joiners-%s_to_%s.xlsx".formatted(from, to),
                out -> reports.writeJoinersXlsx(out, from, to));
    }

    @Operation(summary = "Leavers — Excel",
            description = "Completed exits whose last working day falls in the range. " +
                    "Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/leavers", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> leavers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xlsx("leavers-%s_to_%s.xlsx".formatted(from, to),
                out -> reports.writeLeaversXlsx(out, from, to));
    }

    @Operation(summary = "Contract expiries — Excel",
            description = "Active contracts with end_date in the next N days. " +
                    "Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/contract-expiries", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> contractExpiries(
            @Parameter(description = "Window in days; common values are 30 / 60 / 90 / 180.")
            @RequestParam int days) {
        return xlsx("contract-expiries-%dd.xlsx".formatted(days),
                out -> reports.writeContractExpiriesXlsx(out, days));
    }

    @Operation(summary = "Document expiries — Excel",
            description = "CNIC and certification expiries within the next N days. " +
                    "Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/document-expiries", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> documentExpiries(
            @Parameter(description = "Window in days; common values are 30 / 60 / 90 / 180.")
            @RequestParam int days) {
        return xlsx("document-expiries-%dd.xlsx".formatted(days),
                out -> reports.writeDocumentExpiriesXlsx(out, days));
    }

    @Operation(summary = "Confirmations due — Excel",
            description = "Active probation contracts ending in the next N days. " +
                    "Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/confirmations-due", produces = XLSX_TYPE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<StreamingResponseBody> confirmationsDue(@RequestParam int days) {
        return xlsx("confirmations-due-%dd.xlsx".formatted(days),
                out -> reports.writeConfirmationsDueXlsx(out, days));
    }

    @Operation(summary = "Service letter — PDF",
            description = "Letter of employment for a current employee. Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/{id}/service-letter", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> serviceLetter(@PathVariable long id) {
        byte[] pdf = reports.renderServiceLetter(id);
        return pdfResponse(pdf, "service-letter-%d.pdf".formatted(id));
    }

    @Operation(summary = "Experience letter — PDF",
            description = "Letter for a terminated employee. Requires `employee.reports.run`.")
    @GetMapping(value = "/employees/{id}/experience-letter", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> experienceLetter(@PathVariable long id) {
        byte[] pdf = reports.renderExperienceLetter(id);
        return pdfResponse(pdf, "experience-letter-%d.pdf".formatted(id));
    }

    @Operation(summary = "Appointment letter — PDF",
            description = "Formal letter of appointment: letterhead, addressee, numbered terms from the " +
                    "letter-clause templates, salary table (basic + recurring earnings from payroll, gross in " +
                    "figures and words) and a signature / acceptance block. Query parameters override the " +
                    "employee data or letter-settings defaults for this letter only. A MANAGER may print only a " +
                    "direct report's letter. `includeSalary=true` (default) additionally requires `payroll.read`. " +
                    "Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "403", description = "Missing `employee.reports.run`, or `payroll.read` with includeSalary=true.")
    @ApiResponse(responseCode = "404", description = "Employee not found, or not a direct report of a manager caller.")
    @ApiResponse(responseCode = "422", description = "APPOINTMENT_LETTER_DATA_MISSING — `detail` and `missingFields` list the fields to complete.")
    @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — payroll unreachable while includeSalary=true.")
    @GetMapping(value = "/employees/{id}/appointment-letter", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> appointmentLetter(@PathVariable long id,
                                                    @ParameterObject @Valid AppointmentLetterRequest request) {
        AppointmentLetterService.Rendered letter = appointmentLetters.render(id, request, resolveManagerScope());
        return pdfResponse(letter.pdf(), letter.filename());
    }

    @Operation(summary = "Appointment letter — PDF (overrides in body)",
            description = "Same letter and rules as the GET, with the overrides as a JSON body — for the HR " +
                    "preview/edit drawer. Read-only: do not send an `Idempotency-Key` (a replayed response " +
                    "would not carry the PDF intact). Requires `employee.reports.run`.")
    @ApiResponse(responseCode = "403", description = "Missing `employee.reports.run`, or `payroll.read` with includeSalary=true.")
    @ApiResponse(responseCode = "404", description = "Employee not found, or not a direct report of a manager caller.")
    @ApiResponse(responseCode = "422", description = "APPOINTMENT_LETTER_DATA_MISSING — `detail` and `missingFields` list the fields to complete.")
    @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — payroll unreachable while includeSalary=true.")
    @PostMapping(value = "/employees/{id}/appointment-letter", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("employee.reports.run")
    public ResponseEntity<byte[]> appointmentLetterPreview(@PathVariable long id,
                                                           @Valid @RequestBody(required = false) AppointmentLetterRequest request) {
        AppointmentLetterService.Rendered letter = appointmentLetters.render(id, request, resolveManagerScope());
        return pdfResponse(letter.pdf(), letter.filename());
    }

    @FunctionalInterface
    private interface XlsxBody {
        void write(java.io.OutputStream out) throws java.io.IOException;
    }

    /**
     * The body is written on an async thread with no inbound request, so the
     * caller is carried over explicitly — otherwise "Generated by" and the
     * audit row would say "system".
     */
    private static ResponseEntity<StreamingResponseBody> xlsx(String filename, XlsxBody body) {
        CurrentUser caller = CurrentUserHolder.get().orElse(null);
        StreamingResponseBody srb = out -> {
            if (caller != null) CurrentUserHolder.set(caller);
            try {
                body.write(out);
            } finally {
                if (caller != null) CurrentUserHolder.clear();
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_TYPE))
                .body(srb);
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private Long resolveManagerScope() {
        CurrentUser user = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required."));
        // Org-wide employee visibility (employee.read) -> unscoped, sees every
        // reportee. HR/ADMIN hold it; MANAGER does not (V0024) and is scoped to
        // their own reports below.
        if (user.hasPermission("employee.read")) {
            return null;
        }
        Employee me = employees.findByUserId(user.userId())
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                        "no employee record linked to caller userId=" + user.userId()));
        return me.getId();
    }
}

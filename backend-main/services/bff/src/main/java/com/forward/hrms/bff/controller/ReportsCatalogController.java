package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.dto.response.ReportCatalogEntry;
import com.forward.hrms.bff.dto.response.ReportCatalogEntry.Parameter;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Reports catalog",
        description = "Single endpoint that drives the /reports page on the FE.")
@RestController
@RequestMapping("/bff/reports")
public class ReportsCatalogController {

    private static final List<ReportCatalogEntry> ALL = buildCatalog();

    @Operation(summary = "Reports catalog — filtered by caller permissions",
            description = "Returns every report the caller is allowed to run, in the order the "
                    + "FE should render them. Each entry carries the gateway path, the permission "
                    + "required (already enforced), the output format, sync/async mode, and the "
                    + "parameter manifest the FE renders into a form.")
    @ApiResponse(responseCode = "200", description = "List of available reports for this caller.")
    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public List<ReportCatalogEntry> catalog() {
        CurrentUser cu = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError(
                        CommonErrorCode.UNAUTHENTICATED, "no current user"));
        return ALL.stream()
                .filter(e -> cu.hasPermission(e.permission()))
                .toList();
    }

    /**
     * The common employee filter (employee-service {@code EmployeeReportFilter}) followed by
     * the report's own parameters. One definition so every list report offers the same panel.
     */
    private static List<Parameter> withEmployeeFilter(Parameter... own) {
        List<Parameter> p = new ArrayList<>(List.of(
                new Parameter("status", "query", "enum", false, List.of("ACTIVE", "INACTIVE", "ALL"),
                        "ACTIVE = not left (default), INACTIVE = left, ALL."),
                new Parameter("departmentId", "query", "int", false, null, "Department id."),
                new Parameter("sectionId", "query", "int", false, null, "Section id."),
                new Parameter("designationId", "query", "int", false, null, "Designation id."),
                new Parameter("cardFrom", "query", "string", false, null, "Card range start (inclusive)."),
                new Parameter("cardTo", "query", "string", false, null, "Card range end (inclusive)."),
                new Parameter("cards", "query", "string", false, null, "Comma-separated card numbers.")));
        p.addAll(List.of(own));
        return List.copyOf(p);
    }

    private static List<ReportCatalogEntry> buildCatalog() {
        return List.of(
                // ---------- Employee — employee.reports.run ----------
                new ReportCatalogEntry(
                        "employee.directory", "employee",
                        "Employee directory",
                        "All non-terminated employees with contact + assignment columns. Manager scope auto-applied.",
                        "csv", "sync", "GET", "/reports/employees/directory",
                        "employee.reports.run", false,
                        List.of()),
                new ReportCatalogEntry(
                        "employee.headcount-snapshot", "employee",
                        "Headcount snapshot",
                        "Headcount by department and grade as of a date.",
                        "pdf", "sync", "GET", "/reports/employees/headcount-snapshot",
                        "employee.reports.run", false,
                        List.of(new Parameter("asOf", "query", "date", false, null,
                                "Snapshot date. Defaults to today when omitted."))),
                new ReportCatalogEntry(
                        "employee.department-strength", "employee",
                        "Department strength",
                        "Current headcount by department, section and designation, split by gender. Manager scope auto-applied.",
                        "pdf", "sync", "GET", "/reports/employees/department-strength",
                        "employee.reports.run", false,
                        List.of()),
                new ReportCatalogEntry(
                        "employee.joiners", "employee",
                        "New joiners",
                        "Employees with joining date inside the range.",
                        "xlsx", "sync", "GET", "/reports/employees/joiners",
                        "employee.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),
                new ReportCatalogEntry(
                        "employee.leavers", "employee",
                        "Leavers",
                        "Completed exits with last working day inside the range.",
                        "xlsx", "sync", "GET", "/reports/employees/leavers",
                        "employee.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),
                new ReportCatalogEntry(
                        "employee.contract-expiries", "employee",
                        "Contract expiries",
                        "Active contracts ending within N days.",
                        "xlsx", "sync", "GET", "/reports/employees/contract-expiries",
                        "employee.reports.run", false,
                        List.of(new Parameter("days", "query", "int", true, null,
                                "Look-ahead window in days."))),
                new ReportCatalogEntry(
                        "employee.document-expiries", "employee",
                        "Document expiries",
                        "CNICs and certifications expiring within N days.",
                        "xlsx", "sync", "GET", "/reports/employees/document-expiries",
                        "employee.reports.run", false,
                        List.of(new Parameter("days", "query", "int", true, null,
                                "Look-ahead window in days."))),
                new ReportCatalogEntry(
                        "employee.confirmations-due", "employee",
                        "Confirmations due",
                        "Active probation contracts ending within N days.",
                        "xlsx", "sync", "GET", "/reports/employees/confirmations-due",
                        "employee.reports.run", false,
                        List.of(new Parameter("days", "query", "int", true, null,
                                "Look-ahead window in days."))),
                new ReportCatalogEntry(
                        "employee.profile", "employee",
                        "Employee profile",
                        "Printable personnel file: photo, personal, employment, statutory numbers, history, qualifications, documents, disciplinary and belongings.",
                        "pdf", "sync", "GET", "/reports/employees/{id}/profile",
                        "employee.reports.run", false,
                        List.of(new Parameter("id", "path", "int", true, null, "Employee id."))),
                new ReportCatalogEntry(
                        "employee.list", "employee",
                        "Employee list",
                        "Every employee matching the filter. BASIC or FULL columns (family, contact, CNIC dates, addresses, age, service); optional missing-photo only.",
                        "xlsx", "sync", "GET", "/reports/employees/list",
                        "employee.reports.run", false,
                        withEmployeeFilter(
                                new Parameter("columns", "query", "enum", false, List.of("BASIC", "FULL"),
                                        "Column set. Defaults to BASIC."),
                                new Parameter("missingPhoto", "query", "enum", false, List.of("false", "true"),
                                        "Only employees without a photo."))),
                new ReportCatalogEntry(
                        "employee.department-wise", "employee",
                        "Department wise",
                        "Each department's headcount by grade, employment type, category, gender, marital status, religion, blood group, qualification, age or service band.",
                        "pdf", "sync", "GET", "/reports/employees/department-wise",
                        "employee.reports.run", false,
                        withEmployeeFilter(
                                new Parameter("groupBy", "query", "enum", false,
                                        List.of("GRADE", "EMPLOYMENT_TYPE", "CATEGORY", "GENDER", "MARITAL_STATUS",
                                                "RELIGION", "BLOOD_GROUP", "QUALIFICATION", "AGE_BAND", "SERVICE_BAND"),
                                        "Attribute to break departments down by. Defaults to GRADE."))),
                new ReportCatalogEntry(
                        "employee.change-log", "employee",
                        "Change log",
                        "Department, section, designation and grade changes with old and new value, reason and who recorded it.",
                        "xlsx", "sync", "GET", "/reports/employees/change-log",
                        "employee.reports.run", false,
                        withEmployeeFilter(
                                new Parameter("field", "query", "enum", false,
                                        List.of("DEPARTMENT", "SECTION", "DESIGNATION", "GRADE"),
                                        "Limit to one field; omit for all."),
                                new Parameter("from", "query", "date", false, null,
                                        "Effective from (inclusive). Defaults to one year before `to`."),
                                new Parameter("to", "query", "date", false, null,
                                        "Effective to (inclusive). Defaults to today."))),
                new ReportCatalogEntry(
                        "employee.id-cards", "employee",
                        "Employee ID cards (bulk)",
                        "One CR80 card per page for every employee matching the filter. At most 500 per run.",
                        "pdf", "sync", "GET", "/reports/employees/id-cards",
                        "employee.reports.run", false,
                        withEmployeeFilter()),
                new ReportCatalogEntry(
                        "employee.id-card", "employee",
                        "Employee ID card",
                        "CR80 identity card: photo, card number, name, parentage, department, section, designation, joining date, CNIC.",
                        "pdf", "sync", "GET", "/reports/employees/{id}/id-card",
                        "employee.reports.run", false,
                        List.of(new Parameter("id", "path", "int", true, null, "Employee id."))),
                new ReportCatalogEntry(
                        "employee.service-letter", "employee",
                        "Service letter",
                        "Letter for a currently-employed person.",
                        "pdf", "sync", "GET", "/reports/employees/{id}/service-letter",
                        "employee.reports.run", false,
                        List.of(new Parameter("id", "path", "int", true, null, "Employee id."))),
                new ReportCatalogEntry(
                        "employee.appointment-letter", "employee",
                        "Appointment letter",
                        "Appointment letter in the company form layout: personal and official information, "
                                + "salary (needs payroll.read), signatures. POST the same fields as JSON to preview.",
                        "pdf", "sync", "GET", "/reports/employees/{id}/appointment-letter",
                        "employee.reports.run", false,
                        List.of(
                                new Parameter("id", "path", "int", true, null, "Employee id."),
                                new Parameter("letterDate", "query", "date", false, null,
                                        "Date printed on the letter. Defaults to today."),
                                new Parameter("reportingDate", "query", "date", false, null,
                                        "Date of joining. Defaults to the employee's joining date."),
                                new Parameter("probationMonths", "query", "int", false, null,
                                        "0-24. Defaults to the active probation contract, else policy default."),
                                new Parameter("includeSalary", "query", "enum", false, List.of("true", "false"),
                                        "Print basic salary and allowances. Defaults to true; needs payroll.read."),
                                new Parameter("refNo", "query", "string", false, null,
                                        "Defaults to FS/HR/APPT/{year}/{employeeCode}."))),
                new ReportCatalogEntry(
                        "employee.confirmation-letter", "employee",
                        "Confirmation letter",
                        "Services confirmed from the employee's confirmation date.",
                        "pdf", "sync", "GET", "/reports/employees/{id}/confirmation-letter",
                        "employee.reports.run", false,
                        List.of(new Parameter("id", "path", "int", true, null, "Employee id."))),
                new ReportCatalogEntry(
                        "employee.confirmation-letters", "employee",
                        "Confirmation letters (by month)",
                        "One confirmation letter per page for everyone confirmed in the month. At most 300 per run.",
                        "pdf", "sync", "GET", "/reports/employees/confirmation-letters",
                        "employee.reports.run", false,
                        withEmployeeFilter(new Parameter("month", "query", "string", true, null,
                                "Month of confirmation, yyyy-MM."))),
                new ReportCatalogEntry(
                        "employee.relieving-letter", "employee",
                        "Relieving letter",
                        "For an employee who has left: relieved from service on the last working day, with service period.",
                        "pdf", "sync", "GET", "/reports/employees/{id}/relieving-letter",
                        "employee.reports.run", false,
                        List.of(new Parameter("id", "path", "int", true, null, "Employee id."))),
                new ReportCatalogEntry(
                        "employee.character-certificate", "employee",
                        "Character certificate",
                        "Good conduct during service, for a current or left employee. Refused while a disciplinary action is upheld or undecided.",
                        "pdf", "sync", "GET", "/reports/employees/{id}/character-certificate",
                        "employee.reports.run", false,
                        List.of(new Parameter("id", "path", "int", true, null, "Employee id."))),
                new ReportCatalogEntry(
                        "employee.disability-list", "employee",
                        "Disability list",
                        "Every recorded disability with severity and whether a certificate is on file.",
                        "xlsx", "sync", "GET", "/reports/employees/disabilities",
                        "employee.reports.run", false,
                        withEmployeeFilter()),
                new ReportCatalogEntry(
                        "employee.rejoining-list", "employee",
                        "Rejoining list",
                        "Reinstatements in the period with the exit they reversed and the gap in days.",
                        "xlsx", "sync", "GET", "/reports/employees/rejoinings",
                        "employee.reports.run", false,
                        withEmployeeFilter(
                                new Parameter("from", "query", "date", false, null, "Default: one year before `to`."),
                                new Parameter("to", "query", "date", false, null, "Default: today."))),
                new ReportCatalogEntry(
                        "employee.experience-letter", "employee",
                        "Experience letter",
                        "Letter for a terminated employee.",
                        "pdf", "sync", "GET", "/reports/employees/{id}/experience-letter",
                        "employee.reports.run", false,
                        List.of(new Parameter("id", "path", "int", true, null,
                                "Employee id (must be in TERMINATED status)."))),

                // ---------- Attendance — attendance.reports.run ----------
                new ReportCatalogEntry(
                        "attendance.daily", "attendance",
                        "Daily attendance",
                        "Every employee's record for the named date.",
                        "xlsx", "sync", "GET", "/reports/attendance/daily",
                        "attendance.reports.run", false,
                        List.of(new Parameter("date", "query", "date", true, null, "Target date."))),
                new ReportCatalogEntry(
                        "attendance.monthly", "attendance",
                        "Monthly attendance summary",
                        "Per-employee status counts for the named month.",
                        "xlsx", "sync", "GET", "/reports/attendance/monthly",
                        "attendance.reports.run", false,
                        List.of(
                                new Parameter("year", "query", "int", true, null, "Calendar year."),
                                new Parameter("month", "query", "int", true, null, "1-12."))),
                new ReportCatalogEntry(
                        "attendance.late-comers", "attendance",
                        "Late comers",
                        "Aggregated late-day counts per employee, ranked by frequency.",
                        "xlsx", "sync", "GET", "/reports/attendance/late-comers",
                        "attendance.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),
                new ReportCatalogEntry(
                        "attendance.absentee-log", "attendance",
                        "Absentee log",
                        "ABSENT records inside the range.",
                        "xlsx", "sync", "GET", "/reports/attendance/absentee-log",
                        "attendance.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),
                new ReportCatalogEntry(
                        "attendance.ot-register", "attendance",
                        "Overtime register",
                        "OT requests and computed OT minutes inside the range.",
                        "xlsx", "sync", "GET", "/reports/attendance/ot-register",
                        "attendance.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),
                new ReportCatalogEntry(
                        "attendance.regularization-log", "attendance",
                        "Regularization log",
                        "Every regularization request inside the range, optionally filtered by status.",
                        "xlsx", "sync", "GET", "/reports/attendance/regularization-log",
                        "attendance.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."),
                                new Parameter("status", "query", "enum", false,
                                        List.of("SUBMITTED", "APPROVED", "REJECTED", "WITHDRAWN"),
                                        "Restrict to one status. All when omitted."))),
                new ReportCatalogEntry(
                        "attendance.missing-punch", "attendance",
                        "Missing punches",
                        "PRESENT records with at least one missing punch inside the range.",
                        "xlsx", "sync", "GET", "/reports/attendance/missing-punch",
                        "attendance.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),
                new ReportCatalogEntry(
                        "attendance.shift-roster", "attendance",
                        "Shift roster",
                        "Active shift assignments overlapping the range.",
                        "xlsx", "sync", "GET", "/reports/attendance/shift-roster",
                        "attendance.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),

                // ---------- Leaves — attendance.leaves.reports.run ----------
                new ReportCatalogEntry(
                        "leaves.balance", "leaves",
                        "Leave balance",
                        "Per employee × leave type — allocated, used, pending, carried.",
                        "xlsx", "sync", "GET", "/reports/leaves/balance",
                        "attendance.leaves.reports.run", false,
                        List.of(new Parameter("year", "query", "int", true, null, "Calendar year."))),
                new ReportCatalogEntry(
                        "leaves.taken", "leaves",
                        "Leave taken",
                        "Approved leave days inside the range.",
                        "xlsx", "sync", "GET", "/reports/leaves/taken",
                        "attendance.leaves.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),
                new ReportCatalogEntry(
                        "leaves.application-log", "leaves",
                        "Leave application log",
                        "Every leave request and its status inside the range.",
                        "xlsx", "sync", "GET", "/reports/leaves/application-log",
                        "attendance.leaves.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."))),
                new ReportCatalogEntry(
                        "leaves.carry-forward", "leaves",
                        "Carry-forward forecast",
                        "Projected carry-forward into next year given current allocations and policy.",
                        "xlsx", "sync", "GET", "/reports/leaves/carry-forward",
                        "attendance.leaves.reports.run", false,
                        List.of(new Parameter("year", "query", "int", true, null, "Calendar year."))),

                // ---------- Payroll — payroll.reports.run ----------
                new ReportCatalogEntry(
                        "payroll.payslip-pdf-url", "payroll",
                        "Payslip PDF URL",
                        "Signed MinIO URL for an already-generated payslip PDF.",
                        "json", "sync", "GET", "/reports/payroll/payslips/{id}/pdf-url",
                        "payroll.reports.run", false,
                        List.of(new Parameter("id", "path", "int", true, null, "Payslip id."))),
                new ReportCatalogEntry(
                        "payroll.eobi-return", "payroll",
                        "EOBI return",
                        "Statutory EOBI layout. EOBI-enrolled employees with non-zero contribution.",
                        "pdf", "sync", "GET", "/reports/payroll/eobi-return",
                        "payroll.reports.run", true,
                        List.of(
                                new Parameter("year", "query", "int", true, null, "Calendar year."),
                                new Parameter("month", "query", "int", true, null, "1-12."))),
                new ReportCatalogEntry(
                        "payroll.pessi-return", "payroll",
                        "PESSI return",
                        "Statutory PESSI layout. Employer-only contributions.",
                        "pdf", "sync", "GET", "/reports/payroll/pessi-return",
                        "payroll.reports.run", true,
                        List.of(
                                new Parameter("year", "query", "int", true, null, "Calendar year."),
                                new Parameter("month", "query", "int", true, null, "1-12."))),
                new ReportCatalogEntry(
                        "payroll.income-tax-deducted", "payroll",
                        "Income tax deducted",
                        "Period total plus fiscal-year cumulative. Bounds must lie in the same fiscal year.",
                        "xlsx", "sync", "GET", "/reports/payroll/income-tax-deducted",
                        "payroll.reports.run", true,
                        List.of(
                                new Parameter("fromYear", "query", "int", true, null, "Calendar year of lower bound."),
                                new Parameter("fromMonth", "query", "int", true, null, "1-12 of lower bound."),
                                new Parameter("toYear", "query", "int", true, null, "Calendar year of upper bound."),
                                new Parameter("toMonth", "query", "int", true, null, "1-12 of upper bound."))),
                new ReportCatalogEntry(
                        "payroll.variance", "payroll",
                        "Payroll variance",
                        "Current run vs prior run. Defaults to same-FY immediate predecessor when priorRunId omitted.",
                        "xlsx", "sync", "GET", "/reports/payroll/variance",
                        "payroll.reports.run", false,
                        List.of(
                                new Parameter("currentRunId", "query", "int", true, null, "Payroll run id (current)."),
                                new Parameter("priorRunId", "query", "int", false, null,
                                        "Payroll run id (prior). Defaults to the same-FY immediate predecessor."))),
                new ReportCatalogEntry(
                        "payroll.advances-outstanding", "payroll",
                        "Outstanding advances",
                        "Two sheets — permanent and short-term advances still outstanding.",
                        "xlsx", "sync", "GET", "/reports/payroll/advances-outstanding",
                        "payroll.reports.run", false,
                        List.of()),
                new ReportCatalogEntry(
                        "payroll.off-cycle-payments", "payroll",
                        "Off-cycle payments",
                        "Off-cycle payments inside the range, optionally filtered by transaction source.",
                        "xlsx", "sync", "GET", "/reports/payroll/off-cycle-payments",
                        "payroll.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", true, null, "Inclusive lower bound."),
                                new Parameter("to", "query", "date", true, null, "Inclusive upper bound."),
                                // Internal transaction source, NOT the HR-facing payment-type
                                // catalogue — HR's selectable types live in employee-service's
                                // off_cycle_payment_type master and are not enumerable here.
                                // (The previous list — INCREMENT / ADJUSTMENT / ENCASHMENT —
                                // named values this column has never held.)
                                new Parameter("source", "query", "enum", false,
                                        List.of("HR", "BONUS", "FINAL_SETTLEMENT"),
                                        "Restrict to one transaction source. All when omitted."))),
                new ReportCatalogEntry(
                        "payroll.pay-register", "payroll",
                        "Pay register (per run)",
                        "Async — every payslip × every component, footed.",
                        "xlsx", "async", "POST", "/reports/payroll/pay-register/jobs",
                        "payroll.reports.run", false,
                        List.of(new Parameter("payrollRunId", "body", "int", true, null,
                                "Payroll run id."))),
                new ReportCatalogEntry(
                        "payroll.salary-certificate", "payroll",
                        "Salary certificate",
                        "For banks / embassies: current fixed monthly salary (gross in figures and words) and salary account.",
                        "pdf", "sync", "GET", "/reports/payroll/employees/{id}/salary-certificate",
                        "payroll.reports.run", false,
                        List.of(
                                new Parameter("id", "path", "int", true, null, "Employee id."),
                                new Parameter("addressee", "query", "string", false, null,
                                        "e.g. The Branch Manager, Meezan Bank, Sialkot. Default: To Whom It May Concern."),
                                new Parameter("purpose", "query", "string", false, null,
                                        "e.g. car loan application."))),
                new ReportCatalogEntry(
                        "payroll.increment-list", "payroll",
                        "Salary increment list",
                        "Every basic-salary revision in the period: previous and new basic, increment and %, reason.",
                        "xlsx", "sync", "GET", "/reports/payroll/increments",
                        "payroll.reports.run", false,
                        List.of(
                                new Parameter("from", "query", "date", false, null, "Default: first of this month."),
                                new Parameter("to", "query", "date", false, null, "Default: end of from's month."),
                                new Parameter("departmentId", "query", "int", false, null, "Department id."))),
                new ReportCatalogEntry(
                        "payroll.gratuity-dates", "payroll",
                        "Joining vs gratuity date",
                        "Active employees whose gratuity is counted from a date other than their joining date.",
                        "xlsx", "sync", "GET", "/reports/payroll/gratuity-dates",
                        "payroll.reports.run", false,
                        List.of(
                                new Parameter("onlyDifferent", "query", "enum", false, List.of("true", "false"),
                                        "Only employees where the dates differ. Default true."),
                                new Parameter("departmentId", "query", "int", false, null, "Department id."))),
                new ReportCatalogEntry(
                        "payroll.increment-letters", "payroll",
                        "Increment letters (zip)",
                        "Async — one PDF per affected employee, zipped.",
                        "zip", "async", "POST", "/reports/payroll/increment-letters/jobs",
                        "payroll.reports.run", false,
                        List.of(new Parameter("incrementBatchId", "query", "int", true, null,
                                "Increment batch id."))),
                new ReportCatalogEntry(
                        "payroll.payslips-run", "payroll",
                        "Payslips (zip per run)",
                        "Async — zips already-generated payslip PDFs for a run. Fails fast if the run has no payslips yet.",
                        "zip", "async", "POST", "/reports/payroll/payslips-run/jobs",
                        "payroll.reports.run", false,
                        List.of(new Parameter("payrollRunId", "query", "int", true, null,
                                "Payroll run id."))));
    }
}

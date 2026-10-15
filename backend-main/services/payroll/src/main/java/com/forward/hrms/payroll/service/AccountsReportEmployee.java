package com.forward.hrms.payroll.service;

import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.GradeType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.CARD_NO;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.CNIC;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.DATE_OF_JOINING;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.DEPARTMENT;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.DESIGNATION;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.EMPLOYEE_NAME;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.EMPLOYEE_STATUS;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.EMPLOYMENT_TYPE;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.FATHER_NAME;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.GRADE;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.SECTION;

/**
 * Employee fields an accounts report prints or filters on, resolved from
 * employee-service ({@code /employees/by-ids}) with catalog names from
 * {@code CatalogCache}. Ids are current placement, not as-of the report
 * period — same as the legacy views, which joined the live employee row.
 */
public record AccountsReportEmployee(
        long employeeId,
        String cardNo,
        String name,
        String fatherName,
        String cnic,
        LocalDate dateOfJoining,
        EmploymentStatus status,
        String employmentType,
        Long departmentId,
        String department,
        Long sectionId,
        String section,
        Long designationId,
        String designation,
        String grade,
        String photoUrl,
        /** Department flagged {@code is_production}; null when the department is unknown. */
        Boolean production,
        /** Grade's role bucket; null when the grade is unknown. */
        GradeType gradeType
) {

    /** Without the grade type (reports that don't filter on it). */
    public AccountsReportEmployee(long employeeId, String cardNo, String name, String fatherName, String cnic,
                                  LocalDate dateOfJoining, EmploymentStatus status, String employmentType,
                                  Long departmentId, String department, Long sectionId, String section,
                                  Long designationId, String designation, String grade, String photoUrl,
                                  Boolean production) {
        this(employeeId, cardNo, name, fatherName, cnic, dateOfJoining, status, employmentType, departmentId,
                department, sectionId, section, designationId, designation, grade, photoUrl, production, null);
    }

    /** Without the production flag (reports that don't filter by employee type). */
    public AccountsReportEmployee(long employeeId, String cardNo, String name, String fatherName, String cnic,
                                  LocalDate dateOfJoining, EmploymentStatus status, String employmentType,
                                  Long departmentId, String department, Long sectionId, String section,
                                  Long designationId, String designation, String grade, String photoUrl) {
        this(employeeId, cardNo, name, fatherName, cnic, dateOfJoining, status, employmentType, departmentId,
                department, sectionId, section, designationId, designation, grade, photoUrl, null, null);
    }

    /** Row whose employee employee-service no longer returns; still printed under "All" so totals reconcile. */
    public static AccountsReportEmployee unknown(long employeeId) {
        return new AccountsReportEmployee(employeeId, "#" + employeeId, "Unknown employee " + employeeId,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Value of one of the {@code AccountsReportColumnDto.EMPLOYEE_KEYS} columns. */
    public Object value(String key) {
        return switch (key) {
            case CARD_NO -> cardNo;
            case EMPLOYEE_NAME -> name;
            case FATHER_NAME -> fatherName;
            case CNIC -> cnic;
            case DEPARTMENT -> department;
            case SECTION -> section;
            case DESIGNATION -> designation;
            case GRADE -> grade;
            case DATE_OF_JOINING -> dateOfJoining;
            case EMPLOYEE_STATUS -> statusLabel(status);
            case EMPLOYMENT_TYPE -> employmentType;
            default -> null;
        };
    }

    /** Ledger header block. */
    public Map<String, Object> header() {
        Map<String, Object> h = new HashMap<>();
        h.put(CARD_NO, cardNo);
        h.put(EMPLOYMENT_TYPE, employmentType);
        h.put(EMPLOYEE_NAME, name);
        h.put(FATHER_NAME, fatherName);
        h.put(CNIC, cnic);
        h.put(DATE_OF_JOINING, dateOfJoining);
        h.put(DEPARTMENT, department);
        h.put(SECTION, section);
        h.put(GRADE, grade);
        h.put(DESIGNATION, designation);
        h.put(EMPLOYEE_STATUS, statusLabel(status));
        h.put("photoUrl", photoUrl);
        return h;
    }

    static String statusLabel(EmploymentStatus s) {
        if (s == null) return null;
        String lower = s.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

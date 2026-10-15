package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.ReportColumnType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/**
 * One column of an accounts report. {@code key} is the property name on
 * every row map; employee columns ({@link #EMPLOYEE_KEYS}) are filled by the
 * report service from employee-service, everything else by the report's
 * data source.
 */
@Schema(name = "AccountsReportColumn", description = "Column of an accounts report, in display order.")
public record AccountsReportColumnDto(
        @Schema(description = "Row property name.", example = "advanceAmount") String key,
        @Schema(description = "Header label.", example = "Advance Amount") String label,
        @Schema(description = "Cell type.") ReportColumnType type,
        @Schema(description = "True when the column gets a grand total.") boolean totalable
) {

    public static final String SR_NO = "srNo";
    public static final String CARD_NO = "cardNo";
    public static final String EMPLOYEE_NAME = "employeeName";
    public static final String FATHER_NAME = "fatherName";
    public static final String CNIC = "cnic";
    public static final String DEPARTMENT = "department";
    public static final String SECTION = "section";
    public static final String DESIGNATION = "designation";
    public static final String GRADE = "grade";
    public static final String DATE_OF_JOINING = "dateOfJoining";
    public static final String EMPLOYEE_STATUS = "employeeStatus";
    public static final String EMPLOYMENT_TYPE = "employmentType";
    /** Total of the allowances calculation sheet (F-7); the component columns go before it. */
    public static final String TOTAL = "total";

    public static final Set<String> EMPLOYEE_KEYS = Set.of(CARD_NO, EMPLOYEE_NAME, FATHER_NAME, CNIC, DEPARTMENT,
            SECTION, DESIGNATION, GRADE, DATE_OF_JOINING, EMPLOYEE_STATUS, EMPLOYMENT_TYPE);

    public static AccountsReportColumnDto text(String key, String label) {
        return new AccountsReportColumnDto(key, label, ReportColumnType.TEXT, false);
    }

    public static AccountsReportColumnDto integer(String key, String label) {
        return new AccountsReportColumnDto(key, label, ReportColumnType.INT, false);
    }

    public static AccountsReportColumnDto date(String key, String label) {
        return new AccountsReportColumnDto(key, label, ReportColumnType.DATE, false);
    }

    public static AccountsReportColumnDto decimal(String key, String label) {
        return new AccountsReportColumnDto(key, label, ReportColumnType.DECIMAL, false);
    }

    public static AccountsReportColumnDto month(String key, String label) {
        return new AccountsReportColumnDto(key, label, ReportColumnType.MONTH, false);
    }

    /** Amount with a grand total. */
    public static AccountsReportColumnDto amount(String key, String label) {
        return new AccountsReportColumnDto(key, label, ReportColumnType.AMOUNT, true);
    }

    /** Amount that must not be summed (rates, running balances). */
    public static AccountsReportColumnDto amountNoTotal(String key, String label) {
        return new AccountsReportColumnDto(key, label, ReportColumnType.AMOUNT, false);
    }

    public static AccountsReportColumnDto srNo() {
        return integer(SR_NO, "Sr No");
    }

    public static AccountsReportColumnDto cardNo() {
        return text(CARD_NO, "Card No");
    }

    public static AccountsReportColumnDto employeeName() {
        return text(EMPLOYEE_NAME, "Employee Name");
    }

    public static AccountsReportColumnDto department() {
        return text(DEPARTMENT, "Department");
    }

    public static AccountsReportColumnDto section() {
        return text(SECTION, "Section");
    }

    public static AccountsReportColumnDto designation() {
        return text(DESIGNATION, "Designation");
    }

    public static AccountsReportColumnDto grade() {
        return text(GRADE, "Grade");
    }

    public static AccountsReportColumnDto dateOfJoining() {
        return date(DATE_OF_JOINING, "Date of Joining");
    }

    public static AccountsReportColumnDto employeeStatus() {
        return text(EMPLOYEE_STATUS, "Employee Status");
    }

    public static AccountsReportColumnDto fatherName() {
        return text(FATHER_NAME, "Father Name");
    }

    public static AccountsReportColumnDto signature() {
        return text("signature", "Employee Signature");
    }
}

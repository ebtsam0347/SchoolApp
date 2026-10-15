package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ReportEmployeeStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Who a list-shaped employee report covers. Bound from query parameters;
 * every field is optional and the criteria AND together. Card numbers are
 * employee codes: a range compares numerically when both ends and the code
 * are numbers (legacy cards like {@code 11405}), else case-insensitively as text.
 */
@Schema(name = "EmployeeReportFilter", description = "Common filter for employee list reports. All fields optional.")
public record EmployeeReportFilter(
        @Schema(description = "ACTIVE = not terminated (default), INACTIVE = terminated, ALL.")
        ReportEmployeeStatus status,

        @Schema(description = "Department id.") Long departmentId,
        @Schema(description = "Section id.") Long sectionId,
        @Schema(description = "Designation id.") Long designationId,

        @Schema(description = "Card range start (employee code), inclusive.", example = "11000")
        @Size(max = 32) String cardFrom,

        @Schema(description = "Card range end (employee code), inclusive.", example = "11999")
        @Size(max = 32) String cardTo,

        @Schema(description = "Comma-separated card numbers; when present only these employees are included.",
                example = "11405,11406,11410")
        @Size(max = 4000) String cards
) {

    public static EmployeeReportFilter active() {
        return new EmployeeReportFilter(null, null, null, null, null, null, null);
    }

    public ReportEmployeeStatus statusOrDefault() {
        return status == null ? ReportEmployeeStatus.ACTIVE : status;
    }

    public List<String> cardList() {
        if (cards == null || cards.isBlank()) return List.of();
        return Arrays.stream(cards.split("[,\\s]+")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean matches(Employee e) {
        boolean terminated = e.getEmploymentStatus() == EmploymentStatus.TERMINATED;
        switch (statusOrDefault()) {
            case ACTIVE -> { if (terminated) return false; }
            case INACTIVE -> { if (!terminated) return false; }
            case ALL -> { }
        }
        if (departmentId != null && !departmentId.equals(e.getDepartmentId())) return false;
        if (sectionId != null && !sectionId.equals(e.getSectionId())) return false;
        if (designationId != null && !designationId.equals(e.getDesignationId())) return false;
        String code = e.getEmployeeCode();
        if (!blank(cardFrom) && (code == null || compareCards(code, cardFrom.trim()) < 0)) return false;
        if (!blank(cardTo) && (code == null || compareCards(code, cardTo.trim()) > 0)) return false;
        List<String> list = cardList();
        return list.isEmpty() || (code != null && list.stream().anyMatch(c -> c.equalsIgnoreCase(code.trim())));
    }

    /** Numeric when both are numbers, else case-insensitive text. */
    public static int compareCards(String a, String b) {
        String x = a.trim(), y = b.trim();
        if (x.matches("\\d{1,18}") && y.matches("\\d{1,18}")) return Long.compare(Long.parseLong(x), Long.parseLong(y));
        return x.toLowerCase(Locale.ROOT).compareTo(y.toLowerCase(Locale.ROOT));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

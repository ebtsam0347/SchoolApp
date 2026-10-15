package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportParam;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves report rows' employee ids to {@link AccountsReportEmployee} and
 * applies the employee-side filters (status, card range / list,
 * department, section, designation). Payroll can't join the employee
 * schema, so every accounts report filters here after its payroll query —
 * in memory, over at most a few thousand employees.
 */
@Component
@RequiredArgsConstructor
public class AccountsReportEmployees {

    private final EmployeeClient employees;
    private final CatalogCache catalog;

    public Map<Long, AccountsReportEmployee> resolve(Collection<Long> employeeIds) {
        Map<Long, EmployeeDto> dtos = employees.getDetails(employeeIds);
        CatalogCache.CatalogIndex idx = catalog.snapshot();
        Map<Long, AccountsReportEmployee> out = new HashMap<>(dtos.size() * 2);
        for (Long id : employeeIds) {
            EmployeeDto e = dtos.get(id);
            out.put(id, e == null ? AccountsReportEmployee.unknown(id) : of(e, idx));
        }
        return out;
    }

    private static AccountsReportEmployee of(EmployeeDto e, CatalogCache.CatalogIndex idx) {
        DepartmentDto dept = e.departmentId() == null ? null : idx.departments().get(e.departmentId());
        SectionDto sec = e.sectionId() == null ? null : idx.sections().get(e.sectionId());
        DesignationDto des = e.designationId() == null ? null : idx.designations().get(e.designationId());
        GradeDto grade = e.gradeId() == null ? null : idx.grades().get(e.gradeId());
        return new AccountsReportEmployee(e.id(), e.employeeCode(), e.fullName(), e.fatherName(), e.cnic(),
                e.joiningDate(), e.employmentStatus(),
                e.employmentType() == null ? null : e.employmentType().name(),
                e.departmentId(), dept == null ? null : dept.name(),
                e.sectionId(), sec == null ? null : sec.name(),
                e.designationId(), des == null ? null : des.title(),
                grade == null ? null : grade.name(),
                e.profilePictureUrl(),
                dept == null ? null : dept.isProduction(),
                grade == null ? null : grade.gradeType());
    }

    /** True when {@code e} passes every employee-side filter the report accepts. */
    public static boolean matches(AccountsReport r, AccountsReportFilter f, AccountsReportEmployee e) {
        if (r.accepts(AccountsReportParam.EMP_STATUS) && f.empStatus() != null) {
            EmploymentStatus s = e.status();
            switch (f.empStatus()) {
                case ACTIVE -> {
                    if (s == null || s == EmploymentStatus.TERMINATED) return false;
                }
                case NON_ACTIVE -> {
                    if (s != EmploymentStatus.TERMINATED) return false;
                }
                case ALL -> { }
            }
        }
        if (r.accepts(AccountsReportParam.EMPLOYEE_TYPE) && f.employeeType() != null) {
            switch (f.employeeType()) {
                case PRODUCTION -> {
                    if (!Boolean.TRUE.equals(e.production())) return false;
                }
                case NON_PRODUCTION -> {
                    if (!Boolean.FALSE.equals(e.production())) return false;
                }
                case ALL -> { }
            }
        }
        if (r.accepts(AccountsReportParam.CARD_RANGE) && (f.cardFrom() != null || f.cardTo() != null)) {
            Long card = numericCard(e.cardNo());
            if (card == null) return false;
            if (f.cardFrom() != null && card < f.cardFrom()) return false;
            if (f.cardTo() != null && card > f.cardTo()) return false;
        }
        if (r.accepts(AccountsReportParam.CARD_LIST) && f.cardList() != null && !f.cardList().isEmpty()) {
            if (!normalizedCards(f.cardList()).contains(normalizeCard(e.cardNo()))) return false;
        }
        if (r.accepts(AccountsReportParam.DEPARTMENT) && f.departmentId() != null
                && !Objects.equals(f.departmentId(), e.departmentId())) return false;
        if (r.accepts(AccountsReportParam.SECTION) && f.sectionId() != null
                && !Objects.equals(f.sectionId(), e.sectionId())) return false;
        if (r.accepts(AccountsReportParam.DESIGNATION) && f.designationId() != null
                && !Objects.equals(f.designationId(), e.designationId())) return false;
        if (r.accepts(AccountsReportParam.GRADE_TYPE) && f.gradeType() != null
                && f.gradeType() != e.gradeType()) return false;
        return true;
    }

    /**
     * Card numbers compare without leading zeros — devices and legacy
     * exports report {@code 13411}, some screens type {@code 013411}.
     */
    static String normalizeCard(String card) {
        if (card == null) return "";
        String t = card.trim();
        String stripped = t.replaceFirst("^0+(?=.)", "");
        return stripped.toUpperCase();
    }

    static Set<String> normalizedCards(List<String> cards) {
        return cards.stream().map(AccountsReportEmployees::normalizeCard).collect(Collectors.toSet());
    }

    static Long numericCard(String card) {
        if (card == null) return null;
        String t = card.trim();
        if (!t.matches("\\d{1,18}")) return null;
        return Long.parseLong(t);
    }
}

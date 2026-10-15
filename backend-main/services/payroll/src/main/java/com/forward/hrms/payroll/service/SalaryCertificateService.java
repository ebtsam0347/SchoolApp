package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Salary certificate ({@code jasper/salary-certificate.jrxml}) — the letter
 * banks, embassies and landlords ask for (legacy "Bank Certificate"). States
 * the employee's identity, placement and joining date, the current fixed
 * monthly salary (basic + flat earnings = gross, with the gross in words) and
 * the account the salary is credited to. Per-day earnings are left out: their
 * monthly amount depends on attendance, and a certificate must not overstate.
 *
 * <p>Letterhead text matches the employee-service {@code letter_settings}
 * seed; payroll can't read that schema, so it is a constant here like the
 * payslip's.
 */
@Service
@RequiredArgsConstructor
public class SalaryCertificateService {

    static final String COMPANY_NAME = "FORWARD SPORTS (PVT.) LTD.";
    static final String COMPANY_ADDRESS = "WAZIRABAD ROAD SAHOWALA STOP, SIALKOT";
    static final String COMPANY_CONTACT = "Ph.#: 052-3571800-5";
    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);

    private final EmployeeClient employees;
    private final EmployeeDisplayCache displays;
    private final EmployeeSalaryRepository salaries;
    private final EmployeeSalaryComponentRepository employeeComponents;
    private final SalaryComponentRepository components;
    private final EmployeeBankDetailsRepository bankDetails;
    private final BankRepository banks;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;

    /**
     * @param addressee first line of the letter; blank → "To Whom It May Concern"
     * @param purpose   optional, e.g. "car loan application"; printed as the reason for issue
     */
    @Transactional
    public byte[] render(long employeeId, String addressee, String purpose) {
        EmployeeClient.EmployeeBrief e = employees.getMany(List.of(employeeId)).get(employeeId);
        if (e == null || e.employmentStatus() == null) {
            throw new NotFoundError(PayrollErrorCode.EMPLOYEE_NOT_FOUND, "employee not found");
        }
        if (e.employmentStatus() == EmploymentStatus.TERMINATED) {
            throw new UnprocessableError(PayrollErrorCode.EMPLOYEE_INACTIVE,
                    "Salary certificate is for current employees; " + e.employeeCode() + " has left.");
        }
        EmployeeSalary salary = salaries.findCurrent(employeeId).orElse(null);
        if (salary == null || salary.getBasic() == null || salary.getBasic().signum() <= 0) {
            throw new UnprocessableError(PayrollErrorCode.NO_BASIC_SALARY,
                    "No basic salary on file for " + e.employeeCode() + ".", Map.of("missingFields", List.of("basicSalary")));
        }

        List<EmployeeSalaryComponent> earnings = employeeComponents.findCurrent(employeeId, ComponentType.EARNING);
        Map<Long, SalaryComponent> catalog = components.findAllById(
                earnings.stream().map(EmployeeSalaryComponent::getComponentId).distinct().toList()).stream()
                .collect(Collectors.toMap(SalaryComponent::getId, Function.identity()));
        List<Map<String, ?>> rows = new ArrayList<>();
        rows.add(row("Basic Salary", salary.getBasic()));
        BigDecimal gross = salary.getBasic();
        List<EmployeeSalaryComponent> flat = earnings.stream()
                .filter(c -> c.getAmount() != null && c.getAmount().signum() > 0)
                .filter(c -> catalog.containsKey(c.getComponentId()) && catalog.get(c.getComponentId()).getCalcType() != CalcType.PER_DAY)
                .sorted(Comparator.comparingInt((EmployeeSalaryComponent c) -> catalog.get(c.getComponentId()).getDisplayOrder())
                        .thenComparing(c -> catalog.get(c.getComponentId()).getName()))
                .toList();
        for (EmployeeSalaryComponent c : flat) {
            rows.add(row(catalog.get(c.getComponentId()).getName(), c.getAmount()));
            gross = gross.add(c.getAmount());
        }

        EmployeeDisplay display = displays.get(employeeId);
        String designation = display == null ? null : display.designationTitle();
        String department = display == null ? null : display.departmentName();

        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        Map<String, Object> p = new HashMap<>();
        p.put("companyName", COMPANY_NAME);
        p.put("companyAddress", COMPANY_ADDRESS);
        p.put("companyContact", COMPANY_CONTACT);
        p.put("refNo", "FS/ACC/SAL/%d/%s".formatted(LocalDate.now().getYear(), e.employeeCode()));
        p.put("letterDate", LETTER_DATE.format(LocalDate.now()));
        p.put("addressee", blank(addressee) ? "To Whom It May Concern" : addressee.trim());
        p.put("body", body(e, designation, department));
        p.put("closing", closing(purpose));
        p.put("grossSalary", amount(gross));
        p.put("grossInWords", AmountInWords.of(gross));
        p.put("bankLine", bankLine(employeeId));
        p.put("employeeCode", e.employeeCode());
        p.put("generatedAt", STAMP.format(LocalDateTime.now()));
        p.put("generatedBy", actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username());

        byte[] pdf = jasper.renderPdf("/jasper/salary-certificate.jrxml", p, new JRMapCollectionDataSource(rows));
        audit.emit("report", String.valueOf(employeeId), "salary-certificate.run", null,
                Map.of("employeeId", employeeId, "purpose", blank(purpose) ? "" : purpose.trim()), true);
        return pdf;
    }

    private static String body(EmployeeClient.EmployeeBrief e, String designation, String department) {
        StringBuilder sb = new StringBuilder("This is to certify that ");
        sb.append(e.fullName());
        if (!blank(e.fatherName())) sb.append(", son/daughter of ").append(e.fatherName().trim());
        if (!blank(e.cnic())) sb.append(", CNIC No. ").append(e.cnic().trim());
        sb.append(", Card No. ").append(e.employeeCode()).append(", is an employee of ").append(COMPANY_NAME);
        if (e.joiningDate() != null) sb.append(" since ").append(LETTER_DATE.format(e.joiningDate()));
        if (!blank(designation)) {
            sb.append(" and is presently working as ").append(designation);
            if (!blank(department)) sb.append(" in the ").append(department).append(" department");
        }
        return sb.append(".\n\nThe current monthly salary is as follows:").toString();
    }

    static String closing(String purpose) {
        return "This certificate is issued at the request of the employee"
                + (blank(purpose) ? "" : " for " + purpose.trim())
                + " without any financial liability on the part of the company.";
    }

    private String bankLine(long employeeId) {
        EmployeeBankDetails d = bankDetails.findByEmployeeId(employeeId).orElse(null);
        if (d == null || blank(d.getAccountNumber())) return null;
        Bank bank = d.getBankId() == null ? null : banks.findById(d.getBankId()).orElse(null);
        StringBuilder sb = new StringBuilder("The salary is credited monthly to account No. ").append(d.getAccountNumber().trim());
        if (!blank(d.getAccountTitle())) sb.append(" (").append(d.getAccountTitle().trim()).append(")");
        if (bank != null) sb.append(", ").append(bank.getName());
        if (!blank(d.getBranch())) sb.append(", ").append(d.getBranch().trim());
        return sb.append('.').toString();
    }

    private static Map<String, Object> row(String label, BigDecimal value) {
        Map<String, Object> m = new HashMap<>();
        m.put("label", label);
        m.put("amount", amount(value));
        return m;
    }

    static String amount(BigDecimal v) {
        return "Rs " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Statutory monthly returns — EOBI (employee-funded, enrolled employees
 * only) and PESSI (employer-funded, every payslip in the run). Both are
 * rendered straight from {@code payslips} columns; no separate ledger.
 *
 * <p>The output is a clean tabular PDF HR uses as the source data for
 * the regulator's portal entry. We are not claiming regulator-form
 * fidelity here.
 */
@Service
@RequiredArgsConstructor
public class StatutoryReportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String COMPANY_NAME = "Forward Sports";

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final EmployeePayrollSettingsRepository settings;
    private final EmployeeClient employees;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;

    @Transactional
    public byte[] renderEobiReturn(int periodYear, int periodMonth) {
        PayrollRun run = loadRun(periodYear, periodMonth);
        List<Payslip> all = payslips.findAllByPayrollRunId(run.getId(), Pageable.unpaged()).getContent();
        Set<Long> employeeIds = collectEmployeeIds(all);
        Set<Long> enrolled = enrolledIds(employeeIds);
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(employeeIds);

        List<Map<String, ?>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int serial = 0;
        for (Payslip p : all) {
            if (!enrolled.contains(p.getEmployeeId())) continue;
            if (zero(p.getEobi())) continue;
            EmployeeClient.EmployeeBrief b = briefs.getOrDefault(p.getEmployeeId(),
                    EmployeeClient.EmployeeBrief.placeholder(p.getEmployeeId()));
            rows.add(eobiRow(++serial, p.getEmployeeCode(),
                    nullToBlank(b.eobiNumber()), b.fullName(),
                    nullToBlank(b.cnic()), fmt(p.getEobi())));
            total = total.add(p.getEobi());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("companyName", COMPANY_NAME);
        params.put("period", periodLabel(run));
        params.put("generatedAt", STAMP.format(LocalDateTime.now()));
        params.put("totalCount", rows.size());
        params.put("totalAmount", "Rs " + fmt(total));

        byte[] pdf = jasper.renderPdf("/jasper/eobi-return.jrxml",
                params, new JRMapCollectionDataSource(rows));

        audit.emit("report", "0", "eobi-return.run", null,
                Map.of("period", periodLabel(run), "rowCount", rows.size(), "totalEobi", total),
                true);
        return pdf;
    }

    @Transactional
    public byte[] renderPessiReturn(int periodYear, int periodMonth) {
        PayrollRun run = loadRun(periodYear, periodMonth);
        List<Payslip> all = payslips.findAllByPayrollRunId(run.getId(), Pageable.unpaged()).getContent();
        Set<Long> employeeIds = collectEmployeeIds(all);
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(employeeIds);

        List<Map<String, ?>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int serial = 0;
        for (Payslip p : all) {
            if (zero(p.getPessiEmployer())) continue;
            EmployeeClient.EmployeeBrief b = briefs.getOrDefault(p.getEmployeeId(),
                    EmployeeClient.EmployeeBrief.placeholder(p.getEmployeeId()));
            rows.add(pessiRow(++serial, p.getEmployeeCode(),
                    b.fullName(), nullToBlank(b.cnic()),
                    fmt(p.getGross()), fmt(p.getPessiEmployer())));
            total = total.add(p.getPessiEmployer());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("companyName", COMPANY_NAME);
        params.put("period", periodLabel(run));
        params.put("generatedAt", STAMP.format(LocalDateTime.now()));
        params.put("totalCount", rows.size());
        params.put("totalAmount", "Rs " + fmt(total));

        byte[] pdf = jasper.renderPdf("/jasper/pessi-return.jrxml",
                params, new JRMapCollectionDataSource(rows));

        audit.emit("report", "0", "pessi-return.run", null,
                Map.of("period", periodLabel(run), "rowCount", rows.size(), "totalPessi", total),
                true);
        return pdf;
    }

    // ----- helpers -----

    private PayrollRun loadRun(int year, int month) {
        if (year < 2000 || month < 1 || month > 12) {
            throw new ConflictError(PayrollErrorCode.INVALID_PERIOD,
                    "invalid period: " + year + "-" + String.format("%02d", month));
        }
        return runs.findByPeriodYearAndPeriodMonth(year, month).orElseThrow(() ->
                new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND,
                        "no payroll run for " + year + "-" + String.format("%02d", month)));
    }

    private Set<Long> collectEmployeeIds(List<Payslip> rows) {
        Set<Long> ids = new HashSet<>(rows.size());
        for (Payslip p : rows) ids.add(p.getEmployeeId());
        return ids;
    }

    private Set<Long> enrolledIds(Set<Long> employeeIds) {
        if (employeeIds.isEmpty()) return Set.of();
        Set<Long> enrolled = new HashSet<>();
        for (EmployeePayrollSettings s : settings.findAllByEmployeeIdIn(employeeIds)) {
            if (s.isEobiEnrolled()) enrolled.add(s.getEmployeeId());
        }
        return enrolled;
    }

    private static boolean zero(BigDecimal v) { return v == null || v.signum() == 0; }
    private static String nullToBlank(String s) { return s == null ? "" : s; }

    private static String periodLabel(PayrollRun run) {
        return "%d-%02d".formatted(run.getPeriodYear(), run.getPeriodMonth());
    }

    /** Comma-grouped fixed-2-dp string ({@code "1,234.50"}). Mirrors {@code PayslipPdfService.fmt}. */
    static String fmt(BigDecimal rupees) {
        if (rupees == null) return "";
        BigDecimal scaled = rupees.setScale(2, RoundingMode.HALF_UP);
        return scaled.toPlainString().replaceAll("(?<=\\d)(?=(\\d{3})+\\.)", ",");
    }

    private static Map<String, Object> eobiRow(int serial, String employeeCode, String eobiNumber,
                                               String employeeName, String cnic, String amountText) {
        Map<String, Object> m = new HashMap<>(8);
        m.put("serial", serial);
        m.put("employeeCode", employeeCode);
        m.put("eobiNumber", eobiNumber);
        m.put("employeeName", employeeName);
        m.put("cnic", cnic);
        m.put("amountText", amountText);
        return m;
    }

    private static Map<String, Object> pessiRow(int serial, String employeeCode, String employeeName,
                                                String cnic, String wagesText, String amountText) {
        Map<String, Object> m = new HashMap<>(8);
        m.put("serial", serial);
        m.put("employeeCode", employeeCode);
        m.put("employeeName", employeeName);
        m.put("cnic", cnic);
        m.put("wagesText", wagesText);
        m.put("amountText", amountText);
        return m;
    }
}

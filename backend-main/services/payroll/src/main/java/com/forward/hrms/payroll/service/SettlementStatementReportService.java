package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementAttendance;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementDeduction;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementLeaveDetail;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementAttendanceRepository;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementDeductionRepository;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementLeaveDetailRepository;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Final settlement statement ({@code jasper/settlement-statement.jrxml}) —
 * the legacy "Employee's Dues Statement at the End of Job". One A4 page per
 * settlement: employee card with photo, payments | deductions side by side,
 * net payable in figures and words, the three office signatures and the
 * employee's "nothing remains due" acknowledgement.
 *
 * <p>Every figure comes from the persisted settlement snapshot, never a
 * recompute, so the print always matches what HR approved. The payment and
 * deduction heads mirror {@code SettlementCalculationEngine}'s gross / total
 * deduction sums; the totals printed are the stored ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementStatementReportService {

    static final String COMPANY_NAME = "Forward Sports (Pvt) Ltd.";
    static final String COMPANY_ADDRESS = "Wazirabad Road, Ada Sahowala, Sialkot";
    static final String COMPANY_CONTACT = "Tel: 052-3571800-5";
    /** Same optional letterhead logo as the payslip; the template draws an "FS" monogram without it. */
    private static final String LOGO_RESOURCE = "jasper/logo.png";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private final EmployeeFinalSettlementRepository settlements;
    private final EmployeeFinalSettlementLeaveDetailRepository leaves;
    private final EmployeeFinalSettlementDeductionRepository deductions;
    private final EmployeeFinalSettlementAttendanceRepository attendance;
    private final SalaryComponentRepository components;
    private final EmployeeClient employees;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;

    @Transactional
    public byte[] render(long settlementId) {
        EmployeeFinalSettlement s = settlements.findById(settlementId)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.FINAL_SETTLEMENT_NOT_FOUND,
                        "Final settlement not found: " + settlementId));
        EmployeeFinalSettlementLeaveDetail annual = null;
        for (EmployeeFinalSettlementLeaveDetail l : leaves.findBySettlementId(settlementId)) {
            if ("ANNUAL".equals(l.getLeaveCode())) annual = l;
        }
        EmployeeFinalSettlementAttendance period = attendance.findBySettlementId(settlementId).stream()
                .findFirst().orElse(null);
        EmployeeDto e = employees.getDetails(List.of(s.getEmployeeId())).get(s.getEmployeeId());

        List<Line> pay = new ArrayList<>();
        pay.add(new Line("Salary (current month)", salaryPeriod(s, period), s.getEarnedBasicSalary()));
        pay.add(new Line("Allowances (earned)", null, s.getAllowances()));
        pay.add(new Line("Gratuity", Boolean.TRUE.equals(s.getGratuityEligible())
                ? years(s.getGratuityCompletedYears()) : "not eligible", s.getGratuityAmount()));
        pay.add(new Line("Annual leave encashment",
                annual == null ? null : days(annual.getPayableDays()),
                annual == null ? null : annual.getEncashmentAmount()));
        pay.add(new Line("Outstanding bonus", null, s.getOutstandingBonus()));
        pay.add(new Line("Notice pay", percent(s.getNoticePayPercentage() == null ? null
                : s.getNoticePayPercentage().percentage()), s.getNoticePayAmount()));

        List<Line> ded = new ArrayList<>();
        ded.add(new Line("Permanent advance", null, s.getPermanentAdvance()));
        ded.add(new Line("Short-term advance", null, s.getShortTermAdvance()));
        ded.add(new Line("RFD advance", null, s.getRfdAdvance()));
        ded.add(new Line("Canteen", null, s.getCanteenAmount()));
        ded.add(new Line("Fair price shop", null, s.getFairPriceShopAmount()));
        ded.add(new Line("Excess annual leave", days(s.getAnnualExcessiveLeaveDays()), s.getAnnualExcessiveLeaveAmount()));
        ded.add(new Line("Excess medical / casual leave", days(s.getMedicalCasualExcessiveLeaveDays()),
                s.getMedicalCasualExcessiveLeaveAmount()));
        ded.add(new Line("Income tax", null, s.getTaxAmount()));
        ded.add(new Line("Early-exit deduction", percent(s.getForcedDeductionPercentage() == null ? null
                : s.getForcedDeductionPercentage().percentage()), s.getForcedDeductionAmount()));
        for (EmployeeFinalSettlementDeduction d : deductions.findBySettlementId(settlementId)) {
            ded.add(new Line(deductionName(d), null, d.getAmount()));
        }

        List<Map<String, ?>> rows = new ArrayList<>();
        for (int i = 0; i < Math.max(pay.size(), ded.size()); i++) {
            Map<String, Object> r = new HashMap<>();
            if (i < pay.size()) pay.get(i).into(r, "pay");
            if (i < ded.size()) ded.get(i).into(r, "ded");
            rows.add(r);
        }

        BigDecimal net = nz(s.getNetSettlement());
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        Map<String, Object> p = new HashMap<>();
        p.put("companyName", COMPANY_NAME);
        p.put("companyAddress", COMPANY_ADDRESS);
        p.put("companyContact", COMPANY_CONTACT);
        p.put("logo", classpathImage(LOGO_RESOURCE));
        p.put("refNo", "FS/FNF/%d/%04d".formatted(s.getCreatedAt() == null ? LocalDate.now().getYear()
                : s.getCreatedAt().getYear(), s.getId()));
        p.put("statementDate", DATE.format(LocalDate.now()));
        p.put("statusLabel", statusLabel(s.getStatus()));
        p.put("statusFinal", s.getStatus() == EmployeeFinalSettlementStatus.HR_APPROVED
                || s.getStatus() == EmployeeFinalSettlementStatus.PAID);

        p.put("employeeName", s.getEmployeeName());
        p.put("employeeCode", s.getEmployeeCode());
        p.put("designation", s.getDesignation());
        p.put("department", s.getDepartment());
        p.put("section", s.getSection());
        p.put("grade", s.getGrade());
        p.put("fatherName", e == null ? null : e.fatherName());
        p.put("cnic", e == null ? null : e.cnic());
        p.put("photo", e == null ? null : remoteImage(e.profilePictureUrl()));
        p.put("initials", initials(s.getEmployeeName()));
        p.put("dateOfJoining", date(s.getHireDate()));
        p.put("lastWorkingDay", date(s.getLastWorkingDay()));
        p.put("serviceLength", serviceLength(s.getHireDate(), s.getLastWorkingDay()));
        p.put("monthlyBasic", money(s.getMonthlyBasicSalary()));
        p.put("remarks", blank(s.getRemarks()) ? null : s.getRemarks().trim());

        p.put("totalPayments", money(s.getGrossSettlement()));
        p.put("totalDeductions", money(s.getTotalDeductions()));
        p.put("netLabel", net.signum() < 0 ? "NET RECOVERABLE FROM EMPLOYEE" : "NET AMOUNT PAYABLE");
        p.put("netAmount", "Rs " + money(net.abs()));
        p.put("netInWords", AmountInWords.of(net.abs()));
        p.put("generatedAt", STAMP.format(LocalDateTime.now()));
        p.put("generatedBy", actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username());

        byte[] pdf = jasper.renderPdf("/jasper/settlement-statement.jrxml", p, new JRMapCollectionDataSource(rows));
        audit.emit("report", String.valueOf(settlementId), "settlement-statement.run", null,
                Map.of("settlementId", settlementId, "employeeId", s.getEmployeeId()), true);
        return pdf;
    }

    /**
     * One printed head. {@code note} is the small grey qualifier after the
     * name (days, %, period). Both are XML-escaped — the template renders
     * them with styled markup.
     */
    private record Line(String name, String note, BigDecimal amount) {
        void into(Map<String, Object> row, String side) {
            row.put(side + "Name", xml(name));
            row.put(side + "Note", xml(note));
            row.put(side + "Amount", amount == null || amount.signum() == 0 ? "—" : money(amount));
        }
    }

    private String deductionName(EmployeeFinalSettlementDeduction d) {
        if (!blank(d.getDeductionName())) return d.getDeductionName();
        if (blank(d.getDeductionCode())) return "Other deduction";
        return components.findByCode(d.getDeductionCode()).map(SalaryComponent::getName).orElse(d.getDeductionCode());
    }

    private static String salaryPeriod(EmployeeFinalSettlement s, EmployeeFinalSettlementAttendance a) {
        if (a != null && a.getPeriodFrom() != null && a.getPeriodTo() != null) {
            return SHORT.format(a.getPeriodFrom()) + " – " + SHORT.format(a.getPeriodTo());
        }
        LocalDate lwd = s.getLastWorkingDay();
        return lwd == null ? null : SHORT.format(lwd.withDayOfMonth(1)) + " – " + SHORT.format(lwd);
    }

    static String statusLabel(EmployeeFinalSettlementStatus status) {
        if (status == null) return "DRAFT";
        return switch (status) {
            case DRAFT -> "DRAFT · NOT FOR PAYMENT";
            case HR_APPROVED -> "HR APPROVED";
            case PAID -> "PAID";
            case REJECTED -> "REJECTED · VOID";
            case CANCELLED -> "CANCELLED · VOID";
        };
    }

    static String serviceLength(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) return null;
        Period p = Period.between(from, to.plusDays(1));
        List<String> parts = new ArrayList<>();
        if (p.getYears() > 0) parts.add(p.getYears() + (p.getYears() == 1 ? " year" : " years"));
        if (p.getMonths() > 0) parts.add(p.getMonths() + (p.getMonths() == 1 ? " month" : " months"));
        if (parts.isEmpty()) parts.add(p.getDays() + (p.getDays() == 1 ? " day" : " days"));
        return String.join(" ", parts);
    }

    private static String years(Integer n) {
        return n == null ? null : n + (n == 1 ? " year" : " years");
    }

    private static String days(BigDecimal d) {
        if (d == null || d.signum() == 0) return null;
        String n = d.stripTrailingZeros().toPlainString();
        return n + (d.compareTo(BigDecimal.ONE) == 0 ? " day" : " days");
    }

    private static String percent(BigDecimal pct) {
        return pct == null || pct.signum() == 0 ? null : pct.stripTrailingZeros().toPlainString() + "%";
    }

    private static String initials(String name) {
        if (blank(name)) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (sb.length() < 2 && !part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        return sb.toString();
    }

    private static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    static String money(BigDecimal v) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(nz(v));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String xml(String s) {
        return s == null ? null : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Image classpathImage(String path) {
        ClassPathResource r = new ClassPathResource(path);
        if (!r.exists()) return null;
        try (InputStream in = r.getInputStream()) {
            return ImageIO.read(in);
        } catch (Exception ex) {
            log.warn("Letterhead logo {} unreadable; rendering without it", path, ex);
            return null;
        }
    }

    /** Profile photo from its signed URL; a slow or missing photo prints the initials badge instead. */
    private static Image remoteImage(String url) {
        if (blank(url)) return null;
        try {
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            return res.statusCode() == 200 ? ImageIO.read(new ByteArrayInputStream(res.body())) : null;
        } catch (Exception ex) {
            log.warn("Employee photo unavailable for settlement statement: {}", ex.toString());
            return null;
        }
    }
}

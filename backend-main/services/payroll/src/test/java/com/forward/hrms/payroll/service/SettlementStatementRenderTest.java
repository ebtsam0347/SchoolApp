package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementAttendance;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementDeduction;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementLeaveDetail;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementAttendanceRepository;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementDeductionRepository;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementLeaveDetailRepository;
import com.forward.hrms.payroll.repository.EmployeeFinalSettlementRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dues statement fits one A4 page and prints the stored totals. Writes
 * the PDF under target/ for a visual check.
 */
class SettlementStatementRenderTest {

    private static final long ID = 12L;

    private final EmployeeFinalSettlementRepository settlements = mock(EmployeeFinalSettlementRepository.class);
    private final EmployeeFinalSettlementLeaveDetailRepository leaves = mock(EmployeeFinalSettlementLeaveDetailRepository.class);
    private final EmployeeFinalSettlementDeductionRepository deductions = mock(EmployeeFinalSettlementDeductionRepository.class);
    private final EmployeeFinalSettlementAttendanceRepository attendance = mock(EmployeeFinalSettlementAttendanceRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);

    private final SettlementStatementReportService service = new SettlementStatementReportService(settlements,
            leaves, deductions, attendance, mock(SalaryComponentRepository.class), employees, new JasperRenderer(),
            mock(AuditEmitter.class));

    @Test
    void rendersOnePageWithStoredTotals() throws Exception {
        when(settlements.findById(ID)).thenReturn(Optional.of(settlement(EmployeeFinalSettlementStatus.HR_APPROVED)));
        EmployeeFinalSettlementLeaveDetail annual = new EmployeeFinalSettlementLeaveDetail();
        annual.setLeaveCode("ANNUAL");
        annual.setPayableDays(new BigDecimal("6.5"));
        annual.setEncashmentAmount(new BigDecimal("59296.25"));
        when(leaves.findBySettlementId(ID)).thenReturn(List.of(annual));
        EmployeeFinalSettlementDeduction rent = new EmployeeFinalSettlementDeduction();
        rent.setDeductionCode("RENT");
        rent.setDeductionName("House Rent & Utilities");
        rent.setAmount(new BigDecimal("5000.00"));
        when(deductions.findBySettlementId(ID)).thenReturn(List.of(rent));
        EmployeeFinalSettlementAttendance period = new EmployeeFinalSettlementAttendance();
        period.setPeriodFrom(LocalDate.of(2026, 6, 1));
        period.setPeriodTo(LocalDate.of(2026, 6, 1));
        when(attendance.findBySettlementId(ID)).thenReturn(List.of(period));
        when(employees.getDetails(any())).thenReturn(Map.of());

        byte[] pdf = service.render(ID);

        Path out = Path.of("target", "settlement-statement.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("Karamat Ali", "1,604,360.00", "HR APPROVED", "House Rent & Utilities");
    }

    @Test
    void negativeNetPrintsAsRecoverable() throws Exception {
        EmployeeFinalSettlement s = settlement(EmployeeFinalSettlementStatus.DRAFT);
        s.setNetSettlement(new BigDecimal("-2500.00"));
        when(settlements.findById(ID)).thenReturn(Optional.of(s));
        when(employees.getDetails(any())).thenReturn(Map.of());

        String text = new PdfTextExtractor(new PdfReader(service.render(ID))).getTextFromPage(1);

        assertThat(text).contains("NET RECOVERABLE FROM EMPLOYEE", "Rs 2,500.00", "DRAFT");
    }

    @Test
    void serviceLengthReadsInYearsAndMonths() {
        assertThat(SettlementStatementReportService.serviceLength(LocalDate.of(2020, 3, 6), LocalDate.of(2026, 6, 1)))
                .isEqualTo("6 years 2 months");
        assertThat(SettlementStatementReportService.serviceLength(LocalDate.of(2026, 5, 20), LocalDate.of(2026, 6, 1)))
                .isEqualTo("13 days");
    }

    private static EmployeeFinalSettlement settlement(EmployeeFinalSettlementStatus status) {
        EmployeeFinalSettlement s = new EmployeeFinalSettlement();
        s.setId(ID);
        s.setEmployeeId(99L);
        s.setEmployeeCode("99");
        s.setEmployeeName("Karamat Ali");
        s.setDepartment("Procurement & Sourcing");
        s.setSection("Purchase & Sale");
        s.setDesignation("Sr. Manager");
        s.setGrade("M-2");
        s.setHireDate(LocalDate.of(2020, 3, 6));
        s.setLastWorkingDay(LocalDate.of(2026, 6, 1));
        s.setMonthlyBasicSalary(new BigDecimal("237185.00"));
        s.setEarnedBasicSalary(new BigDecimal("7650.00"));
        s.setAllowances(new BigDecimal("1200.00"));
        s.setGrossSalary(new BigDecimal("8850.00"));
        s.setGratuityEligible(true);
        s.setGratuityCompletedYears(6);
        s.setGratuityAmount(new BigDecimal("1573903.75"));
        s.setOutstandingBonus(BigDecimal.ZERO);
        s.setNoticePayPercentage(NoticePayPercentage.NONE);
        s.setNoticePayAmount(BigDecimal.ZERO);
        s.setPermanentAdvance(BigDecimal.ZERO);
        s.setShortTermAdvance(BigDecimal.ZERO);
        s.setRfdAdvance(BigDecimal.ZERO);
        s.setCanteenAmount(new BigDecimal("1540.00"));
        s.setFairPriceShopAmount(BigDecimal.ZERO);
        s.setAnnualExcessiveLeaveDays(BigDecimal.ZERO);
        s.setAnnualExcessiveLeaveAmount(BigDecimal.ZERO);
        s.setMedicalCasualExcessiveLeaveDays(new BigDecimal("3"));
        s.setMedicalCasualExcessiveLeaveAmount(new BigDecimal("27367.50"));
        s.setTaxAmount(new BigDecimal("3782.50"));
        s.setAdjustmentType(SettlementAdjustmentType.NONE);
        s.setForcedDeductionPercentage(ForcedTerminationDeductionPercentage.PERCENT_0);
        s.setForcedDeductionAmount(BigDecimal.ZERO);
        s.setGrossSettlement(new BigDecimal("1642050.00"));
        s.setTotalDeductions(new BigDecimal("37690.00"));
        s.setNetSettlement(new BigDecimal("1604360.00"));
        s.setRemarks("Resigned for personal reasons; all company assets returned and cleared.");
        s.setStatus(status);
        s.setCreatedAt(LocalDateTime.of(2026, 6, 3, 10, 0));
        return s;
    }
}

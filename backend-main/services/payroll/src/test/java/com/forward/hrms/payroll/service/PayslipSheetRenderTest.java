package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.payroll.config.PayrollProperties;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipAttendanceInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipBankInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipDeductions;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipEarnings;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipEmployeeInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLineItem;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipSalaryInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipWithEmployee;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.service.composite.PayrollCompositesService;

import com.lowagie.text.pdf.PdfReader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The three-per-page sheet: every slip is the same height, so N slips print
 * on ceil(N / 3) pages in both languages — including a slip with more heads
 * than fit. Writes the PDFs under target/ for a visual check.
 */
class PayslipSheetRenderTest {

    private static final long RUN_ID = 135L;
    private static final int SLIPS = 7;

    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayrollCompositesService composites = mock(PayrollCompositesService.class);
    private PayslipPdfService pdfService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<PayrollCompositesService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(composites);
        pdfService = new PayslipPdfService(new PayrollProperties(new PayrollProperties.Payslips("payslips", null), null),
                mock(ObjectStorage.class), payslips, mock(Outbox.class), mock(AuditEmitter.class), runs, provider,
                mock(TransactionTemplate.class), mock(BatchRunner.class));
        pdfService.compileTemplate();

        when(runs.existsById(RUN_ID)).thenReturn(true);
        List<Payslip> entities = new ArrayList<>();
        List<PayslipWithEmployee> rows = new ArrayList<>();
        for (int i = 0; i < SLIPS; i++) {
            Payslip p = new Payslip();
            ReflectionTestUtils.setField(p, "id", 1000L + i);
            p.setOtHours(new BigDecimal("12.5"));
            entities.add(p);
            rows.add(row(1000L + i, i, i == 1 ? 12 : 4, i == 2));
        }
        when(payslips.findAllByPayrollRunId(eq(RUN_ID), any())).thenReturn(new PageImpl<>(entities));
        when(composites.runDetailRows(eq(RUN_ID), any())).thenReturn(rows);
    }

    @Test
    void englishSheet_isThreeSlipsPerPage() throws Exception {
        byte[] pdf = pdfService.renderSheet(RUN_ID, null, false);
        write("payslip-sheet-en.pdf", pdf);
        assertThat(pages(pdf)).isEqualTo(3);
    }

    @Test
    void urduSheet_isThreeSlipsPerPage() throws Exception {
        byte[] pdf = pdfService.renderSheet(RUN_ID, null, true);
        write("payslip-sheet-ur.pdf", pdf);
        assertThat(pages(pdf)).isEqualTo(3);
    }

    @Test
    void sheetLines_dropsZeros_padsToRows_andFoldsOverflowIntoOthers() {
        List<PayslipLineItem> earnings = new ArrayList<>();
        earnings.add(new PayslipLineItem("BASIC", "Basic", new BigDecimal("30000")));
        earnings.add(new PayslipLineItem("MEAL", "Meal", BigDecimal.ZERO));
        for (int i = 0; i < 9; i++) earnings.add(new PayslipLineItem("A" + i, "Allowance " + i, new BigDecimal("100")));

        List<Map<String, Object>> lines = PayslipPdfService.sheetLines(earnings, List.of(), 8, false);

        assertThat(lines).hasSize(8);
        assertThat(lines.get(0)).containsEntry("earningName", "Basic");
        assertThat(lines).noneMatch(l -> "Meal".equals(l.get("earningName")));
        // 10 non-zero heads into 8 rows: 7 kept, the last 3 (300.00) folded.
        assertThat(lines.get(7)).containsEntry("earningName", "Others").containsEntry("earningAmountText", "300.00");
        assertThat(lines).allMatch(l -> !l.containsKey("deductionName"));
    }

    private static PayslipWithEmployee row(long id, int i, int earningHeads, boolean held) {
        List<PayslipLineItem> earnings = new ArrayList<>();
        earnings.add(new PayslipLineItem("BASIC", "Basic Salary", new BigDecimal("32000.00")));
        String[] codes = {"HRA", "MEDICAL", "CONVEYANCE", "OT", "MEAL", "SPECIAL", "SENIORITY", "TRAVEL",
                "EDUCATION", "ACCOMMODATION", "NIGHT_SHIFT"};
        for (int k = 1; k < earningHeads; k++) {
            earnings.add(new PayslipLineItem(codes[k - 1], codes[k - 1].charAt(0) + codes[k - 1].substring(1).toLowerCase()
                    + " Allowance", new BigDecimal("2500.00")));
        }
        earnings.add(new PayslipLineItem("TRAVEL", "Travel Allowance", BigDecimal.ZERO));
        BigDecimal gross = earnings.stream().map(PayslipLineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PayslipLineItem> deductions = List.of(
                new PayslipLineItem(null, "EOBI", new BigDecimal("370.00")),
                new PayslipLineItem("CANTEEN", "Canteen", new BigDecimal("1200.00")),
                new PayslipLineItem("PERMANENT_ADVANCE", "Permanent Advance", new BigDecimal("800.00")));
        BigDecimal ded = new BigDecimal("2370.00");
        BigDecimal net = gross.subtract(ded);
        return new PayslipWithEmployee(id, 2026, 9, "2026-27", held ? "HELD" : "DRAFT", held ? "Pending clearance" : null,
                null,
                new PayslipEmployeeInfo(3000L + i, String.valueOf(13411 + i), "Muhammad Ali Raza " + i,
                        "Muhammad Raza", "Machine Operator", "Stitching", "G-3", LocalDate.of(2021, 3, 15), null),
                i % 2 == 0 ? new PayslipBankInfo("HBL", "Sialkot Cantt", "0123-4567890-01") : null,
                new PayslipSalaryInfo(new BigDecimal("32000.00"), gross, ded, net, BigDecimal.ZERO,
                        new BigDecimal("370.00"), BigDecimal.ZERO, new BigDecimal("15000.00")),
                new PayslipEarnings(earnings, gross),
                new PayslipDeductions(deductions, ded),
                new PayslipAttendanceInfo(4, 1, 26, new BigDecimal("24"), new BigDecimal("1"), BigDecimal.ZERO,
                        new BigDecimal("1"), BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, new BigDecimal("25")),
                null, null, null);
    }

    private static int pages(byte[] pdf) throws Exception {
        return new PdfReader(pdf).getNumberOfPages();
    }

    private static void write(String name, byte[] pdf) throws Exception {
        Path dir = Path.of("target", "payslip-sheet");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), pdf);
    }
}

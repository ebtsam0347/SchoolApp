package com.forward.hrms.payroll.service.composite;

import com.forward.hrms.payroll.client.dto.attendace.AnnualLeaveSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.EmployeeLeavePayslipSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.LeaveTypeAvailedDto;
import com.forward.hrms.payroll.client.dto.attendace.PayrollAttendanceAuditDto;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.SalaryComponentDto;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipAttendanceInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLeaveInfo;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLineItem;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipReconciliation;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.PayslipStatus;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the salary-slip mapping rules behind
 * {@code GET /payroll/runs/{id}/detail}: zero-fill against the component
 * catalog, aggregation (no amount is ever dropped), the reconciliation
 * block, and the attendance mapping. No Spring context, no DB.
 *
 * <p>"Employee 13411, run 2" cases use figures pulled from the local dev DB
 * (fshrms) — real persisted, bulk-imported data.
 */
class PayrollCompositesServiceLegacyPayslipTest {

    private static SalaryComponentDto component(String code, String name, ComponentType type, int displayOrder) {
        return new SalaryComponentDto(0L, code, name, type, CalcType.FLAT, null, displayOrder, true, false);
    }

    private static final List<SalaryComponentDto> EARNING_CATALOG = List.of(
            component("HRA", "House Rent Allowance", ComponentType.EARNING, 10),
            component("CONVEYANCE", "Conveyance Allowance", ComponentType.EARNING, 20),
            component("MEDICAL", "Medical Allowance", ComponentType.EARNING, 30),
            component("MEAL", "Meal Allowance", ComponentType.EARNING, 40),
            component("TRAVEL", "Travel Allowance", ComponentType.EARNING, 41),
            component("EDUCATION", "Education Allowance", ComponentType.EARNING, 42),
            component("ACCOMMODATION", "Accommodation Allowance", ComponentType.EARNING, 43),
            component("EXTRA", "Extra Allowance", ComponentType.EARNING, 44),
            component("SPECIAL", "Special Allowance", ComponentType.EARNING, 45),
            component("SENIORITY", "Seniority Allowance", ComponentType.EARNING, 46),
            component("REWARD_POLICY", "Reward Policy", ComponentType.EARNING, 47));

    private static final List<SalaryComponentDto> DEDUCTION_CATALOG = List.of(
            component("INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, 90),
            component("RENT", "Rent Deduction", ComponentType.DEDUCTION, 92),
            component("CANTEEN", "Canteen Deduction", ComponentType.DEDUCTION, 93),
            component("UNION_FUND", "Union Fund", ComponentType.DEDUCTION, 94),
            component("LOAN", "Loan Repayment", ComponentType.DEDUCTION, 95),
            component("ADVANCE", "Salary Advance", ComponentType.DEDUCTION, 96),
            component("OTHER", "Other", ComponentType.DEDUCTION, 99));

    private static PayslipLine line(String code, String name, ComponentType type, String amount) {
        PayslipLine l = new PayslipLine();
        l.setCode(code);
        l.setName(name);
        l.setType(type);
        l.setCalcType(CalcType.FLAT);
        l.setAmount(new BigDecimal(amount));
        return l;
    }

    private static PayslipDto payslip(String basic, String ot, String gross, String deductions, String tax,
                                      String eobi, String net, String lop) {
        return new PayslipDto(1L, 1L, 1L, "EMP-1", 2026, 8, "2026-27",
                new BigDecimal(basic), BigDecimal.ZERO, new BigDecimal(gross), new BigDecimal(deductions),
                new BigDecimal(tax), new BigDecimal(eobi), BigDecimal.ZERO, new BigDecimal(net),
                new BigDecimal(lop), 31, new BigDecimal("31.00"), BigDecimal.ZERO, 0, 0,
                BigDecimal.ZERO, new BigDecimal(ot), null, null, PayslipStatus.PAID, null, null, null);
    }

    private static PayslipDto simple(String basic, String gross, String deductions, String tax, String eobi,
                                     String net) {
        return payslip(basic, "0", gross, deductions, tax, eobi, net, "0");
    }

    private static BigDecimal totalDeduction(PayslipDto p) {
        return p.deductions().add(p.tax()).add(p.eobi());
    }

    private static BigDecimal amount(List<PayslipLineItem> items, String code) {
        return items.stream().filter(l -> l.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("code not found: " + code)).amount();
    }

    private static BigDecimal sum(List<PayslipLineItem> items) {
        return items.stream().map(PayslipLineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------- zero-fill scenarios ----------------

    @Test
    void deductions_noDeductionsAtAll_everyHeadStillAppearsAtZero() {
        PayslipDto p = simple("50000", "50000", "0", "0", "0", "50000");

        List<PayslipLineItem> result = PayrollCompositesService.legacyDeductionLines(p, DEDUCTION_CATALOG, List.of());

        assertThat(result).extracting(PayslipLineItem::code)
                .containsExactly("INCOME_TAX", "RENT", "CANTEEN", "UNION_FUND", "LOAN", "ADVANCE", "OTHER", "EOBI");
        assertThat(result).allSatisfy(l -> assertThat(l.amount()).isEqualByComparingTo(BigDecimal.ZERO));
        assertThat(totalDeduction(p)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void earnings_basicOnly_zeroHeadsStillPresent_andSumEqualsGross() {
        PayslipDto p = simple("50000", "50000", "0", "0", "0", "50000");

        List<PayslipLineItem> result = PayrollCompositesService.legacyEarningLines(p, EARNING_CATALOG, List.of());

        assertThat(result).hasSize(2 + EARNING_CATALOG.size());
        assertThat(result.get(0)).isEqualTo(new PayslipLineItem("BASIC", "Basic", new BigDecimal("50000")));
        assertThat(result.get(1).code()).isEqualTo("OT");
        assertThat(sum(result)).isEqualByComparingTo(p.gross());
    }

    @Test
    void earnings_multipleAllowances_realAmountsPreserved_andSumEqualsGross() {
        List<PayslipLine> lines = List.of(
                line("HRA", "House Rent Allowance", ComponentType.EARNING, "5000"),
                line("CONVEYANCE", "Conveyance Allowance", ComponentType.EARNING, "2000"),
                line("MEDICAL", "Medical Allowance", ComponentType.EARNING, "1500"));
        PayslipDto p = simple("50000", "58500", "0", "0", "0", "58500");

        List<PayslipLineItem> result = PayrollCompositesService.legacyEarningLines(p, EARNING_CATALOG, lines);

        assertThat(amount(result, "HRA")).isEqualByComparingTo("5000");
        assertThat(amount(result, "CONVEYANCE")).isEqualByComparingTo("2000");
        assertThat(amount(result, "MEDICAL")).isEqualByComparingTo("1500");
        assertThat(amount(result, "MEAL")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result).extracting(PayslipLineItem::code).doesNotHaveDuplicates();
        assertThat(sum(result)).isEqualByComparingTo(p.gross());
    }

    @Test
    void earnings_realBasicAndOtLines_areNotDoubleCounted() {
        // A freshly computed payslip persists BASIC and OT as lines too.
        List<PayslipLine> lines = List.of(
                line("BASIC", "Basic", ComponentType.EARNING, "50000"),
                line("OT", "Overtime", ComponentType.EARNING, "3000"),
                line("EXTRA", "Extra Allowance", ComponentType.EARNING, "2000"));
        PayslipDto p = payslip("50000", "3000", "55000", "0", "0", "0", "55000", "0");

        List<PayslipLineItem> result = PayrollCompositesService.legacyEarningLines(p, EARNING_CATALOG, lines);

        assertThat(result).extracting(PayslipLineItem::code).doesNotHaveDuplicates();
        assertThat(sum(result)).isEqualByComparingTo("55000");
    }

    // ---------------- duplicate-code aggregation (HELD_SALARY_RELEASE) ----------------

    @Test
    void earnings_twoHeldSalaryReleases_bothKept_andReconcileToGross() {
        // Basic 55,000 + held release 10,000 + held release 15,000 = gross 80,000.
        List<PayslipLine> lines = List.of(
                line("BASIC", "Basic", ComponentType.EARNING, "55000"),
                line("HELD_SALARY_RELEASE", "Held Salary Released - 7", ComponentType.EARNING, "10000"),
                line("HELD_SALARY_RELEASE", "Held Salary Released - 9", ComponentType.EARNING, "15000"));
        PayslipDto p = payslip("55000", "0", "80000", "0", "0", "0", "80000", "0");

        List<PayslipLineItem> result = PayrollCompositesService.legacyEarningLines(p, EARNING_CATALOG, lines);

        assertThat(result.stream().filter(l -> l.code().equals("HELD_SALARY_RELEASE")).map(PayslipLineItem::amount))
                .containsExactlyInAnyOrder(new BigDecimal("10000"), new BigDecimal("15000"));
        assertThat(sum(result)).isEqualByComparingTo("80000");
        PayslipReconciliation r = PayrollCompositesService.reconcile(p, result, List.of(), BigDecimal.ZERO);
        assertThat(r.earningsDifference()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void earnings_sameCatalogCodeTwice_isSummed_notDropped() {
        // e.g. a component revised mid-month leaves two effective rows -> two lines, one code.
        List<PayslipLine> lines = List.of(
                line("HRA", "House Rent Allowance", ComponentType.EARNING, "1000"),
                line("HRA", "House Rent Allowance", ComponentType.EARNING, "500"));
        PayslipDto p = simple("50000", "51500", "0", "0", "0", "51500");

        List<PayslipLineItem> result = PayrollCompositesService.legacyEarningLines(p, EARNING_CATALOG, lines);

        assertThat(amount(result, "HRA")).isEqualByComparingTo("1500");
        assertThat(sum(result)).isEqualByComparingTo("51500");
    }

    // ---------------- deductions ----------------

    @Test
    void deductions_eobiPresent_pessiNeverAppearsAsEmployeeDeduction() {
        PayslipDto p = simple("50000", "50000", "0", "0", "400", "49600");

        List<PayslipLineItem> result = PayrollCompositesService.legacyDeductionLines(p, DEDUCTION_CATALOG, List.of());

        assertThat(amount(result, "EOBI")).isEqualByComparingTo("400");
        assertThat(result).extracting(PayslipLineItem::code).doesNotContain("PESSI", "PESSI_EMPLOYER");
    }

    @Test
    void deductions_incomeTax_zeroAndNonZero() {
        PayslipDto zero = simple("30000", "30000", "0", "0", "0", "30000");
        assertThat(amount(PayrollCompositesService.legacyDeductionLines(zero, DEDUCTION_CATALOG, List.of()),
                "INCOME_TAX")).isEqualByComparingTo(BigDecimal.ZERO);

        List<PayslipLine> taxLine = List.of(line("INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, "15000"));
        PayslipDto withTax = simple("120000", "120000", "0", "15000", "0", "105000");
        assertThat(amount(PayrollCompositesService.legacyDeductionLines(withTax, DEDUCTION_CATALOG, taxLine),
                "INCOME_TAX")).isEqualByComparingTo("15000");
    }

    @Test
    void deductions_multipleSimultaneous_eachExactlyOnce_andSumEqualsTotalDeduction() {
        // deductions column = non-tax deduction lines (ADVANCE 3000 + OTHER 400); tax 83; eobi 400.
        List<PayslipLine> lines = List.of(
                line("INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, "83"),
                line("OTHER", "Other", ComponentType.DEDUCTION, "400"),
                line("ADVANCE", "Salary Advance", ComponentType.DEDUCTION, "3000"));
        PayslipDto p = simple("58300", "58300", "3400", "83", "400", "54417");

        List<PayslipLineItem> result = PayrollCompositesService.legacyDeductionLines(p, DEDUCTION_CATALOG, lines);

        assertThat(result).extracting(PayslipLineItem::code).doesNotHaveDuplicates();
        assertThat(sum(result)).isEqualByComparingTo(totalDeduction(p));   // 3883
        PayslipReconciliation r = PayrollCompositesService.reconcile(p,
                PayrollCompositesService.legacyEarningLines(p, EARNING_CATALOG, List.of()), result, totalDeduction(p));
        assertThat(r.reconciled()).isTrue();
    }

    @Test
    void deductions_realLineNotInCatalog_isAppended_notDropped() {
        List<PayslipLine> lines = List.of(line("LEGACY_CODE", "Legacy misc", ComponentType.DEDUCTION, "250"));
        PayslipDto p = simple("50000", "50000", "250", "0", "0", "49750");

        List<PayslipLineItem> result = PayrollCompositesService.legacyDeductionLines(p, DEDUCTION_CATALOG, lines);

        assertThat(amount(result, "LEGACY_CODE")).isEqualByComparingTo("250");
        assertThat(sum(result)).isEqualByComparingTo(totalDeduction(p));
    }

    // ---------------- reconciliation ----------------

    @Test
    void reconcile_realImportedPayslip13411_reportsTheGapInsteadOfHidingIt() {
        List<PayslipLine> lines = List.of(
                line("INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, "83.00"),
                line("OTHER", "Other", ComponentType.DEDUCTION, "400.00"));
        PayslipDto p = simple("58300.00", "58300.00", "37108.00", "83.00", "400.00", "20709.00");
        List<PayslipLineItem> earn = PayrollCompositesService.legacyEarningLines(p, EARNING_CATALOG, List.of());
        List<PayslipLineItem> ded = PayrollCompositesService.legacyDeductionLines(p, DEDUCTION_CATALOG, lines);

        PayslipReconciliation r = PayrollCompositesService.reconcile(p, earn, ded, totalDeduction(p));

        assertThat(totalDeduction(p)).isEqualByComparingTo("37591.00");
        assertThat(r.earningsDifference()).isEqualByComparingTo("0");           // gross ties out
        assertThat(r.deductionsLineTotal()).isEqualByComparingTo("883.00");     // 83 + 400 + eobi 400
        assertThat(r.deductionsDifference()).isEqualByComparingTo("36708.00");  // legacy breakdown never stored
        assertThat(r.netExpected()).isEqualByComparingTo("20709.00");
        assertThat(r.netDifference()).isEqualByComparingTo("0");                // persisted net is correct
        assertThat(r.reconciled()).isFalse();
        assertThat(r.notes()).anyMatch(n -> n.contains("line-level breakdown is incomplete"));
    }

    @Test
    void reconcile_flagsNetThatDoesNotFollowFromGrossAndDeductions() {
        PayslipDto p = simple("50000", "50000", "0", "0", "400", "5500");   // the "net = 5500" pattern

        PayslipReconciliation r = PayrollCompositesService.reconcile(p,
                PayrollCompositesService.legacyEarningLines(p, EARNING_CATALOG, List.of()),
                PayrollCompositesService.legacyDeductionLines(p, DEDUCTION_CATALOG, List.of()),
                totalDeduction(p));

        assertThat(r.netExpected()).isEqualByComparingTo("49600");
        assertThat(r.netDifference()).isEqualByComparingTo("-44100");
        assertThat(r.reconciled()).isFalse();
        assertThat(r.notes()).anyMatch(n -> n.contains("differs from gross - total deduction"));
    }

    // ---------------- attendance mapping (derived audit, no preJoiningDays) ----------------

    @Test
    void attendance_mapsEveryDerivedFigure_exceptPreJoiningDays() {
        PayrollAttendanceAuditDto a = new PayrollAttendanceAuditDto(31, 2, 5, 2, 24,
                new BigDecimal("23"), new BigDecimal("1"), new BigDecimal("0"), new BigDecimal("0"),
                new BigDecimal("0"), 0, 0, 0, new BigDecimal("0"), new BigDecimal("26"));

        PayslipAttendanceInfo info = PayrollCompositesService.attendanceInfo(a);

        assertThat(info.weeklyOffDays()).isEqualTo(5);
        assertThat(info.holidayDays()).isEqualTo(2);
        assertThat(info.workingDays()).isEqualTo(24);
        assertThat(info.presentDays()).isEqualByComparingTo("23");
        assertThat(info.paidLeaveDays()).isEqualByComparingTo("1");
        assertThat(info.unpaidLeaveDays()).isEqualByComparingTo("0");
        assertThat(info.absentDays()).isEqualByComparingTo("0");
        assertThat(info.unrecordedAbsentDays()).isEqualByComparingTo("0");
        assertThat(info.halfDayRecords()).isZero();
        assertThat(info.presentOnOffDays()).isZero();
        assertThat(info.leaveConflictDays()).isZero();
        assertThat(info.lopDays()).isEqualByComparingTo("0");
        assertThat(info.paidDays()).isEqualByComparingTo("26");
        assertThat(java.util.Arrays.stream(PayslipAttendanceInfo.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("preJoiningDays", "audit", "calendarDays");
    }

    @Test
    void attendance_unavailable_isNull() {
        assertThat(PayrollCompositesService.attendanceInfo(null)).isNull();
    }

    // ---------------- leave mapping ----------------

    @Test
    void leaveInfo_mapsUsage_andKeepsUnknownAnnualEntitlementNull() {
        EmployeeLeavePayslipSummaryDto s = new EmployeeLeavePayslipSummaryDto(1L,
                List.of(new LeaveTypeAvailedDto("CASUAL", "Casual leave", new BigDecimal("1"), new BigDecimal("3"))),
                new AnnualLeaveSummaryDto(null, new BigDecimal("2"), null, null), null);

        PayslipLeaveInfo info = PayrollCompositesService.leaveInfo(s);

        assertThat(info.leaves()).singleElement().satisfies(l -> {
            assertThat(l.availedThisMonth()).isEqualByComparingTo("1");
            assertThat(l.availedThisCycle()).isEqualByComparingTo("3");
        });
        assertThat(info.annualLeave().availed()).isEqualByComparingTo("2");
        assertThat(info.annualLeave().total()).isNull();
        assertThat(PayrollCompositesService.leaveInfo(null)).isNull();
    }
}

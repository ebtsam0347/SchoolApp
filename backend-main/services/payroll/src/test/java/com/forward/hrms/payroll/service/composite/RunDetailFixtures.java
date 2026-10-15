package com.forward.hrms.payroll.service.composite;

import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.PayslipStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payslip fixtures shared by the salary-slip and frozen-contract tests. The legacy figures are the
 * persisted values of the approved Run 2 target / Runs 1 and 3 taken from the local dev database
 * (fshrms); the engine figures follow {@code PayrollComputeService}'s equations
 * ({@code gross = basic + allowances}, {@code net = gross - deductions - tax - eobi}).
 */
final class RunDetailFixtures {

    private RunDetailFixtures() {}

    static final String LEGACY = "legacy-migration";
    static final String MIGRATION_HELD_REASON = "migrated as unpaid from legacy";

    /** One persisted payslip with its persisted lines and created_by. */
    record Fixture(String label, PayslipDto payslip, List<PayslipLine> lines, String createdBy) {}

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static PayslipLine line(long payslipId, String code, String name, ComponentType type, String amount, int order) {
        PayslipLine l = new PayslipLine();
        l.setPayslipId(payslipId);
        l.setCode(code);
        l.setName(name);
        l.setType(type);
        l.setCalcType(CalcType.FLAT);
        l.setAmount(d(amount));
        l.setDisplayOrder(order);
        return l;
    }

    static PayslipDto payslip(long id, long runId, long employeeId, String code, int year, int month,
                              String basic, String allowances, String gross, String deductions, String tax,
                              String eobi, String pessi, String net, String otHours, String otAmount,
                              PayslipStatus status, String heldReason) {
        return new PayslipDto(id, runId, employeeId, code, year, month, "2026-27",
                d(basic), d(allowances), d(gross), d(deductions), d(tax), d(eobi), d(pessi), d(net),
                d("0.00"), 26, d("25.50"), d("0.50"), 2, 4, d(otHours), d(otAmount),
                null, LocalDateTime.of(year, month, 10, 11, 29, 39), status, heldReason, null, null);
    }

    // ------------------------------------------------------------------ legacy import

    /** Run 1 (July), PAID, legacy import: EXTRA + SENIORITY, EOBI stored as an OTHER line, zero tax. */
    static Fixture run1Paid10005() {
        return new Fixture("run1-paid-10005",
                payslip(3819, 1, 3092, "10005", 2026, 7, "44133.00", "45636.00", "44383.00", "5600.00", "0.00",
                        "400.00", "2546.00", "39636.00", "0.00", "0.00", PayslipStatus.PAID, null),
                List.of(line(3819, "EXTRA", "Extra Allowance", ComponentType.EARNING, "2200.00", 25),
                        line(3819, "SENIORITY", "Seniority Allowance", ComponentType.EARNING, "1000.00", 27),
                        line(3819, "OTHER", "Other", ComponentType.DEDUCTION, "400.00", 103)),
                LEGACY);
    }

    /** Run 1 (July), legacy HELD (Closing) with a positive net. */
    static Fixture run1Held131() {
        return new Fixture("run1-held-131",
                payslip(4644, 1, 131, "131", 2026, 7, "197107.00", "0.00", "197107.00", "670873.00", "12835.00",
                        "0.00", "0.00", "-683708.00", "0.00", "0.00", PayslipStatus.HELD, MIGRATION_HELD_REASON),
                List.of(line(4644, "INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, "12835.00", 100)),
                LEGACY);
    }

    /** Run 2 restored: 13411, STA/LTA/canteen/FPS live only inside the deductions aggregate. */
    static Fixture run2Paid13411() {
        return new Fixture("run2-paid-13411",
                payslip(5001, 2, 3801, "13411", 2026, 8, "58300.00", "58300.00", "58300.00", "37108.00", "83.00",
                        "400.00", "2388.00", "20709.00", "0.00", "0.00", PayslipStatus.PAID, null),
                List.of(line(5001, "INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, "83.00", 100),
                        line(5001, "OTHER", "Other", ComponentType.DEDUCTION, "400.00", 103)),
                LEGACY);
    }

    /** Run 2 restored: 10839, Closing row whose tax was recorded but not deducted from net. */
    static Fixture run2Held10839() {
        return new Fixture("run2-held-10839-closing-tax",
                payslip(5002, 2, 3656, "10839", 2026, 8, "58605.00", "33811.00", "58605.00", "0.00", "86.00",
                        "0.00", "0.00", "33811.00", "0.00", "0.00", PayslipStatus.HELD, MIGRATION_HELD_REASON),
                List.of(line(5002, "INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, "86.00", 100)),
                LEGACY);
    }

    /** Run 2 restored: 528, HELD with a negative net. */
    static Fixture run2Held528() {
        return new Fixture("run2-held-528-negative-net",
                payslip(5003, 2, 832, "528", 2026, 8, "41458.00", "28480.00", "43208.00", "210030.00", "0.00",
                        "0.00", "0.00", "-181550.00", "0.00", "0.00", PayslipStatus.HELD, MIGRATION_HELD_REASON),
                List.of(line(5003, "EXTRA", "Extra Allowance", ComponentType.EARNING, "1373.00", 25)),
                LEGACY);
    }

    /** Run 2 restored: 368, special OT 10,165 sits inside allowances (no line, no OT column). */
    static Fixture run2Paid368() {
        return new Fixture("run2-paid-368-special-ot",
                payslip(5004, 2, 368, "368", 2026, 8, "42258.00", "58603.00", "48608.00", "8000.00", "0.00",
                        "400.00", "2500.00", "50203.00", "0.00", "0.00", PayslipStatus.PAID, null),
                List.of(line(5004, "EXTRA", "Extra Allowance", ComponentType.EARNING, "2100.00", 25),
                        line(5004, "SENIORITY", "Seniority Allowance", ComponentType.EARNING, "3000.00", 27),
                        line(5004, "OTHER", "Other", ComponentType.DEDUCTION, "400.00", 103)),
                LEGACY);
    }

    /** Run 2 restored: 1258, annual-leave payout 22,754 plus special OT 9,765 inside allowances. */
    static Fixture run2Paid1258() {
        return new Fixture("run2-paid-1258-annual-leave",
                payslip(5005, 2, 1258, "1258", 2026, 8, "42258.00", "80667.00", "47608.00", "5000.00", "0.00",
                        "400.00", "2500.00", "75267.00", "0.00", "0.00", PayslipStatus.PAID, null),
                List.of(line(5005, "EXTRA", "Extra Allowance", ComponentType.EARNING, "2100.00", 25),
                        line(5005, "SENIORITY", "Seniority Allowance", ComponentType.EARNING, "2750.00", 27),
                        line(5005, "OTHER", "Other", ComponentType.DEDUCTION, "400.00", 103)),
                LEGACY);
    }

    /** Run 2 restored: paid overtime (legacy overtimepaid lives in ot_amount, outside allowances). */
    static Fixture run2PaidOvertime12680() {
        return new Fixture("run2-paid-12680-overtime",
                payslip(5006, 2, 12680, "12680", 2026, 8, "42258.00", "55955.00", "42258.00", "7911.00", "0.00",
                        "400.00", "2500.00", "48491.00", "2.08", "847.00", PayslipStatus.PAID, null),
                List.of(line(5006, "EXTRA", "Extra Allowance", ComponentType.EARNING, "2100.00", 25),
                        line(5006, "SENIORITY", "Seniority Allowance", ComponentType.EARNING, "250.00", 27),
                        line(5006, "OTHER", "Other", ComponentType.DEDUCTION, "400.00", 103)),
                LEGACY);
    }

    /** Run 2 restored: no EOBI, no tax, no OT, no lines at all (all-zero heads). */
    static Fixture run2PaidNoEobiNoTax() {
        return new Fixture("run2-paid-14824-zero-eobi",
                payslip(5007, 2, 14824, "14824", 2026, 8, "50000.00", "50000.00", "50000.00", "1000.00", "0.00",
                        "0.00", "1500.00", "49000.00", "0.00", "0.00", PayslipStatus.PAID, null),
                List.of(), LEGACY);
    }

    /** Run 3 (September), legacy HELD, negative net. */
    static Fixture run3Held4151() {
        return new Fixture("run3-held-4151",
                payslip(1828, 3, 1190, "4151", 2026, 9, "120295.00", "23134.00", "120295.00", "322628.00",
                        "2732.00", "0.00", "0.00", "-302226.00", "0.00", "0.00", PayslipStatus.HELD,
                        MIGRATION_HELD_REASON),
                List.of(line(1828, "INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, "2732.00", 100)),
                LEGACY);
    }

    // ------------------------------------------------------------------ payroll engine

    /** Engine payslip: BASIC line, EXTRA, an OT line, a CANTEEN line and an INCOME_TAX line. */
    static Fixture enginePaid() {
        return new Fixture("engine-paid",
                payslip(9001, 2, 77, "E-77", 2026, 8, "40000.00", "2500.00", "42500.00", "300.00", "100.00",
                        "1500.00", "1500.00", "40600.00", "2.00", "500.00", PayslipStatus.PAID, null),
                List.of(line(9001, "BASIC", "Basic", ComponentType.EARNING, "40000.00", 0),
                        line(9001, "EXTRA", "Extra Allowance", ComponentType.EARNING, "2000.00", 100),
                        line(9001, "INCOME_TAX", "Income Tax", ComponentType.DEDUCTION, "100.00", 110),
                        line(9001, "OT", "Overtime", ComponentType.EARNING, "500.00", 120),
                        line(9001, "CANTEEN", "Canteen", ComponentType.DEDUCTION, "300.00", 130)),
                "admin");
    }

    /** Engine payslip with an assigned-but-zero component line and zero tax/EOBI/OT/canteen. */
    static Fixture engineZeroValues() {
        return new Fixture("engine-zero-values",
                payslip(9002, 2, 78, "E-78", 2026, 8, "30000.00", "0.00", "30000.00", "0.00", "0.00",
                        "0.00", "0.00", "30000.00", "0.00", "0.00", PayslipStatus.PAID, null),
                List.of(line(9002, "BASIC", "Basic", ComponentType.EARNING, "30000.00", 0),
                        line(9002, "MEAL", "Meal Allowance", ComponentType.EARNING, "0.00", 100)),
                "admin");
    }

    static Fixture engineWithStatus(PayslipStatus status) {
        PayslipDto base = enginePaid().payslip();
        PayslipDto p = new PayslipDto(base.id() + 10, base.payrollRunId(), base.employeeId(), base.employeeCode(),
                base.periodYear(), base.periodMonth(), base.fiscalYearCode(), base.basic(), base.allowances(),
                base.gross(), base.deductions(), base.tax(), base.eobi(), base.pessiEmployer(), base.net(),
                base.lopDays(), base.workingDays(), base.paidDays(), base.absentDays(), base.holidaysInPeriod(),
                base.weekendsInPeriod(), base.otHours(), base.otAmount(), null, base.generatedAt(), status,
                status == PayslipStatus.HELD ? "manual hold" : null, null, null);
        return new Fixture("engine-" + status.name().toLowerCase(), p, enginePaid().lines(), "admin");
    }

    static List<Fixture> allLegacy() {
        return List.of(run1Paid10005(), run1Held131(), run2Paid13411(), run2Held10839(), run2Held528(),
                run2Paid368(), run2Paid1258(), run2PaidOvertime12680(), run2PaidNoEobiNoTax(), run3Held4151());
    }

    static List<Fixture> allEngine() {
        return List.of(enginePaid(), engineZeroValues(), engineWithStatus(PayslipStatus.RELEASE_PENDING),
                engineWithStatus(PayslipStatus.RELEASED), engineWithStatus(PayslipStatus.HELD),
                engineWithStatus(PayslipStatus.DRAFT));
    }
}

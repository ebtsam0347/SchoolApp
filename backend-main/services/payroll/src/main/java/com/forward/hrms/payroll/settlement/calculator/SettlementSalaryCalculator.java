package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.payroll.dto.response.SettlementPreviewDto;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.service.AttendanceSnapshot;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementSalaryCalculator implements SettlementCalculator {

    // Statutory day-rate divisor — used ONLY for the per-employee
    // allowance/deduction catalog lines below, matching the same
    // convention SettlementLeaveEncashmentCalculator and GratuityService
    // use for their own day-rate purposes. Earned BASIC salary does
    // NOT use this divisor (see calculate()) — it follows
    // PayrollComputeService's own calendar-days-in-month policy instead
    // (Audit Finding 1: settlement basic previously excluded weekends/
    // holidays and used this fixed /26, disagreeing with regular payroll
    // paying weekly-offs and holidays whenever they aren't LOP).
    private static final BigDecimal DAY_DIVISOR = BigDecimal.valueOf(26);

    private final EmployeeSalaryComponentRepository components;
    private final SalaryComponentRepository componentCatalog;

    @Override
    public void calculate(
            SettlementCalculationContext ctx,
            SettlementCalculationResult result) {

        LocalDate lwd = ctx.getLastWorkingDay();

        LocalDate periodStart = lwd.withDayOfMonth(1);

        // Allowance/deduction catalog lines below are still driven by
        // present days plus paid leave days (attendance-service's
        // authoritative paid/unpaid leave classification) at the
        // statutory /26 day-rate — unchanged, out of this fix's scope.
        AttendanceSnapshot snapshot = ctx.getAttendanceSnapshot();
        BigDecimal daysPresent = snapshot == null ? BigDecimal.ZERO : snapshot.daysPresent();
        BigDecimal paidLeaveDays = ctx.getPaidLeaveDays() == null ? BigDecimal.ZERO : ctx.getPaidLeaveDays();
        BigDecimal payableAttendanceDays = daysPresent.add(paidLeaveDays);

        // set monthly actual salary
        BigDecimal monthlyBasicSalary = Money.of(ctx.getSalary().getBasic()).amount();
        result.setMonthlyBasicSalary(monthlyBasicSalary);

        // earnedBasic — matches PayrollComputeService's own regular-
        // payroll policy: a salaried employee's weekly-offs and holidays
        // inside the elapsed settlement period (1st of the termination
        // month through the last working day) are inherently paid, same
        // as any other day that isn't LOP. snapshot.paidDays() (built in
        // SettlementServiceNew.toAttendanceSnapshot) already sums
        // present + paid-leave + holiday + weekend days within that
        // elapsed period, capped at its length — the correct numerator.
        // The denominator is the FULL calendar month the termination
        // falls in, not the fixed statutory /26 used below for
        // allowances (which answers a different question — see
        // DAY_DIVISOR's comment).
        BigDecimal payableDaysInPeriod = snapshot == null ? BigDecimal.ZERO : snapshot.paidDays();
        BigDecimal daysInMonth = BigDecimal.valueOf(lwd.lengthOfMonth());
        BigDecimal basic = Money.of(monthlyBasicSalary)
                .proration(payableDaysInPeriod, daysInMonth)
                .amount();

        BigDecimal allowances = BigDecimal.ZERO;
        BigDecimal incomeTax = BigDecimal.ZERO;

        List<EmployeeSalaryComponent> rows =
                components.findEffectiveDuring(
                        ctx.getEmployeeId(),
                        periodStart,
                        lwd);

        // Load component catalog only once
        Map<Long, SalaryComponent> componentMap =
                componentCatalog.findAllByActiveTrue()
                        .stream()
                        .collect(Collectors.toMap(
                                SalaryComponent::getId,
                                Function.identity()));

        for (EmployeeSalaryComponent line : rows) {

            SalaryComponent comp = componentMap.get(line.getComponentId());

            if (comp == null) {
                continue;
            }

            // CANTEEN and FAIR_PRICE_SHOP are computed separately from
            // live usage/ledger data (SettlementCanteenCalculator /
            // SettlementFairPriceShopCalculator) — skip any per-employee
            // catalog line for the same code so it isn't charged twice,
            // mirroring PayrollComputeService's regular-run guard.
            if ("CANTEEN".equals(comp.getCode()) || "FAIR_PRICE_SHOP".equals(comp.getCode())) {
                continue;
            }

            BigDecimal amount = Money.of(line.getAmount())
                            .divide(DAY_DIVISOR)
                            .multiply(payableAttendanceDays)
                            .amount();

            if (comp.getType() == ComponentType.EARNING) {

                allowances = allowances.add(amount);

            } else if ("INCOME_TAX".equals(comp.getCode())) {

                incomeTax = incomeTax.add(amount);

            } else {

                result.getDeductions().add(new SettlementPreviewDto.OutstandingDeduction(
                                comp.getCode(),
                                amount));

                result.setOtherDeductions(
                        result.getOtherDeductions().add(amount));
            }
        }

        result.setBasicEarnedSalary(basic);
        result.setAllowances(allowances);
        result.setGrossSalary(basic.add(allowances));
        result.setIncomeTax(incomeTax);
    }
}

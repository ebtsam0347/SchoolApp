package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.repository.RfdAdvanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * RFD advance recovered at final settlement: the rows dated in the
 * last-working-day month, the same month-window rule the payroll run uses.
 * Nothing is marked on the rows, so a rejected / cancelled settlement has
 * nothing to reverse.
 */
@Service
@RequiredArgsConstructor
public class RfdAdvanceSettlementService {

    private final RfdAdvanceRepository repository;

    public BigDecimal process(EmployeeFinalSettlement settlement) {
        return forMonthOf(settlement.getEmployeeId(), settlement.getLastWorkingDay());
    }

    public BigDecimal forMonthOf(long employeeId, LocalDate lastWorkingDay) {
        YearMonth month = YearMonth.from(lastWorkingDay);
        return repository.sumForPeriod(employeeId, month.atDay(1), month.atEndOfMonth());
    }
}

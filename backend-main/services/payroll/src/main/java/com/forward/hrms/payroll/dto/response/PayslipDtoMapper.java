package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.Payslip;

import java.util.List;

public final class PayslipDtoMapper {

    private PayslipDtoMapper() {}

    public static PayslipDto of(Payslip p) {
        return of(p, null);
    }

    public static PayslipDto of(Payslip p, List<PayslipLineDto> lines) {
        return new PayslipDto(p.getId(), p.getPayrollRunId(), p.getEmployeeId(),
                p.getEmployeeCode(), p.getPeriodYear(), p.getPeriodMonth(),
                p.getFiscalYearCode(),
                p.getBasic(), p.getAllowances(), p.getGross(),
                p.getDeductions(), p.getTax(), p.getEobi(), p.getPessiEmployer(),
                p.getNet(), p.getLopDays(),
                p.getWorkingDays(), p.getPaidDays(), p.getAbsentDays(),
                p.getHolidaysInPeriod(), p.getWeekendsInPeriod(),
                p.getOtHours(), p.getOtAmount(),
                p.getPdfObjectKey(), p.getGeneratedAt(), p.getStatus(),
                p.getHeldReason(), p.getReleasedInRunId(), lines);
    }
}

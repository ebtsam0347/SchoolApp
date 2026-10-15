package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.repository.PayslipRepository;

public final class PayrollRunTotalsDtoMapper {

    private PayrollRunTotalsDtoMapper() {}

    public static PayrollRunTotalsDto of(long runId, PayslipRepository.RunTotals t) {
        return new PayrollRunTotalsDto(runId, t.employeeCount(),
                t.basic(), t.allowances(),
                t.gross(), t.deductions(), t.tax(), t.eobi(), t.pessiEmployer(), t.net(),
                t.heldCount(), t.releasePendingCount());
    }
}

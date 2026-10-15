package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.repository.AllowanceTransactionRepository;

public final class AllowanceRunTotalsDtoMapper {
    public AllowanceRunTotalsDtoMapper() {
    }
    public static AllowanceRunTotalsDto of(long runId, AllowanceTransactionRepository.RunTotals t){
        return new AllowanceRunTotalsDto(runId, t.employeeCount(), t.allowances());
    }
}

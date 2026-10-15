package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;

public final class EmployeeBankDetailsDtoMapper {

    private EmployeeBankDetailsDtoMapper() {}

    public static EmployeeBankDetailsDto of(EmployeeBankDetails b, Bank bank) {
        return new EmployeeBankDetailsDto(b.getId(), b.getEmployeeId(), b.getBankId(),
                bank == null ? null : bank.getCode(),
                bank == null ? null : bank.getName(),
                b.getBranch(), b.getAccountTitle(), b.getIban(), b.getAccountNumber());
    }
}

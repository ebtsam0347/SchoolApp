package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.IncrementBatchLine;

public final class IncrementBatchLineDtoMapper {

    private IncrementBatchLineDtoMapper() {}

    public static IncrementBatchLineDto of(IncrementBatchLine l) {
        return new IncrementBatchLineDto(l.getId(), l.getBatchId(), l.getEmployeeId(),
                l.getOldBasic(), l.getNewBasic(), l.getOldIncomeTax(), l.getNewIncomeTax(),
                l.getEffectiveFrom(), l.getStatus(), l.getErrorMessage());
    }
}

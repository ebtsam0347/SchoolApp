package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.PayrollRun;

public final class PayrollRunDtoMapper {

    private PayrollRunDtoMapper() {}

    public static PayrollRunDto of(PayrollRun r) {
        return new PayrollRunDto(r.getId(), r.getFiscalYearCode(),
                r.getPeriodYear(), r.getPeriodMonth(), r.getStatus(),
                r.isAttendanceLocked(), r.getComputedAt(), r.getApprovedAt(),
                r.getPostedAt(), r.getApprovedBy(), r.getNotes(),
                r.getSkippedCount(),
                r.getProgressCount(), r.getTotalCount());
    }
}

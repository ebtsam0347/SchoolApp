package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.AllowanceRun;

public final class AllowanceRunDtoMapper {

    private AllowanceRunDtoMapper() {}

    public static AllowanceRunDto of(AllowanceRun r) {
        return new AllowanceRunDto(r.getId(), r.getFiscalYearCode(),
                r.getAllowanceYear(), r.getAllowanceMonth(), r.getStatus(),
                r.getComputedAt(), r.getApprovedAt(),
                r.getPostedAt(), r.getApprovedBy(), r.getNotes(),
                r.getSkippedCount(),
                r.getProgressCount(), r.getTotalCount());
    }
}

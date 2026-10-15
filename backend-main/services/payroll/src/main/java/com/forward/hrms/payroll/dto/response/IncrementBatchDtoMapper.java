package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.IncrementBatch;

import java.util.List;

public final class IncrementBatchDtoMapper {

    private IncrementBatchDtoMapper() {}

    public static IncrementBatchDto of(IncrementBatch b) {
        return of(b, null);
    }

    public static IncrementBatchDto of(IncrementBatch b, List<IncrementBatchLineDto> lines) {
        return new IncrementBatchDto(b.getId(), b.getName(), b.getStatus(),
                b.getEffectiveFrom(), b.getSourceFilename(), b.getNotes(),
                b.getAppliedAt(), b.getAppliedBy(), lines);
    }
}

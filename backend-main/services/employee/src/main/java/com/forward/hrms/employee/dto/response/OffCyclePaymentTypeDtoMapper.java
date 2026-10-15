package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.OffCyclePaymentType;

public final class OffCyclePaymentTypeDtoMapper {

    private OffCyclePaymentTypeDtoMapper() {
    }

    public static OffCyclePaymentTypeDto of(OffCyclePaymentType entity) {
        return new OffCyclePaymentTypeDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getActive()
        );
    }
}
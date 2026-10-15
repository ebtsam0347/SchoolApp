package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.OffCyclePayment;

public final class OffCyclePaymentDtoMapper {

    private OffCyclePaymentDtoMapper() {}

    public static OffCyclePaymentDto of(OffCyclePayment p) {
        return new OffCyclePaymentDto(p.getId(), p.getEmployeeId(), p.getPaidOn(),
                p.getAmount(), p.getReason(), p.getPaymentMode(),
                p.getReferenceNo(), p.getSourceEventId(), p.getSourceInstallmentId(),
                p.getSource(), p.getOffCyclePaymentTypeId(), p.getOffCyclePaymentTypeName(),
                p.getStatus(),
                p.getHrApprovedBy(), p.getHrApprovedAt(),
                p.getPaidBy(), p.getPaidAt(),
                p.getCreatedBy(), p.getCreatedAt(),
                p.getUpdatedBy(), p.getUpdatedAt());
    }
}

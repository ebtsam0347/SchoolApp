package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.LeaveBalance;

import java.math.BigDecimal;

public final class LeaveBalanceDtoMapper {

    private LeaveBalanceDtoMapper() {}

    public static LeaveBalanceDto of(LeaveBalance b, String code) {
        BigDecimal available = b.getAllocated().add(b.getCarried())
                .subtract(b.getUsed()).subtract(b.getPending());
        return new LeaveBalanceDto(b.getId(), b.getEmployeeId(), b.getLeaveTypeId(), code,
                b.getYear(), b.getCycleStart(), b.getCycleEnd(),
                b.getAllocated(), b.getUsed(), b.getPending(), b.getCarried(), available);
    }
}

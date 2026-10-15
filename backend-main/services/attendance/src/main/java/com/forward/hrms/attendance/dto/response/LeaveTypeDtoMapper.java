package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.LeaveType;

public final class LeaveTypeDtoMapper {

    private LeaveTypeDtoMapper() {}

    public static LeaveTypeDto of(LeaveType t) {
        return new LeaveTypeDto(t.getId(), t.getCode(), t.getName(), t.isPaid(),
                t.getDefaultPerYear(), t.getCycleAnchor().name(),
                t.isProrateOnEntry(), t.isEncashable(),
                t.getCarryForwardMax(), t.isRequiresApproval(), t.isActive(),
                t.isShowOnDashboard());
    }
}

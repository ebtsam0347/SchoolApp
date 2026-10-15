package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.commons.approval.entity.ApprovalStep;

import java.util.Map;

public final class ApprovalStepDtoMapper {

    private ApprovalStepDtoMapper() {}

    public static ApprovalStepDto of(ApprovalStep s, Map<Long, String> userNames) {
        return new ApprovalStepDto(
                s.getStageNo(), s.getLabel(),
                s.getAssignedTo(),
                s.getAssignedTo() == null ? null : userNames.get(s.getAssignedTo()),
                s.getStatus().name(),
                s.getDecidedBy(),
                s.getDecidedBy() == null ? null : userNames.get(s.getDecidedBy()),
                s.getDecidedAt(),
                s.getNote());
    }
}

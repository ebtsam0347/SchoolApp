package com.forward.hrms.attendance.approval;

import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.commons.approval.spi.RequestTypeRegistration;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers the attendance request type keys with the commons-approval engine
 * so {@code ApprovalWorkflowService.listChains()} can enumerate them.
 */
@Component
public class AttendanceRequestTypeRegistration implements RequestTypeRegistration {

    @Override
    public Set<String> requestTypes() {
        return Arrays.stream(RequestType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }
}

package com.forward.hrms.payroll.approval;

import com.forward.hrms.commons.approval.spi.RequestTypeRegistration;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Registers payroll's request type keys with the commons-approval engine
 * so {@code ApprovalWorkflowService.listChains()} can enumerate them.
 */
@Component
public class PayrollRequestTypeRegistration implements RequestTypeRegistration {

    @Override
    public Set<String> requestTypes() {
        return Set.of("PERMANENT_ADVANCE", "SHORT_TERM_ADVANCE");
    }
}

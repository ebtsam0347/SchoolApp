package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.spi.RequestTypeRegistration;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RequisitionRequestTypeRegistration implements RequestTypeRegistration {

    @Override
    public Set<String> requestTypes() {
        return Set.of("REQUISITION_NEW_HIRE", "REQUISITION_REPLACEMENT");
    }
}

package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.spi.RequestTypeRegistration;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FactoryTourRequestTypeRegistration implements RequestTypeRegistration {

    @Override
    public Set<String> requestTypes() {
        return Set.of("FACTORY_TOUR_LOCAL", "FACTORY_TOUR_INTL");
    }
}

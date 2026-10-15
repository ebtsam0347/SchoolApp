package com.forward.hrms.employee.approval;

import com.forward.hrms.commons.approval.spi.RequestTypeRegistration;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Declares the Travel module's commons-approval request types so HR can
 * configure their chains via {@code /approval-workflows/<TYPE>}.
 */
@Component
public class TravelApprovalRequestTypeRegistration implements RequestTypeRegistration {

    @Override
    public Set<String> requestTypes() {
        return Set.of(
                TravelApprovalTypes.TRAVEL_REQUEST,
                TravelApprovalTypes.TRAVEL_ADVANCE,
                TravelApprovalTypes.TRAVEL_SETTLEMENT);
    }
}

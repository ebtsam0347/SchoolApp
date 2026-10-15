package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.Size;

/**
 * Body for ticking the finance-reconciliation flag on a paid travel
 * advance. The external payment id, when supplied, overwrites the value
 * captured at payment time.
 */
public record ReconcileTravelAdvanceRequest(

        @Size(max = 150, message = "External payment id must not exceed 150 characters")
        String externalPaymentId
) {
}

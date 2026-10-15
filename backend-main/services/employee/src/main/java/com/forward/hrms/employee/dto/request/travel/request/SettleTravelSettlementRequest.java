package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.Size;

/**
 * Body for the Accounts Payment Confirmation stage: records the payment
 * (company payable) or recovery (employee receivable) transaction
 * references as the settlement moves HR_APPROVED → PAID.
 */
public record SettleTravelSettlementRequest(

        @Size(max = 100, message = "Settlement payment reference must not exceed 100 characters")
        String settlementPaymentReference,

        @Size(max = 150, message = "External transaction id must not exceed 150 characters")
        String externalTransactionId
) {
}

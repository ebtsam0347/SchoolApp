package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.Size;

/**
 * Body for marking an approved travel expense as paid. The payment
 * reference is the identifier returned by Payroll/Finance for the
 * disbursement.
 */
public record MarkTravelExpensePaidRequest(

        @Size(max = 100, message = "Payment reference must not exceed 100 characters")
        String paymentReference
) {
}

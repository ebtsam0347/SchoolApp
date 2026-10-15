package com.forward.hrms.employee.dto.request.travel.request;

import com.forward.hrms.employee.enums.travel.TravelAdvancePaymentMethodMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for recording the money Finance actually released against an
 * approved travel advance.
 *
 * <p>All fields are optional: {@code paidAmount} defaults to the approved
 * amount (and may not exceed it); {@code paidCurrencyCode} defaults to the
 * approved / requested currency.
 */
public record MarkTravelAdvancePaidRequest(

        @DecimalMin(value = "0.00", inclusive = true, message = "Paid amount cannot be negative")
        @Digits(integer = 13, fraction = 2,
                message = "Paid amount must have at most 13 integer digits and 2 decimal places")
        BigDecimal paidAmount,

        @Size(min = 3, max = 3, message = "Paid currency code must contain exactly 3 characters")
        String paidCurrencyCode,

        @Size(max = 100, message = "Payment reference must not exceed 100 characters")
        String paymentReference,

        @Size(max = 150, message = "External payment id must not exceed 150 characters")
        String externalPaymentId,

        TravelAdvancePaymentMethodMode paymentMethod
) {
}

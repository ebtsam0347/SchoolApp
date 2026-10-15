package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.Size;

/**
 * Body for a reject / cancel action on a travel request, advance,
 * settlement or expense. The reason is optional but recommended — it is
 * echoed into the audit trail and (for rejections) surfaced to the
 * requester.
 */
public record TravelReasonRequest(

        @Size(max = 2000, message = "Reason must not exceed 2000 characters")
        String reason
) {
}

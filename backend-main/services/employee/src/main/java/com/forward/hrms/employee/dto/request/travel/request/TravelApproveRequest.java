package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.Size;

/**
 * Body for an approve action on a travel request, advance or settlement
 * approval stage. The note is optional and recorded on the approval step.
 */
public record TravelApproveRequest(

        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note
) {
}

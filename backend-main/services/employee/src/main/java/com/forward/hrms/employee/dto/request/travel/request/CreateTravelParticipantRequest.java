package com.forward.hrms.employee.dto.request.travel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nested participant information supplied by the client.
 *
 * <p>
 * Only employeeCode is accepted from the client.
 *
 * <p>
 * The following information is intentionally NOT accepted:
 *
 * <ul>
 *     <li>employeeId</li>
 *     <li>employee name</li>
 *     <li>grade</li>
 *     <li>designation</li>
 *     <li>department</li>
 *     <li>section</li>
 *     <li>participant role</li>
 *     <li>entitlement eligibility</li>
 * </ul>
 *
 * <p>
 * Those values belong to the backend/domain and should be
 * resolved from Employee Service and business rules.
 */
public record CreateTravelParticipantRequest(

        /*
         * Business employee code used to identify the employee, e.g.
         * "EMP-100245". The backend resolves this code to the Employee
         * Service employee ID and retrieves the employee information
         * required for the participant snapshot.
         */
        @NotBlank(message = "Employee code is required")
        @Size(max = 50, message = "Employee code must not exceed 50 characters")
        String employeeCode
) {
}

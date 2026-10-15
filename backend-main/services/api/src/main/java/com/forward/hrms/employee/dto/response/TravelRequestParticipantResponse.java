package com.forward.hrms.employee.dto.response;

import java.time.LocalDateTime;

/**
 * Response representation of an employee participating in a
 * Travel / Factory Tour Request.
 *
 * <p>
 * Employee master data is represented using business-friendly values
 * and historical snapshot information captured when the participant was
 * added to the request.
 *
 * <p>Lives in the shared {@code api} module (see {@link TravelRequestResponse}
 * for why); the entity-to-DTO mapping stays in employee-service's
 * {@code TravelRequestService}.
 */
public record TravelRequestParticipantResponse(

        /* Database identifier of the participant record. */
        Long id,

        /* Employee business code exposed to the API consumer. */
        String employeeCode,

        /* Employee name. */
        String employeeName,

        /* Role within the Travel Request: REQUESTER or PARTICIPANT. */
        String participantRole,

        /* Employee grade identifier captured at request creation. */
        Long gradeId,

        /* Employee grade business code captured at request creation. */
        String gradeCode,

        /* Employee grade name captured at request creation. */
        String gradeName,

        /* Employee designation identifier captured at request creation. */
        Long designationId,

        /* Employee designation name captured at request creation. */
        String designationName,

        /* Employee department identifier captured at request creation. */
        Long departmentId,

        /* Employee department name captured at request creation. */
        String departmentName,

        /* Employee section identifier captured at request creation. */
        Long sectionId,

        /* Employee section name captured at request creation. */
        String sectionName,

        /* Indicates whether the employee is currently expected to travel. */
        boolean traveling,

        /* Reason why the participant was removed from the trip. */
        String removalReason,

        /* Timestamp when the participant was removed. */
        LocalDateTime removedAt,

        /* Indicates whether the participant is eligible for travel entitlements. */
        boolean entitlementEligible,

        /* Timestamp when the participant record was created. */
        LocalDateTime createdAt,

        /* Timestamp when the participant record was last updated. */
        LocalDateTime updatedAt,

        /* Optimistic-locking version. */
        Long version
) {
}

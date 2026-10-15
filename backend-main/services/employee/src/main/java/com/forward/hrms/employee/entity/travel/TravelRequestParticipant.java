package com.forward.hrms.employee.entity.travel;


import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.travel.TravelParticipantRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Employee associated with a Travel Request.
 * <p>
 * A Travel Request can contain multiple participants.
 * <p>
 * A participant represents an employee who is associated with
 * the Travel Request and may either be the employee who initiated
 * the request or another employee traveling as part of the request.
 * <p>
 * Employee information remains owned by the Employee Service.
 * Therefore, this entity intentionally does not maintain JPA
 * relationships to Employee, Grade, Designation, Department,
 * or Section entities.
 * <p>
 * The employee ID represents the current Employee Service identity,
 * while the snapshot fields preserve the employee's organizational
 * information at the time of the Travel Request.
 * <p>
 * This is important for historical transactions because an employee's
 * grade, designation, department, section, or name may change after
 * the Travel Request has been created.
 */
@Entity
@Table(
        name = "travel_request_participants"
)
@Getter
@Setter
public class TravelRequestParticipant extends AuditedEntity {

    /**
     * Travel Request to which this participant belongs.
     * <p>
     * Stored as a plain {@code Long} FK (project convention); the DB
     * foreign key {@code fk_travel_request_participants_request} lives
     * in the migration.
     */
    @Column(name = "travel_request_id", nullable = false)
    private Long travelRequestId;

    /**
     * Employee identifier from the Employee Service.
     * <p>
     * This is intentionally stored as an employee ID rather than
     * a JPA relationship because Employee information is owned by
     * the Employee domain and the schema does not define a foreign key.
     */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /**
     * Employee/card number captured when the participant was added
     * to the Travel Request.
     * <p>
     * This snapshot is preserved for historical reporting even if
     * the employee number or related employee information changes
     * in the Employee Service.
     */
    @Column(
            name = "employee_no_snapshot",
            nullable = false,
            length = 50
    )
    private String employeeNoSnapshot;

    /**
     * Employee name captured when the participant was added.
     * <p>
     * This is a historical snapshot and should not be used as the
     * current employee master-data value.
     */
    @Column(
            name = "employee_name_snapshot",
            nullable = false,
            length = 200
    )
    private String employeeNameSnapshot;

    /**
     * Defines the employee's role within the Travel Request.
     * <p>
     * REQUESTER:
     * Employee who initiated the Travel Request.
     * <p>
     * PARTICIPANT:
     * Another employee traveling as part of the request.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "participant_role",
            nullable = false,
            length = 20
    )
    private TravelParticipantRole participantRole;

    /**
     * Employee grade identifier captured at the time of the request.
     * <p>
     * This is intentionally not a JPA relationship because Grade
     * information is owned by the Employee/Master Data domain.
     */
    @Column(name = "grade_id_snapshot")
    private Long gradeIdSnapshot;

    /**
     * Employee grade business code captured at the time of the request.
     * <p>
     * Example:
     * <p>
     * E-4
     */
    @Column(name = "grade_code_snapshot", length = 50)
    private String gradeCodeSnapshot;

    /**
     * Human-readable grade name captured at the time of the request.
     * <p>
     * Example:
     * <p>
     * Manager
     */
    @Column(name = "grade_name_snapshot", length = 150)
    private String gradeNameSnapshot;

    /**
     * Employee designation identifier captured at the time of
     * the request.
     */
    @Column(name = "designation_id_snapshot")
    private Long designationIdSnapshot;

    /**
     * Human-readable designation captured at the time of the request.
     */
    @Column(name = "designation_name_snapshot", length = 200)
    private String designationNameSnapshot;

    /**
     * Employee department identifier captured at the time of
     * the request.
     */
    @Column(name = "department_id_snapshot")
    private Long departmentIdSnapshot;

    /**
     * Department name captured at the time of the request.
     */
    @Column(name = "department_name_snapshot", length = 200)
    private String departmentNameSnapshot;

    /**
     * Employee section identifier captured at the time of
     * the request.
     */
    @Column(name = "section_id_snapshot")
    private Long sectionIdSnapshot;

    /**
     * Section name captured at the time of the request.
     */
    @Column(name = "section_name_snapshot", length = 200)
    private String sectionNameSnapshot;

    /**
     * Indicates whether this employee is currently expected to
     * travel as part of the request.
     * <p>
     * The participant record is retained instead of being deleted
     * when an employee is removed from the trip so that the
     * historical participation record remains available.
     */
    @Column(
            name = "is_travelling",
            nullable = false
    )
    private boolean travelling = true;

    /**
     * Explanation/reason for removing the participant from the trip.
     */
    @Column(
            name = "removal_reason",
            columnDefinition = "TEXT"
    )
    private String removalReason;

    /**
     * Timestamp when the participant was removed from the trip.
     */
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    /**
     * Employee/user who removed the participant.
     * <p>
     * This is intentionally stored as an ID rather than a JPA
     * relationship because the user/employee identity is managed
     * outside this Travel domain relationship.
     */
    @Column(name = "removed_by")
    private Long removedBy;

    /**
     * Indicates whether this participant is eligible for travel
     * entitlements under the applicable travel policy.
     * <p>
     * This is stored at participant level because different employees
     * associated with the same Travel Request may have different
     * financial eligibility.
     */
    @Column(
            name = "entitlement_eligible",
            nullable = false
    )
    private boolean entitlementEligible = true;

    /**
     * JPA/Hibernate optimistic-locking version.
     * <p>
     * This is a technical record version and is unrelated to any
     * business or travel policy version.
     */
    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;
}
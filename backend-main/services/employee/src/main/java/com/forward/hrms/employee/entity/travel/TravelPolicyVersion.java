package com.forward.hrms.employee.entity.travel;


import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "travel_policy_versions")
@Getter
@Setter
public class TravelPolicyVersion extends AuditedEntity {

    /**
     * Business identifier of the travel policy.
     * <p>
     * Example:
     * FACTORY_TRAVEL
     */
    @Column(name = "policy_code", nullable = false, length = 50)
    private String policyCode;

    /**
     * Human-readable name of the policy.
     * <p>
     * Example:
     * Factory Travel Policy
     */
    @Column(name = "policy_name", nullable = false, length = 200)
    private String policyName;

    /**
     * Business version number of this policy.
     * <p>
     * Example:
     * 1, 2, 3...
     * <p>
     * This is different from the JPA optimistic-lock version.
     */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /**
     * Date from which this policy version becomes applicable.
     */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /**
     * Date until which this policy version remains applicable.
     * <p>
     * NULL means that no end date has been defined.
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * Lifecycle state of the policy version.
     * <p>
     * DRAFT
     * ACTIVE
     * INACTIVE
     * RETIRED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TravelPolicyVersionStatus status = TravelPolicyVersionStatus.DRAFT;

    /**
     * Optional administrative/business description.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * JPA optimistic-locking version.
     * <p>
     * This is NOT the business policy version.
     * <p>
     * versionNo = policy version
     * version   = database record modification version
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

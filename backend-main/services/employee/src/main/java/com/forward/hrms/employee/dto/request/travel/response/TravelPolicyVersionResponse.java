package com.forward.hrms.employee.dto.request.travel.response;

import com.forward.hrms.employee.entity.travel.TravelPolicyVersion;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;

import java.time.LocalDate;

/**
 * Response representation of a Travel Policy Version. Exposes the
 * business information of a policy version while hiding the JPA entity.
 */
public record TravelPolicyVersionResponse(

        /* Database identifier of the Travel Policy Version. */
        Long id,

        /* Business identifier of the Travel Policy, e.g. "LOCAL_FACTORY_TRAVEL". */
        String policyCode,

        /* Human-readable name of the policy. */
        String policyName,

        /*
         * Business version number of this policy (1, 2, 3, ...) -
         * different from the technical JPA optimistic-lock version.
         */
        Integer versionNo,

        /* Date from which this policy version becomes applicable. */
        LocalDate effectiveFrom,

        /*
         * Date until which this policy version remains applicable.
         * NULL means no end date has been defined.
         */
        LocalDate effectiveTo,

        /* Current lifecycle state: DRAFT, ACTIVE, INACTIVE, RETIRED. */
        TravelPolicyVersionStatus status,

        /* Administrative/business description of the policy. */
        String description,

        /*
         * Technical JPA optimistic-locking version - different from the
         * business policy versionNo.
         */
        Long version
) {

    public static TravelPolicyVersionResponse of(TravelPolicyVersion e) {
        return new TravelPolicyVersionResponse(
                e.getId(), e.getPolicyCode(), e.getPolicyName(), e.getVersionNo(),
                e.getEffectiveFrom(), e.getEffectiveTo(), e.getStatus(),
                e.getDescription(), e.getVersion());
    }
}

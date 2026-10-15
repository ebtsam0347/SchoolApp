package com.forward.hrms.employee.enums.travel;

import com.forward.hrms.commons.core.error.ErrorCode;

/**
 * Machine-readable error codes for the Travel module. {@code Enum.name()}
 * satisfies the {@link ErrorCode} contract and is what the RFC 7807
 * {@code code} field carries.
 */
public enum TravelErrorCode implements ErrorCode {

    // Policy versions
    TRAVEL_POLICY_VERSION_NOT_FOUND,
    /**
     * Mutation attempted on a policy version that is not DRAFT.
     */
    TRAVEL_POLICY_VERSION_NOT_DRAFT,
    /**
     * Illegal lifecycle transition (e.g. activate a RETIRED version).
     */
    TRAVEL_POLICY_VERSION_INVALID_STATE,
    TRAVEL_POLICY_VERSION_DUPLICATE,
    /**
     * Supplied {@code policyCode} is not one of the families
     * {@link TravelType} maps a travel type to. Creating such a version
     * would yield a policy family no Travel Request could ever resolve.
     */
    TRAVEL_POLICY_CODE_UNSUPPORTED,
    /**
     * Activation refused because another version of the same
     * {@code policyCode} is already ACTIVE. At most one ACTIVE version
     * per policy code is permitted.
     */
    TRAVEL_POLICY_VERSION_ALREADY_ACTIVE,
    /**
     * Inconsistent master data: more than one ACTIVE version of the same
     * {@code policyCode} covers the requested date. Never resolved by
     * "highest version wins" — it is surfaced as a conflict.
     */
    TRAVEL_MULTIPLE_ACTIVE_POLICIES,

    // Grade limits
    TRAVEL_GRADE_LIMIT_NOT_FOUND,
    TRAVEL_GRADE_LIMIT_DUPLICATE,

    // Fuel policies
    TRAVEL_FUEL_POLICY_NOT_FOUND,
    TRAVEL_FUEL_POLICY_DUPLICATE,
    /**
     * No ACTIVE fuel policy whose code equals the leg's vehicle source
     * (and whose effective window covers today).
     */
    TRAVEL_NO_APPLICABLE_FUEL_POLICY,

    // Travel requests
    /**
     * Supplied {@code travelTypeCode} does not match a known
     * {@link com.forward.hrms.employee.enums.travel.TravelType} value.
     */
    TRAVEL_INVALID_TRAVEL_TYPE,
    TRAVEL_REQUEST_NOT_FOUND,
    TRAVEL_REQUEST_INVALID_STATE,
    TRAVEL_REQUEST_NO_PARTICIPANTS,
    /**
     * No ACTIVE policy version whose effective window covers the trip.
     */
    TRAVEL_NO_APPLICABLE_POLICY,

    // Participants / legs / entitlements
    TRAVEL_PARTICIPANT_NOT_FOUND,
    TRAVEL_PARTICIPANT_DUPLICATE,
    TRAVEL_LEG_NOT_FOUND,
    /**
     * Supplied {@code vehicleSourceCode} does not match a known
     * {@link com.forward.hrms.employee.enums.travel.VehicleSource} value.
     */
    TRAVEL_INVALID_VEHICLE_SOURCE,
    TRAVEL_ENTITLEMENT_NOT_FOUND,

    // Entitlements
    /**
     * The matched entitlement has zero remaining budget (claimed &gt; 0, eligible == 0).
     */
    TRAVEL_ENTITLEMENT_EXHAUSTED,

    // Expenses
    TRAVEL_EXPENSE_NOT_FOUND,
    TRAVEL_EXPENSE_INVALID_STATE,

    // Advances
    TRAVEL_ADVANCE_NOT_FOUND,
    TRAVEL_ADVANCE_INVALID_STATE,

    // Settlements
    TRAVEL_SETTLEMENT_NOT_FOUND,
    TRAVEL_SETTLEMENT_INVALID_STATE,
    TRAVEL_SETTLEMENT_ALREADY_EXISTS
}

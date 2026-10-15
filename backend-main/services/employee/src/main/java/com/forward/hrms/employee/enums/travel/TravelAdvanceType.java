package com.forward.hrms.employee.enums.travel;

/**
 * Defines the business purpose/type of Travel Request advance.
 *
 * <p>
 * An advance may be requested initially, additionally, due to
 * an emergency during travel, or as an administrative adjustment.
 * </p>
 */
public enum TravelAdvanceType {

    /**
     * First/main advance requested for the Travel Request.
     */
    INITIAL,

    /**
     * Additional advance requested after the initial advance.
     */
    ADDITIONAL,

    /**
     * Emergency advance required during the travel.
     */
    EMERGENCY,

    /**
     * Administrative/financial adjustment to an advance.
     */
    ADJUSTMENT
}

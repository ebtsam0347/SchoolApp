package com.forward.hrms.employee.enums.travel;

/**
 * The outcome a Transport user records at the first (TRANSPORT) stage of the
 * {@code TRAVEL_REQUEST} approval chain via
 * {@code POST /travel/requests/{id}/transport/verify}.
 *
 * <ul>
 *   <li>{@link #APPROVE} — leg verifications (if any) are applied, the
 *       entitlement snapshot is regenerated on the effective distance, and
 *       the TRANSPORT stage is approved so the request advances to DD/HOD.</li>
 *   <li>{@link #REJECT} — the TRANSPORT stage is rejected; the chain is
 *       terminated and no DD/HOD or HR stage is activated.</li>
 * </ul>
 */
public enum TransportDecision {
    APPROVE,
    REJECT
}

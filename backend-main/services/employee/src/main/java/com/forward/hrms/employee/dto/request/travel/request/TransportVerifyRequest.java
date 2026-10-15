package com.forward.hrms.employee.dto.request.travel.request;

import com.forward.hrms.employee.enums.travel.TransportDecision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for {@code POST /travel/requests/{id}/transport/verify} — the action
 * the Transport pool ({@code travel.transport.verify}) takes at the first
 * stage of the {@code TRAVEL_REQUEST} approval chain.
 *
 * <p>On {@link TransportDecision#APPROVE} the supplied {@code legs}
 * verifications are applied (planned distance is never overwritten), the
 * entitlement snapshot is regenerated on the effective distance, and the
 * TRANSPORT stage is approved so the request advances to DD/HOD. On
 * {@link TransportDecision#REJECT} the stage is rejected and the chain is
 * terminated — no DD/HOD or HR stage is created.
 */
public record TransportVerifyRequest(

        /* Whether Transport approves or rejects the transport stage. */
        @NotNull(message = "A decision (APPROVE or REJECT) is required")
        TransportDecision decision,

        /*
         * Per-leg verifications. Optional and only applied on APPROVE; a leg
         * omitted here keeps its current values.
         */
        @Valid
        List<TravelVerifyLegRequest> legs,

        /* Optional remark recorded on the approval step and in the audit trail. */
        @Size(max = 2000, message = "Remarks must not exceed 2000 characters")
        String remarks
) {
}

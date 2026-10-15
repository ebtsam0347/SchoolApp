package com.forward.hrms.employee.enums.travel;


/**
 * Lifecycle state of a Travel Request.
 *
 * <p>The approval hierarchy is TRANSPORT &rarr; DD/Requester's Head, run on
 * the {@code TRAVEL_REQUEST} commons-approval chain. Each stage has its own
 * pending / approved / rejected status so the API and UI can show exactly
 * which authority is acting; a rejection at any stage is terminal and never
 * activates the next stage. There is no HR stage in this chain.
 *
 * <pre>
 * DRAFT ─ submit ─▶ SUBMITTED ─▶ TRANSPORT_PENDING
 *                                     │
 *                        approve ─────┼───── reject ─▶ TRANSPORT_REJECTED (stop)
 *                                     ▼
 *                     TRANSPORT_APPROVED ─▶ DD_PENDING
 *                                              │
 *                                 approve ─────┼───── reject ─▶ DD_REJECTED (stop)
 *                                              ▼
 *                                  DD_APPROVED ─▶ APPROVED
 * </pre>
 *
 * <p>{@code TRANSPORT_APPROVED} / {@code DD_APPROVED} are stage-completion
 * markers (recorded on the approval step and in audit); the request status
 * then rests on the next stage's {@code *_PENDING}, and only the final
 * DD/Head approval yields {@link #APPROVED}.
 *
 * <p>{@link #SUBMITTED} and {@link #REJECTED} are retained for historical
 * rows raised before the staged statuses existed; new flows do not rest on
 * them.
 */
public enum TravelRequestStatus {

    DRAFT,

    SUBMITTED,

    TRANSPORT_PENDING,
    TRANSPORT_APPROVED,
    TRANSPORT_REJECTED,

    DD_PENDING,
    DD_APPROVED,
    DD_REJECTED,

    APPROVED,

    REJECTED,

    CANCELLED,

    IN_PROGRESS,

    COMPLETED,

    SETTLEMENT_PENDING,

    SETTLED
}

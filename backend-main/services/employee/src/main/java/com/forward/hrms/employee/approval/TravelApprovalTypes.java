package com.forward.hrms.employee.approval;

/**
 * commons-approval {@code request_type} keys owned by the Travel module,
 * plus the permission codes of the {@code TRAVEL_REQUEST} chain's
 * PERMISSION-resolver stages. Shared by the outcome handlers, the
 * {@code RequestTypeRegistration} bean, the Travel services that call
 * {@code ApprovalService.openChain}, and the {@code V0618} workflow seed.
 * Request-type keys are kept &le; 24 chars
 * ({@code approval_workflow_stage.request_type} / {@code approval_steps.request_type}).
 */
public final class TravelApprovalTypes {

    /** The trip itself. Chain: TRANSPORT &rarr; DD/Requester's Head (final). */
    public static final String TRAVEL_REQUEST = "TRAVEL_REQUEST";

    /** A travel advance requested against an approved trip. */
    public static final String TRAVEL_ADVANCE = "TRAVEL_ADVANCE";

    /** The final expense/advance settlement for a participant. */
    public static final String TRAVEL_SETTLEMENT = "TRAVEL_SETTLEMENT";

    /** Stage 1 of {@code TRAVEL_REQUEST}: Transport verification (PERMISSION resolver). */
    public static final String TRANSPORT_VERIFY_PERMISSION = "travel.transport.verify";

    private TravelApprovalTypes() {
    }
}

package com.forward.hrms.commons.approval.spi;

import com.forward.hrms.commons.approval.service.ApprovalService;

/**
 * Called inside the {@link ApprovalService} MANDATORY transaction immediately
 * after a step is approved or fully rejected. Each consuming service registers
 * one implementation per request type it owns.
 *
 * <p>Because the callback runs in the same transaction as the step write,
 * any database writes in the handler are rolled back together if the outer
 * transaction rolls back — service callers need only call
 * {@code approvals.approve()} / {@code approvals.reject()} and return the DTO.
 */
public interface ApprovalOutcomeHandler {

    /** The request type key this handler applies to (e.g. "LEAVE", "OVERTIME"). */
    String requestType();

    /**
     * Called after any stage is approved. Check {@code decision.isFinal()} to
     * distinguish an intermediate stage advance from the whole chain being approved.
     * <ul>
     *   <li>Not final — publish the "stage approved, next up" notification event.</li>
     *   <li>Final — flip domain status, publish the final approved event, emit audit.</li>
     * </ul>
     *
     * @param requestId the domain request id
     * @param decision  the decision record (step, nextPending, actor)
     */
    void onApproved(long requestId, ApprovalService.Decision decision);

    /**
     * Called immediately after any stage is rejected (which skips remaining stages).
     *
     * @param requestId the domain request id
     * @param reason    the rejection note
     * @param decision  the decision record from ApprovalService
     */
    void onRejected(long requestId, String reason, ApprovalService.Decision decision);
}

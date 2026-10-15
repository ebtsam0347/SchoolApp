package com.forward.hrms.employee.service.travel;

import com.forward.hrms.commons.approval.service.ApprovalInboxQueries;
import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.approval.TravelApprovalTypes;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelAdvanceRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelAdvanceResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.travel.TravelRequest;
import com.forward.hrms.employee.entity.travel.TravelRequestAdvance;
import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.travel.TravelAdvancePaymentMethodMode;
import com.forward.hrms.employee.enums.travel.TravelAdvanceStatus;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.TravelRequestStatus;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestAdvanceRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestParticipantRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Advances requested, approved, paid, reconciled and finally settled
 * against a Travel Request.
 *
 * <pre>
 * DRAFT ─ submit ─▶ SUBMITTED ─ approve* ─▶ APPROVED ─ markPaid ─▶ PAID ─ markSettled ─▶ SETTLED
 *   │                   │                      │
 *   └── cancel ─────────┴── reject ─▶ REJECTED │
 *                                    CANCELED  │
 * </pre>
 *
 * <p>Multi-stage approval runs through commons-approval
 * ({@code TRAVEL_ADVANCE} chain: manager → HR → accounts). The domain
 * status flip on the final decision happens in
 * {@code TravelAdvanceApprovalOutcomeHandler}, inside the engine's
 * transaction — this service only opens / advances / cancels the chain.
 *
 * <p>{@code markPaid} records the money Finance actually released;
 * {@code reconcilePayment} ticks the finance-reconciliation flag without
 * changing status; {@code markSettled} is driven by
 * {@link TravelSettlementService} when the participant's settlement is
 * finalized and splits the paid advance into a consumed
 * ({@code settledAmount}) and a returned ({@code returnedAmount}) portion.
 */
@Service
@RequiredArgsConstructor
public class TravelAdvanceService {

    /** A trip must be approved (and not yet closed) before an advance can be raised against it. */
    private static final Set<TravelRequestStatus> ADVANCEABLE = EnumSet.of(
            TravelRequestStatus.APPROVED,
            TravelRequestStatus.IN_PROGRESS);

    private final TravelRequestAdvanceRepository advances;
    private final TravelRequestRepository requests;
    private final TravelRequestParticipantRepository participants;
    private final EmployeeRepository employees;
    private final TravelNumberService travelNumbers;
    private final ApprovalService approvals;
    private final ApprovalInboxService approvalInbox;
    private final TravelApprovalAssembler travelApprovals;
    private final AuditEmitter audit;

    // ── create / edit ────────────────────────────────────────────────────────

    @Transactional
    public TravelAdvanceResponse create(Long travelRequestId, CreateTravelAdvanceRequest req) {
        TravelRequest request = requireRequest(travelRequestId);
        if (!ADVANCEABLE.contains(request.getStatus())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "travel request is " + request.getStatus()
                            + "; advances can only be raised while it is APPROVED or IN_PROGRESS");
        }
        TravelRequestParticipant participant = resolveParticipant(travelRequestId, req.employeeCode());

        TravelRequestAdvance a = new TravelRequestAdvance();
        a.setTravelRequestId(travelRequestId);
        a.setTravelRequestParticipantId(participant.getId());
        a.setAdvanceNumber(travelNumbers.nextAdvanceNumber());
        a.setStatus(TravelAdvanceStatus.DRAFT);
        a.setSettlementCurrencyCode(request.getCurrencyCode());
        applyRequestFields(a, req);
        advances.save(a);

        audit.emit("travel_advance", String.valueOf(a.getId()), "created",
                null, snapshot(a), false);
        return toResponse(a, participant);
    }

    @Transactional
    public TravelAdvanceResponse update(Long id, CreateTravelAdvanceRequest req) {
        TravelRequestAdvance a = requireAdvance(id);
        requireStatus(a, TravelAdvanceStatus.DRAFT);
        // employeeCode is fixed at creation — the participant a DRAFT advance belongs to does not move.
        Map<String, Object> before = snapshot(a);
        applyRequestFields(a, req);
        audit.emit("travel_advance", String.valueOf(id), "updated", before, snapshot(a), false);
        return toResponse(a);
    }

    // ── reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TravelAdvanceResponse get(Long id) {
        TravelRequestAdvance a = requireAdvance(id);
        return toResponse(a);
    }

    @Transactional(readOnly = true)
    public List<TravelAdvanceResponse> listForRequest(Long travelRequestId) {
        Map<Long, TravelRequestParticipant> byId = participantsById(travelRequestId);
        return advances.findByTravelRequestId(travelRequestId).stream()
                .map(a -> toResponse(a, byId.get(a.getTravelRequestParticipantId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TravelAdvanceResponse> listForParticipant(Long travelRequestParticipantId) {
        TravelRequestParticipant participant = requireParticipant(travelRequestParticipantId);
        return advances.findByTravelRequestParticipantId(travelRequestParticipantId).stream()
                .map(a -> toResponse(a, participant))
                .toList();
    }

    /**
     * Stage-aware "advances awaiting my approval" inbox — same contract as
     * {@code /permanent-advances/pending-approvals}: only rows the caller
     * can act on right now, newest first. Advances have no natural date
     * span, so the {@code from}/{@code to} window is applied on
     * {@code createdAt}.
     */
    @Transactional(readOnly = true)
    public PageResponse<TravelAdvanceResponse> pendingApprovals(LocalDate from, LocalDate to, Pageable pageable) {
        CurrentUser caller = requireCaller();
        return ApprovalInboxQueries.findPending(
                approvalInbox, TravelApprovalTypes.TRAVEL_ADVANCE, caller,
                advances::findAllById, TravelRequestAdvance::getId,
                a -> a.getCreatedAt().toLocalDate(), a -> a.getCreatedAt().toLocalDate(),
                from, to, pageable, this::toResponse);
    }

    // ── approval chain ───────────────────────────────────────────────────────

    @Transactional
    public TravelAdvanceResponse submit(Long id) {
        TravelRequestAdvance a = requireAdvance(id);
        requireStatus(a, TravelAdvanceStatus.DRAFT);
        TravelRequestParticipant participant = participantOf(a);

        a.setStatus(TravelAdvanceStatus.SUBMITTED);
        a.setApprovalRequestId(a.getId());
        approvals.openChain(TravelApprovalTypes.TRAVEL_ADVANCE, id,
                participant != null ? participant.getEmployeeId() : a.getTravelRequestParticipantId());

        audit.emit("travel_advance", String.valueOf(id), "submitted",
                null, Map.of("status", "SUBMITTED"), true);
        return toResponse(a, participant);
    }

    @Transactional
    public TravelAdvanceResponse approve(Long id, String note) {
        TravelRequestAdvance a = requireAdvance(id);
        requireStatus(a, TravelAdvanceStatus.SUBMITTED, TravelAdvanceStatus.UNDER_REVIEW);
        approvals.approve(TravelApprovalTypes.TRAVEL_ADVANCE, id, note);
        return toResponse(requireAdvance(id));
    }

    @Transactional
    public TravelAdvanceResponse reject(Long id, String reason) {
        TravelRequestAdvance a = requireAdvance(id);
        requireStatus(a, TravelAdvanceStatus.SUBMITTED, TravelAdvanceStatus.UNDER_REVIEW);
        approvals.reject(TravelApprovalTypes.TRAVEL_ADVANCE, id, reason);
        return toResponse(requireAdvance(id));
    }

    @Transactional
    public TravelAdvanceResponse cancel(Long id, String reason) {
        TravelRequestAdvance a = requireAdvance(id);
        if (a.getStatus() != TravelAdvanceStatus.DRAFT && a.getStatus() != TravelAdvanceStatus.SUBMITTED) {
            throw new ConflictError(TravelErrorCode.TRAVEL_ADVANCE_INVALID_STATE,
                    "only DRAFT or SUBMITTED advances can be cancelled");
        }
        if (a.getStatus() == TravelAdvanceStatus.SUBMITTED) {
            approvals.cancelChain(TravelApprovalTypes.TRAVEL_ADVANCE, id);
        }
        a.setStatus(TravelAdvanceStatus.CANCELED);
        audit.emit("travel_advance", String.valueOf(id), "cancelled",
                null, Map.of("status", "CANCELED", "reason", reason == null ? "" : reason), true);
        return toResponse(a);
    }

    // ── payment / reconciliation / settlement ────────────────────────────────

    /** APPROVED → PAYMENT_PENDING: hand the approved advance to Finance's payment queue. */
    @Transactional
    public TravelAdvanceResponse markPaymentPending(Long id) {
        TravelRequestAdvance a = requireAdvance(id);
        requireStatus(a, TravelAdvanceStatus.APPROVED, TravelAdvanceStatus.PARTIALLY_APPROVED);
        a.setStatus(TravelAdvanceStatus.PAYMENT_PENDING);
        audit.emit("travel_advance", String.valueOf(id), "payment_pending",
                null, Map.of("status", "PAYMENT_PENDING"), true);
        return toResponse(a);
    }

    /**
     * Record the money Finance actually released. {@code paidAmount}
     * defaults to the approved amount and may not exceed it.
     */
    @Transactional
    public TravelAdvanceResponse markPaid(Long id,
                                          BigDecimal paidAmount,
                                          String paidCurrencyCode,
                                          String paymentReference,
                                          String externalPaymentId,
                                          TravelAdvancePaymentMethodMode paymentMethod) {
        TravelRequestAdvance a = requireAdvance(id);
        requireStatus(a, TravelAdvanceStatus.APPROVED, TravelAdvanceStatus.PARTIALLY_APPROVED,
                TravelAdvanceStatus.PAYMENT_PENDING);

        BigDecimal approved = a.getApprovedAmount() != null ? a.getApprovedAmount() : a.getRequestedAmount();
        BigDecimal paid = paidAmount != null ? paidAmount : approved;
        if (paid.signum() < 0 || paid.compareTo(approved) > 0) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_ADVANCE_INVALID_STATE,
                    "paid amount must be between 0 and the approved amount (" + approved + ")");
        }

        a.setPaidAmount(paid);
        a.setPaidCurrencyCode(paidCurrencyCode != null ? currency(paidCurrencyCode)
                : a.getApprovedCurrencyCode() != null ? a.getApprovedCurrencyCode() : a.getRequestedCurrencyCode());
        a.setPaidAt(LocalDateTime.now());
        a.setPaymentReference(paymentReference);
        a.setExternalPaymentId(externalPaymentId);
        a.setPaymentMethod(paymentMethod);
        a.setStatus(TravelAdvanceStatus.PAID);

        audit.emit("travel_advance", String.valueOf(id), "paid",
                null, Map.of("status", "PAID",
                        "paidAmount", String.valueOf(paid),
                        "paymentReference", paymentReference == null ? "" : paymentReference), true);
        return toResponse(a);
    }

    /** Tick the finance-reconciliation flag; does not change {@link TravelAdvanceStatus}. */
    @Transactional
    public TravelAdvanceResponse reconcilePayment(Long id, String externalPaymentId) {
        TravelRequestAdvance a = requireAdvance(id);
        requireStatus(a, TravelAdvanceStatus.PAID, TravelAdvanceStatus.SETTLED);
        if (a.getPaidAt() == null) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_ADVANCE_INVALID_STATE,
                    "advance has no recorded payment to reconcile");
        }
        a.setPaymentReconciled(true);
        a.setPaymentReconciledAt(LocalDateTime.now());
        a.setPaymentReconciledBy(requireCaller().userId());
        if (externalPaymentId != null && !externalPaymentId.isBlank()) {
            a.setExternalPaymentId(externalPaymentId);
        }
        audit.emit("travel_advance", String.valueOf(id), "payment_reconciled",
                null, Map.of("isPaymentReconciled", "true"), false);
        return toResponse(a);
    }

    /**
     * PAID → SETTLED. Driven by {@link TravelSettlementService} once the
     * participant's settlement is finalized. {@code settledAmount +
     * returnedAmount} must not exceed the paid amount (DB
     * {@code ck_travel_request_advances_settlement_not_over_paid}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markSettled(Long id, BigDecimal settledAmount, BigDecimal returnedAmount) {
        TravelRequestAdvance a = requireAdvance(id);
        requireStatus(a, TravelAdvanceStatus.PAID);
        BigDecimal paid = a.getPaidAmount();
        if (paid == null) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_ADVANCE_INVALID_STATE,
                    "advance " + a.getAdvanceNumber() + " has not been paid; nothing to settle");
        }
        BigDecimal settled = nz(settledAmount);
        BigDecimal returned = nz(returnedAmount);
        if (settled.signum() < 0 || returned.signum() < 0
                || settled.add(returned).compareTo(paid) > 0) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_ADVANCE_INVALID_STATE,
                    "settled + returned (" + settled.add(returned) + ") exceeds the paid amount (" + paid + ")");
        }
        a.setSettledAmount(settled);
        a.setReturnedAmount(returned);
        a.setStatus(TravelAdvanceStatus.SETTLED);
        audit.emit("travel_advance", String.valueOf(id), "settled",
                null, Map.of("status", "SETTLED",
                        "settledAmount", String.valueOf(settled),
                        "returnedAmount", String.valueOf(returned)), true);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void applyRequestFields(TravelRequestAdvance a, CreateTravelAdvanceRequest req) {
        a.setAdvanceType(req.advanceType());
        a.setRequestedAmount(req.requestedAmount());
        a.setRequestedCurrencyCode(currency(req.requestedCurrencyCode()));
        a.setPurpose(req.purpose());
        a.setRemarks(req.remarks());
    }

    private TravelRequestParticipant resolveParticipant(Long travelRequestId, String employeeCode) {
        Employee emp = employees.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                        "employee not found: " + employeeCode));
        return participants.findByTravelRequestIdAndEmployeeId(travelRequestId, emp.getId())
                .orElseThrow(() -> new NotFoundError(TravelErrorCode.TRAVEL_PARTICIPANT_NOT_FOUND,
                        employeeCode + " is not a participant of this travel request"));
    }

    private Map<Long, TravelRequestParticipant> participantsById(Long travelRequestId) {
        Map<Long, TravelRequestParticipant> byId = new LinkedHashMap<>();
        for (TravelRequestParticipant p : participants.findByTravelRequestId(travelRequestId)) {
            byId.put(p.getId(), p);
        }
        return byId;
    }

    private TravelRequestParticipant participantOf(TravelRequestAdvance a) {
        return participants.findById(a.getTravelRequestParticipantId()).orElse(null);
    }

    private TravelAdvanceResponse toResponse(TravelRequestAdvance a) {
        return toResponse(a, participantOf(a));
    }

    private TravelAdvanceResponse toResponse(TravelRequestAdvance a, TravelRequestParticipant participant) {
        TravelApprovalAssembler.View chain = travelApprovals.view(
                TravelApprovalTypes.TRAVEL_ADVANCE, a.getId(), isCancellable(a));
        return TravelAdvanceResponse.of(a, participant, chain.currentStage(), chain.steps(), chain.actions());
    }

    private static boolean isCancellable(TravelRequestAdvance a) {
        return a.getStatus() == TravelAdvanceStatus.DRAFT || a.getStatus() == TravelAdvanceStatus.SUBMITTED;
    }

    private TravelRequestAdvance requireAdvance(Long id) {
        return advances.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_ADVANCE_NOT_FOUND, "travel advance not found"));
    }

    private TravelRequest requireRequest(Long id) {
        return requests.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_REQUEST_NOT_FOUND, "travel request not found"));
    }

    private TravelRequestParticipant requireParticipant(Long id) {
        return participants.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_PARTICIPANT_NOT_FOUND, "travel participant not found"));
    }

    private static void requireStatus(TravelRequestAdvance a, TravelAdvanceStatus... allowed) {
        for (TravelAdvanceStatus s : allowed) {
            if (a.getStatus() == s) {
                return;
            }
        }
        throw new ConflictError(TravelErrorCode.TRAVEL_ADVANCE_INVALID_STATE,
                "travel advance is " + a.getStatus() + "; expected one of " + java.util.Arrays.toString(allowed));
    }

    private static CurrentUser requireCaller() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required"));
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static String currency(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> snapshot(TravelRequestAdvance a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("travelRequestId", a.getTravelRequestId());
        m.put("travelRequestParticipantId", a.getTravelRequestParticipantId());
        m.put("advanceNumber", a.getAdvanceNumber());
        m.put("advanceType", String.valueOf(a.getAdvanceType()));
        m.put("requestedAmount", String.valueOf(a.getRequestedAmount()));
        m.put("requestedCurrencyCode", a.getRequestedCurrencyCode());
        m.put("approvedAmount", String.valueOf(a.getApprovedAmount()));
        m.put("paidAmount", String.valueOf(a.getPaidAmount()));
        m.put("settledAmount", String.valueOf(a.getSettledAmount()));
        m.put("returnedAmount", String.valueOf(a.getReturnedAmount()));
        m.put("status", String.valueOf(a.getStatus()));
        return m;
    }
}

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
import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.approval.TravelApprovalTypes;
import com.forward.hrms.employee.approval.TravelSettlementApprovalOutcomeHandler;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelSettlementItemRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelSettlementRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelSettlementItemResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelSettlementResponse;
import com.forward.hrms.employee.entity.travel.TravelExpenseItem;
import com.forward.hrms.employee.entity.travel.TravelRequest;
import com.forward.hrms.employee.entity.travel.TravelRequestAdvance;
import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import com.forward.hrms.employee.entity.travel.TravelRequestSettlement;
import com.forward.hrms.employee.entity.travel.TravelSettlementItem;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.TravelExpenseStatus;
import com.forward.hrms.employee.enums.travel.TravelAdvanceStatus;
import com.forward.hrms.employee.enums.travel.TravelRequestStatus;
import com.forward.hrms.employee.enums.travel.TravelSettlementDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemSourceType;
import com.forward.hrms.employee.enums.travel.TravelSettlementStatus;
import com.forward.hrms.employee.enums.travel.TravelSettlementType;
import com.forward.hrms.employee.repository.travel.TravelExpenseItemRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestAdvanceRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestParticipantRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestSettlementRepository;
import com.forward.hrms.employee.repository.travel.TravelSettlementItemRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reconciles a Travel Request participant's approved expenses against the
 * advances they were paid, plus any approved adjustments, into a single
 * payable/receivable balance.
 *
 * <pre>
 * final = Σ approved expenses  −  Σ advances paid  ±  adjustments
 *
 *   final &gt; 0 → COMPANY_PAYABLE      (company owes the employee)
 *   final &lt; 0 → EMPLOYEE_RECEIVABLE  (employee owes the company)
 *   final = 0 → NO_BALANCE
 * </pre>
 *
 * <pre>
 * DRAFT ─ submit ─▶ SUBMITTED ─ HR approve ─▶ HR_APPROVED ─ Accounts pay ─▶ PAID
 *   │                   │
 *   └── cancel ─────────┴── HR reject ─▶ REJECTED
 * </pre>
 *
 * <p>Approval runs through commons-approval ({@code TRAVEL_SETTLEMENT}
 * chain, 2 stages: HR Settlement Verification, then Accounts Payment
 * Confirmation). {@code approve}/{@code reject} only act at {@code
 * SUBMITTED} (stage 1, HR); the stage-1 approval flips the header to
 * {@code HR_APPROVED} from {@code TravelSettlementApprovalOutcomeHandler}.
 * {@code settle} is the Accounts-only action on an {@code HR_APPROVED}
 * header: it closes stage 2 of the chain itself, then finalizes the
 * header to {@code PAID}, drives each linked advance to {@code SETTLED}
 * via {@link TravelAdvanceService#markSettled}, and moves the trip
 * {@code SETTLEMENT_PENDING → SETTLED} once every traveling participant
 * has a {@code PAID} FINAL settlement.
 */
@Service
@RequiredArgsConstructor
public class TravelSettlementService {

    /** A settlement can only be raised once the trip itself is done. */
    private static final Set<TravelRequestStatus> SETTLEABLE = EnumSet.of(
            TravelRequestStatus.COMPLETED,
            TravelRequestStatus.SETTLEMENT_PENDING);

    // Includes SUBMITTED (not yet reviewed) per confirmed business rule: the
    // prior HRMS included un-reviewed expenses in settlement, and Software
    // Development has confirmed that requirement carries over here.
    // applyExpenseItem's existing approved -> eligible -> zero fallback
    // already produces the right settlement_amount for a SUBMITTED row (its
    // eligibleAmount, computed at claim time) without any further change.
    private static final Set<TravelExpenseStatus> SETTLEABLE_EXPENSE = EnumSet.of(
            TravelExpenseStatus.SUBMITTED,
            TravelExpenseStatus.APPROVED,
            TravelExpenseStatus.PARTIALLY_APPROVED,
            TravelExpenseStatus.PAID);

    private static final Set<TravelSettlementStatus> ACTIVE_SETTLEMENT = EnumSet.of(
            TravelSettlementStatus.DRAFT,
            TravelSettlementStatus.SUBMITTED,
            TravelSettlementStatus.HR_APPROVED,
            TravelSettlementStatus.UNDER_REVIEW,
            TravelSettlementStatus.APPROVED,
            TravelSettlementStatus.PARTIALLY_APPROVED,
            TravelSettlementStatus.PAYMENT_PENDING,
            TravelSettlementStatus.RECOVERY_PENDING,
            TravelSettlementStatus.PAID,
            TravelSettlementStatus.RECOVERED,
            TravelSettlementStatus.SETTLED);

    private final TravelRequestSettlementRepository settlements;
    private final TravelSettlementItemRepository settlementItems;
    private final TravelRequestRepository requests;
    private final TravelRequestParticipantRepository participants;
    private final TravelExpenseItemRepository expenses;
    private final TravelRequestAdvanceRepository advances;
    private final TravelNumberService travelNumbers;
    private final TravelAdvanceService advanceService;
    private final ApprovalService approvals;
    private final ApprovalInboxService approvalInbox;
    private final TravelApprovalAssembler travelApprovals;
    private final AuditEmitter audit;

    // ── create / recalculate ─────────────────────────────────────────────────

    @Transactional
    public TravelSettlementResponse create(Long travelRequestId, CreateTravelSettlementRequest req) {
        TravelRequest request = requireRequest(travelRequestId);
        if (!SETTLEABLE.contains(request.getStatus())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "travel request is " + request.getStatus()
                            + "; it must be COMPLETED before a settlement can be raised");
        }
        TravelRequestParticipant participant = requireParticipant(req.participantId());
        if (!participant.getTravelRequestId().equals(travelRequestId)) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_PARTICIPANT_NOT_FOUND,
                    "participant " + req.participantId() + " does not belong to this travel request");
        }
        if (req.settlementType() == TravelSettlementType.FINAL && hasActiveFinalSettlement(participant.getId())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_SETTLEMENT_ALREADY_EXISTS,
                    "a FINAL settlement already exists for this participant");
        }

        String ccy = currency(req.settlementCurrencyCode());
        TravelRequestSettlement s = new TravelRequestSettlement();
        s.setTravelRequestId(travelRequestId);
        s.setTravelRequestParticipantId(participant.getId());
        s.setSettlementNumber(travelNumbers.nextSettlementNumber());
        s.setSettlementType(req.settlementType());
        s.setStatus(TravelSettlementStatus.DRAFT);
        s.setSettlementCurrencyCode(ccy);
        s.setSettlementSummary(req.settlementSummary());
        s.setRemarks(req.remarks());

        List<TravelSettlementItem> items;
        try {
            settlements.save(s);
            items = buildItems(s, participant, req.items(), ccy);
            settlements.flush();
        } catch (DataIntegrityViolationException e) {
            // V0614 partial unique indexes: one active FINAL settlement per
            // participant / one source record per settlement. Render as the
            // module's normal 409 instead of a raw DB error.
            throw toSettlementConflict(e);
        }
        applyTotals(s, items);

        if (request.getStatus() == TravelRequestStatus.COMPLETED) {
            request.setStatus(TravelRequestStatus.SETTLEMENT_PENDING);
            audit.emit("travel_request", String.valueOf(travelRequestId), "settlement_pending",
                    null, Map.of("status", "SETTLEMENT_PENDING"), true);
        }

        audit.emit("travel_settlement", String.valueOf(s.getId()), "created",
                null, snapshot(s), false);
        return toResponse(s, participant);
    }

    /**
     * Server-side assembly layer in front of {@link #create}: the caller
     * names only the travel request and the participant — no expense /
     * advance ids — and the service discovers the participant's own
     * settleable records, builds the settlement-item payloads and hands
     * them to the existing {@link #create} engine, which runs the same
     * {@code buildItems} / {@code applyExpenseItem} / {@code applyAdvanceItem}
     * / {@code applyTotals} path as the manual endpoint. The result is a
     * {@code DRAFT} FINAL settlement.
     *
     * <p><b>Discovery</b> (participant-scoped, per the existing lifecycles):
     * <ul>
     *   <li>Expenses of this request+participant whose status is one of
     *       {@link #SETTLEABLE_EXPENSE} (SUBMITTED / APPROVED /
     *       PARTIALLY_APPROVED / PAID — SUBMITTED, i.e. not yet reviewed,
     *       is included per the confirmed business rule carried over from
     *       the prior HRMS) — same set the manual path accepts.</li>
     *   <li>Advances of this request+participant that are {@code PAID} with
     *       a recorded {@code paidAmount} — same rule as
     *       {@link #applyAdvanceItem}.</li>
     *   <li>Records already pulled into another still-active (not
     *       CANCELLED/REJECTED) settlement of this participant are skipped,
     *       so nothing is settled twice.</li>
     * </ul>
     * Request entitlements are <i>not</i> re-read here and never become a
     * cash line — the per-grade entitlement snapshot was already folded
     * into each expense's {@code eligibleAmount} at claim/review time.
     *
     * <p><b>One FINAL settlement per participant.</b> An existing
     * {@code DRAFT} FINAL settlement is returned as-is (idempotent
     * re-prepare); any other active FINAL settlement
     * (SUBMITTED…SETTLED) is a {@link ConflictError}. A previously
     * CANCELLED / REJECTED FINAL settlement does not block a fresh one —
     * matching {@link #hasActiveFinalSettlement}. The
     * {@code uq_travel_settlements_one_active_final} partial unique index
     * (V0614) is the hard backstop for a true create/create race.
     */
    @Transactional
    public TravelSettlementResponse prepareFinalSettlement(Long travelRequestId, Long participantId) {
        TravelRequest request = requireRequest(travelRequestId);
        if (!SETTLEABLE.contains(request.getStatus())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "travel request is " + request.getStatus()
                            + "; it must be COMPLETED before a settlement can be prepared");
        }
        TravelRequestParticipant participant = requireParticipant(participantId);
        if (!participant.getTravelRequestId().equals(travelRequestId)) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_PARTICIPANT_NOT_FOUND,
                    "participant " + participantId + " does not belong to this travel request");
        }
        if (!participant.isTravelling()) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE,
                    "participant " + participantId + " is not travelling on this request and cannot be settled");
        }

        // ── one FINAL settlement per participant ──────────────────────────────
        List<TravelRequestSettlement> participantSettlements =
                settlements.findByTravelRequestParticipantId(participantId);
        List<TravelRequestSettlement> activeFinals = participantSettlements.stream()
                .filter(x -> x.getSettlementType() == TravelSettlementType.FINAL)
                .filter(x -> x.getStatus() != TravelSettlementStatus.CANCELLED
                        && x.getStatus() != TravelSettlementStatus.REJECTED)
                .toList();
        TravelRequestSettlement reusableDraft = activeFinals.stream()
                .filter(x -> x.getStatus() == TravelSettlementStatus.DRAFT)
                .findFirst().orElse(null);
        if (reusableDraft != null) {
            return toResponse(reusableDraft, participant);
        }
        if (!activeFinals.isEmpty()) {
            TravelRequestSettlement blocking = activeFinals.getFirst();
            throw new ConflictError(TravelErrorCode.TRAVEL_SETTLEMENT_ALREADY_EXISTS,
                    "a FINAL settlement (" + blocking.getSettlementNumber() + ", " + blocking.getStatus()
                            + ") already exists for this participant");
        }

        // ── discover the participant's settleable records, minus any already
        //    consumed by a still-active settlement of the same participant ────
        List<Long> activeSettlementIds = participantSettlements.stream()
                .filter(x -> x.getStatus() != TravelSettlementStatus.CANCELLED
                        && x.getStatus() != TravelSettlementStatus.REJECTED)
                .map(TravelRequestSettlement::getId)
                .toList();
        Set<Long> consumedExpenseIds = consumedSourceIds(activeSettlementIds, TravelSettlementItemSourceType.EXPENSE);
        Set<Long> consumedAdvanceIds = consumedSourceIds(activeSettlementIds, TravelSettlementItemSourceType.ADVANCE);

        List<TravelExpenseItem> settleableExpenses = expenses.findByTravelRequestParticipantId(participantId).stream()
                .filter(e -> travelRequestId.equals(e.getTravelRequestId()))
                .filter(e -> SETTLEABLE_EXPENSE.contains(e.getStatus()))
                .filter(e -> !consumedExpenseIds.contains(e.getId()))
                .sorted(Comparator.comparing(TravelExpenseItem::getId))
                .toList();
        List<TravelRequestAdvance> settleableAdvances = advances.findByTravelRequestParticipantId(participantId).stream()
                .filter(a -> travelRequestId.equals(a.getTravelRequestId()))
                .filter(a -> a.getStatus() == TravelAdvanceStatus.PAID && a.getPaidAmount() != null)
                .filter(a -> !consumedAdvanceIds.contains(a.getId()))
                .sorted(Comparator.comparing(TravelRequestAdvance::getId))
                .toList();

        if (settleableExpenses.isEmpty() && settleableAdvances.isEmpty()) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE,
                    "no settleable expenses or advances found for participant " + participantId
                            + " on this travel request");
        }

        // ── build the item payloads and delegate to the existing engine ──────
        String ccy = request.getCurrencyCode();
        List<CreateTravelSettlementItemRequest> items = new ArrayList<>(
                settleableExpenses.size() + settleableAdvances.size());
        for (TravelExpenseItem e : settleableExpenses) {
            // Informational only for EXPENSE/ADVANCE — applyExpenseItem re-derives
            // both amounts from the persisted record. Kept accurate for readability.
            BigDecimal approved = e.getApprovedAmount() != null ? e.getApprovedAmount()
                    : e.getEligibleAmount() != null ? e.getEligibleAmount() : BigDecimal.ZERO;
            items.add(new CreateTravelSettlementItemRequest(
                    TravelSettlementItemSourceType.EXPENSE, e.getId(),
                    nz(e.getClaimedAmount()), approved, ccy,
                    TravelSettlementItemDirection.PAYABLE, null));
        }
        for (TravelRequestAdvance a : settleableAdvances) {
            items.add(new CreateTravelSettlementItemRequest(
                    TravelSettlementItemSourceType.ADVANCE, a.getId(),
                    a.getPaidAmount(), a.getPaidAmount(), ccy,
                    TravelSettlementItemDirection.RECEIVABLE, null));
        }

        CreateTravelSettlementRequest assembled = new CreateTravelSettlementRequest(
                participantId, TravelSettlementType.FINAL, ccy,
                "Auto-prepared final settlement", items, null);

        // create() translates the V0614 unique-index violations (the
        // creation/create race backstop) to TRAVEL_SETTLEMENT_ALREADY_EXISTS.
        return create(travelRequestId, assembled);
    }

    /** Map a V0614 unique-index violation to the module's standard 409. */
    private static ConflictError toSettlementConflict(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        String detail = cause != null && cause.contains("uq_travel_settlement_items_source")
                ? "the same expense or advance is already included in this settlement"
                : "a FINAL settlement already exists for this participant";
        return new ConflictError(TravelErrorCode.TRAVEL_SETTLEMENT_ALREADY_EXISTS, detail);
    }

    private Set<Long> consumedSourceIds(List<Long> settlementIds, TravelSettlementItemSourceType sourceType) {
        if (settlementIds.isEmpty()) {
            return Set.of();
        }
        return settlementItems.findByTravelSettlementIdInAndSourceType(settlementIds, sourceType).stream()
                .map(TravelSettlementItem::getSourceRecordId)
                .collect(Collectors.toSet());
    }

    /** Re-derive the totals and direction from the persisted items (DRAFT only). */
    @Transactional
    public TravelSettlementResponse recalculate(Long id) {
        TravelRequestSettlement s = requireSettlement(id);
        requireStatus(s, TravelSettlementStatus.DRAFT);
        Map<String, Object> before = snapshot(s);
        applyTotals(s, settlementItems.findByTravelSettlementId(id));
        audit.emit("travel_settlement", String.valueOf(id), "recalculated", before, snapshot(s), false);
        return toResponse(s, participantOf(s));
    }

    // ── reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TravelSettlementResponse get(Long id) {
        TravelRequestSettlement s = requireSettlement(id);
        return toResponse(s, participantOf(s));
    }

    @Transactional(readOnly = true)
    public List<TravelSettlementResponse> listForRequest(Long travelRequestId) {
        Map<Long, TravelRequestParticipant> byId = participantsById(travelRequestId);
        return settlements.findByTravelRequestId(travelRequestId).stream()
                .map(s -> toResponse(s, byId.get(s.getTravelRequestParticipantId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TravelSettlementResponse> listForParticipant(Long travelRequestParticipantId) {
        TravelRequestParticipant participant = requireParticipant(travelRequestParticipantId);
        return settlements.findByTravelRequestParticipantId(travelRequestParticipantId).stream()
                .map(s -> toResponse(s, participant))
                .toList();
    }

    /**
     * Stage-aware "settlements awaiting my approval" inbox — same contract
     * as {@code /leaves/pending-approvals}: only rows the caller can act on
     * right now, newest first. Settlements have no natural date span, so the
     * {@code from}/{@code to} window is applied on {@code createdAt}.
     */
    @Transactional(readOnly = true)
    public PageResponse<TravelSettlementResponse> pendingApprovals(LocalDate from, LocalDate to, Pageable pageable) {
        CurrentUser caller = requireCaller();
        return ApprovalInboxQueries.findPending(
                approvalInbox, TravelApprovalTypes.TRAVEL_SETTLEMENT, caller,
                settlements::findAllById, TravelRequestSettlement::getId,
                s -> s.getCreatedAt().toLocalDate(), s -> s.getCreatedAt().toLocalDate(),
                from, to, pageable, s -> toResponse(s, participantOf(s)));
    }

    // ── approval chain ───────────────────────────────────────────────────────

    @Transactional
    public TravelSettlementResponse submit(Long id) {
        TravelRequestSettlement s = requireSettlement(id);
        requireStatus(s, TravelSettlementStatus.DRAFT);
        if (settlementItems.findByTravelSettlementId(id).isEmpty()) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE,
                    "a settlement needs at least one item before it can be submitted");
        }
        TravelRequestParticipant participant = participantOf(s);

        s.setStatus(TravelSettlementStatus.SUBMITTED);
        s.setApprovalRequestId(s.getId());
        approvals.openChain(TravelApprovalTypes.TRAVEL_SETTLEMENT, id,
                participant != null ? participant.getEmployeeId() : s.getTravelRequestParticipantId());

        audit.emit("travel_settlement", String.valueOf(id), "submitted",
                null, Map.of("status", "SUBMITTED"), true);
        return toResponse(s, participant);
    }

    /** HR Settlement Verification (stage 1) only — SUBMITTED → HR_APPROVED. */
    @Transactional
    public TravelSettlementResponse approve(Long id, String note) {
        TravelRequestSettlement s = requireSettlement(id);
        requireStatus(s, TravelSettlementStatus.SUBMITTED);
        approvals.approve(TravelApprovalTypes.TRAVEL_SETTLEMENT, id, note);
        return toResponse(requireSettlement(id), participantOf(requireSettlement(id)));
    }

    /** HR Settlement Verification (stage 1) only — SUBMITTED → REJECTED (terminal). */
    @Transactional
    public TravelSettlementResponse reject(Long id, String reason) {
        TravelRequestSettlement s = requireSettlement(id);
        requireStatus(s, TravelSettlementStatus.SUBMITTED);
        approvals.reject(TravelApprovalTypes.TRAVEL_SETTLEMENT, id, reason);
        return toResponse(requireSettlement(id), participantOf(requireSettlement(id)));
    }

    @Transactional
    public TravelSettlementResponse cancel(Long id, String reason) {
        TravelRequestSettlement s = requireSettlement(id);
        if (s.getStatus() != TravelSettlementStatus.DRAFT && s.getStatus() != TravelSettlementStatus.SUBMITTED) {
            throw new ConflictError(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE,
                    "only DRAFT or SUBMITTED settlements can be cancelled");
        }
        if (s.getStatus() == TravelSettlementStatus.SUBMITTED) {
            approvals.cancelChain(TravelApprovalTypes.TRAVEL_SETTLEMENT, id);
        }
        s.setStatus(TravelSettlementStatus.CANCELLED);
        audit.emit("travel_settlement", String.valueOf(id), "cancelled",
                null, Map.of("status", "CANCELLED", "reason", reason == null ? "" : reason), true);
        revertTripIfNoActiveSettlements(s.getTravelRequestId());
        return toResponse(s, participantOf(s));
    }

    // ── payment / settlement ─────────────────────────────────────────────────

    /**
     * Accounts Payment Confirmation (stage 2) — the only action that can
     * move an {@code HR_APPROVED} settlement forward. Closes stage 2 of the
     * {@code TRAVEL_SETTLEMENT} chain itself (so the pending-approval inbox
     * and the {@code currentStage}/{@code steps} view resolve correctly —
     * {@link TravelSettlementApprovalOutcomeHandler} is a no-op on this
     * final decision by design), then in the same transaction: records the
     * payment reference, flips to PAID, drives every linked advance to
     * SETTLED (splitting each paid advance into a consumed and a returned
     * portion), and moves the trip to SETTLED once all traveling
     * participants are PAID.
     */
    @Transactional
    public TravelSettlementResponse settle(Long id, String settlementPaymentReference, String externalTransactionId) {
        TravelRequestSettlement s = requireSettlement(id);
        requireStatus(s, TravelSettlementStatus.HR_APPROVED);
        CurrentUser caller = requireCaller();

        approvals.approve(TravelApprovalTypes.TRAVEL_SETTLEMENT, id, settlementPaymentReference);

        settleLinkedAdvances(s);

        LocalDateTime now = LocalDateTime.now();
        s.setStatus(TravelSettlementStatus.PAID);
        s.setSettledAt(now);
        s.setSettlementPaymentReference(settlementPaymentReference);
        s.setExternalTransactionId(externalTransactionId);
        s.setIsFinalized(true);
        s.setFinalizedAt(now);
        s.setFinalizedBy(caller.userId());

        audit.emit("travel_settlement", String.valueOf(id), "paid",
                null, Map.of("status", "PAID",
                        "direction", String.valueOf(s.getSettlementDirection()),
                        "finalSettlementAmount", String.valueOf(s.getFinalSettlementAmount())), true);

        completeTripIfAllSettled(s.getTravelRequestId());
        return toResponse(s, participantOf(s));
    }

    // ── item building / totals ───────────────────────────────────────────────

    private List<TravelSettlementItem> buildItems(TravelRequestSettlement s,
                                                  TravelRequestParticipant participant,
                                                  List<CreateTravelSettlementItemRequest> supplied,
                                                  String ccy) {
        List<TravelSettlementItem> out = new ArrayList<>(supplied.size());
        for (CreateTravelSettlementItemRequest r : supplied) {
            TravelSettlementItem it = new TravelSettlementItem();
            it.setTravelSettlementId(s.getId());
            it.setSourceType(r.sourceType());
            it.setSourceRecordId(r.sourceRecordId());
            it.setCurrencyCode(ccy);
            it.setDescription(r.description());

            switch (r.sourceType()) {
                case EXPENSE -> applyExpenseItem(it, r, s, participant);
                case ADVANCE -> applyAdvanceItem(it, r, s, participant);
                case ADJUSTMENT -> applyAdjustmentItem(it, r);
            }

            if (it.getSettlementAmount().compareTo(it.getSourceAmount()) > 0) {
                throw new UnprocessableError(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE,
                        "settlement amount " + it.getSettlementAmount()
                                + " exceeds the source amount " + it.getSourceAmount());
            }
            out.add(settlementItems.save(it));
        }
        return out;
    }

    private void applyExpenseItem(TravelSettlementItem it, CreateTravelSettlementItemRequest r,
                                  TravelRequestSettlement s, TravelRequestParticipant participant) {
        TravelExpenseItem e = expenses.findById(r.sourceRecordId())
                .orElseThrow(() -> new NotFoundError(TravelErrorCode.TRAVEL_EXPENSE_NOT_FOUND,
                        "travel expense not found: " + r.sourceRecordId()));
        requireBelongsToParticipant(e.getTravelRequestId(), e.getTravelRequestParticipantId(), s, participant, "expense");
        if (!SETTLEABLE_EXPENSE.contains(e.getStatus())) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_EXPENSE_INVALID_STATE,
                    "expense " + e.getId() + " is " + e.getStatus()
                            + "; only submitted, approved, partially approved or paid expenses can be settled");
        }
        BigDecimal approved = e.getApprovedAmount() != null ? e.getApprovedAmount()
                : e.getEligibleAmount() != null ? e.getEligibleAmount() : BigDecimal.ZERO;
        it.setSourceAmount(e.getClaimedAmount());
        it.setSettlementAmount(approved);
        it.setDirection(TravelSettlementItemDirection.PAYABLE);
        if (it.getDescription() == null || it.getDescription().isBlank()) {
            it.setDescription("Expense " + e.getExpenseCategoryCode()
                    + (e.getExpenseSubcategoryCode() == null ? "" : " / " + e.getExpenseSubcategoryCode()));
        }
    }

    private void applyAdvanceItem(TravelSettlementItem it, CreateTravelSettlementItemRequest r,
                                  TravelRequestSettlement s, TravelRequestParticipant participant) {
        TravelRequestAdvance a = advances.findById(r.sourceRecordId())
                .orElseThrow(() -> new NotFoundError(TravelErrorCode.TRAVEL_ADVANCE_NOT_FOUND,
                        "travel advance not found: " + r.sourceRecordId()));
        requireBelongsToParticipant(a.getTravelRequestId(), a.getTravelRequestParticipantId(), s, participant, "advance");
        if (a.getStatus() != TravelAdvanceStatus.PAID || a.getPaidAmount() == null) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_ADVANCE_INVALID_STATE,
                    "advance " + a.getAdvanceNumber() + " is not PAID; only paid advances can be settled");
        }
        it.setSourceAmount(a.getPaidAmount());
        it.setSettlementAmount(a.getPaidAmount());
        it.setDirection(TravelSettlementItemDirection.RECEIVABLE);
        if (it.getDescription() == null || it.getDescription().isBlank()) {
            it.setDescription("Advance " + a.getAdvanceNumber());
        }
    }

    private void applyAdjustmentItem(TravelSettlementItem it, CreateTravelSettlementItemRequest r) {
        if (r.direction() == null) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE,
                    "an ADJUSTMENT item must carry a direction");
        }
        BigDecimal amount = nz(r.settlementAmount());
        it.setSettlementAmount(amount);
        it.setSourceAmount(r.sourceAmount() != null ? r.sourceAmount() : amount);
        it.setDirection(r.direction());
    }

    /**
     * {@code final = Σ approved expenses − Σ advances paid ± adjustments}.
     * Eligible/claimed totals are widened to keep the DB invariants
     * ({@code approved ≤ eligible ≤ claimed}) when a reviewer over-approved
     * a line against policy.
     */
    private void applyTotals(TravelRequestSettlement s, List<TravelSettlementItem> items) {
        Money claimed = Money.ZERO;
        Money eligible = Money.ZERO;
        Money approvedExpenses = Money.ZERO;
        Money advancesPaid = Money.ZERO;
        Money adjustments = Money.ZERO;

        for (TravelSettlementItem it : items) {
            Money amount = Money.of(it.getSettlementAmount());
            switch (it.getSourceType()) {
                case EXPENSE -> {
                    TravelExpenseItem e = expenses.findById(it.getSourceRecordId()).orElse(null);
                    if (e != null) {
                        claimed = claimed.add(Money.of(nz(e.getClaimedAmount())));
                        eligible = eligible.add(Money.of(nz(e.getEligibleAmount())));
                    }
                    approvedExpenses = approvedExpenses.add(amount);
                }
                case ADVANCE -> advancesPaid = advancesPaid.add(amount);
                case ADJUSTMENT -> adjustments = it.getDirection() == TravelSettlementItemDirection.PAYABLE
                        ? adjustments.add(amount)
                        : adjustments.subtract(amount);
            }
        }

        BigDecimal totalEligible = eligible.amount().max(approvedExpenses.amount());
        BigDecimal totalClaimed = claimed.amount().max(totalEligible);

        s.setTotalClaimedExpenses(totalClaimed);
        s.setTotalEligibleExpenses(totalEligible);
        s.setTotalApprovedExpenses(approvedExpenses.amount());
        s.setTotalAdvancesPaid(advancesPaid.amount());
        s.setTotalAdjustments(adjustments.amount());

        BigDecimal net = approvedExpenses.subtract(advancesPaid).add(adjustments).amount();
        if (net.signum() > 0) {
            s.setFinalSettlementAmount(net);
            s.setSettlementDirection(TravelSettlementDirection.COMPANY_PAYABLE);
        } else if (net.signum() < 0) {
            s.setFinalSettlementAmount(net.negate());
            s.setSettlementDirection(TravelSettlementDirection.EMPLOYEE_RECEIVABLE);
        } else {
            s.setFinalSettlementAmount(BigDecimal.ZERO);
            s.setSettlementDirection(TravelSettlementDirection.NO_BALANCE);
        }
    }

    /**
     * Split each paid advance linked to this settlement into a consumed
     * ({@code settledAmount}) and returned ({@code returnedAmount}) portion.
     * The consumable pool is the approved-expense total; advances are
     * filled in id order.
     */
    private void settleLinkedAdvances(TravelRequestSettlement s) {
        List<TravelSettlementItem> advanceItems = settlementItems.findByTravelSettlementIdAndSourceType(
                s.getId(), TravelSettlementItemSourceType.ADVANCE);
        advanceItems.sort(Comparator.comparing(TravelSettlementItem::getSourceRecordId));

        Money consumable = Money.of(nz(s.getTotalApprovedExpenses()));
        for (TravelSettlementItem it : advanceItems) {
            TravelRequestAdvance a = advances.findById(it.getSourceRecordId())
                    .orElseThrow(() -> new NotFoundError(TravelErrorCode.TRAVEL_ADVANCE_NOT_FOUND,
                            "travel advance not found: " + it.getSourceRecordId()));
            if (a.getStatus() == TravelAdvanceStatus.SETTLED) {
                continue;
            }
            Money paid = Money.of(nz(a.getPaidAmount()));
            Money consumed = consumable.min(paid);
            BigDecimal settled = consumed.amount();
            BigDecimal returned = paid.subtract(consumed).amount();
            advanceService.markSettled(a.getId(), settled, returned);
            consumable = consumable.subtract(consumed);
        }
    }

    // ── trip lifecycle coupling ──────────────────────────────────────────────

    private void completeTripIfAllSettled(Long travelRequestId) {
        TravelRequest request = requireRequest(travelRequestId);
        if (request.getStatus() != TravelRequestStatus.SETTLEMENT_PENDING) {
            return;
        }
        List<TravelRequestParticipant> travelling = participants.findTravellingByTravelRequestId(travelRequestId);
        List<TravelRequestSettlement> all = settlements.findByTravelRequestId(travelRequestId);
        boolean everyoneSettled = !travelling.isEmpty() && travelling.stream().allMatch(p ->
                all.stream().anyMatch(x -> x.getTravelRequestParticipantId().equals(p.getId())
                        && x.getSettlementType() == TravelSettlementType.FINAL
                        && x.getStatus() == TravelSettlementStatus.PAID));
        if (everyoneSettled) {
            request.setStatus(TravelRequestStatus.SETTLED);
            audit.emit("travel_request", String.valueOf(travelRequestId), "settled",
                    null, Map.of("status", "SETTLED"), true);
        }
    }

    private void revertTripIfNoActiveSettlements(Long travelRequestId) {
        TravelRequest request = requireRequest(travelRequestId);
        if (request.getStatus() != TravelRequestStatus.SETTLEMENT_PENDING) {
            return;
        }
        boolean anyActive = settlements.findByTravelRequestId(travelRequestId).stream()
                .anyMatch(x -> ACTIVE_SETTLEMENT.contains(x.getStatus()));
        if (!anyActive) {
            request.setStatus(TravelRequestStatus.COMPLETED);
            audit.emit("travel_request", String.valueOf(travelRequestId), "settlement_reopened",
                    null, Map.of("status", "COMPLETED"), true);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean hasActiveFinalSettlement(Long participantId) {
        return settlements.findByTravelRequestParticipantId(participantId).stream()
                .anyMatch(x -> x.getSettlementType() == TravelSettlementType.FINAL
                        && x.getStatus() != TravelSettlementStatus.CANCELLED
                        && x.getStatus() != TravelSettlementStatus.REJECTED);
    }

    private void requireBelongsToParticipant(Long recordRequestId, Long recordParticipantId,
                                             TravelRequestSettlement s, TravelRequestParticipant participant,
                                             String label) {
        if (!recordRequestId.equals(s.getTravelRequestId())
                || !recordParticipantId.equals(participant.getId())) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE,
                    "the referenced " + label + " does not belong to this travel request participant");
        }
    }

    private Map<Long, TravelRequestParticipant> participantsById(Long travelRequestId) {
        Map<Long, TravelRequestParticipant> byId = new LinkedHashMap<>();
        for (TravelRequestParticipant p : participants.findByTravelRequestId(travelRequestId)) {
            byId.put(p.getId(), p);
        }
        return byId;
    }

    private TravelRequestParticipant participantOf(TravelRequestSettlement s) {
        return participants.findById(s.getTravelRequestParticipantId()).orElse(null);
    }

    private TravelSettlementResponse toResponse(TravelRequestSettlement s, TravelRequestParticipant participant) {
        List<TravelSettlementItemResponse> itemDtos = settlementItems.findByTravelSettlementId(s.getId()).stream()
                .map(TravelSettlementItemResponse::of)
                .toList();
        TravelApprovalAssembler.View chain = travelApprovals.view(
                TravelApprovalTypes.TRAVEL_SETTLEMENT, s.getId(), isCancellable(s));
        return TravelSettlementResponse.of(s, itemDtos, participant,
                chain.currentStage(), chain.steps(), chain.actions());
    }

    private static boolean isCancellable(TravelRequestSettlement s) {
        return s.getStatus() == TravelSettlementStatus.DRAFT || s.getStatus() == TravelSettlementStatus.SUBMITTED;
    }

    private TravelRequestSettlement requireSettlement(Long id) {
        return settlements.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_SETTLEMENT_NOT_FOUND, "travel settlement not found"));
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

    private static void requireStatus(TravelRequestSettlement s, TravelSettlementStatus... allowed) {
        for (TravelSettlementStatus st : allowed) {
            if (s.getStatus() == st) {
                return;
            }
        }
        throw new ConflictError(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE,
                "travel settlement is " + s.getStatus() + "; expected one of " + java.util.Arrays.toString(allowed));
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

    private static Map<String, Object> snapshot(TravelRequestSettlement s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("travelRequestId", s.getTravelRequestId());
        m.put("travelRequestParticipantId", s.getTravelRequestParticipantId());
        m.put("settlementNumber", s.getSettlementNumber());
        m.put("settlementType", String.valueOf(s.getSettlementType()));
        m.put("status", String.valueOf(s.getStatus()));
        m.put("totalApprovedExpenses", String.valueOf(s.getTotalApprovedExpenses()));
        m.put("totalAdvancesPaid", String.valueOf(s.getTotalAdvancesPaid()));
        m.put("totalAdjustments", String.valueOf(s.getTotalAdjustments()));
        m.put("finalSettlementAmount", String.valueOf(s.getFinalSettlementAmount()));
        m.put("settlementDirection", String.valueOf(s.getSettlementDirection()));
        m.put("settlementCurrencyCode", s.getSettlementCurrencyCode());
        return m;
    }
}

package com.forward.hrms.employee.service.travel;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelExpenseItemRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelExpenseItemResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.travel.TravelExpenseItem;
import com.forward.hrms.employee.entity.travel.TravelRequest;
import com.forward.hrms.employee.entity.travel.TravelRequestEntitlement;
import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.TravelExpenseStatus;
import com.forward.hrms.employee.enums.travel.TravelRequestStatus;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.travel.TravelExpenseItemRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestEntitlementRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestParticipantRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Actual expense claims incurred during a Travel Request.
 *
 * <pre>
 * SUBMITTED ─ approve ─▶ APPROVED / PARTIALLY_APPROVED ─ markPaid ─▶ PAID
 *     │
 *     ├─ reject ─▶ REJECTED
 *     └─ cancel ─▶ CANCELLED
 * </pre>
 *
 * <p>Expenses use a single-reviewer flow (there is no per-expense
 * commons-approval chain and no {@code approval_request_id} column); the
 * aggregate {@code TRAVEL_SETTLEMENT} chain is where the multi-stage
 * approval happens.
 *
 * <p>Eligibility is computed against the participant's entitlement
 * snapshot: {@code eligible = min(claimed, entitlement budget - already
 * consumed)}. An expense with no matching entitlement is fully eligible
 * pending review.
 */
@Service
@RequiredArgsConstructor
public class TravelExpenseService {

    private static final Set<TravelRequestStatus> CLAIMABLE = EnumSet.of(
            TravelRequestStatus.APPROVED,
            TravelRequestStatus.IN_PROGRESS,
            TravelRequestStatus.COMPLETED,
            TravelRequestStatus.SETTLEMENT_PENDING);

    private final TravelExpenseItemRepository expenses;
    private final TravelRequestRepository requests;
    private final TravelRequestParticipantRepository participants;
    private final TravelRequestEntitlementRepository entitlements;
    private final EmployeeRepository employees;
    private final AuditEmitter audit;

    // ── create / edit ────────────────────────────────────────────────────────

    @Transactional
    public TravelExpenseItemResponse create(Long travelRequestId, CreateTravelExpenseItemRequest req) {
        TravelRequest request = requireRequest(travelRequestId);
        if (!CLAIMABLE.contains(request.getStatus())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE,
                    "travel request is " + request.getStatus() + "; expenses can only be added once it is "
                            + "APPROVED or later");
        }
        TravelRequestParticipant participant = resolveParticipant(travelRequestId, req.employeeCode());
        TravelRequestEntitlement entitlement = resolveEntitlement(participant.getId(), req.entitlementTypeCode());

        TravelExpenseItem e = new TravelExpenseItem();
        e.setTravelRequestId(travelRequestId);
        e.setTravelRequestParticipantId(participant.getId());
        e.setStatus(TravelExpenseStatus.SUBMITTED);
        applyClaimFields(e, req, request, entitlement);
        expenses.save(e);

        audit.emit("travel_expense", String.valueOf(e.getId()), "created",
                null, snapshot(e), false);
        return TravelExpenseItemResponse.of(e, participant);
    }

    @Transactional
    public TravelExpenseItemResponse update(Long id, CreateTravelExpenseItemRequest req) {
        TravelExpenseItem e = requireExpense(id);
        requireStatus(e, TravelExpenseStatus.SUBMITTED);
        TravelRequest request = requireRequest(e.getTravelRequestId());
        TravelRequestParticipant participant = requireParticipant(e.getTravelRequestParticipantId());
        TravelRequestEntitlement entitlement = resolveEntitlement(participant.getId(), req.entitlementTypeCode());

        Map<String, Object> before = snapshot(e);
        applyClaimFields(e, req, request, entitlement);
        audit.emit("travel_expense", String.valueOf(id), "updated", before, snapshot(e), false);
        return TravelExpenseItemResponse.of(e, participant);
    }

    // ── reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TravelExpenseItemResponse get(Long id) {
        TravelExpenseItem e = requireExpense(id);
        return TravelExpenseItemResponse.of(e, participants.findById(e.getTravelRequestParticipantId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<TravelExpenseItemResponse> listForRequest(Long travelRequestId) {
        Map<Long, TravelRequestParticipant> byId = participants.findByTravelRequestId(travelRequestId).stream()
                .collect(Collectors.toMap(TravelRequestParticipant::getId, Function.identity()));
        return expenses.findByTravelRequestId(travelRequestId).stream()
                .map(e -> TravelExpenseItemResponse.of(e, byId.get(e.getTravelRequestParticipantId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TravelExpenseItemResponse> listForParticipant(Long travelRequestParticipantId) {
        TravelRequestParticipant participant = requireParticipant(travelRequestParticipantId);
        return expenses.findByTravelRequestParticipantId(travelRequestParticipantId).stream()
                .map(e -> TravelExpenseItemResponse.of(e, participant))
                .toList();
    }

    // ── review ───────────────────────────────────────────────────────────────

    @Transactional
    public TravelExpenseItemResponse approve(Long id, BigDecimal approvedAmount, String note) {
        TravelExpenseItem e = requireExpense(id);
        requireStatus(e, TravelExpenseStatus.SUBMITTED, TravelExpenseStatus.UNDER_REVIEW);

        BigDecimal eligible = e.getEligibleAmount() == null ? BigDecimal.ZERO : e.getEligibleAmount();
        BigDecimal approved = approvedAmount != null ? approvedAmount : eligible;
        if (approved.signum() < 0 || approved.compareTo(e.getClaimedAmount()) > 0) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_EXPENSE_INVALID_STATE,
                    "approved amount must be between 0 and the claimed amount");
        }

        e.setApprovedAmount(approved);
        e.setNonReimbursableAmount(e.getClaimedAmount().subtract(approved));
        e.setStatus(approved.compareTo(eligible) >= 0
                ? TravelExpenseStatus.APPROVED : TravelExpenseStatus.PARTIALLY_APPROVED);
        e.setRejectionReason(null);
        stampReview(e);

        audit.emit("travel_expense", String.valueOf(id), "approved",
                null, Map.of("status", String.valueOf(e.getStatus()),
                        "approvedAmount", String.valueOf(approved),
                        "note", note == null ? "" : note), true);
        return TravelExpenseItemResponse.of(e, participants.findById(e.getTravelRequestParticipantId()).orElse(null));
    }

    @Transactional
    public TravelExpenseItemResponse reject(Long id, String reason) {
        TravelExpenseItem e = requireExpense(id);
        requireStatus(e, TravelExpenseStatus.SUBMITTED, TravelExpenseStatus.UNDER_REVIEW);

        e.setStatus(TravelExpenseStatus.REJECTED);
        e.setRejectionReason(reason);
        e.setApprovedAmount(BigDecimal.ZERO);
        e.setNonReimbursableAmount(e.getClaimedAmount());
        stampReview(e);

        audit.emit("travel_expense", String.valueOf(id), "rejected",
                null, Map.of("status", "REJECTED", "reason", reason == null ? "" : reason), true);
        return TravelExpenseItemResponse.of(e, participants.findById(e.getTravelRequestParticipantId()).orElse(null));
    }

    @Transactional
    public TravelExpenseItemResponse cancel(Long id, String reason) {
        TravelExpenseItem e = requireExpense(id);
        requireStatus(e, TravelExpenseStatus.SUBMITTED, TravelExpenseStatus.UNDER_REVIEW);
        e.setStatus(TravelExpenseStatus.CANCELLED);
        audit.emit("travel_expense", String.valueOf(id), "cancelled",
                null, Map.of("status", "CANCELLED", "reason", reason == null ? "" : reason), true);
        return TravelExpenseItemResponse.of(e, participants.findById(e.getTravelRequestParticipantId()).orElse(null));
    }

    @Transactional
    public TravelExpenseItemResponse markPaid(Long id, String paymentReference) {
        TravelExpenseItem e = requireExpense(id);
        requireStatus(e, TravelExpenseStatus.APPROVED, TravelExpenseStatus.PARTIALLY_APPROVED);
        e.setStatus(TravelExpenseStatus.PAID);
        e.setPaidAt(LocalDateTime.now());
        e.setPaymentReference(paymentReference);
        audit.emit("travel_expense", String.valueOf(id), "paid",
                null, Map.of("status", "PAID", "paymentReference", paymentReference == null ? "" : paymentReference),
                true);
        return TravelExpenseItemResponse.of(e, participants.findById(e.getTravelRequestParticipantId()).orElse(null));
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void applyClaimFields(TravelExpenseItem e,
                                  CreateTravelExpenseItemRequest req,
                                  TravelRequest request,
                                  TravelRequestEntitlement entitlement) {
        // Computed first, against the not-yet-mutated entity, so a rejection here
        // (zero remaining entitlement) leaves both a brand-new (create) and an
        // already-managed (update) TravelExpenseItem untouched — nothing to save,
        // nothing dirty to roll back.
        BigDecimal eligible = computeEligible(req.claimedAmount(), entitlement, e.getId());
        if (eligible.signum() == 0) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_ENTITLEMENT_EXHAUSTED,
                    "no remaining eligible entitlement" + (entitlement == null ? "" :
                            " for " + entitlement.getEntitlementTypeCode()) + " on this travel request");
        }

        String settlementCcy = request.getCurrencyCode();
        boolean sameCcy = req.claimedCurrencyCode().equalsIgnoreCase(settlementCcy);

        e.setExpenseCategoryCode(req.expenseCategoryCode());
        e.setExpenseSubcategoryCode(req.expenseSubcategoryCode());
        e.setDescription(req.description());
        e.setExpenseAt(req.expenseAt());
        e.setClaimedAmount(req.claimedAmount());
        e.setClaimedCurrencyCode(req.claimedCurrencyCode());
        e.setSettlementCurrencyCode(settlementCcy);
        e.setExchangeRate(sameCcy ? BigDecimal.ONE : null);
        e.setConvertedClaimedAmount(sameCcy ? req.claimedAmount() : null);
        e.setReceiptReference(req.receiptReference());
        e.setReceiptDocumentId(req.receiptDocumentId());
        e.setReceiptRequired(Boolean.TRUE);
        e.setReceiptAttached(req.receiptDocumentId() != null && !req.receiptDocumentId().isBlank());
        e.setRemarks(req.remarks());
        e.setTravelRequestEntitlementId(entitlement == null ? null : entitlement.getId());
        e.setEligibleAmount(eligible);
        e.setNonReimbursableAmount(req.claimedAmount().subtract(eligible));
    }

    /**
     * {@code min(claimed, entitlement.totalLimit - already-consumed)}; {@code claimed} when uncapped.
     */
    private BigDecimal computeEligible(BigDecimal claimed, TravelRequestEntitlement entitlement, Long excludeId) {
        if (entitlement == null || entitlement.getTotalLimitAmount() == null) {
            return claimed;
        }
        BigDecimal consumed = expenses.findByTravelRequestEntitlementId(entitlement.getId()).stream()
                .filter(x -> excludeId == null || !x.getId().equals(excludeId))
                .filter(x -> x.getStatus() != TravelExpenseStatus.REJECTED
                        && x.getStatus() != TravelExpenseStatus.CANCELLED)
                .map(x -> x.getApprovedAmount() != null ? x.getApprovedAmount()
                        : x.getEligibleAmount() != null ? x.getEligibleAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Money remaining = Money.of(entitlement.getTotalLimitAmount()).subtract(Money.of(consumed)).max(Money.ZERO);
        return Money.of(claimed).min(remaining).amount();
    }

    private TravelRequestEntitlement resolveEntitlement(Long participantId, String entitlementTypeCode) {
        if (entitlementTypeCode == null || entitlementTypeCode.isBlank()) {
            return null;
        }
        return entitlements.findActiveByParticipantIdAndType(participantId, entitlementTypeCode).stream()
                .findFirst().orElse(null);
    }

    private TravelRequestParticipant resolveParticipant(Long travelRequestId, String employeeCode) {
        Employee emp = employees.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND,
                        "employee not found: " + employeeCode));
        return participants.findByTravelRequestIdAndEmployeeId(travelRequestId, emp.getId())
                .orElseThrow(() -> new NotFoundError(TravelErrorCode.TRAVEL_PARTICIPANT_NOT_FOUND,
                        employeeCode + " is not a participant of this travel request"));
    }

    private void stampReview(TravelExpenseItem e) {
        e.setReviewedBy(requireCaller().userId());
        e.setReviewedAt(LocalDateTime.now());
    }

    private TravelExpenseItem requireExpense(Long id) {
        return expenses.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_EXPENSE_NOT_FOUND, "travel expense not found"));
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

    private static void requireStatus(TravelExpenseItem e, TravelExpenseStatus... allowed) {
        for (TravelExpenseStatus s : allowed) {
            if (e.getStatus() == s) {
                return;
            }
        }
        throw new ConflictError(TravelErrorCode.TRAVEL_EXPENSE_INVALID_STATE,
                "travel expense is " + e.getStatus() + "; expected one of " + java.util.Arrays.toString(allowed));
    }

    private static CurrentUser requireCaller() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required"));
    }

    private static Map<String, Object> snapshot(TravelExpenseItem e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("travelRequestId", e.getTravelRequestId());
        m.put("travelRequestParticipantId", e.getTravelRequestParticipantId());
        m.put("travelRequestEntitlementId", e.getTravelRequestEntitlementId());
        m.put("expenseCategoryCode", e.getExpenseCategoryCode());
        m.put("expenseSubcategoryCode", e.getExpenseSubcategoryCode());
        m.put("expenseAt", String.valueOf(e.getExpenseAt()));
        m.put("claimedAmount", String.valueOf(e.getClaimedAmount()));
        m.put("eligibleAmount", String.valueOf(e.getEligibleAmount()));
        m.put("status", String.valueOf(e.getStatus()));
        return m;
    }
}

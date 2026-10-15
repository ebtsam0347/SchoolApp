package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelAdvanceRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelExpenseItemRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelFuelPolicyRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelLegRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelParticipantRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyGradeLimitRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyVersionRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelSettlementItemRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelSettlementRequest;
import com.forward.hrms.employee.dto.request.travel.request.TransportVerifyRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelAdvanceResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelExpenseItemResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelPolicyVersionResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelSettlementItemResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelSettlementResponse;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.travel.TravelRequestSettlement;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.travel.TransportDecision;
import com.forward.hrms.employee.enums.travel.TravelAdvancePaymentMethodMode;
import com.forward.hrms.employee.enums.travel.TravelAdvanceStatus;
import com.forward.hrms.employee.enums.travel.TravelAdvanceType;
import com.forward.hrms.employee.enums.travel.TravelExpenseStatus;
import com.forward.hrms.employee.enums.travel.TravelLimitType;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;
import com.forward.hrms.employee.enums.travel.TravelSettlementDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemSourceType;
import com.forward.hrms.employee.enums.travel.TravelSettlementStatus;
import com.forward.hrms.employee.enums.travel.TravelSettlementType;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.travel.TravelFuelPolicyRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestParticipantRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestSettlementRepository;
import com.forward.hrms.employee.service.travel.TravelAdvanceService;
import com.forward.hrms.employee.service.travel.TravelExpenseService;
import com.forward.hrms.employee.service.travel.TravelFuelPolicyService;
import com.forward.hrms.employee.service.travel.TravelPolicyService;
import com.forward.hrms.employee.service.travel.TravelRequestService;
import com.forward.hrms.employee.service.travel.TravelSettlementService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused coverage for the server-side settlement <b>prepare</b> layer
 * ({@code POST /travel/requests/{id}/participants/{pid}/settlement/prepare}
 * &rarr; {@link TravelSettlementService#prepareFinalSettlement}). Runs the
 * whole trip lifecycle against a real Postgres (Testcontainers) and proves
 * that:
 *
 * <ul>
 *   <li>the caller supplies <b>no</b> expense/advance ids — the service
 *       discovers the participant's own settleable records;</li>
 *   <li>the discovered records feed the <b>existing</b>
 *       {@code create} / {@code buildItems} / {@code applyTotals} engine —
 *       COMPANY_PAYABLE and EMPLOYEE_RECEIVABLE come out identical to the
 *       manual path;</li>
 *   <li>there is at most one active FINAL settlement per participant
 *       (idempotent DRAFT reuse; 409 on an active one; DB partial unique
 *       index as the concurrency backstop);</li>
 *   <li>a source expense/advance cannot land in the same settlement twice
 *       (DB partial unique index);</li>
 *   <li>non-settleable expenses/advances are skipped;</li>
 *   <li>different-grade participants each get their own settlement from
 *       their own entitlement-capped expenses;</li>
 *   <li>the existing submit / approve / settle / advance-reconciliation
 *       flow still runs unchanged on a prepared settlement.</li>
 * </ul>
 */
@SpringBootTest
@WithTestcontainers
class TravelSettlementPreparationIntegrationTest {

    private static final AtomicLong USER_ID_SEQ = new AtomicLong(System.nanoTime() & 0x7fffffffL);

    @Autowired TravelPolicyService policyService;
    @Autowired TravelRequestService requestService;
    @Autowired TravelAdvanceService advanceService;
    @Autowired TravelExpenseService expenseService;
    @Autowired TravelSettlementService settlementService;
    @Autowired TravelFuelPolicyService fuelPolicyService;
    @Autowired TravelFuelPolicyRepository fuelPolicyRepo;
    @Autowired EmployeeRepository employees;
    @Autowired GradeRepository grades;
    @Autowired TravelRequestParticipantRepository participants;
    @Autowired TravelRequestSettlementRepository settlements;

    private String gradeCode;
    private Long gradeId;
    private Employee requester;
    private long managerUserId;

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @BeforeEach
    void setUp() {
        Grade g = new Grade();
        gradeCode = "PRP-G-" + System.nanoTime();
        g.setCode(gradeCode);
        g.setName("Prepare Test Grade");
        g.setLevel(1);
        gradeId = grades.save(g).getId();

        managerUserId = nextUserId();
        long managerEmployeeId = persistEmployee("Prep Manager " + managerUserId, managerUserId, null, null);
        long requesterUserId = nextUserId();
        persistEmployee("Prep Requester " + requesterUserId, requesterUserId, managerEmployeeId, gradeId);
        requester = employees.findByUserId(requesterUserId).orElseThrow();

        // Only a Manager/HOD may raise a travel request — give the requester a direct report.
        long reportUserId = nextUserId();
        persistEmployee("Prep Report " + reportUserId, reportUserId, requester.getId(), gradeId);

        // ACTIVE FACTORY_TRAVEL version + DINNER / PER_DAY / 1500 for the requester's grade.
        CurrentUserHolder.set(actor(nextUserId(), "travel.admin"));
        TravelPolicyVersionResponse version = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                "LOCAL_FACTORY_TRAVEL", "Factory Travel Policy", LocalDate.now().minusMonths(1), null, "prep"));
        policyService.addGradeLimit(version.id(), new CreateTravelPolicyGradeLimitRequest(
                gradeCode, "DINNER", TravelLimitType.PER_DAY, new BigDecimal("1500.00"), "PKR", true, null));
        activateReplacingCurrent(version);
        CurrentUserHolder.clear();

        // code IS the vehicle-source code — ensure the one this suite's legs use is active.
        // Idempotent: the Testcontainers Postgres persists across every test (and class).
        ensureFuelPolicy("COMPANY", "10.00");
    }

    /** Ensures an ACTIVE fuel policy exists for {@code vehicleSourceCode}; a no-op if one already does. */
    private void ensureFuelPolicy(String vehicleSourceCode, String fuelAverage) {
        if (fuelPolicyRepo.existsByCodeAndIsActiveTrue(vehicleSourceCode)) {
            return;
        }
        CurrentUserHolder.set(actor(nextUserId(), "travel.admin"));
        fuelPolicyService.create(new CreateTravelFuelPolicyRequest(
                vehicleSourceCode, "Test " + vehicleSourceCode + " Fuel Policy", new BigDecimal(fuelAverage),
                LocalDate.now().minusMonths(1), null, true));
        CurrentUserHolder.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1 + 4 + 5 + 6 : COMPANY_PAYABLE, auto-discovery of expenses AND advance
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_companyPayable_autoDiscoversExpensesAndAdvance_noIdsFromCaller() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("5000.00"));

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse transport = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("8000.00")));
        TravelExpenseItemResponse dinner = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), "DINNER", "MEAL", "DINNER", new BigDecimal("10000.00")));
        assertThat(dinner.eligibleAmount()).isEqualByComparingTo("3000.00"); // 1500 × 2 days entitlement cap

        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(transport.id(), null, null);
        expenseService.approve(dinner.id(), null, null);

        // The caller names ONLY the request + participant. No expense/advance ids.
        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        assertThat(prepared.status()).isEqualTo(TravelSettlementStatus.DRAFT);
        assertThat(prepared.settlementType()).isEqualTo(TravelSettlementType.FINAL);
        assertThat(prepared.items()).hasSize(3);

        // items reference the real source records — 2 expenses + 1 advance
        assertThat(prepared.items())
                .filteredOn(i -> i.sourceType() == TravelSettlementItemSourceType.EXPENSE)
                .extracting(TravelSettlementItemResponse::sourceRecordId)
                .containsExactlyInAnyOrder(transport.id(), dinner.id());
        assertThat(prepared.items())
                .filteredOn(i -> i.sourceType() == TravelSettlementItemSourceType.ADVANCE)
                .extracting(TravelSettlementItemResponse::sourceRecordId)
                .containsExactly(f.advanceId);

        // same numbers as the manual path: 11000 approved − 5000 advance = 6000 payable
        assertThat(prepared.totalApprovedExpenses()).isEqualByComparingTo("11000.00");
        assertThat(prepared.totalAdvancesPaid()).isEqualByComparingTo("5000.00");
        assertThat(prepared.finalSettlementAmount()).isEqualByComparingTo("6000.00");
        assertThat(prepared.settlementDirection()).isEqualTo(TravelSettlementDirection.COMPANY_PAYABLE);

        // creating the settlement flipped the trip to SETTLEMENT_PENDING (existing behaviour)
        assertThat(requestService.get(f.tripId).status()).isEqualTo("SETTLEMENT_PENDING");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2 : EMPLOYEE_RECEIVABLE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_employeeReceivable_whenAdvanceExceedsApprovedExpenses() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("5000.00"));

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse taxi = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("2500.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(taxi.id(), null, null);

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        assertThat(prepared.totalApprovedExpenses()).isEqualByComparingTo("2500.00");
        assertThat(prepared.totalAdvancesPaid()).isEqualByComparingTo("5000.00");
        assertThat(prepared.finalSettlementAmount()).isEqualByComparingTo("2500.00");
        assertThat(prepared.settlementDirection()).isEqualTo(TravelSettlementDirection.EMPLOYEE_RECEIVABLE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7 : idempotent DRAFT reuse — same prepare call twice, no duplicate draft
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_calledTwice_returnsSameDraft_noDuplicateRow() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("2000.00"));
        approvedTaxi(f.tripId, "3000.00");

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse first = settlementService.prepareFinalSettlement(f.tripId, f.participantId);
        TravelSettlementResponse second = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(settlements.findByTravelRequestParticipantId(f.participantId)).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8 : an already-active FINAL settlement blocks a new prepare
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_whenAnActiveFinalSettlementExists_isConflict() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("2000.00"));
        approvedTaxi(f.tripId, "3000.00");

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse draft = settlementService.prepareFinalSettlement(f.tripId, f.participantId);
        settlementService.submit(draft.id()); // DRAFT → SUBMITTED : now "active", not reusable

        assertThatThrownBy(() -> settlementService.prepareFinalSettlement(f.tripId, f.participantId))
                .isInstanceOf(ConflictError.class);
        assertThat(settlements.findByTravelRequestParticipantId(f.participantId)).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9 : DB partial unique index blocks a second active FINAL settlement row
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void dbIndex_blocksSecondActiveFinalSettlementRow_forSameParticipant() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("2000.00"));
        approvedTaxi(f.tripId, "3000.00");

        CurrentUserHolder.set(requesterUser());
        settlementService.prepareFinalSettlement(f.tripId, f.participantId); // one DRAFT FINAL exists

        TravelRequestSettlement duplicate = new TravelRequestSettlement();
        duplicate.setTravelRequestId(f.tripId);
        duplicate.setTravelRequestParticipantId(f.participantId);
        duplicate.setSettlementNumber("SET-DUP-" + System.nanoTime());
        duplicate.setSettlementType(TravelSettlementType.FINAL);
        duplicate.setStatus(TravelSettlementStatus.DRAFT);
        duplicate.setSettlementDirection(TravelSettlementDirection.NO_BALANCE);
        duplicate.setSettlementCurrencyCode("PKR");

        assertThatThrownBy(() -> settlements.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10 : the same expense twice in one settlement — the V0614 item index
    //      fires and create() renders it as the module's normal 409, not a
    //      raw DataIntegrityViolationException.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void create_duplicateSourceRecordInSameSettlement_isRenderedAsBusinessConflict() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        Long expenseId = approvedTaxi(f.tripId, "4000.00");

        CurrentUserHolder.set(requesterUser());
        CreateTravelSettlementRequest twoOfTheSameExpense = new CreateTravelSettlementRequest(
                f.participantId, TravelSettlementType.FINAL, "PKR", "dup test",
                List.of(
                        new CreateTravelSettlementItemRequest(TravelSettlementItemSourceType.EXPENSE, expenseId,
                                new BigDecimal("4000.00"), new BigDecimal("4000.00"), "PKR",
                                TravelSettlementItemDirection.PAYABLE, null),
                        new CreateTravelSettlementItemRequest(TravelSettlementItemSourceType.EXPENSE, expenseId,
                                new BigDecimal("4000.00"), new BigDecimal("4000.00"), "PKR",
                                TravelSettlementItemDirection.PAYABLE, null)),
                null);

        assertThatThrownBy(() -> settlementService.create(f.tripId, twoOfTheSameExpense))
                .isInstanceOf(ConflictError.class)
                .hasMessageContaining("already included in this settlement");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10b : CANCELLED FINAL does not block a fresh prepare (retry lifecycle)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_afterCancellingTheDraft_canPrepareAgain() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("2000.00"));
        approvedTaxi(f.tripId, "3000.00");

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse first = settlementService.prepareFinalSettlement(f.tripId, f.participantId);
        settlementService.cancel(first.id(), "changed my mind");

        TravelSettlementResponse retried = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        assertThat(retried.id()).isNotEqualTo(first.id());
        assertThat(retried.status()).isEqualTo(TravelSettlementStatus.DRAFT);
        // one CANCELLED + one DRAFT; still exactly one ACTIVE final
        List<TravelRequestSettlement> all = settlements.findByTravelRequestParticipantId(f.participantId);
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(s -> s.getStatus() != TravelSettlementStatus.CANCELLED).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10c : an expense already consumed by another still-active settlement of
    //       the same participant is not discovered again
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_skipsExpenseAlreadyConsumedByAnActiveInterimSettlement() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("2000.00"));

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse expenseA = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("3000.00")));
        TravelExpenseItemResponse expenseB = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "PARKING", "PARKING", new BigDecimal("1500.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(expenseA.id(), null, null);
        expenseService.approve(expenseB.id(), null, null);

        // an INTERIM settlement that consumes expense A
        CurrentUserHolder.set(requesterUser());
        settlementService.create(f.tripId, new CreateTravelSettlementRequest(
                f.participantId, TravelSettlementType.INTERIM, "PKR", "interim",
                List.of(new CreateTravelSettlementItemRequest(TravelSettlementItemSourceType.EXPENSE, expenseA.id(),
                        new BigDecimal("3000.00"), new BigDecimal("3000.00"), "PKR",
                        TravelSettlementItemDirection.PAYABLE, null)),
                null));

        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        List<Long> expenseSources = prepared.items().stream()
                .filter(i -> i.sourceType() == TravelSettlementItemSourceType.EXPENSE)
                .map(TravelSettlementItemResponse::sourceRecordId).toList();
        assertThat(expenseSources).containsExactly(expenseB.id());     // A skipped, B included
        assertThat(prepared.items())
                .filteredOn(i -> i.sourceType() == TravelSettlementItemSourceType.ADVANCE)
                .extracting(TravelSettlementItemResponse::sourceRecordId)
                .containsExactly(f.advanceId);                          // advance still discovered
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10d : REJECTED FINAL does not block a fresh prepare (retry lifecycle)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_afterRejectingTheSubmittedSettlement_canPrepareAgain() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("2000.00"));
        approvedTaxi(f.tripId, "3000.00");

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse first = settlementService.prepareFinalSettlement(f.tripId, f.participantId);
        settlementService.submit(first.id());
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.reject(first.id(), "receipts missing");

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse retried = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        assertThat(retried.id()).isNotEqualTo(first.id());
        assertThat(retried.status()).isEqualTo(TravelSettlementStatus.DRAFT);
        List<TravelRequestSettlement> all = settlements.findByTravelRequestParticipantId(f.participantId);
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(s -> s.getStatus() != TravelSettlementStatus.REJECTED).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10e : ADJUSTMENT items are exempt from the source-uniqueness index —
    //       several adjustment lines may share a source reference (unchanged).
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void create_multipleAdjustmentItems_withSameSourceRef_areAllowed() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse s = settlementService.create(f.tripId, new CreateTravelSettlementRequest(
                f.participantId, TravelSettlementType.FINAL, "PKR", "adjustments",
                List.of(
                        new CreateTravelSettlementItemRequest(TravelSettlementItemSourceType.ADJUSTMENT, 0L,
                                new BigDecimal("500.00"), new BigDecimal("500.00"), "PKR",
                                TravelSettlementItemDirection.PAYABLE, "per diem top-up"),
                        new CreateTravelSettlementItemRequest(TravelSettlementItemSourceType.ADJUSTMENT, 0L,
                                new BigDecimal("300.00"), new BigDecimal("300.00"), "PKR",
                                TravelSettlementItemDirection.RECEIVABLE, "penalty")),
                null));

        assertThat(s.items()).hasSize(2);
        assertThat(s.items()).allMatch(i -> i.sourceType() == TravelSettlementItemSourceType.ADJUSTMENT
                && i.sourceRecordId() == 0L);
        // 500 payable − 300 receivable = 200 net payable
        assertThat(s.finalSettlementAmount()).isEqualByComparingTo("200.00");
        assertThat(s.settlementDirection()).isEqualTo(TravelSettlementDirection.COMPANY_PAYABLE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11 + 12 : non-settleable expenses / advances are excluded from discovery
    //
    // Business rule (confirmed): SUBMITTED expenses ARE settleable — the
    // prior HRMS included un-reviewed expenses in settlement, so SETTLEABLE_EXPENSE
    // now covers SUBMITTED / APPROVED / PARTIALLY_APPROVED / PAID. Only
    // REJECTED / CANCELLED expenses (and non-PAID advances) are excluded.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_excludesNonSettleableExpenseAndNonPaidAdvance() {
        Fixture f = tripApprovedNoAdvance();

        // one PAID advance (settleable) + one that is only APPROVED (not settleable —
        // advance settleability is unchanged by this business rule)
        Long paidAdvanceId = raiseApproveAndPayAdvance(f.tripId, new BigDecimal("2000.00"));
        Long approvedOnlyAdvanceId = raiseAndApproveAdvance(f.tripId, new BigDecimal("1000.00"));

        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(f.tripId);
        requestService.completeTravel(f.tripId);

        // one APPROVED expense (settleable) + one REJECTED (still excluded)
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse approved = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("3000.00")));
        TravelExpenseItemResponse rejected = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "PARKING", "PARKING", new BigDecimal("500.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(approved.id(), null, null);
        expenseService.reject(rejected.id(), "not a business expense");

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        List<Long> expenseSources = prepared.items().stream()
                .filter(i -> i.sourceType() == TravelSettlementItemSourceType.EXPENSE)
                .map(TravelSettlementItemResponse::sourceRecordId).toList();
        List<Long> advanceSources = prepared.items().stream()
                .filter(i -> i.sourceType() == TravelSettlementItemSourceType.ADVANCE)
                .map(TravelSettlementItemResponse::sourceRecordId).toList();

        assertThat(expenseSources).containsExactly(approved.id());
        assertThat(expenseSources).doesNotContain(rejected.id());
        assertThat(advanceSources).containsExactly(paidAdvanceId);
        assertThat(advanceSources).doesNotContain(approvedOnlyAdvanceId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW — SUBMITTED expenses are settleable (confirmed business rule: the
    // prior HRMS included un-reviewed expenses in settlement; settlement
    // preparation must not require APPROVED before an expense can be included).
    // ─────────────────────────────────────────────────────────────────────────

    /** Requirement #1 + #4: a COMPLETED trip with only a SUBMITTED expense and no paid advance still prepares. */
    @Test
    void prepare_onlySubmittedExpense_noPaidAdvance_succeeds() {
        Fixture f = tripApprovedNoAdvance();
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(f.tripId);
        requestService.completeTravel(f.tripId);

        TravelExpenseItemResponse submitted = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("3000.00")));
        assertThat(submitted.status()).isEqualTo(TravelExpenseStatus.SUBMITTED);
        assertThat(submitted.approvedAmount()).isNull();
        assertThat(submitted.eligibleAmount()).isEqualByComparingTo("3000.00"); // no entitlement cap on TAXI

        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        assertThat(prepared.status()).isEqualTo(TravelSettlementStatus.DRAFT);
        assertThat(prepared.settlementType()).isEqualTo(TravelSettlementType.FINAL);
        assertThat(prepared.items()).hasSize(1);
        assertThat(prepared.items().getFirst().sourceType()).isEqualTo(TravelSettlementItemSourceType.EXPENSE);
        assertThat(prepared.items().getFirst().sourceRecordId()).isEqualTo(submitted.id());

        // settlement_amount for an un-reviewed expense falls back to eligibleAmount
        // (approvedAmount is null) — the existing approved -> eligible -> zero chain
        // in applyExpenseItem, unchanged.
        assertThat(prepared.items().getFirst().settlementAmount()).isEqualByComparingTo("3000.00");
        assertThat(prepared.totalApprovedExpenses()).isEqualByComparingTo("3000.00");
        assertThat(prepared.finalSettlementAmount()).isEqualByComparingTo("3000.00");
        assertThat(prepared.settlementDirection()).isEqualTo(TravelSettlementDirection.COMPANY_PAYABLE);
    }

    /** Requirement #2: a SUBMITTED expense and an APPROVED expense are both included. */
    @Test
    void prepare_submittedAndApprovedExpenses_bothIncluded() {
        Fixture f = tripApprovedNoAdvance();
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(f.tripId);
        requestService.completeTravel(f.tripId);

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse submitted = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("1200.00")));
        TravelExpenseItemResponse approved = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "PARKING", "PARKING", new BigDecimal("800.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(approved.id(), null, null);

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        List<Long> expenseSources = prepared.items().stream()
                .filter(i -> i.sourceType() == TravelSettlementItemSourceType.EXPENSE)
                .map(TravelSettlementItemResponse::sourceRecordId).toList();
        assertThat(expenseSources).containsExactlyInAnyOrder(submitted.id(), approved.id());
        assertThat(prepared.totalApprovedExpenses()).isEqualByComparingTo("2000.00"); // 1200 + 800
    }

    /** Requirement #3: SUBMITTED is included, REJECTED is excluded, in the same trip. */
    @Test
    void prepare_submittedIncluded_rejectedExcluded() {
        Fixture f = tripApprovedNoAdvance();
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(f.tripId);
        requestService.completeTravel(f.tripId);

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse submitted = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("1500.00")));
        TravelExpenseItemResponse rejected = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "PARKING", "PARKING", new BigDecimal("600.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.reject(rejected.id(), "no receipt");

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        List<Long> expenseSources = prepared.items().stream()
                .filter(i -> i.sourceType() == TravelSettlementItemSourceType.EXPENSE)
                .map(TravelSettlementItemResponse::sourceRecordId).toList();
        assertThat(expenseSources).containsExactly(submitted.id());
        assertThat(expenseSources).doesNotContain(rejected.id());
        assertThat(prepared.totalApprovedExpenses()).isEqualByComparingTo("1500.00");
    }

    /** CANCELLED expenses remain excluded, matching REJECTED. */
    @Test
    void prepare_cancelledExpenseExcluded() {
        Fixture f = tripApprovedNoAdvance();
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(f.tripId);
        requestService.completeTravel(f.tripId);

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse submitted = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("1500.00")));
        TravelExpenseItemResponse cancelled = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "PARKING", "PARKING", new BigDecimal("600.00")));
        expenseService.cancel(cancelled.id(), "duplicate entry");

        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);

        List<Long> expenseSources = prepared.items().stream()
                .filter(i -> i.sourceType() == TravelSettlementItemSourceType.EXPENSE)
                .map(TravelSettlementItemResponse::sourceRecordId).toList();
        assertThat(expenseSources).containsExactly(submitted.id());
        assertThat(expenseSources).doesNotContain(cancelled.id());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 13 : trip not COMPLETED → prepare is a conflict; no settleable records → 422
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_whenTripNotCompleted_isConflict() {
        Fixture f = tripApprovedNoAdvance();
        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> settlementService.prepareFinalSettlement(f.tripId, f.participantId))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void prepare_whenNoSettleableRecords_isUnprocessable() {
        Fixture f = tripApprovedNoAdvance();
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(f.tripId);
        requestService.completeTravel(f.tripId);

        assertThatThrownBy(() -> settlementService.prepareFinalSettlement(f.tripId, f.participantId))
                .isInstanceOf(UnprocessableError.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 14 : multiple participants, different grades → each settlement uses its
    //      own entitlement-capped expenses
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_perParticipant_differentGrades_eachSettlementUsesItsOwnRecords() {
        // second grade + its own DINNER limit, on a fresh ACTIVE policy version
        Grade gb = new Grade();
        String gradeCodeB = "PRP-GB-" + System.nanoTime();
        gb.setCode(gradeCodeB);
        gb.setName("Prepare Test Grade B");
        gb.setLevel(2);
        Long gradeIdB = grades.save(gb).getId();

        CurrentUserHolder.set(actor(nextUserId(), "travel.admin"));
        TravelPolicyVersionResponse v2 = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                "LOCAL_FACTORY_TRAVEL", "Factory Travel Policy v2", LocalDate.now().minusMonths(1), null, "prep-v2"));
        policyService.addGradeLimit(v2.id(), new CreateTravelPolicyGradeLimitRequest(
                gradeCode, "DINNER", TravelLimitType.PER_DAY, new BigDecimal("1500.00"), "PKR", true, null));
        policyService.addGradeLimit(v2.id(), new CreateTravelPolicyGradeLimitRequest(
                gradeCodeB, "DINNER", TravelLimitType.PER_DAY, new BigDecimal("3000.00"), "PKR", true, null));
        activateReplacingCurrent(v2);
        CurrentUserHolder.clear();

        long coUserId = nextUserId();
        long coEmpId = persistEmployee("Prep CoTraveller " + coUserId, coUserId, null, gradeIdB);
        Employee co = employees.findById(coEmpId).orElseThrow();

        // trip with the requester (grade A) + co-traveller (grade B)
        CurrentUserHolder.set(requesterUser());
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        TravelRequestResponse created = requestService.create(new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode()),
                        new CreateTravelParticipantRequest(co.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-1234", "Corolla", new BigDecimal("120.00"), null))));
        Long tripId = created.id();
        requestService.submit(tripId);
        transportApprove(tripId);
        CurrentUserHolder.set(user(managerUserId));
        requestService.approve(tripId, null);
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(tripId);
        requestService.completeTravel(tripId);

        // each participant claims the same 5000 DINNER — capped by their own grade
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse dinnerA = expenseService.create(tripId,
                expenseReq(requester.getEmployeeCode(), "DINNER", "MEAL", "DINNER", new BigDecimal("5000.00")));
        TravelExpenseItemResponse dinnerB = expenseService.create(tripId,
                expenseReq(co.getEmployeeCode(), "DINNER", "MEAL", "DINNER", new BigDecimal("5000.00")));
        assertThat(dinnerA.eligibleAmount()).isEqualByComparingTo("3000.00"); // 1500 × 2 (grade A)
        assertThat(dinnerB.eligibleAmount()).isEqualByComparingTo("5000.00"); // min(5000, 3000 × 2) (grade B)

        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(dinnerA.id(), null, null);
        expenseService.approve(dinnerB.id(), null, null);

        Long participantA = participants.findByTravelRequestIdAndEmployeeId(tripId, requester.getId()).orElseThrow().getId();
        Long participantB = participants.findByTravelRequestIdAndEmployeeId(tripId, co.getId()).orElseThrow().getId();

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse settlementA = settlementService.prepareFinalSettlement(tripId, participantA);
        TravelSettlementResponse settlementB = settlementService.prepareFinalSettlement(tripId, participantB);

        assertThat(settlementA.id()).isNotEqualTo(settlementB.id());
        assertThat(settlementA.items()).extracting(TravelSettlementItemResponse::sourceRecordId)
                .containsExactly(dinnerA.id());
        assertThat(settlementB.items()).extracting(TravelSettlementItemResponse::sourceRecordId)
                .containsExactly(dinnerB.id());
        assertThat(settlementA.finalSettlementAmount()).isEqualByComparingTo("3000.00"); // grade A cap
        assertThat(settlementB.finalSettlementAmount()).isEqualByComparingTo("5000.00"); // grade B cap
        assertThat(settlementA.settlementDirection()).isEqualTo(TravelSettlementDirection.COMPANY_PAYABLE);
        assertThat(settlementB.settlementDirection()).isEqualTo(TravelSettlementDirection.COMPANY_PAYABLE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 15 : the existing submit → approve → settle → advance-reconciliation flow
    //      still runs unchanged on a PREPARED settlement
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void prepare_thenExistingApprovalAndSettleFlow_reconcilesAdvance_andSettlesTrip() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("5000.00"));

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse taxi = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal("8000.00")));
        TravelExpenseItemResponse dinner = expenseService.create(f.tripId,
                expenseReq(requester.getEmployeeCode(), "DINNER", "MEAL", "DINNER", new BigDecimal("10000.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(taxi.id(), null, null);
        expenseService.approve(dinner.id(), null, null);

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse prepared = settlementService.prepareFinalSettlement(f.tripId, f.participantId);
        settlementService.submit(prepared.id());

        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(prepared.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        TravelSettlementResponse settled = settlementService.settle(prepared.id(), "PAYOUT-PREP-1", null);

        assertThat(settled.status()).isEqualTo(TravelSettlementStatus.PAID);
        assertThat(settled.finalized()).isTrue();
        assertThat(settled.finalSettlementAmount()).isEqualByComparingTo("6000.00");

        TravelAdvanceResponse advance = advanceService.get(f.advanceId);
        assertThat(advance.status()).isEqualTo(TravelAdvanceStatus.SETTLED);
        assertThat(advance.settledAmount()).isEqualByComparingTo("5000.00");
        assertThat(advance.returnedAmount()).isEqualByComparingTo("0.00");

        assertThat(requestService.get(f.tripId).status()).isEqualTo("SETTLED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 16 : the 2-stage HR Settlement Verification -> Accounts Payment
    //      Confirmation chain (DRAFT -> SUBMITTED -> HR_APPROVED -> PAID)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void hrApprove_movesSubmittedToHrApproved_andOpensAccountsStage() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");

        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        TravelSettlementResponse approved = settlementService.approve(submitted.id(), "verified");

        assertThat(approved.status()).isEqualTo(TravelSettlementStatus.HR_APPROVED);
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.approvedBy()).isNotNull();
        assertThat(approved.currentStage().stageRole()).isEqualTo("Accounts Payment Confirmation");
        assertThat(approved.steps()).hasSize(2);
        assertThat(approved.steps().get(0).stageRole()).isEqualTo("HR Settlement Verification");
        assertThat(approved.steps().get(0).status()).isEqualTo("APPROVED");
        assertThat(approved.steps().get(1).stageRole()).isEqualTo("Accounts Payment Confirmation");
        assertThat(approved.steps().get(1).status()).isEqualTo("PENDING");
    }

    @Test
    void hrReject_movesSubmittedToTerminalRejected() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");

        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        TravelSettlementResponse rejected = settlementService.reject(submitted.id(), "receipts missing");

        assertThat(rejected.status()).isEqualTo(TravelSettlementStatus.REJECTED);
        assertThat(rejected.currentStage()).isNull();

        // terminal: neither stage can act on it again
        assertThatThrownBy(() -> settlementService.approve(rejected.id(), null))
                .isInstanceOf(ConflictError.class);
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        assertThatThrownBy(() -> settlementService.settle(rejected.id(), "PAY", null))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void accountsMarkPaid_movesHrApprovedToPaid_andPopulatesPaymentFields() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(submitted.id(), null);

        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        TravelSettlementResponse paid = settlementService.settle(submitted.id(), "PAY-REF-1", "TXN-1");

        assertThat(paid.status()).isEqualTo(TravelSettlementStatus.PAID);
        assertThat(paid.settlementPaymentReference()).isEqualTo("PAY-REF-1");
        assertThat(paid.externalTransactionId()).isEqualTo("TXN-1");
        assertThat(paid.settledAt()).isNotNull();
        assertThat(paid.finalized()).isTrue();
        assertThat(paid.finalizedAt()).isNotNull();
        assertThat(paid.finalizedBy()).isNotNull();
        assertThat(paid.currentStage()).isNull();
        assertThat(paid.steps().get(1).status()).isEqualTo("APPROVED");
    }

    @Test
    void accountsCannotAct_beforeHrApproval() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");

        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        assertThatThrownBy(() -> settlementService.settle(submitted.id(), "PAY", null))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void accountsCannotMarkSubmittedDirectlyAsPaid() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");

        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        assertThatThrownBy(() -> settlementService.settle(submitted.id(), "PAY", null))
                .isInstanceOf(ConflictError.class);
        assertThat(settlementService.get(submitted.id()).status()).isEqualTo(TravelSettlementStatus.SUBMITTED);
    }

    @Test
    void paidAndRejected_cannotBeActedOnAgain() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(submitted.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        settlementService.settle(submitted.id(), "PAY", null);

        // double mark-paid on an already-PAID settlement
        assertThatThrownBy(() -> settlementService.settle(submitted.id(), "PAY-AGAIN", null))
                .isInstanceOf(ConflictError.class);
        // and HR can no longer approve/reject it either
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        assertThatThrownBy(() -> settlementService.approve(submitted.id(), null))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void unauthorizedHrUser_cannotApproveOrReject() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");

        // travel.write alone (no travel.settlement.approve) cannot act on the HR stage
        CurrentUserHolder.set(actor(nextUserId(), "travel.write"));
        assertThatThrownBy(() -> settlementService.approve(submitted.id(), null))
                .isInstanceOf(ForbiddenError.class);
        assertThatThrownBy(() -> settlementService.reject(submitted.id(), "no"))
                .isInstanceOf(ForbiddenError.class);
    }

    @Test
    void unauthorizedAccountsUser_cannotMarkPaid() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(submitted.id(), null);

        // HR's own permission does not carry over to the Accounts stage
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        assertThatThrownBy(() -> settlementService.settle(submitted.id(), "PAY", null))
                .isInstanceOf(ForbiddenError.class);
    }

    @Test
    void pendingInbox_movesFromHrToAccounts_thenDisappearsOncePaid() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("1000.00"));
        TravelSettlementResponse submitted = submittedAdjustmentSettlement(f, "500.00");

        CurrentUser hr = actor(nextUserId(), "travel.settlement.approve");
        CurrentUser accounts = actor(nextUserId(), "travel.settlement.paid");

        assertThat(inboxIds(hr)).contains(submitted.id());
        assertThat(inboxIds(accounts)).doesNotContain(submitted.id());

        CurrentUserHolder.set(hr);
        settlementService.approve(submitted.id(), null);

        assertThat(inboxIds(accounts)).contains(submitted.id());
        assertThat(inboxIds(hr)).doesNotContain(submitted.id());

        CurrentUserHolder.set(accounts);
        settlementService.settle(submitted.id(), "PAY", null);

        assertThat(inboxIds(hr)).doesNotContain(submitted.id());
        assertThat(inboxIds(accounts)).doesNotContain(submitted.id());
    }

    private List<Long> inboxIds(CurrentUser caller) {
        CurrentUserHolder.set(caller);
        return settlementService.pendingApprovals(null, null, org.springframework.data.domain.PageRequest.of(0, 200))
                .content().stream().map(TravelSettlementResponse::id).toList();
    }

    /** create (single ADJUSTMENT item) → submit → SUBMITTED, ready for the HR stage. */
    private TravelSettlementResponse submittedAdjustmentSettlement(Fixture f, String amount) {
        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse s = settlementService.create(f.tripId(), new CreateTravelSettlementRequest(
                f.participantId(), TravelSettlementType.FINAL, "PKR", "adjustment-only",
                List.of(new CreateTravelSettlementItemRequest(TravelSettlementItemSourceType.ADJUSTMENT, 0L,
                        new BigDecimal(amount), new BigDecimal(amount), "PKR",
                        TravelSettlementItemDirection.PAYABLE, "top-up")),
                null));
        return settlementService.submit(s.id());
    }

    // ──────────────────────────────────────────────────────── fixtures / helpers

    private record Fixture(Long tripId, Long advanceId, Long participantId) {}

    /** create → submit → transport + DD/HOD approve → APPROVED, no advance. */
    private Fixture tripApprovedNoAdvance() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        Long tripId = created.id();
        requestService.submit(tripId);
        transportApprove(tripId);
        CurrentUserHolder.set(user(managerUserId));
        requestService.approve(tripId, null);
        Long participantId = participants.findByTravelRequestIdAndEmployeeId(tripId, requester.getId())
                .orElseThrow().getId();
        return new Fixture(tripId, null, participantId);
    }

    /** approve → advance → approve (mgr+HR+accounts) → pay → start → complete. */
    private Fixture tripCompletedWithPaidAdvance(BigDecimal advanceAmount) {
        Fixture approved = tripApprovedNoAdvance();
        Long advanceId = raiseApproveAndPayAdvance(approved.tripId(), advanceAmount);
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(approved.tripId());
        requestService.completeTravel(approved.tripId());
        return new Fixture(approved.tripId(), advanceId, approved.participantId());
    }

    private Long raiseAndApproveAdvance(Long tripId, BigDecimal amount) {
        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(tripId, new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, amount, "PKR", "advance", null));
        advanceService.submit(advance.id());
        CurrentUserHolder.set(user(managerUserId));
        advanceService.approve(advance.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        advanceService.approve(advance.id(), "hr ok");
        CurrentUserHolder.set(actor(nextUserId(), "travel.advance.approve"));
        advanceService.approve(advance.id(), "fund it");
        return advance.id();
    }

    private Long raiseApproveAndPayAdvance(Long tripId, BigDecimal amount) {
        Long advanceId = raiseAndApproveAdvance(tripId, amount);
        CurrentUserHolder.set(actor(nextUserId(), "travel.advance.approve"));
        advanceService.markPaymentPending(advanceId);
        advanceService.markPaid(advanceId, amount, "PKR", "PAY-" + advanceId, null,
                TravelAdvancePaymentMethodMode.BANK_TRANSFER);
        return advanceId;
    }

    private Long approvedTaxi(Long tripId, String amount) {
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse taxi = expenseService.create(tripId,
                expenseReq(requester.getEmployeeCode(), null, "TRANSPORTATION", "TAXI", new BigDecimal(amount)));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(taxi.id(), null, null);
        CurrentUserHolder.set(requesterUser());
        return taxi.id();
    }

    private void transportApprove(Long tripId) {
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        requestService.transportVerify(tripId,
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null));
    }

    private CreateTravelRequest tripRequest() {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        return new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-1234", "Corolla", new BigDecimal("120.00"), null)));
    }

    private CreateTravelExpenseItemRequest expenseReq(String employeeCode, String entitlementTypeCode,
                                                      String category, String subcategory, BigDecimal claimed) {
        return new CreateTravelExpenseItemRequest(
                employeeCode, entitlementTypeCode, category, subcategory,
                subcategory + " during trip", LocalDateTime.now(), claimed, "PKR", null, null, null);
    }

    private long persistEmployee(String fullName, long userId, Long managerId, Long empGradeId) {
        Employee e = new Employee();
        e.setEmployeeCode("PRP-" + System.nanoTime());
        e.setFullName(fullName);
        e.setUserId(userId);
        e.setManagerId(managerId);
        e.setGradeId(empGradeId);
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employees.save(e).getId();
    }

    private static long nextUserId() {
        return USER_ID_SEQ.incrementAndGet();
    }

    private CurrentUser requesterUser() {
        return user(requester.getUserId());
    }

    private static CurrentUser user(long userId) {
        return new CurrentUser(userId, "u" + userId, Set.of(), Set.of());
    }

    private static CurrentUser actor(long userId, String... permissions) {
        return new CurrentUser(userId, "a" + userId, Set.of(), Set.of(permissions));
    }

    /**
     * Activates {@code draft}, first retiring whatever version of the same
     * policy code is currently ACTIVE. At most one ACTIVE version per policy
     * code is allowed and this module's Postgres persists across every test
     * in the class, so each seeding run must replace the previous ACTIVE
     * version rather than stack another one beside it.
     */
    private void activateReplacingCurrent(TravelPolicyVersionResponse draft) {
        policyService.listVersionsOfPolicy(draft.policyCode()).stream()
                .filter(v -> v.status() == TravelPolicyVersionStatus.ACTIVE)
                .forEach(v -> policyService.retirePolicyVersion(v.id()));
        policyService.activatePolicyVersion(draft.id());
    }

}

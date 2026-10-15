package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.request.travel.request.*;
import com.forward.hrms.employee.dto.request.travel.response.*;
import com.forward.hrms.employee.dto.response.TravelRequestLegResponse;
import com.forward.hrms.employee.dto.response.TravelRequestParticipantResponse;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.travel.TravelRequest;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.travel.*;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.travel.TravelFuelPolicyRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestParticipantRepository;
import com.forward.hrms.employee.repository.travel.TravelRequestRepository;
import com.forward.hrms.employee.service.travel.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end walk of the whole travel module against a real Postgres
 * (Testcontainers): HR sets up a versioned policy + grade limit, a
 * manager/HOD raises a request that runs the {@code TRAVEL_REQUEST} chain
 * (TRANSPORT → DD/Requester's Head, final — no HR stage), an advance runs
 * {@code TRAVEL_ADVANCE} (MANAGER → HR → ACCOUNTS) then is paid, expenses are claimed and reviewed,
 * and a settlement reconciles {@code Σ approved expenses − Σ advances}
 * into a payable/receivable balance, drives the linked advance to SETTLED
 * and flips the trip {@code SETTLEMENT_PENDING → SETTLED}.
 *
 * <p>Also covers entitlement capping, the approval-chain view
 * ({@code currentStage}/{@code steps}/{@code actions}), the
 * {@code pendingApprovals} inbox, and the DRAFT-trip advance guard.
 */
@SpringBootTest
@WithTestcontainers
class TravelModuleFlowIntegrationTest {

    // Testcontainers reuse persists the DB across classes; employees.user_id
    // is unique — never hardcode a userId literal.
    private static final AtomicLong USER_ID_SEQ = new AtomicLong(System.nanoTime() & 0x7fffffffL);

    @Autowired
    TravelPolicyService policyService;
    @Autowired
    TravelFuelPolicyService fuelPolicyService;
    @Autowired
    TravelRequestService requestService;
    @Autowired
    TravelAdvanceService advanceService;
    @Autowired
    TravelExpenseService expenseService;
    @Autowired
    TravelSettlementService settlementService;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    GradeRepository grades;
    @Autowired
    TravelRequestParticipantRepository participants;
    @Autowired
    TravelRequestRepository travelRequestRepo;
    @Autowired
    TravelFuelPolicyRepository fuelPolicyRepo;

    private String gradeCode;
    private Employee requester;
    private long managerUserId;

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @BeforeEach
    void setUp() {
        Grade g = new Grade();
        gradeCode = "TRV-G-" + System.nanoTime();
        g.setCode(gradeCode);
        g.setName("Travel Test Grade");
        g.setLevel(1);
        Long gradeId = grades.save(g).getId();

        managerUserId = nextUserId();
        long managerEmployeeId = persistEmployee("Trip Manager " + managerUserId, managerUserId, null, null);
        long requesterUserId = nextUserId();
        persistEmployee("Trip Requester " + requesterUserId, requesterUserId, managerEmployeeId, gradeId);
        requester = employees.findByUserId(requesterUserId).orElseThrow();

        // Only a Manager/HOD may raise a travel request — give the requester a direct report.
        long reportUserId = nextUserId();
        persistEmployee("Trip Report " + reportUserId, reportUserId, requester.getId(), gradeId);

        // ACTIVE LOCAL_FACTORY_TRAVEL policy version + DINNER / PER_DAY / 1500 limit for the requester's grade.
        CurrentUserHolder.set(actor(nextUserId(), "travel.admin"));
        TravelPolicyVersionResponse version = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                "LOCAL_FACTORY_TRAVEL", "Factory Travel Policy", LocalDate.now().minusMonths(1), null, "test"));
        policyService.addGradeLimit(version.id(), new CreateTravelPolicyGradeLimitRequest(
                gradeCode, "DINNER", TravelLimitType.PER_DAY, new BigDecimal("1500.00"), "PKR", true, null));
        activateReplacingCurrent(version);

        // INTERNATIONAL maps to its own policy family; seeded so a request can switch
        // travel type without losing its applicable policy.
        TravelPolicyVersionResponse international = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                "INTERNATIONAL_FACTORY_TRAVEL", "International Travel Policy", LocalDate.now().minusMonths(1), null, "test"));
        policyService.addGradeLimit(international.id(), new CreateTravelPolicyGradeLimitRequest(
                gradeCode, "DINNER", TravelLimitType.PER_DAY, new BigDecimal("4500.00"), "PKR", true, null));
        activateReplacingCurrent(international);
        CurrentUserHolder.clear();

        // code IS the vehicle-source code (COMPANY/EMPLOYEE/RENTAL/PUBLIC/OTHER) —
        // ensure the ones this suite's legs use are active. Idempotent: the
        // Testcontainers Postgres persists across every test (and class) in
        // this module, and only one ACTIVE row per code is allowed.
        ensureFuelPolicy("COMPANY", "10.00");
        ensureFuelPolicy("EMPLOYEE", "10.00");
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

    @Test
    void fullLifecycle_companyPayable_whenExpensesExceedTheAdvance() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("5000.00"));

        // Two expenses: one uncapped (8000), one DINNER claim (10000) capped to the 3000 entitlement.
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse transport = expenseService.create(f.tripId,
                expenseReq(null, "TRANSPORTATION", "TAXI", new BigDecimal("8000.00")));
        TravelExpenseItemResponse dinner = expenseService.create(f.tripId,
                expenseReq("DINNER", "MEAL", "DINNER", new BigDecimal("10000.00")));
        assertThat(dinner.eligibleAmount()).isEqualByComparingTo("3000.00"); // entitlement cap applied

        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        assertThat(expenseService.approve(transport.id(), null, null).approvedAmount())
                .isEqualByComparingTo("8000.00");
        assertThat(expenseService.approve(dinner.id(), null, null).approvedAmount())
                .isEqualByComparingTo("3000.00");

        // Settlement: 11000 approved expenses − 5000 advance = 6000 payable to the employee.
        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse settlement = settlementService.create(f.tripId, new CreateTravelSettlementRequest(
                f.participantId, TravelSettlementType.FINAL, "PKR", "final",
                List.of(
                        item(TravelSettlementItemSourceType.EXPENSE, transport.id(),
                                "8000.00", "8000.00", TravelSettlementItemDirection.PAYABLE),
                        item(TravelSettlementItemSourceType.EXPENSE, dinner.id(),
                                "10000.00", "3000.00", TravelSettlementItemDirection.PAYABLE),
                        item(TravelSettlementItemSourceType.ADVANCE, f.advanceId,
                                "5000.00", "5000.00", TravelSettlementItemDirection.RECEIVABLE)),
                null));

        assertThat(settlement.items()).hasSize(3);
        assertThat(settlement.totalApprovedExpenses()).isEqualByComparingTo("11000.00");
        assertThat(settlement.totalAdvancesPaid()).isEqualByComparingTo("5000.00");
        assertThat(settlement.finalSettlementAmount()).isEqualByComparingTo("6000.00");
        assertThat(settlement.settlementDirection()).isEqualTo(TravelSettlementDirection.COMPANY_PAYABLE);
        assertThat(requestService.get(f.tripId).status()).isEqualTo("SETTLEMENT_PENDING");

        settlementService.submit(settlement.id());
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(settlement.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        TravelSettlementResponse settled = settlementService.settle(settlement.id(), "PAYOUT-1", null);
        assertThat(settled.status()).isEqualTo(TravelSettlementStatus.PAID);
        assertThat(settled.finalized()).isTrue();

        // Advance fully consumed by the expenses — nothing returned.
        TravelAdvanceResponse advSettled = advanceService.get(f.advanceId);
        assertThat(advSettled.status()).isEqualTo(TravelAdvanceStatus.SETTLED);
        assertThat(advSettled.settledAmount()).isEqualByComparingTo("5000.00");
        assertThat(advSettled.returnedAmount()).isEqualByComparingTo("0.00");

        assertThat(requestService.get(f.tripId).status()).isEqualTo("SETTLED");
    }

    @Test
    void fullLifecycle_employeeReceivable_whenTheAdvanceExceedsApprovedExpenses() {
        Fixture f = tripCompletedWithPaidAdvance(new BigDecimal("5000.00"));

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse taxi = expenseService.create(f.tripId,
                expenseReq(null, "TRANSPORTATION", "TAXI", new BigDecimal("2500.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(taxi.id(), null, null);

        CurrentUserHolder.set(requesterUser());
        TravelSettlementResponse settlement = settlementService.create(f.tripId, new CreateTravelSettlementRequest(
                f.participantId, TravelSettlementType.FINAL, "PKR", "final",
                List.of(
                        item(TravelSettlementItemSourceType.EXPENSE, taxi.id(),
                                "2500.00", "2500.00", TravelSettlementItemDirection.PAYABLE),
                        item(TravelSettlementItemSourceType.ADVANCE, f.advanceId,
                                "5000.00", "5000.00", TravelSettlementItemDirection.RECEIVABLE)),
                null));

        assertThat(settlement.finalSettlementAmount()).isEqualByComparingTo("2500.00");
        assertThat(settlement.settlementDirection()).isEqualTo(TravelSettlementDirection.EMPLOYEE_RECEIVABLE);

        settlementService.submit(settlement.id());
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(settlement.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        settlementService.settle(settlement.id(), "RECOVER-1", null);

        // 2500 of the 5000 advance was consumed; the employee returns the other 2500.
        TravelAdvanceResponse advSettled = advanceService.get(f.advanceId);
        assertThat(advSettled.status()).isEqualTo(TravelAdvanceStatus.SETTLED);
        assertThat(advSettled.settledAmount()).isEqualByComparingTo("2500.00");
        assertThat(advSettled.returnedAmount()).isEqualByComparingTo("2500.00");
        assertThat(requestService.get(f.tripId).status()).isEqualTo("SETTLED");
    }

    // ── expense creation: zero-eligible-amount rejection (entitlement exhaustion) ────

    @Test
    void expenseCreation_rejectsSecondExpense_whenEntitlementIsFullyConsumed() {
        Long tripId = tripWithAccommodationCap(new BigDecimal("12000.00"));

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse first = expenseService.create(tripId,
                accommodationExpenseReq(new BigDecimal("12000.00")));
        assertThat(first.eligibleAmount()).isEqualByComparingTo("12000.00");

        int countBefore = expenseService.listForRequest(tripId).size();

        assertThatThrownBy(() -> expenseService.create(tripId, accommodationExpenseReq(new BigDecimal("3000.00"))))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> {
                    UnprocessableError err = (UnprocessableError) ex;
                    assertThat(err.code()).isEqualTo(TravelErrorCode.TRAVEL_ENTITLEMENT_EXHAUSTED);
                    assertThat(err.status()).isEqualTo(422);
                });

        // no second row was persisted
        assertThat(expenseService.listForRequest(tripId)).hasSize(countBefore);
    }

    @Test
    void expenseCreation_allowsPartialEligibility_whenClaimExceedsRemainingButNotFullyConsumed() {
        Long tripId = tripWithAccommodationCap(new BigDecimal("12000.00"));

        CurrentUserHolder.set(requesterUser());
        expenseService.create(tripId, accommodationExpenseReq(new BigDecimal("7000.00")));

        TravelExpenseItemResponse second = expenseService.create(tripId,
                accommodationExpenseReq(new BigDecimal("6000.00")));

        assertThat(second.eligibleAmount()).isEqualByComparingTo("5000.00");
        assertThat(second.nonReimbursableAmount()).isEqualByComparingTo("1000.00");
        assertThat(second.status()).isEqualTo(TravelExpenseStatus.SUBMITTED);
    }

    @Test
    void expenseCreation_allowsExactRemainingEntitlement() {
        Long tripId = tripWithAccommodationCap(new BigDecimal("12000.00"));

        CurrentUserHolder.set(requesterUser());
        expenseService.create(tripId, accommodationExpenseReq(new BigDecimal("9000.00")));

        TravelExpenseItemResponse second = expenseService.create(tripId,
                accommodationExpenseReq(new BigDecimal("3000.00")));

        assertThat(second.eligibleAmount()).isEqualByComparingTo("3000.00");
        assertThat(second.nonReimbursableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void expenseUpdate_selfExclusion_stillAppliesUnderTheZeroEligibleGuard() {
        Long tripId = tripWithAccommodationCap(new BigDecimal("12000.00"));

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse first = expenseService.create(tripId,
                accommodationExpenseReq(new BigDecimal("12000.00")));
        assertThat(first.eligibleAmount()).isEqualByComparingTo("12000.00");

        // Re-saving the SAME expense at the same claimed amount must not double-count
        // itself as already-consumed (existing self-exclusion, still applied first).
        TravelExpenseItemResponse resaved = expenseService.update(first.id(),
                accommodationExpenseReq(new BigDecimal("12000.00")));
        assertThat(resaved.eligibleAmount()).isEqualByComparingTo("12000.00");

        // A distinct, second expense against the now-fully-consumed entitlement is
        // still rejected (create-side guard unaffected by the update above).
        assertThatThrownBy(() -> expenseService.create(tripId, accommodationExpenseReq(new BigDecimal("1000.00"))))
                .isInstanceOf(UnprocessableError.class);

        // Growing the first expense's OWN claim beyond the entitlement (still excluding
        // itself) is partially eligible, not rejected outright — Rule C via update.
        TravelExpenseItemResponse grown = expenseService.update(first.id(),
                accommodationExpenseReq(new BigDecimal("15000.00")));
        assertThat(grown.eligibleAmount()).isEqualByComparingTo("12000.00");
        assertThat(grown.nonReimbursableAmount()).isEqualByComparingTo("3000.00");
    }

    @Test
    void expenseUpdate_isRejected_whenRetargetedAtAnAlreadyExhaustedEntitlement() {
        Long tripId = tripWithAccommodationCap(new BigDecimal("12000.00"));

        CurrentUserHolder.set(requesterUser());
        expenseService.create(tripId, accommodationExpenseReq(new BigDecimal("12000.00")));

        // Unrelated/uncapped expense — created successfully with no entitlement tie.
        TravelExpenseItemResponse taxi = expenseService.create(tripId,
                expenseReq(null, "TRANSPORTATION", "TAXI", new BigDecimal("500.00")));
        assertThat(taxi.travelRequestEntitlementId()).isNull();

        // Retargeting it at the now-fully-consumed ACCOMMODATION entitlement is rejected,
        // and the original row is left untouched.
        assertThatThrownBy(() -> expenseService.update(taxi.id(), accommodationExpenseReq(new BigDecimal("500.00"))))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_ENTITLEMENT_EXHAUSTED));

        TravelExpenseItemResponse unchanged = expenseService.get(taxi.id());
        assertThat(unchanged.travelRequestEntitlementId()).isNull();
        assertThat(unchanged.claimedAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void submit_opensTwoStageChain_andShowsInTheTransportPendingInbox() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.estimatedAmount()).isEqualByComparingTo("3000.00"); // 1500 PER_DAY × 2 travel days

        TravelRequestResponse submitted = requestService.submit(created.id());
        assertThat(submitted.status()).isEqualTo("TRANSPORT_PENDING");
        assertThat(submitted.steps()).hasSize(2);
        assertThat(submitted.steps()).extracting(s -> s.stageRole())
                .containsExactly("Transport Verification", "DD/HOD Approval");
        assertThat(submitted.steps().get(0).status()).isEqualTo(StepStatus.PENDING.name());
        assertThat(submitted.steps().get(1).status()).isEqualTo(StepStatus.NOT_YET.name());
        assertThat(submitted.currentStage().stageNo()).isEqualTo(1);
        assertThat(submitted.actions()).isEmpty(); // requester holds no travel.transport.verify

        // The manager cannot act yet — the Transport stage is first.
        CurrentUserHolder.set(user(managerUserId));
        assertThat(requestService.pendingApprovals(null, null, null, null, PageRequest.of(0, 20)).content())
                .noneMatch(r -> r.id().equals(created.id()));

        // A transport-pool user sees the actionable row.
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        PageResponse<TravelRequestResponse> inbox =
                requestService.pendingApprovals(null, null, null, null, PageRequest.of(0, 20));
        TravelRequestResponse row = inbox.content().stream()
                .filter(r -> r.id().equals(created.id()))
                .findFirst().orElseThrow();
        assertThat(row.currentStage().stageNo()).isEqualTo(1);
        assertThat(row.currentStage().stageRole()).isEqualTo("Transport Verification");
        assertThat(row.actions()).contains("APPROVE", "REJECT");

        // Inbox filters: q on id / request number / requester code / name, exact type.
        // The DB keeps earlier runs' rows, so assert the hit, not exclusivity.
        for (String q : List.of(String.valueOf(created.id()), created.requestNo().toLowerCase(),
                submitted.requesterEmployeeCode(), submitted.requesterEmployeeName().toUpperCase())) {
            assertThat(requestService.pendingApprovals(null, null, q, null, PageRequest.of(0, 200)).content())
                    .as("q=%s", q).anyMatch(r -> r.id().equals(created.id()));
        }
        assertThat(requestService.pendingApprovals(null, null, null, created.travelTypeCode().toLowerCase(),
                PageRequest.of(0, 200)).content()).anyMatch(r -> r.id().equals(created.id()));
        assertThat(requestService.pendingApprovals(null, null, null, "NO_SUCH_TYPE", PageRequest.of(0, 200))
                .content()).isEmpty();
        assertThat(requestService.pendingApprovals(null, null, "no-such-trip", null, PageRequest.of(0, 200))
                .content()).isEmpty();
    }

    @Test
    void advance_cannotBeRaised_whileTheTripIsStillDraft() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse draft = requestService.create(tripRequest());

        assertThatThrownBy(() -> advanceService.create(draft.id(), new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("1000.00"),
                "PKR", "too early", null)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void ddRejection_isTerminal_andNoFurtherStageIsCreated() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        transportApprove(created.id(), null);

        CurrentUserHolder.set(user(managerUserId));
        TravelRequestResponse rejected = requestService.reject(created.id(), "not this quarter");
        assertThat(rejected.status()).isEqualTo("DD_REJECTED");
        assertThat(rejected.currentStage()).isNull();
        assertThat(rejected.steps()).extracting(s -> s.status())
                .containsExactly(StepStatus.APPROVED.name(), StepStatus.REJECTED.name());

        // DD/Head is the final stage — a rejected request cannot be pushed any further
        CurrentUserHolder.set(user(managerUserId));
        assertThatThrownBy(() -> requestService.approve(created.id(), "too late"))
                .isInstanceOf(ConflictError.class);
    }

    // ── company rule: only a Manager/HOD may create ──────────────────────────

    @Test
    void nonManager_cannotCreateTravelRequest() {
        long leafUserId = nextUserId();
        persistEmployee("Leaf Employee " + leafUserId, leafUserId, requester.getId(), null);
        CurrentUserHolder.set(user(leafUserId));

        assertThatThrownBy(() -> requestService.create(tripRequestFor(leafUserId)))
                .isInstanceOf(ForbiddenError.class);
    }

    @Test
    void hod_canCreate_evenWithoutDirectReports() {
        // A department head with zero direct reports is still a Manager/HOD.
        long hodUserId = nextUserId();
        long hodEmpId = persistEmployee("Dept Head " + hodUserId, hodUserId, null, null);
        Department d = new Department();
        d.setCode("TRV-DEPT-" + System.nanoTime());
        d.setName("Travel Test Dept");
        d.setHeadId(hodEmpId);
        departments.save(d);

        CurrentUserHolder.set(user(hodUserId));
        TravelRequestResponse created = requestService.create(tripRequestFor(hodUserId));
        assertThat(created.status()).isEqualTo("DRAFT");
    }

    // ── DRAFT edit: creator only ─────────────────────────────────────────────

    @Test
    void creatorCanEditOwnDraft_butOthersCannot_andNotAfterSubmit() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());

        // creator edits their own DRAFT
        CreateTravelRequest edit = new CreateTravelRequest(
                "INTERNATIONAL", "PKR", "Corrected purpose",
                created.startDatetime(), created.endDatetime(),
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "ISLAMABAD",
                        created.startDatetime(), created.startDatetime().plusHours(5),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-9999", "Hiace",
                        new BigDecimal("250.00"), null)));
        TravelRequestResponse updated = requestService.update(created.id(), edit);
        assertThat(updated.purpose()).isEqualTo("Corrected purpose");
        assertThat(updated.travelTypeCode()).isEqualTo("INTERNATIONAL");
        assertThat(updated.legs()).hasSize(1);
        assertThat(updated.legs().get(0).destinationLocationCode()).isEqualTo("ISLAMABAD");

        // a different employee cannot edit it
        CurrentUserHolder.set(user(managerUserId));
        assertThatThrownBy(() -> requestService.update(created.id(), edit))
                .isInstanceOf(ForbiddenError.class);

        // once submitted, DRAFT-edit is closed
        CurrentUserHolder.set(requesterUser());
        requestService.submit(created.id());
        assertThatThrownBy(() -> requestService.update(created.id(), edit))
                .isInstanceOf(ConflictError.class);
    }

    // ── response mapping: root-level requester fields ───────────────────────

    @Test
    void requesterFields_arePopulated_fromTheRequesterParticipantSnapshot() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());

        assertThat(created.requesterEmployeeCode()).isEqualTo(requester.getEmployeeCode());
        assertThat(created.requesterEmployeeName()).isEqualTo(requester.getFullName());

        TravelRequestParticipantResponse requesterPart = created.participants().stream()
                .filter(p -> "REQUESTER".equals(p.participantRole()))
                .findFirst().orElseThrow();
        assertThat(requesterPart.employeeCode()).isEqualTo(created.requesterEmployeeCode());
        assertThat(requesterPart.employeeName()).isEqualTo(created.requesterEmployeeName());

        // survives submit
        TravelRequestResponse submitted = requestService.submit(created.id());
        assertThat(submitted.requesterEmployeeCode()).isEqualTo(requester.getEmployeeCode());
        assertThat(submitted.requesterEmployeeName()).isEqualTo(requester.getFullName());
    }

    // ── creator cannot approve their own submitted request ───────────────────

    @Test
    void creatorCannotApproveOwnSubmittedRequest() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        // still the creator in the CurrentUserHolder
        assertThatThrownBy(() -> requestService.approve(created.id(), "self approve"))
                .isInstanceOf(ForbiddenError.class);
        assertThat(requestService.get(created.id()).status()).isEqualTo("TRANSPORT_PENDING");
    }

    // ── TRANSPORT → DD/Head chain: staged progression + rejections ───────────

    @Test
    void travelRequest_transportThenDd_withDdFinal_andDdCanReject() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        TravelRequestResponse submitted = requestService.submit(created.id());

        // exactly TRANSPORT → DD/HOD, no HR stage
        assertThat(submitted.steps()).extracting(s -> s.stageRole())
                .containsExactly("Transport Verification", "DD/HOD Approval");
        assertThat(submitted.steps().get(0).status()).isEqualTo(StepStatus.PENDING.name());
        assertThat(submitted.steps().get(1).status()).isEqualTo(StepStatus.NOT_YET.name());
        assertThat(submitted.status()).isEqualTo("TRANSPORT_PENDING");

        // transport approves → DD_PENDING, DD/HOD stage PENDING
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterTransport = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, "vehicle ok"));
        assertThat(afterTransport.status()).isEqualTo("DD_PENDING");
        assertThat(afterTransport.currentStage().stageNo()).isEqualTo(2);
        assertThat(afterTransport.currentStage().stageRole()).isEqualTo("DD/HOD Approval");

        // DD/HOD (the requester's manager) rejects → DD_REJECTED (terminal, no HR stage)
        CurrentUserHolder.set(user(managerUserId));
        TravelRequestResponse rejected = requestService.reject(created.id(), "budget frozen");
        assertThat(rejected.status()).isEqualTo("DD_REJECTED");
        assertThat(rejected.currentStage()).isNull();
    }

    /**
     * Backend proof for the frontend-facing Approval Inbox contract:
     * {@code GET /travel/requests/pending-approvals} (behind the BFF's
     * {@code GET /bff/approvals/inbox} → {@code CombinedApprovalsInbox.travelRequests}
     * / {@code .counts().travelRequests()}, same fan-out as Leave/Advances/Overtime —
     * see {@code ApprovalsInboxCompositeService}) is a live, moving inbox: the
     * request enters exactly one role pool's pending count at a time and leaves
     * it the instant that stage is decided, never lingering in two pools at
     * once and never orphaned once DD/Head gives final approval. There is no
     * HR pool in this chain.
     *
     * <p>Counts are asserted as deltas off a per-actor baseline (not absolute
     * totals) because the Testcontainers Postgres is reused class-wide and
     * earlier tests may leave other PENDING travel requests sitting in the
     * same role pools.
     */
    @Test
    void travelRequest_movesThroughApprovalInboxSections_withCorrectCounts_endToEnd() {
        // Fixed actor identities reused for every before/after count comparison
        // in this test — a role-pool (PERMISSION-resolver) actor sees every
        // pending request for that permission class-wide, so the baseline must
        // come from the SAME identity as the later reading.
        CurrentUser transportActor = actor(nextUserId(), "travel.transport.verify");
        CurrentUser ddActor = user(managerUserId); // MANAGER-resolver: the requester's own manager

        long transportBaseline = countFor(transportActor);
        long ddBaseline = countFor(ddActor);

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        TravelRequestResponse submitted = requestService.submit(created.id());
        assertThat(submitted.status()).isEqualTo("TRANSPORT_PENDING");
        long tripId = created.id();
        assertThat(dbStatus(tripId)).isEqualTo(TravelRequestStatus.TRANSPORT_PENDING); // straight off the entity row

        // 1) Submitted → Transport section only: count +1, DD untouched.
        TravelRequestResponse transportRow = requirePending(transportActor, tripId);
        assertThat(transportRow.currentStage().stageRole()).isEqualTo("Transport Verification");
        assertThat(transportRow.status()).isEqualTo("TRANSPORT_PENDING");
        assertThat(transportRow.actions()).contains("APPROVE", "REJECT");
        assertThat(countFor(transportActor)).isEqualTo(transportBaseline + 1);
        assertThat(countFor(ddActor)).isEqualTo(ddBaseline);
        assertThatNotPending(ddActor, tripId);

        // 2) Transport approves → leaves Transport, appears in DD/HOD: count
        // moves from Transport to DD.
        CurrentUserHolder.set(transportActor);
        requestService.transportVerify(tripId,
                new TransportVerifyRequest(TransportDecision.APPROVE, null, "vehicle ok"));
        assertThat(countFor(transportActor)).isEqualTo(transportBaseline);
        assertThatNotPending(transportActor, tripId);
        TravelRequestResponse ddRow = requirePending(ddActor, tripId);
        assertThat(ddRow.currentStage().stageRole()).isEqualTo("DD/HOD Approval");
        assertThat(ddRow.status()).isEqualTo("DD_PENDING");
        assertThat(ddRow.actions()).contains("APPROVE", "REJECT");
        assertThat(dbStatus(tripId)).isEqualTo(TravelRequestStatus.DD_PENDING);
        assertThat(countFor(ddActor)).isEqualTo(ddBaseline + 1);

        // 3) DD/HOD approves (final stage) → APPROVED, no pending item
        // anywhere — both pools back to their pre-test baselines.
        CurrentUserHolder.set(ddActor);
        TravelRequestResponse finalRow = requestService.approve(tripId, "dd ok");
        assertThat(finalRow.status()).isEqualTo("APPROVED");
        assertThat(finalRow.currentStage()).isNull();
        assertThat(dbStatus(tripId)).isEqualTo(TravelRequestStatus.APPROVED);
        assertThat(countFor(ddActor)).isEqualTo(ddBaseline);
        assertThatNotPending(ddActor, tripId);
        assertThat(countFor(transportActor)).isEqualTo(transportBaseline);
        assertThatNotPending(transportActor, tripId);
    }

    private long countFor(CurrentUser actor) {
        CurrentUserHolder.set(actor);
        return requestService.pendingApprovals(null, null, null, null, PageRequest.of(0, 200)).totalElements();
    }

    private TravelRequestResponse requirePending(CurrentUser actor, long tripId) {
        CurrentUserHolder.set(actor);
        return requestService.pendingApprovals(null, null, null, null, PageRequest.of(0, 200)).content().stream()
                .filter(r -> r.id().equals(tripId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected travel request " + tripId + " to be pending for " + actor.username()));
    }

    private void assertThatNotPending(CurrentUser actor, long tripId) {
        CurrentUserHolder.set(actor);
        assertThat(requestService.pendingApprovals(null, null, null, null, PageRequest.of(0, 200)).content())
                .noneMatch(r -> r.id().equals(tripId));
    }

    /**
     * Reads the persisted status directly off the entity, bypassing the DTO/service read path.
     */
    private TravelRequestStatus dbStatus(long tripId) {
        return travelRequestRepo.findById(tripId).orElseThrow().getStatus();
    }

    @Test
    void ddStage_rejectsAWriterWhoIsNotTheAssignee_thenAcceptsTheResolvedManager() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        transportApprove(created.id(), null); // now DD_PENDING (final stage)

        // travel.write but not the assigned DD/Head → 403 FORBIDDEN
        CurrentUserHolder.set(actor(nextUserId(), "travel.write"));
        assertThatThrownBy(() -> requestService.approve(created.id(), "no rights"))
                .isInstanceOf(ForbiddenError.class);
        assertThat(requestService.get(created.id()).status()).isEqualTo("DD_PENDING");

        // the resolved manager (DD/Head) approves → APPROVED directly, final stage
        CurrentUserHolder.set(user(managerUserId));
        assertThat(requestService.approve(created.id(), "dd ok").status()).isEqualTo("APPROVED");
    }

    // ── TRANSPORT stage: rejection, distance verification, authorization ─────

    @Test
    void transportRejection_isTerminal_noDdStageCreated() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse rejected = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.REJECT, null, "no vehicle available"));

        assertThat(rejected.status()).isEqualTo("TRANSPORT_REJECTED");
        assertThat(rejected.currentStage()).isNull();
        assertThat(rejected.steps()).extracting(s -> s.status())
                .containsExactly(StepStatus.REJECTED.name(), StepStatus.SKIPPED.name());

        // no later stage can be actioned
        CurrentUserHolder.set(user(managerUserId));
        assertThatThrownBy(() -> requestService.approve(created.id(), "dd ok"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void transportVerify_setsActualDistance_withoutTouchingPlannedDistance() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest()); // one leg, planned 120
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("145.00"), null, null, null)),
                        null));

        assertThat(afterVerify.status()).isEqualTo("DD_PENDING");
        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.plannedDistanceKm()).isEqualByComparingTo("120.00");   // preserved
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("145.00");    // Transport value
        assertThat(leg.effectiveDistanceKm()).isEqualByComparingTo("145.00"); // effective = actual
    }

    @Test
    void transportVerify_actualDistanceZero_fallsBackToPlanned_andIsPersistedAsTheFinalActual() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, BigDecimal.ZERO, null, null, null)),
                        null));

        // 0 is "not verified": the finalized/persisted actualDistanceKm falls
        // back to plannedDistanceKm — never left null — and plannedDistanceKm
        // itself is untouched.
        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.plannedDistanceKm()).isEqualByComparingTo("120.00");
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("120.00");
        assertThat(leg.effectiveDistanceKm()).isEqualByComparingTo("120.00");
    }

    @Test
    void transportVerify_actualDistanceOmitted_fallsBackToPlanned_andIsPersistedAsTheFinalActual() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        // No legs list at all — the common "approve with no per-leg overrides"
        // path still finalizes actualDistanceKm to the planned fallback.
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null));

        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.plannedDistanceKm()).isEqualByComparingTo("120.00");
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("120.00");
        assertThat(afterVerify.totalDistanceKm()).isEqualByComparingTo("120.00");
    }

    @Test
    void transportVerify_positiveActualDistance_overridesPlanned_plannedNeverOverwritten() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest()); // planned 120
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("480.00"), null, null, null)),
                        null));

        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.plannedDistanceKm()).isEqualByComparingTo("120.00"); // requester's estimate, untouched
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("480.00");  // transport-approved final distance
        assertThat(afterVerify.totalDistanceKm()).isEqualByComparingTo("480.00");
    }

    @Test
    void transportUser_cannotActOnTheDdStage() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        transportApprove(created.id(), null); // now DD_PENDING

        // a transport-pool user is not the requester's manager → cannot approve the DD stage
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        assertThatThrownBy(() -> requestService.approve(created.id(), "not my stage"))
                .isInstanceOf(ForbiddenError.class);
        // …and the transport verify path is closed once the stage has moved on
        assertThatThrownBy(() -> requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void unrelatedPermissionHolder_cannotPerformTransportVerification() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        // holds travel.approve (expense review) but not travel.transport.verify → cannot approve stage 1
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        assertThatThrownBy(() -> requestService.approve(created.id(), "wrong stage"))
                .isInstanceOf(ForbiddenError.class);
        assertThat(requestService.get(created.id()).status()).isEqualTo("TRANSPORT_PENDING");
    }

    @Test
    void ddHodUser_cannotPerformTransportVerification() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        // the requester's own manager (DD/HOD) has no travel.transport.verify and is not
        // assigned to stage 1 → cannot verify transport, either via transportVerify(...) or
        // the generic approve() path.
        CurrentUserHolder.set(user(managerUserId));
        assertThatThrownBy(() -> requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null)))
                .isInstanceOf(ForbiddenError.class);
        assertThatThrownBy(() -> requestService.approve(created.id(), "not my stage"))
                .isInstanceOf(ForbiddenError.class);
        assertThat(requestService.get(created.id()).status()).isEqualTo("TRANSPORT_PENDING");
    }

    @Test
    void ddStage_onlyTheResolvedManagerCanApprove_unrelatedEmployeeAndTransportCannot() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        transportApprove(created.id(), null); // now DD_PENDING, assigned to managerUserId

        // an unrelated employee (no roles, no permissions, not the resolved manager, no delegation)
        CurrentUserHolder.set(user(nextUserId()));
        assertThatThrownBy(() -> requestService.approve(created.id(), "not related"))
                .isInstanceOf(ForbiddenError.class);

        // a transport-pool user is not the assignee of this MANAGER-resolver stage either
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        assertThatThrownBy(() -> requestService.approve(created.id(), "wrong stage"))
                .isInstanceOf(ForbiddenError.class);

        assertThat(requestService.get(created.id()).status()).isEqualTo("DD_PENDING");

        // only the resolver's own manager succeeds — DD/Head is the final stage
        CurrentUserHolder.set(user(managerUserId));
        assertThat(requestService.approve(created.id(), "dd ok").status()).isEqualTo("APPROVED");
    }

    @Test
    void ddStage_doubleApproval_andPostApprovedActions_areRejectedAsStale() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        transportApprove(created.id(), null); // DD_PENDING

        CurrentUserHolder.set(user(managerUserId));
        assertThat(requestService.approve(created.id(), "dd ok").status()).isEqualTo("APPROVED");
        assertThat(dbStatus(created.id())).isEqualTo(TravelRequestStatus.APPROVED);

        // the same DD/HOD manager trying again — a "double approval" once the chain has
        // already closed — is rejected as a stale action, NOT silently accepted a second time.
        CurrentUserHolder.set(user(managerUserId));
        assertThatThrownBy(() -> requestService.approve(created.id(), "again"))
                .isInstanceOf(ConflictError.class);
        assertThatThrownBy(() -> requestService.reject(created.id(), "too late"))
                .isInstanceOf(ConflictError.class);
        assertThat(dbStatus(created.id())).isEqualTo(TravelRequestStatus.APPROVED); // unchanged
    }

    @Test
    void perKmEntitlement_usesEffectiveDistance_whenTransportOverridesPlanned() {
        // a policy version whose only limit is PER_KM for the requester's grade
        CurrentUserHolder.set(actor(nextUserId(), "travel.admin"));
        TravelPolicyVersionResponse v = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                "LOCAL_FACTORY_TRAVEL", "Per-KM Policy", LocalDate.now().minusMonths(1), null, "perkm"));
        policyService.addGradeLimit(v.id(), new CreateTravelPolicyGradeLimitRequest(
                gradeCode, "MILEAGE", TravelLimitType.PER_KM, new BigDecimal("20.00"), "PKR", true, null));
        activateReplacingCurrent(v);
        CurrentUserHolder.clear();

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest()); // planned 120 km
        assertThat(created.estimatedAmount()).isEqualByComparingTo("2400.00"); // 20 × 120 planned
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("150.00"), null, null, null)),
                        null));
        assertThat(afterVerify.estimatedAmount()).isEqualByComparingTo("3000.00"); // 20 × 150 effective
    }

    @Test
    void concurrentTransportApproval_onlyTheFirstTransitions() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null));

        // a second transport user acting "moments later" finds the stage gone
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        assertThatThrownBy(() -> requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null)))
                .isInstanceOf(ConflictError.class);
    }

    // ── Fuel/petrol issuance: calculated at Transport verification ───────────

    @Test
    void transportVerify_calculatesPetrolIssued_fromActualDistance_notPlannedDistance() {
        Long policyId = fuelPolicyId("COMPANY");

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest()); // one leg, planned 120, COMPANY
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("450.00"), null, null, null)),
                        null));

        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("450.00");
        assertThat(leg.plannedDistanceKm()).isEqualByComparingTo("120.00"); // untouched
        assertThat(leg.travelFuelPolicyId()).isEqualTo(policyId);
        assertThat(leg.petrolIssued()).isEqualByComparingTo("45.00"); // 450 / 10, NOT 120 / 10
        assertThat(afterVerify.travelFuelPolicyId()).isEqualTo(policyId);
        assertThat(afterVerify.petrolIssued()).isEqualByComparingTo("45.00");
        assertThat(afterVerify.totalDistanceKm()).isEqualByComparingTo("450.00"); // Σ legs, NOT planned 120
    }

    @Test
    void transportVerify_zeroFuelAverage_yieldsZeroPetrolIssued_withoutError() {
        ensureFuelPolicy("OTHER", "0.00"); // a deliberate "no petrol issued" vehicle source
        Long policyId = fuelPolicyId("OTHER");

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        Long legId = created.legs().getFirst().id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("450.00"), "OTHER", null, null)),
                        null));

        TravelRequestLegResponse leg = afterVerify.legs().getFirst();
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("450.00");
        assertThat(leg.travelFuelPolicyId()).isEqualTo(policyId);
        assertThat(leg.petrolIssued()).isEqualByComparingTo("0.00"); // fuelAverage == 0, no exception
        assertThat(afterVerify.petrolIssued()).isEqualByComparingTo("0.00");
    }

    @Test
    void transportVerify_multipleLegs_sumsDistanceAndPetrolAcrossLegs() {
        Long policyId = fuelPolicyId("EMPLOYEE");

        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        CreateTravelRequest twoLegTrip = new CreateTravelRequest(
                "LOCAL", "PKR", "Multi-city audit", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(
                        new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                                "COMPANY_VEHICLE", "EMPLOYEE", "LEB-1234", "Corolla",
                                new BigDecimal("500.00"), null),
                        new CreateTravelLegRequest(2, "LAHORE", "FAISALABAD", start.plusHours(5), start.plusHours(8),
                                "COMPANY_VEHICLE", "EMPLOYEE", "LEB-1234", "Corolla",
                                new BigDecimal("300.00"), null)));

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(twoLegTrip);
        requestService.submit(created.id());
        Long leg1Id = created.legs().get(0).id();
        Long leg2Id = created.legs().get(1).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(
                                new TravelVerifyLegRequest(leg1Id, new BigDecimal("450.00"), null, null, null),
                                new TravelVerifyLegRequest(leg2Id, new BigDecimal("250.00"), null, null, null)),
                        null));

        assertThat(afterVerify.legs()).hasSize(2);
        BigDecimal totalActualDistance = afterVerify.legs().stream()
                .map(TravelRequestLegResponse::actualDistanceKm)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPetrol = afterVerify.legs().stream()
                .map(TravelRequestLegResponse::petrolIssued)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalActualDistance).isEqualByComparingTo("700.00"); // 450 + 250
        assertThat(totalPetrol).isEqualByComparingTo("70.00"); // 45 + 25
        assertThat(afterVerify.petrolIssued()).isEqualByComparingTo(totalPetrol); // request summary == Σ legs
        assertThat(afterVerify.totalDistanceKm()).isEqualByComparingTo(totalActualDistance);
        assertThat(afterVerify.travelFuelPolicyId()).isEqualTo(policyId); // shared by both legs

        // Verify the PERSISTED Travel Request summary directly against
        // hardcoded expected values (not merely re-deriving them from the
        // same response object above) — one read straight off the entity,
        // one through a fresh service call.
        TravelRequest persisted = travelRequestRepo.findById(created.id()).orElseThrow();
        assertThat(persisted.getTotalDistanceKm()).isEqualByComparingTo("700.00");
        assertThat(persisted.getPetrolIssued()).isEqualByComparingTo("70.00");
        assertThat(persisted.getTravelFuelPolicyId()).isEqualTo(policyId);

        TravelRequestResponse reloaded = requestService.get(created.id());
        assertThat(reloaded.totalDistanceKm()).isEqualByComparingTo("700.00");
        assertThat(reloaded.petrolIssued()).isEqualByComparingTo("70.00");
        assertThat(reloaded.travelFuelPolicyId()).isEqualTo(policyId);
    }

    @Test
    void transportVerify_fuelCalculation_doesNotDisturb_ddFlow() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        TravelRequestResponse submitted = requestService.submit(created.id());
        assertThat(submitted.status()).isEqualTo("TRANSPORT_PENDING");
        Long legId = created.legs().getFirst().id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterTransport = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("450.00"), null, null, null)),
                        null));
        assertThat(afterTransport.status()).isEqualTo("DD_PENDING");
        assertThat(afterTransport.petrolIssued()).isEqualByComparingTo("45.00");

        // Unchanged: TRANSPORT → DD/Head (final) flow still runs exactly as before.
        CurrentUserHolder.set(user(managerUserId));
        TravelRequestResponse afterDd = requestService.approve(created.id(), null);
        assertThat(afterDd.status()).isEqualTo("APPROVED");
        assertThat(afterDd.petrolIssued()).isEqualByComparingTo("45.00"); // untouched by DD
    }

    @Test
    void transportVerify_legWithNoVehicleSource_leavesFuelFieldsAtZero() {
        // e.g. an AIR/TRAIN leg — the requester never set a vehicle source, and
        // Transport doesn't set one either, so there's no fuel-policy lookup key at all.
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        CreateTravelRequest noSourceTrip = new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, start.plusDays(1).withHour(17),
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "AIR", null, null, "PIA Flight", new BigDecimal("145.00"), null)));

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(noSourceTrip);
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("145.00"), null, null, null)),
                        null));

        TravelRequestLegResponse leg = afterVerify.legs().getFirst();
        assertThat(leg.vehicleSourceCode()).isNull();
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("145.00");
        assertThat(leg.travelFuelPolicyId()).isNull();
        assertThat(leg.petrolIssued()).isEqualByComparingTo("0.00");
        assertThat(afterVerify.travelFuelPolicyId()).isNull();
        assertThat(afterVerify.petrolIssued()).isEqualByComparingTo("0.00");
    }

    @Test
    void transportVerify_vehicleSourceWithNoActiveFuelPolicy_isRejected() {
        // RENTAL is a valid VehicleSource but this suite never seeds a fuel
        // policy for it — a real admin misconfiguration, not a silent zero.
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        assertThatThrownBy(() -> requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("145.00"), "RENTAL", null, null)),
                        null)))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_NO_APPLICABLE_FUEL_POLICY));
    }

    private Long fuelPolicyId(String vehicleSourceCode) {
        return fuelPolicyRepo.findByCode(vehicleSourceCode).orElseThrow().getId();
    }

    // ── Vehicle source: persistence, validation ──────────────────────────────

    @Test
    void createRequest_persistsSuppliedVehicleSourceCode_andReturnsItInTheResponse() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest()); // vehicleSourceCode "COMPANY"

        assertThat(created.legs().get(0).vehicleSourceCode()).isEqualTo("COMPANY");

        // survives a fresh read, not just the create-time echo
        TravelRequestResponse reloaded = requestService.get(created.id());
        assertThat(reloaded.legs().get(0).vehicleSourceCode()).isEqualTo("COMPANY");
    }

    @Test
    void createRequest_rejectsAnUnknownVehicleSourceCode() {
        CurrentUserHolder.set(requesterUser());
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        CreateTravelRequest bad = new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, start.plusDays(1).withHour(17),
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "NOT_A_REAL_SOURCE", "LEB-1234", "Corolla",
                        new BigDecimal("120.00"), null)));

        assertThatThrownBy(() -> requestService.create(bad))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_INVALID_VEHICLE_SOURCE));
    }

    @Test
    void transportVerify_rejectsAnUnknownVehicleSourceCode() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        assertThatThrownBy(() -> requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, null, "NOT_A_REAL_SOURCE", null, null)),
                        null)))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_INVALID_VEHICLE_SOURCE));
        // rejected before commit — the leg and the request are untouched
        assertThat(requestService.get(created.id()).status()).isEqualTo("TRANSPORT_PENDING");
    }

    // ── actualDistanceKm fallback + petrol calculation (confirmed business rule) ──

    private static final BigDecimal FALLBACK_PLANNED = new BigDecimal("500.00");

    private CreateTravelRequest tripRequestWithPlannedDistance(BigDecimal plannedDistanceKm) {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        return new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-1234", "Corolla", plannedDistanceKm, null)));
    }

    /** planned=500, actual=480, average=10 (COMPANY) → actual=480, petrol=48.00 (never the planned distance). */
    @Test
    void transportVerify_petrol_usesFinalizedPositiveActual_notPlanned() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequestWithPlannedDistance(FALLBACK_PLANNED));
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("480.00"), null, null, null)),
                        null));

        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.plannedDistanceKm()).isEqualByComparingTo("500.00");
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("480.00");
        assertThat(leg.petrolIssued()).isEqualByComparingTo("48.00");
    }

    /** planned=500, actual omitted (null), average=10 (COMPANY) → actual=500, petrol=50.00. */
    @Test
    void transportVerify_petrol_fallsBackToPlanned_whenActualOmitted() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequestWithPlannedDistance(FALLBACK_PLANNED));
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null));

        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.plannedDistanceKm()).isEqualByComparingTo("500.00");
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("500.00");
        assertThat(leg.petrolIssued()).isEqualByComparingTo("50.00");
    }

    /** planned=500, actual=0, average=10 (COMPANY) → actual=500, petrol=50.00 (0 treated same as omitted). */
    @Test
    void transportVerify_petrol_fallsBackToPlanned_whenActualIsZero() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequestWithPlannedDistance(FALLBACK_PLANNED));
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, BigDecimal.ZERO, null, null, null)),
                        null));

        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.plannedDistanceKm()).isEqualByComparingTo("500.00");
        assertThat(leg.actualDistanceKm()).isEqualByComparingTo("500.00");
        assertThat(leg.petrolIssued()).isEqualByComparingTo("50.00");
    }

    /**
     * Request-level summary sums the PERSISTED, finalized leg values —
     * including a leg Transport left out of {@code legs} entirely, which
     * still finalizes to its own planned distance AND still auto-resolves
     * its fuel policy from its (creation-time) vehicle source.
     */
    @Test
    void transportVerify_requestSummary_sumsFinalizedLegs_includingAnUnmentionedFallbackLeg() {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        CreateTravelRequest twoLegTrip = new CreateTravelRequest(
                "LOCAL", "PKR", "Multi-city audit", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(
                        new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                                "COMPANY_VEHICLE", "COMPANY", "LEB-1234", "Corolla",
                                new BigDecimal("480.00"), null),
                        new CreateTravelLegRequest(2, "LAHORE", "FAISALABAD", start.plusHours(5), start.plusHours(8),
                                "COMPANY_VEHICLE", "COMPANY", "LEB-1234", "Corolla",
                                new BigDecimal("500.00"), null)));

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(twoLegTrip);
        requestService.submit(created.id());
        Long leg1Id = created.legs().get(0).id(); // explicitly verified below
        // leg2 is deliberately left out of the transportVerify legs list.

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(leg1Id, new BigDecimal("450.00"), null, null, null)),
                        null));

        // leg1: actual=450 (explicit), petrol=45.00; leg2: falls back to planned=500,
        // still COMPANY from creation -> auto-resolved, petrol=50.00
        assertThat(afterVerify.legs().get(0).actualDistanceKm()).isEqualByComparingTo("450.00");
        assertThat(afterVerify.legs().get(0).petrolIssued()).isEqualByComparingTo("45.00");
        assertThat(afterVerify.legs().get(1).actualDistanceKm()).isEqualByComparingTo("500.00");
        assertThat(afterVerify.legs().get(1).petrolIssued()).isEqualByComparingTo("50.00");

        assertThat(afterVerify.totalDistanceKm()).isEqualByComparingTo("950.00"); // 450 + 500
        assertThat(afterVerify.petrolIssued()).isEqualByComparingTo("95.00");     // 45.00 + 50.00
    }

    // ── Vehicle source ↔ fuel policy resolution ──────────────────────────────

    @Test
    void transportVerify_resolvesFuelPolicy_byLegVehicleSource() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest()); // leg vehicleSourceCode = COMPANY
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("120.00"), "COMPANY", null, null)),
                        null));

        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.vehicleSourceCode()).isEqualTo("COMPANY");
        assertThat(leg.travelFuelPolicyId()).isEqualTo(fuelPolicyId("COMPANY"));
        assertThat(leg.petrolIssued()).isEqualByComparingTo("12.00"); // 120 / 10
    }

    /** Changing the vehicle source mid-verify re-resolves the fuel policy to the NEW source, never the old one. */
    @Test
    void transportVerify_changingVehicleSource_reresolvesFuelPolicyToTheNewSource() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest()); // created as COMPANY
        requestService.submit(created.id());
        Long legId = created.legs().get(0).id();

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse afterVerify = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(legId, new BigDecimal("120.00"), "EMPLOYEE", null, null)),
                        null));

        TravelRequestLegResponse leg = afterVerify.legs().get(0);
        assertThat(leg.vehicleSourceCode()).isEqualTo("EMPLOYEE");
        assertThat(leg.travelFuelPolicyId()).isEqualTo(fuelPolicyId("EMPLOYEE"));
        assertThat(leg.travelFuelPolicyId()).isNotEqualTo(fuelPolicyId("COMPANY"));
    }

    @Test
    void fuelPolicyAdmin_rejectsAnUnknownVehicleSourceCode() {
        CurrentUserHolder.set(actor(nextUserId(), "travel.admin"));
        assertThatThrownBy(() -> fuelPolicyService.create(new CreateTravelFuelPolicyRequest(
                "NOT_A_REAL_SOURCE", "Bad code policy",
                new BigDecimal("10.00"), LocalDate.now().minusMonths(1), null, true)))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_INVALID_VEHICLE_SOURCE));
    }

    // ── Response completeness: policyVersionNo, assignedToName/decidedByName ─

    @Test
    void travelRequestResponse_populatesPolicyVersionNo_fromTheAttachedPolicyVersion() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());

        assertThat(created.policyVersionId()).isNotNull();
        // Compare against the real, persisted version's own versionNo (never a
        // hardcoded literal — the shared "LOCAL_FACTORY_TRAVEL" code accumulates
        // versions across every test in this class).
        Integer actualVersionNo = policyService.getPolicyVersion(created.policyVersionId()).versionNo();
        assertThat(created.policyVersionNo()).isNotNull();
        assertThat(created.policyVersionNo()).isEqualTo(actualVersionNo);
    }

    @Test
    void travelRequestResponse_populatesAssignedToNameAndDecidedByName_forResolvableActors() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        TravelRequestResponse submitted = requestService.submit(created.id());

        // Stage 2 (DD/HOD) is assigned to the requester's manager — a real employee.
        assertThat(submitted.steps().get(1).assignedTo()).isEqualTo(managerUserId);
        assertThat(submitted.steps().get(1).assignedToName())
                .isEqualTo(employees.findByUserId(managerUserId).orElseThrow().getFullName());

        // Transport approves — decidedBy is a real employee too.
        long transportUserId = nextUserId();
        persistEmployee("Transport Verifier " + transportUserId, transportUserId, null, null);
        CurrentUserHolder.set(actor(transportUserId, "travel.transport.verify"));
        TravelRequestResponse afterTransport = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null));

        assertThat(afterTransport.steps().get(0).decidedBy()).isEqualTo(transportUserId);
        assertThat(afterTransport.steps().get(0).decidedByName())
                .isEqualTo(employees.findByUserId(transportUserId).orElseThrow().getFullName());
    }

    // ── Travel Advance: MANAGER → HR → ACCOUNTS ──────────────────────────────

    @Test
    void travelAdvance_runsManagerThenHrThenAccounts_toApproved() {
        Fixture f = tripApproved();

        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(f.tripId(), new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL,
                new BigDecimal("4000.00"), "PKR", "hotel", null));
        TravelAdvanceResponse submitted = advanceService.submit(advance.id());

        assertThat(submitted.steps()).extracting(s -> s.stageRole())
                .containsExactly("Manager Approval", "HR Approval", "Accounts Approval");
        assertThat(submitted.steps().get(0).status()).isEqualTo(StepStatus.PENDING.name());
        assertThat(submitted.steps().get(1).status()).isEqualTo(StepStatus.NOT_YET.name());
        assertThat(submitted.steps().get(2).status()).isEqualTo(StepStatus.NOT_YET.name());

        CurrentUserHolder.set(user(managerUserId));
        assertThat(advanceService.approve(advance.id(), null).status())
                .isEqualTo(TravelAdvanceStatus.SUBMITTED);
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        assertThat(advanceService.approve(advance.id(), "hr ok").status())
                .isEqualTo(TravelAdvanceStatus.SUBMITTED);
        CurrentUserHolder.set(actor(nextUserId(), "travel.advance.approve"));
        assertThat(advanceService.approve(advance.id(), "fund it").status())
                .isEqualTo(TravelAdvanceStatus.APPROVED);
    }

    // ──────────────────────────────────────────────────────── fixtures / helpers

    private record Fixture(Long tripId, Long advanceId, Long participantId) {
    }

    /**
     * create → submit → transport + DD/HOD approve → APPROVED trip, no advance yet.
     */
    private Fixture tripApproved() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        Long tripId = created.id();
        requestService.submit(tripId);
        transportApprove(tripId, null);
        CurrentUserHolder.set(user(managerUserId));
        requestService.approve(tripId, null);
        return new Fixture(tripId, null, null);
    }

    /**
     * Approve the TRANSPORT stage as a fresh transport-pool user, optionally verifying legs.
     */
    private void transportApprove(Long tripId, List<TravelVerifyLegRequest> legs) {
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        requestService.transportVerify(tripId,
                new TransportVerifyRequest(TransportDecision.APPROVE, legs, null));
    }

    /**
     * A trip in {@code APPROVED} status (claimable) whose requester grade has a single
     * ACCOMMODATION / FIXED / {@code capAmount} limit. Runs on its own policy version —
     * same pattern as {@code perKmEntitlement_...} — so it never interacts with
     * {@code setUp()}'s shared DINNER/PER_DAY policy or that policy's own tests
     * (e.g. {@code estimatedAmount} assertions elsewhere in this class).
     */
    private Long tripWithAccommodationCap(BigDecimal capAmount) {
        CurrentUserHolder.set(actor(nextUserId(), "travel.admin"));
        TravelPolicyVersionResponse v = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                "LOCAL_FACTORY_TRAVEL", "Accommodation Cap Policy " + System.nanoTime(),
                LocalDate.now().minusMonths(1), null, "test"));
        policyService.addGradeLimit(v.id(), new CreateTravelPolicyGradeLimitRequest(
                gradeCode, "ACCOMMODATION", TravelLimitType.FIXED, capAmount, "PKR", true, null));
        activateReplacingCurrent(v);
        CurrentUserHolder.clear();
        return tripApproved().tripId();
    }

    private CreateTravelExpenseItemRequest accommodationExpenseReq(BigDecimal claimed) {
        return expenseReq("ACCOMMODATION", "ACCOMMODATION", "HOTEL", claimed);
    }

    private CreateTravelRequest tripRequestFor(long requesterUserId) {
        Employee e = employees.findByUserId(requesterUserId).orElseThrow();
        LocalDateTime start = LocalDateTime.now().plusDays(10)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        return new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, end,
                List.of(new CreateTravelParticipantRequest(e.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-1234", "Corolla",
                        new BigDecimal("120.00"), null)));
    }

    /**
     * create → submit → approve (transport + DD/HOD) → advance → approve (mgr + HR + accounts) → pay → start → complete.
     */
    private Fixture tripCompletedWithPaidAdvance(BigDecimal advanceAmount) {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        Long tripId = created.id();
        requestService.submit(tripId);
        transportApprove(tripId, null);

        CurrentUserHolder.set(user(managerUserId));
        assertThat(requestService.approve(tripId, null).status()).isEqualTo("APPROVED");

        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(tripId, new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, advanceAmount, "PKR", "hotel + meals", null));
        Long advanceId = advance.id();
        advanceService.submit(advanceId);

        CurrentUserHolder.set(user(managerUserId));
        advanceService.approve(advanceId, null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        advanceService.approve(advanceId, "hr ok");
        CurrentUserHolder.set(actor(nextUserId(), "travel.advance.approve"));
        assertThat(advanceService.approve(advanceId, "fund it").status()).isEqualTo(TravelAdvanceStatus.APPROVED);
        advanceService.markPaymentPending(advanceId);
        TravelAdvanceResponse paid = advanceService.markPaid(advanceId, advanceAmount, "PKR",
                "PAY-" + advanceId, null, TravelAdvancePaymentMethodMode.BANK_TRANSFER);
        assertThat(paid.status()).isEqualTo(TravelAdvanceStatus.PAID);

        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(tripId);
        assertThat(requestService.completeTravel(tripId).status()).isEqualTo("COMPLETED");

        Long participantId = participants.findByTravelRequestIdAndEmployeeId(tripId, requester.getId())
                .orElseThrow().getId();
        return new Fixture(tripId, advanceId, participantId);
    }

    private CreateTravelRequest tripRequest() {
        LocalDateTime start = LocalDateTime.now().plusDays(10)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        return new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-1234", "Corolla",
                        new BigDecimal("120.00"), null)));
    }

    private CreateTravelExpenseItemRequest expenseReq(String entitlementTypeCode, String category,
                                                      String subcategory, BigDecimal claimed) {
        return new CreateTravelExpenseItemRequest(
                requester.getEmployeeCode(), entitlementTypeCode, category, subcategory,
                subcategory + " during trip", LocalDateTime.now(), claimed, "PKR", null, null, null);
    }

    private static CreateTravelSettlementItemRequest item(TravelSettlementItemSourceType type, Long sourceId,
                                                          String sourceAmount, String settlementAmount,
                                                          TravelSettlementItemDirection direction) {
        return new CreateTravelSettlementItemRequest(type, sourceId,
                new BigDecimal(sourceAmount), new BigDecimal(settlementAmount), "PKR", direction, null);
    }

    private long persistEmployee(String fullName, long userId, Long managerId, Long gradeId) {
        Employee e = new Employee();
        e.setEmployeeCode("TRV-" + System.nanoTime());
        e.setFullName(fullName);
        e.setUserId(userId);
        e.setManagerId(managerId);
        e.setGradeId(gradeId);
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

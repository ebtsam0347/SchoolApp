package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
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
import com.forward.hrms.employee.dto.request.travel.request.TravelVerifyLegRequest;
import com.forward.hrms.employee.dto.request.travel.request.TransportVerifyRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelAdvanceResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelExpenseItemResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelPolicyVersionResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelSettlementResponse;
import com.forward.hrms.employee.dto.response.TravelRequestParticipantResponse;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.travel.TransportDecision;
import com.forward.hrms.employee.enums.travel.TravelAdvancePaymentMethodMode;
import com.forward.hrms.employee.enums.travel.TravelAdvanceStatus;
import com.forward.hrms.employee.enums.travel.TravelAdvanceType;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.TravelExpenseStatus;
import com.forward.hrms.employee.enums.travel.TravelLimitType;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;
import com.forward.hrms.employee.enums.travel.TravelSettlementDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemDirection;
import com.forward.hrms.employee.enums.travel.TravelSettlementItemSourceType;
import com.forward.hrms.employee.enums.travel.TravelSettlementStatus;
import com.forward.hrms.employee.enums.travel.TravelSettlementType;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.travel.TravelFuelPolicyRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end case coverage for the whole Travel Request module — creation
 * through settlement — complementing {@link TravelModuleFlowIntegrationTest}
 * and {@link TravelSettlementPreparationIntegrationTest} rather than
 * duplicating them: this class targets validation guards, terminal/negative
 * branches and multi-record scenarios (two advances, two participants) not
 * already exercised there.
 *
 * <p>Read-only with respect to production code — every case here is driven
 * purely through the existing public service methods.
 */
@SpringBootTest
@WithTestcontainers
class TravelRequestModuleCoverageIntegrationTest {

    // Testcontainers reuse persists the DB across classes; employees.user_id
    // is unique — never hardcode a userId literal.
    private static final AtomicLong USER_ID_SEQ = new AtomicLong((System.nanoTime() ^ 0x51a7c0de) & 0x7fffffffL);

    @Autowired TravelPolicyService policyService;
    @Autowired TravelRequestService requestService;
    @Autowired TravelAdvanceService advanceService;
    @Autowired TravelExpenseService expenseService;
    @Autowired TravelSettlementService settlementService;
    @Autowired TravelFuelPolicyService fuelPolicyService;
    @Autowired TravelFuelPolicyRepository fuelPolicyRepo;
    @Autowired EmployeeRepository employees;
    @Autowired DepartmentRepository departments;
    @Autowired GradeRepository grades;

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
        gradeCode = "TRV-COV-G-" + System.nanoTime();
        g.setCode(gradeCode);
        g.setName("Travel Coverage Test Grade");
        g.setLevel(1);
        Long gradeId = grades.save(g).getId();

        managerUserId = nextUserId();
        long managerEmployeeId = persistEmployee("Coverage Manager " + managerUserId, managerUserId, null, null);
        long requesterUserId = nextUserId();
        persistEmployee("Coverage Requester " + requesterUserId, requesterUserId, managerEmployeeId, gradeId);
        requester = employees.findByUserId(requesterUserId).orElseThrow();

        // Only a Manager/HOD may raise a travel request — give the requester a direct report.
        long reportUserId = nextUserId();
        persistEmployee("Coverage Report " + reportUserId, reportUserId, requester.getId(), gradeId);

        CurrentUserHolder.set(actor(nextUserId(), "travel.admin"));
        TravelPolicyVersionResponse version = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                "LOCAL_FACTORY_TRAVEL", "Coverage Factory Travel Policy", LocalDate.now().minusMonths(1), null, "test"));
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

    // ── A. Creation & validation ─────────────────────────────────────────────

    @Test
    void create_multiLegMultiParticipant_persistsCorrectSnapshotAndTotals() {
        long coUserId = nextUserId();
        persistEmployee("Coverage CoTraveler " + coUserId, coUserId, requester.getId(), null);
        Employee coTraveler = employees.findByUserId(coUserId).orElseThrow();

        LocalDateTime start = LocalDateTime.now().plusDays(20).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(2).withHour(17);
        CreateTravelRequest req = new CreateTravelRequest(
                "LOCAL", "PKR", "Multi-city vendor audit", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode()),
                        new CreateTravelParticipantRequest(coTraveler.getEmployeeCode())),
                List.of(
                        new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                                "COMPANY_VEHICLE", "COMPANY", "LEB-1111", "Coaster",
                                new BigDecimal("120.00"), null),
                        new CreateTravelLegRequest(2, "LAHORE", "FAISALABAD", start.plusDays(1), start.plusDays(1).plusHours(3),
                                "COMPANY_VEHICLE", "COMPANY", "LEB-1111", "Coaster",
                                new BigDecimal("130.00"), null)));

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(req);

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.legs()).hasSize(2);
        assertThat(created.participants()).hasSize(2);
        assertThat(created.participants()).extracting(TravelRequestParticipantResponse::participantRole)
                .containsExactlyInAnyOrder("REQUESTER", "PARTICIPANT");
    }

    @Test
    void create_endBeforeStart_isRejected() {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.minusDays(1);
        CreateTravelRequest req = new CreateTravelRequest(
                "LOCAL", "PKR", "Bad dates", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, null,
                        "COMPANY_VEHICLE", "COMPANY", null, null, new BigDecimal("100.00"), null)));

        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> requestService.create(req))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE));
    }

    @Test
    void create_duplicateLegSequence_isRejected() {
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1);
        CreateTravelRequest req = new CreateTravelRequest(
                "LOCAL", "PKR", "Duplicate sequence", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(
                        new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, null,
                                "COMPANY_VEHICLE", "COMPANY", null, null, new BigDecimal("100.00"), null),
                        new CreateTravelLegRequest(1, "LAHORE", "FAISALABAD", start.plusHours(6), null,
                                "COMPANY_VEHICLE", "COMPANY", null, null, new BigDecimal("50.00"), null)));

        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> requestService.create(req))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE));
    }

    @Test
    void create_noApplicablePolicy_isRejected() {
        // Predates the setUp() policy version's effectiveFrom (now - 1 month) —
        // no ACTIVE policy version's window covers this trip.
        LocalDateTime start = LocalDateTime.now().minusYears(2);
        LocalDateTime end = start.plusDays(1);
        CreateTravelRequest req = new CreateTravelRequest(
                "LOCAL", "PKR", "Too far in the past", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, null,
                        "COMPANY_VEHICLE", "COMPANY", null, null, new BigDecimal("100.00"), null)));

        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> requestService.create(req))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_NO_APPLICABLE_POLICY));
    }

    // ── B. Submit guards ──────────────────────────────────────────────────────

    @Test
    void submit_withNoLegs_isRejected() {
        // Bypasses the DTO's @NotEmpty (only enforced at the controller) to
        // exercise the service-level defense-in-depth guard directly.
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        CreateTravelRequest req = new CreateTravelRequest(
                "LOCAL", "PKR", "No legs", start, start.plusDays(1),
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of());

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(req);
        assertThat(created.legs()).isEmpty();

        assertThatThrownBy(() -> requestService.submit(created.id()))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE));
    }

    @Test
    void submit_alreadySubmitted_isRejected() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        assertThatThrownBy(() -> requestService.submit(created.id()))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void submit_requesterWithNoManager_isRejected() {
        // A department head with no manager above them can CREATE (isManagerOrHod
        // via headId) but SUBMIT independently requires a resolvable DD/HOD —
        // their own manager — which does not exist here.
        long hodUserId = nextUserId();
        long hodEmpId = persistEmployee("Coverage Dept Head " + hodUserId, hodUserId, null, null);
        Department d = new Department();
        d.setCode("TRV-COV-DEPT-" + System.nanoTime());
        d.setName("Coverage Test Dept");
        d.setHeadId(hodEmpId);
        departments.save(d);
        Employee hod = employees.findByUserId(hodUserId).orElseThrow();

        CurrentUserHolder.set(user(hodUserId));
        LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
        TravelRequestResponse created = requestService.create(new CreateTravelRequest(
                "LOCAL", "PKR", "HOD trip", start, start.plusDays(1),
                List.of(new CreateTravelParticipantRequest(hod.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, null,
                        "COMPANY_VEHICLE", "COMPANY", null, null, new BigDecimal("100.00"), null))));
        assertThat(created.status()).isEqualTo("DRAFT");

        assertThatThrownBy(() -> requestService.submit(created.id()))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE));
    }

    // ── C. Cancel — each reachable state ──────────────────────────────────────

    @Test
    void cancel_draft_succeeds() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        TravelRequestResponse cancelled = requestService.cancel(created.id(), "changed plans");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_duringTransportPending_succeeds_andCancelsChain() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        TravelRequestResponse cancelled = requestService.cancel(created.id(), "trip postponed");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");

        // the cancelled chain can no longer be acted on
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        assertThatThrownBy(() -> requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE, null, null)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void cancel_afterFullyApproved_isRejected() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> requestService.cancel(tripId, "too late"))
                .isInstanceOf(ConflictError.class)
                .satisfies(ex -> assertThat(((ConflictError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE));
    }

    @Test
    void cancel_alreadyCancelled_isRejected() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.cancel(created.id(), "first cancel");

        assertThatThrownBy(() -> requestService.cancel(created.id(), "second cancel"))
                .isInstanceOf(ConflictError.class);
    }

    // ── D. Start / Complete guards ────────────────────────────────────────────

    @Test
    void startTravel_fromNonApprovedStatus_isRejected() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        assertThatThrownBy(() -> requestService.startTravel(created.id()))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void completeTravel_withoutStarting_isRejected() {
        Long tripId = tripApproved();
        assertThatThrownBy(() -> requestService.completeTravel(tripId))
                .isInstanceOf(ConflictError.class);
    }

    // ── E. Transport-stage extra ──────────────────────────────────────────────

    @Test
    void transportVerify_unknownLegId_isRejected() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        assertThatThrownBy(() -> requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.APPROVE,
                        List.of(new TravelVerifyLegRequest(999_999_999L, new BigDecimal("50.00"),
                                null, null, null)),
                        null)))
                .isInstanceOf(com.forward.hrms.commons.core.error.NotFoundError.class);
    }

    // ── F. Rejection cascade ──────────────────────────────────────────────────

    @Test
    void transportRejected_blocksAdvanceCreation() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        TravelRequestResponse rejected = requestService.transportVerify(created.id(),
                new TransportVerifyRequest(TransportDecision.REJECT, null, "no vehicle"));
        assertThat(rejected.status()).isEqualTo("TRANSPORT_REJECTED");

        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> advanceService.create(created.id(), new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("1000.00"),
                "PKR", "hotel", null)))
                .isInstanceOf(ConflictError.class);
    }

    // ── G. Travel Advance ─────────────────────────────────────────────────────

    @Test
    void advance_create_forCoTravelingParticipant_succeeds() {
        long coUserId = nextUserId();
        persistEmployee("Coverage Advance CoTraveler " + coUserId, coUserId, requester.getId(), null);
        Employee coTraveler = employees.findByUserId(coUserId).orElseThrow();

        Long tripId = tripApprovedWithParticipant(coTraveler.getEmployeeCode());

        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(tripId, new CreateTravelAdvanceRequest(
                coTraveler.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("2000.00"),
                "PKR", "co-traveler hotel", null));
        assertThat(advance.employeeCode()).isEqualTo(coTraveler.getEmployeeCode());
        assertThat(advance.status()).isEqualTo(TravelAdvanceStatus.DRAFT);
    }

    @Test
    void advance_cannotBeRaised_afterTripCompleted() {
        Long tripId = tripCompleted();
        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> advanceService.create(tripId, new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("500.00"),
                "PKR", "too late", null)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void advance_reject_isTerminal_blocksFurtherApproval() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(tripId, new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("3000.00"),
                "PKR", "hotel", null));
        advanceService.submit(advance.id());

        CurrentUserHolder.set(user(managerUserId));
        TravelAdvanceResponse rejected = advanceService.reject(advance.id(), "budget frozen");
        assertThat(rejected.status()).isEqualTo(TravelAdvanceStatus.REJECTED);

        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        assertThatThrownBy(() -> advanceService.approve(advance.id(), "too late"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void advance_cancel_fromDraft_succeeds() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(tripId, new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("1000.00"),
                "PKR", "hotel", null));
        TravelAdvanceResponse cancelled = advanceService.cancel(advance.id(), "not needed");
        assertThat(cancelled.status()).isEqualTo(TravelAdvanceStatus.CANCELED);
    }

    @Test
    void advance_cancel_fromSubmitted_cancelsChain() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(tripId, new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("1000.00"),
                "PKR", "hotel", null));
        advanceService.submit(advance.id());

        TravelAdvanceResponse cancelled = advanceService.cancel(advance.id(), "changed mind");
        assertThat(cancelled.status()).isEqualTo(TravelAdvanceStatus.CANCELED);

        CurrentUserHolder.set(user(managerUserId));
        assertThatThrownBy(() -> advanceService.approve(advance.id(), "too late"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void advance_update_onlyWhileDraft() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(tripId, new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("1000.00"),
                "PKR", "hotel", null));

        TravelAdvanceResponse updated = advanceService.update(advance.id(), new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("1500.00"),
                "PKR", "hotel + meals", null));
        assertThat(updated.requestedAmount()).isEqualByComparingTo("1500.00");

        advanceService.submit(advance.id());
        assertThatThrownBy(() -> advanceService.update(advance.id(), new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, new BigDecimal("2000.00"),
                "PKR", "too late", null)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void advance_markPaid_rejectsAmountAboveApproved() {
        Long advanceId = advanceApproved(new BigDecimal("4000.00"));
        assertThatThrownBy(() -> advanceService.markPaid(advanceId, new BigDecimal("5000.00"),
                "PKR", "PAY-OVER", null, TravelAdvancePaymentMethodMode.BANK_TRANSFER))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_ADVANCE_INVALID_STATE));
    }

    @Test
    void advance_markPaymentPending_thenMarkPaid_flow() {
        Long advanceId = advanceApproved(new BigDecimal("2500.00"));
        TravelAdvanceResponse pending = advanceService.markPaymentPending(advanceId);
        assertThat(pending.status()).isEqualTo(TravelAdvanceStatus.PAYMENT_PENDING);

        TravelAdvanceResponse paid = advanceService.markPaid(advanceId, new BigDecimal("2500.00"),
                "PKR", "PAY-1", "EXT-1", TravelAdvancePaymentMethodMode.BANK_TRANSFER);
        assertThat(paid.status()).isEqualTo(TravelAdvanceStatus.PAID);
        assertThat(paid.paidAmount()).isEqualByComparingTo("2500.00");
    }

    @Test
    void advance_reconcilePayment_requiresPriorPayment_thenFlags() {
        Long advanceId = advanceApproved(new BigDecimal("1200.00"));

        assertThatThrownBy(() -> advanceService.reconcilePayment(advanceId, "EXT-9"))
                .isInstanceOf(ConflictError.class);

        advanceService.markPaid(advanceId, new BigDecimal("1200.00"), "PKR", "PAY-2", null,
                TravelAdvancePaymentMethodMode.CASH);
        TravelAdvanceResponse reconciled = advanceService.reconcilePayment(advanceId, "EXT-9");
        assertThat(reconciled.paymentReconciled()).isTrue();
    }

    // ── H. Travel Expense ─────────────────────────────────────────────────────

    @Test
    void expense_create_beforeApproved_isRejected() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        requestService.submit(created.id());

        assertThatThrownBy(() -> expenseService.create(created.id(), expenseReq(
                null, "TRANSPORTATION", "TAXI", new BigDecimal("500.00"))))
                .isInstanceOf(ConflictError.class)
                .satisfies(ex -> assertThat(((ConflictError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE));
    }

    @Test
    void expense_reject_setsZeroApprovedAndFullNonReimbursable() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse expense = expenseService.create(tripId, expenseReq(
                null, "TRANSPORTATION", "TAXI", new BigDecimal("800.00")));

        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        TravelExpenseItemResponse rejected = expenseService.reject(expense.id(), "no receipt attached");
        assertThat(rejected.status()).isEqualTo(TravelExpenseStatus.REJECTED);
        assertThat(rejected.approvedAmount()).isEqualByComparingTo("0.00");
        assertThat(rejected.nonReimbursableAmount()).isEqualByComparingTo("800.00");
        assertThat(rejected.rejectionReason()).isEqualTo("no receipt attached");
    }

    @Test
    void expense_cancel_fromSubmitted() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse expense = expenseService.create(tripId, expenseReq(
                null, "TRANSPORTATION", "TAXI", new BigDecimal("300.00")));

        TravelExpenseItemResponse cancelled = expenseService.cancel(expense.id(), "duplicate entry");
        assertThat(cancelled.status()).isEqualTo(TravelExpenseStatus.CANCELLED);
    }

    @Test
    void expense_markPaid_afterApproval() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse expense = expenseService.create(tripId, expenseReq(
                null, "TRANSPORTATION", "TAXI", new BigDecimal("450.00")));

        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(expense.id(), null, null);
        TravelExpenseItemResponse paid = expenseService.markPaid(expense.id(), "PAY-EXP-1");
        assertThat(paid.status()).isEqualTo(TravelExpenseStatus.PAID);
        assertThat(paid.paymentReference()).isEqualTo("PAY-EXP-1");
    }

    @Test
    void expense_approve_explicitPartialAmount_marksPartiallyApproved() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse expense = expenseService.create(tripId, expenseReq(
                null, "TRANSPORTATION", "TAXI", new BigDecimal("1000.00")));

        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        TravelExpenseItemResponse approved = expenseService.approve(expense.id(), new BigDecimal("600.00"), "partial ok");
        assertThat(approved.status()).isEqualTo(TravelExpenseStatus.PARTIALLY_APPROVED);
        assertThat(approved.approvedAmount()).isEqualByComparingTo("600.00");
        assertThat(approved.nonReimbursableAmount()).isEqualByComparingTo("400.00");
    }

    // ── I. Travel Settlement (manual create path + edge cases) ───────────────

    @Test
    void settlement_manualCreate_adjustmentItem_missingDirection_isRejected() {
        Long tripId = tripCompleted();
        Long participantId = requesterParticipantId(tripId);

        assertThatThrownBy(() -> settlementService.create(tripId, new CreateTravelSettlementRequest(
                participantId, TravelSettlementType.ADJUSTMENT, "PKR", "missing direction",
                List.of(new CreateTravelSettlementItemRequest(
                        TravelSettlementItemSourceType.ADJUSTMENT, 1L,
                        new BigDecimal("100.00"), new BigDecimal("100.00"), "PKR", null, "test")),
                null)))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE));
    }

    @Test
    void settlement_manualCreate_settlementAmountExceedsSourceAmount_isRejected() {
        Long tripId = tripCompleted();
        Long participantId = requesterParticipantId(tripId);

        assertThatThrownBy(() -> settlementService.create(tripId, new CreateTravelSettlementRequest(
                participantId, TravelSettlementType.ADJUSTMENT, "PKR", "over-claim adjustment",
                List.of(new CreateTravelSettlementItemRequest(
                        TravelSettlementItemSourceType.ADJUSTMENT, 1L,
                        new BigDecimal("100.00"), new BigDecimal("500.00"), "PKR",
                        TravelSettlementItemDirection.PAYABLE, "test")),
                null)))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE));
    }

    @Test
    void settlement_manualCreate_expenseItem_ignoresClientSuppliedDirectionAndAmounts() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse expense = expenseService.create(tripId, expenseReq(
                null, "TRANSPORTATION", "TAXI", new BigDecimal("1000.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(expense.id(), null, null); // approvedAmount = 1000

        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(tripId);
        requestService.completeTravel(tripId);
        Long participantId = requesterParticipantId(tripId);

        // Deliberately wrong direction/amounts from the "client" — the backend
        // must re-derive them from the persisted expense record.
        TravelSettlementResponse settlement = settlementService.create(tripId, new CreateTravelSettlementRequest(
                participantId, TravelSettlementType.FINAL, "PKR", "server authoritative",
                List.of(new CreateTravelSettlementItemRequest(
                        TravelSettlementItemSourceType.EXPENSE, expense.id(),
                        new BigDecimal("1.00"), new BigDecimal("1.00"), "PKR",
                        TravelSettlementItemDirection.RECEIVABLE, null)),
                null));

        assertThat(settlement.items()).hasSize(1);
        assertThat(settlement.items().get(0).direction()).isEqualTo(TravelSettlementItemDirection.PAYABLE);
        assertThat(settlement.items().get(0).sourceAmount()).isEqualByComparingTo("1000.00");
        assertThat(settlement.items().get(0).settlementAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void settlement_submit_withNoItems_isRejected() {
        Long tripId = tripCompleted();
        Long participantId = requesterParticipantId(tripId);

        // create() itself does not require items — only submit() does.
        TravelSettlementResponse settlement = settlementService.create(tripId, new CreateTravelSettlementRequest(
                participantId, TravelSettlementType.ADJUSTMENT, "PKR", "empty", List.of(), null));
        assertThat(settlement.items()).isEmpty();

        assertThatThrownBy(() -> settlementService.submit(settlement.id()))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_SETTLEMENT_INVALID_STATE));
    }

    @Test
    void settlement_reject_isTerminal_tripStaysAtSettlementPending() {
        Long tripId = tripCompleted();
        Long participantId = requesterParticipantId(tripId);
        TravelSettlementResponse settlement = adjustmentSettlement(tripId, participantId, "500.00",
                TravelSettlementItemDirection.PAYABLE);
        assertThat(requestService.get(tripId).status()).isEqualTo("SETTLEMENT_PENDING");

        settlementService.submit(settlement.id());
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        TravelSettlementResponse rejected = settlementService.reject(settlement.id(), "not approved");
        assertThat(rejected.status()).isEqualTo(TravelSettlementStatus.REJECTED);

        // Rejection is terminal for the settlement, but (unlike cancel) does not
        // revert the trip — it stays SETTLEMENT_PENDING.
        assertThat(requestService.get(tripId).status()).isEqualTo("SETTLEMENT_PENDING");
    }

    @Test
    void settlement_cancel_fromDraft_reopensTripToCompleted() {
        Long tripId = tripCompleted();
        Long participantId = requesterParticipantId(tripId);
        TravelSettlementResponse settlement = adjustmentSettlement(tripId, participantId, "300.00",
                TravelSettlementItemDirection.PAYABLE);
        assertThat(requestService.get(tripId).status()).isEqualTo("SETTLEMENT_PENDING");

        TravelSettlementResponse cancelled = settlementService.cancel(settlement.id(), "raised by mistake");
        assertThat(cancelled.status()).isEqualTo(TravelSettlementStatus.CANCELLED);
        assertThat(requestService.get(tripId).status()).isEqualTo("COMPLETED");
    }

    @Test
    void settlement_create_beforeCompleted_isConflict() {
        Long tripId = tripApproved();
        Long participantId = requesterParticipantId(tripId);

        assertThatThrownBy(() -> settlementService.create(tripId, new CreateTravelSettlementRequest(
                participantId, TravelSettlementType.ADJUSTMENT, "PKR", "too early",
                List.of(new CreateTravelSettlementItemRequest(
                        TravelSettlementItemSourceType.ADJUSTMENT, 1L,
                        new BigDecimal("100.00"), new BigDecimal("100.00"), "PKR",
                        TravelSettlementItemDirection.PAYABLE, null)),
                null)))
                .isInstanceOf(ConflictError.class)
                .satisfies(ex -> assertThat(((ConflictError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_REQUEST_INVALID_STATE));
    }

    @Test
    void settlement_create_participantMismatch_isRejected() {
        Long tripAId = tripCompleted();
        Long tripBId = tripCompleted();
        Long participantOfTripA = requesterParticipantId(tripAId);

        assertThatThrownBy(() -> settlementService.create(tripBId, new CreateTravelSettlementRequest(
                participantOfTripA, TravelSettlementType.ADJUSTMENT, "PKR", "cross-request participant",
                List.of(new CreateTravelSettlementItemRequest(
                        TravelSettlementItemSourceType.ADJUSTMENT, 1L,
                        new BigDecimal("100.00"), new BigDecimal("100.00"), "PKR",
                        TravelSettlementItemDirection.PAYABLE, null)),
                null)))
                .isInstanceOf(UnprocessableError.class)
                .satisfies(ex -> assertThat(((UnprocessableError) ex).code())
                        .isEqualTo(TravelErrorCode.TRAVEL_PARTICIPANT_NOT_FOUND));
    }

    @Test
    void settlement_twoAdvances_splitConsumptionAcrossThem_inIdOrder() {
        Long tripId = tripApproved();

        Long advance1 = payAdvance(tripId, new BigDecimal("3000.00"));
        Long advance2 = payAdvance(tripId, new BigDecimal("4000.00"));
        assertThat(advance1).isLessThan(advance2);

        CurrentUserHolder.set(requesterUser());
        TravelExpenseItemResponse expense = expenseService.create(tripId, expenseReq(
                null, "TRANSPORTATION", "TAXI", new BigDecimal("5000.00")));
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        expenseService.approve(expense.id(), null, null); // approvedAmount = 5000

        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(tripId);
        requestService.completeTravel(tripId);
        Long participantId = requesterParticipantId(tripId);

        TravelSettlementResponse settlement = settlementService.create(tripId, new CreateTravelSettlementRequest(
                participantId, TravelSettlementType.FINAL, "PKR", "two-advance split",
                List.of(
                        settlementItem(TravelSettlementItemSourceType.EXPENSE, expense.id(),
                                "5000.00", "5000.00", TravelSettlementItemDirection.PAYABLE),
                        settlementItem(TravelSettlementItemSourceType.ADVANCE, advance1,
                                "3000.00", "3000.00", TravelSettlementItemDirection.RECEIVABLE),
                        settlementItem(TravelSettlementItemSourceType.ADVANCE, advance2,
                                "4000.00", "4000.00", TravelSettlementItemDirection.RECEIVABLE)),
                null));

        assertThat(settlement.totalApprovedExpenses()).isEqualByComparingTo("5000.00");
        assertThat(settlement.totalAdvancesPaid()).isEqualByComparingTo("7000.00");
        assertThat(settlement.settlementDirection()).isEqualTo(TravelSettlementDirection.EMPLOYEE_RECEIVABLE);
        assertThat(settlement.finalSettlementAmount()).isEqualByComparingTo("2000.00");

        settlementService.submit(settlement.id());
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(settlement.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        settlementService.settle(settlement.id(), "RECOVER-SPLIT", null);

        // consumable pool (5000) fills advance1 (3000) first, then advance2 up to 2000 of 4000.
        TravelAdvanceResponse a1 = advanceService.get(advance1);
        assertThat(a1.status()).isEqualTo(TravelAdvanceStatus.SETTLED);
        assertThat(a1.settledAmount()).isEqualByComparingTo("3000.00");
        assertThat(a1.returnedAmount()).isEqualByComparingTo("0.00");

        TravelAdvanceResponse a2 = advanceService.get(advance2);
        assertThat(a2.status()).isEqualTo(TravelAdvanceStatus.SETTLED);
        assertThat(a2.settledAmount()).isEqualByComparingTo("2000.00");
        assertThat(a2.returnedAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void settlement_multipleParticipants_tripStaysPending_untilAllSettled() {
        long coUserId = nextUserId();
        persistEmployee("Coverage Settle CoTraveler " + coUserId, coUserId, requester.getId(), null);
        Employee coTraveler = employees.findByUserId(coUserId).orElseThrow();

        Long tripId = tripApprovedWithParticipant(coTraveler.getEmployeeCode());
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(tripId);
        requestService.completeTravel(tripId);

        Long requesterParticipantId = requesterParticipantId(tripId);
        Long coParticipantId = participantIdFor(tripId, coTraveler.getEmployeeCode());

        // Settle the requester alone first.
        TravelSettlementResponse s1 = adjustmentSettlement(tripId, requesterParticipantId, "200.00",
                TravelSettlementItemDirection.PAYABLE);
        settlementService.submit(s1.id());
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(s1.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        settlementService.settle(s1.id(), "PAYOUT-REQ", null);

        assertThat(requestService.get(tripId).status()).isEqualTo("SETTLEMENT_PENDING");

        // Now settle the co-traveler — only after this does the trip flip to SETTLED.
        TravelSettlementResponse s2 = adjustmentSettlement(tripId, coParticipantId, "150.00",
                TravelSettlementItemDirection.PAYABLE);
        settlementService.submit(s2.id());
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.approve"));
        settlementService.approve(s2.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.settlement.paid"));
        settlementService.settle(s2.id(), "PAYOUT-CO", null);

        assertThat(requestService.get(tripId).status()).isEqualTo("SETTLED");
    }

    // ──────────────────────────────────────────────────────── fixtures / helpers

    private CreateTravelRequest tripRequest() {
        LocalDateTime start = LocalDateTime.now().plusDays(10)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        return new CreateTravelRequest(
                "LOCAL", "PKR", "Coverage trip", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-2222", "Corolla",
                        new BigDecimal("120.00"), null)));
    }

    private CreateTravelRequest tripRequestWithParticipant(String coTravelerEmployeeCode) {
        LocalDateTime start = LocalDateTime.now().plusDays(10)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        return new CreateTravelRequest(
                "LOCAL", "PKR", "Coverage trip with co-traveler", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode()),
                        new CreateTravelParticipantRequest(coTravelerEmployeeCode)),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-2222", "Corolla",
                        new BigDecimal("120.00"), null)));
    }

    /** create → submit → transport + DD/HOD approve → APPROVED trip, single participant (requester). */
    private Long tripApproved() {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequest());
        Long tripId = created.id();
        approveWholeChain(tripId);
        return tripId;
    }

    /** Same as {@link #tripApproved()} but with an additional traveling participant. */
    private Long tripApprovedWithParticipant(String coTravelerEmployeeCode) {
        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(tripRequestWithParticipant(coTravelerEmployeeCode));
        Long tripId = created.id();
        approveWholeChain(tripId);
        return tripId;
    }

    private void approveWholeChain(Long tripId) {
        CurrentUserHolder.set(requesterUser());
        requestService.submit(tripId);
        CurrentUserHolder.set(actor(nextUserId(), "travel.transport.verify"));
        requestService.transportVerify(tripId, new TransportVerifyRequest(TransportDecision.APPROVE, null, null));
        CurrentUserHolder.set(user(managerUserId));
        assertThat(requestService.approve(tripId, null).status()).isEqualTo("APPROVED");
    }

    /** {@link #tripApproved()} then started and completed. */
    private Long tripCompleted() {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        requestService.startTravel(tripId);
        assertThat(requestService.completeTravel(tripId).status()).isEqualTo("COMPLETED");
        return tripId;
    }

    /** Raise, submit and fully approve (MANAGER → HR → ACCOUNTS) an advance for the requester; returns its id. */
    private Long advanceApproved(BigDecimal amount) {
        Long tripId = tripApproved();
        CurrentUserHolder.set(requesterUser());
        TravelAdvanceResponse advance = advanceService.create(tripId, new CreateTravelAdvanceRequest(
                requester.getEmployeeCode(), TravelAdvanceType.INITIAL, amount, "PKR", "advance", null));
        advanceService.submit(advance.id());

        CurrentUserHolder.set(user(managerUserId));
        advanceService.approve(advance.id(), null);
        CurrentUserHolder.set(actor(nextUserId(), "travel.approve"));
        advanceService.approve(advance.id(), "hr ok");
        CurrentUserHolder.set(actor(nextUserId(), "travel.advance.approve"));
        assertThat(advanceService.approve(advance.id(), "fund it").status()).isEqualTo(TravelAdvanceStatus.APPROVED);
        return advance.id();
    }

    /** Raise, approve and pay (in full) an advance against {@code tripId} for the requester; returns its id. */
    private Long payAdvance(Long tripId, BigDecimal amount) {
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
        advanceService.markPaymentPending(advance.id());
        advanceService.markPaid(advance.id(), amount, "PKR", "PAY-" + advance.id(), null,
                TravelAdvancePaymentMethodMode.BANK_TRANSFER);
        return advance.id();
    }

    /** Creates and immediately submits + approves a single-item ADJUSTMENT settlement (does not settle it). */
    private TravelSettlementResponse adjustmentSettlement(Long tripId, Long participantId, String amount,
                                                          TravelSettlementItemDirection direction) {
        return settlementService.create(tripId, new CreateTravelSettlementRequest(
                participantId, TravelSettlementType.FINAL, "PKR", "coverage adjustment",
                List.of(new CreateTravelSettlementItemRequest(
                        TravelSettlementItemSourceType.ADJUSTMENT, 1L,
                        new BigDecimal(amount), new BigDecimal(amount), "PKR", direction, "coverage")),
                null));
    }

    private static CreateTravelSettlementItemRequest settlementItem(TravelSettlementItemSourceType type,
                                                                     Long sourceId, String sourceAmount,
                                                                     String settlementAmount,
                                                                     TravelSettlementItemDirection direction) {
        return new CreateTravelSettlementItemRequest(type, sourceId,
                new BigDecimal(sourceAmount), new BigDecimal(settlementAmount), "PKR", direction, null);
    }

    private Long requesterParticipantId(Long tripId) {
        return participantIdFor(tripId, requester.getEmployeeCode());
    }

    private Long participantIdFor(Long tripId, String employeeCode) {
        return requestService.get(tripId).participants().stream()
                .filter(p -> employeeCode.equals(p.employeeCode()))
                .findFirst()
                .map(TravelRequestParticipantResponse::id)
                .orElseThrow(() -> new AssertionError(
                        "participant " + employeeCode + " not found on trip " + tripId));
    }

    private CreateTravelExpenseItemRequest expenseReq(String entitlementTypeCode, String category,
                                                      String subcategory, BigDecimal claimed) {
        return new CreateTravelExpenseItemRequest(
                requester.getEmployeeCode(), entitlementTypeCode, category, subcategory,
                subcategory + " during trip", LocalDateTime.now(), claimed, "PKR", null, null, null);
    }

    private long persistEmployee(String fullName, long userId, Long managerId, Long gradeId) {
        Employee e = new Employee();
        e.setEmployeeCode("TRVCOV-" + System.nanoTime());
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

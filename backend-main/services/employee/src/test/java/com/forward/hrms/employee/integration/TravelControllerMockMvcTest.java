package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelAdvanceRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelFuelPolicyRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelLegRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelParticipantRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyGradeLimitRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyVersionRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelPolicyVersionResponse;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelRequest;
import com.forward.hrms.employee.dto.request.travel.request.TransportVerifyRequest;
import com.forward.hrms.employee.enums.travel.TransportDecision;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.travel.TravelAdvanceType;
import com.forward.hrms.employee.enums.travel.TravelLimitType;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.travel.TravelFuelPolicyRepository;
import com.forward.hrms.employee.service.travel.TravelFuelPolicyService;
import com.forward.hrms.employee.service.travel.TravelPolicyService;
import com.forward.hrms.employee.service.travel.TravelRequestService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer coverage for the travel endpoints: routing, the
 * {@code @RequirePermission} gates + approval-chain {@code canAct}
 * authority, {@code @RequestBody(required=false)} bodies, and the
 * {@code BusinessError → RFC 7807} status mapping. Runs the real
 * {@code DispatcherServlet} + security filter chain (identity comes from
 * trusted {@code X-User-*} headers, as it does behind the gateway) against
 * a real Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithTestcontainers
class TravelControllerMockMvcTest {

    private static final AtomicLong USER_ID_SEQ = new AtomicLong(System.nanoTime() & 0x7fffffffL);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EmployeeRepository employees;
    @Autowired GradeRepository grades;
    @Autowired TravelPolicyService policyService;
    @Autowired TravelRequestService requestService;
    @Autowired TravelFuelPolicyService fuelPolicyService;
    @Autowired TravelFuelPolicyRepository fuelPolicyRepo;

    private Employee requester;
    private long managerUserId;

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @BeforeEach
    void setUp() {
        Grade g = new Grade();
        String gradeCode = "MVC-G-" + System.nanoTime();
        g.setCode(gradeCode);
        g.setName("MVC Test Grade");
        g.setLevel(1);
        Long gradeId = grades.save(g).getId();

        managerUserId = nextUserId();
        long managerEmployeeId = persistEmployee("MVC Manager " + managerUserId, managerUserId, null, null);
        long requesterUserId = nextUserId();
        persistEmployee("MVC Requester " + requesterUserId, requesterUserId, managerEmployeeId, gradeId);
        requester = employees.findByUserId(requesterUserId).orElseThrow();

        // Only a Manager/HOD may raise a travel request — give the requester a direct report.
        long reportUserId = nextUserId();
        persistEmployee("MVC Report " + reportUserId, reportUserId, requester.getId(), gradeId);

        CurrentUserHolder.set(caller(nextUserId(), "travel.admin"));
        var version = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                "LOCAL_FACTORY_TRAVEL", "Factory Travel Policy", LocalDate.now().minusMonths(1), null, "seed"));
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
        CurrentUserHolder.set(caller(nextUserId(), "travel.admin"));
        fuelPolicyService.create(new CreateTravelFuelPolicyRequest(
                vehicleSourceCode, "Test " + vehicleSourceCode + " Fuel Policy", new BigDecimal(fuelAverage),
                LocalDate.now().minusMonths(1), null, true));
        CurrentUserHolder.clear();
    }

    // ── auth / permission gates ──────────────────────────────────────────────

    @Test
    void anyEndpoint_withoutIdentityHeaders_isRejected() throws Exception {
        // No X-User-* headers → Spring Security's AuthorizationFilter denies before the controller.
        mvc.perform(get("/travel/requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRequest_withOnlyTravelRead_is403() throws Exception {
        mvc.perform(post("/travel/requests")
                        .headers(identity(requester.getUserId(), "travel.read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void createRequest_withTravelWrite_is201_chainNotYetOpened() throws Exception {
        mvc.perform(post("/travel/requests")
                        .headers(identity(requester.getUserId(), "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.requestNo").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                // root-level requester fields derived from the REQUESTER participant snapshot
                .andExpect(jsonPath("$.requesterEmployeeCode").value(requester.getEmployeeCode()))
                .andExpect(jsonPath("$.requesterEmployeeName").value(requester.getFullName()))
                .andExpect(jsonPath("$.participants[?(@.participantRole=='REQUESTER')].employeeCode")
                        .value(hasItem(requester.getEmployeeCode())))
                .andExpect(jsonPath("$.currentStage").isEmpty())
                .andExpect(jsonPath("$.steps", hasSize(0)))
                // no chain yet, but the owner holding travel.write can cancel a DRAFT
                .andExpect(jsonPath("$.actions", hasItem("CANCEL")));
    }

    @Test
    void createPolicy_withoutTravelAdmin_is403() throws Exception {
        // The unsupported policyCode is deliberate: @RequirePermission runs
        // before the handler body, so this must still be 403 and never the
        // 422 the policy-code guard would raise.
        mvc.perform(post("/travel/policies")
                        .headers(identity(nextUserId(), "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateTravelPolicyVersionRequest(
                                "OTHER_TRAVEL", "Other", LocalDate.now(), null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPolicy_withUnsupportedPolicyCode_is422() throws Exception {
        mvc.perform(post("/travel/policies")
                        .headers(identity(nextUserId(), "travel.admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateTravelPolicyVersionRequest(
                                "FACTORY_TRAVEL", "Obsolete family", LocalDate.now(), null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRAVEL_POLICY_CODE_UNSUPPORTED"));
    }

    @Test
    void createPolicy_withTravelAdmin_is201() throws Exception {
        mvc.perform(post("/travel/policies")
                        .headers(identity(nextUserId(), "travel.admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateTravelPolicyVersionRequest(
                                "LOCAL_FACTORY_TRAVEL", "Other Travel Policy", LocalDate.now(), null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void advanceMarkPaid_withoutFinancePermission_is403_beforeReachingTheService() throws Exception {
        // The @RequirePermission aspect runs before the handler body, so a bogus id still yields 403.
        mvc.perform(post("/travel/advances/999999/mark-paid")
                        .headers(identity(nextUserId(), "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void settlementSettle_withoutFinancePermission_is403() throws Exception {
        // travel.settlement.approve (the HR stage) is not travel.settlement.paid (Accounts) — still 403.
        mvc.perform(post("/travel/settlements/999999/settle")
                        .headers(identity(nextUserId(), "travel.write", "travel.settlement.approve"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── BusinessError → RFC 7807 status mapping ──────────────────────────────

    @Test
    void getRequest_unknownId_is404_withProblemCode() throws Exception {
        mvc.perform(get("/travel/requests/999999999")
                        .headers(identity(nextUserId(), "travel.read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_REQUEST_NOT_FOUND"));
    }

    @Test
    void createRequest_endBeforeStart_is422() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(10);
        CreateTravelRequest bad = new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, start.minusDays(1),
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(leg(start)));
        mvc.perform(post("/travel/requests")
                        .headers(identity(requester.getUserId(), "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void createRequest_blankPurpose_is400_withViolations() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(10);
        CreateTravelRequest bad = new CreateTravelRequest(
                "LOCAL", "PKR", "   ", start, start.plusDays(1),
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(leg(start)));
        mvc.perform(post("/travel/requests")
                        .headers(identity(requester.getUserId(), "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void raiseAdvance_whileTripIsDraft_is409() throws Exception {
        Long tripId = seedDraftTrip();
        mvc.perform(post("/travel/requests/" + tripId + "/advances")
                        .headers(identity(requester.getUserId(), "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateTravelAdvanceRequest(
                                requester.getEmployeeCode(), TravelAdvanceType.INITIAL,
                                new BigDecimal("1000.00"), "PKR", "too early", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").exists());
    }

    // ── routing ─────────────────────────────────────────────────────────────

    @Test
    void pendingApprovalsLiteralPath_resolvesToTheInbox_notThePathVariableHandler() throws Exception {
        // Gated on travel.write (the stage-1 manager holds no travel.approve).
        mvc.perform(get("/travel/requests/pending-approvals")
                        .headers(identity(nextUserId(), "travel.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createRequest_byEmployeeWhoIsNotAManagerOrHod_is403() throws Exception {
        long leafUserId = nextUserId();
        persistEmployee("MVC Leaf " + leafUserId, leafUserId, requester.getId(), null);

        mvc.perform(post("/travel/requests")
                        .headers(identity(leafUserId, "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validCreateRequestFor(leafUserId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ── @RequestBody(required=false) + approval-chain authority ──────────────

    @Test
    void transportVerify_withoutTransportPermission_is403() throws Exception {
        Long tripId = seedSubmittedRequest();

        mvc.perform(post("/travel/requests/" + tripId + "/transport/verify")
                        .headers(identity(nextUserId(), "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transportVerify_byTransportPool_advancesToDdStage_is200() throws Exception {
        Long tripId = seedSubmittedRequest();
        Long legId = requestService.get(tripId).legs().get(0).id();

        mvc.perform(post("/travel/requests/" + tripId + "/transport/verify")
                        .headers(identity(nextUserId(), "travel.transport.verify"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"legs\":[{\"legId\":" + legId
                                + ",\"actualDistanceKm\":150.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DD_PENDING"))
                .andExpect(jsonPath("$.currentStage.stageNo").value(2));
    }

    @Test
    void approveDdStage_bySubmittersManager_withOnlyTravelWrite_andNoBody_is200() throws Exception {
        Long tripId = seedTransportApprovedRequest();

        // Manager holds only travel.write — DD/HOD authority comes from the chain (they are the
        // assignee), and the endpoint accepts an empty body. DD/HOD is the final stage.
        mvc.perform(post("/travel/requests/" + tripId + "/approve")
                        .headers(identity(managerUserId, "travel.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.currentStage").isEmpty());
    }

    @Test
    void approve_byAWriterWhoCannotActOnTheStage_is403() throws Exception {
        Long tripId = seedTransportApprovedRequest();

        mvc.perform(post("/travel/requests/" + tripId + "/approve")
                        .headers(identity(nextUserId(), "travel.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── /approvals/history covers the travel types ──────────────────────────

    @Test
    void approvalHistory_includesTravelRequestDecisions() throws Exception {
        seedApprovedRequest();

        LocalDate today = LocalDate.now();
        mvc.perform(get("/approvals/history")
                        .param("from", today.minusDays(1).toString())
                        .param("to", today.toString())
                        .headers(identity(managerUserId, "employee.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].type", hasItem("TRAVEL_REQUEST")));
    }

    // ──────────────────────────────────────────────────────────── helpers

    private Long seedDraftTrip() {
        CurrentUserHolder.set(caller(requester.getUserId()));
        Long id = requestService.create(validCreateRequest()).id();
        CurrentUserHolder.clear();
        return id;
    }

    private Long seedSubmittedRequest() {
        CurrentUserHolder.set(caller(requester.getUserId()));
        Long id = requestService.create(validCreateRequest()).id();
        requestService.submit(id);
        CurrentUserHolder.clear();
        return id;
    }

    /** create → submit → TRANSPORT approve; request is left at DD_PENDING. */
    private Long seedTransportApprovedRequest() {
        Long id = seedSubmittedRequest();
        CurrentUserHolder.set(caller(nextUserId(), "travel.transport.verify"));
        requestService.transportVerify(id, new TransportVerifyRequest(TransportDecision.APPROVE, null, null));
        CurrentUserHolder.clear();
        return id;
    }

    private void seedApprovedRequest() {
        Long id = seedTransportApprovedRequest();
        CurrentUserHolder.set(caller(managerUserId));
        requestService.approve(id, null);
        CurrentUserHolder.clear();
    }

    private CreateTravelRequest validCreateRequest() {
        return validCreateRequestFor(requester.getUserId());
    }

    private CreateTravelRequest validCreateRequestFor(long callerUserId) {
        String employeeCode = employees.findByUserId(callerUserId).orElseThrow().getEmployeeCode();
        LocalDateTime start = LocalDateTime.now().plusDays(10)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        return new CreateTravelRequest(
                "LOCAL", "PKR", "Vendor audit", start, start.plusDays(1).withHour(17),
                List.of(new CreateTravelParticipantRequest(employeeCode)),
                List.of(leg(start)));
    }

    private static CreateTravelLegRequest leg(LocalDateTime start) {
        return new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                "COMPANY_VEHICLE", "COMPANY", "LEB-1234", "Corolla", new BigDecimal("120.00"), null);
    }

    private long persistEmployee(String fullName, long userId, Long managerId, Long gradeId) {
        Employee e = new Employee();
        e.setEmployeeCode("MVC-" + System.nanoTime());
        e.setFullName(fullName);
        e.setUserId(userId);
        e.setManagerId(managerId);
        e.setGradeId(gradeId);
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employees.save(e).getId();
    }

    private static HttpHeaders identity(long userId, String... permissions) {
        HttpHeaders h = new HttpHeaders();
        h.add("X-User-Id", String.valueOf(userId));
        h.add("X-User-Name", "u" + userId);
        if (permissions.length > 0) {
            h.add("X-User-Permissions", String.join(",", permissions));
        }
        return h;
    }

    private static CurrentUser caller(long userId, String... permissions) {
        return new CurrentUser(userId, "u" + userId, Set.of(), Set.of(permissions));
    }

    private static long nextUserId() {
        return USER_ID_SEQ.incrementAndGet();
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

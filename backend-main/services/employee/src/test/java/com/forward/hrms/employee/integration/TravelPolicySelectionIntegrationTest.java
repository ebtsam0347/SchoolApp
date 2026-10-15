package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelLegRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelParticipantRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyGradeLimitRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyVersionRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelPolicyVersionResponse;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.travel.TravelLimitType;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.service.travel.TravelPolicyService;
import com.forward.hrms.employee.service.travel.TravelRequestService;

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
 * Travel policy selection and the one-ACTIVE-version-per-policy-code rule.
 *
 * <pre>
 * travelTypeCode -&gt; policyCode -&gt; the one ACTIVE version covering the trip
 *                -&gt; policy_version_id pinned on the request
 * </pre>
 *
 * <p>Covers the travel-type mapping (LOCAL / INTERNATIONAL), rejection of unknown
 * travel types, the no-applicable-policy and out-of-window cases, version
 * evolution with historical pinning, and the lifecycle guards that keep a
 * policy code down to a single ACTIVE version.
 */
@SpringBootTest
@WithTestcontainers
class TravelPolicySelectionIntegrationTest {

    private static final String LOCAL_POLICY = "LOCAL_FACTORY_TRAVEL";
    private static final String INTERNATIONAL_POLICY = "INTERNATIONAL_FACTORY_TRAVEL";

    // Testcontainers reuse persists the DB across classes; employees.user_id
    // is unique — never hardcode a userId literal.
    private static final AtomicLong USER_ID_SEQ = new AtomicLong((System.nanoTime() ^ 0x70115ec7L) & 0x7fffffffL);

    @Autowired TravelPolicyService policyService;
    @Autowired TravelRequestService requestService;
    @Autowired EmployeeRepository employees;
    @Autowired GradeRepository grades;

    private String gradeCode;
    private Employee requester;

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @BeforeEach
    void setUp() {
        Grade g = new Grade();
        gradeCode = "TRV-SEL-G-" + System.nanoTime();
        g.setCode(gradeCode);
        g.setName("Travel Selection Test Grade");
        g.setLevel(1);
        Long gradeId = grades.save(g).getId();

        long managerUserId = nextUserId();
        long managerEmployeeId = persistEmployee("Selection Manager " + managerUserId, managerUserId, null, null);
        long requesterUserId = nextUserId();
        persistEmployee("Selection Requester " + requesterUserId, requesterUserId, managerEmployeeId, gradeId);
        requester = employees.findByUserId(requesterUserId).orElseThrow();

        // Only a Manager/HOD may raise a travel request — give the requester a direct report.
        long reportUserId = nextUserId();
        persistEmployee("Selection Report " + reportUserId, reportUserId, requester.getId(), gradeId);
    }

    // ── A / B: travelTypeCode selects the policy family ──────────────────────

    @Test
    void localTravelType_resolvesTheActiveLocalFactoryTravelVersion() {
        TravelPolicyVersionResponse local = seedActivePolicy(LOCAL_POLICY, "1500.00", LocalDate.now().minusMonths(1));
        // A second family is ACTIVE at the same time: the one-ACTIVE rule is
        // per policy code, so this must not influence the LOCAL selection.
        seedActivePolicy(INTERNATIONAL_POLICY, "4500.00", LocalDate.now().minusMonths(1));

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(trip("LOCAL"));

        assertThat(created.policyVersionId()).isEqualTo(local.id());
        assertThat(policyService.getPolicyVersion(created.policyVersionId()).policyCode())
                .isEqualTo(LOCAL_POLICY);
    }

    @Test
    void internalTravelType_resolvesTheActiveInternalFactoryTravelVersion() {
        seedActivePolicy(LOCAL_POLICY, "1500.00", LocalDate.now().minusMonths(1));
        TravelPolicyVersionResponse international =
                seedActivePolicy(INTERNATIONAL_POLICY, "4500.00", LocalDate.now().minusMonths(1));

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse created = requestService.create(trip("INTERNATIONAL"));

        assertThat(created.policyVersionId()).isEqualTo(international.id());
        assertThat(policyService.getPolicyVersion(created.policyVersionId()).policyCode())
                .isEqualTo(INTERNATIONAL_POLICY);
    }

    // ── C: unsupported travel type ───────────────────────────────────────────

    @Test
    void unknownTravelType_isRejected_ratherThanResolvingAnArbitraryPolicy() {
        seedActivePolicy(LOCAL_POLICY, "1500.00", LocalDate.now().minusMonths(1));

        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> requestService.create(trip("FACTORY_VISIT")))
                .isInstanceOf(UnprocessableError.class)
                .hasMessageContaining("unknown travel type code: FACTORY_VISIT");
    }

    // ── D: no ACTIVE policy at all ───────────────────────────────────────────

    @Test
    void noActiveVersionForTheFamily_yieldsNoApplicablePolicy() {
        retireActive(LOCAL_POLICY);

        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> requestService.create(trip("LOCAL")))
                .isInstanceOf(UnprocessableError.class)
                .hasMessageContaining("no ACTIVE '" + LOCAL_POLICY + "' policy version covers");
    }

    // ── E: ACTIVE, but its window misses the trip ────────────────────────────

    @Test
    void activeVersionNotCoveringTheTravelDate_yieldsNoApplicablePolicy_withNoFallback() {
        // Effective window already closed; an older RETIRED version must not
        // be picked up as a fallback.
        seedActivePolicy(LOCAL_POLICY, "1500.00", LocalDate.now().minusMonths(6), LocalDate.now().minusMonths(3));

        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> requestService.create(trip("LOCAL")))
                .isInstanceOf(UnprocessableError.class)
                .hasMessageContaining("no ACTIVE '" + LOCAL_POLICY + "' policy version covers");
    }

    @Test
    void draftVersionIsNeverSelected() {
        retireActive(LOCAL_POLICY);
        CurrentUserHolder.set(adminUser());
        policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                LOCAL_POLICY, "Draft only", LocalDate.now().minusMonths(1), null, "draft-only"));
        CurrentUserHolder.clear();

        CurrentUserHolder.set(requesterUser());
        assertThatThrownBy(() -> requestService.create(trip("LOCAL")))
                .isInstanceOf(UnprocessableError.class)
                .hasMessageContaining("no ACTIVE '" + LOCAL_POLICY + "' policy version covers");
    }

    // ── F: version evolution + historical pinning ────────────────────────────

    @Test
    void aNewerActiveVersion_appliesToNewRequestsOnly_theOlderRequestStaysPinned() {
        TravelPolicyVersionResponse vOld =
                seedActivePolicy(LOCAL_POLICY, "1500.00", LocalDate.now().minusMonths(1));

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse onOld = requestService.create(trip("LOCAL"));
        CurrentUserHolder.clear();
        assertThat(onOld.policyVersionId()).isEqualTo(vOld.id());

        // Roll the family forward: the live version is retired first, exactly
        // as the lifecycle requires — nothing is auto-retired for us.
        TravelPolicyVersionResponse vNew =
                seedActivePolicy(LOCAL_POLICY, "2500.00", LocalDate.now().minusMonths(1));
        assertThat(vNew.versionNo()).isGreaterThan(vOld.versionNo());
        assertThat(policyService.getPolicyVersion(vOld.id()).status())
                .isEqualTo(TravelPolicyVersionStatus.RETIRED);

        CurrentUserHolder.set(requesterUser());
        TravelRequestResponse onNew = requestService.create(trip("LOCAL"));
        CurrentUserHolder.clear();

        assertThat(onNew.policyVersionId()).isEqualTo(vNew.id());
        // The pre-existing request never re-resolves onto the newer version.
        assertThat(requestService.get(onOld.id()).policyVersionId()).isEqualTo(vOld.id());
    }

    // ── G: duplicate ACTIVE protection ───────────────────────────────────────

    @Test
    void activatingASecondVersionOfTheSameCode_isRejected() {
        seedActivePolicy(LOCAL_POLICY, "1500.00", LocalDate.now().minusMonths(1));

        CurrentUserHolder.set(adminUser());
        TravelPolicyVersionResponse rival = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                LOCAL_POLICY, "Rival", LocalDate.now().minusMonths(1), null, "rival"));

        assertThatThrownBy(() -> policyService.activatePolicyVersion(rival.id()))
                .isInstanceOf(ConflictError.class)
                .hasMessageContaining("an ACTIVE version already exists for policy code '" + LOCAL_POLICY + "'");

        // Refused activation must not have mutated either row.
        assertThat(policyService.getPolicyVersion(rival.id()).status())
                .isEqualTo(TravelPolicyVersionStatus.DRAFT);
        assertThat(activeVersionsOf(LOCAL_POLICY)).hasSize(1);
    }

    // ── H: DRAFT may coexist with ACTIVE ─────────────────────────────────────

    @Test
    void aDraftVersionMayCoexistWithTheActiveOne_andTheActiveOneKeepsBeingSelected() {
        TravelPolicyVersionResponse active =
                seedActivePolicy(LOCAL_POLICY, "1500.00", LocalDate.now().minusMonths(1));

        CurrentUserHolder.set(adminUser());
        TravelPolicyVersionResponse draft = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                LOCAL_POLICY, "Next", LocalDate.now().minusMonths(1), null, "next"));
        CurrentUserHolder.clear();

        assertThat(draft.status()).isEqualTo(TravelPolicyVersionStatus.DRAFT);
        assertThat(activeVersionsOf(LOCAL_POLICY)).hasSize(1);

        CurrentUserHolder.set(requesterUser());
        assertThat(requestService.create(trip("LOCAL")).policyVersionId()).isEqualTo(active.id());
    }

    // ── I: the rule is per policy code, not global ───────────────────────────

    @Test
    void bothFamiliesMayHoldTheirOwnActiveVersionSimultaneously() {
        seedActivePolicy(LOCAL_POLICY, "1500.00", LocalDate.now().minusMonths(1));
        seedActivePolicy(INTERNATIONAL_POLICY, "4500.00", LocalDate.now().minusMonths(1));

        assertThat(activeVersionsOf(LOCAL_POLICY)).hasSize(1);
        assertThat(activeVersionsOf(INTERNATIONAL_POLICY)).hasSize(1);
    }

    // ── K: only the two supported policy families may be created ─────────────

    @Test
    void unsupportedPolicyCode_cannotBeCreated() {
        CurrentUserHolder.set(adminUser());
        for (String unsupported : List.of(
                "FACTORY_TRAVEL",            // the retired pre-split family
                "INTERNAL_FACTORY_TRAVEL",   // never a TravelType mapping
                "LOCAL_FACTORY_TRAVELL",     // typo
                "OTHER_TRAVEL",
                "local_factory_travel")) {   // exact match only, no case folding
            assertThatThrownBy(() -> policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                    unsupported, "Unsupported", LocalDate.now(), null, null)))
                    .as("policyCode %s", unsupported)
                    .isInstanceOf(UnprocessableError.class)
                    .hasMessageContaining("unsupported travel policy code");
        }
        CurrentUserHolder.clear();
    }

    @Test
    void bothSupportedPolicyCodes_mayStillBeCreated() {
        CurrentUserHolder.set(adminUser());
        for (String supported : List.of(LOCAL_POLICY, INTERNATIONAL_POLICY)) {
            assertThat(policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                    supported, "Supported", LocalDate.now(), null, null)).policyCode())
                    .isEqualTo(supported);
        }
        CurrentUserHolder.clear();
    }

    /**
     * The create-time guard must not reach an existing version: historical
     * families (FACTORY_TRAVEL, INTERNAL_FACTORY_TRAVEL) still load, and
     * policyCode stays immutable on update.
     */
    @Test
    void existingVersions_areNotRevalidated_andPolicyCodeStaysImmutable() {
        CurrentUserHolder.set(adminUser());
        TravelPolicyVersionResponse v = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                LOCAL_POLICY, "Immutable code", LocalDate.now().minusMonths(1), null, "immutable"));

        // A payload carrying a different (even unsupported) policyCode is ignored, not applied.
        TravelPolicyVersionResponse updated = policyService.updatePolicyVersion(v.id(),
                new CreateTravelPolicyVersionRequest(
                        "FACTORY_TRAVEL", "Renamed", LocalDate.now().minusMonths(1), null, "immutable-2"));
        assertThat(updated.policyCode()).isEqualTo(LOCAL_POLICY);
        assertThat(updated.policyName()).isEqualTo("Renamed");
        assertThat(policyService.getPolicyVersion(v.id()).policyCode()).isEqualTo(LOCAL_POLICY);
        CurrentUserHolder.clear();
    }

    // ── J: existing lifecycle transitions still hold ─────────────────────────

    @Test
    void draftActiveInactiveRetiredTransitions_stillBehaveAsBefore() {
        retireActive(LOCAL_POLICY);
        CurrentUserHolder.set(adminUser());
        TravelPolicyVersionResponse v = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                LOCAL_POLICY, "Lifecycle", LocalDate.now().minusMonths(1), null, "lifecycle"));

        // DRAFT cannot be retired, and a non-DRAFT cannot be re-activated.
        assertThatThrownBy(() -> policyService.retirePolicyVersion(v.id()))
                .isInstanceOf(ConflictError.class);

        assertThat(policyService.activatePolicyVersion(v.id()).status())
                .isEqualTo(TravelPolicyVersionStatus.ACTIVE);
        assertThatThrownBy(() -> policyService.activatePolicyVersion(v.id()))
                .isInstanceOf(ConflictError.class)
                .hasMessageContaining("expected DRAFT");

        assertThat(policyService.deactivatePolicyVersion(v.id()).status())
                .isEqualTo(TravelPolicyVersionStatus.INACTIVE);
        assertThat(policyService.retirePolicyVersion(v.id()).status())
                .isEqualTo(TravelPolicyVersionStatus.RETIRED);

        // An INACTIVE/RETIRED sibling frees the code for the next version.
        TravelPolicyVersionResponse next = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                LOCAL_POLICY, "Lifecycle next", LocalDate.now().minusMonths(1), null, "lifecycle-next"));
        assertThat(policyService.activatePolicyVersion(next.id()).status())
                .isEqualTo(TravelPolicyVersionStatus.ACTIVE);
        CurrentUserHolder.clear();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Retires the live version of {@code policyCode}, then creates + activates a fresh one. */
    private TravelPolicyVersionResponse seedActivePolicy(String policyCode, String dinnerLimit, LocalDate from) {
        return seedActivePolicy(policyCode, dinnerLimit, from, null);
    }

    private TravelPolicyVersionResponse seedActivePolicy(String policyCode, String dinnerLimit,
                                                         LocalDate from, LocalDate to) {
        retireActive(policyCode);
        CurrentUserHolder.set(adminUser());
        TravelPolicyVersionResponse v = policyService.createPolicyVersion(new CreateTravelPolicyVersionRequest(
                policyCode, policyCode + " policy", from, to, "selection-test"));
        policyService.addGradeLimit(v.id(), new CreateTravelPolicyGradeLimitRequest(
                gradeCode, "DINNER", TravelLimitType.PER_DAY, new BigDecimal(dinnerLimit), "PKR", true, null));
        TravelPolicyVersionResponse activated = policyService.activatePolicyVersion(v.id());
        CurrentUserHolder.clear();
        return activated;
    }

    /**
     * Retires every ACTIVE version of {@code policyCode}. The module's Postgres
     * persists across classes, so a suite must not assume it starts clean.
     */
    private void retireActive(String policyCode) {
        CurrentUserHolder.set(adminUser());
        activeVersionsOf(policyCode).forEach(v -> policyService.retirePolicyVersion(v.id()));
        CurrentUserHolder.clear();
    }

    private List<TravelPolicyVersionResponse> activeVersionsOf(String policyCode) {
        return policyService.listVersionsOfPolicy(policyCode).stream()
                .filter(v -> v.status() == TravelPolicyVersionStatus.ACTIVE)
                .toList();
    }

    private CreateTravelRequest trip(String travelTypeCode) {
        LocalDateTime start = LocalDateTime.now().plusDays(10)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1).withHour(17);
        return new CreateTravelRequest(
                travelTypeCode, "PKR", "Policy selection trip", start, end,
                List.of(new CreateTravelParticipantRequest(requester.getEmployeeCode())),
                List.of(new CreateTravelLegRequest(1, "SIALKOT", "LAHORE", start, start.plusHours(4),
                        "COMPANY_VEHICLE", "COMPANY", "LEB-3333", "Corolla",
                        new BigDecimal("120.00"), null)));
    }

    private long persistEmployee(String fullName, long userId, Long managerId, Long gradeId) {
        Employee e = new Employee();
        e.setEmployeeCode("TRVSEL-" + System.nanoTime());
        e.setFullName(fullName);
        e.setUserId(userId);
        e.setManagerId(managerId);
        e.setGradeId(gradeId);
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employees.save(e).getId();
    }

    private CurrentUser requesterUser() {
        return new CurrentUser(requester.getUserId(), "u" + requester.getUserId(), Set.of(), Set.of());
    }

    private static CurrentUser adminUser() {
        long id = nextUserId();
        return new CurrentUser(id, "a" + id, Set.of(), Set.of("travel.admin"));
    }

    private static long nextUserId() {
        return USER_ID_SEQ.incrementAndGet();
    }
}

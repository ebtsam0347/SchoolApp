package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.AdjustPermanentAdvanceRequest;
import com.forward.hrms.payroll.dto.request.CreatePermanentAdvanceRequest;
import com.forward.hrms.payroll.dto.request.CreateShortTermAdvanceBatchRequest;
import com.forward.hrms.payroll.dto.request.ShortTermAdvanceLineInput;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceDto;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceInboxRow;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceInboxRow;
import com.forward.hrms.payroll.enums.AdvanceApprovalStage;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.service.GratuityService;
import com.forward.hrms.payroll.service.PermanentAdvanceService;
import com.forward.hrms.payroll.service.ShortTermAdvanceService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the merged (not bucketed) pending-approvals inbox for permanent
 * advances and short-term advance batches, across the 3-stage chain
 * (Manager → HR → Accounts): a caller holding only payroll.advance.hr sees
 * MANAGER_APPROVED rows tagged HR_APPROVAL, a caller holding only
 * payroll.advance.accounts sees HR_APPROVED rows tagged ACCOUNTS_APPROVAL,
 * and a caller holding both sees one combined list with both tags — no
 * separate buckets. Backed by the real commons-approval chain
 * (PERMANENT_ADVANCE / SHORT_TERM_ADVANCE) — seed() drives the chain
 * through the real service methods (create/managerApprove/hrApprove), same
 * as FactoryTourApprovalFlowIntegrationTest, rather than writing entity
 * status directly (which would leave no approval_steps rows behind it).
 * {@code systemActor}'s own userId doubles as the resolved manager for
 * every seeded row — {@code employeeClient.managerUserId(anyLong())} is
 * stubbed to always return it, so systemActor can drive each row through
 * its own Manager stage during seeding.
 */
@SpringBootTest
@WithTestcontainers
class AdvancePendingApprovalsIntegrationTest {

    // DB persists across test classes (Testcontainers reuse) — never
    // hardcode a request code / employee id literal.
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() & 0x7fffffffL);
    private static final long SYSTEM_ACTOR_USER_ID = 0L;

    @Autowired PermanentAdvanceService permanentAdvances;
    @Autowired ShortTermAdvanceService shortTermAdvances;
    @Autowired GratuityService gratuity;
    @Autowired EmployeePayrollSettingsRepository settingsRepo;
    @Autowired EmployeeSalaryRepository salaryRepo;

    @MockitoBean EmployeeClient employeeClient;
    // Short-term advance lines resolve employees through the display cache;
    // no employee-service to fall through to here, so every line gets a placeholder.
    @MockitoBean EmployeeDisplayCache displayCache;

    private static final java.time.LocalDate JOINING_DATE = java.time.LocalDate.of(2019, 3, 1);
    private static final BigDecimal BASIC = new BigDecimal("45000.00");

    /** employmentStatus served by the getDetails stub; tests flip it per employee id. */
    private final Map<Long, EmploymentStatus> statusOverrides = new java.util.HashMap<>();
    private final Set<Long> unknownEmployees = new java.util.HashSet<>();

    private long managerApprovedEmployeeId;
    private long hrApprovedEmployeeId;
    private long managerApprovedAdvanceId;
    private long hrApprovedAdvanceId;

    @BeforeEach
    void seed() {
        when(displayCache.getMany(any())).thenReturn(Map.of());
        // create() checks the employee is ACTIVE and snapshots their joining date.
        // Ids in `unknownEmployees` come back absent, as employee-service would.
        when(employeeClient.getDetails(any())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            Map<Long, EmployeeDto> out = new java.util.LinkedHashMap<>();
            for (Long id : ids) {
                if (unknownEmployees.contains(id)) continue;
                EmployeeDto e = mock(EmployeeDto.class);
                when(e.id()).thenReturn(id);
                when(e.employmentStatus()).thenReturn(statusOverrides.getOrDefault(id, EmploymentStatus.ACTIVE));
                when(e.joiningDate()).thenReturn(JOINING_DATE);
                out.put(id, e);
            }
            return out;
        });
        when(employeeClient.getMany(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            Map<Long, EmployeeClient.EmployeeBrief> out = new java.util.LinkedHashMap<>();
            for (Long id : ids) out.put(id, EmployeeClient.EmployeeBrief.placeholder(id));
            return out;
        });
        // Every row's Manager stage resolves to systemActor's own userId,
        // so seed() can drive each row past Manager approval itself.
        when(employeeClient.managerUserId(anyLong())).thenReturn(SYSTEM_ACTOR_USER_ID);

        managerApprovedEmployeeId = SEQ.incrementAndGet();
        hrApprovedEmployeeId = SEQ.incrementAndGet();

        CurrentUserHolder.set(systemActor());
        // Each row is a self-apply (/me is re-stubbed to the target before
        // each create), so it opens the normal chain at the Manager stage.
        filingAs(managerApprovedEmployeeId);
        managerApprovedAdvanceId = permanentAdvances.create(advanceRequest(managerApprovedEmployeeId)).id();
        permanentAdvances.managerApprove(managerApprovedAdvanceId, null);

        filingAs(hrApprovedEmployeeId);
        hrApprovedAdvanceId = permanentAdvances.create(advanceRequest(hrApprovedEmployeeId)).id();
        permanentAdvances.managerApprove(hrApprovedAdvanceId, null);
        permanentAdvances.hrApprove(hrApprovedAdvanceId, null);

        long managerApprovedBatchId = shortTermAdvances.create(batchRequest(filingAs(SEQ.incrementAndGet()))).id();
        shortTermAdvances.managerApprove(managerApprovedBatchId, null);

        long hrApprovedBatchId = shortTermAdvances.create(batchRequest(filingAs(SEQ.incrementAndGet()))).id();
        shortTermAdvances.managerApprove(hrApprovedBatchId, null);
        shortTermAdvances.hrApprove(hrApprovedBatchId, null);
        CurrentUserHolder.clear();
    }

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @Test
    void hrOnlyCaller_seesOnlyManagerApprovedRows_taggedHrApproval() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr")));

        PageResponse<PermanentAdvanceInboxRow> inbox =
                permanentAdvances.pendingApprovals(null, PageRequest.of(0, 20));

        assertThat(inbox.content()).allSatisfy(row -> {
            assertThat(row.request().status()).isEqualTo(AdvanceStatus.MANAGER_APPROVED);
            assertThat(row.approvalStage()).isEqualTo(AdvanceApprovalStage.HR_APPROVAL);
        });
        assertThat(inbox.content()).extracting(r -> r.request().employeeId())
                .contains(managerApprovedEmployeeId)
                .doesNotContain(hrApprovedEmployeeId);
    }

    @Test
    void accountsOnlyCaller_seesOnlyHrApprovedRows_taggedAccountsApproval() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.accounts")));

        PageResponse<PermanentAdvanceInboxRow> inbox =
                permanentAdvances.pendingApprovals(null, PageRequest.of(0, 20));

        assertThat(inbox.content()).allSatisfy(row -> {
            assertThat(row.request().status()).isEqualTo(AdvanceStatus.HR_APPROVED);
            assertThat(row.approvalStage()).isEqualTo(AdvanceApprovalStage.ACCOUNTS_APPROVAL);
        });
        assertThat(inbox.content()).extracting(r -> r.request().employeeId())
                .contains(hrApprovedEmployeeId)
                .doesNotContain(managerApprovedEmployeeId);
    }

    @Test
    void callerWithBothPermissions_seesOneMergedList_notSeparateBuckets() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr", "payroll.advance.accounts")));

        PageResponse<PermanentAdvanceInboxRow> inbox =
                permanentAdvances.pendingApprovals(null, PageRequest.of(0, 20));

        List<Long> employeeIds = inbox.content().stream()
                .map(r -> r.request().employeeId()).toList();
        assertThat(employeeIds).contains(managerApprovedEmployeeId, hrApprovedEmployeeId);

        AdvanceApprovalStage managerApprovedRowStage = inbox.content().stream()
                .filter(r -> r.request().employeeId() == managerApprovedEmployeeId)
                .findFirst().orElseThrow().approvalStage();
        AdvanceApprovalStage hrApprovedRowStage = inbox.content().stream()
                .filter(r -> r.request().employeeId() == hrApprovedEmployeeId)
                .findFirst().orElseThrow().approvalStage();
        assertThat(managerApprovedRowStage).isEqualTo(AdvanceApprovalStage.HR_APPROVAL);
        assertThat(hrApprovedRowStage).isEqualTo(AdvanceApprovalStage.ACCOUNTS_APPROVAL);
    }

    @Test
    void permanentAdvanceQ_matchesIdRequestCodeEmployeeCodeAndName() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr", "payroll.advance.accounts")));

        // Numeric q also substring-matches request / employee codes, and the
        // DB keeps earlier runs' rows — so assert the id hit, not exclusivity.
        assertThat(permanentAdvances.pendingApprovals(String.valueOf(hrApprovedAdvanceId), PageRequest.of(0, 200))
                .content()).extracting(r -> r.request().id()).contains(hrApprovedAdvanceId);

        String requestCode = permanentAdvances.get(managerApprovedAdvanceId).requestCode();
        assertThat(permanentAdvances.pendingApprovals(requestCode.toLowerCase(), PageRequest.of(0, 20))
                .content()).extracting(r -> r.request().id()).containsExactly(managerApprovedAdvanceId);

        // EmployeeBrief.placeholder: code "EMP-<id>", name "Employee <id>".
        assertThat(permanentAdvances.pendingApprovals("emp-" + managerApprovedEmployeeId, PageRequest.of(0, 20))
                .content()).extracting(r -> r.request().employeeId()).containsExactly(managerApprovedEmployeeId);
        assertThat(permanentAdvances.pendingApprovals("employee " + hrApprovedEmployeeId, PageRequest.of(0, 20))
                .content()).extracting(r -> r.request().employeeId()).containsExactly(hrApprovedEmployeeId);

        PageResponse<PermanentAdvanceInboxRow> none =
                permanentAdvances.pendingApprovals("no-such-advance", PageRequest.of(0, 20));
        assertThat(none.content()).isEmpty();
        assertThat(none.totalElements()).isZero();
    }

    @Test
    void shortTermAdvances_sameMergeBehaviorAsPermanentAdvances() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr", "payroll.advance.accounts")));

        PageResponse<ShortTermAdvanceInboxRow> inbox =
                shortTermAdvances.pendingApprovals(null, PageRequest.of(0, 20));

        assertThat(inbox.content()).extracting(r -> r.request().status())
                .contains(AdvanceStatus.MANAGER_APPROVED, AdvanceStatus.HR_APPROVED);
        assertThat(inbox.content()).extracting(ShortTermAdvanceInboxRow::approvalStage)
                .contains(AdvanceApprovalStage.HR_APPROVAL, AdvanceApprovalStage.ACCOUNTS_APPROVAL);
    }

    @Test
    void hrAdjust_atHrStage_changesAmounts_andKeepsStage() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr")));

        PermanentAdvanceDto adjusted = permanentAdvances.hrAdjustBeforeApproval(managerApprovedAdvanceId,
                new AdjustPermanentAdvanceRequest(new BigDecimal("40000.00"), new BigDecimal("4000.00"), "revised"));

        assertThat(adjusted.amount()).isEqualByComparingTo("40000.00");
        assertThat(adjusted.monthlyDeduction()).isEqualByComparingTo("4000.00");
        assertThat(adjusted.status()).isEqualTo(AdvanceStatus.MANAGER_APPROVED);
    }

    @Test
    void hrAdjust_refusedOnceHrHasApproved_andWhenInstallmentExceedsAmount() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr")));

        assertThatThrownBy(() -> permanentAdvances.hrAdjustBeforeApproval(hrApprovedAdvanceId,
                new AdjustPermanentAdvanceRequest(new BigDecimal("40000.00"), new BigDecimal("4000.00"), null)))
                .isInstanceOf(ConflictError.class);
        assertThatThrownBy(() -> permanentAdvances.hrAdjustBeforeApproval(managerApprovedAdvanceId,
                new AdjustPermanentAdvanceRequest(new BigDecimal("1000.00"), new BigDecimal("4000.00"), null)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void hrApproved_listsOnlyHrApproved_filteredByEmployee() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr")));

        assertThat(permanentAdvances.hrApprovedReceivingList(hrApprovedEmployeeId, PageRequest.of(0, 20)).content())
                .extracting(PermanentAdvanceDto::id).containsExactly(hrApprovedAdvanceId);
        assertThat(permanentAdvances.hrApprovedReceivingList(managerApprovedEmployeeId, PageRequest.of(0, 20)).content())
                .isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    @Test
    void create_snapshotsJoiningDate_andCarriesEmployeeSummary() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr")));

        PermanentAdvanceDto a = permanentAdvances.get(managerApprovedAdvanceId);
        assertThat(a.dateOfJoining()).isEqualTo(JOINING_DATE);
        // displayCache stub returns nothing, so the summary is the placeholder for that id.
        assertThat(a.employee()).isNotNull();
        assertThat(a.employee().id()).isEqualTo(managerApprovedEmployeeId);
        assertThat(a.employee().fullName()).isEqualTo("Employee " + managerApprovedEmployeeId);
    }

    @Test
    void create_snapshotsGratuityComputedServerSide() {
        CurrentUserHolder.set(caller(Set.of("payroll.advance.hr")));

        PermanentAdvanceDto a = permanentAdvances.get(managerApprovedAdvanceId);
        BigDecimal expected = gratuity.compute(managerApprovedEmployeeId, java.time.LocalDate.now()).gratuity();
        assertThat(expected).isPositive();
        assertThat(a.gratuityAtRequest()).isEqualByComparingTo(expected);
    }

    @Test
    void create_refusedForNonActiveOrUnknownEmployee() {
        CurrentUserHolder.set(systemActor());

        long onNotice = filingAs(SEQ.incrementAndGet());
        statusOverrides.put(onNotice, EmploymentStatus.ON_NOTICE);
        assertThatThrownBy(() -> permanentAdvances.create(advanceRequest(onNotice)))
                .isInstanceOf(UnprocessableError.class)
                .hasMessageContaining("ON_NOTICE");

        long unknown = filingAs(SEQ.incrementAndGet());
        unknownEmployees.add(unknown);
        assertThatThrownBy(() -> permanentAdvances.create(advanceRequest(unknown)))
                .isInstanceOf(UnprocessableError.class)
                .hasMessageContaining("not found");
    }

    private CreatePermanentAdvanceRequest advanceRequest(long employeeId) {
        seedPayroll(employeeId);
        return new CreatePermanentAdvanceRequest(employeeId,
                new BigDecimal("50000.00"), new BigDecimal("5000.00"),
                null, null, null, null, "2026-27", null, null);
    }

    /** create() snapshots gratuity, which needs payroll settings + a salary row. */
    private void seedPayroll(long employeeId) {
        if (settingsRepo.findByEmployeeId(employeeId).isPresent()) return;
        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setHireDate(JOINING_DATE);
        s.setEobiEnrolled(false);
        settingsRepo.save(s);
        EmployeeSalary sal = new EmployeeSalary();
        sal.setEmployeeId(employeeId);
        sal.setBasic(BASIC);
        sal.setEffectiveFrom(JOINING_DATE);
        salaryRepo.save(sal);
    }

    /**
     * Stubs /me to {@code employeeId}, so the next create is a self-apply
     * whose Manager stage resolves (via {@code PayrollManagerResolver}) to systemActor.
     */
    private long filingAs(long employeeId) {
        when(employeeClient.meEmployeeIdOrNull()).thenReturn(employeeId);
        when(employeeClient.me()).thenReturn(new EmployeeClient.Me(employeeId, SYSTEM_ACTOR_USER_ID));
        return employeeId;
    }

    private CreateShortTermAdvanceBatchRequest batchRequest(long employeeId) {
        return new CreateShortTermAdvanceBatchRequest(null, null,
                java.time.Year.now().getValue() + 1, 1, null,
                List.of(new ShortTermAdvanceLineInput(employeeId, new BigDecimal("10000.00"))));
    }

    private CurrentUser systemActor() {
        return new CurrentUser(SYSTEM_ACTOR_USER_ID, "system", Set.of(),
                Set.of("payroll.advance.hr", "payroll.advance.accounts"));
    }

    private CurrentUser caller(Set<String> permissions) {
        return new CurrentUser(System.nanoTime() & 0x7fffffffL, "advance-reviewer", Set.of(), permissions);
    }
}

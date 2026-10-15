package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.commons.approval.autoconfig.ApprovalPermissions;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the on-behalf authority matrix — the code that decides who may raise
 * a request for someone else, and whether doing so counts as an approval. A
 * regression here is silent privilege escalation, so every branch gets a
 * case, including the two negative-information ones: an unauthorized caller
 * must not be able to tell a nonexistent employee id from one they simply
 * have no authority over, nor read a target's employment status off a 422.
 */
class OnBehalfResolverTest {

    private static final String OVERRIDE = "attendance.approval.override";
    private static final String TEAM_GEN = OnBehalfResolver.TEAM_GEN_LEAVE;

    private static final long CALLER_EMP = 100L;
    private static final long TARGET_EMP = 200L;
    private static final long CALLER_DEPT = 7L;
    private static final long OTHER_DEPT = 8L;
    private static final long OTHER_MANAGER = 999L;

    private EmployeeClient employees;
    private CallerContext caller;
    private OnBehalfResolver resolver;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeClient.class);
        caller = mock(CallerContext.class);
        resolver = new OnBehalfResolver(employees, caller, new ApprovalPermissions(
                "attendance.read", "attendance.write", "attendance.admin", OVERRIDE));
        when(caller.currentEmployee())
                .thenReturn(Optional.of(employee(CALLER_EMP, CALLER_DEPT, null, "ACTIVE")));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    // ── SELF ──────────────────────────────────────────────────────────────

    @Test
    void nullOnBehalfOfIsSelf() {
        assertThat(resolver.resolve(CALLER_EMP, null, TEAM_GEN).mode())
                .isEqualTo(OnBehalfResolver.Mode.SELF);
    }

    @Test
    void onBehalfOfSelfIsSelf() {
        assertThat(resolver.resolve(CALLER_EMP, CALLER_EMP, TEAM_GEN).mode())
                .isEqualTo(OnBehalfResolver.Mode.SELF);
    }

    // ── the three on-behalf authorities ───────────────────────────────────

    @Test
    void overrideHolderGetsHrOverride() {
        login(Set.of(OVERRIDE));
        target(TARGET_EMP, OTHER_DEPT, OTHER_MANAGER, "ACTIVE");

        OnBehalfResolver.Resolution r = resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN);

        assertThat(r.mode()).isEqualTo(OnBehalfResolver.Mode.HR_OVERRIDE);
        assertThat(r.autoApproves()).isTrue();
    }

    @Test
    void directManagerGetsManagerOnBehalf() {
        login(Set.of());
        target(TARGET_EMP, OTHER_DEPT, CALLER_EMP, "ACTIVE");

        OnBehalfResolver.Resolution r = resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN);

        assertThat(r.mode()).isEqualTo(OnBehalfResolver.Mode.MANAGER_ON_BEHALF);
        assertThat(r.autoApproves()).isTrue();
    }

    @Test
    void teamGeneratorInSameDepartmentNeverAutoApproves() {
        login(Set.of(TEAM_GEN));
        target(TARGET_EMP, CALLER_DEPT, OTHER_MANAGER, "ACTIVE");

        OnBehalfResolver.Resolution r = resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN);

        assertThat(r.mode()).isEqualTo(OnBehalfResolver.Mode.TEAM_ON_BEHALF);
        assertThat(r.targetEmployeeId()).isEqualTo(TARGET_EMP);
        assertThat(r.isOnBehalf()).isTrue();
        assertThat(r.autoApproves()).isFalse();
    }

    // ── which manager the chain anchors on ────────────────────────────────

    @Test
    void teamGeneratorAnchorsTheChainOnTheirOwnManagerNotTheTargets() {
        login(Set.of(TEAM_GEN));
        target(TARGET_EMP, CALLER_DEPT, OTHER_MANAGER, "ACTIVE");

        OnBehalfResolver.Resolution r = resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN);

        assertThat(r.approvalAnchorEmployeeId())
                .as("a clerk's submissions go to the department head they support")
                .isEqualTo(CALLER_EMP);
        assertThat(r.targetEmployeeId()).isEqualTo(TARGET_EMP);
    }

    @Test
    void everyOtherModeAnchorsOnTheTarget() {
        login(Set.of(OVERRIDE));
        target(TARGET_EMP, OTHER_DEPT, OTHER_MANAGER, "ACTIVE");
        assertThat(resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN).approvalAnchorEmployeeId())
                .isEqualTo(TARGET_EMP);

        login(Set.of());
        target(TARGET_EMP, OTHER_DEPT, CALLER_EMP, "ACTIVE");
        assertThat(resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN).approvalAnchorEmployeeId())
                .isEqualTo(TARGET_EMP);
    }

    @Test
    void selfSubmissionAnchorsOnTheCaller() {
        OnBehalfResolver.Resolution r = resolver.resolve(CALLER_EMP, null, TEAM_GEN);

        assertThat(r.approvalAnchorEmployeeId()).isEqualTo(CALLER_EMP);
        assertThat(r.targetEmployeeId()).isEqualTo(CALLER_EMP);
    }

    @Test
    void managerAuthorityWinsWhenBothApply() {
        login(Set.of(TEAM_GEN));
        target(TARGET_EMP, CALLER_DEPT, CALLER_EMP, "ACTIVE");

        assertThat(resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN).mode())
                .isEqualTo(OnBehalfResolver.Mode.MANAGER_ON_BEHALF);
    }

    // ── denials ───────────────────────────────────────────────────────────

    @Test
    void teamGeneratorOutsideOwnDepartmentIsForbidden() {
        login(Set.of(TEAM_GEN));
        target(TARGET_EMP, OTHER_DEPT, OTHER_MANAGER, "ACTIVE");

        assertForbidden(TEAM_GEN);
    }

    @Test
    void teamGenerationPermissionIsPerRequestType() {
        login(Set.of(OnBehalfResolver.TEAM_GEN_OVERTIME));
        target(TARGET_EMP, CALLER_DEPT, OTHER_MANAGER, "ACTIVE");

        assertForbidden(OnBehalfResolver.TEAM_GEN_LEAVE);
    }

    @Test
    void nullDepartmentsNeverMatchEachOther() {
        login(Set.of(TEAM_GEN));
        when(caller.currentEmployee())
                .thenReturn(Optional.of(employee(CALLER_EMP, null, null, "ACTIVE")));
        target(TARGET_EMP, null, OTHER_MANAGER, "ACTIVE");

        assertForbidden(TEAM_GEN);
    }

    @Test
    void plainEmployeeIsForbidden() {
        login(Set.of("attendance.read", "attendance.write"));
        target(TARGET_EMP, CALLER_DEPT, OTHER_MANAGER, "ACTIVE");

        assertForbidden(TEAM_GEN);
    }

    // ── negative information must not leak ────────────────────────────────

    @Test
    void unknownIdLooksExactlyLikeAnUnauthorizedOneToANonOverrideCaller() {
        login(Set.of(TEAM_GEN));
        when(employees.getByIdOptional(anyLong())).thenReturn(Optional.empty());

        assertForbidden(TEAM_GEN);
    }

    @Test
    void unknownIdIsNotFoundForOverrideHolders() {
        login(Set.of(OVERRIDE));
        when(employees.getByIdOptional(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void inactiveTargetDoesNotLeakItsStatusToAnUnauthorizedCaller() {
        login(Set.of(TEAM_GEN));
        target(TARGET_EMP, OTHER_DEPT, OTHER_MANAGER, "TERMINATED");

        assertForbidden(TEAM_GEN);
    }

    @Test
    void inactiveTargetIsRejectedOnceAuthorityIsEstablished() {
        login(Set.of(TEAM_GEN));
        target(TARGET_EMP, CALLER_DEPT, OTHER_MANAGER, "TERMINATED");

        assertThatThrownBy(() -> resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN))
                .isInstanceOf(UnprocessableError.class);
    }

    @Test
    void onNoticeTargetIsStillAllowed() {
        login(Set.of(TEAM_GEN));
        target(TARGET_EMP, CALLER_DEPT, OTHER_MANAGER, "ON_NOTICE");

        assertThat(resolver.resolve(CALLER_EMP, TARGET_EMP, TEAM_GEN).mode())
                .isEqualTo(OnBehalfResolver.Mode.TEAM_ON_BEHALF);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void login(Set<String> permissions) {
        CurrentUser user = new CurrentUser(55L, "clerk", "Clerk", 1L, Set.of(), permissions);
        CurrentUserHolder.set(user);
        when(caller.requireUser()).thenReturn(user);
    }

    private void target(long id, Long departmentId, Long managerId, String status) {
        when(employees.getByIdOptional(id))
                .thenReturn(Optional.of(employee(id, departmentId, managerId, status)));
    }

    private void assertForbidden(String teamGenerationPermission) {
        assertThatThrownBy(() -> resolver.resolve(CALLER_EMP, TARGET_EMP, teamGenerationPermission))
                .isInstanceOf(ForbiddenError.class);
    }

    private static EmployeeClient.EmployeeView employee(long id, Long departmentId,
                                                        Long managerId, String status) {
        return new EmployeeClient.EmployeeView(
                id, id + 1000, "FS-" + id, "Employee " + id,
                departmentId, null, managerId, managerId == null ? null : managerId + 1000,
                status, LocalDate.of(2020, 1, 1));
    }
}

package com.forward.hrms.attendance.approval;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.service.CallerContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 1 of every attendance approval chain is a MANAGER-resolver stage.
 * This class turns the anchor employee id that {@code openChain} was given
 * into that employee's manager <em>user</em> id — it does not decide which
 * employee to anchor on; {@code OnBehalfResolver.approvalAnchorEmployeeId()}
 * does that. What matters here is that it resolves whichever id it is handed
 * rather than falling back to the caller, and that the self path stays free
 * of an upstream round-trip.
 */
class AttendanceManagerResolverTest {

    private static final long CALLER_EMP = 100L;
    private static final long CALLER_MANAGER_USER = 1100L;
    private static final long TARGET_EMP = 200L;
    private static final long TARGET_MANAGER_USER = 1200L;

    private EmployeeClient employees;
    private CallerContext caller;
    private AttendanceManagerResolver resolver;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeClient.class);
        caller = mock(CallerContext.class);
        resolver = new AttendanceManagerResolver(employees, caller);
        when(caller.currentEmployee())
                .thenReturn(Optional.of(employee(CALLER_EMP, CALLER_MANAGER_USER)));
    }

    @Test
    void selfSubmissionUsesTheCachedCallerRowAndMakesNoUpstreamCall() {
        assertThat(resolver.resolveManagerUserId(CALLER_EMP)).isEqualTo(CALLER_MANAGER_USER);
        verify(employees, never()).getById(anyLong());
    }

    @Test
    void resolvesTheManagerOfTheAnchorEmployeeNotTheCaller() {
        when(employees.getById(TARGET_EMP)).thenReturn(employee(TARGET_EMP, TARGET_MANAGER_USER));

        assertThat(resolver.resolveManagerUserId(TARGET_EMP))
                .isEqualTo(TARGET_MANAGER_USER)
                .isNotEqualTo(CALLER_MANAGER_USER);
    }

    @Test
    void targetWithNoManagerResolvesToNoAssignee() {
        when(employees.getById(TARGET_EMP)).thenReturn(employee(TARGET_EMP, null));

        assertThat(resolver.resolveManagerUserId(TARGET_EMP)).isNull();
    }

    private static EmployeeClient.EmployeeView employee(long id, Long managerUserId) {
        return new EmployeeClient.EmployeeView(
                id, id + 1000, "FS-" + id, "Employee " + id,
                7L, null, managerUserId == null ? null : managerUserId - 1000, managerUserId,
                "ACTIVE", LocalDate.of(2020, 1, 1));
    }
}

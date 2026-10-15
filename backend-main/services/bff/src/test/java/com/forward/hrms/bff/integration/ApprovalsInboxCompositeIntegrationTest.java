package com.forward.hrms.bff.integration;

import com.forward.hrms.attendance.dto.response.ApprovalsInbox;
import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.bff.client.PayrollClient;
import com.forward.hrms.bff.client.dto.PageView;
import com.forward.hrms.bff.dto.response.CombinedApprovalsInbox;
import com.forward.hrms.bff.service.ApprovalsInboxCompositeService;
import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.dto.response.FactoryToursInbox;
import com.forward.hrms.employee.dto.response.RequisitionDto;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.employee.enums.RequisitionStatus;
import com.forward.hrms.employee.enums.RequisitionType;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceDto;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceInboxRow;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceBatchDto;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceInboxRow;
import com.forward.hrms.payroll.enums.AdvanceApprovalStage;
import com.forward.hrms.payroll.enums.AdvanceStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * BFF composite contract: {@code GET /bff/approvals/inbox}. Downstream
 * clients + the employee-display cache are mocked so we test the fan-out
 * / merge / partial-failure logic, not transport. Asserts the merged shape
 * and — the point of this composite — that one source erroring surfaces as
 * an empty bucket + a name in erroredSources rather than failing the call.
 */
@SpringBootTest(properties = "hrms.outbox.enabled=false")
@WithTestcontainers
class ApprovalsInboxCompositeIntegrationTest {

    @Autowired ApprovalsInboxCompositeService composite;

    @MockitoBean AttendanceClient attendanceClient;
    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean PayrollClient payrollClient;
    @MockitoBean EmployeeDisplayCache displayCache;

    @BeforeEach
    void setIdentity() {
        CurrentUserHolder.set(new CurrentUser(1L, "approver", 1L, Set.of("MANAGER"), Set.of()));
    }

    @AfterEach
    void clearIdentity() {
        CurrentUserHolder.clear();
    }

    @Test
    void allSourcesHealthy_mergesEveryBucket_withEmployeeEnrichedAdvances() {
        when(attendanceClient.approvalsInbox(null, null, null, null, null, null)).thenReturn(
                new ApprovalsInbox(List.of(), List.of(), List.of(), List.of(),
                        new ApprovalsInbox.Counts(0, 0, 0, 0, 0)));

        RequisitionDto requisition = requisitionDto(501L);
        when(employeeClient.requisitionsPending(anyInt(), any(), any(), any()))
                .thenReturn(new PageView<>(List.of(requisition), 0, 200, 1L, 1, null));
        when(employeeClient.factoryToursPending())
                .thenReturn(new FactoryToursInbox(List.of(), new FactoryToursInbox.Counts(0, 0)));
        TravelRequestResponse travelRequest = travelRequestResponse(901L, "TRANSPORT_PENDING");
        when(employeeClient.travelRequestsPending(anyInt(), any(), any(), any(), any()))
                .thenReturn(new PageView<>(List.of(travelRequest), 0, 200, 1L, 1, null));

        PermanentAdvanceDto advance = permanentAdvanceDto(701L, 88L, AdvanceStatus.PENDING);
        when(payrollClient.permanentAdvancesPending(anyInt(), any())).thenReturn(new PageView<>(
                List.of(new PermanentAdvanceInboxRow(advance, AdvanceApprovalStage.HR_APPROVAL)),
                0, 200, 1L, 1, null));
        when(payrollClient.shortTermAdvancesPending(anyInt(), any()))
                .thenReturn(new PageView<>(List.of(), 0, 200, 0L, 0, null));

        when(displayCache.getMany(any())).thenReturn(java.util.Map.of(
                88L, new EmployeeDisplay(88L, "FS-0088", "Card No Holder",
                        null, null, null, null, null, null, null)));

        CombinedApprovalsInbox result = composite.build(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result.requisitions()).extracting(RequisitionDto::id).containsExactly(501L);
        assertThat(result.travelRequests()).extracting(TravelRequestResponse::id).containsExactly(901L);
        assertThat(result.travelRequests().get(0).status()).isEqualTo("TRANSPORT_PENDING");
        assertThat(result.permanentAdvances()).hasSize(1);
        assertThat(result.permanentAdvances().get(0).approvalStage()).isEqualTo(AdvanceApprovalStage.HR_APPROVAL);
        assertThat(result.permanentAdvances().get(0).employee()).isNotNull();
        assertThat(result.permanentAdvances().get(0).employee().fullName()).isEqualTo("Card No Holder");
        assertThat(result.shortTermAdvances()).isEmpty();
        assertThat(result.counts().requisitions()).isEqualTo(1);
        assertThat(result.counts().travelRequests()).isEqualTo(1);
        assertThat(result.counts().permanentAdvances()).isEqualTo(1);
        assertThat(result.counts().total()).isEqualTo(3);
        assertThat(result.erroredSources()).isEmpty();
    }

    @Test
    void oneSourceErrors_restStillReturned_erroredSourceFlagged_notWholeCallFailed() {
        when(attendanceClient.approvalsInbox(null, null, null, null, null, null)).thenReturn(
                new ApprovalsInbox(List.of(), List.of(), List.of(), List.of(),
                        new ApprovalsInbox.Counts(0, 0, 0, 0, 0)));
        when(employeeClient.requisitionsPending(anyInt(), any(), any(), any()))
                .thenReturn(new PageView<>(List.of(requisitionDto(9L)), 0, 200, 1L, 1, null));
        when(employeeClient.factoryToursPending())
                .thenReturn(new FactoryToursInbox(List.of(), new FactoryToursInbox.Counts(0, 0)));
        when(employeeClient.travelRequestsPending(anyInt(), any(), any(), any(), any()))
                .thenReturn(new PageView<>(List.of(), 0, 200, 0L, 0, null));
        // Payroll is down for permanent advances specifically.
        when(payrollClient.permanentAdvancesPending(anyInt(), any()))
                .thenThrow(new RuntimeException("payroll-service unreachable"));
        when(payrollClient.shortTermAdvancesPending(anyInt(), any()))
                .thenReturn(new PageView<>(List.of(), 0, 200, 0L, 0, null));
        when(displayCache.getMany(any())).thenReturn(java.util.Map.of());

        CombinedApprovalsInbox result = composite.build(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result.requisitions()).hasSize(1);
        assertThat(result.permanentAdvances()).isEmpty();
        assertThat(result.erroredSources()).containsExactly("permanent-advances");
        assertThat(result.counts().permanentAdvances()).isZero();
        assertThat(result.counts().requisitions()).isEqualTo(1);
    }

    private static RequisitionDto requisitionDto(long id) {
        return new RequisitionDto(id, RequisitionType.NEW_HIRE, RequisitionStatus.PENDING_APPROVAL,
                10L, "Metric", 20L, "Java", "panel cutting", 1L,
                0L, 5L, 5L,"backfill",
                "need urgently", 5L, 5L, null, null, null,
                LocalDateTime.now(), "5", null,
                List.of(), List.of());
    }

    private static TravelRequestResponse travelRequestResponse(long id, String status) {
        return new TravelRequestResponse(id, "TR-2026-" + id, "FS-0099", "Transport Test Employee",
                "FACTORY_VISIT", "PKR", "site visit",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                List.of(), null, null, status, "TRAVEL_REQUEST#" + id, BigDecimal.ZERO,
                null, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDateTime.now(), null, null, null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(), 0L,
                null, List.of(), List.of());
    }

    private static PermanentAdvanceDto permanentAdvanceDto(long id, long employeeId, AdvanceStatus status) {
        return new PermanentAdvanceDto(id, "PA-" + id, employeeId, null, null,
                new BigDecimal("50000.00"), new BigDecimal("5000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, status,
                BigDecimal.ZERO, new BigDecimal("50000.00"),
                null, null, null, null, null, null, null,
                null, null, null, LocalDateTime.now(), null,
                null, List.of(), List.of(),
                null, BigDecimal.ZERO, null);
    }
}

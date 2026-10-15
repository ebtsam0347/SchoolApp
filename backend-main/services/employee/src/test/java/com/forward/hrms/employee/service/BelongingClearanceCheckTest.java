package com.forward.hrms.employee.service;

import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.response.BelongingClearanceCheckDto;
import com.forward.hrms.employee.entity.BelongingIssuance;
import com.forward.hrms.employee.entity.BelongingMaster;
import com.forward.hrms.employee.enums.belonging.ActivationStatus;
import com.forward.hrms.employee.enums.belonging.ClearanceStatus;
import com.forward.hrms.employee.repository.BelongingIssuanceRepository;
import com.forward.hrms.employee.repository.BelongingMasterRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The termination clearance verdict payroll-service gates HR approval on.
 *
 * <p>Two things are asserted, and the first is the one that matters: that
 * the <em>filter</em> is pushed into the repository query rather than
 * applied in Java. Everything the service receives is by definition
 * blocking, so a row leaking through the query would be reported as
 * blocking even if it were RETURNED — which is why the query is spelled out
 * in {@code findBlockingClearance} and asserted here by the arguments it is
 * called with.
 *
 * <p>Second: a settlement for an employee holding nothing — the common case
 * — must come back {@code cleared} with an empty list and never a null one,
 * because payroll puts that list straight into a 409 ProblemDetail.
 */
class BelongingClearanceCheckTest {

    private final BelongingIssuanceRepository issuances = mock(BelongingIssuanceRepository.class);
    private final BelongingMasterRepository masters = mock(BelongingMasterRepository.class);
    private final EmployeeService employees = mock(EmployeeService.class);
    private final AuditEmitter audit = mock(AuditEmitter.class);

    private final BelongingIssuanceService service =
            new BelongingIssuanceService(issuances, masters, employees, audit);

    @Test
    void employeeHoldingNothingIsCleared() {
        when(issuances.findBlockingClearance(eq(7L), any(), any())).thenReturn(List.of());

        BelongingClearanceCheckDto result = service.clearanceCheck(7L);

        assertThat(result.cleared()).isTrue();
        assertThat(result.pendingCount()).isZero();
        assertThat(result.pendingAssetValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.pending())
                .as("payroll puts this list in a ProblemDetail — it must never be null")
                .isEmpty();
    }

    @Test
    void blockingRowsAreReportedWithTheirTotalValue() {
        when(issuances.findBlockingClearance(eq(7L), any(), any())).thenReturn(List.of(
                issuance(11L, "Laptop", new BigDecimal("185000.00"), ClearanceStatus.PENDING),
                issuance(12L, "Motorcycle", new BigDecimal("240000.00"), null)
        ));

        BelongingClearanceCheckDto result = service.clearanceCheck(7L);

        assertThat(result.cleared()).isFalse();
        assertThat(result.pendingCount()).isEqualTo(2);
        assertThat(result.pendingAssetValue()).isEqualByComparingTo("425000.00");
        assertThat(result.pending())
                .extracting(BelongingClearanceCheckDto.PendingBelonging::issuanceId,
                        BelongingClearanceCheckDto.PendingBelonging::belongingName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(11L, "Laptop"),
                        org.assertj.core.groups.Tuple.tuple(12L, "Motorcycle"));
    }

    @Test
    void aNullAssetPriceDoesNotBreakTheTotal() {
        when(issuances.findBlockingClearance(eq(7L), any(), any())).thenReturn(List.of(
                issuance(11L, "SIM", null, ClearanceStatus.REJECTED),
                issuance(12L, "Laptop", new BigDecimal("185000.00"), null)
        ));

        BelongingClearanceCheckDto result = service.clearanceCheck(7L);

        assertThat(result.pendingAssetValue()).isEqualByComparingTo("185000.00");
        assertThat(result.pendingCount())
                .as("a priceless asset still blocks — value is context, not the gate")
                .isEqualTo(2);
    }

    @Test
    void theBlockingFilterIsPushedIntoTheQuery() {
        when(issuances.findBlockingClearance(7L, ActivationStatus.ACTIVE, ClearanceStatus.APPROVED))
                .thenReturn(List.of());

        service.clearanceCheck(7L);

        org.mockito.Mockito.verify(issuances)
                .findBlockingClearance(7L, ActivationStatus.ACTIVE, ClearanceStatus.APPROVED);
    }

    private static BelongingIssuance issuance(long id,
                                              String name,
                                              BigDecimal price,
                                              ClearanceStatus clearance) {

        BelongingMaster master = new BelongingMaster();
        master.setId(id * 100);
        master.setBelongingName(name);

        BelongingIssuance issuance = new BelongingIssuance();
        issuance.setId(id);
        issuance.setBelongingMaster(master);
        issuance.setEmployeeId(7L);
        issuance.setAssetModel(name + " model");
        issuance.setAssetPrice(price);
        issuance.setIssuedDate(LocalDate.of(2025, 3, 1));
        issuance.setActivationStatus(ActivationStatus.ACTIVE);
        issuance.setActivationDate(LocalDate.of(2025, 3, 1));
        issuance.setClearanceStatus(clearance);
        return issuance;
    }
}

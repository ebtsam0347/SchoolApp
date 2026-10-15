package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.employee.dto.response.OffCyclePaymentTypeDto;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.payroll.dto.request.CreateOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.request.PayOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.request.UpdateOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDto;
import com.forward.hrms.payroll.entity.OffCyclePayment;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.enums.OffCyclePaymentMode;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.OffCyclePaymentStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.OffCyclePaymentRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The off-cycle payment state machine, end to end in-memory.
 *
 * <pre>
 * HR CREATE ──▶ HR_APPROVED ──┬── HR edit     ──▶ HR_APPROVED
 *                             ├── HR cancel   ──▶ CANCELLED (immutable)
 *                             └── Accounts pay ─▶ PAID      (immutable)
 * </pre>
 *
 * <p>Plain Mockito — the transitions and their audit snapshots are pure
 * service logic, so a Spring context would only slow the feedback loop.
 * Permission enforcement is annotation-driven and is asserted separately
 * in {@link com.forward.hrms.payroll.controller.OffCyclePaymentSecurityContractTest}.
 */
class OffCyclePaymentLifecycleTest {

    private static final long HR_USER = 7L;
    private static final long ACCOUNTS_USER = 99L;

    /** An active row in the HR-facing type master owned by employee-service. */
    private static final OffCyclePaymentTypeDto EID_SUPPORT =
            new OffCyclePaymentTypeDto(4L, "Eid Support", "Seasonal support payment", true);

    /**
     * A master type whose NAME looks like a bonus. Selecting it must still
     * produce an HR transaction - the rule that stops HR steering
     * SettlementBonusCalculator by renaming a dropdown entry.
     */
    private static final OffCyclePaymentTypeDto NAMED_BONUS =
            new OffCyclePaymentTypeDto(9L, "Special Bonus", "Looks like a bonus, is not", true);

    private final OffCyclePaymentRepository repo = mock(OffCyclePaymentRepository.class);
    private final Outbox outbox = mock(Outbox.class);
    private final AuditEmitter audit = mock(AuditEmitter.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final OffCyclePaymentService service =
            new OffCyclePaymentService(repo, outbox, audit, employees);

    @BeforeEach
    void signInAsHr() {
        actAs(HR_USER, "hr.farhan");
        // Stand in for the identity column so the mapper and the outbox
        // key have an id to read, the way a real save would.
        when(repo.save(any(OffCyclePayment.class))).thenAnswer(i -> {
            OffCyclePayment p = i.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });
        when(repo.saveAndFlush(any(OffCyclePayment.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(employees.activeOffCyclePaymentTypes(any()))
                .thenReturn(List.of(EID_SUPPORT, NAMED_BONUS));
    }

    @AfterEach
    void signOut() {
        CurrentUserHolder.clear();
    }

    // ======================================================
    // Scenario 1 — HR creates
    // ======================================================

    @Test
    void create_landsInHrApprovedAndStampsTheAuthorizingHrUser() {
        OffCyclePaymentDto dto = service.create(request(EID_SUPPORT.id(), "50000.00"));

        assertThat(dto.status()).isEqualTo(OffCyclePaymentStatus.HR_APPROVED);
        assertThat(dto.hrApprovedBy()).isEqualTo(HR_USER);
        assertThat(dto.hrApprovedAt()).isNotNull();
        assertThat(dto.paidBy()).isNull();
        assertThat(dto.paidAt()).isNull();
    }

    @Test
    void create_referencesTheMasterTypeAndSnapshotsItsName() {
        OffCyclePaymentDto dto = service.create(request(EID_SUPPORT.id(), "50000.00"));

        assertThat(dto.source()).isEqualTo(OffCyclePaymentSource.HR);
        assertThat(dto.offCyclePaymentTypeId()).isEqualTo(EID_SUPPORT.id());
        assertThat(dto.offCyclePaymentTypeName())
                .as("snapshotted so a later master rename leaves history readable")
                .isEqualTo("Eid Support");
    }

    @Test
    void create_withATypeNamedBonus_isStillAnHrTransaction() {
        OffCyclePaymentDto dto = service.create(request(NAMED_BONUS.id(), "50000.00"));

        assertThat(dto.source())
                .as("if the master name could set source=BONUS, HR could steer "
                        + "SettlementBonusCalculator by renaming a dropdown entry")
                .isEqualTo(OffCyclePaymentSource.HR);
        assertThat(dto.offCyclePaymentTypeName()).isEqualTo("Special Bonus");
    }

    @Test
    void create_rejectsATypeThatIsInactiveOrUnknown() {
        assertThatThrownBy(() -> service.create(request(999L, "50000.00")))
                .isInstanceOf(UnprocessableError.class)
                .extracting(e -> ((UnprocessableError) e).code())
                .isEqualTo(PayrollErrorCode.OFF_CYCLE_TYPE_INVALID);

        verify(repo, never()).save(any());
    }

    @Test
    void create_failsClosedWhenEmployeeServiceIsUnreachable() {
        // What the client actually raises on a transport or non-2xx
        // failure. It must reach the caller as a 502 upstream error, not
        // be downgraded to a 422 that blames HR for a bad type.
        when(employees.activeOffCyclePaymentTypes(any()))
                .thenThrow(new UpstreamError(PayrollErrorCode.OFF_CYCLE_TYPE_LOOKUP_FAILED,
                        "employee-service down", null));

        assertThatThrownBy(() -> service.create(request(EID_SUPPORT.id(), "50000.00")))
                .as("an outage must block the write, never accept an unvalidated type")
                .isInstanceOf(UpstreamError.class)
                .satisfies(e -> {
                    assertThat(((UpstreamError) e).status()).isEqualTo(502);
                    assertThat(((UpstreamError) e).code())
                            .isEqualTo(PayrollErrorCode.OFF_CYCLE_TYPE_LOOKUP_FAILED);
                });

        verify(repo, never()).save(any());
    }

    // ======================================================
    // Scenario 2 — HR edits an unpaid payment
    // ======================================================

    @Test
    void update_keepsTheSameRowAndStaysHrApproved() {
        OffCyclePayment row = existing(OffCyclePaymentStatus.HR_APPROVED);

        OffCyclePaymentDto dto = service.update(1L, new UpdateOffCyclePaymentRequest(
                new BigDecimal("55000.00"), null, null, null, null, null));

        assertThat(dto.id()).isEqualTo(row.getId());
        assertThat(dto.amount()).isEqualByComparingTo("55000.00");
        assertThat(dto.status()).isEqualTo(OffCyclePaymentStatus.HR_APPROVED);
    }

    @Test
    void update_toADifferentTypeRevalidatesAndReSnapshots() {
        existing(OffCyclePaymentStatus.HR_APPROVED);

        OffCyclePaymentDto dto = service.update(1L, new UpdateOffCyclePaymentRequest(
                null, NAMED_BONUS.id(), null, null, null, null));

        assertThat(dto.offCyclePaymentTypeId()).isEqualTo(NAMED_BONUS.id());
        assertThat(dto.offCyclePaymentTypeName()).isEqualTo("Special Bonus");
        assertThat(dto.source())
                .as("an edit never reclassifies the transaction")
                .isEqualTo(OffCyclePaymentSource.HR);
    }

    @Test
    void update_rejectsAnInactiveOrUnknownType() {
        existing(OffCyclePaymentStatus.HR_APPROVED);

        assertThatThrownBy(() -> service.update(1L, new UpdateOffCyclePaymentRequest(
                null, 999L, null, null, null, null)))
                .isInstanceOf(UnprocessableError.class)
                .extracting(e -> ((UnprocessableError) e).code())
                .isEqualTo(PayrollErrorCode.OFF_CYCLE_TYPE_INVALID);
    }

    // ======================================================
    // Scenario 3 — Accounts pays
    // ======================================================

    @Test
    void pay_flipsToPaidAndRecordsTheAccountsActorAndPaymentDetail() {
        existing(OffCyclePaymentStatus.HR_APPROVED);
        actAs(ACCOUNTS_USER, "accounts.bilal");

        OffCyclePaymentDto dto = service.pay(1L, new PayOffCyclePaymentRequest(
                OffCyclePaymentMode.BANK, "TRX-55821", LocalDate.of(2026, 9, 25)));

        assertThat(dto.status()).isEqualTo(OffCyclePaymentStatus.PAID);
        assertThat(dto.paidBy()).isEqualTo(ACCOUNTS_USER);
        assertThat(dto.paidAt()).isNotNull();
        assertThat(dto.paymentMode()).isEqualTo(OffCyclePaymentMode.BANK);
        assertThat(dto.referenceNo()).isEqualTo("TRX-55821");
        assertThat(dto.paidOn()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void pay_withoutADateKeepsTheDateHrRecorded() {
        OffCyclePayment row = existing(OffCyclePaymentStatus.HR_APPROVED);
        LocalDate hrDate = row.getPaidOn();

        OffCyclePaymentDto dto = service.pay(1L,
                new PayOffCyclePaymentRequest(OffCyclePaymentMode.CASH, null, null));

        assertThat(dto.paidOn()).isEqualTo(hrDate);
    }

    // ======================================================
    // HR cancels
    // ======================================================

    @Test
    void cancel_flipsToCancelledAndKeepsTheRow() {
        existing(OffCyclePaymentStatus.HR_APPROVED);

        OffCyclePaymentDto dto = service.cancel(1L);

        assertThat(dto.status()).isEqualTo(OffCyclePaymentStatus.CANCELLED);
        verify(repo, never()).delete(any());
    }

    // ======================================================
    // Scenarios 4 & 5 — terminal states are immutable
    // ======================================================

    @ParameterizedTest
    @EnumSource(value = OffCyclePaymentStatus.class, names = {"PAID", "CANCELLED"})
    void terminalStatus_rejectsEdit(OffCyclePaymentStatus terminal) {
        existing(terminal);

        assertThatThrownBy(() -> service.update(1L, new UpdateOffCyclePaymentRequest(
                new BigDecimal("1.00"), null, null, null, null, null)))
                .isInstanceOf(ConflictError.class)
                .extracting(e -> ((ConflictError) e).code())
                .isEqualTo(PayrollErrorCode.OFF_CYCLE_INVALID_STATE);
    }

    @ParameterizedTest
    @EnumSource(value = OffCyclePaymentStatus.class, names = {"PAID", "CANCELLED"})
    void terminalStatus_rejectsCancel(OffCyclePaymentStatus terminal) {
        existing(terminal);

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(ConflictError.class)
                .extracting(e -> ((ConflictError) e).code())
                .isEqualTo(PayrollErrorCode.OFF_CYCLE_INVALID_STATE);
    }

    @ParameterizedTest
    @EnumSource(value = OffCyclePaymentStatus.class, names = {"PAID", "CANCELLED"})
    void terminalStatus_rejectsPayment(OffCyclePaymentStatus terminal) {
        existing(terminal);

        assertThatThrownBy(() -> service.pay(1L,
                new PayOffCyclePaymentRequest(OffCyclePaymentMode.BANK, "X", null)))
                .isInstanceOf(ConflictError.class)
                .extracting(e -> ((ConflictError) e).code())
                .isEqualTo(PayrollErrorCode.OFF_CYCLE_INVALID_STATE);
    }

    @Test
    void terminalStatus_leavesTheFinancialDataUntouched() {
        OffCyclePayment row = existing(OffCyclePaymentStatus.PAID);
        BigDecimal before = row.getAmount();

        assertThatThrownBy(() -> service.update(1L, new UpdateOffCyclePaymentRequest(
                new BigDecimal("999999.00"), NAMED_BONUS.id(), "tampered", null, null, null)))
                .isInstanceOf(ConflictError.class);

        assertThat(row.getAmount()).isEqualByComparingTo(before);
        assertThat(row.getOffCyclePaymentTypeId()).isEqualTo(EID_SUPPORT.id());
        verify(repo, never()).saveAndFlush(any());
    }

    // ======================================================
    // Hard delete is retired
    // ======================================================

    @Test
    void delete_refusesInsteadOfDestroyingFinancialHistory() {
        existing(OffCyclePaymentStatus.HR_APPROVED);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictError.class)
                .extracting(e -> ((ConflictError) e).code())
                .isEqualTo(PayrollErrorCode.OFF_CYCLE_DELETE_RETIRED);

        verify(repo, never()).delete(any());
    }

    // ======================================================
    // Concurrency — HR edits while Accounts pays
    // ======================================================

    @Test
    void staleUpdate_surfacesAsAConflictNotAnUntranslated500() {
        existing(OffCyclePaymentStatus.HR_APPROVED);
        doThrow(new OptimisticLockingFailureException("stale"))
                .when(repo).saveAndFlush(any(OffCyclePayment.class));

        assertThatThrownBy(() -> service.update(1L, new UpdateOffCyclePaymentRequest(
                new BigDecimal("60000.00"), null, null, null, null, null)))
                .isInstanceOf(ConflictError.class)
                .extracting(e -> ((ConflictError) e).code())
                .isEqualTo(PayrollErrorCode.OFF_CYCLE_CONCURRENT_UPDATE);
    }

    // ======================================================
    // Scenario 7 — audit trail
    // ======================================================

    @Test
    void create_emitsNullBeforeAndACompleteAfterSnapshot() {
        service.create(new CreateOffCyclePaymentRequest(
                42L, LocalDate.of(2026, 9, 20), new BigDecimal("50000.00"),
                EID_SUPPORT.id(), "Eid support", OffCyclePaymentMode.CASH, "V-1"));

        AuditArgs a = captureAudit();
        assertThat(a.action()).isEqualTo("off_cycle_recorded");
        assertThat(a.before()).isNull();
        assertThat(a.after()).containsEntry("status", "HR_APPROVED")
                .containsEntry("amount", new BigDecimal("50000.00"))
                .containsEntry("referenceNo", "V-1")
                .containsEntry("paymentMode", "CASH")
                .containsEntry("source", "HR")
                // Both: the name keeps the entry readable after a master
                // rename, the id keeps it traceable to the master row.
                .containsEntry("offCyclePaymentTypeId", EID_SUPPORT.id())
                .containsEntry("offCyclePaymentTypeName", "Eid Support");
    }

    @Test
    void update_emitsBothSidesIncludingFieldsTheOldSnapshotDropped() {
        existing(OffCyclePaymentStatus.HR_APPROVED);

        service.update(1L, new UpdateOffCyclePaymentRequest(
                null, null, "corrected reason", OffCyclePaymentMode.BANK, "CHQ-9", null));

        AuditArgs a = captureAudit();
        assertThat(a.action()).isEqualTo("off_cycle_updated");
        // The defect this guards: a reason / mode / reference edit used to
        // produce an audit row identical to the previous one.
        assertThat(a.before()).containsEntry("reason", "Eid support")
                .containsEntry("paymentMode", "CASH")
                .containsEntry("referenceNo", null);
        assertThat(a.after()).containsEntry("reason", "corrected reason")
                .containsEntry("paymentMode", "BANK")
                .containsEntry("referenceNo", "CHQ-9");
    }

    @Test
    void cancel_emitsTheHrApprovedToCancelledTransition() {
        existing(OffCyclePaymentStatus.HR_APPROVED);

        service.cancel(1L);

        AuditArgs a = captureAudit();
        assertThat(a.action()).isEqualTo("off_cycle_cancelled");
        assertThat(a.before()).containsEntry("status", "HR_APPROVED");
        assertThat(a.after()).containsEntry("status", "CANCELLED");
    }

    @Test
    void pay_emitsTheHrApprovedToPaidTransitionWithTheAccountsActor() {
        existing(OffCyclePaymentStatus.HR_APPROVED);
        actAs(ACCOUNTS_USER, "accounts.bilal");

        service.pay(1L, new PayOffCyclePaymentRequest(OffCyclePaymentMode.BANK, "TRX-1", null));

        AuditArgs a = captureAudit();
        assertThat(a.action()).isEqualTo("off_cycle_paid");
        assertThat(a.before()).containsEntry("status", "HR_APPROVED")
                .containsEntry("paidBy", null);
        assertThat(a.after()).containsEntry("status", "PAID")
                .containsEntry("paidBy", ACCOUNTS_USER)
                .containsEntry("referenceNo", "TRX-1");
    }

    // ======================================================
    // Bonus-sourced rows
    // ======================================================

    @Test
    void recordBonusInstallment_isBornPaidSoItNeverEntersTheAccountsQueue() {
        when(repo.findBySourceInstallmentIdAndEmployeeId(5L, 42L)).thenReturn(Optional.empty());

        OffCyclePayment p = service.recordBonusInstallment(42L, new BigDecimal("12000.00"),
                LocalDate.of(2026, 9, 20), "Eid — installment 1 (50%)",
                OffCyclePaymentMode.BANK, 3L, 5L);

        assertThat(p.getStatus()).isEqualTo(OffCyclePaymentStatus.PAID);
        assertThat(p.getPaidAt()).isNotNull();
        assertThat(p.getSource())
                .as("only this path may mint BONUS; it is what SettlementBonusCalculator reads")
                .isEqualTo(OffCyclePaymentSource.BONUS);
        assertThat(p.getOffCyclePaymentTypeId())
                .as("system-generated rows carry no HR master type")
                .isNull();
    }

    @Test
    void recordBonusInstallment_reapplyStaysIdempotentAndEmitsNothingTwice() {
        OffCyclePayment already = new OffCyclePayment();
        already.setStatus(OffCyclePaymentStatus.PAID);
        when(repo.findBySourceInstallmentIdAndEmployeeId(5L, 42L)).thenReturn(Optional.of(already));

        OffCyclePayment p = service.recordBonusInstallment(42L, new BigDecimal("12000.00"),
                LocalDate.of(2026, 9, 20), "Eid — installment 1 (50%)",
                OffCyclePaymentMode.BANK, 3L, 5L);

        assertThat(p).isSameAs(already);
        verify(repo, never()).save(any());
        verify(audit, never()).emit(anyString(), anyString(), anyString(), any(), any(), anyBoolean());
    }

    // ======================================================
    // Fixtures
    // ======================================================

    private static CreateOffCyclePaymentRequest request(long typeId, String amount) {
        return new CreateOffCyclePaymentRequest(42L, LocalDate.of(2026, 9, 20),
                new BigDecimal(amount), typeId, "Eid support", OffCyclePaymentMode.CASH, null);
    }

    private static void actAs(long userId, String username) {
        CurrentUserHolder.set(new CurrentUser(userId, username, Set.of(), Set.of()));
    }

    /** An existing row in {@code status}, wired into the repository mock. */
    private OffCyclePayment existing(OffCyclePaymentStatus status) {
        OffCyclePayment p = new OffCyclePayment();
        p.setId(1L);
        p.setEmployeeId(42L);
        p.setPaidOn(LocalDate.of(2026, 9, 20));
        p.setAmount(new BigDecimal("50000.00"));
        p.setSource(OffCyclePaymentSource.HR);
        p.setOffCyclePaymentTypeId(EID_SUPPORT.id());
        p.setOffCyclePaymentTypeName(EID_SUPPORT.name());
        p.setReason("Eid support");
        p.setPaymentMode(OffCyclePaymentMode.CASH);
        p.setStatus(status);
        p.setHrApprovedBy(HR_USER);
        when(repo.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    @SuppressWarnings("unchecked")
    private AuditArgs captureAudit() {
        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(audit).emit(eq("off_cycle_payment"), anyString(), action.capture(),
                before.capture(), after.capture(), eq(true));
        return new AuditArgs(action.getValue(),
                (Map<String, Object>) before.getValue(),
                (Map<String, Object>) after.getValue());
    }

    private record AuditArgs(String action, Map<String, Object> before, Map<String, Object> after) {}
}

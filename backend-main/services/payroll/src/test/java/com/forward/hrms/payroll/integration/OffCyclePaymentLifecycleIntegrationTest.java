package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.dto.response.OffCyclePaymentTypeDto;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.CreateOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.request.PayOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.request.UpdateOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDto;
import com.forward.hrms.payroll.entity.OffCyclePayment;
import com.forward.hrms.payroll.enums.OffCyclePaymentMode;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.OffCyclePaymentStatus;
import com.forward.hrms.payroll.repository.OffCyclePaymentRepository;
import com.forward.hrms.payroll.service.OffCyclePaymentService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The lifecycle against a real Postgres, so the V0056 schema and the
 * status-aware bonus aggregation are exercised together rather than
 * mocked apart.
 *
 * <p>Covers what the in-memory
 * {@link com.forward.hrms.payroll.service.OffCyclePaymentLifecycleTest}
 * structurally cannot: the migration's column/constraint contract, the
 * {@code @Version} column actually incrementing, and
 * {@code totalBonusPaidForEvent} dropping a cancelled BONUS row.
 *
 * <p>The historical backfill itself is not asserted here — a fresh
 * container has no pre-V0056 rows to backfill. It was verified by running
 * V0056 against a seeded copy of the dev database (three legacy rows, all
 * landing PAID with no invented actors).
 */
@SpringBootTest
@WithTestcontainers
class OffCyclePaymentLifecycleIntegrationTest {

    /**
     * Nanotime-derived, like the other payroll integration tests: the
     * shared Postgres container is {@code withReuse(true)}, so it outlives
     * the JVM and a fixed seed collides with rows left by the previous
     * run — which reads as a phantom extra payment in the bonus totals.
     */
    private static final AtomicLong EMPLOYEE_SEQ =
            new AtomicLong(900_000L + (System.nanoTime() & 0x00FFFFFFL));

    /** Active rows in the employee-service HR type master. */
    private static final OffCyclePaymentTypeDto EID_SUPPORT =
            new OffCyclePaymentTypeDto(4L, "Eid Support", null, true);
    private static final OffCyclePaymentTypeDto NAMED_BONUS =
            new OffCyclePaymentTypeDto(9L, "Special Bonus", null, true);

    @Autowired OffCyclePaymentService service;
    @Autowired OffCyclePaymentRepository repo;
    @Autowired JdbcTemplate jdbc;

    /** The master lives in another service; stub it rather than stand one up. */
    @MockitoBean EmployeeClient employees;

    private long employeeId;

    @BeforeEach
    void signInAsHr() {
        employeeId = EMPLOYEE_SEQ.incrementAndGet();
        CurrentUserHolder.set(new CurrentUser(7L, "hr.farhan", Set.of("HR"), Set.of()));
        when(employees.activeOffCyclePaymentTypes(any()))
                .thenReturn(List.of(EID_SUPPORT, NAMED_BONUS));
    }

    @AfterEach
    void signOut() {
        CurrentUserHolder.clear();
    }

    // ======================================================
    // V0056 schema contract
    // ======================================================

    @Test
    void statusIsNotNullAndHasNoDefault() {
        Map<String, Object> col = column("status");

        assertThat(col.get("is_nullable")).isEqualTo("NO");
        assertThat(col.get("column_default"))
                .as("a DEFAULT would let an insert that forgets status silently mint a PAID "
                        + "financial record")
                .isNull();
    }

    @Test
    void versionIsNotNullAndDefaultsToZeroForLegacyRows() {
        Map<String, Object> col = column("version");

        assertThat(col.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(col.get("column_default"))).startsWith("0");
    }

    @Test
    void workflowActorColumnsAreNullableSoHistoryNeedNotBeInvented() {
        for (String c : new String[]{"hr_approved_by", "hr_approved_at", "paid_by", "paid_at"}) {
            assertThat(column(c).get("is_nullable")).as(c).isEqualTo("YES");
        }
    }

    @Test
    void masterReferenceColumnsAreNullableForSystemAndHistoricalRows() {
        for (String c : new String[]{"off_cycle_payment_type_id", "off_cycle_payment_type_name"}) {
            assertThat(column(c).get("is_nullable")).as(c).isEqualTo("YES");
        }
    }

    @Test
    void categoryCheckPinsTheThreeTransactionSources() {
        String clause = jdbc.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conname = 'chk_ocp_category'
                """, String.class);

        assertThat(clause).contains("HR").contains("BONUS").contains("FINAL_SETTLEMENT");
    }

    @Test
    void systemRowsCannotCarryAnHrMasterType() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into payroll.off_cycle_payments
                    (employee_id, paid_on, amount, category, off_cycle_payment_type_id,
                     status, created_by, updated_by, version)
                values (?, date '2026-09-20', 100.00, 'BONUS', 4,
                        'PAID', 'test', 'test', 0)
                """, employeeId))
                .as("chk_ocp_system_has_no_type keeps the HR master off system transactions")
                .isInstanceOf(Exception.class);
    }

    @Test
    void statusCheckConstraintPinsTheThreeStates() {
        String clause = jdbc.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conname = 'chk_ocp_status'
                """, String.class);

        assertThat(clause).contains("HR_APPROVED").contains("PAID").contains("CANCELLED");
    }

    // ======================================================
    // Round trip
    // ======================================================

    @Test
    void createEditThenPay_endsPaidWithBothActorsRecorded() {
        OffCyclePaymentDto created = service.create(request(EID_SUPPORT.id(), "50000.00"));
        assertThat(created.status()).isEqualTo(OffCyclePaymentStatus.HR_APPROVED);
        assertThat(created.hrApprovedBy()).isEqualTo(7L);

        OffCyclePaymentDto edited = service.update(created.id(), new UpdateOffCyclePaymentRequest(
                new BigDecimal("55000.00"), null, null, null, null, null));
        assertThat(edited.id()).isEqualTo(created.id());
        assertThat(edited.status()).isEqualTo(OffCyclePaymentStatus.HR_APPROVED);

        CurrentUserHolder.set(new CurrentUser(99L, "accounts.bilal", Set.of("ACCOUNTS"), Set.of()));
        OffCyclePaymentDto paid = service.pay(created.id(),
                new PayOffCyclePaymentRequest(OffCyclePaymentMode.BANK, "TRX-1", null));

        assertThat(paid.status()).isEqualTo(OffCyclePaymentStatus.PAID);
        assertThat(paid.paidBy()).isEqualTo(99L);
        assertThat(paid.amount()).isEqualByComparingTo("55000.00");
        // Both authorities are now on the row itself, not only in audit —
        // HR does not hold audit.read and would otherwise see neither.
        assertThat(paid.hrApprovedBy()).isEqualTo(7L);
        assertThat(paid.createdBy()).isEqualTo("hr.farhan");
    }

    @Test
    void paidRowIsImmutableInTheDatabaseRoundTrip() {
        OffCyclePaymentDto created = service.create(request(EID_SUPPORT.id(), "50000.00"));
        service.pay(created.id(), new PayOffCyclePaymentRequest(OffCyclePaymentMode.CASH, null, null));

        assertThatThrownBy(() -> service.update(created.id(), new UpdateOffCyclePaymentRequest(
                new BigDecimal("999999.00"), null, null, null, null, null)))
                .isInstanceOf(ConflictError.class);
        assertThatThrownBy(() -> service.cancel(created.id())).isInstanceOf(ConflictError.class);
        assertThatThrownBy(() -> service.pay(created.id(),
                new PayOffCyclePaymentRequest(OffCyclePaymentMode.CASH, null, null)))
                .isInstanceOf(ConflictError.class);

        OffCyclePayment reloaded = repo.findById(created.id()).orElseThrow();
        assertThat(reloaded.getAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void versionIncrementsOnEachTransition() {
        OffCyclePaymentDto created = service.create(request(EID_SUPPORT.id(), "50000.00"));
        Long born = repo.findById(created.id()).orElseThrow().getVersion();

        service.update(created.id(), new UpdateOffCyclePaymentRequest(
                new BigDecimal("51000.00"), null, null, null, null, null));

        assertThat(repo.findById(created.id()).orElseThrow().getVersion()).isGreaterThan(born);
    }

    // ======================================================
    // Settlement aggregation must be status-aware
    // ======================================================

    @Test
    void hrPaymentWithABonusSoundingTypeDoesNotCountAsBonusPaid() {
        // Exactly the trap the source/type split exists to prevent: HR
        // picks a master type called "Special Bonus", pays it, and it must
        // stay out of the settlement bonus base.
        OffCyclePaymentDto hr = service.create(request(NAMED_BONUS.id(), "25000.00"));
        service.pay(hr.id(), new PayOffCyclePaymentRequest(OffCyclePaymentMode.BANK, "T1", null));

        assertThat(repo.findById(hr.id()).orElseThrow().getSource())
                .isEqualTo(OffCyclePaymentSource.HR);
        assertThat(repo.totalBonusPaid(employeeId))
                .as("the HR master must never be able to feed SettlementBonusCalculator")
                .isEqualByComparingTo("0");
    }

    @Test
    void systemBonusRowsAreTheOnlyOnesThatCountAsBonusPaid() {
        seedLegacyBonusRow("12000.00");
        service.create(request(EID_SUPPORT.id(), "5000.00"));   // HR, must not count

        assertThat(repo.totalBonusPaid(employeeId)).isEqualByComparingTo("12000.00");
    }

    @Test
    void unpaidRowsDoNotCountAsBonusPaid() {
        // A BONUS row that never reached PAID: not released, so it cannot
        // reduce what a leaver is owed.
        jdbc.update("""
                insert into payroll.off_cycle_payments
                    (employee_id, paid_on, amount, category, reason, payment_mode,
                     status, created_by, updated_by, version)
                values (?, date '2026-09-20', 9000.00, 'BONUS', 'unpaid', 'BANK',
                        'HR_APPROVED', 'test', 'test', 0)
                """, employeeId);

        assertThat(repo.totalBonusPaid(employeeId)).isEqualByComparingTo("0");
    }

    // ======================================================
    // Accounts queue
    // ======================================================

    @Test
    void sourceFilterReachesTheQueryAndReturnsOnlyThatSource() {
        OffCyclePaymentDto hr = service.create(request(EID_SUPPORT.id(), "1000.00"));
        seedLegacyBonusRow("7000.00");

        var hrOnly = service.filter(employeeId, null, null,
                OffCyclePaymentSource.HR, null, PageRequest.of(0, 20));
        var bonusOnly = service.filter(employeeId, null, null,
                OffCyclePaymentSource.BONUS, null, PageRequest.of(0, 20));

        // The str(p.source) = :source branch of the filter query: previously
        // only the pass-null path was exercised, so a broken non-null bind
        // would have gone unnoticed.
        assertThat(hrOnly.content()).extracting(OffCyclePaymentDto::id)
                .containsExactly(hr.id());
        assertThat(bonusOnly.content()).extracting(OffCyclePaymentDto::source)
                .containsExactly(OffCyclePaymentSource.BONUS);
        assertThat(bonusOnly.content()).extracting(OffCyclePaymentDto::id)
                .doesNotContain(hr.id());
    }

    @Test
    void reportQueryHonoursTheSourceFilterAndTheDateRange() {
        service.create(request(EID_SUPPORT.id(), "1000.00"));
        seedLegacyBonusRow("7000.00");

        var bonusRows = repo.findForReport(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                OffCyclePaymentSource.BONUS.name(), PageRequest.of(0, 50)).getContent();
        var allRows = repo.findForReport(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                null, PageRequest.of(0, 50)).getContent();

        assertThat(bonusRows).isNotEmpty()
                .allMatch(r -> r.getSource() == OffCyclePaymentSource.BONUS);
        // Same query serves the unfiltered report now that the unbounded
        // findAll() branch is gone, so the null bind has to keep working.
        assertThat(allRows).extracting(OffCyclePayment::getSource)
                .contains(OffCyclePaymentSource.HR, OffCyclePaymentSource.BONUS);
    }

    @Test
    void reportQueryExcludesRowsOutsideTheDateRange() {
        service.create(request(EID_SUPPORT.id(), "1000.00"));   // paidOn 2026-09-20

        var outside = repo.findForReport(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                null, PageRequest.of(0, 50)).getContent();

        assertThat(outside).extracting(OffCyclePayment::getEmployeeId).doesNotContain(employeeId);
    }

    @Test
    void statusFilterReturnsOnlyTheAccountsQueue() {
        OffCyclePaymentDto waiting = service.create(request(EID_SUPPORT.id(), "1000.00"));
        OffCyclePaymentDto done = service.create(request(EID_SUPPORT.id(), "2000.00"));
        service.pay(done.id(), new PayOffCyclePaymentRequest(OffCyclePaymentMode.CASH, null, null));

        var queue = service.filter(employeeId, null, null, null,
                OffCyclePaymentStatus.HR_APPROVED, PageRequest.of(0, 20));

        assertThat(queue.content()).extracting(OffCyclePaymentDto::id)
                .containsExactly(waiting.id())
                .doesNotContain(done.id());
    }

    private CreateOffCyclePaymentRequest request(long typeId, String amount) {
        return new CreateOffCyclePaymentRequest(employeeId, LocalDate.of(2026, 9, 20),
                new BigDecimal(amount), typeId, "integration fixture",
                OffCyclePaymentMode.CASH, null);
    }

    /**
     * A PAID BONUS row with no bonus-event provenance - the shape a
     * hand-entered legacy bonus has, and the shape V0057 deliberately
     * leaves classified as BONUS so settlement figures do not move.
     * Inserted directly because recordBonusInstallment needs real
     * bonus_events / bonus_installments rows to satisfy its FKs.
     */
    private void seedLegacyBonusRow(String amount) {
        jdbc.update("""
                insert into payroll.off_cycle_payments
                    (employee_id, paid_on, amount, category, reason, payment_mode,
                     status, paid_at, created_by, updated_by, version)
                values (?, date '2026-09-20', ?::numeric, 'BONUS', 'legacy bonus', 'BANK',
                        'PAID', now(), 'test', 'test', 0)
                """, employeeId, amount);
    }

    private Map<String, Object> column(String name) {
        return jdbc.queryForMap("""
                select is_nullable, column_default
                from information_schema.columns
                where table_schema = 'payroll'
                  and table_name = 'off_cycle_payments'
                  and column_name = ?
                """, name);
    }
}

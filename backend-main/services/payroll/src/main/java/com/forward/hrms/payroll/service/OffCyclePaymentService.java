package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.OffCyclePaymentTypeDto;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.CreateOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.request.PayOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.request.UpdateOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDto;
import com.forward.hrms.payroll.entity.OffCyclePayment;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.OffCyclePaymentStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.OffCyclePaymentRepository;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Off-cycle payment lifecycle. Two authorities, three states:
 *
 * <pre>
 * HR CREATE ──▶ HR_APPROVED ──┬── HR edit     ──▶ HR_APPROVED
 *                             ├── HR cancel   ──▶ CANCELLED (immutable)
 *                             └── Accounts pay ─▶ PAID      (immutable)
 * </pre>
 *
 * <p>Creation <em>is</em> the HR authorization — there is deliberately no
 * submit/approve step, so a row is born {@code HR_APPROVED} with
 * {@code hrApprovedBy/At} stamped from the creating user.
 *
 * <p>Every guard keys on {@link OffCyclePayment#getStatus()}. The old
 * {@code sourceEventId != null} rule was provenance, not lifecycle: it
 * froze bonus-sourced rows while leaving HR-recorded rows editable and
 * hard-deletable forever, including long after the money was out.
 */
@Service
@RequiredArgsConstructor
public class OffCyclePaymentService {

    private final OffCyclePaymentRepository repo;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final EmployeeClient employees;

    @Transactional(readOnly = true)
    public PageResponse<OffCyclePaymentDto> filter(Long employeeId, LocalDate from, LocalDate to,
                                                   OffCyclePaymentSource source,
                                                   OffCyclePaymentStatus status,
                                                   Pageable pageable) {
        return PageResponse.from(repo.filter(employeeId, from, to,
                        source == null ? null : source.name(),
                        status == null ? null : status.name(), pageable)
                .map(OffCyclePaymentDtoMapper::of));
    }

    @Transactional(readOnly = true)
    public OffCyclePaymentDto get(long id) {
        return OffCyclePaymentDtoMapper.of(load(id));
    }

    @Transactional(readOnly = true)
    public List<OffCyclePaymentDto> listForEmployee(long employeeId, LocalDate from, LocalDate to) {
        List<OffCyclePayment> rows = (from != null && to != null)
                ? repo.findAllByEmployeeIdAndPaidOnBetweenOrderByPaidOnDesc(employeeId, from, to)
                : repo.findAllByEmployeeIdOrderByPaidOnDesc(employeeId);
        return rows.stream().map(OffCyclePaymentDtoMapper::of).toList();
    }

    /**
     * HR records — and thereby authorizes — an off-cycle payment. The row
     * lands in {@code HR_APPROVED}, waiting for Accounts to pay it.
     */
    @Transactional
    public OffCyclePaymentDto create(CreateOffCyclePaymentRequest req) {
        CurrentUser actor = currentUser();
        OffCyclePaymentTypeDto type = requireActiveType(req.offCyclePaymentTypeId());

        OffCyclePayment p = new OffCyclePayment();
        p.setEmployeeId(req.employeeId());
        p.setPaidOn(req.paidOn());
        p.setAmount(req.amount());
        // Always HR, never derived from the type's name. A master type
        // named "Bonus" is still an HR transaction -- otherwise HR could
        // move money into SettlementBonusCalculator by renaming a
        // dropdown entry.
        p.setSource(OffCyclePaymentSource.HR);
        p.setOffCyclePaymentTypeId(type.id());
        p.setOffCyclePaymentTypeName(type.name());
        p.setReason(req.reason());
        p.setPaymentMode(req.paymentMode());
        p.setReferenceNo(req.referenceNo());
        p.setStatus(OffCyclePaymentStatus.HR_APPROVED);
        p.setHrApprovedBy(actor.userId());
        p.setHrApprovedAt(LocalDateTime.now());
        p = repo.save(p);

        emit(p, "RECORDED", null);
        return OffCyclePaymentDtoMapper.of(p);
    }

    /**
     * HR edits an approved-but-unpaid payment. Same row, same id, still
     * {@code HR_APPROVED} — editing never re-opens an approval step.
     */
    @Transactional
    public OffCyclePaymentDto update(long id, UpdateOffCyclePaymentRequest req) {
        OffCyclePayment p = load(id);
        requireOpen(p, "edited");
        requireHrCreated(p, "edited");

        Map<String, Object> before = snapshot(p);
        if (req.amount() != null) p.setAmount(req.amount());
        if (req.offCyclePaymentTypeId() != null) {
            // Re-pointing the type revalidates against the live master and
            // re-snapshots the name. The source is untouched: an edit
            // never reclassifies a transaction.
            OffCyclePaymentTypeDto type = requireActiveType(req.offCyclePaymentTypeId());
            p.setOffCyclePaymentTypeId(type.id());
            p.setOffCyclePaymentTypeName(type.name());
        }
        if (req.reason() != null) p.setReason(req.reason());
        if (req.paymentMode() != null) p.setPaymentMode(req.paymentMode());
        if (req.referenceNo() != null) p.setReferenceNo(req.referenceNo());
        if (req.paidOn() != null) p.setPaidOn(req.paidOn());
        flush(p);

        emit(p, "UPDATED", before);
        return OffCyclePaymentDtoMapper.of(p);
    }

    /**
     * HR withdraws an approved-but-unpaid payment. HR only — Accounts is
     * the payment authority, not a second approval layer, and never
     * cancels HR's authorization. The row is kept, not deleted: cancelled
     * financial history stays readable.
     */
    @Transactional
    public OffCyclePaymentDto cancel(long id) {
        OffCyclePayment p = load(id);
        requireOpen(p, "cancelled");
        requireHrCreated(p, "cancelled");

        Map<String, Object> before = snapshot(p);
        p.setStatus(OffCyclePaymentStatus.CANCELLED);
        flush(p);

        emit(p, "CANCELLED", before);
        return OffCyclePaymentDtoMapper.of(p);
    }

    /**
     * Accounts releases the money and records how. Only {@code
     * HR_APPROVED} can be paid, so a second call on a paid row — or any
     * call on a cancelled one — is rejected rather than being a silent
     * no-op.
     */
    @Transactional
    public OffCyclePaymentDto pay(long id, PayOffCyclePaymentRequest req) {
        CurrentUser actor = currentUser();
        OffCyclePayment p = load(id);
        requireOpen(p, "paid");

        Map<String, Object> before = snapshot(p);
        p.setPaymentMode(req.paymentMode());
        if (req.referenceNo() != null) p.setReferenceNo(req.referenceNo());
        if (req.paidOn() != null) p.setPaidOn(req.paidOn());
        p.setStatus(OffCyclePaymentStatus.PAID);
        p.setPaidBy(actor.userId());
        p.setPaidAt(LocalDateTime.now());
        flush(p);

        emit(p, "PAID", before);
        return OffCyclePaymentDtoMapper.of(p);
    }

    /**
     * Retired. The lifecycle replaced destructive deletion: an unpaid
     * payment is cancelled (keeping the row and its history), and a paid
     * one is immutable. The route stays mapped so existing callers get a
     * 409 naming the replacement instead of a 404 they would read as
     * "already gone" — no consumer in this repo calls it (BFF only reads
     * off-cycle payments), so nothing breaks in practice.
     *
     * @throws NotFoundError if the id does not exist, so a genuine 404
     *                       still looks like one.
     */
    @Transactional(readOnly = true)
    public void delete(long id) {
        OffCyclePayment p = load(id);
        throw new ConflictError(PayrollErrorCode.OFF_CYCLE_DELETE_RETIRED,
                p.getStatus().isOpen()
                        ? "off-cycle payments are no longer hard-deleted — POST /off-cycle-payments/"
                                + id + "/cancel instead"
                        : "a " + p.getStatus() + " off-cycle payment is immutable financial history "
                                + "and cannot be deleted");
    }

    /**
     * Insert a bonus-sourced row. The caller (BonusEventService.applyInstallment)
     * fans out per-employee REQUIRES_NEW; a saga retry between the per-employee
     * commits and the closing tx would otherwise duplicate every row. Idempotent
     * on (sourceInstallmentId, employeeId) — a re-apply hits the existing row
     * and short-circuits without emitting a second outbox/audit event. The
     * partial unique index `uq_ocp_bonus_source` (V0013) is the durable guard.
     *
     * <p>Applying an installment <em>is</em> the payment: the caller passes
     * the installment's own {@code paidOn}, and
     * {@code SettlementBonusCalculator} already treats these rows as money
     * paid when it works out what a leaver is still owed. So they are born
     * {@code PAID}, not {@code HR_APPROVED} — routing them through the
     * Accounts queue would invent approval work that the bonus workflow
     * never had.
     */
    @Transactional
    public OffCyclePayment recordBonusInstallment(long employeeId, BigDecimal amount, LocalDate paidOn,
                                                  String reason,
                                                  com.forward.hrms.payroll.enums.OffCyclePaymentMode mode,
                                                  long sourceEventId, long sourceInstallmentId) {
        var existing = repo.findBySourceInstallmentIdAndEmployeeId(sourceInstallmentId, employeeId);
        if (existing.isPresent()) return existing.get();
        OffCyclePayment p = new OffCyclePayment();
        p.setEmployeeId(employeeId);
        p.setPaidOn(paidOn);
        p.setAmount(amount);
        p.setSource(OffCyclePaymentSource.BONUS);
        p.setReason(reason);
        p.setPaymentMode(mode);
        p.setSourceEventId(sourceEventId);
        p.setSourceInstallmentId(sourceInstallmentId);
        p.setStatus(OffCyclePaymentStatus.PAID);
        // The apply moment is the real payment moment — not a fabricated
        // one. `paidBy` stays null when the apply runs on a batch thread
        // with no authenticated caller rather than naming a stand-in.
        p.setPaidAt(LocalDateTime.now());
        p.setPaidBy(CurrentUserHolder.get().map(CurrentUser::userId).orElse(null));
        p = repo.save(p);
        emit(p, "RECORDED", null);
        return p;
    }

    OffCyclePayment load(long id) {
        return repo.findById(id).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.OFF_CYCLE_NOT_FOUND,
                        "off-cycle payment not found"));
    }

    /**
     * Every state guard in one place: only {@code HR_APPROVED} rows move.
     * {@code PAID} and {@code CANCELLED} are terminal, which also covers
     * pay-twice and cancel-twice.
     */
    private void requireOpen(OffCyclePayment p, String verb) {
        if (!p.getStatus().isOpen()) {
            throw new ConflictError(PayrollErrorCode.OFF_CYCLE_INVALID_STATE,
                    "a " + p.getStatus() + " off-cycle payment cannot be " + verb);
        }
    }

    /**
     * Defensive only — no API path produces a non-PAID system row today,
     * so this is unreachable through the service. It exists so that if one
     * ever appears, HR editing it surfaces as a normal 409 instead of a
     * raw 500 from the {@code chk_ocp_system_has_no_type} CHECK.
     */
    private void requireHrCreated(OffCyclePayment p, String verb) {
        if (!p.getSource().isHrCreated()) {
            throw new ConflictError(PayrollErrorCode.OFF_CYCLE_INVALID_STATE,
                    "a " + p.getSource() + " off-cycle payment is system-generated and cannot be "
                            + verb);
        }
    }

    /**
     * The HR-facing type must be a live row in employee-service's
     * {@code off_cycle_payment_type} master. Existence and active state
     * collapse into one 422 deliberately: an inactive type and a
     * nonexistent one are both "not selectable", and telling them apart
     * gives the caller nothing to act on.
     *
     * <p>The master exposes no {@code GET /{id}}, so this scans the
     * active list. Small HR-maintained catalog, and this runs only on
     * create or a type change, never on a read.
     *
     * <p>Fails closed: {@code activeOffCyclePaymentTypes} lets transport
     * failures propagate rather than returning an empty list, so an
     * employee-service outage blocks to write instead of quietly
     * accepting an unvalidated type onto a financial record.
     */
    private OffCyclePaymentTypeDto requireActiveType(Long typeId) {
        if (typeId == null) {
            throw new UnprocessableError(PayrollErrorCode.OFF_CYCLE_TYPE_INVALID,
                    "offCyclePaymentTypeId is required");
        }
        for (OffCyclePaymentTypeDto t : employees.activeOffCyclePaymentTypes(
                EmployeeClient.currentRequestIdentity())) {
            if (typeId.equals(t.id())) return t;
        }
        throw new UnprocessableError(PayrollErrorCode.OFF_CYCLE_TYPE_INVALID,
                "off-cycle payment type " + typeId + " does not exist or is not active");
    }

    /**
     * Surfaces the {@code @Version} conflict inside the transaction, where
     * it can become a 409 instead of escaping to the commit boundary as an
     * untranslated 500. HR editing while Accounts pays is the race this
     * exists for.
     */
    private void flush(OffCyclePayment p) {
        try {
            repo.saveAndFlush(p);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictError(PayrollErrorCode.OFF_CYCLE_CONCURRENT_UPDATE,
                    "this off-cycle payment was changed by someone else — reload and retry");
        }
    }

    private CurrentUser currentUser() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
    }

    /**
     * Complete row state, for both sides of an audit diff. Every mutable
     * and lifecycle field is here: a snapshot that omits `referenceNo` or
     * `paymentMode` makes an edit to those fields indistinguishable from
     * no edit at all.
     */
    private static Map<String, Object> snapshot(OffCyclePayment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", p.getEmployeeId());
        m.put("paidOn", str(p.getPaidOn()));
        m.put("amount", p.getAmount());
        m.put("source", p.getSource() == null ? null : p.getSource().name());
        // id and name together: the id says which master row was
        // chosen, the name says what HR saw. Either alone leaves an
        // old audit entry unreadable once the master is renamed.
        m.put("offCyclePaymentTypeId", p.getOffCyclePaymentTypeId());
        m.put("offCyclePaymentTypeName", p.getOffCyclePaymentTypeName());
        m.put("reason", p.getReason());
        m.put("paymentMode", p.getPaymentMode() == null ? null : p.getPaymentMode().name());
        m.put("referenceNo", p.getReferenceNo());
        m.put("sourceEventId", p.getSourceEventId());
        m.put("sourceInstallmentId", p.getSourceInstallmentId());
        m.put("status", p.getStatus() == null ? null : p.getStatus().name());
        m.put("hrApprovedBy", p.getHrApprovedBy());
        m.put("hrApprovedAt", str(p.getHrApprovedAt()));
        m.put("paidBy", p.getPaidBy());
        m.put("paidAt", str(p.getPaidAt()));
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * One outbox business event plus one audit event per transition.
     * {@code before} is null only on a create; every other action carries
     * both sides so the trail says what actually changed.
     */
    private void emit(OffCyclePayment p, String action, Map<String, Object> before) {
        outbox.publish(PayrollEvents.TOPIC, "employee:" + p.getEmployeeId(),
                PayrollEvents.OFF_CYCLE_PAYMENT_CHANGED,
                new PayrollEvents.OffCyclePaymentChanged(
                        p.getId(), p.getEmployeeId(), action, p.getPaidOn(),
                        p.getAmount(), p.getSource().name(), p.getStatus().name(),
                        CurrentUserHolder.usernameOrSystem()));
        audit.emit("off_cycle_payment", String.valueOf(p.getId()),
                "off_cycle_" + action.toLowerCase(),
                before,
                snapshot(p),
                true);
    }
}

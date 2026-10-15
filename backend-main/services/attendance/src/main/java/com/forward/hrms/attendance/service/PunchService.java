package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.response.IngestPunchResult;
import com.forward.hrms.attendance.dto.response.PunchDto;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.AttendancePunch;
import com.forward.hrms.attendance.entity.DeviceEventLog;
import com.forward.hrms.attendance.entity.DevicePollState;
import com.forward.hrms.attendance.entity.PunchUnmatched;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.DeviceType;
import com.forward.hrms.attendance.enums.PunchSource;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.AttendanceDeviceRepository;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendancePunchRepository;
import com.forward.hrms.attendance.repository.DeviceEventLogRepository;
import com.forward.hrms.attendance.repository.DevicePollStateRepository;
import com.forward.hrms.attendance.repository.PunchUnmatchedRepository;
import com.forward.hrms.attendance.dto.response.PunchDtoMapper;
import com.forward.hrms.commons.batch.BatchOutcomes;
import com.forward.hrms.commons.batch.BatchRunner;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Punch ingest. Resolves the {@code attendance_date} via shift-boundary
 * logic, dedups same-time duplicates, and triggers
 * {@link AttendanceRecomputeService} for the affected day.
 *
 * <p>The cap on WEB punches lives here (2/day, hard-rejected on the
 * 3rd). BIOMETRIC / OPERATOR / REGULARIZED have no cap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PunchService {

    public static final Duration PRE_SHIFT_WINDOW = Duration.ofHours(2);
    public static final int WEB_DAILY_CAP = 2;

    private final AttendancePunchRepository punches;
    private final ShiftResolver shifts;
    private final AttendanceRecomputeService recompute;
    private final EmployeeClient employees;
    private final AttendanceEmployeeRepository attendanceEmployees;
    private final AttendanceDeviceRepository devices;
    private final DevicePollStateRepository pollState;
    private final PunchUnmatchedRepository unmatched;
    private final DeviceEventLogRepository deviceEventLogs;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final AttendancePeriodLockService periodLocks;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final BatchRunner batch;
    private final jakarta.persistence.EntityManager entityManager;

    /** Flush+clear cadence inside a big replay batch. Hibernate's
     * auto-flush before each query is O(N²) on session size, so a
     * straight 10k-row {@code ingestBiometricBatch} grows from ~31ms
     * per row at row 1 to several hundred ms at row 9999. Periodic
     * flush+clear keeps the session bounded and the cost per row
     * roughly constant. 500 is a balance — small enough that the
     * session stays light, large enough that the JDBC batch insert
     * actually amortizes. */
    private static final int BATCH_FLUSH_INTERVAL = 500;

    @Transactional
    public AttendancePunch recordSelfPunch(long employeeId, LocalDateTime at) {
        LocalDateTime when = at == null ? LocalDateTime.now() : at;
        rejectFuture(when);
        LocalDate workDate = resolveWorkDate(employeeId, when);
        periodLocks.assertOpen(workDate);
        if (punches.countByEmployeeAndDateAndSource(employeeId, workDate, PunchSource.WEB) >= WEB_DAILY_CAP) {
            throw new ConflictError(AttendanceErrorCode.PUNCH_LIMIT,
                    "self portal is capped at %d punches per day".formatted(WEB_DAILY_CAP));
        }
        return savePunch(employeeId, when, workDate, PunchSource.WEB, null, null);
    }

    @Transactional
    public AttendancePunch recordOperatorPunch(long employeeId, LocalDateTime when, String reason) {
        return recordOperatorPunch(employeeId, when, reason, null);
    }

    /**
     * @param direction IN / OUT pins the punch as the day's check-in /
     *                  check-out (see {@code AttendanceRecomputeService});
     *                  null derives it by position
     */
    public AttendancePunch recordOperatorPunch(long employeeId, LocalDateTime when, String reason,
                                               com.forward.hrms.attendance.enums.PunchDirection direction) {
        rejectFuture(when);
        EmployeeClient.EmployeeView ev = employees.getById(employeeId);
        LocalDate workDate = resolveWorkDate(ev.id(), when);
        periodLocks.assertOpen(workDate);
        AttendancePunch saved = savePunchNoRecompute(ev.id(), when, workDate, PunchSource.OPERATOR, null, reason);
        if (direction != null) {
            saved.setDirection(direction);
            saved = punches.save(saved);
        }
        recompute.recompute(ev.id(), workDate);
        return saved;
    }

    /**
     * Auto attendance: an OPERATOR punch pinned IN / OUT on an explicit work
     * date (no pre-shift routing — a night shift's next-morning OUT still
     * belongs to {@code workDate}). An existing punch at that exact instant
     * is re-pinned instead of duplicated. No recompute — the caller runs one
     * per day.
     */
    AttendancePunch savePinnedOperatorPunch(long employeeId, LocalDateTime when, LocalDate workDate,
                                            String reason,
                                            com.forward.hrms.attendance.enums.PunchDirection direction) {
        AttendancePunch p = punches.findByEmployeeIdAndPunchedAt(employeeId, when)
                .orElseGet(() -> savePunchNoRecompute(employeeId, when, workDate, PunchSource.OPERATOR,
                        null, reason));
        p.setDirection(direction);
        return punches.save(p);
    }

    /**
     * §7.attendance #28 — per-row partial via {@link BatchRunner}. Each
     * row runs in its own {@code REQUIRES_NEW} transaction; an unknown
     * employee, future instant, or duplicate exact-instant punch only
     * rolls back its own row. Returns both succeeded and failed rows so
     * the operator can surface partial-batch failures without digging
     * logs.
     */
    public com.forward.hrms.attendance.dto.response.BulkPunchResponse recordOperatorPunchesBulk(List<OperatorRow> rows) {
        BatchOutcomes<OperatorRow, AttendancePunch> outcomes = batch.runWithPartial(rows,
                row -> recordOperatorPunch(row.employeeId(), row.punchedAt(), row.reason()));
        List<PunchDto> succeeded = outcomes.succeeded().stream()
                .map(s -> PunchDtoMapper.of(s.result()))
                .toList();
        List<com.forward.hrms.attendance.dto.response.BulkPunchResponse.FailedRow> failed =
                new ArrayList<>(outcomes.failed().size());
        for (var f : outcomes.failed()) {
            log.warn("operator punches bulk: row employee={} at={} failed code={} msg={}",
                    f.item().employeeId(), f.item().punchedAt(), f.errorCode(), f.message());
            failed.add(new com.forward.hrms.attendance.dto.response.BulkPunchResponse.FailedRow(
                    f.item().employeeId(), f.item().punchedAt(), f.item().reason(),
                    f.errorCode(), f.message()));
        }
        return new com.forward.hrms.attendance.dto.response.BulkPunchResponse(succeeded, failed);
    }

    /**
     * Operator amendment of an existing punch. Captures a before-snapshot,
     * mutates {@code punched_at}, recalculates {@code attendance_date} (which
     * can shift to the previous day inside the pre-shift window), and
     * recomputes the day(s) the change touches. Audit emit carries the full
     * before/after for the per-entity timeline.
     */
    @Transactional
    public AttendancePunch amendPunch(long id, LocalDateTime newAt, String reason) {
        rejectFuture(newAt);
        AttendancePunch p = punches.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.PUNCH_NOT_FOUND,
                        "punch " + id + " not found"));
        periodLocks.assertOpen(p.getAttendanceDate());
        Map<String, Object> before = snapshot(p);

        // Same-instant collision against another row would silently overwrite
        // dedup's invariant; reject as a duplicate.
        Optional<AttendancePunch> existing = punches.findByEmployeeIdAndPunchedAt(p.getEmployeeId(), newAt);
        if (existing.isPresent() && !existing.get().getId().equals(p.getId())) {
            throw new ConflictError(AttendanceErrorCode.PUNCH_DUPLICATE,
                    "another punch already exists for that exact instant");
        }

        LocalDate oldDate = p.getAttendanceDate();
        LocalDate newDate = resolveWorkDate(p.getEmployeeId(), newAt);
        periodLocks.assertOpen(newDate);
        p.setPunchedAt(newAt);
        p.setAttendanceDate(newDate);
        p.setReason(reason);
        AttendancePunch updated = punches.save(p);

        recompute.recompute(p.getEmployeeId(), oldDate);
        if (!newDate.equals(oldDate)) {
            recompute.recompute(p.getEmployeeId(), newDate);
        }

        Map<String, Object> after = snapshot(updated);
        after.put("amendmentReason", reason);
        audit.emit("punch", String.valueOf(id), "amended", before, after, true);
        return updated;
    }

    /**
     * Operator hard-delete. Captures a before-snapshot, removes the row,
     * recomputes the day, and audits before/null with the operator's
     * reason. Hard-delete is the locked-in semantic — the audit event is
     * the history.
     */
    @Transactional
    public void deletePunch(long id, String reason) {
        AttendancePunch p = punches.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.PUNCH_NOT_FOUND,
                        "punch " + id + " not found"));
        Map<String, Object> before = snapshot(p);
        long employeeId = p.getEmployeeId();
        LocalDate workDate = p.getAttendanceDate();
        periodLocks.assertOpen(workDate);

        punches.delete(p);
        // Force a flush before recompute so the day's punch list reflects the removal.
        punches.flush();
        recompute.recompute(employeeId, workDate);

        audit.emit("punch", String.valueOf(id), "deleted",
                before, Map.of("deletionReason", reason), true);
    }

    private static Map<String, Object> snapshot(AttendancePunch p) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", p.getId());
        out.put("employeeId", p.getEmployeeId());
        out.put("punchedAt", p.getPunchedAt().toString());
        out.put("attendanceDate", p.getAttendanceDate().toString());
        out.put("source", p.getSource() == null ? null : p.getSource().name());
        out.put("deviceId", p.getDeviceId());
        out.put("reason", p.getReason());
        return out;
    }

    public record OperatorRow(long employeeId, LocalDateTime punchedAt, String reason) {}

    /**
     * Bulk biometric ingest from attendance-poller. Each row carries
     * {@code (employeeCode, punchedAt, deviceId)} — the device's user_id
     * field IS the employee_code, so we resolve directly via the
     * {@code attendance_employees} snapshot. No per-device bridge join.
     *
     * <p>Unknown-snapshot rows land in {@code skipped[]} (so the poller
     * gets telemetry) AND get persisted to {@code attendance_punch_unmatched}
     * — the scheduled replay job in {@link DevicePunchReplayJob} picks
     * them up once the snapshot row catches up. Future timestamps and
     * same-time duplicates are silently dropped (devices retransmit on
     * retry).
     *
     * <p>After processing, the per-device {@code last_punched_at}
     * advances to {@code max(punchedAt)} of the batch (only forward).
     * The poller filters its next pull against this value, so a cycle
     * that fails before sending the heartbeat is still safe — the next
     * cycle re-sends, and the unique-key insert deduplicates.
     */
    @Transactional
    public IngestPunchResult ingestBiometricBatch(List<BiometricRow> rows) {
        long t0 = System.currentTimeMillis();
        log.info("ingestBiometricBatch: starting with {} rows", rows.size());
        List<PunchDto> ingested = new ArrayList<>(rows.size());
        List<IngestPunchResult.Skipped> skipped = new ArrayList<>();
        Map<Long, LocalDateTime> hwmByDevice = new java.util.HashMap<>();
        // One catalog lookup per distinct device in the batch — keyed
        // for the per-row fork between attendance flow and the non-
        // attendance device-event log.
        Map<Long, DeviceType> deviceTypes = loadDeviceTypes(rows);
        // Collect distinct (employeeId, workDate) pairs the batch touched
        // so we can run recompute exactly once per day, not once per punch.
        // For a 10k-row replay batch this drops recompute calls from 10k
        // to typically 50–500 (depending on how many days × employees the
        // batch spans), which is what makes the replay job tick complete
        // in seconds instead of ~15 minutes.
        Set<DayKey> daysToRecompute = new java.util.HashSet<>();
        // One lock lookup per distinct (year, month) the batch touches.
        // Late device punches for a locked month are dropped — accepting
        // them would change attendance after payroll froze the period.
        Map<YearMonth, Boolean> lockedPeriodCache = new java.util.HashMap<>();
        int rowsSinceFlush = 0;
        for (BiometricRow row : rows) {
            // HWM advances against every record we *processed*, including
            // skipped/unknown ones — they came off the device, the poller
            // shouldn't re-pull them next cycle.
            hwmByDevice.merge(row.deviceId(), row.punchedAt(),
                    (a, b) -> a.isAfter(b) ? a : b);

            DeviceType type = deviceTypes.getOrDefault(row.deviceId(), DeviceType.ATTENDANCE);
            if (type != DeviceType.ATTENDANCE) {
                // Canteen / access-log device — store the raw event and
                // skip the entire attendance flow. No work-date, no
                // recompute, no punch_recorded event, no unmatched queue.
                Long resolvedEmployeeId = attendanceEmployees.findByEmployeeCode(row.employeeCode())
                        .map(AttendanceEmployee::getEmployeeId)
                        .orElse(null);
                recordDeviceEvent(row, type, resolvedEmployeeId);
                continue;
            }

            Optional<AttendanceEmployee> snapshot =
                    attendanceEmployees.findByEmployeeCode(row.employeeCode());
            if (snapshot.isEmpty()) {
                skipped.add(new IngestPunchResult.Skipped(
                        row.employeeCode(), row.deviceId(), row.punchedAt(),
                        AttendanceErrorCode.UNKNOWN_DEVICE_USER.name()));
                queueUnmatched(row);
                continue;
            }
            long employeeId = snapshot.get().getEmployeeId();
            try {
                rejectFuture(row.punchedAt());
            } catch (UnprocessableError ex) {
                log.warn("skipping future biometric punch employee_code={} device={} at={}",
                        row.employeeCode(), row.deviceId(), row.punchedAt());
                continue;
            }
            if (punches.findByEmployeeIdAndPunchedAt(employeeId, row.punchedAt()).isPresent()) continue;
            LocalDate workDate = resolveWorkDate(employeeId, row.punchedAt());
            boolean locked = lockedPeriodCache.computeIfAbsent(YearMonth.from(workDate),
                    ym -> periodLocks.isLocked(ym.getYear(), ym.getMonthValue()));
            if (locked) {
                skipped.add(new IngestPunchResult.Skipped(
                        row.employeeCode(), row.deviceId(), row.punchedAt(),
                        AttendanceErrorCode.PERIOD_LOCKED.name()));
                continue;
            }
            AttendancePunch saved = savePunchNoRecompute(employeeId, row.punchedAt(), workDate,
                    PunchSource.BIOMETRIC, row.deviceId(), null);
            ingested.add(PunchDtoMapper.of(saved));
            daysToRecompute.add(new DayKey(employeeId, workDate));
            if (++rowsSinceFlush >= BATCH_FLUSH_INTERVAL) {
                // Bound the persistence-context size so the next dirty
                // check / select doesn't have to scan thousands of
                // pending entities. Saved punches stay in the DB
                // because flush() syncs them; clear() just detaches
                // them from the in-memory session.
                entityManager.flush();
                entityManager.clear();
                rowsSinceFlush = 0;
            }
        }
        long tInsertDone = System.currentTimeMillis();
        log.info("ingestBiometricBatch: persisted {} punches in {}ms; recomputing {} (employee, day) pairs",
                ingested.size(), tInsertDone - t0, daysToRecompute.size());
        for (DayKey key : daysToRecompute) {
            recompute.recompute(key.employeeId(), key.workDate());
        }
        hwmByDevice.forEach(this::advanceHwm);
        log.info("ingestBiometricBatch: done in {}ms (recompute={}ms)",
                System.currentTimeMillis() - t0, System.currentTimeMillis() - tInsertDone);
        return new IngestPunchResult(ingested, skipped);
    }

    private record DayKey(long employeeId, LocalDate workDate) {}

    private Map<Long, DeviceType> loadDeviceTypes(List<BiometricRow> rows) {
        Set<Long> deviceIds = new java.util.HashSet<>();
        for (BiometricRow row : rows) {
            if (row.deviceId() != null) deviceIds.add(row.deviceId());
        }
        if (deviceIds.isEmpty()) return Map.of();
        Map<Long, DeviceType> out = new java.util.HashMap<>();
        devices.findAllById(deviceIds).forEach(d -> out.put(d.getId(), d.getDeviceType()));
        return out;
    }

    /**
     * Persist a canteen / access-log event. Idempotent on
     * {@code (device_id, employee_code, punched_at)} — biometric
     * retransmits are silent. {@code employee_id} is best-effort: the
     * raw employee_code is the source of truth and stays on the row
     * even when the snapshot doesn't yet know the code.
     */
    private void recordDeviceEvent(BiometricRow row, DeviceType type, Long employeeId) {
        try {
            DeviceEventLog e = new DeviceEventLog();
            e.setDeviceId(row.deviceId());
            e.setEmployeeCode(row.employeeCode());
            e.setEmployeeId(employeeId);
            e.setPunchedAt(row.punchedAt());
            e.setDeviceType(type);
            deviceEventLogs.saveAndFlush(e);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.debug("device event already logged: device={} employee_code={} at={}",
                    row.deviceId(), row.employeeCode(), row.punchedAt());
        }
    }

    /**
     * Insert into the unmatched queue, idempotent on
     * {@code (device_id, employee_code, punched_at)}. We catch the
     * unique-violation (the poller may resend on retry) instead of
     * pre-checking — one row, one statement.
     */
    private void queueUnmatched(BiometricRow row) {
        try {
            PunchUnmatched u = new PunchUnmatched();
            u.setDeviceId(row.deviceId());
            u.setEmployeeCode(row.employeeCode());
            u.setPunchedAt(row.punchedAt());
            u.setFirstSeenAt(LocalDateTime.now());
            unmatched.saveAndFlush(u);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Already queued from a prior cycle — fine, the replay job will pick it up.
            log.debug("unmatched row already queued: device={} employee_code={} at={}",
                    row.deviceId(), row.employeeCode(), row.punchedAt());
        }
    }

    private void advanceHwm(long deviceId, LocalDateTime newHwm) {
        DevicePollState state = pollState.findById(deviceId)
                .orElseGet(() -> {
                    log.debug("creating DevicePollState for deviceId={}", deviceId);
                    DevicePollState s = new DevicePollState();
                    s.setDeviceId(deviceId);
                    return s;
                });
        if (state.getLastPunchedAt() == null || state.getLastPunchedAt().isBefore(newHwm)) {
            state.setLastPunchedAt(newHwm);
            pollState.save(state);
        }
    }

    /** Used by RegularizationService to write punches on final approval. */
    @Transactional
    public Optional<AttendancePunch> writeRegularizedPunch(long employeeId, LocalDateTime punchedAt,
                                                           LocalDate verbatimDate, long requestId) {
        Optional<AttendancePunch> saved = writeRegularizedPunchNoRecompute(
                employeeId, punchedAt, verbatimDate, requestId);
        saved.ifPresent(p -> recompute.recompute(employeeId, verbatimDate));
        return saved;
    }

    /**
     * §7.attendance #16 — internal helper that writes the punch row and
     * fires the outbox event but does NOT call recompute. The single-punch
     * call site keeps {@link #writeRegularizedPunch}; the two-punch call
     * site (regularization approve with both check-in and check-out) calls
     * this twice and then runs one recompute via the public
     * {@link #recomputeForDate} helper. Avoids two recomputes + two
     * {@code attendance.punch_recorded} events sandwiched around the same
     * day's projection.
     */
    @Transactional
    public Optional<AttendancePunch> writeRegularizedPunchNoRecompute(long employeeId, LocalDateTime punchedAt,
                                                                       LocalDate verbatimDate, long requestId) {
        if (punches.findByEmployeeIdAndPunchedAt(employeeId, punchedAt).isPresent()) {
            return Optional.empty();
        }
        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(employeeId);
        p.setPunchedAt(punchedAt);
        p.setAttendanceDate(verbatimDate);
        p.setSource(PunchSource.REGULARIZED);
        p.setReason("regularization #" + requestId);
        AttendancePunch saved = punches.save(p);
        outbox.publish(AttendanceEvents.TOPIC, "employee:" + employeeId,
                AttendanceEvents.PUNCH_RECORDED,
                new AttendanceEvents.PunchRecorded(employeeId, punchedAt, verbatimDate,
                        PunchSource.REGULARIZED.name(), saved.getReason()));
        return Optional.of(saved);
    }

    /** §7.attendance #16 — exposed so RegularizationService can recompute once after a paired write. */
    @Transactional
    public void recomputeForDate(long employeeId, LocalDate date) {
        recompute.recompute(employeeId, date);
    }

    private AttendancePunch savePunch(long employeeId, LocalDateTime when, LocalDate workDate,
                                      PunchSource source, Long deviceId, String reason) {
        AttendancePunch saved = savePunchNoRecompute(employeeId, when, workDate, source, deviceId, reason);
        recompute.recompute(employeeId, workDate);
        return saved;
    }

    /**
     * Inserts the punch row + emits the outbox event + audit (if OPERATOR),
     * but skips the recompute. Callers that want to batch many punches
     * across the same {@code (employeeId, workDate)} pairs (e.g. the
     * replay job ingesting a 10k-row backlog) call this and then run
     * recompute once per distinct pair at the end of the batch — drops
     * tick wall-time from minutes to seconds on big batches.
     */
    private AttendancePunch savePunchNoRecompute(long employeeId, LocalDateTime when, LocalDate workDate,
                                                  PunchSource source, Long deviceId, String reason) {
        // Same-time dedup is silent on biometric ingest but we want a
        // clear conflict on web / operator paths so the user knows.
        Optional<AttendancePunch> existing = punches.findByEmployeeIdAndPunchedAt(employeeId, when);
        if (existing.isPresent()) {
            if (source == PunchSource.BIOMETRIC) return existing.get();
            throw new ConflictError(AttendanceErrorCode.PUNCH_DUPLICATE,
                    "duplicate punch for that exact instant");
        }

        AttendancePunch p = new AttendancePunch();
        p.setEmployeeId(employeeId);
        p.setPunchedAt(when);
        p.setAttendanceDate(workDate);
        p.setSource(source);
        p.setDeviceId(deviceId);
        p.setReason(reason);
        AttendancePunch saved = punches.save(p);

        outbox.publish(AttendanceEvents.TOPIC, "employee:" + employeeId,
                AttendanceEvents.PUNCH_RECORDED,
                new AttendanceEvents.PunchRecorded(employeeId, when, workDate, source.name(), reason));
        if (source == PunchSource.OPERATOR) {
            audit.emit("punch", employeeId + ":" + when.toString(), "operator_added",
                    null, Map.of("reason", reason == null ? "" : reason), true);
        }
        return saved;
    }

    /**
     * Resolves the {@code attendance_date} (work date) for a punch, for every
     * shift alike. A work date "opens" {@link #PRE_SHIFT_WINDOW} before its
     * shift starts and stays open until the next one opens, so the punch
     * belongs to the latest work date that has opened by then:
     * <ul>
     *   <li>within 2h before the shift start, or any time after it, it is
     *       that day's punch (early arrival, check-in, late arrival,
     *       check-out, evening OT);</li>
     *   <li>more than 2h before the day's shift start, it is the previous
     *       work date's — an OT check-out after midnight (07:45 shift, out
     *       at 02:00) or a night shift's check-out (20:00–05:00, out at
     *       05:00).</li>
     * </ul>
     * A shift starting just after midnight (e.g. 00:30) opens the evening
     * before, so a 23:00 arrival is the next work date's. Days with no
     * resolvable shift fall back to the calendar date.
     */
    public LocalDate resolveWorkDate(long employeeId, LocalDateTime when) {
        // §0.5.B: server, JVM, DB all run TZ=Asia/Karachi. LocalDateTime
        // comparisons are already in PKT — no ZoneId gymnastics needed.
        LocalDate punchedDate = when.toLocalDate();

        // Tomorrow's work date can only have opened already when its shift
        // starts within the window after midnight — i.e. late-evening punches.
        if (!when.toLocalTime().isBefore(java.time.LocalTime.MIDNIGHT.minus(PRE_SHIFT_WINDOW))) {
            LocalDate tomorrow = punchedDate.plusDays(1);
            Optional<ShiftResolution> next = shifts.resolve(employeeId, tomorrow);
            if (next.isPresent() && !when.isBefore(opensAt(tomorrow, next.get()))) {
                return tomorrow;
            }
        }

        Optional<ShiftResolution> today = shifts.resolve(employeeId, punchedDate);
        if (today.isEmpty() || !when.isBefore(opensAt(punchedDate, today.get()))) {
            return punchedDate;
        }
        // Before today's shift opens: the previous work date's punch — unless
        // there was no shift then (e.g. first day after joining), in which
        // case it's an unusually early arrival for today.
        LocalDate yesterday = punchedDate.minusDays(1);
        return shifts.resolve(employeeId, yesterday).isPresent() ? yesterday : punchedDate;
    }

    /** When a work date starts taking punches: its shift start less the pre-shift window. */
    static LocalDateTime opensAt(LocalDate workDate, ShiftResolution shift) {
        return workDate.atTime(shift.startTime()).minus(PRE_SHIFT_WINDOW);
    }

    /**
     * The instant a clock time lands on for a work date whose window is
     * {@code [opens, closes)} — the inverse of {@link #resolveWorkDate}: tried
     * on the day before, the work date itself and the day after, the one
     * inside the window wins (a 07:45 shift's 02:00 is the next morning, a
     * 00:30 shift's 23:00 the evening before). Null when the time falls in
     * none of them (a window shorter than 24h around a shift change).
     */
    static LocalDateTime instantInWindow(LocalDate workDate, java.time.LocalTime time,
                                         LocalDateTime opens, LocalDateTime closes) {
        LocalDateTime onDay = workDate.atTime(time);
        for (LocalDateTime c : List.of(onDay.minusDays(1), onDay, onDay.plusDays(1))) {
            if (!c.isBefore(opens) && c.isBefore(closes)) return c;
        }
        return null;
    }

    private void rejectFuture(LocalDateTime when) {
        if (when.isAfter(LocalDateTime.now().plusSeconds(60))) {
            // §7.attendance #58 — bump a counter so persistent device clock
            // skew is visible on dashboards instead of being a stream of
            // single-line warnings.
            meterRegistry.counter("attendance.punch.future_skipped").increment();
            throw new UnprocessableError(AttendanceErrorCode.PUNCH_FUTURE,
                    "punch is in the future");
        }
    }

    public record BiometricRow(String employeeCode, LocalDateTime punchedAt, Long deviceId) {}
}

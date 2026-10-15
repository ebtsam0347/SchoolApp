package com.forward.hrms.attendance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.attendance.dto.request.UploadTemplateRequest;
import com.forward.hrms.attendance.dto.response.DeviceSyncTaskDto;
import com.forward.hrms.attendance.dto.response.DeviceUserDto;
import com.forward.hrms.attendance.dto.response.EmployeeTemplateDto;
import com.forward.hrms.attendance.dto.response.UnenrollResultDto;
import com.forward.hrms.attendance.entity.AttendanceDevice;
import com.forward.hrms.attendance.entity.AttendanceDeviceUser;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.AttendanceEmployeeTemplate;
import com.forward.hrms.attendance.entity.DeviceSyncTask;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.DeviceSyncTaskStatus;
import com.forward.hrms.attendance.enums.DeviceSyncTaskType;
import com.forward.hrms.attendance.enums.TemplateKind;
import com.forward.hrms.attendance.repository.AttendanceDeviceRepository;
import com.forward.hrms.attendance.repository.AttendanceDeviceUserRepository;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendanceEmployeeTemplateRepository;
import com.forward.hrms.attendance.repository.DeviceSyncTaskRepository;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sync-task queue lifecycle for the attendance-poller sidecar: claim →
 * complete | fail | retry, plus the HR-facing list / device-user views
 * and the employee-template upload + auto fan-out path. All callers go
 * through the poller's service-account JWT — no human reaches these
 * methods directly.
 *
 * <p>Backoff on retryable failure is {@code min(60s × 2^attempts, 1h)};
 * after {@code hrms.attendance.device-sync.max-attempts} (default 5)
 * the task parks in FAILED for HR to retry.
 */
@Slf4j
@Service
public class DeviceSyncQueueService {

    private static final Duration BACKOFF_BASE = Duration.ofSeconds(60);
    private static final Duration BACKOFF_CAP = Duration.ofHours(1);

    /**
     * §7.attendance #37 — fingerprint templates are typically 30-50 KB;
     * face templates run a bit larger but still well under 1 MB. Cap at
     * 1 MB before decoding so a misbehaving poller can't push 100 MB
     * binaries through this endpoint.
     */
    private static final int MAX_TEMPLATE_BYTES = 1 * 1024 * 1024;

    private final AttendanceDeviceRepository devices;
    private final AttendanceDeviceUserRepository deviceUsers;
    private final AttendanceEmployeeRepository attendanceEmployees;
    private final AttendanceEmployeeTemplateRepository templates;
    private final DeviceSyncTaskRepository tasks;
    private final ObjectMapper json;
    private final AuditEmitter audit;
    private final int maxAttempts;

    public DeviceSyncQueueService(AttendanceDeviceRepository devices,
                                  AttendanceDeviceUserRepository deviceUsers,
                                  AttendanceEmployeeRepository attendanceEmployees,
                                  AttendanceEmployeeTemplateRepository templates,
                                  DeviceSyncTaskRepository tasks,
                                  ObjectMapper json,
                                  AuditEmitter audit,
                                  @Value("${hrms.attendance.device-sync.max-attempts:5}") int maxAttempts) {
        this.devices = devices;
        this.deviceUsers = deviceUsers;
        this.attendanceEmployees = attendanceEmployees;
        this.templates = templates;
        this.tasks = tasks;
        this.json = json;
        this.audit = audit;
        this.maxAttempts = maxAttempts;
    }

    // -------------------------------------------------------------------
    // Sync queue: claim → complete | fail
    // -------------------------------------------------------------------

    @Transactional
    public List<DeviceSyncTaskDto> claimNext(long deviceId, int limit) {
        requireDevice(deviceId);
        List<DeviceSyncTask> claimed = tasks.lockClaimable(deviceId, Math.max(1, Math.min(limit, 100)));
        LocalDateTime now = LocalDateTime.now();
        for (DeviceSyncTask t : claimed) {
            t.setStatus(DeviceSyncTaskStatus.IN_PROGRESS);
            t.setClaimedAt(now);
        }
        return claimed.stream().map(t -> DeviceSyncTaskDto.of(t, json)).toList();
    }

    @Transactional
    public void complete(long deviceId, long taskId, String detail) {
        DeviceSyncTask task = loadTaskForDevice(deviceId, taskId);
        if (task.getStatus() != DeviceSyncTaskStatus.IN_PROGRESS) {
            throw new UnprocessableError(AttendanceErrorCode.SYNC_TASK_NOT_FOUND,
                    "task is not IN_PROGRESS, current status: " + task.getStatus());
        }
        task.setStatus(DeviceSyncTaskStatus.DONE);
        task.setCompletedAt(LocalDateTime.now());
        task.setLastError(null);

        applySideEffectsOnComplete(task);
        if (detail != null && !detail.isBlank()) {
            log.debug("sync task {} done — {}", taskId, detail);
        }
    }

    @Transactional
    public void fail(long deviceId, long taskId, String error, Boolean retryable) {
        DeviceSyncTask task = loadTaskForDevice(deviceId, taskId);
        if (task.getStatus() != DeviceSyncTaskStatus.IN_PROGRESS) {
            throw new UnprocessableError(AttendanceErrorCode.SYNC_TASK_NOT_FOUND,
                    "task is not IN_PROGRESS, current status: " + task.getStatus());
        }
        boolean canRetry = !Boolean.FALSE.equals(retryable);
        int nextAttempts = task.getAttempts() + 1;
        task.setAttempts(nextAttempts);
        task.setLastError(error);
        if (!canRetry || nextAttempts >= maxAttempts) {
            task.setStatus(DeviceSyncTaskStatus.FAILED);
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setStatus(DeviceSyncTaskStatus.PENDING);
            task.setClaimedAt(null);
            task.setScheduledFor(LocalDateTime.now().plus(backoffFor(nextAttempts)));
        }
    }

    // -------------------------------------------------------------------
    // HR-facing: list / retry
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<DeviceSyncTaskDto> listTasks(long deviceId,
                                                     Collection<DeviceSyncTaskStatus> statuses,
                                                     Pageable pageable) {
        requireDevice(deviceId);
        Pageable resolved = PageableDefaults.apply(pageable, 50, 200,
                Sort.by(Sort.Direction.DESC, "scheduledFor"));
        Page<DeviceSyncTask> page = (statuses == null || statuses.isEmpty())
                ? tasks.findByDeviceId(deviceId, resolved)
                : tasks.findByDeviceIdAndStatusIn(deviceId, statuses, resolved);
        return PageResponse.from(page,
                page.getContent().stream().map(t -> DeviceSyncTaskDto.of(t, json)).toList());
    }

    /**
     * Re-arm a FAILED task: flip back to PENDING with attempts=0,
     * scheduled_for=now, lastError=null. The next poller cycle picks
     * it up via {@link DeviceSyncTaskRepository#lockClaimable}.
     */
    @Transactional
    public DeviceSyncTaskDto retryTask(long deviceId, long taskId) {
        DeviceSyncTask task = loadTaskForDevice(deviceId, taskId);
        if (task.getStatus() != DeviceSyncTaskStatus.FAILED) {
            throw new UnprocessableError(AttendanceErrorCode.SYNC_TASK_NOT_FAILED,
                    "task is not FAILED, current status: " + task.getStatus());
        }
        task.setStatus(DeviceSyncTaskStatus.PENDING);
        task.setAttempts(0);
        task.setLastError(null);
        task.setClaimedAt(null);
        task.setCompletedAt(null);
        task.setScheduledFor(LocalDateTime.now());
        return DeviceSyncTaskDto.of(task, json);
    }

    // -------------------------------------------------------------------
    // HR-facing: device users
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<DeviceUserDto> listDeviceUsers(long deviceId, Pageable pageable) {
        requireDevice(deviceId);
        Pageable resolved = PageableDefaults.apply(pageable, 100, 500,
                Sort.by(Sort.Direction.ASC, "employeeId"));
        Page<AttendanceDeviceUser> page = deviceUsers.findByDeviceId(deviceId, resolved);
        return PageResponse.from(page,
                page.getContent().stream().map(DeviceUserDto::of).toList());
    }

    /**
     * HR removes an employee from a device: queue DELETE_USER for the
     * poller and drop the bridge row immediately so no further
     * UPSERT_USER / PUSH_TEMPLATE lands on this (device, employee) pair.
     * If the device is offline, the queued task drains when it returns.
     */
    @Transactional
    public void removeUserFromDevice(long deviceId, long employeeId) {
        requireDevice(deviceId);
        AttendanceDeviceUser bridge = deviceUsers.findByDeviceIdAndEmployeeId(deviceId, employeeId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.DEVICE_USER_NOT_FOUND,
                        "employee " + employeeId + " is not enrolled on device " + deviceId));
        String employeeCode = requireEmployeeCode(employeeId);
        queueDeleteUser(deviceId, employeeId, employeeCode);
        deviceUsers.delete(bridge);
        audit.emit("attendance_device_user", deviceId + ":" + employeeId, "removed",
                Map.of("employeeCode", employeeCode),
                Map.of("removed", true), false);
    }

    /**
     * Post-adoption cleanup — bulk remove enrolled users from a device.
     * Matched employees (by id) get a DELETE_USER keyed by employee_code and
     * their bridge row dropped; stray device users (raw user_id, no employee)
     * get a DELETE_USER keyed by the raw id with no bridge to drop. Both go
     * through the same poller DELETE_USER path. Unknown employee ids and blank
     * raw ids are skipped, not failed — the caller's reconciliation list is the
     * source of truth and a stale entry shouldn't abort the batch.
     */
    @Transactional
    public UnenrollResultDto unenroll(long deviceId, List<Long> employeeIds, List<String> deviceUserIds) {
        requireDevice(deviceId);
        if ((employeeIds == null || employeeIds.isEmpty())
                && (deviceUserIds == null || deviceUserIds.isEmpty())) {
            throw new UnprocessableError(AttendanceErrorCode.EMPLOYEES_REQUIRED,
                    "provide at least one of employeeIds or deviceUserIds");
        }
        int queued = 0, droppedBridges = 0, skipped = 0;
        if (employeeIds != null) {
            for (Long employeeId : employeeIds) {
                if (employeeId == null) { skipped++; continue; }
                AttendanceEmployee emp = attendanceEmployees.findByEmployeeId(employeeId).orElse(null);
                if (emp == null) { skipped++; continue; }
                queueDeleteUser(deviceId, employeeId, emp.getEmployeeCode());
                queued++;
                Optional<AttendanceDeviceUser> bridge =
                        deviceUsers.findByDeviceIdAndEmployeeId(deviceId, employeeId);
                if (bridge.isPresent()) { deviceUsers.delete(bridge.get()); droppedBridges++; }
                audit.emit("attendance_device_user", deviceId + ":" + employeeId, "unenrolled",
                        Map.of("employeeCode", emp.getEmployeeCode()),
                        Map.of("removed", true), false);
            }
        }
        if (deviceUserIds != null) {
            for (String raw : deviceUserIds) {
                String code = raw == null ? "" : raw.trim();
                if (code.isEmpty()) { skipped++; continue; }
                // No employee record: park employee_id=0 (same convention as
                // SNAPSHOT_DEVICE) and key the DELETE_USER on the raw user_id.
                queueDeleteUser(deviceId, 0L, code);
                queued++;
                audit.emit("attendance_device_user", deviceId + ":raw:" + code, "unenrolled",
                        Map.of("deviceUserId", code), Map.of("removed", true), false);
            }
        }
        log.info("Un-enrolled from device {} — queued={} droppedBridges={} skipped={}",
                deviceId, queued, droppedBridges, skipped);
        return new UnenrollResultDto(queued, droppedBridges, skipped);
    }

    /**
     * Termination fan-out — for every active device the employee is on,
     * queue DELETE_USER and drop the bridge row. Idempotent: if the
     * employee has no bridge rows the call is a no-op.
     */
    @Transactional
    public int queueDeleteForAllDevices(long employeeId) {
        List<AttendanceDeviceUser> assignments = deviceUsers.findByEmployeeId(employeeId);
        if (assignments.isEmpty()) return 0;
        String employeeCode = requireEmployeeCode(employeeId);
        java.util.Set<Long> activeDeviceIds = devices.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(AttendanceDevice::getId)
                .collect(java.util.stream.Collectors.toSet());
        int queued = 0;
        for (AttendanceDeviceUser bridge : assignments) {
            // §7.attendance — skip inactive devices: no poller drains
            // their queue. Bridge row is still removed so the employee's
            // re-onboarding (if any) starts clean.
            if (activeDeviceIds.contains(bridge.getDeviceId())) {
                queueDeleteUser(bridge.getDeviceId(), employeeId, employeeCode);
                queued++;
            }
            deviceUsers.delete(bridge);
        }
        if (queued > 0) {
            log.info("Queued DELETE_USER for employee {} across {} active device(s)",
                    employeeId, queued);
        }
        return queued;
    }

    // -------------------------------------------------------------------
    // Templates
    // -------------------------------------------------------------------

    /**
     * HR triggers a fingerprint capture off a device — typically the
     * enrollment device, right after the employee placed a finger on the
     * keypad. Queues a CAPTURE_TEMPLATE task; the poller reads each present
     * finger and POSTs it to {@link #uploadTemplate}, whose fan-out then
     * pushes the prints to every other device the employee is on. The
     * employee must already be on this device (bridge row) — capture reads
     * what the device holds, it doesn't create the user. The completing
     * poller flips this device's {@code hasFingerprint} (see
     * {@link #applySideEffectsOnComplete}).
     */
    @Transactional
    public DeviceSyncTaskDto queueCaptureTemplate(long deviceId, long employeeId) {
        requireDevice(deviceId);
        deviceUsers.findByDeviceIdAndEmployeeId(deviceId, employeeId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.DEVICE_USER_NOT_FOUND,
                        "employee " + employeeId + " is not enrolled on device " + deviceId));
        String employeeCode = requireEmployeeCode(employeeId);
        DeviceSyncTask task = new DeviceSyncTask();
        task.setDeviceId(deviceId);
        task.setEmployeeId(employeeId);
        task.setTaskType(DeviceSyncTaskType.CAPTURE_TEMPLATE);
        task.setPayload(captureTemplatePayload(employeeCode));
        task.setStatus(DeviceSyncTaskStatus.PENDING);
        task.setScheduledFor(LocalDateTime.now());
        DeviceSyncTask saved = tasks.save(task);
        audit.emit("attendance_device_user", deviceId + ":" + employeeId, "capture_template.queued",
                null, Map.of("employeeCode", employeeCode, "taskId", saved.getId()), false);
        return DeviceSyncTaskDto.of(saved, json);
    }

    @Transactional(readOnly = true)
    public EmployeeTemplateDto getTemplate(long templateId) {
        return EmployeeTemplateDto.of(templates.findById(templateId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.TEMPLATE_NOT_FOUND,
                        "template not found: " + templateId)));
    }

    /**
     * Stores a captured template (or replaces the prior one for
     * (employee, kind, finger)). Store-only — provisioning to devices is
     * a separate, deliberate step: HR picks the target device(s) and
     * calls {@link #pushTemplatesToDevice}. No automatic fan-out, so a
     * capture never lands a print on a device the operator didn't choose.
     */
    @Transactional
    public EmployeeTemplateDto uploadTemplate(UploadTemplateRequest req) {
        validateTemplateShape(req);
        requireDevice(req.capturedDeviceId());

        // §7.attendance #37 — base64 inflates by 4/3, so the encoded form
        // is bounded by ceil(MAX_TEMPLATE_BYTES * 4 / 3). Reject above
        // that before we even attempt to decode.
        if (req.bytes() == null || req.bytes().length() > (MAX_TEMPLATE_BYTES * 4 / 3) + 4) {
            throw new UnprocessableError(AttendanceErrorCode.TEMPLATE_KIND_INVALID,
                    "template bytes exceed " + MAX_TEMPLATE_BYTES + " bytes");
        }
        byte[] bytes;
        try {
            // §7.attendance #36 — MIME decoder is permissive on URL-safe
            // and standard alphabets, plus tolerates whitespace. Pollers
            // historically picked one or the other depending on json
            // serialization choices.
            bytes = Base64.getMimeDecoder().decode(req.bytes());
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableError(AttendanceErrorCode.TEMPLATE_KIND_INVALID,
                    "template bytes are not valid base64");
        }
        if (bytes.length > MAX_TEMPLATE_BYTES) {
            throw new UnprocessableError(AttendanceErrorCode.TEMPLATE_KIND_INVALID,
                    "template payload exceeds " + MAX_TEMPLATE_BYTES + " bytes");
        }

        AttendanceEmployeeTemplate row = templates
                .findByEmployeeIdAndKindAndFingerId(req.employeeId(), req.kind(), req.fingerId())
                .orElseGet(() -> {
                    log.debug("creating AttendanceEmployeeTemplate for employeeId={} kind={} fingerId={}",
                            req.employeeId(), req.kind(), req.fingerId());
                    return new AttendanceEmployeeTemplate();
                });
        row.setEmployeeId(req.employeeId());
        row.setKind(req.kind());
        row.setFingerId(req.fingerId());
        row.setTemplate(bytes);
        row.setTemplateFormat(req.format());
        row.setCapturedDeviceId(req.capturedDeviceId());
        row.setCapturedAt(LocalDateTime.now());
        AttendanceEmployeeTemplate saved = templates.save(row);
        return EmployeeTemplateDto.of(saved);
    }

    /**
     * Provision an employee's captured fingerprints onto a specific
     * device the operator chose — the deliberate counterpart to
     * {@link #uploadTemplate}'s store-only capture. Queues one
     * PUSH_TEMPLATE per stored FINGERPRINT template; the poller pushes
     * the bytes and the bridge's {@code hasFingerprint} flips on
     * completion. Reusable any time (e.g. when a new gate is added) —
     * no re-scan needed since the print is already in the central store.
     *
     * <p>The employee must already be enrolled on the target device
     * (bridge row) and the device must be active. FACE templates are
     * skipped — push isn't implemented for them (see the poller's
     * {@code _push_template}).
     */
    @Transactional
    public List<DeviceSyncTaskDto> pushTemplatesToDevice(long deviceId, long employeeId) {
        AttendanceDevice device = requireDevice(deviceId);
        if (!device.isActive()) {
            throw new UnprocessableError(AttendanceErrorCode.DEVICE_INACTIVE,
                    "device " + deviceId + " is not active — no poller drains its queue");
        }
        deviceUsers.findByDeviceIdAndEmployeeId(deviceId, employeeId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.DEVICE_USER_NOT_FOUND,
                        "employee " + employeeId + " is not enrolled on device " + deviceId
                                + " — enroll on the device before pushing templates"));
        String employeeCode = requireEmployeeCode(employeeId);
        List<AttendanceEmployeeTemplate> stored = templates.findByEmployeeId(employeeId).stream()
                .filter(t -> t.getKind() == TemplateKind.FINGERPRINT)
                .toList();
        if (stored.isEmpty()) {
            throw new UnprocessableError(AttendanceErrorCode.TEMPLATES_NOT_FOUND,
                    "no captured fingerprint templates for employee " + employeeId
                            + " — capture from a device first");
        }
        List<DeviceSyncTaskDto> queued = new java.util.ArrayList<>(stored.size());
        for (AttendanceEmployeeTemplate t : stored) {
            DeviceSyncTask task = new DeviceSyncTask();
            task.setDeviceId(deviceId);
            task.setEmployeeId(employeeId);
            task.setTaskType(DeviceSyncTaskType.PUSH_TEMPLATE);
            task.setPayload(pushTemplatePayload(employeeCode, t));
            task.setStatus(DeviceSyncTaskStatus.PENDING);
            task.setScheduledFor(LocalDateTime.now());
            queued.add(DeviceSyncTaskDto.of(tasks.save(task), json));
        }
        audit.emit("attendance_device_user", deviceId + ":" + employeeId, "push_templates.queued",
                null, Map.of("employeeCode", employeeCode, "templateCount", queued.size()), false);
        return queued;
    }

    // -------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------

    private void applySideEffectsOnComplete(DeviceSyncTask task) {
        switch (task.getTaskType()) {
            case UPSERT_USER -> deviceUsers
                    .findByDeviceIdAndEmployeeId(task.getDeviceId(), task.getEmployeeId())
                    .ifPresent(du -> du.setSyncedAt(LocalDateTime.now()));
            case PUSH_TEMPLATE -> deviceUsers
                    .findByDeviceIdAndEmployeeId(task.getDeviceId(), task.getEmployeeId())
                    .ifPresent(du -> {
                        TemplateKind kind = readPayloadKind(task.getPayload());
                        if (kind == TemplateKind.FINGERPRINT) du.setHasFingerprint(true);
                        else if (kind == TemplateKind.FACE) du.setHasFace(true);
                    });
            case CAPTURE_TEMPLATE -> deviceUsers
                    // A completed capture means the poller read ≥1 fingerprint
                    // off this device and uploaded it (it fails the task if it
                    // finds none), so the capturing device holds the print —
                    // flip its bridge flag. FINGERPRINT-only, per the task type.
                    .findByDeviceIdAndEmployeeId(task.getDeviceId(), task.getEmployeeId())
                    .ifPresent(du -> du.setHasFingerprint(true));
            case DELETE_USER -> {
                // The bridge row is removed by the consumer that queued the
                // delete (employee.terminated handler / HR endpoint), once
                // every device has acknowledged. v1 leaves the bridge in
                // place after a single device's DELETE_USER completes —
                // multi-device removals are handled outside this flow.
            }
            case SNAPSHOT_DEVICE -> {
                // Snapshot lifecycle is owned by DeviceSnapshotService —
                // finalizeSnapshot / failSnapshot flip the snapshot row
                // AND the task in one go. The poller calls those instead
                // of the generic complete/fail, so this case shouldn't
                // fire in practice. Left as a no-op so the switch stays
                // exhaustive over DeviceSyncTaskType.
            }
        }
    }

    private String pushTemplatePayload(String employeeCode, AttendanceEmployeeTemplate t) {
        ObjectNode node = json.createObjectNode();
        node.put("employeeCode", employeeCode);
        node.put("templateId", t.getId());
        node.put("kind", t.getKind().name());
        if (t.getFingerId() != null) node.put("fingerId", t.getFingerId());
        else node.putNull("fingerId");
        try {
            return json.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise PUSH_TEMPLATE payload", e);
        }
    }

    private TemplateKind readPayloadKind(String payload) {
        try {
            JsonNode tree = json.readTree(payload);
            return TemplateKind.valueOf(tree.path("kind").asText());
        } catch (Exception e) {
            log.warn("unable to read kind from PUSH_TEMPLATE payload: {}", payload, e);
            return null;
        }
    }

    private static void validateTemplateShape(UploadTemplateRequest req) {
        if (req.kind() == TemplateKind.FINGERPRINT) {
            if (req.fingerId() == null || req.fingerId() < 0 || req.fingerId() > 9) {
                throw new UnprocessableError(AttendanceErrorCode.TEMPLATE_KIND_INVALID,
                        "FINGERPRINT requires fingerId in [0..9]");
            }
        } else {
            if (req.fingerId() != null) {
                throw new UnprocessableError(AttendanceErrorCode.TEMPLATE_KIND_INVALID,
                        "FACE templates must have null fingerId");
            }
        }
    }

    private void queueDeleteUser(long deviceId, long employeeId, String employeeCode) {
        DeviceSyncTask task = new DeviceSyncTask();
        task.setDeviceId(deviceId);
        task.setEmployeeId(employeeId);
        task.setTaskType(DeviceSyncTaskType.DELETE_USER);
        task.setPayload(deleteUserPayload(employeeCode));
        task.setStatus(DeviceSyncTaskStatus.PENDING);
        task.setScheduledFor(LocalDateTime.now());
        tasks.save(task);
    }

    private String deleteUserPayload(String employeeCode) {
        ObjectNode node = json.createObjectNode();
        node.put("employeeCode", employeeCode);
        try {
            return json.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise DELETE_USER payload", e);
        }
    }

    private String captureTemplatePayload(String employeeCode) {
        ObjectNode node = json.createObjectNode();
        node.put("employeeCode", employeeCode);
        try {
            return json.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise CAPTURE_TEMPLATE payload", e);
        }
    }

    private String requireEmployeeCode(long employeeId) {
        return attendanceEmployees.findByEmployeeId(employeeId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.EMPLOYEE_NOT_FOUND,
                        "no attendance_employees snapshot for employee " + employeeId
                                + " — wait for employee.created to land or backfill the row"))
                .getEmployeeCode();
    }

    private DeviceSyncTask loadTaskForDevice(long deviceId, long taskId) {
        Optional<DeviceSyncTask> opt = tasks.findById(taskId);
        if (opt.isEmpty() || !opt.get().getDeviceId().equals(deviceId)) {
            throw new NotFoundError(AttendanceErrorCode.SYNC_TASK_NOT_FOUND,
                    "sync task not found for device " + deviceId + ": " + taskId);
        }
        return opt.get();
    }

    private AttendanceDevice requireDevice(long deviceId) {
        return devices.findById(deviceId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.DEVICE_NOT_FOUND,
                        "attendance device not found: " + deviceId));
    }

    private static Duration backoffFor(int attempts) {
        long seconds = BACKOFF_BASE.toSeconds() * (1L << Math.min(attempts, 12));
        return Duration.ofSeconds(Math.min(seconds, BACKOFF_CAP.toSeconds()));
    }
}

package com.forward.hrms.attendance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.attendance.dto.request.FinalizeDeviceSnapshotRequest;
import com.forward.hrms.attendance.dto.response.DeviceSnapshotDto;
import com.forward.hrms.attendance.dto.response.DeviceSnapshotSummaryDto;
import com.forward.hrms.attendance.dto.response.DeviceReconciliationSummaryDto;
import com.forward.hrms.attendance.dto.response.DeviceUserAdoptionDto;
import com.forward.hrms.attendance.dto.response.DeviceUserReconciliationDto;
import com.forward.hrms.attendance.dto.response.ReconciliationExportDto;
import com.forward.hrms.attendance.entity.AttendanceDevice;
import com.forward.hrms.attendance.entity.AttendanceDeviceUser;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.DeviceSnapshot;
import com.forward.hrms.attendance.entity.DeviceSyncTask;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.DeviceSnapshotStatus;
import com.forward.hrms.attendance.enums.DeviceSyncTaskStatus;
import com.forward.hrms.attendance.enums.DeviceSyncTaskType;
import com.forward.hrms.attendance.enums.DeviceUserMatchCategory;
import com.forward.hrms.attendance.repository.AttendanceDeviceRepository;
import com.forward.hrms.attendance.repository.AttendanceDeviceUserRepository;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.DeviceSnapshotRepository;
import com.forward.hrms.attendance.repository.DeviceSyncTaskRepository;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;

import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Device-snapshot lifecycle: HR requests a snapshot of everything a
 * device knows; the poller drains the linked SNAPSHOT_DEVICE sync task,
 * dials the device, and posts the dump back via {@link
 * #finalizeSnapshot} or {@link #failSnapshot}.
 *
 * <p>Snapshots and their linked sync task share the retry/backoff
 * policy of {@link DeviceSyncQueueService} ({@code min(60s × 2^attempts,
 * 1h)}, capped at {@code hrms.attendance.device-sync.max-attempts}). The
 * snapshot row stays PENDING across retries and only flips to FAILED
 * once the linked task is parked terminally.
 */
@Slf4j
@Service
public class DeviceSnapshotService {

    private static final Duration BACKOFF_BASE = Duration.ofSeconds(60);
    private static final Duration BACKOFF_CAP = Duration.ofHours(1);

    /** Reconciliation exports land in the shared reports bucket under an attendance prefix. */
    private static final String REPORTS_BUCKET = "reports";
    private static final Duration EXPORT_URL_TTL = Duration.ofMinutes(10);

    private final AttendanceDeviceRepository devices;
    private final DeviceSnapshotRepository snapshots;
    private final DeviceSyncTaskRepository tasks;
    private final AttendanceDeviceUserRepository deviceUsers;
    private final AttendanceEmployeeRepository attendanceEmployees;
    private final ObjectStorage storage;
    private final ObjectMapper json;
    private final AuditEmitter audit;
    private final int maxAttempts;

    public DeviceSnapshotService(AttendanceDeviceRepository devices,
                                 DeviceSnapshotRepository snapshots,
                                 DeviceSyncTaskRepository tasks,
                                 AttendanceDeviceUserRepository deviceUsers,
                                 AttendanceEmployeeRepository attendanceEmployees,
                                 ObjectStorage storage,
                                 ObjectMapper json,
                                 AuditEmitter audit,
                                 @Value("${hrms.attendance.device-sync.max-attempts:5}") int maxAttempts) {
        this.devices = devices;
        this.snapshots = snapshots;
        this.tasks = tasks;
        this.deviceUsers = deviceUsers;
        this.attendanceEmployees = attendanceEmployees;
        this.storage = storage;
        this.json = json;
        this.audit = audit;
        this.maxAttempts = maxAttempts;
    }

    /**
     * HR asks for a snapshot of everything the device knows. Persists a
     * PENDING snapshot row, queues a SNAPSHOT_DEVICE task that links to
     * it, and returns the row. The poller drains the task, dials the
     * device, and posts the dump back via {@link #finalizeSnapshot}.
     */
    @Transactional
    public DeviceSnapshotSummaryDto requestSnapshot(long deviceId) {
        requireDevice(deviceId);
        DeviceSnapshot snap = new DeviceSnapshot();
        snap.setDeviceId(deviceId);
        snap.setStatus(DeviceSnapshotStatus.PENDING);
        snap.setRequestedBy(CurrentUserHolder.usernameOrSystem());
        DeviceSnapshot saved = snapshots.save(snap);

        DeviceSyncTask task = new DeviceSyncTask();
        task.setDeviceId(deviceId);
        // §7.attendance — SNAPSHOT_DEVICE is a device-level op, not
        // employee-scoped. employee_id is non-null on the column so we
        // park 0; the poller doesn't read it.
        task.setEmployeeId(0L);
        task.setTaskType(DeviceSyncTaskType.SNAPSHOT_DEVICE);
        task.setPayload(snapshotPayload(saved.getId()));
        task.setStatus(DeviceSyncTaskStatus.PENDING);
        task.setScheduledFor(LocalDateTime.now());
        DeviceSyncTask savedTask = tasks.save(task);

        saved.setSyncTaskId(savedTask.getId());
        audit.emit("attendance_device_snapshot", String.valueOf(saved.getId()), "requested",
                null, Map.of("deviceId", deviceId, "syncTaskId", savedTask.getId()), false);
        return DeviceSnapshotSummaryDto.of(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceSnapshotSummaryDto> listSnapshots(long deviceId,
                                                                Collection<DeviceSnapshotStatus> statuses,
                                                                Pageable pageable) {
        requireDevice(deviceId);
        Pageable resolved = PageableDefaults.apply(pageable, 20, 100,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DeviceSnapshot> page = (statuses == null || statuses.isEmpty())
                ? snapshots.findByDeviceId(deviceId, resolved)
                : snapshots.findByDeviceIdAndStatusIn(deviceId, statuses, resolved);
        return PageResponse.from(page,
                page.getContent().stream().map(DeviceSnapshotSummaryDto::of).toList());
    }

    @Transactional(readOnly = true)
    public DeviceSnapshotDto getSnapshot(long deviceId, long snapshotId) {
        DeviceSnapshot s = loadSnapshotForDevice(deviceId, snapshotId);
        return DeviceSnapshotDto.of(s, json);
    }

    /**
     * One-time / re-runnable adopt: reconcile a COMPLETED snapshot's user
     * dump into the {@code attendance_device_users} bridge. For each device
     * user the device-side {@code user_id} (leading zeros stripped — devices
     * report unpadded numerics, lookup is by string equality) is matched to
     * {@link com.forward.hrms.attendance.entity.AttendanceEmployee#getEmployeeCode()};
     * the (device, employee) bridge row is created or its privilege / syncedAt
     * refreshed. Device users with no matching employee are skipped and
     * returned in {@code unmatchedUserIds}.
     *
     * <p>Idempotent — safe to re-run (after adding a device, or to pick up
     * keypad-side enrollments). {@code hasFingerprint} / {@code hasFace} are
     * deliberately left untouched: {@code get_users()} can't report template
     * presence, so those flags continue to flip only on PUSH_TEMPLATE
     * completion. {@code syncedAt} is stamped because the user is observed
     * present on the device — that's the definition of "in sync".
     */
    @Transactional
    public DeviceUserAdoptionDto adoptUsers(long deviceId, long snapshotId) {
        DeviceSnapshot s = requireCompletedSnapshot(deviceId, snapshotId);
        JsonNode users = parseUsers(s.getUsers());

        LocalDateTime now = LocalDateTime.now();
        int total = 0, created = 0, updated = 0;
        List<String> unmatched = new ArrayList<>();
        for (JsonNode u : users) {
            total++;
            String rawUserId = u.path("user_id").asText("");
            short privilege = (short) u.path("privilege").asInt(0);
            String code = stripLeadingZeros(rawUserId);
            if (code.isEmpty()) {
                unmatched.add(rawUserId);
                continue;
            }
            AttendanceEmployee employee = attendanceEmployees.findByEmployeeCode(code).orElse(null);
            if (employee == null) {
                log.info("adopt snapshot {} device {} — no employee for device user_id '{}' (code '{}'), skipping",
                        snapshotId, deviceId, rawUserId, code);
                unmatched.add(rawUserId);
                continue;
            }
            long employeeId = employee.getEmployeeId();
            AttendanceDeviceUser bridge = deviceUsers
                    .findByDeviceIdAndEmployeeId(deviceId, employeeId).orElse(null);
            if (bridge == null) {
                bridge = new AttendanceDeviceUser();
                bridge.setDeviceId(deviceId);
                bridge.setEmployeeId(employeeId);
                bridge.setPrivilege(privilege);
                bridge.setSyncedAt(now);
                deviceUsers.save(bridge);
                created++;
            } else {
                bridge.setPrivilege(privilege);
                bridge.setSyncedAt(now);
                updated++;
            }
        }

        audit.emit("attendance_device_snapshot", String.valueOf(snapshotId), "users_adopted",
                null,
                Map.of("deviceId", deviceId, "totalUsers", total,
                        "created", created, "updated", updated, "skipped", unmatched.size()),
                false);
        log.info("adopt snapshot {} device {} — total={} created={} updated={} skipped={}",
                snapshotId, deviceId, total, created, updated, unmatched.size());
        return new DeviceUserAdoptionDto(deviceId, snapshotId, total, created, updated,
                unmatched.size(), unmatched);
    }

    /**
     * Category counts for a COMPLETED snapshot's device users — the FE's
     * at-a-glance breakdown before fetching the per-user page.
     */
    @Transactional(readOnly = true)
    public DeviceReconciliationSummaryDto reconciliationSummary(long deviceId, long snapshotId) {
        DeviceSnapshot s = requireCompletedSnapshot(deviceId, snapshotId);
        List<DeviceUserReconciliationDto> rows = reconcile(s);
        int active = 0, inactive = 0, unmatched = 0;
        for (DeviceUserReconciliationDto r : rows) {
            switch (r.category()) {
                case MATCHED_ACTIVE -> active++;
                case MATCHED_INACTIVE -> inactive++;
                case UNMATCHED -> unmatched++;
            }
        }
        return new DeviceReconciliationSummaryDto(deviceId, snapshotId, rows.size(),
                active, inactive, unmatched);
    }

    /**
     * Paginated reconciliation rows, optionally filtered to one category.
     * The device user list lives in the snapshot's jsonb, so this paginates
     * an in-memory list (device order; caller sort is ignored) — fine for a
     * few thousand rows on an occasional HR action.
     */
    @Transactional(readOnly = true)
    public PageResponse<DeviceUserReconciliationDto> reconciliationPage(long deviceId, long snapshotId,
                                                                        DeviceUserMatchCategory category,
                                                                        Pageable pageable) {
        DeviceSnapshot s = requireCompletedSnapshot(deviceId, snapshotId);
        List<DeviceUserReconciliationDto> rows = reconcile(s);
        if (category != null) {
            rows = rows.stream().filter(r -> r.category() == category).toList();
        }
        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), rows.size());
        int to = (int) Math.min((long) from + pageable.getPageSize(), rows.size());
        Page<DeviceUserReconciliationDto> page =
                new PageImpl<>(rows.subList(from, to), pageable, rows.size());
        return PageResponse.from(page, page.getContent());
    }

    /**
     * Render the full reconciliation as an .xlsx, upload it to object
     * storage, and return a short-lived signed download URL. The export is
     * what HR opens — the raw JSON snapshot is for engineers.
     */
    @Transactional
    public ReconciliationExportDto exportReconciliation(long deviceId, long snapshotId) {
        DeviceSnapshot s = requireCompletedSnapshot(deviceId, snapshotId);
        List<DeviceUserReconciliationDto> rows = reconcile(s);
        byte[] xlsx = buildWorkbook(rows);
        String key = "attendance/device-users/%d/snapshot-%d.xlsx".formatted(deviceId, snapshotId);
        storage.upload(REPORTS_BUCKET, key, new ByteArrayInputStream(xlsx), xlsx.length,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null);
        audit.emit("attendance_device_snapshot", String.valueOf(snapshotId), "reconciliation_exported",
                null, Map.of("deviceId", deviceId, "rows", rows.size(), "objectKey", key), false);
        String url = storage.signedGetUrl(REPORTS_BUCKET, key, EXPORT_URL_TTL);
        return new ReconciliationExportDto(url, key);
    }

    /**
     * Poller side — finalize an in-flight snapshot with the captured
     * payloads. Validates the JSON and writes it onto the row, flips
     * the status to COMPLETED, and marks the linked SNAPSHOT_DEVICE
     * sync task DONE in the same transaction.
     */
    @Transactional
    public DeviceSnapshotSummaryDto finalizeSnapshot(long deviceId, long snapshotId,
                                                     FinalizeDeviceSnapshotRequest req) {
        DeviceSnapshot s = loadSnapshotForDevice(deviceId, snapshotId);
        if (s.getStatus() != DeviceSnapshotStatus.PENDING) {
            throw new UnprocessableError(AttendanceErrorCode.DEVICE_SNAPSHOT_NOT_PENDING,
                    "snapshot is not PENDING, current status: " + s.getStatus());
        }
        s.setDeviceInfo(serialize(req.deviceInfo()));
        s.setUsers(serialize(req.users()));
        s.setAttendance(serialize(req.attendance()));
        s.setUserCount(req.userCount());
        s.setRecordCount(req.recordCount());
        s.setStatus(DeviceSnapshotStatus.COMPLETED);
        s.setCompletedAt(LocalDateTime.now());
        s.setError(null);

        if (s.getSyncTaskId() != null) {
            tasks.findById(s.getSyncTaskId()).ifPresent(task -> {
                if (task.getStatus() == DeviceSyncTaskStatus.IN_PROGRESS
                        || task.getStatus() == DeviceSyncTaskStatus.PENDING) {
                    task.setStatus(DeviceSyncTaskStatus.DONE);
                    task.setCompletedAt(LocalDateTime.now());
                    task.setLastError(null);
                }
            });
        }
        audit.emit("attendance_device_snapshot", String.valueOf(s.getId()), "completed",
                null,
                Map.of("userCount", s.getUserCount() == null ? 0 : s.getUserCount(),
                       "recordCount", s.getRecordCount() == null ? 0 : s.getRecordCount()),
                false);
        return DeviceSnapshotSummaryDto.of(s);
    }

    /**
     * Poller side — record a snapshot failure. Marks the snapshot row
     * FAILED with the error message and applies the same retry/backoff
     * policy to the linked sync task as
     * {@link DeviceSyncQueueService#fail}.
     */
    @Transactional
    public DeviceSnapshotSummaryDto failSnapshot(long deviceId, long snapshotId,
                                                 String error, Boolean retryable) {
        DeviceSnapshot s = loadSnapshotForDevice(deviceId, snapshotId);
        if (s.getStatus() != DeviceSnapshotStatus.PENDING) {
            throw new UnprocessableError(AttendanceErrorCode.DEVICE_SNAPSHOT_NOT_PENDING,
                    "snapshot is not PENDING, current status: " + s.getStatus());
        }
        boolean canRetry = !Boolean.FALSE.equals(retryable);
        DeviceSyncTask task = s.getSyncTaskId() == null ? null
                : tasks.findById(s.getSyncTaskId()).orElse(null);
        boolean willRetry = false;
        if (task != null && task.getStatus() == DeviceSyncTaskStatus.IN_PROGRESS) {
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
                willRetry = true;
            }
        }
        if (willRetry) {
            // Snapshot row stays PENDING — the next poller cycle will
            // retry against the same row.
            s.setError(error);
            audit.emit("attendance_device_snapshot", String.valueOf(s.getId()), "retry_scheduled",
                    null, Map.of("attempt", task.getAttempts(), "error", error), false);
        } else {
            s.setStatus(DeviceSnapshotStatus.FAILED);
            s.setCompletedAt(LocalDateTime.now());
            s.setError(error);
            audit.emit("attendance_device_snapshot", String.valueOf(s.getId()), "failed",
                    null, Map.of("error", error), false);
        }
        return DeviceSnapshotSummaryDto.of(s);
    }

    private DeviceSnapshot loadSnapshotForDevice(long deviceId, long snapshotId) {
        DeviceSnapshot s = snapshots.findById(snapshotId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.DEVICE_SNAPSHOT_NOT_FOUND,
                        "device snapshot not found: " + snapshotId));
        if (!s.getDeviceId().equals(deviceId)) {
            throw new NotFoundError(AttendanceErrorCode.DEVICE_SNAPSHOT_NOT_FOUND,
                    "snapshot does not belong to device " + deviceId);
        }
        return s;
    }

    /**
     * Pulls the {@code users} array out of the snapshot's
     * {@code {count, users:[...]}} dump. Lenient — a COMPLETED snapshot
     * always carries valid JSON (finalize serialized it from a JsonNode),
     * so a missing/odd payload just yields an empty list (zero adoptions)
     * rather than failing the call.
     */
    private JsonNode parseUsers(String usersJson) {
        if (usersJson == null || usersJson.isBlank()) {
            return json.createArrayNode();
        }
        try {
            JsonNode arr = json.readTree(usersJson).path("users");
            return arr.isArray() ? arr : json.createArrayNode();
        } catch (Exception e) {
            log.warn("snapshot users payload not parseable, treating as empty: {}", e.getMessage());
            return json.createArrayNode();
        }
    }

    /**
     * Device {@code user_id} ↔ {@code employee_code} are matched by string
     * equality on the unpadded numeric — the bridge stores codes exactly as
     * the ZK protocol returns them, so {@code 013411} from a hand-keyed
     * device must collapse to {@code 13411} before lookup. Keeps the last
     * digit even for an all-zero id.
     */
    private static String stripLeadingZeros(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        int i = 0;
        while (i < trimmed.length() - 1 && trimmed.charAt(i) == '0') i++;
        return trimmed.substring(i);
    }

    /**
     * Match every device user against the employee mirror in one bulk query.
     * Shared by the summary, the paginated view, and the Excel export.
     */
    private List<DeviceUserReconciliationDto> reconcile(DeviceSnapshot s) {
        record Raw(String raw, String code, short privilege) {}
        JsonNode users = parseUsers(s.getUsers());
        List<Raw> raws = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode u : users) {
            String raw = u.path("user_id").asText("");
            short priv = (short) u.path("privilege").asInt(0);
            String code = stripLeadingZeros(raw);
            raws.add(new Raw(raw, code, priv));
            if (!code.isEmpty()) codes.add(code);
        }
        Map<String, AttendanceEmployee> byCode = codes.isEmpty() ? Map.of()
                : attendanceEmployees.findAllByEmployeeCodeIn(codes).stream()
                    .collect(Collectors.toMap(AttendanceEmployee::getEmployeeCode, e -> e, (a, b) -> a));
        List<DeviceUserReconciliationDto> out = new ArrayList<>(raws.size());
        for (Raw r : raws) {
            AttendanceEmployee e = r.code().isEmpty() ? null : byCode.get(r.code());
            if (e == null) {
                out.add(new DeviceUserReconciliationDto(r.raw(), r.code().isEmpty() ? null : r.code(),
                        null, null, null, DeviceUserMatchCategory.UNMATCHED, r.privilege()));
            } else {
                boolean active = "ACTIVE".equalsIgnoreCase(e.getEmploymentStatus());
                out.add(new DeviceUserReconciliationDto(r.raw(), e.getEmployeeCode(), e.getEmployeeId(),
                        e.getFullName(), e.getEmploymentStatus(),
                        active ? DeviceUserMatchCategory.MATCHED_ACTIVE
                               : DeviceUserMatchCategory.MATCHED_INACTIVE,
                        r.privilege()));
            }
        }
        return out;
    }

    private byte[] buildWorkbook(List<DeviceUserReconciliationDto> rows) {
        // SXSSF streams to disk-backed windows — device user counts run a few
        // thousand, comfortably past the 2k threshold the reports doctrine sets.
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Device users");
            String[] headers = {"Device User ID", "Employee Code", "Employee ID", "Name",
                    "Employment Status", "Category", "Privilege"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) head.createCell(i).setCellValue(headers[i]);
            int rn = 1;
            for (DeviceUserReconciliationDto r : rows) {
                Row row = sheet.createRow(rn++);
                row.createCell(0).setCellValue(r.deviceUserId());
                row.createCell(1).setCellValue(r.employeeCode() == null ? "" : r.employeeCode());
                if (r.employeeId() != null) row.createCell(2).setCellValue(r.employeeId());
                row.createCell(3).setCellValue(r.name() == null ? "" : r.name());
                row.createCell(4).setCellValue(r.employmentStatus() == null ? "" : r.employmentStatus());
                row.createCell(5).setCellValue(r.category().name());
                row.createCell(6).setCellValue(r.privilege());
            }
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to build reconciliation workbook", e);
        }
    }

    private DeviceSnapshot requireCompletedSnapshot(long deviceId, long snapshotId) {
        DeviceSnapshot s = loadSnapshotForDevice(deviceId, snapshotId);
        if (s.getStatus() != DeviceSnapshotStatus.COMPLETED) {
            throw new UnprocessableError(AttendanceErrorCode.DEVICE_SNAPSHOT_NOT_COMPLETED,
                    "snapshot is not COMPLETED, current status: " + s.getStatus());
        }
        return s;
    }

    private String snapshotPayload(long snapshotId) {
        ObjectNode node = json.createObjectNode();
        node.put("snapshotId", snapshotId);
        try {
            return json.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise SNAPSHOT_DEVICE payload", e);
        }
    }

    private String serialize(Object node) {
        if (node == null) return null;
        try {
            return json.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise snapshot payload", e);
        }
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

package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.request.CreateAttendanceDeviceRequest;
import com.forward.hrms.attendance.dto.request.SetDeviceCommPasswordRequest;
import com.forward.hrms.attendance.dto.request.UnenrollDeviceUsersRequest;
import com.forward.hrms.attendance.dto.request.UpdateAttendanceDeviceRequest;
import com.forward.hrms.attendance.dto.response.AttendanceDeviceDto;
import com.forward.hrms.attendance.dto.response.DeviceReconciliationSummaryDto;
import com.forward.hrms.attendance.dto.response.DeviceSnapshotDto;
import com.forward.hrms.attendance.dto.response.DeviceSnapshotSummaryDto;
import com.forward.hrms.attendance.dto.response.DeviceSyncTaskDto;
import com.forward.hrms.attendance.dto.response.DeviceUserAdoptionDto;
import com.forward.hrms.attendance.dto.response.DeviceUserDto;
import com.forward.hrms.attendance.dto.response.DeviceUserReconciliationDto;
import com.forward.hrms.attendance.dto.response.PollerTargetDto;
import com.forward.hrms.attendance.dto.response.ReconciliationExportDto;
import com.forward.hrms.attendance.dto.response.UnenrollResultDto;
import com.forward.hrms.attendance.enums.DeviceSnapshotStatus;
import com.forward.hrms.attendance.enums.DeviceSyncTaskStatus;
import com.forward.hrms.attendance.enums.DeviceUserMatchCategory;
import com.forward.hrms.attendance.service.AttendanceDeviceService;
import com.forward.hrms.attendance.service.DeviceSnapshotService;
import com.forward.hrms.attendance.service.DeviceSyncQueueService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@Tag(name = "Attendance devices",
        description = "Catalog of biometric / kiosk devices that emit punches. Owned by IT_OPS / ADMIN. " +
                "Every management endpoint requires `attendance.devices.admin`; the poller-targets " +
                "feed stays on `attendance.poller`.")
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class AttendanceDeviceController {

    private final AttendanceDeviceService devices;
    private final DeviceSyncQueueService syncQueue;
    private final DeviceSnapshotService deviceSnapshots;

    @Operation(summary = "List devices",
            description = "Active only by default. Pass `includeInactive=true` for the HR-admin catalog UI. " +
                    "Requires `attendance.devices.admin`.")
    @GetMapping
    @RequirePermission("attendance.devices.admin")
    public List<AttendanceDeviceDto> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return devices.list(includeInactive);
    }

    @Operation(summary = "Get a device",
            description = "Requires `attendance.devices.admin`.")
    @GetMapping("/{id}")
    @RequirePermission("attendance.devices.admin")
    public AttendanceDeviceDto get(@PathVariable long id) {
        return devices.get(id);
    }

    @Operation(summary = "Create a device (HR)",
            description = "Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "409", description = "DEVICE_CODE_TAKEN — a device with this code already exists.")
    @PostMapping
    @RequirePermission("attendance.devices.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceDeviceDto create(@Valid @RequestBody CreateAttendanceDeviceRequest req) {
        return devices.create(req);
    }

    @Operation(summary = "Update a device (HR)",
            description = "Patch — null fields stay unchanged. `code` is immutable; deactivate and " +
                    "create a new row if the code is wrong. Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @PatchMapping("/{id}")
    @RequirePermission("attendance.devices.admin")
    public AttendanceDeviceDto update(@PathVariable long id,
                                      @Valid @RequestBody UpdateAttendanceDeviceRequest req) {
        return devices.update(id, req);
    }

    @Operation(summary = "Deactivate a device (HR)",
            description = "Soft delete — flips `active=false`. Existing punches keep their device_id " +
                    "FK; the row stays available for historical reads. Re-enable by PATCHing " +
                    "`active=true`. Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @DeleteMapping("/{id}")
    @RequirePermission("attendance.devices.admin")
    public AttendanceDeviceDto deactivate(@PathVariable long id) {
        return devices.deactivate(id);
    }

    @Operation(summary = "Set the device's ZK comm password (HR)",
            description = "Write-only. The current value is never returned in any response — " +
                    "the GET endpoints expose `commPasswordSet: bool` instead. Send 0 to clear. " +
                    "Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @PostMapping("/{id}/comm-password")
    @RequirePermission("attendance.devices.admin")
    public AttendanceDeviceDto setCommPassword(@PathVariable long id,
                                               @Valid @RequestBody SetDeviceCommPasswordRequest req) {
        return devices.setCommPassword(id, req);
    }

    @Operation(summary = "Reset the device's high-water-mark (HR)",
            description = "Nulls `last_punched_at` so the next poller cycle pulls every record " +
                    "currently on the device. Use after a device clock reset or to re-ingest a " +
                    "swathe of records whose bridge rows landed late. Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @PostMapping("/{id}/reset-hwm")
    @RequirePermission("attendance.devices.admin")
    public AttendanceDeviceDto resetHwm(@PathVariable long id) {
        return devices.resetHwm(id);
    }

    @Operation(summary = "List sync tasks for a device (HR)",
            description = "All sync tasks for a device, newest scheduled_for first. Filter by " +
                    "`status` (repeat for multi-select: PENDING / IN_PROGRESS / DONE / FAILED). " +
                    "Default sort `scheduledFor,desc`. Default page size 50 (max 200). " +
                    "Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @GetMapping("/{id}/sync-tasks")
    @RequirePermission("attendance.devices.admin")
    public PageResponse<DeviceSyncTaskDto> listSyncTasks(
            @PathVariable("id") long deviceId,
            @Parameter(description = "Filter tasks by status. Omit for all.")
            @RequestParam(required = false) Collection<DeviceSyncTaskStatus> status,
            @Parameter(hidden = true) Pageable pageable) {
        return syncQueue.listTasks(deviceId, status, PageableDefaults.apply(pageable, "scheduledFor"));
    }

    @Operation(summary = "Retry a FAILED sync task (HR)",
            description = "Re-arms a task that has parked in FAILED — flips back to PENDING with " +
                    "attempts=0 and scheduled_for=now. The next poller cycle picks it up. " +
                    "Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "SYNC_TASK_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "SYNC_TASK_NOT_FAILED — task is not in FAILED status.")
    @PostMapping("/{id}/sync-tasks/{taskId}/retry")
    @RequirePermission("attendance.devices.admin")
    public DeviceSyncTaskDto retrySyncTask(@PathVariable("id") long deviceId,
                                           @PathVariable long taskId) {
        return syncQueue.retryTask(deviceId, taskId);
    }

    @Operation(summary = "List employees enrolled on a device (HR)",
            description = "Bridge rows for the device — employeeId, privilege, hasFingerprint / " +
                    "hasFace flags, last syncedAt. The employee's device-side identifier is the " +
                    "employee_code (resolved through attendance_employees). Default sort " +
                    "`employeeId,asc`. Default page size 100 (max 500). Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @GetMapping("/{id}/users")
    @RequirePermission("attendance.devices.admin")
    public PageResponse<DeviceUserDto> listDeviceUsers(@PathVariable("id") long deviceId,
                                                       @Parameter(hidden = true) Pageable pageable) {
        return syncQueue.listDeviceUsers(deviceId, PageableDefaults.apply(pageable, "employeeId"));
    }

    @Operation(summary = "Remove an employee from a device (HR)",
            description = "Drops the bridge row immediately and queues a DELETE_USER for the poller. " +
                    "If the device is offline, the queued task drains when it returns. " +
                    "Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "204", description = "Bridge row removed; DELETE_USER queued.")
    @ApiResponse(responseCode = "404",
            description = "DEVICE_NOT_FOUND or DEVICE_USER_NOT_FOUND — employee is not on this device.")
    @DeleteMapping("/{id}/users/{employeeId}")
    @RequirePermission("attendance.devices.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUserFromDevice(@PathVariable("id") long deviceId,
                                     @PathVariable long employeeId) {
        syncQueue.removeUserFromDevice(deviceId, employeeId);
    }

    @Operation(summary = "Capture an employee's fingerprints off a device (HR)",
            description = "Queues a CAPTURE_TEMPLATE task for the poller to read the employee's " +
                    "enrolled fingerprint templates off this device — typically the enrollment " +
                    "device, right after the employee placed a finger on the keypad — and store " +
                    "them centrally. Store-only: capture does NOT push to any other device. To " +
                    "provision the print onto the device(s) you choose, call POST " +
                    "/devices/{id}/users/{employeeId}/push-templates on each target afterwards. " +
                    "The employee must already be on this device. Returns the queued task so the " +
                    "FE can track it via GET /devices/{id}/sync-tasks. Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404",
            description = "DEVICE_NOT_FOUND, DEVICE_USER_NOT_FOUND (employee not on this device), " +
                    "or EMPLOYEE_NOT_FOUND (no attendance snapshot for the employee yet).")
    @PostMapping("/{id}/users/{employeeId}/capture-template")
    @RequirePermission("attendance.devices.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceSyncTaskDto captureTemplate(@PathVariable("id") long deviceId,
                                             @PathVariable long employeeId) {
        return syncQueue.queueCaptureTemplate(deviceId, employeeId);
    }

    @Operation(summary = "Push an employee's captured fingerprints onto a device (HR)",
            description = "Queues a PUSH_TEMPLATE task per stored fingerprint template so the " +
                    "poller provisions the employee's prints onto THIS device — the deliberate, " +
                    "operator-chosen counterpart to capture (which is store-only). Call it on each " +
                    "device you want the employee's prints on; reusable any time (e.g. when a new " +
                    "gate is added) with no re-scan, since the print is already in the central " +
                    "store. The employee must already be enrolled on this device and the device " +
                    "must be active. FACE templates are skipped. Returns the queued task(s). " +
                    "Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404",
            description = "DEVICE_NOT_FOUND, DEVICE_USER_NOT_FOUND (employee not on this device), " +
                    "or EMPLOYEE_NOT_FOUND.")
    @ApiResponse(responseCode = "422",
            description = "DEVICE_INACTIVE (target not active) or TEMPLATES_NOT_FOUND " +
                    "(employee has no captured fingerprints yet — capture first).")
    @PostMapping("/{id}/users/{employeeId}/push-templates")
    @RequirePermission("attendance.devices.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public List<DeviceSyncTaskDto> pushTemplates(@PathVariable("id") long deviceId,
                                                 @PathVariable long employeeId) {
        return syncQueue.pushTemplatesToDevice(deviceId, employeeId);
    }

    @Operation(summary = "Bulk un-enroll users from a device (HR)",
            description = "Post-adoption cleanup. Removes the listed matched employees (by id; bridge " +
                    "row dropped) and/or stray device users (by raw deviceUserId; no bridge) from the " +
                    "device — each queues a DELETE_USER for the poller. Unknown ids are skipped, not " +
                    "failed. POST (not DELETE) because it carries a body. Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "EMPLOYEES_REQUIRED — both lists empty.")
    @PostMapping("/{id}/users/unenroll")
    @RequirePermission("attendance.devices.admin")
    public UnenrollResultDto unenroll(@PathVariable("id") long deviceId,
                                      @Valid @RequestBody UnenrollDeviceUsersRequest req) {
        return syncQueue.unenroll(deviceId, req.employeeIds(), req.deviceUserIds());
    }

    @Operation(summary = "Request a device snapshot (HR)",
            description = "Persists a PENDING snapshot row + queues a SNAPSHOT_DEVICE sync task. " +
                    "The poller drains the task, dials the device, and posts deviceInfo / users / " +
                    "attendance JSON back; the snapshot row flips to COMPLETED at that point. Use " +
                    "GET /devices/{id}/snapshots/{snapshotId} to read the dump. " +
                    "Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @PostMapping("/{id}/snapshots")
    @RequirePermission("attendance.devices.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceSnapshotSummaryDto requestSnapshot(@PathVariable("id") long deviceId) {
        return deviceSnapshots.requestSnapshot(deviceId);
    }

    @Operation(summary = "List device snapshots (HR)",
            description = "Newest first, no payloads — counts + status + error only. " +
                    "Filter by `status` (PENDING / COMPLETED / FAILED). Default page size 20 " +
                    "(max 100). Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @GetMapping("/{id}/snapshots")
    @RequirePermission("attendance.devices.admin")
    public PageResponse<DeviceSnapshotSummaryDto> listSnapshots(
            @PathVariable("id") long deviceId,
            @Parameter(description = "Filter by snapshot status. Omit for all.")
            @RequestParam(required = false) Collection<DeviceSnapshotStatus> status,
            @Parameter(hidden = true) Pageable pageable) {
        return deviceSnapshots.listSnapshots(deviceId, status, PageableDefaults.apply(pageable, "createdAt"));
    }

    @Operation(summary = "Get a device snapshot with payloads (HR)",
            description = "Full snapshot: deviceInfo / users / attendance JSON populated when " +
                    "status is COMPLETED. The FE can render these in a viewer or download them as " +
                    "JSON files. Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND.")
    @GetMapping("/{id}/snapshots/{snapshotId}")
    @RequirePermission("attendance.devices.admin")
    public DeviceSnapshotDto getSnapshot(@PathVariable("id") long deviceId,
                                         @PathVariable long snapshotId) {
        return deviceSnapshots.getSnapshot(deviceId, snapshotId);
    }

    @Operation(summary = "Adopt a snapshot's users into the bridge (HR)",
            description = "Reconciles a COMPLETED snapshot's user dump into the device's " +
                    "attendance_device_users bridge — the cutover path for devices that already " +
                    "carry enrollments. Each device user_id (leading zeros stripped) is matched to " +
                    "an employee_code; the bridge row is created or its privilege / syncedAt " +
                    "refreshed, and users with no matching employee are skipped and returned. " +
                    "Idempotent — re-run after adding a device or to pick up keypad-side " +
                    "enrollments. Does NOT set hasFingerprint / hasFace (get_users can't report " +
                    "template presence). Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND.")
    @ApiResponse(responseCode = "422",
            description = "DEVICE_SNAPSHOT_NOT_COMPLETED — snapshot has no captured users yet.")
    @PostMapping("/{id}/snapshots/{snapshotId}/adopt-users")
    @RequirePermission("attendance.devices.admin")
    public DeviceUserAdoptionDto adoptSnapshotUsers(@PathVariable("id") long deviceId,
                                                    @PathVariable long snapshotId) {
        return deviceSnapshots.adoptUsers(deviceId, snapshotId);
    }

    @Operation(summary = "Snapshot reconciliation summary (HR)",
            description = "Counts of the snapshot's device users by category: matched-active (keep), " +
                    "matched-inactive (e.g. terminated — un-enroll candidate), and unmatched " +
                    "(stray, no employee — un-enroll candidate). Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "DEVICE_SNAPSHOT_NOT_COMPLETED.")
    @GetMapping("/{id}/snapshots/{snapshotId}/reconciliation/summary")
    @RequirePermission("attendance.devices.admin")
    public DeviceReconciliationSummaryDto reconciliationSummary(@PathVariable("id") long deviceId,
                                                                @PathVariable long snapshotId) {
        return deviceSnapshots.reconciliationSummary(deviceId, snapshotId);
    }

    @Operation(summary = "Snapshot reconciliation rows (HR)",
            description = "Paginated device users reconciled against the employee mirror. Filter by " +
                    "`category` (MATCHED_ACTIVE / MATCHED_INACTIVE / UNMATCHED). For UNMATCHED rows " +
                    "pass `deviceUserId` back to the un-enroll endpoint; for matched rows pass " +
                    "`employeeId`. Default page size 100 (max 1000). Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "DEVICE_SNAPSHOT_NOT_COMPLETED.")
    @GetMapping("/{id}/snapshots/{snapshotId}/reconciliation")
    @RequirePermission("attendance.devices.admin")
    public PageResponse<DeviceUserReconciliationDto> reconciliation(
            @PathVariable("id") long deviceId,
            @PathVariable long snapshotId,
            @Parameter(description = "Filter by reconciliation category. Omit for all.")
            @RequestParam(required = false) DeviceUserMatchCategory category,
            @Parameter(hidden = true) Pageable pageable) {
        return deviceSnapshots.reconciliationPage(deviceId, snapshotId, category,
                PageableDefaults.apply(pageable, 100, 1000, org.springframework.data.domain.Sort.unsorted()));
    }

    @Operation(summary = "Export reconciliation as Excel (HR)",
            description = "Renders the full reconciliation to an .xlsx, stores it in object storage, " +
                    "and returns a short-lived signed download URL the FE opens directly — the " +
                    "human-readable alternative to the raw JSON snapshot. Requires `attendance.devices.admin`.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "DEVICE_SNAPSHOT_NOT_COMPLETED.")
    @PostMapping("/{id}/snapshots/{snapshotId}/reconciliation/export")
    @RequirePermission("attendance.devices.admin")
    public ReconciliationExportDto exportReconciliation(@PathVariable("id") long deviceId,
                                                        @PathVariable long snapshotId) {
        return deviceSnapshots.exportReconciliation(deviceId, snapshotId);
    }

    @Operation(summary = "Poller-targets feed (poller)",
            description = "Active devices with a non-null `deviceIp`, joined to their high-water-mark. " +
                    "**Carries the ZK comm password** — gated to the poller's service account. " +
                    "Requires `attendance.poller`.")
    @GetMapping("/poller-targets")
    @RequirePermission("attendance.poller")
    public List<PollerTargetDto> pollerTargets() {
        return devices.pollerTargets();
    }
}

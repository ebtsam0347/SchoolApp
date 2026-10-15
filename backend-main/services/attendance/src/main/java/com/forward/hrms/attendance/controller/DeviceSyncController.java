package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.request.CompleteSyncTaskRequest;
import com.forward.hrms.attendance.dto.request.FailDeviceSnapshotRequest;
import com.forward.hrms.attendance.dto.request.FailSyncTaskRequest;
import com.forward.hrms.attendance.dto.request.FinalizeDeviceSnapshotRequest;
import com.forward.hrms.attendance.dto.request.HeartbeatRequest;
import com.forward.hrms.attendance.dto.response.DeviceSnapshotSummaryDto;
import com.forward.hrms.attendance.dto.response.DeviceSyncTaskDto;
import com.forward.hrms.attendance.service.DeviceSnapshotService;
import com.forward.hrms.attendance.service.DeviceSyncQueueService;
import com.forward.hrms.attendance.service.DeviceSyncService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Device sync (poller)",
        description = "Service-account surface drained by the attendance-poller sidecar (`services/" +
                "attendance-poller`). Claims sync tasks, reports outcomes, and pushes the per-cycle " +
                "device heartbeat. Requires `attendance.poller`.")
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceSyncController {

    private final DeviceSyncQueueService syncQueue;
    private final DeviceSnapshotService deviceSnapshots;
    private final DeviceSyncService sync;

    @Operation(summary = "Claim next batch of sync tasks",
            description = "Atomically claims up to `limit` PENDING tasks for the device whose " +
                    "`scheduled_for <= now` (FOR UPDATE SKIP LOCKED). Empty list when nothing is " +
                    "ready. limit is clamped to [1, 100].")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @GetMapping("/{id}/sync-tasks/next")
    @RequirePermission("attendance.poller")
    public List<DeviceSyncTaskDto> claimNext(@PathVariable("id") long deviceId,
                                             @RequestParam(defaultValue = "10") int limit) {
        return syncQueue.claimNext(deviceId, limit);
    }

    @Operation(summary = "Mark a claimed task DONE",
            description = "Flips the task to DONE and applies side effects: UPSERT_USER stamps " +
                    "`attendance_device_users.synced_at`; PUSH_TEMPLATE flips `has_fingerprint` / " +
                    "`has_face` on the bridge row.")
    @ApiResponse(responseCode = "204", description = "Marked done.")
    @ApiResponse(responseCode = "404", description = "SYNC_TASK_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "Task is not IN_PROGRESS (already completed / failed).")
    @PostMapping("/{id}/sync-tasks/{taskId}/complete")
    @RequirePermission("attendance.poller")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable("id") long deviceId,
                         @PathVariable long taskId,
                         @Valid @RequestBody(required = false) CompleteSyncTaskRequest req) {
        syncQueue.complete(deviceId, taskId, req == null ? null : req.detail());
    }

    @Operation(summary = "Mark a claimed task FAILED",
            description = "`retryable=false` parks the row in FAILED immediately. `retryable=true` " +
                    "(default) increments attempts and re-arms with exponential backoff " +
                    "(min(60s × 2^attempts, 1h)) until the configured max-attempts cap, after which " +
                    "the row stays FAILED for HR to retry.")
    @ApiResponse(responseCode = "204", description = "Failure recorded.")
    @ApiResponse(responseCode = "404", description = "SYNC_TASK_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "Task is not IN_PROGRESS.")
    @PostMapping("/{id}/sync-tasks/{taskId}/fail")
    @RequirePermission("attendance.poller")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(@PathVariable("id") long deviceId,
                     @PathVariable long taskId,
                     @Valid @RequestBody FailSyncTaskRequest req) {
        syncQueue.fail(deviceId, taskId, req.error(), req.retryable());
    }

    @Operation(summary = "Finalize a device snapshot (poller)",
            description = "Posted after a SNAPSHOT_DEVICE task completes. Body carries the " +
                    "deviceInfo / users / attendance JSON dumped from the device. The service " +
                    "stores them on the snapshot row, flips its status to COMPLETED, and marks " +
                    "the linked sync task DONE in the same transaction.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "DEVICE_SNAPSHOT_NOT_PENDING — already completed / failed.")
    @PostMapping("/{id}/snapshots/{snapshotId}/finalize")
    @RequirePermission("attendance.poller")
    public DeviceSnapshotSummaryDto finalizeSnapshot(@PathVariable("id") long deviceId,
                                                     @PathVariable long snapshotId,
                                                     @Valid @RequestBody FinalizeDeviceSnapshotRequest req) {
        return deviceSnapshots.finalizeSnapshot(deviceId, snapshotId, req);
    }

    @Operation(summary = "Mark a device snapshot FAILED (poller)",
            description = "Posted when a SNAPSHOT_DEVICE task can't be fulfilled. Same retry/" +
                    "backoff policy as a regular task fail — `retryable=true` (default) re-arms " +
                    "with exponential backoff up to the cap; `retryable=false` parks the task in " +
                    "FAILED. The snapshot row is flipped to FAILED only on the terminal failure " +
                    "(after backoff is exhausted or retryable=false).")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND.")
    @ApiResponse(responseCode = "422", description = "DEVICE_SNAPSHOT_NOT_PENDING.")
    @PostMapping("/{id}/snapshots/{snapshotId}/fail")
    @RequirePermission("attendance.poller")
    public DeviceSnapshotSummaryDto failSnapshot(@PathVariable("id") long deviceId,
                                                 @PathVariable long snapshotId,
                                                 @Valid @RequestBody FailDeviceSnapshotRequest req) {
        return deviceSnapshots.failSnapshot(deviceId, snapshotId, req.error(), req.retryable());
    }

    @Operation(summary = "Heartbeat",
            description = "Updates `attendance_devices.status` + `last_seen_at`. Called by the poller " +
                    "on every cycle (success or fail) so the dashboard freshness indicator stays current.")
    @ApiResponse(responseCode = "204", description = "Heartbeat recorded.")
    @ApiResponse(responseCode = "404", description = "DEVICE_NOT_FOUND.")
    @PostMapping("/{id}/heartbeat")
    @RequirePermission("attendance.poller")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@PathVariable("id") long deviceId,
                          @Valid @RequestBody HeartbeatRequest req) {
        sync.heartbeat(deviceId, req);
    }
}

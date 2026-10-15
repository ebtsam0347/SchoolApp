package com.forward.hrms.attendance.enums;

/**
 * Lifecycle of an HR-requested device snapshot.
 *
 * <ul>
 *   <li>{@code PENDING} — row created, SNAPSHOT_DEVICE sync task queued.
 *       Awaiting the poller.</li>
 *   <li>{@code COMPLETED} — poller finalized the snapshot with payloads
 *       (deviceInfo + users + attendance).</li>
 *   <li>{@code FAILED} — poller could not produce the snapshot (device
 *       unreachable, ZK error, etc.). Error message is on the row.</li>
 * </ul>
 */
public enum DeviceSnapshotStatus {
    PENDING,
    COMPLETED,
    FAILED
}

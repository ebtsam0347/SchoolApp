package com.forward.hrms.attendance.enums;

public enum DeviceSyncTaskType {
    UPSERT_USER,
    DELETE_USER,
    PUSH_TEMPLATE,
    /**
     * HR-triggered read of an employee's enrolled fingerprint templates
     * off one device (the enrollment device, where the employee just
     * placed a finger on the keypad). The poller reads each present
     * finger via pyzk {@code get_user_template} and POSTs it to
     * {@code POST /templates}, which stores it centrally — store-only, no
     * fan-out. Provisioning to other devices is a separate, deliberate
     * step (PUSH_TEMPLATE, queued by the push-templates endpoint for the
     * device(s) the operator picks). Payload shape:
     * {@code {employeeCode: <string>}}. FINGERPRINT only (FACE push isn't
     * implemented in pyzk 0.9).
     */
    CAPTURE_TEMPLATE,
    /**
     * HR-triggered snapshot of a device's users + attendance log + metadata.
     * The poller dumps the device, posts the JSON back to the service via
     * {@code POST /devices/{id}/snapshots/{snapshotId}/finalize}, then
     * marks the task DONE. Payload shape: {@code {snapshotId: <long>}}.
     */
    SNAPSHOT_DEVICE
}

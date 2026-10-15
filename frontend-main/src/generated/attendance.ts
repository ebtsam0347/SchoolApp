/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).
 * Regenerate: `npm run gen:api`.
 */

export interface paths {
    "/templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Upload a captured template
         * @description Stores the template (one per (employee, kind, fingerId) — re-captures replace). Store-only: does NOT queue any push. Provisioning to a device is a separate, operator-chosen step via POST /devices/{id}/users/{employeeId}/push-templates.
         */
        post: operations["upload"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-leaves": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Apply for a short leave
         * @description Requires `attendance.write`.
         */
        post: operations["apply"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-leaves/{id}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reject at the caller's current stage
         * @description Requires `attendance.write`.
         */
        post: operations["reject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-leaves/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel my short leave
         * @description Submitter-only. PENDING any time, or APPROVED before the leave date. Requires `attendance.write`.
         */
        post: operations["cancel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-leaves/{id}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Approve at the caller's current stage
         * @description Requires `attendance.write`.
         */
        post: operations["approve"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/shifts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List active shifts
         * @description Requires `attendance.read`.
         */
        get: operations["list"];
        put?: never;
        /**
         * Create a shift (HR)
         * @description Requires `attendance.admin`.
         */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/shifts/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get an employee's shift assignments
         * @description Returns the employee's shift-assignment history newest-first by `effectiveFrom`. The first row whose range covers today is the active assignment; rows with `effectiveTo` set are closed history. Requires `attendance.read`.
         */
        get: operations["assignments"];
        put?: never;
        /**
         * Assign a shift to an employee
         * @description Routed under /shifts so the gateway can keep /employees/** scoped to employee-service. Requires `attendance.admin`.
         */
        post: operations["assign"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/regularizations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Submit a regularization
         * @description Self-submit by default. A manager may set `onBehalfOf` to submit for a direct report — the MANAGER stage is auto-approved and the request advances to HR. HR with `attendance.approval.override` may set `onBehalfOf` for any employee, in which case the whole chain is auto-approved (punches written, day recomputed). Requires `attendance.write`.
         */
        post: operations["submit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/regularizations/{id}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reject at caller's current stage
         * @description Requires `attendance.write`.
         */
        post: operations["reject_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/regularizations/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel my regularization
         * @description Submitter-only. Allowed only while the request is PENDING. Requires `attendance.write`.
         */
        post: operations["cancel_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/regularizations/{id}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Approve at caller's current stage
         * @description Requires `attendance.write`.
         */
        post: operations["approve_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Apply for OT on a working day
         * @description Self-apply by default. A manager may set `onBehalfOf` to submit for a direct report — the MANAGER stage is auto-approved and the request advances to HR. HR with `attendance.approval.override` may set `onBehalfOf` for any employee, in which case the whole chain is auto-approved. Requires `attendance.write`.
         */
        post: operations["apply_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/{id}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reject an OT request at the caller's current stage
         * @description Requires `attendance.write`.
         */
        post: operations["reject_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel my OT request
         * @description Submitter-only. PENDING any time, or APPROVED before the work date. If the request was APPROVED the day's OT minute buckets are recomputed. Requires `attendance.write`.
         */
        post: operations["cancel_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/{id}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Approve an OT request at the caller's current stage
         * @description Requires `attendance.write`.
         */
        post: operations["approve_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/bulk": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Bulk apply for a department
         * @description All employees must be in the submitter's department; rows for employees without a punch on the date are returned in `skipped[]`. Requires `attendance.overtime.bulk`.
         */
        post: operations["bulk"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/batches/{batchId}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reject every row in a bulk batch
         * @description Requires `attendance.write`.
         */
        post: operations["rejectBatch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/batches/{batchId}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Approve every row in a bulk batch the caller can act on
         * @description Requires `attendance.write`.
         */
        post: operations["approveBatch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Apply for leave
         * @description Self-apply by default. A manager may set `onBehalfOf` to submit for a direct report — the MANAGER stage is auto-approved (the manager submitted, no re-approval needed) and the request advances to HR. HR with `attendance.approval.override` may set `onBehalfOf` for any employee, in which case the whole chain is auto-approved. Requires `attendance.write`.
         */
        post: operations["apply_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/{id}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reject at the caller's current stage
         * @description Requires `attendance.write`.
         */
        post: operations["reject_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/{id}/days/decide": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Per-day decide (MANAGER stage)
         * @description MANAGER decides each day APPROVE or REJECT. Once all days are decided, the MANAGER stage closes — APPROVED (advances to HR for ratification) if any day was approved, REJECTED (skips later stages) if every day was rejected. HR cannot redo per-day decisions. Requires `attendance.read`.
         */
        post: operations["decideDays"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel my leave
         * @description Requires `attendance.write`.
         */
        post: operations["cancel_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/{id}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Approve at the caller's current stage
         * @description Requires `attendance.write`.
         */
        post: operations["approve_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/types": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List leave types
         * @description Active types only by default. Pass `includeInactive=true` for the HR-admin catalog UI (so deactivated types can be re-enabled). Requires `attendance.read`.
         */
        get: operations["listTypes"];
        put?: never;
        /**
         * Create leave type
         * @description HR-admin. `code` is unique and immutable post-create. Requires `attendance.admin`.
         */
        post: operations["createType"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Preview a leave request
         * @description Dry-run for the apply-leave drawer. Returns the projected workingDays, skip counts, balanceAfter, and any soft warnings the FE should surface before the user hits submit. Same business rules submit enforces. Requires `attendance.read`.
         */
        post: operations["preview"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/holidays": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List holidays for a year
         * @description Requires `attendance.read`.
         */
        get: operations["list_1"];
        put?: never;
        /**
         * Create a holiday (HR)
         * @description Requires `attendance.admin`.
         */
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List devices
         * @description Active only by default. Pass `includeInactive=true` for the HR-admin catalog UI. Requires `attendance.devices.admin`.
         */
        get: operations["list_2"];
        put?: never;
        /**
         * Create a device (HR)
         * @description Requires `attendance.devices.admin`.
         */
        post: operations["create_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/users/{employeeId}/push-templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Push an employee's captured fingerprints onto a device (HR)
         * @description Queues a PUSH_TEMPLATE task per stored fingerprint template so the poller provisions the employee's prints onto THIS device — the deliberate, operator-chosen counterpart to capture (which is store-only). Call it on each device you want the employee's prints on; reusable any time (e.g. when a new gate is added) with no re-scan, since the print is already in the central store. The employee must already be enrolled on this device and the device must be active. FACE templates are skipped. Returns the queued task(s). Requires `attendance.devices.admin`.
         */
        post: operations["pushTemplates"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/users/{employeeId}/capture-template": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Capture an employee's fingerprints off a device (HR)
         * @description Queues a CAPTURE_TEMPLATE task for the poller to read the employee's enrolled fingerprint templates off this device — typically the enrollment device, right after the employee placed a finger on the keypad — and store them centrally. Store-only: capture does NOT push to any other device. To provision the print onto the device(s) you choose, call POST /devices/{id}/users/{employeeId}/push-templates on each target afterwards. The employee must already be on this device. Returns the queued task so the FE can track it via GET /devices/{id}/sync-tasks. Requires `attendance.devices.admin`.
         */
        post: operations["captureTemplate"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/users/unenroll": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Bulk un-enroll users from a device (HR)
         * @description Post-adoption cleanup. Removes the listed matched employees (by id; bridge row dropped) and/or stray device users (by raw deviceUserId; no bridge) from the device — each queues a DELETE_USER for the poller. Unknown ids are skipped, not failed. POST (not DELETE) because it carries a body. Requires `attendance.devices.admin`.
         */
        post: operations["unenroll"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/sync-tasks/{taskId}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Retry a FAILED sync task (HR)
         * @description Re-arms a task that has parked in FAILED — flips back to PENDING with attempts=0 and scheduled_for=now. The next poller cycle picks it up. Requires `attendance.devices.admin`.
         */
        post: operations["retrySyncTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/sync-tasks/{taskId}/fail": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mark a claimed task FAILED
         * @description `retryable=false` parks the row in FAILED immediately. `retryable=true` (default) increments attempts and re-arms with exponential backoff (min(60s × 2^attempts, 1h)) until the configured max-attempts cap, after which the row stays FAILED for HR to retry.
         */
        post: operations["fail"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/sync-tasks/{taskId}/complete": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mark a claimed task DONE
         * @description Flips the task to DONE and applies side effects: UPSERT_USER stamps `attendance_device_users.synced_at`; PUSH_TEMPLATE flips `has_fingerprint` / `has_face` on the bridge row.
         */
        post: operations["complete"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshots": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List device snapshots (HR)
         * @description Newest first, no payloads — counts + status + error only. Filter by `status` (PENDING / COMPLETED / FAILED). Default page size 20 (max 100). Requires `attendance.devices.admin`.
         */
        get: operations["listSnapshots"];
        put?: never;
        /**
         * Request a device snapshot (HR)
         * @description Persists a PENDING snapshot row + queues a SNAPSHOT_DEVICE sync task. The poller drains the task, dials the device, and posts deviceInfo / users / attendance JSON back; the snapshot row flips to COMPLETED at that point. Use GET /devices/{id}/snapshots/{snapshotId} to read the dump. Requires `attendance.devices.admin`.
         */
        post: operations["requestSnapshot"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshots/{snapshotId}/reconciliation/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Export reconciliation as Excel (HR)
         * @description Renders the full reconciliation to an .xlsx, stores it in object storage, and returns a short-lived signed download URL the FE opens directly — the human-readable alternative to the raw JSON snapshot. Requires `attendance.devices.admin`.
         */
        post: operations["exportReconciliation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshots/{snapshotId}/finalize": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Finalize a device snapshot (poller)
         * @description Posted after a SNAPSHOT_DEVICE task completes. Body carries the deviceInfo / users / attendance JSON dumped from the device. The service stores them on the snapshot row, flips its status to COMPLETED, and marks the linked sync task DONE in the same transaction.
         */
        post: operations["finalizeSnapshot"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshots/{snapshotId}/fail": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Mark a device snapshot FAILED (poller)
         * @description Posted when a SNAPSHOT_DEVICE task can't be fulfilled. Same retry/backoff policy as a regular task fail — `retryable=true` (default) re-arms with exponential backoff up to the cap; `retryable=false` parks the task in FAILED. The snapshot row is flipped to FAILED only on the terminal failure (after backoff is exhausted or retryable=false).
         */
        post: operations["failSnapshot"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshots/{snapshotId}/adopt-users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Adopt a snapshot's users into the bridge (HR)
         * @description Reconciles a COMPLETED snapshot's user dump into the device's attendance_device_users bridge — the cutover path for devices that already carry enrollments. Each device user_id (leading zeros stripped) is matched to an employee_code; the bridge row is created or its privilege / syncedAt refreshed, and users with no matching employee are skipped and returned. Idempotent — re-run after adding a device or to pick up keypad-side enrollments. Does NOT set hasFingerprint / hasFace (get_users can't report template presence). Requires `attendance.devices.admin`.
         */
        post: operations["adoptSnapshotUsers"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/reset-hwm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reset the device's high-water-mark (HR)
         * @description Nulls `last_punched_at` so the next poller cycle pulls every record currently on the device. Use after a device clock reset or to re-ingest a swathe of records whose bridge rows landed late. Requires `attendance.devices.admin`.
         */
        post: operations["resetHwm"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/heartbeat": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Heartbeat
         * @description Updates `attendance_devices.status` + `last_seen_at`. Called by the poller on every cycle (success or fail) so the dashboard freshness indicator stays current.
         */
        post: operations["heartbeat"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/comm-password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Set the device's ZK comm password (HR)
         * @description Write-only. The current value is never returned in any response — the GET endpoints expose `commPasswordSet: bool` instead. Send 0 to clear. Requires `attendance.devices.admin`.
         */
        post: operations["setCommPassword"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/snapshots": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Batch payroll snapshot (per-employee, single period)
         * @description Aggregates a per-employee payroll snapshot (lopDays, daysPresent, otHours, workingDays, paidDays, absentDays) for the given employees over the given period. Replaces the per-employee `/attendance/employee/{id}` round-trip loop in payroll's compute. Cap 500 (matches payroll's largest run size today). Requires `attendance.read`.
         */
        post: operations["snapshots"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/shift-overrides": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List overrides covering a date or for an employee over a range
         * @description Requires `attendance.read`.
         */
        get: operations["list_3"];
        put?: never;
        /**
         * Create overrides for a list of employees over a date range
         * @description Requires `attendance.operator`.
         */
        post: operations["create_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/punches/{id}/delete": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Delete a punch (operator)
         * @description Hard-delete with a mandatory audit reason; recomputes the day. The audit event is the trail. Requires `attendance.operator`.
         */
        post: operations["deletePunch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/punches/ingest": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Bulk biometric ingest
         * @description Called by attendance-poller. Each punch carries `(employeeCode, deviceId, punchedAt)` — the device's user_id field IS the employee_code. The service resolves to employee_id via attendance_employees, drops same-time duplicates silently, and triggers attendance_records re-derivation in the same transaction. Punches whose employee_code has no snapshot row come back in `skipped[]` with reason `UNKNOWN_DEVICE_USER` and are parked in the unmatched-replay queue. Requires `attendance.poller`.
         */
        post: operations["ingest"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/punch": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Self-punch (ESS)
         * @description Records a portal punch for the calling employee. Cap of 2 WEB punches per day. Requires `attendance.write`.
         */
        post: operations["punch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/periods/unlock": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Unlock a payroll period
         * @description 404 if the period is not currently locked. Requires `attendance.admin`.
         */
        post: operations["unlock"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/periods/lock": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Lock a payroll period
         * @description Idempotent — re-locking returns the existing lock without error. Requires `attendance.admin`.
         */
        post: operations["lock"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/operator/punches/bulk": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Operator-added punches in bulk
         * @description End-of-shift / paper-form bulk insert. Up to 100 rows per call. Whole batch is one transaction — any failure rolls back the lot. Each row dedups against same-instant duplicates and produces an audit row. Requires `attendance.operator`.
         */
        post: operations["operatorPunchesBulk"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/operator/punch": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Operator-added punch off paper proof
         * @description Operator records a missed punch with a free-text reason. Bypasses the WEB 2/day cap; standard dedup on (employee, exact instant) still applies. Requires `attendance.operator`.
         */
        post: operations["operatorPunch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/approval-delegations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Create a delegation (caller as delegator)
         * @description Requires `attendance.write`.
         */
        post: operations["create_4"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/approval-delegations/on-behalf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Create a delegation on a manager's behalf (HR)
         * @description Requires `attendance.approval.override`.
         */
        post: operations["createOnBehalf"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/shifts/reorder": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Bulk-reorder shifts
         * @description Applies the supplied `displayOrder` to each listed shift in a single transaction. Returns the active catalog. Requires `attendance.admin`.
         */
        patch: operations["reorder"];
        trace?: never;
    };
    "/leaves/types/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Deactivate leave type
         * @description Soft delete — flips `active=false`. Existing balance rows and historical leave requests are preserved; ESS callers stop seeing the type on the apply drawer and dashboard. Re-enable by PATCHing `active=true`. Requires `attendance.admin`.
         */
        delete: operations["deactivateType"];
        options?: never;
        head?: never;
        /**
         * Update leave type
         * @description HR-admin partial update. `code` is not updatable. Requires `attendance.admin`.
         */
        patch: operations["updateType"];
        trace?: never;
    };
    "/holidays/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Delete a holiday (HR)
         * @description Requires `attendance.admin`.
         */
        delete: operations["delete"];
        options?: never;
        head?: never;
        /**
         * Update a holiday (HR)
         * @description Patch — null fields stay unchanged. The holiday date is immutable; delete and recreate if the date itself is wrong. Requires `attendance.admin`.
         */
        patch: operations["update"];
        trace?: never;
    };
    "/devices/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a device
         * @description Requires `attendance.devices.admin`.
         */
        get: operations["get"];
        put?: never;
        post?: never;
        /**
         * Deactivate a device (HR)
         * @description Soft delete — flips `active=false`. Existing punches keep their device_id FK; the row stays available for historical reads. Re-enable by PATCHing `active=true`. Requires `attendance.devices.admin`.
         */
        delete: operations["deactivate"];
        options?: never;
        head?: never;
        /**
         * Update a device (HR)
         * @description Patch — null fields stay unchanged. `code` is immutable; deactivate and create a new row if the code is wrong. Requires `attendance.devices.admin`.
         */
        patch: operations["update_1"];
        trace?: never;
    };
    "/attendance/shift-overrides/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Delete an override (reverts every date in the range)
         * @description Requires `attendance.operator`.
         */
        delete: operations["delete_1"];
        options?: never;
        head?: never;
        /**
         * Patch an override (re-triggers recompute over the union of old + new ranges)
         * @description Requires `attendance.operator`.
         */
        patch: operations["update_2"];
        trace?: never;
    };
    "/attendance/punches/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Amend an existing punch (operator)
         * @description Updates `punchedAt` on a single punch and rewrites the day's attendance record. If the new instant moves the work-date (pre-shift window), both old and new days are recomputed. The before/after snapshot lands on the audit timeline. Requires `attendance.operator`.
         */
        patch: operations["amendPunch"];
        trace?: never;
    };
    "/approval-delegations/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Revoke a delegation
         * @description Requires `attendance.write`.
         */
        delete: operations["revoke"];
        options?: never;
        head?: never;
        /**
         * Patch (extend / shorten / close) a delegation
         * @description Requires `attendance.write`.
         */
        patch: operations["update_3"];
        trace?: never;
    };
    "/templates/{templateId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Fetch a template's bytes
         * @description Returns the base64-encoded template payload for the poller to push to the target device. Referenced via `templateId` in the PUSH_TEMPLATE task payload.
         */
        get: operations["get_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-leaves/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a short leave
         * @description Requires `attendance.read`.
         */
        get: operations["get_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-leaves/pending-approvals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pending approvals — caller's stage-aware inbox
         * @description Paginated. Date filter on `leaveDate`. Default sort newest-first. Requires `attendance.read`.
         */
        get: operations["pending"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/short-leaves/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My short leaves
         * @description Paginated list of the caller's short leaves. All filters optional. Default sort `leaveDate,desc`. Default page size 20 (max 200). Requires `attendance.read`.
         */
        get: operations["mine"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/leaves/taken": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Leave taken — Excel
         * @description Approved leave days in the date range. Requires `attendance.leaves.reports.run`.
         */
        get: operations["leaveTaken"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/leaves/carry-forward": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Year-end carry-forward forecast — Excel
         * @description Projected carry-forward into next year given current allocations and the leave-type carry policy. Requires `attendance.leaves.reports.run`.
         */
        get: operations["carryForward"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/leaves/balance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Leave balance snapshot — Excel
         * @description Per employee × leave type × allocated/used/pending/carried for the named year. Requires `attendance.leaves.reports.run`.
         */
        get: operations["leaveBalance"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/leaves/application-log": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Leave application log — Excel
         * @description Every leave request in the date range with its status. Requires `attendance.leaves.reports.run`.
         */
        get: operations["leaveApplicationLog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/attendance/shift-roster": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Shift roster export — Excel
         * @description Active shift assignments overlapping with the range. Requires `attendance.reports.run`.
         */
        get: operations["shiftRoster"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/attendance/regularization-log": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Regularization log — Excel
         * @description Regularization requests in the range. Optional `status` filter (SUBMITTED / APPROVED / REJECTED / WITHDRAWN). Requires `attendance.reports.run`.
         */
        get: operations["regularizationLog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/attendance/ot-register": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * OT register — Excel
         * @description OT requests within the range, joined with the attendance record's OT minutes. Requires `attendance.reports.run`.
         */
        get: operations["otRegister"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/attendance/monthly": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Monthly attendance summary — Excel
         * @description Per-employee status counts for the month. Requires `attendance.reports.run`.
         */
        get: operations["monthly"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/attendance/missing-punch": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Missing-punch log — Excel
         * @description PRESENT records with at least one of {check-in, check-out} null in the range. Requires `attendance.reports.run`.
         */
        get: operations["missingPunch"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/attendance/late-comers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Late comers — Excel
         * @description Aggregated late-day counts and totals per employee within the date range, ranked by frequency. Requires `attendance.reports.run`.
         */
        get: operations["lateComers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/attendance/daily": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Daily attendance sheet — Excel
         * @description Every employee's record for the named date. Requires `attendance.reports.run`.
         */
        get: operations["daily"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/attendance/absentee-log": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Absentee log — Excel
         * @description Every ABSENT record in the range. Requires `attendance.reports.run`.
         */
        get: operations["absenteeLog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/regularizations/pending-approvals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pending approvals for the caller
         * @description Paginated. Date filter on `attendanceDate`. Default sort newest-first. Requires `attendance.read`.
         */
        get: operations["pending_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/pending-approvals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pending OT approvals — caller's stage-aware inbox
         * @description Paginated. Date filter on `attendanceDate`. Default sort newest-first. Requires `attendance.read`.
         */
        get: operations["pending_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My OT requests
         * @description Paginated list of the caller's OT requests. All filters optional. Default sort `attendanceDate,desc`. Default page size 20 (max 200). Requires `attendance.read`.
         */
        get: operations["mine_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/overtime/me/with-records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My OT requests joined with attendance minutes (ESS)
         * @description Returns the caller's OT requests with the attendance record's OT and worked-minutes for the same (employee, workDate) joined in. Minutes are zero when the OT is still PENDING / REJECTED, or when no attendance record exists yet for the day. Requires `attendance.read`.
         */
        get: operations["overtimeMine"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a leave request
         * @description Requires `attendance.read`.
         */
        get: operations["get_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/{id}/days": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-day breakdown
         * @description Requires `attendance.read`.
         */
        get: operations["days"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/pending-approvals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pending approvals — caller's stage-aware inbox
         * @description Paginated. Date filter is overlap-style. Default sort newest-first. Inbox-derived; max page size 200. Requires `attendance.read`.
         */
        get: operations["pending_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My leaves
         * @description Paginated list of the caller's leave requests. All filters are optional. Date filter is overlap-style — include any request whose [fromDate, toDate] intersects [from, to]. Default sort is `fromDate,desc`. Default page size 20 (max 200). Requires `attendance.read`.
         */
        get: operations["myLeaves"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/me/balances": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My leave balances
         * @description Without `year`, returns the currently active cycle per leave type (the cycle window containing today — correct for annual anniversary cycles and Jul–Jun fiscal cycles alike). Pass `year` for a historical view: cycles that started in that calendar year. Requires `attendance.read`.
         */
        get: operations["myBalances"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/leaves/apply-context": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Apply-leave drawer context
         * @description Single round-trip bundle for the apply-leave drawer: leave types catalog, caller's current-year balances, and holidays in the next 90 days.
         */
        get: operations["leaveApplyContext"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List employees enrolled on a device (HR)
         * @description Bridge rows for the device — employeeId, privilege, hasFingerprint / hasFace flags, last syncedAt. The employee's device-side identifier is the employee_code (resolved through attendance_employees). Default sort `employeeId,asc`. Default page size 100 (max 500). Requires `attendance.devices.admin`.
         */
        get: operations["listDeviceUsers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/sync-tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List sync tasks for a device (HR)
         * @description All sync tasks for a device, newest scheduled_for first. Filter by `status` (repeat for multi-select: PENDING / IN_PROGRESS / DONE / FAILED). Default sort `scheduledFor,desc`. Default page size 50 (max 200). Requires `attendance.devices.admin`.
         */
        get: operations["listSyncTasks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/sync-tasks/next": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Claim next batch of sync tasks
         * @description Atomically claims up to `limit` PENDING tasks for the device whose `scheduled_for <= now` (FOR UPDATE SKIP LOCKED). Empty list when nothing is ready. limit is clamped to [1, 100].
         */
        get: operations["claimNext"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshots/{snapshotId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get a device snapshot with payloads (HR)
         * @description Full snapshot: deviceInfo / users / attendance JSON populated when status is COMPLETED. The FE can render these in a viewer or download them as JSON files. Requires `attendance.devices.admin`.
         */
        get: operations["getSnapshot"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshots/{snapshotId}/reconciliation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Snapshot reconciliation rows (HR)
         * @description Paginated device users reconciled against the employee mirror. Filter by `category` (MATCHED_ACTIVE / MATCHED_INACTIVE / UNMATCHED). For UNMATCHED rows pass `deviceUserId` back to the un-enroll endpoint; for matched rows pass `employeeId`. Default page size 100 (max 1000). Requires `attendance.devices.admin`.
         */
        get: operations["reconciliation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/snapshots/{snapshotId}/reconciliation/summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Snapshot reconciliation summary (HR)
         * @description Counts of the snapshot's device users by category: matched-active (keep), matched-inactive (e.g. terminated — un-enroll candidate), and unmatched (stray, no employee — un-enroll candidate). Requires `attendance.devices.admin`.
         */
        get: operations["reconciliationSummary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/poller-targets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Poller-targets feed (poller)
         * @description Active devices with a non-null `deviceIp`, joined to their high-water-mark. **Carries the ZK comm password** — gated to the poller's service account. Requires `attendance.poller`.
         */
        get: operations["pollerTargets"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Org-wide attendance records (HR / admin / operator)
         * @description Paginated daily-record list across the company. All filters are optional; when no date filter is supplied the response is scoped to today (server-local). `date` is a shortcut for `from=to=date`. `employeeId` wins over `employeeCode`; `departmentId`/`sectionId` are pre-resolved against employee-service. Default sort `attendanceDate,desc;employeeId,asc`. Default page size 50 (max 500). Requires `attendance.admin` OR `attendance.operator` — covers HR, admin, and the attendance-operator persona; managers and plain employees use the per-employee `/me` and `/employee/{id}` endpoints instead.
         */
        get: operations["records"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/records/with-employee": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Org-wide attendance records with employee summary (HR / admin / operator)
         * @description Sibling to `/attendance/records` — same filters and pagination, plus a compact `employee` summary on every row so the FE can render the table without an extra fan-out per row. Requires `attendance.admin` OR `attendance.operator`.
         */
        get: operations["recordsWithEmployee"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/punches": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Org-wide punches (HR / admin / operator)
         * @description Paginated raw-punch log across the company — primary view for the attendance-operator's daily reconciliation. All filters are optional; default scope is today. `date` is a shortcut for `from=to=date`. `employeeId` wins over `employeeCode`; `departmentId`/`sectionId` are pre-resolved against employee-service. Default sort `punchedAt,desc`. Default page size 50 (max 500). Requires `attendance.admin` OR `attendance.operator`.
         */
        get: operations["punchesList"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/punches/with-employee": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Org-wide punches with employee summary (HR / admin / operator)
         * @description Sibling to `/attendance/punches` — same filters and pagination, plus a compact `employee` summary on every row. Primary list view for the attendance-operator's daily reconciliation. Requires `attendance.admin` OR `attendance.operator`.
         */
        get: operations["punchesWithEmployee"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/periods/locks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List locked periods in a year
         * @description Returns one row per locked (year, month). Requires `attendance.read`.
         */
        get: operations["list_4"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/org/pulse": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Org-wide attendance pulse for a date
         * @description Single SQL aggregate over `attendance_records` grouped by status. Defaults to today (server-local date). Requires `attendance.read`.
         */
        get: operations["pulse"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/org/not-checked-in": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Who hasn't checked in yet (live)
         * @description Active employees whose shift-start + grace has elapsed today without a check-in punch, most overdue first. Weekend / holiday / approved-leave and not-yet-due shifts are excluded; the list shrinks as people punch in and clears itself — nothing is persisted (distinct from the nightly ABSENT status). Paginated (default 20, max 200); always sorted by `minutesOverdue` descending. Requires `attendance.read`.
         */
        get: operations["notCheckedIn"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/org/attendance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Org-wide attendance volume per day over a range
         * @description Per-day status counts. Days with no records (rare — usually means the nightly close hasn't run) are absent from the response, not zero rows. Requires `attendance.read`.
         */
        get: operations["dailyTrend"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/operator/dashboard": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Operator dashboard composite
         * @description One-shot landing payload for the attendance-operator UI: org pulse, today's punch / override / pending-regularization counts, and the two action queues (missing checkouts, late arrivals) enriched with employee summaries. Default date is today (server-local Asia/Karachi). Requires `attendance.admin` OR `attendance.operator`.
         */
        get: operations["operatorDashboard"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My monthly attendance
         * @description Per-day rows for the calling employee for the supplied calendar month. Requires `attendance.read`.
         */
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/me/today/composite": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Today screen composite (ESS)
         * @description One round-trip Today view: today's record, today's punches, any approved leave covering today, today's holiday, the next upcoming holiday + approved leave, and a 3-bucket leave balance summary (annual / sick / casual).
         */
        get: operations["today"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/me/punches": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My punches for a specific day
         * @description Requires `attendance.read`.
         */
        get: operations["myPunches"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/me/month/composite": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Calendar/month view (ESS)
         * @description Per-day rows for the supplied month, pre-merged with holidays in the month and any approved leaves that overlap the month. Each day carries a `kind` render hint plus the underlying record / holiday / leave overlay.
         */
        get: operations["month"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/me/heatmap": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My heatmap (ESS)
         * @description Per-day status + late variance for the calling employee. Same shape as the per-id endpoint; this one is open to any authenticated employee. Requires `attendance.read`.
         */
        get: operations["myHeatmap"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/employees": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Batch single-date lookup (manager / HR)
         * @description Returns a list of attendance records for the given employees on the given date — drives the BFF "team today" composite without N+1 calls. Caller buckets the result by `employeeId`. Cap 200. `date` defaults to today (server-local). Requires `attendance.read`.
         */
        get: operations["employeesOnDate"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/employee/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-employee daily attendance over a range (HR / manager)
         * @description Requires `attendance.read`.
         */
        get: operations["forEmployee"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/employee/{id}/today": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-employee today (HR / manager spot-check)
         * @description Returns today's attendance projection. Always 200: when no punches and no nightly close have produced a record yet, the response is `{ state: NOT_PUNCHED, record: null }`. Requires `attendance.read`.
         */
        get: operations["employeeToday"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/employee/{id}/punches": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-employee punches over a range (HR / manager)
         * @description Requires `attendance.read`.
         */
        get: operations["punchesForEmployee"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/employee/{id}/heatmap": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-employee heatmap rollup
         * @description Per-day status + late variance for a calendar month — server-side rollup that drives the dashboard heatmap (D-S5). Falls back to WEEKEND on Sunday and ABSENT on weekdays for days with no record. Requires `attendance.read`.
         */
        get: operations["heatmapForEmployee"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/attendance/employee/{id}/canteen-usage": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-employee canteen usage over a range
         * @description Aggregates CANTEEN-typed device events for the employee between `from` and `to` (inclusive). Returns `days` (distinct calendar dates) and `weeks` (distinct 7-day chunks counted from the 1st of the month: days 1–7, 8–14, 15–21, 22–28, 29–end). Payroll's compute consumes this to apply the grade-conditional canteen deduction. Requires `attendance.read`.
         */
        get: operations["canteenUsage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/approvals/inbox": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Combined approvals inbox
         * @description One round-trip approvals inbox: leaves, overtime, and regularization pending-approval lists fetched in sequence, then enriched with an `employee` summary per row. Counts are the visible totals (not the size of the returned page). Requires `attendance.read`.
         */
        get: operations["approvalsInbox"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/approvals/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * My approval-decision history
         * @description Returns one row per stage the caller closed (APPROVED or REJECTED) — unified across leave / OT / regularization. The result is always scoped to the caller; there is no `deciderId` query param. `from` and `to` are required, `to` is inclusive, and the window must be at most 366 days. Default sort `decidedAt,desc`. Default page size 20 (max 200).
         */
        get: operations["myHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/approval-delegations/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List delegations involving the caller
         * @description Requires `attendance.read`.
         */
        get: operations["myDelegations"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/leaves/utilization": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Leave utilization for a year
         * @description Per leave-type and overall: allocated (incl. carried) + used + pending + used%.
         */
        get: operations["utilization"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/leaves/seasonality": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Leave seasonality (monthly days taken)
         * @description Per month: total leave days + breakdown by leave-type. Window 1..24 months; defaults to 12.
         */
        get: operations["seasonality"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/attendance/scorecard/{employeeId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Per-employee punctuality scorecard
         * @description Rolling N-day window. PRESENT / on-time / late / absent / leave counts plus on-time% and OT hours. Window 1..365; defaults to 90.
         */
        get: operations["scorecard"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/attendance/ot-trend": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Monthly OT-hours trend
         * @description Per month: total OT minutes + OT hours. Window 1..24 months; defaults to 12.
         */
        get: operations["otTrend"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/attendance/on-time-pct": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Monthly on-time % trend
         * @description Per month: PRESENT count + on-time count + on-time%. Window 1..24 months; defaults to 12.
         */
        get: operations["onTime"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analytics/attendance/absenteeism-pct": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Monthly absenteeism % trend
         * @description Per month: PRESENT+ABSENT+LEAVE denominator + ABSENT count + absenteeism%. Window 1..24 months; defaults to 12.
         */
        get: operations["absenteeism"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{id}/users/{employeeId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Remove an employee from a device (HR)
         * @description Drops the bridge row immediately and queues a DELETE_USER for the poller. If the device is offline, the queued task drains when it returns. Requires `attendance.devices.admin`.
         */
        delete: operations["removeUserFromDevice"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Poller posts a captured template here. Store-only — the row is saved centrally; pushing to devices is a separate, operator-chosen step. */
        UploadTemplateRequest: {
            /** Format: int64 */
            employeeId: number;
            /** @enum {string} */
            kind: "FINGERPRINT" | "FACE";
            /**
             * Format: int32
             * @description 0..9 for FINGERPRINT; must be null for FACE.
             */
            fingerId?: number | null;
            /** Format: int32 */
            format: number;
            /** @description Base64-encoded template bytes. */
            bytes: string;
            /** Format: int64 */
            capturedDeviceId: number;
        };
        /** @description A captured biometric template. Bytes are base64 in transit so this DTO is JSON-safe. */
        EmployeeTemplateDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @enum {string} */
            kind?: "FINGERPRINT" | "FACE";
            /**
             * Format: int32
             * @description 0..9 for FINGERPRINT; null for FACE.
             */
            fingerId?: number | null;
            /** Format: int32 */
            format?: number;
            /** @description Base64-encoded template bytes. */
            bytes?: string;
            /** Format: int64 */
            capturedDeviceId?: number;
            /** Format: date-time */
            capturedAt?: string;
        };
        /** @description Apply for a sub-day leave: leave work between (startTime, endTime) on leaveDate. Total minutes is derived from the window. Different from regularization (which inserts missing punches) and from leave_requests (full or half day). */
        CreateShortLeaveRequest: {
            /** Format: date */
            leaveDate: string;
            /** @example 13:00 */
            startTime: string;
            /**
             * @description Must be strictly greater than startTime — no midnight wrap.
             * @example 15:00
             */
            endTime: string;
            reason?: string | null;
        };
        /** @description One stage in the approval chain. `assignedToName` / `decidedByName` are looked up from employee-service when the controller has access; null otherwise. */
        ApprovalStepDto: {
            /** Format: int32 */
            stageNo?: number;
            stageRole?: string;
            /** Format: int64 */
            assignedTo?: number | null;
            assignedToName?: string | null;
            status?: string;
            /** Format: int64 */
            decidedBy?: number | null;
            decidedByName?: string | null;
            /** Format: date-time */
            decidedAt?: string | null;
            note?: string | null;
        };
        /** @description The PENDING stage of the chain. Null when the request is finalized. */
        CurrentStageDto: {
            /** Format: int32 */
            stageNo?: number;
            stageRole?: string;
            /** Format: int64 */
            assignedTo?: number | null;
            assignedToName?: string | null;
        };
        ShortLeaveRequestDto: {
            /** Format: int64 */
            id?: number;
            /** @description Human-readable code, e.g. SL-2026-0017. */
            requestCode?: string;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            leaveDate?: string;
            startTime?: string;
            endTime?: string;
            /**
             * Format: int32
             * @description Derived from (startTime, endTime).
             */
            totalMinutes?: number;
            reason?: string | null;
            status?: string;
            currentStage?: components["schemas"]["CurrentStageDto"];
            steps?: components["schemas"]["ApprovalStepDto"][];
            actions?: string[];
            /** Format: date-time */
            createdAt?: string;
        };
        /** @description Reason recorded on the approval step. */
        RejectRequest: {
            reason: string;
        };
        /** @description Optional note attached to the approval step. */
        ApproveRequest: {
            note?: string | null;
        };
        CreateShiftRequest: {
            code: string;
            name: string;
            startTime: string;
            endTime: string;
            breakStartTime?: string | null;
            breakEndTime?: string | null;
            /**
             * Format: int32
             * @description Default 10.
             */
            graceMinutes?: number;
            /**
             * Format: int32
             * @description Extra minutes added to break + end on Fridays only. Null/0 = no Friday adjustment.
             */
            fridayExtraMinutes?: number;
        };
        ShiftDto: {
            /** Format: int64 */
            id?: number;
            code?: string;
            name?: string;
            startTime?: string;
            endTime?: string;
            breakStartTime?: string | null;
            breakEndTime?: string | null;
            /**
             * Format: int32
             * @description Derived from break window.
             */
            breakMinutes?: number;
            /** Format: int32 */
            graceMinutes?: number;
            /** Format: int32 */
            fridayExtraMinutes?: number | null;
            active?: boolean;
            /**
             * Format: int32
             * @description HR-controlled sort order; lower sorts first.
             * @example 10
             */
            displayOrder?: number;
        };
        AssignShiftRequest: {
            /** Format: int64 */
            shiftId: number;
            /** Format: date */
            effectiveFrom: string;
            /** Format: date */
            effectiveTo?: string | null;
        };
        /** @description Submit a regularization for a missed check-in / check-out. At least one of `checkIn` / `checkOut` must be set; `reason` is mandatory. The request flows through the same multi-stage approval chain as leaves and OT. */
        RegularizationRequest: {
            /** Format: date */
            workDate: string;
            /** Format: date-time */
            checkIn?: string | null;
            /** Format: date-time */
            checkOut?: string | null;
            reason: string;
            /**
             * Format: int64
             * @description Submit on behalf of another employee. Caller must be the target's direct manager, or hold `attendance.approval.override` (HR). When set, the MANAGER stage is auto-approved (manager-on-behalf) or the whole chain is auto-approved (HR override).
             */
            onBehalfOf?: number | null;
        };
        RegularizationRequestDto: {
            /** Format: int64 */
            id?: number;
            /** @description Human-readable code, e.g. REG-2026-0008. */
            requestCode?: string;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            workDate?: string;
            /** Format: date-time */
            requestedCheckIn?: string | null;
            /** Format: date-time */
            requestedCheckOut?: string | null;
            reason?: string;
            status?: string;
            currentStage?: components["schemas"]["CurrentStageDto"];
            steps?: components["schemas"]["ApprovalStepDto"][];
            actions?: string[];
            /** Format: date-time */
            createdAt?: string;
        };
        /** @description Self-apply OT for a single working day. Authorization-only — no hours/minutes; actual minutes are derived later from `attendance_records.worked_minutes`. The chosen `overtimeType` selects which OT bucket the day's overtime lands in — the other two stay zero. */
        CreateOvertimeRequest: {
            /** Format: date */
            workDate: string;
            /**
             * @description Which OT bucket the day's overtime is credited to.
             * @enum {string}
             */
            overtimeType: "NORMAL" | "EXTRA" | "SPECIAL";
            reason?: string | null;
            /**
             * Format: int64
             * @description Submit on behalf of another employee. Caller must be the target's direct manager, or hold `attendance.approval.override` (HR). When set, the MANAGER stage is auto-approved (manager-on-behalf) or the whole chain is auto-approved (HR override).
             */
            onBehalfOf?: number | null;
        };
        OvertimeRequestDto: {
            /** Format: int64 */
            id?: number;
            /** @description Human-readable code, e.g. OT-2026-0017. */
            requestCode?: string;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            workDate?: string;
            /** @description NORMAL / EXTRA / SPECIAL — the bucket the day's overtime is credited to. */
            overtimeType?: string;
            reason?: string | null;
            /** Format: uuid */
            bulkBatchId?: string | null;
            status?: string;
            currentStage?: components["schemas"]["CurrentStageDto"];
            steps?: components["schemas"]["ApprovalStepDto"][];
            actions?: string[];
            /** Format: date-time */
            createdAt?: string;
        };
        /** @description Bulk OT submission. All employees must be in the submitter's department; rows for employees with no punch on the date are skipped (returned in `skipped[]`). `overtimeType` applies to every row in the batch. */
        BulkOvertimeRequest: {
            /** Format: date */
            workDate: string;
            /**
             * @description Which OT bucket the day's overtime is credited to, for every employee in the batch.
             * @enum {string}
             */
            overtimeType: "NORMAL" | "EXTRA" | "SPECIAL";
            reason?: string | null;
            employeeIds: number[];
        };
        /** @description Outcome of a bulk OT submission. `created[]` lists the rows actually written; `skipped[]` lists employee ids that were silently dropped because they had no punch on the date. */
        BulkOvertimeResponse: {
            /** Format: uuid */
            bulkBatchId?: string;
            created?: components["schemas"]["OvertimeRequestDto"][];
            skipped?: number[];
        };
        /** @description ESS leave application. Sundays + holidays in the range are skipped at submit time. */
        CreateLeaveRequest: {
            /** Format: int64 */
            leaveTypeId: number;
            /** Format: date */
            fromDate: string;
            /** Format: date */
            toDate: string;
            /** @description True for half-day requests; combine with `halfDayPart`. */
            halfDay?: boolean;
            /** @enum {string|null} */
            halfDayPart?: "AM" | "PM" | null;
            reason?: string | null;
            /**
             * Format: int64
             * @description Submit on behalf of another employee. Caller must be the target's direct manager, or hold `attendance.approval.override` (HR). When set, the MANAGER stage is auto-approved (manager-on-behalf) or the whole chain is auto-approved (HR override).
             */
            onBehalfOf?: number | null;
        };
        /** @description Leave request DTO returned to submitters and approvers. */
        LeaveRequestDto: {
            /** Format: int64 */
            id?: number;
            /** @description Human-readable code, e.g. LR-2026-0042. */
            requestCode?: string;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int64 */
            leaveTypeId?: number;
            /** @description Leave type code, denormalized. */
            leaveTypeCode?: string;
            /** Format: date */
            fromDate?: string;
            /** Format: date */
            toDate?: string;
            halfDay?: boolean;
            halfDayPart?: string | null;
            days?: number;
            reason?: string | null;
            status?: string;
            currentStage?: components["schemas"]["CurrentStageDto"];
            steps?: components["schemas"]["ApprovalStepDto"][];
            /** @description Actions the calling user can take right now. */
            actions?: string[];
            /** Format: date-time */
            createdAt?: string;
        };
        /** @description Per-day approve/reject — only allowed when the current PENDING stage is the FINAL stage. */
        PerDayDecideRequest: {
            decisions: components["schemas"]["PerDayDecision"][];
        };
        PerDayDecision: {
            /** Format: date */
            workDate: string;
            /** @enum {string} */
            action: "APPROVE" | "REJECT";
            reason?: string | null;
        };
        /** @description HR-admin create payload. `code` must be unique and is immutable post-create — it is denormalized into balance rows, leave requests, and audit events. */
        CreateLeaveTypeRequest: {
            /** @example ANNUAL */
            code: string;
            /** @example Annual leave */
            name: string;
            /** @description Defaults to true. */
            paid?: boolean | null;
            /** @example 14 */
            defaultPerYear: number;
            /**
             * @description Entitlement cycle: JOINING (anniversary, e.g. annual leave), FISCAL (Jul–Jun, e.g. M1/Casual), or NONE (occasional, calendar-year lazy).
             * @enum {string}
             */
            cycleAnchor: "JOINING" | "FISCAL" | "NONE";
            /** @description Prorate the first cycle for a mid-cycle joiner. FISCAL types only. Defaults to false. */
            prorateOnEntry?: boolean | null;
            /** @description Encash unused balance when the cycle closes at renewal. Defaults to false. */
            encashable?: boolean | null;
            /** @description Defaults to 0. */
            carryForwardMax?: number | null;
            /** @description Defaults to true. */
            requiresApproval?: boolean | null;
            /** @description Defaults to true. */
            showOnDashboard?: boolean | null;
        };
        LeaveTypeDto: {
            /** Format: int64 */
            id?: number;
            code?: string;
            name?: string;
            paid?: boolean;
            defaultPerYear?: number;
            /** @description Entitlement cycle anchor: JOINING, FISCAL, or NONE. */
            cycleAnchor?: string;
            /** @description Prorate the first cycle for a mid-cycle joiner (FISCAL types). */
            prorateOnEntry?: boolean;
            /** @description Unused balance is encashed when the cycle closes at renewal. */
            encashable?: boolean;
            carryForwardMax?: number;
            requiresApproval?: boolean;
            active?: boolean;
            /** @description If false, the dashboard balance card hides this type. It still appears on the apply-leave drawer. */
            showOnDashboard?: boolean;
        };
        /** @description Dry-run shape for the apply-leave drawer. Same business rules submit enforces — Sundays + holidays are skipped, half-day collapses to 0.5, balance projection uses current balances. `warnings[]` carries soft hints (e.g. NEGATIVE_BALANCE, CROSSES_FY) that the FE can surface without blocking submit; the actual submit will still fail hard if a rule is violated. */
        LeavePreviewDto: {
            /** @description Net working days that count toward the request. */
            workingDays?: number;
            /** Format: int32 */
            sundaysSkipped?: number;
            /** Format: int32 */
            holidaysSkipped?: number;
            /** @description Caller's available balance for this leave type after the request lands. */
            balanceAfter?: number | null;
            /** @description Soft warnings; submit will still attempt with these set. */
            warnings?: string[];
        };
        CreateHolidayRequest: {
            /** Format: date */
            holidayDate: string;
            name: string;
            /** @enum {string} */
            type: "NATIONAL" | "COMPANY" | "PROVINCIAL";
            description?: string | null;
        };
        HolidayDto: {
            /** Format: int64 */
            id?: number;
            /** Format: date */
            holidayDate?: string;
            name?: string;
            type?: string;
            description?: string | null;
        };
        /** @description HR-admin create payload for the biometric / kiosk device catalog. `code` must be unique and is immutable post-create. The comm port is read by the attendance-poller sidecar to open a ZK-protocol session — leave at the default (4370) for stock ZKTeco units. The ZK comm password is set via the dedicated POST /devices/{id}/comm-password endpoint and is never returned in any GET. */
        CreateAttendanceDeviceRequest: {
            /** @example RND_RECEPTION */
            code: string;
            /** @example Reception kiosk */
            name: string;
            location?: string | null;
            /**
             * @description IPv4 / IPv6 the poller dials.
             * @example 10.10.20.5
             */
            deviceIp?: string | null;
            /**
             * Format: int32
             * @description Default 4370.
             * @example 4370
             */
            commPort?: number | null;
            /** @description True for the single device HR uses for in-person enrollment. Default false. */
            enrollment?: boolean | null;
            /**
             * @description Physical role of the device. Default ATTENDANCE — set to CANTEEN or ACCESS_LOG for units whose punches shouldn't count as attendance.
             * @enum {string|null}
             */
            deviceType?: "ATTENDANCE" | "CANTEEN" | "ACCESS_LOG" | null;
        };
        AttendanceDeviceDto: {
            /** Format: int64 */
            id?: number;
            code?: string;
            name?: string;
            location?: string | null;
            /** @description IP address the poller dials in to. */
            deviceIp?: string | null;
            /**
             * Format: int32
             * @description ZK protocol TCP port; default 4370.
             */
            commPort?: number;
            /** @description True when a non-zero ZK comm password has been set. Use POST /devices/{id}/comm-password to set it. */
            commPasswordSet?: boolean;
            /** @description True for the single device HR uses to enroll fingerprints / faces. */
            enrollment?: boolean;
            /**
             * @description Physical role of the device. Default ATTENDANCE; flip to CANTEEN or ACCESS_LOG for units whose punches shouldn't count as attendance.
             * @enum {string}
             */
            deviceType?: "ATTENDANCE" | "CANTEEN" | "ACCESS_LOG";
            /**
             * @description Last reported by the poller's heartbeat.
             * @enum {string}
             */
            status?: "UNKNOWN" | "ONLINE" | "OFFLINE" | "ERROR";
            /** Format: date-time */
            lastSeenAt?: string | null;
            /**
             * Format: date-time
             * @description High-water-mark for biometric ingest. Null when no punch has been received yet or after POST /devices/{id}/reset-hwm.
             */
            lastPunchedAt?: string | null;
            /**
             * Format: int64
             * @description Number of unmatched-card punches waiting in the replay queue.
             */
            unmatchedCount?: number;
            active?: boolean;
        };
        /** @description A claimed sync task as the poller sees it. Payload shape varies by taskType (see V0008 SQL comment in the attendance service). */
        DeviceSyncTaskDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            deviceId?: number;
            /** Format: int64 */
            employeeId?: number;
            /** @enum {string} */
            taskType?: "UPSERT_USER" | "DELETE_USER" | "PUSH_TEMPLATE" | "CAPTURE_TEMPLATE" | "SNAPSHOT_DEVICE";
            /** @description Task payload as a free-form object; keys vary by taskType. */
            payload?: {
                [key: string]: unknown;
            };
            /** @enum {string} */
            status?: "PENDING" | "IN_PROGRESS" | "DONE" | "FAILED";
            /** Format: int32 */
            attempts?: number;
            /** Format: date-time */
            scheduledFor?: string;
            /** Format: date-time */
            claimedAt?: string | null;
            lastError?: string | null;
        };
        /** @description Bulk remove enrolled users from a device. Each entry queues a DELETE_USER for the poller and drops the bridge row (if any). Provide matched employees by id and/or stray device users by their raw device user_id. At least one list must be non-empty. */
        UnenrollDeviceUsersRequest: {
            /** @description Matched employees to un-enroll. Their bridge row is dropped and a DELETE_USER (keyed by employee_code) is queued. */
            employeeIds?: number[];
            /** @description Raw device user_ids with no employee record (UNMATCHED rows). A DELETE_USER keyed by the raw user_id is queued; there is no bridge row to drop. */
            deviceUserIds?: string[];
        };
        /** @description Outcome of a bulk un-enroll. DELETE_USER tasks drain on the next poller cycle; offline devices drain when they return. */
        UnenrollResultDto: {
            /**
             * Format: int32
             * @description DELETE_USER tasks queued.
             */
            queued?: number;
            /**
             * Format: int32
             * @description Bridge rows dropped (matched employees that were enrolled).
             */
            droppedBridges?: number;
            /**
             * Format: int32
             * @description Entries skipped (unknown employee id, or blank device user_id).
             */
            skipped?: number;
        };
        /** @description Reported by the poller when a sync task failed against the device. retryable=false (e.g. template format mismatch, unknown user) parks the row in FAILED immediately regardless of attempts; retryable=true (the default) increments attempts and re-arms with exponential backoff until max-attempts is hit. */
        FailSyncTaskRequest: {
            error: string;
            /** @description Defaults to true. */
            retryable?: boolean | null;
        };
        /** @description Reported by the poller when a sync task succeeded on the device. */
        CompleteSyncTaskRequest: {
            /** @description Free-text confirmation, e.g. firmware echo of the new user. */
            detail?: string | null;
        };
        /** @description Lightweight view of a device snapshot — status + counts + error, no payloads. Use the per-id GET to fetch the full deviceInfo / users / attendance JSON. */
        DeviceSnapshotSummaryDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            deviceId?: number;
            /** Format: int64 */
            syncTaskId?: number | null;
            /** @enum {string} */
            status?: "PENDING" | "COMPLETED" | "FAILED";
            requestedBy?: string;
            /** Format: date-time */
            requestedAt?: string;
            /** Format: date-time */
            completedAt?: string | null;
            /** Format: int32 */
            userCount?: number | null;
            /** Format: int32 */
            recordCount?: number | null;
            error?: string | null;
        };
        /** @description A generated Excel reconciliation report uploaded to object storage. The downloadUrl is a short-lived signed link the FE opens directly. */
        ReconciliationExportDto: {
            /** @description Short-lived signed GET URL for the .xlsx. */
            downloadUrl?: string;
            /** @description Object key in the reports bucket. */
            objectKey?: string;
        };
        /** @description Posted by the poller when a SNAPSHOT_DEVICE task completes. Carries the captured deviceInfo / users / attendance JSON straight from the device. The service stores them on the snapshot row and flips its status to COMPLETED. */
        FinalizeDeviceSnapshotRequest: {
            /** @description Device metadata — firmware, serial, platform, clock, etc. */
            deviceInfo: unknown;
            /** @description Shape: {count, users: [...]}. */
            users: unknown;
            /** @description Shape: {count, records: [...]}. */
            attendance: unknown;
            /**
             * Format: int32
             * @description Convenience count of users in the dump (mirrors users.count).
             * @example 1240
             */
            userCount?: number;
            /**
             * Format: int32
             * @description Convenience count of attendance records (mirrors attendance.count).
             * @example 8423
             */
            recordCount?: number;
        };
        /** @description Posted by the poller when a SNAPSHOT_DEVICE task can't be fulfilled — device unreachable, ZK error, or anything else that means there is no payload to deliver. Mirrors the sync task fail call but flips the snapshot row too. */
        FailDeviceSnapshotRequest: {
            /**
             * @description Human-readable failure reason.
             * @example device unreachable: TimeoutError
             */
            error: string;
            /**
             * @description Whether the underlying sync task should remain retryable. Default true — service applies the standard backoff. Pass false for permanent failures (firmware mismatch, etc.).
             * @example true
             */
            retryable?: boolean;
        };
        /** @description Outcome of adopting a completed device snapshot's user dump into the attendance_device_users bridge. Matches each device user_id (leading zeros stripped) to an employee_code; creates or updates the bridge row and skips users with no matching employee. hasFingerprint / hasFace are NOT inferred here — get_users() can't report template presence, so they stay at their current value (false on newly created rows). */
        DeviceUserAdoptionDto: {
            /** Format: int64 */
            deviceId?: number;
            /** Format: int64 */
            snapshotId?: number;
            /**
             * Format: int32
             * @description Total user entries present in the snapshot dump.
             */
            totalUsers?: number;
            /**
             * Format: int32
             * @description Bridge rows newly created.
             */
            created?: number;
            /**
             * Format: int32
             * @description Existing bridge rows updated (privilege / syncedAt refreshed).
             */
            updated?: number;
            /**
             * Format: int32
             * @description Device users with no matching employee_code — skipped, not adopted.
             */
            skipped?: number;
            /** @description The device user_id values (as reported, before zero-strip) that matched no employee. Surfaced so HR can reconcile stray enrollments. */
            unmatchedUserIds?: string[];
        };
        /** @description Poller posts this on every cycle (success or fail). Drives the dashboard freshness indicator. Optional fields are reported on first contact / firmware change so the catalog stays current without an HR edit. */
        HeartbeatRequest: {
            /** @enum {string} */
            status: "UNKNOWN" | "ONLINE" | "OFFLINE" | "ERROR";
            /** Format: date-time */
            deviceTime?: string | null;
            firmwareVersion?: string | null;
            serialNumber?: string | null;
        };
        /** @description Write-only set of the ZK comm password. The current value is never returned in any GET — the device DTO instead exposes a commPasswordSet boolean. */
        SetDeviceCommPasswordRequest: {
            /**
             * Format: int32
             * @description Numeric ZK comm password. Use 0 to clear.
             * @example 0
             */
            commPassword: number;
        };
        /** @description Batch payroll snapshot request. */
        SnapshotsRequest: {
            /** @description Employees to snapshot (1..500). */
            employeeIds: number[];
            /**
             * Format: date
             * @description Period start (inclusive).
             */
            from: string;
            /**
             * Format: date
             * @description Period end (inclusive).
             */
            to: string;
        };
        /** @description Per-employee aggregated attendance snapshot for a payroll period. */
        AttendanceSnapshotDto: {
            /** @description Days marked ABSENT in the period. */
            lopDays?: number;
            /**
             * Format: int32
             * @description PRESENT + 0.5 × HALF_DAY (rounded up).
             */
            daysPresent?: number;
            /** @description Sum of OT minutes / 60. */
            otHours?: number;
            /**
             * Format: int32
             * @description daysInPeriod − holidayCount − sundayCount.
             */
            workingDays?: number;
            /** @description workingDays − absentDays (≥ 0). */
            paidDays?: number;
            /** @description absentCount + (halfDayCount / 2). */
            absentDays?: number;
            /**
             * Format: int32
             * @description HOLIDAY count.
             */
            holidaysInPeriod?: number;
            /**
             * Format: int32
             * @description WEEKEND count.
             */
            sundaysInPeriod?: number;
        };
        /** @description Operator override over a date range, applied per employee. Triggers same-tx recompute of every attendance_records row in the range. */
        CreateShiftOverrideRequest: {
            employeeIds: number[];
            /** Format: date */
            fromDate: string;
            /** Format: date */
            toDate: string;
            shiftStartTime: string;
            shiftEndTime: string;
            shiftBreakStartTime: string;
            shiftBreakEndTime: string;
            /** Format: int32 */
            shiftGraceMinutes: number;
            reason?: string | null;
        };
        ShiftOverrideDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            fromDate?: string;
            /** Format: date */
            toDate?: string;
            shiftStartTime?: string;
            shiftEndTime?: string;
            shiftBreakStartTime?: string;
            shiftBreakEndTime?: string;
            /**
             * Format: int32
             * @description Derived from break window.
             */
            shiftBreakMinutes?: number;
            /** Format: int32 */
            shiftGraceMinutes?: number;
            reason?: string | null;
        };
        /** @description Operator hard-delete of a punch. Reason is required and goes onto the audit trail; the row itself is removed and the day's attendance_records are recomputed. */
        DeletePunchRequest: {
            reason: string;
        };
        /** @description Bulk ingest from attendance-poller. Each entry is a raw punch — the service resolves the employee from employeeCode (the device's user_id field IS the employee_code) via attendance_employees, drops same-time duplicates silently, and drives attendance_records re-derivation transactionally. Entries whose employee_code has no matching snapshot row are returned in skipped[] with reason UNKNOWN_DEVICE_USER and parked in the unmatched-replay queue. */
        BiometricBatchRequest: {
            punches: components["schemas"]["BiometricPunch"][];
        };
        BiometricPunch: {
            /** @description Device-side user_id, equal to employees.employee_code. */
            employeeCode: string;
            /** Format: date-time */
            punchedAt: string;
            /**
             * Format: int64
             * @description FK into attendance_devices.id
             */
            deviceId: number;
        };
        /** @description Outcome of a /punches/ingest call. Punches whose employee_code has no matching attendance_employees snapshot row land in skipped[] with reason UNKNOWN_DEVICE_USER; the poller logs them and they are parked in the unmatched-replay queue. */
        IngestPunchResult: {
            ingested?: components["schemas"]["PunchDto"][];
            skipped?: components["schemas"]["SkippedPunch"][];
        };
        PunchDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date-time */
            punchedAt?: string;
            /** Format: date */
            workDate?: string;
            source?: string;
            /**
             * Format: int64
             * @description FK into attendance_devices.id
             */
            deviceId?: number | null;
            reason?: string | null;
        };
        SkippedPunch: {
            employeeCode?: string;
            /** Format: int64 */
            deviceId?: number;
            /** Format: date-time */
            punchedAt?: string;
            reason?: string;
        };
        /** @description ESS self-punch. `at` is optional — when omitted, the server uses now(). */
        PunchRequest: {
            /**
             * Format: date-time
             * @description Punch instant. Server stamps now() when null.
             */
            at?: string | null;
        };
        /** @description Lock state for a payroll-period (year + month) in attendance. */
        AttendancePeriodLock: {
            /**
             * Format: int32
             * @example 2026
             */
            periodYear?: number;
            /**
             * Format: int32
             * @example 4
             */
            periodMonth?: number;
            /**
             * Format: date-time
             * @example 2026-05-09T16:48:00
             */
            lockedAt?: string;
            /** @example 158 */
            lockedBy?: string;
        };
        /** @description End-of-shift / paper-form bulk insert of operator punches. Each row is a single (employee, instant) punch with the operator's free-text reason. The whole batch runs in one transaction — any single row failing rolls back the lot. Cap is 100 rows per call. */
        BulkOperatorPunchRequest: {
            /** @description 1..100 punch rows; the whole batch is one transaction. */
            punches: components["schemas"]["BulkOperatorPunchRequest.Row"][];
        };
        "BulkOperatorPunchRequest.Row": {
            /** Format: int64 */
            employeeId: number;
            /** Format: date-time */
            punchedAt: string;
            reason: string;
        };
        /** @description Per-row outcome of a bulk operator-punch insert. Either list may be empty. */
        BulkPunchResponse: {
            succeeded?: components["schemas"]["PunchDto"][];
            failed?: components["schemas"]["BulkPunchResponse.FailedRow"][];
        };
        /** @description An input row that failed to insert. {@code errorCode} is the stable {@code AttendanceErrorCode} name when the cause is a known business error. */
        "BulkPunchResponse.FailedRow": {
            /** Format: int64 */
            employeeId: number;
            /** Format: date-time */
            punchedAt: string;
            reason?: string;
            errorCode: string;
            message: string;
        };
        /** @description Operator-added punch with offline proof. `reason` is the paper-trail note ("missed checkout, paper form #12 / approved by supervisor X"). */
        OperatorPunchRequest: {
            /** Format: int64 */
            employeeId: number;
            /** Format: date-time */
            punchedAt: string;
            reason: string;
        };
        /** @description Create or extend an approval delegation. `requestTypes` defaults to all three types when null. `toDate` null = open-ended (until revoked). */
        CreateDelegationRequest: {
            /** Format: int64 */
            delegateId: number;
            requestTypes?: ("LEAVE" | "SHORT_LEAVE" | "OVERTIME" | "REGULARIZATION")[] | null;
            /** Format: date */
            fromDate: string;
            /** Format: date */
            toDate?: string | null;
            reason?: string | null;
        };
        DelegationDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            delegatorId?: number;
            /** Format: int64 */
            delegateId?: number;
            requestTypes?: string[] | null;
            /** Format: date */
            fromDate?: string;
            /** Format: date */
            toDate?: string | null;
            reason?: string | null;
            status?: string;
            /** Format: date-time */
            revokedAt?: string | null;
            /** Format: int64 */
            revokedBy?: number | null;
        };
        /** @description HR (with APPROVAL_OVERRIDE) creates a delegation on a manager's behalf when the manager is unreachable. `reason` is required for audit. */
        CreateDelegationOnBehalfRequest: {
            /** Format: int64 */
            delegatorId: number;
            /** Format: int64 */
            delegateId: number;
            requestTypes?: ("LEAVE" | "SHORT_LEAVE" | "OVERTIME" | "REGULARIZATION")[] | null;
            /** Format: date */
            fromDate: string;
            /** Format: date */
            toDate?: string | null;
            reason: string;
        };
        /** @description One entry of a bulk reorder payload — the new displayOrder for a catalog row. */
        ReorderEntryRequest: {
            /**
             * Format: int64
             * @description Catalog row id.
             * @example 42
             */
            id: number;
            /**
             * Format: int32
             * @description New display order. Lower sorts first.
             * @example 20
             */
            displayOrder: number;
        };
        /** @description HR-admin partial update. Any field left null is unchanged. `code` is immutable; to retire a code use DELETE (soft-deactivate) and create a new type. */
        UpdateLeaveTypeRequest: {
            name?: string | null;
            paid?: boolean | null;
            defaultPerYear?: number | null;
            /** @enum {string|null} */
            cycleAnchor?: "JOINING" | "FISCAL" | "NONE" | null;
            prorateOnEntry?: boolean | null;
            encashable?: boolean | null;
            carryForwardMax?: number | null;
            requiresApproval?: boolean | null;
            active?: boolean | null;
            showOnDashboard?: boolean | null;
        };
        /** @description Patch a holiday. Null fields stay unchanged. The date is immutable — delete and recreate if the date itself is wrong. */
        UpdateHolidayRequest: {
            name?: string | null;
            /** @enum {string|null} */
            type?: "NATIONAL" | "COMPANY" | "PROVINCIAL" | null;
            description?: string | null;
        };
        /** @description HR-admin partial update. Any field left null is unchanged. `code` is immutable; to retire a device flip `active=false` (DELETE). The ZK comm password is set via the dedicated POST /devices/{id}/comm-password endpoint, not this update path, so it cannot be exfiltrated through a leaked GET response. */
        UpdateAttendanceDeviceRequest: {
            name?: string | null;
            location?: string | null;
            deviceIp?: string | null;
            /** Format: int32 */
            commPort?: number | null;
            enrollment?: boolean | null;
            /**
             * @description Reclassify this device. Most are ATTENDANCE; flip to CANTEEN or ACCESS_LOG for non-attendance units.
             * @enum {string|null}
             */
            deviceType?: "ATTENDANCE" | "CANTEEN" | "ACCESS_LOG" | null;
            active?: boolean | null;
        };
        /** @description Patch — null fields stay unchanged. A change to from/to recomputes the union of old and new ranges. */
        UpdateShiftOverrideRequest: {
            shiftStartTime?: string | null;
            shiftEndTime?: string | null;
            shiftBreakStartTime?: string | null;
            shiftBreakEndTime?: string | null;
            /** Format: int32 */
            shiftGraceMinutes?: number | null;
            /** Format: date */
            fromDate?: string | null;
            /** Format: date */
            toDate?: string | null;
            reason?: string | null;
        };
        /** @description Operator amendment to an existing punch. Both fields are mandatory — the new instant and an audit-grade reason explaining the correction. Source / employee / device stay as-is. */
        AmendPunchRequest: {
            /** Format: date-time */
            punchedAt: string;
            /** @description Free-text justification ("corrected per paper form #43, dial-pad lag"). */
            reason: string;
        };
        /** @description Extend / shorten / annotate. Null = unchanged. */
        UpdateDelegationRequest: {
            /** Format: date */
            toDate?: string | null;
            reason?: string | null;
        };
        PageResponseShortLeaveRequestDto: {
            content?: components["schemas"]["ShortLeaveRequestDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description An employee's shift assignment over a time range. `effectiveTo` is null for the currently-open assignment. Returned newest-first by `effectiveFrom`; the first row whose range covers today is the active one. */
        EmployeeShiftDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int64 */
            shiftId?: number;
            /** @description Denormalized shift code at read time. */
            shiftCode?: string;
            /** @description Denormalized shift name at read time. */
            shiftName?: string;
            /** Format: date */
            effectiveFrom?: string;
            /** Format: date */
            effectiveTo?: string | null;
        };
        StreamingResponseBody: unknown;
        PageResponseRegularizationRequestDto: {
            content?: components["schemas"]["RegularizationRequestDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PageResponseOvertimeRequestDto: {
            content?: components["schemas"]["OvertimeRequestDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description OT request enriched with the attendance-record minutes for the same (employee, workDate). Minutes are zero when the OT is still PENDING/REJECTED or when no attendance record exists for the day yet. */
        OvertimeMineRow: {
            /** @description Underlying OT request — authorization shape, no minutes. */
            request?: components["schemas"]["OvertimeRequestDto"];
            /** Format: int32 */
            otNormalMinutes?: number;
            /** Format: int32 */
            otExtraMinutes?: number;
            /** Format: int32 */
            otSpecialMinutes?: number;
            /** Format: int32 */
            workedMinutes?: number;
        };
        LeaveDayDto: {
            /** Format: int64 */
            id?: number;
            /** Format: date */
            workDate?: string;
            status?: string;
            /** Format: int64 */
            decidedBy?: number | null;
            /** Format: date-time */
            decidedAt?: string | null;
            /** Format: int32 */
            decidedAtStage?: number | null;
            rejectionReason?: string | null;
        };
        PageResponseLeaveRequestDto: {
            content?: components["schemas"]["LeaveRequestDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        LeaveBalanceDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int64 */
            leaveTypeId?: number;
            /** @description Leave type code, denormalized for the FE. */
            leaveTypeCode?: string;
            /**
             * Format: int32
             * @description Calendar year the cycle starts in (grouping label).
             */
            year?: number;
            /**
             * Format: date
             * @description Inclusive first day of this entitlement cycle.
             */
            cycleStart?: string;
            /**
             * Format: date
             * @description Inclusive last day of this entitlement cycle.
             */
            cycleEnd?: string;
            allocated?: number;
            used?: number;
            pending?: number;
            carried?: number;
            available?: number;
        };
        /** @description One-shot bundle for the apply-leave drawer — all three reads the drawer needs on open. Avoids the cold-start jitter of three sequential calls. Holidays are bounded to the next 90 days from today (Asia/Karachi); past holidays are elsewhere on the calendar view. */
        LeaveApplyContext: {
            /** @description Active leave types from the catalog. */
            types?: components["schemas"]["LeaveTypeDto"][];
            /** @description Caller's current-year balances, denormalized with leave type code. */
            balances?: components["schemas"]["LeaveBalanceDto"][];
            /** @description Holidays in the next 90 days from today (Asia/Karachi). */
            holidaysInNext90Days?: components["schemas"]["HolidayDto"][];
        };
        /** @description An employee enrolled on a device — view of the attendance_device_users bridge row. The employee's identity on the device is the employee_code; look it up via the employee snapshot. */
        DeviceUserDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            deviceId?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: int32 */
            privilege?: number;
            hasFingerprint?: boolean;
            hasFace?: boolean;
            /**
             * Format: date-time
             * @description Timestamp of the last successful UPSERT_USER for this row. Null when the row has never been pushed.
             */
            syncedAt?: string | null;
        };
        PageResponseDeviceUserDto: {
            content?: components["schemas"]["DeviceUserDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PageResponseDeviceSyncTaskDto: {
            content?: components["schemas"]["DeviceSyncTaskDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        PageResponseDeviceSnapshotSummaryDto: {
            content?: components["schemas"]["DeviceSnapshotSummaryDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description Full device snapshot including deviceInfo / users / attendance JSON payloads. All three are populated only when status is COMPLETED. */
        DeviceSnapshotDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            deviceId?: number;
            /** Format: int64 */
            syncTaskId?: number | null;
            /** @enum {string} */
            status?: "PENDING" | "COMPLETED" | "FAILED";
            requestedBy?: string;
            /** Format: date-time */
            requestedAt?: string;
            /** Format: date-time */
            completedAt?: string | null;
            /** Format: int32 */
            userCount?: number | null;
            /** Format: int32 */
            recordCount?: number | null;
            error?: string | null;
            /** @description Raw device metadata as the poller captured it (firmware, serial, platform, clock, etc.). Shape varies by firmware. */
            deviceInfo?: null;
            /** @description Users on the device. Shape: {count, users: [...]}. */
            users?: null;
            /** @description Attendance log on the device at snapshot time. Shape: {count, records: [...]}. */
            attendance?: null;
        };
        /** @description One device-enrolled user reconciled against the employee mirror. The device user_id (leading zeros stripped) is matched to an employee_code; category tells HR whether to keep or un-enroll. */
        DeviceUserReconciliationDto: {
            /** @description Raw user_id as stored on the device (before zero-strip). Pass this back verbatim to the un-enroll endpoint for UNMATCHED rows. */
            deviceUserId?: string;
            /** @description employee_code matched (device user_id, zeros stripped); null when the device user_id is blank. */
            employeeCode?: string | null;
            /**
             * Format: int64
             * @description Matched employee id; null when UNMATCHED.
             */
            employeeId?: number | null;
            /** @description Employee full name when matched; null otherwise. */
            name?: string | null;
            /** @description Employment status from the mirror when matched (ACTIVE / TERMINATED). */
            employmentStatus?: string | null;
            /** @enum {string} */
            category?: "MATCHED_ACTIVE" | "MATCHED_INACTIVE" | "UNMATCHED";
            /** Format: int32 */
            privilege?: number;
        };
        PageResponseDeviceUserReconciliationDto: {
            content?: components["schemas"]["DeviceUserReconciliationDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description Counts of a completed snapshot's device users by reconciliation category. Lets the FE show the breakdown without fetching the full per-user list. */
        DeviceReconciliationSummaryDto: {
            /** Format: int64 */
            deviceId?: number;
            /** Format: int64 */
            snapshotId?: number;
            /** Format: int32 */
            total?: number;
            /**
             * Format: int32
             * @description Matched to an ACTIVE employee — keep.
             */
            matchedActive?: number;
            /**
             * Format: int32
             * @description Matched to a non-ACTIVE (e.g. TERMINATED) employee — un-enroll candidate.
             */
            matchedInactive?: number;
            /**
             * Format: int32
             * @description No matching employee_code — stray enrollment, un-enroll candidate.
             */
            unmatched?: number;
        };
        /** @description Active device the attendance-poller should pull from. Carries comm password — do not surface this DTO outside the attendance.poller surface. */
        PollerTargetDto: {
            /** Format: int64 */
            id: number;
            code: string;
            /** @description ZK protocol IP. */
            deviceIp: string;
            /** Format: int32 */
            commPort: number;
            /**
             * Format: int32
             * @description ZK comm password — sensitive, gate to attendance.poller.
             */
            commPassword: number;
            /**
             * Format: date-time
             * @description High-water-mark; the poller filters its ZK pull to records strictly after this. Null on a fresh device — pull everything.
             */
            lastPunchedAt?: string | null;
        };
        /** @description Per-day attendance projection. Shift fields are the snapshot — they do NOT change when the shift catalog or assignment changes. */
        AttendanceRecordDto: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            workDate?: string;
            /** Format: date-time */
            checkIn?: string | null;
            /** Format: date-time */
            checkOut?: string | null;
            /** Format: int64 */
            effectiveShift?: number | null;
            shiftStartTime?: string | null;
            shiftEndTime?: string | null;
            shiftBreakStartTime?: string | null;
            shiftBreakEndTime?: string | null;
            /**
             * Format: int32
             * @description Derived from break window.
             */
            shiftBreakMinutes?: number;
            /** Format: int32 */
            shiftGraceMinutes?: number | null;
            status?: string;
            /** Format: int32 */
            lateMinutes?: number;
            /** Format: int32 */
            earlyMinutes?: number;
            /** Format: int32 */
            workedMinutes?: number;
            /** Format: int32 */
            otNormalMinutes?: number;
            /** Format: int32 */
            otExtraMinutes?: number;
            /** Format: int32 */
            otSpecialMinutes?: number;
            source?: string | null;
            notes?: string | null;
            /** @description §7.payroll #23 — paid-leave flag. Populated when status == LEAVE from the leave type's paid column at approval time. Null on every other status. */
            paid?: boolean | null;
        };
        PageResponseAttendanceRecordDto: {
            content?: components["schemas"]["AttendanceRecordDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description Org-wide attendance records list, page-by-page. Each row carries the upstream record plus a compact `employee` summary (name, code, department, photo) so the FE can render the table without an extra fan-out per row. Pagination metadata mirrors the upstream PageResponse 1:1. */
        AttendanceRecordsPage: {
            content?: components["schemas"]["AttendanceRecordsPage.Row"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string | null;
        };
        "AttendanceRecordsPage.Row": {
            record?: components["schemas"]["AttendanceRecordDto"];
            employee?: components["schemas"]["EmployeeSummary"];
        };
        /** @description Compact employee row for resolving names against an id list. Used by the approvals inbox, payroll composites, attendance composites, and any surface that comes back keyed by employee id without names attached. */
        EmployeeSummary: {
            /**
             * Format: int64
             * @description Employee id.
             */
            id?: number;
            /** @description Display name. */
            fullName?: string;
            /** @description Human-readable employee code (e.g. FS-2026-0042). Also the RFID/punch-card number used by attendance devices. */
            employeeCode?: string;
            /**
             * Format: int64
             * @description Department id.
             */
            departmentId?: number | null;
            /** @description Department name, looked up from the catalog. */
            departmentName?: string | null;
            /**
             * Format: int64
             * @description Section id.
             */
            sectionId?: number | null;
            /** @description Section name, looked up from the catalog. */
            sectionName?: string | null;
            /**
             * Format: int64
             * @description Designation id.
             */
            designationId?: number | null;
            /** @description Designation title, looked up from the catalog. */
            designationName?: string | null;
            /** @description Signed URL for the employee's profile photo. */
            profilePictureUrl?: string | null;
        };
        PageResponsePunchDto: {
            content?: components["schemas"]["PunchDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description Org-wide punches list, page-by-page. Same contract as AttendanceRecordsPage — raw punches enriched with a compact `employee` summary. Primary table for the attendance-operator's daily reconciliation. */
        AttendancePunchesPage: {
            content?: components["schemas"]["AttendancePunchesPage.Row"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string | null;
        };
        "AttendancePunchesPage.Row": {
            punch?: components["schemas"]["PunchDto"];
            employee?: components["schemas"]["EmployeeSummary"];
        };
        /** @description Org-wide attendance roll-up for a single date. One SQL aggregate over attendance_records — feeds the HR dashboard's `OrgPulse` card and avoids the BFF pulling per-employee status rows just to bucket four numbers. */
        OrgPulseDto: {
            /** Format: date */
            asOf?: string;
            /** Format: int64 */
            present?: number;
            /** Format: int64 */
            late?: number;
            /** Format: int64 */
            absent?: number;
            /** Format: int64 */
            onLeave?: number;
            /** Format: int64 */
            halfDay?: number;
            /** Format: int64 */
            holiday?: number;
            /** Format: int64 */
            weekend?: number;
            /** Format: int64 */
            total?: number;
        };
        Pageable: {
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            sort?: string[];
        };
        /** @description An active employee whose shift-start + grace has already elapsed today without a check-in punch. Derived live at read time — nothing is persisted, and the row drops off the list the moment a punch arrives. Distinct from the settled ABSENT status the nightly close writes. */
        NotCheckedInDto: {
            /**
             * Format: int64
             * @description Employee id (employee-service row id).
             */
            employeeId?: number;
            /** @description Employee code. */
            employeeCode?: string;
            /** @description Full name (from the attendance-side employee mirror). */
            fullName?: string;
            /**
             * Format: int64
             * @description Resolved shift id for today; null on override-only days.
             */
            shiftId?: number;
            /** @description Shift start time for today. */
            shiftStartTime?: string;
            /**
             * Format: int32
             * @description Grace minutes applied to the shift.
             */
            graceMinutes?: number;
            /** @description Cutoff time after which a missing check-in counts (shiftStart + grace). */
            cutoff?: string;
            /**
             * Format: int64
             * @description Minutes elapsed past the cutoff as of the query time.
             */
            minutesOverdue?: number;
        };
        PageResponseNotCheckedInDto: {
            content?: components["schemas"]["NotCheckedInDto"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        /** @description Per-day org-wide status counts. Used by the HR dashboard's 30-day trend and any other surface that wants attendance volume over time without joining across 312 × 30 rows on the FE. */
        OrgAttendanceDayDto: {
            /** Format: date */
            date?: string;
            /** Format: int64 */
            present?: number;
            /** Format: int64 */
            late?: number;
            /** Format: int64 */
            absent?: number;
            /** Format: int64 */
            onLeave?: number;
            /** Format: int64 */
            halfDay?: number;
            /** Format: int64 */
            total?: number;
        };
        /** @description One-shot daily view for the attendance-operator persona. Headlines + the two lists the operator most often acts on (missing checkouts, late arrivals) plus volume stats (today's punches / overrides / pending regularizations). Underlying calls all fan out in parallel; permission gating is on the upstream calls. Default date is today (server-local Asia/Karachi). */
        OperatorDashboard: {
            /**
             * Format: date
             * @description The date this dashboard reflects.
             */
            date?: string;
            /** @description Status counts (PRESENT/LATE/ABSENT/...) for the date. */
            pulse?: components["schemas"]["OrgPulseDto"];
            /**
             * Format: int64
             * @description Total punches recorded for this date — across every source.
             */
            totalPunchesToday?: number;
            /**
             * Format: int32
             * @description Number of shift overrides whose range covers this date.
             */
            activeOverridesToday?: number;
            /**
             * Format: int32
             * @description Open regularization requests pending approval (org-wide count).
             */
            pendingRegularizations?: number;
            /** @description Employees who punched in but haven't punched out yet — primary operator action queue. Capped to keep the dashboard payload bounded. */
            missingCheckouts?: components["schemas"]["OperatorDashboard.AttendanceRow"][];
            /** @description Employees with late_minutes > 0 today. */
            lateArrivals?: components["schemas"]["OperatorDashboard.AttendanceRow"][];
        };
        /** @description Daily attendance record + the compact employee summary used everywhere. */
        "OperatorDashboard.AttendanceRow": {
            record?: components["schemas"]["AttendanceRecordDto"];
            employee?: components["schemas"]["EmployeeSummary"];
        };
        /** @description One-shot Today screen. Replaces 4–5 round-trips: today's attendance record, today's punches, any active approved leave that overlaps today, today's holiday, next upcoming holiday/leave, and a 3-bucket balance summary. */
        AttendanceToday: {
            /** @description Today's projection. Null when the day hasn't produced a record yet (no punches, nightly close not run). */
            today?: components["schemas"]["AttendanceToday.TodayRecord"];
            /** @description Today's punches in chronological order. */
            punches?: components["schemas"]["PunchDto"][];
            /** @description Approved leave covering today; null when no overlap. */
            activeLeave?: components["schemas"]["LeaveRequestDto"];
            /** @description Today's holiday; null on working days. */
            todayHoliday?: components["schemas"]["HolidayDto"];
            upcoming?: components["schemas"]["AttendanceToday.Upcoming"];
            balanceSummary?: components["schemas"]["AttendanceToday.BalanceSummary"];
        };
        /** @description The three buckets the Today header shows. Codes match the leave-type catalog — anything else is rolled into the corresponding bucket by name match (ANNUAL/SICK/CASUAL). */
        "AttendanceToday.BalanceSummary": {
            annualAvailable?: number;
            sickAvailable?: number;
            casualAvailable?: number;
        };
        "AttendanceToday.NextLeave": {
            /** Format: date */
            fromDate?: string;
            /** Format: date */
            toDate?: string;
            leaveTypeCode?: string;
        };
        /** @description Subset of AttendanceRecordDto plus the shift snapshot — the FE needs both to render schedule + worked time on the same card. */
        "AttendanceToday.TodayRecord": {
            /** Format: date */
            workDate?: string;
            status?: string;
            /** Format: date-time */
            checkIn?: string | null;
            /** Format: date-time */
            checkOut?: string | null;
            /** Format: int32 */
            lateMinutes?: number;
            /** Format: int32 */
            workedMinutes?: number;
            /** Format: int32 */
            otNormalMinutes?: number;
            /** Format: int32 */
            otExtraMinutes?: number;
            /** Format: int32 */
            otSpecialMinutes?: number;
            shiftStartTime?: string | null;
            shiftEndTime?: string | null;
            /** Format: int32 */
            shiftBreakMinutes?: number | null;
        };
        "AttendanceToday.Upcoming": {
            nextHoliday?: components["schemas"]["HolidayDto"];
            nextApprovedLeave?: components["schemas"]["AttendanceToday.NextLeave"];
        };
        /** @description Calendar month view. Each day's `kind` is the FE's render hint — pick the single overlay that wins for that cell. Precedence (high → low): HOLIDAY > LEAVE > the record's status (PRESENT, ABSENT, HALF_DAY, WEEKEND). */
        AttendanceMonth: {
            /** @description Per-day rows for the month, in chronological order. */
            days?: components["schemas"]["AttendanceMonth.Day"][];
            summary?: components["schemas"]["AttendanceMonth.Summary"];
        };
        "AttendanceMonth.Day": {
            /** Format: date */
            workDate?: string;
            /** @description Render hint — one of PRESENT, ABSENT, LEAVE, HOLIDAY, WEEKEND, HALF_DAY. */
            kind?: string;
            record?: components["schemas"]["AttendanceRecordDto"];
            holiday?: components["schemas"]["HolidayDto"];
            leave?: components["schemas"]["AttendanceMonth.DayLeave"];
        };
        /** @description Compact leave overlay — full request lives elsewhere. */
        "AttendanceMonth.DayLeave": {
            /** Format: int64 */
            requestId?: number;
            leaveTypeCode?: string;
            halfDay?: boolean;
            halfDayPart?: string | null;
        };
        "AttendanceMonth.Summary": {
            /** Format: int32 */
            present?: number;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            leave?: number;
            /** Format: int32 */
            holiday?: number;
            /** Format: int32 */
            halfDay?: number;
            /** Format: int32 */
            weekend?: number;
            /** Format: int32 */
            totalWorkedMinutes?: number;
            /** Format: int32 */
            totalOtMinutes?: number;
            /** Format: int32 */
            totalLateMinutes?: number;
        };
        /** @description Per-day status + late-variance for an employee in a given month. Server-side rollup so the FE doesn't have to walk per-day attendance rows just to render the heatmap (D-S5 in BFF_WISHLIST.md). */
        EmployeeHeatmapDto: {
            /** Format: int64 */
            employeeId?: number;
            /** @example 2026-04 */
            month?: string;
            days?: components["schemas"]["EmployeeHeatmapDto.Day"][];
        };
        "EmployeeHeatmapDto.Day": {
            /** Format: date */
            date?: string;
            /** @description PRESENT | LATE | ABSENT | LEAVE | HALF_DAY | HOLIDAY | WEEKEND. */
            status?: string;
            /**
             * Format: int32
             * @description Late minutes the day variance bar shows; 0 for non-PRESENT days.
             */
            varianceMin?: number;
        };
        /** @description Per-employee 'today' projection. Always 200 — when no punches and no nightly close have produced a record yet, state is NOT_PUNCHED and record is null. */
        EmployeeTodayDto: {
            /**
             * @description PRESENT when a record exists; NOT_PUNCHED when no record exists yet.
             * @enum {string}
             */
            state?: "PRESENT" | "NOT_PUNCHED";
            /** @description Today's attendance record, or null when state == NOT_PUNCHED. */
            record?: components["schemas"]["AttendanceRecordDto"];
        };
        /** @description Per-employee canteen usage over a date range, derived from CANTEEN-typed device events. `days` is the count of distinct calendar dates; `weeks` is the count of distinct 7-day chunks measured from the 1st of the month (days 1–7, 8–14, 15–21, 22–28, 29–end). */
        CanteenUsageDto: {
            /**
             * Format: int32
             * @description Distinct dates with at least one canteen punch.
             * @example 12
             */
            days?: number;
            /**
             * Format: int32
             * @description Distinct 7-day chunks (from day 1) with at least one canteen punch.
             * @example 3
             */
            weeks?: number;
        };
        /** @description Combined approvals inbox. The three downstream pending-approvals lists fan out in parallel, then each row is enriched with an `employee` summary in one additional batch resolve. Counts are pre-computed for the badge UI. */
        ApprovalsInbox: {
            leaves?: components["schemas"]["ApprovalsInbox.LeaveRow"][];
            overtime?: components["schemas"]["ApprovalsInbox.OvertimeRow"][];
            regularization?: components["schemas"]["ApprovalsInbox.RegularizationRow"][];
            counts?: components["schemas"]["ApprovalsInbox.Counts"];
        };
        /** @description Per-bucket counts plus the total — drives the badge in the nav. */
        "ApprovalsInbox.Counts": {
            /** Format: int32 */
            leaves?: number;
            /** Format: int32 */
            overtime?: number;
            /** Format: int32 */
            regularization?: number;
            /** Format: int32 */
            total?: number;
        };
        "ApprovalsInbox.LeaveRow": {
            request?: components["schemas"]["LeaveRequestDto"];
            employee?: components["schemas"]["EmployeeSummary"];
        };
        "ApprovalsInbox.OvertimeRow": {
            request?: components["schemas"]["OvertimeRequestDto"];
            employee?: components["schemas"]["EmployeeSummary"];
        };
        "ApprovalsInbox.RegularizationRow": {
            request?: components["schemas"]["RegularizationRequestDto"];
            employee?: components["schemas"]["EmployeeSummary"];
        };
        /** @description An approval decision (APPROVED or REJECTED) the caller closed at one stage of one request. Unified across leave / OT / regularization. */
        ApprovalDecisionRow: {
            /**
             * @description Type discriminator.
             * @enum {string}
             */
            type?: "LEAVE" | "OVERTIME" | "REGULARIZATION";
            /** Format: int64 */
            requestId?: number;
            /** @description Human-readable request code (LR-…, OT-…, REG-…). */
            requestCode?: string;
            /** Format: int64 */
            employeeId?: number;
            employeeCode?: string | null;
            fullName?: string | null;
            /**
             * Format: date-time
             * @description When the caller closed the stage.
             */
            decidedAt?: string;
            /**
             * Format: date-time
             * @description When the underlying request was created — used to compute decision latency.
             */
            requestCreatedAt?: string;
            /**
             * @description Stage outcome.
             * @enum {string}
             */
            decision?: "APPROVED" | "REJECTED";
            /**
             * Format: int32
             * @description 1-based stage index.
             */
            stageNo?: number;
            /** @description Stage role (MANAGER, HR, …). */
            stageRole?: string;
            /** @description Note recorded with the decision. */
            note?: string | null;
            /**
             * Format: date
             * @description LEAVE: fromDate. OVERTIME / REGULARIZATION: workDate.
             */
            startDate?: string | null;
            /**
             * Format: date
             * @description LEAVE: toDate. Null for OVERTIME / REGULARIZATION.
             */
            endDate?: string | null;
            /** @description LEAVE: leave-type code (e.g. ANNUAL). */
            leaveTypeCode?: string | null;
            /** @description LEAVE: total working days requested. */
            days?: number | null;
            /** @description LEAVE / REGULARIZATION: free-text reason. */
            reason?: string | null;
        };
        PageResponseApprovalDecisionRow: {
            content?: components["schemas"]["ApprovalDecisionRow"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
            sort?: string;
        };
        UtilizationByType: {
            /** Format: int64 */
            leaveTypeId?: number;
            code?: string;
            name?: string;
            allocated?: number;
            used?: number;
            pending?: number;
            usedPct?: number;
        };
        UtilizationDto: {
            /** Format: int32 */
            year?: number;
            totalAllocated?: number;
            totalUsed?: number;
            overallPct?: number;
            byLeaveType?: components["schemas"]["UtilizationByType"][];
        };
        SeasonalityByType: {
            /** Format: int64 */
            leaveTypeId?: number;
            code?: string;
            days?: number;
        };
        SeasonalityDto: {
            /** Format: int32 */
            months?: number;
            series?: components["schemas"]["SeasonalityMonth"][];
        };
        SeasonalityMonth: {
            month?: string;
            totalDays?: number;
            byLeaveType?: components["schemas"]["SeasonalityByType"][];
        };
        ScorecardDto: {
            /** Format: int64 */
            employeeId?: number;
            /** Format: date */
            from?: string;
            /** Format: date */
            to?: string;
            /** Format: int32 */
            dayWindow?: number;
            /** Format: int64 */
            presentDays?: number;
            /** Format: int64 */
            onTimeDays?: number;
            /** Format: int64 */
            lateDays?: number;
            /** Format: int64 */
            absentDays?: number;
            /** Format: int64 */
            leaveDays?: number;
            onTimePct?: number;
            otHours?: number;
        };
        OtHoursTrendDto: {
            /** Format: int32 */
            months?: number;
            series?: components["schemas"]["OtMonth"][];
        };
        OtMonth: {
            month?: string;
            /** Format: int64 */
            otMinutes?: number;
            otHours?: number;
        };
        MonthlyEntry: {
            month?: string;
            /** Format: int64 */
            denominator?: number;
            /** Format: int64 */
            numerator?: number;
            pct?: number;
        };
        OnTimeTrendDto: {
            /** Format: int32 */
            months?: number;
            series?: components["schemas"]["MonthlyEntry"][];
        };
        AbsenteeismTrendDto: {
            /** Format: int32 */
            months?: number;
            series?: components["schemas"]["MonthlyEntry"][];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    upload: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UploadTemplateRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeTemplateDto"];
                };
            };
            /** @description DEVICE_NOT_FOUND on `capturedDeviceId`. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeTemplateDto"];
                };
            };
            /** @description TEMPLATE_KIND_INVALID — fingerId out of range, missing for FINGERPRINT, or set for FACE; or bytes are not valid base64. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeTemplateDto"];
                };
            };
        };
    };
    apply: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateShortLeaveRequest"];
            };
        };
        responses: {
            /** @description Created and routed to first stage. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortLeaveRequestDto"];
                };
            };
            /** @description SHORT_LEAVE_OVERLAPS_EXISTING — an active short leave overlaps the requested window. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortLeaveRequestDto"];
                };
            };
            /** @description SHORT_LEAVE_RANGE_INVALID — endTime is not after startTime. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortLeaveRequestDto"];
                };
            };
        };
    };
    reject: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RejectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortLeaveRequestDto"];
                };
            };
        };
    };
    cancel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Caller is not the submitter. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortLeaveRequestDto"];
                };
            };
            /** @description SHORT_LEAVE_NOT_CANCELLABLE — not in a cancellable state. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortLeaveRequestDto"];
                };
            };
        };
    };
    approve: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ApproveRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortLeaveRequestDto"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShiftDto"][];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateShiftRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShiftDto"];
                };
            };
        };
    };
    assignments: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeShiftDto"][];
                };
            };
        };
    };
    assign: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AssignShiftRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    submit: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RegularizationRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RegularizationRequestDto"];
                };
            };
            /** @description ON_BEHALF_NOT_AUTHORIZED — `onBehalfOf` set but caller is neither the target's manager nor an override holder. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RegularizationRequestDto"];
                };
            };
        };
    };
    reject_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RejectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RegularizationRequestDto"];
                };
            };
        };
    };
    cancel_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RegularizationRequestDto"];
                };
            };
        };
    };
    approve_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ApproveRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RegularizationRequestDto"];
                };
            };
        };
    };
    apply_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateOvertimeRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"];
                };
            };
            /** @description ON_BEHALF_NOT_AUTHORIZED — `onBehalfOf` set but caller is neither the target's manager nor an override holder. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"];
                };
            };
        };
    };
    reject_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RejectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"];
                };
            };
        };
    };
    cancel_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Cancelled. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"];
                };
            };
            /** @description Caller is not the submitter. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"];
                };
            };
            /** @description Request not in a cancellable state. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"];
                };
            };
        };
    };
    approve_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ApproveRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"];
                };
            };
        };
    };
    bulk: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BulkOvertimeRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BulkOvertimeResponse"];
                };
            };
        };
    };
    rejectBatch: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                batchId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RejectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"][];
                };
            };
        };
    };
    approveBatch: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                batchId: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ApproveRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeRequestDto"][];
                };
            };
        };
    };
    apply_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateLeaveRequest"];
            };
        };
        responses: {
            /** @description Leave created and routed to first pending stage. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveRequestDto"];
                };
            };
            /** @description ON_BEHALF_NOT_AUTHORIZED — `onBehalfOf` set but caller is neither the target's manager nor an override holder. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveRequestDto"];
                };
            };
        };
    };
    reject_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RejectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveRequestDto"];
                };
            };
        };
    };
    decideDays: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PerDayDecideRequest"];
            };
        };
        responses: {
            /** @description STAGE_PER_DAY_NOT_ALLOWED — caller's stage isn't MANAGER. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveRequestDto"];
                };
            };
        };
    };
    cancel_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveRequestDto"];
                };
            };
        };
    };
    approve_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ApproveRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveRequestDto"];
                };
            };
        };
    };
    listTypes: {
        parameters: {
            query?: {
                includeInactive?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveTypeDto"][];
                };
            };
        };
    };
    createType: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateLeaveTypeRequest"];
            };
        };
        responses: {
            /** @description Created. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveTypeDto"];
                };
            };
            /** @description LEAVE_TYPE_CODE_TAKEN — a type with this code already exists. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveTypeDto"];
                };
            };
        };
    };
    preview: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateLeaveRequest"];
            };
        };
        responses: {
            /** @description Preview computed. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeavePreviewDto"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                year?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HolidayDto"][];
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateHolidayRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HolidayDto"];
                };
            };
        };
    };
    list_2: {
        parameters: {
            query?: {
                includeInactive?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceDeviceDto"][];
                };
            };
        };
    };
    create_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateAttendanceDeviceRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceDeviceDto"];
                };
            };
            /** @description DEVICE_CODE_TAKEN — a device with this code already exists. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceDeviceDto"];
                };
            };
        };
    };
    pushTemplates: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                employeeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSyncTaskDto"][];
                };
            };
            /** @description DEVICE_NOT_FOUND, DEVICE_USER_NOT_FOUND (employee not on this device), or EMPLOYEE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSyncTaskDto"][];
                };
            };
            /** @description DEVICE_INACTIVE (target not active) or TEMPLATES_NOT_FOUND (employee has no captured fingerprints yet — capture first). */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSyncTaskDto"][];
                };
            };
        };
    };
    captureTemplate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                employeeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSyncTaskDto"];
                };
            };
            /** @description DEVICE_NOT_FOUND, DEVICE_USER_NOT_FOUND (employee not on this device), or EMPLOYEE_NOT_FOUND (no attendance snapshot for the employee yet). */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSyncTaskDto"];
                };
            };
        };
    };
    unenroll: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UnenrollDeviceUsersRequest"];
            };
        };
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UnenrollResultDto"];
                };
            };
            /** @description EMPLOYEES_REQUIRED — both lists empty. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UnenrollResultDto"];
                };
            };
        };
    };
    retrySyncTask: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                taskId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description SYNC_TASK_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSyncTaskDto"];
                };
            };
            /** @description SYNC_TASK_NOT_FAILED — task is not in FAILED status. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSyncTaskDto"];
                };
            };
        };
    };
    fail: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                taskId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FailSyncTaskRequest"];
            };
        };
        responses: {
            /** @description Failure recorded. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description SYNC_TASK_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Task is not IN_PROGRESS. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    complete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                taskId: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["CompleteSyncTaskRequest"];
            };
        };
        responses: {
            /** @description Marked done. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description SYNC_TASK_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Task is not IN_PROGRESS (already completed / failed). */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    listSnapshots: {
        parameters: {
            query?: {
                /** @description Filter by snapshot status. Omit for all. */
                status?: ("PENDING" | "COMPLETED" | "FAILED")[];
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseDeviceSnapshotSummaryDto"];
                };
            };
        };
    };
    requestSnapshot: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSnapshotSummaryDto"];
                };
            };
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSnapshotSummaryDto"];
                };
            };
        };
    };
    exportReconciliation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                snapshotId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReconciliationExportDto"];
                };
            };
            /** @description DEVICE_SNAPSHOT_NOT_COMPLETED. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReconciliationExportDto"];
                };
            };
        };
    };
    finalizeSnapshot: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                snapshotId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FinalizeDeviceSnapshotRequest"];
            };
        };
        responses: {
            /** @description DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSnapshotSummaryDto"];
                };
            };
            /** @description DEVICE_SNAPSHOT_NOT_PENDING — already completed / failed. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSnapshotSummaryDto"];
                };
            };
        };
    };
    failSnapshot: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                snapshotId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FailDeviceSnapshotRequest"];
            };
        };
        responses: {
            /** @description DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSnapshotSummaryDto"];
                };
            };
            /** @description DEVICE_SNAPSHOT_NOT_PENDING. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSnapshotSummaryDto"];
                };
            };
        };
    };
    adoptSnapshotUsers: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                snapshotId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceUserAdoptionDto"];
                };
            };
            /** @description DEVICE_SNAPSHOT_NOT_COMPLETED — snapshot has no captured users yet. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceUserAdoptionDto"];
                };
            };
        };
    };
    resetHwm: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceDeviceDto"];
                };
            };
        };
    };
    heartbeat: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["HeartbeatRequest"];
            };
        };
        responses: {
            /** @description Heartbeat recorded. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    setCommPassword: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SetDeviceCommPasswordRequest"];
            };
        };
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceDeviceDto"];
                };
            };
        };
    };
    snapshots: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SnapshotsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        [key: string]: components["schemas"]["AttendanceSnapshotDto"];
                    };
                };
            };
        };
    };
    list_3: {
        parameters: {
            query?: {
                date?: string;
                employeeId?: number;
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShiftOverrideDto"][];
                };
            };
        };
    };
    create_3: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateShiftOverrideRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShiftOverrideDto"][];
                };
            };
        };
    };
    deletePunch: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeletePunchRequest"];
            };
        };
        responses: {
            /** @description Punch deleted. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description PUNCH_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    ingest: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BiometricBatchRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IngestPunchResult"];
                };
            };
        };
    };
    punch: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PunchRequest"];
            };
        };
        responses: {
            /** @description Punch recorded. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"];
                };
            };
            /** @description PUNCH_LIMIT — third WEB punch on the same day. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"];
                };
            };
        };
    };
    unlock: {
        parameters: {
            query: {
                year: number;
                month: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    lock: {
        parameters: {
            query: {
                year: number;
                month: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendancePeriodLock"];
                };
            };
        };
    };
    operatorPunchesBulk: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BulkOperatorPunchRequest"];
            };
        };
        responses: {
            /** @description All punches recorded. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BulkPunchResponse"];
                };
            };
            /** @description PUNCH_DUPLICATE on a same-instant collision; whole batch rolled back. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BulkPunchResponse"];
                };
            };
            /** @description PUNCH_FUTURE on any row in the batch; whole batch rolled back. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BulkPunchResponse"];
                };
            };
        };
    };
    operatorPunch: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OperatorPunchRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"];
                };
            };
        };
    };
    create_4: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateDelegationRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DelegationDto"];
                };
            };
        };
    };
    createOnBehalf: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateDelegationOnBehalfRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DelegationDto"];
                };
            };
        };
    };
    reorder: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReorderEntryRequest"][];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShiftDto"][];
                };
            };
        };
    };
    deactivateType: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description LEAVE_TYPE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveTypeDto"];
                };
            };
        };
    };
    updateType: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateLeaveTypeRequest"];
            };
        };
        responses: {
            /** @description LEAVE_TYPE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveTypeDto"];
                };
            };
        };
    };
    delete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateHolidayRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HolidayDto"];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceDeviceDto"];
                };
            };
        };
    };
    deactivate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceDeviceDto"];
                };
            };
        };
    };
    update_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateAttendanceDeviceRequest"];
            };
        };
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceDeviceDto"];
                };
            };
        };
    };
    delete_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    update_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateShiftOverrideRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShiftOverrideDto"];
                };
            };
        };
    };
    amendPunch: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AmendPunchRequest"];
            };
        };
        responses: {
            /** @description Punch amended. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"];
                };
            };
            /** @description PUNCH_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"];
                };
            };
            /** @description PUNCH_DUPLICATE — another punch already exists for the new instant. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"];
                };
            };
            /** @description PUNCH_FUTURE — new instant is in the future. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"];
                };
            };
        };
    };
    revoke: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DelegationDto"];
                };
            };
        };
    };
    update_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateDelegationRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DelegationDto"];
                };
            };
        };
    };
    get_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                templateId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description TEMPLATE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeTemplateDto"];
                };
            };
        };
    };
    get_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ShortLeaveRequestDto"];
                };
            };
        };
    };
    pending: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseShortLeaveRequestDto"];
                };
            };
        };
    };
    mine: {
        parameters: {
            query?: {
                status?: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseShortLeaveRequestDto"];
                };
            };
        };
    };
    leaveTaken: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    carryForward: {
        parameters: {
            query: {
                year: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    leaveBalance: {
        parameters: {
            query: {
                year: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    leaveApplicationLog: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    shiftRoster: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    regularizationLog: {
        parameters: {
            query: {
                from: string;
                to: string;
                status?: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    otRegister: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    monthly: {
        parameters: {
            query: {
                year: number;
                month: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    missingPunch: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    lateComers: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    daily: {
        parameters: {
            query: {
                /** @description Date (yyyy-mm-dd). */
                date: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    absenteeLog: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": components["schemas"]["StreamingResponseBody"];
                };
            };
        };
    };
    pending_1: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseRegularizationRequestDto"];
                };
            };
        };
    };
    pending_2: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseOvertimeRequestDto"];
                };
            };
        };
    };
    mine_1: {
        parameters: {
            query?: {
                status?: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseOvertimeRequestDto"];
                };
            };
        };
    };
    overtimeMine: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OvertimeMineRow"][];
                };
            };
        };
    };
    get_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveRequestDto"];
                };
            };
        };
    };
    days: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveDayDto"][];
                };
            };
        };
    };
    pending_3: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseLeaveRequestDto"];
                };
            };
        };
    };
    myLeaves: {
        parameters: {
            query?: {
                status?: "PENDING" | "APPROVED" | "REJECTED" | "PARTIALLY_APPROVED" | "CANCELLED";
                leaveTypeId?: number;
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseLeaveRequestDto"];
                };
            };
        };
    };
    myBalances: {
        parameters: {
            query?: {
                year?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveBalanceDto"][];
                };
            };
        };
    };
    leaveApplyContext: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LeaveApplyContext"];
                };
            };
        };
    };
    listDeviceUsers: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseDeviceUserDto"];
                };
            };
        };
    };
    listSyncTasks: {
        parameters: {
            query?: {
                /** @description Filter tasks by status. Omit for all. */
                status?: ("PENDING" | "IN_PROGRESS" | "DONE" | "FAILED")[];
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseDeviceSyncTaskDto"];
                };
            };
        };
    };
    claimNext: {
        parameters: {
            query?: {
                limit?: number;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSyncTaskDto"][];
                };
            };
        };
    };
    getSnapshot: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                snapshotId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceSnapshotDto"];
                };
            };
        };
    };
    reconciliation: {
        parameters: {
            query?: {
                /** @description Filter by reconciliation category. Omit for all. */
                category?: "MATCHED_ACTIVE" | "MATCHED_INACTIVE" | "UNMATCHED";
            };
            header?: never;
            path: {
                id: number;
                snapshotId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseDeviceUserReconciliationDto"];
                };
            };
            /** @description DEVICE_SNAPSHOT_NOT_COMPLETED. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseDeviceUserReconciliationDto"];
                };
            };
        };
    };
    reconciliationSummary: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                snapshotId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description DEVICE_NOT_FOUND or DEVICE_SNAPSHOT_NOT_FOUND. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceReconciliationSummaryDto"];
                };
            };
            /** @description DEVICE_SNAPSHOT_NOT_COMPLETED. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeviceReconciliationSummaryDto"];
                };
            };
        };
    };
    pollerTargets: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PollerTargetDto"][];
                };
            };
        };
    };
    records: {
        parameters: {
            query?: {
                /** @description Single-day shortcut. Wins over from/to. */
                date?: string;
                /** @description Inclusive lower bound. Defaults to today when neither date nor from/to supplied. */
                from?: string;
                /** @description Inclusive upper bound. Defaults to today when neither date nor from/to supplied. */
                to?: string;
                /** @description Filter by employee id. Wins over employeeCode. */
                employeeId?: number;
                /** @description Filter by employee code (e.g. FS-2026-0042). */
                employeeCode?: string;
                /** @description Filter by department id (resolved via employee-service). */
                departmentId?: number;
                /** @description Filter by section id (resolved via employee-service). */
                sectionId?: number;
                /** @description Filter by attendance status. */
                status?: "PRESENT" | "ABSENT" | "LEAVE" | "HOLIDAY" | "WEEKEND" | "HALF_DAY";
                /** @description When true, return only rows with lateMinutes > 0. */
                lateOnly?: boolean;
                /** @description When true, return only rows with a check-in but no check-out (operator dashboard: "who forgot to punch out"). */
                missingCheckout?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Page of attendance records. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseAttendanceRecordDto"];
                };
            };
        };
    };
    recordsWithEmployee: {
        parameters: {
            query?: {
                date?: string;
                from?: string;
                to?: string;
                employeeId?: number;
                employeeCode?: string;
                departmentId?: number;
                sectionId?: number;
                status?: "PRESENT" | "ABSENT" | "LEAVE" | "HOLIDAY" | "WEEKEND" | "HALF_DAY";
                lateOnly?: boolean;
                missingCheckout?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceRecordsPage"];
                };
            };
        };
    };
    punchesList: {
        parameters: {
            query?: {
                /** @description Single-day shortcut. Wins over from/to. */
                date?: string;
                /** @description Inclusive lower bound. Defaults to today when neither date nor from/to supplied. */
                from?: string;
                /** @description Inclusive upper bound. Defaults to today when neither date nor from/to supplied. */
                to?: string;
                /** @description Filter by employee id. Wins over employeeCode. */
                employeeId?: number;
                /** @description Filter by employee code. */
                employeeCode?: string;
                /** @description Filter by department id (resolved via employee-service). */
                departmentId?: number;
                /** @description Filter by section id (resolved via employee-service). */
                sectionId?: number;
                /** @description Filter by punch source. */
                source?: "BIOMETRIC" | "WEB" | "OPERATOR" | "REGULARIZED" | "SYSTEM";
                /** @description Filter by attendance device id. */
                deviceId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Page of punches. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponsePunchDto"];
                };
            };
        };
    };
    punchesWithEmployee: {
        parameters: {
            query?: {
                date?: string;
                from?: string;
                to?: string;
                employeeId?: number;
                employeeCode?: string;
                departmentId?: number;
                sectionId?: number;
                source?: "BIOMETRIC" | "WEB" | "OPERATOR" | "REGULARIZED" | "SYSTEM";
                deviceId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendancePunchesPage"];
                };
            };
        };
    };
    list_4: {
        parameters: {
            query: {
                year: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendancePeriodLock"][];
                };
            };
        };
    };
    pulse: {
        parameters: {
            query?: {
                /** @description Date to roll up (server-local). Defaults to today. */
                asOf?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Status counts for the supplied date. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OrgPulseDto"];
                };
            };
        };
    };
    notCheckedIn: {
        parameters: {
            query: {
                pageable: components["schemas"]["Pageable"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Not-checked-in roster as of now. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseNotCheckedInDto"];
                };
            };
        };
    };
    dailyTrend: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OrgAttendanceDayDto"][];
                };
            };
        };
    };
    operatorDashboard: {
        parameters: {
            query?: {
                /** @description Date to roll up. Defaults to today (Asia/Karachi). */
                date?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OperatorDashboard"];
                };
            };
        };
    };
    me: {
        parameters: {
            query?: {
                /** @description YYYY-MM, defaults to current month. */
                month?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceRecordDto"][];
                };
            };
        };
    };
    today: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceToday"];
                };
            };
        };
    };
    myPunches: {
        parameters: {
            query: {
                date: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"][];
                };
            };
        };
    };
    month: {
        parameters: {
            query?: {
                /** @description YYYY-MM, defaults to current month (Asia/Karachi). */
                month?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceMonth"];
                };
            };
        };
    };
    myHeatmap: {
        parameters: {
            query?: {
                /** @description YYYY-MM, defaults to current month. */
                month?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeHeatmapDto"];
                };
            };
        };
    };
    employeesOnDate: {
        parameters: {
            query: {
                /** @description Employee ids (comma-separated or repeated). */
                ids: number[];
                /** @description Defaults to today. */
                date?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceRecordDto"][];
                };
            };
        };
    };
    forEmployee: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AttendanceRecordDto"][];
                };
            };
        };
    };
    employeeToday: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Today's projection (record may be null when state == NOT_PUNCHED). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeTodayDto"];
                };
            };
        };
    };
    punchesForEmployee: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PunchDto"][];
                };
            };
        };
    };
    heatmapForEmployee: {
        parameters: {
            query?: {
                /** @description YYYY-MM, defaults to current month. */
                month?: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EmployeeHeatmapDto"];
                };
            };
        };
    };
    canteenUsage: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CanteenUsageDto"];
                };
            };
        };
    };
    approvalsInbox: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ApprovalsInbox"];
                };
            };
        };
    };
    myHistory: {
        parameters: {
            query: {
                /** @description Decision date lower bound (inclusive). */
                from: string;
                /** @description Decision date upper bound (inclusive). */
                to: string;
                /** @description Filter by request type. Omit for all. */
                type?: "LEAVE" | "SHORT_LEAVE" | "OVERTIME" | "REGULARIZATION";
                /** @description Filter by decision outcome. Omit for both. */
                decision?: "APPROVED" | "REJECTED";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description REQUEST_DATE_OUT_OF_RANGE — missing dates, inverted range, or window over 366 days. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PageResponseApprovalDecisionRow"];
                };
            };
        };
    };
    myDelegations: {
        parameters: {
            query?: {
                status?: "ACTIVE" | "REVOKED";
                activeOn?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DelegationDto"][];
                };
            };
        };
    };
    utilization: {
        parameters: {
            query: {
                year: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UtilizationDto"];
                };
            };
        };
    };
    seasonality: {
        parameters: {
            query?: {
                months?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SeasonalityDto"];
                };
            };
        };
    };
    scorecard: {
        parameters: {
            query?: {
                days?: number;
            };
            header?: never;
            path: {
                /** @description Employee id (upstream system). */
                employeeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ScorecardDto"];
                };
            };
        };
    };
    otTrend: {
        parameters: {
            query?: {
                months?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OtHoursTrendDto"];
                };
            };
        };
    };
    onTime: {
        parameters: {
            query?: {
                months?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OnTimeTrendDto"];
                };
            };
        };
    };
    absenteeism: {
        parameters: {
            query?: {
                months?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AbsenteeismTrendDto"];
                };
            };
        };
    };
    removeUserFromDevice: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                employeeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Bridge row removed; DELETE_USER queued. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description DEVICE_NOT_FOUND or DEVICE_USER_NOT_FOUND — employee is not on this device. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}

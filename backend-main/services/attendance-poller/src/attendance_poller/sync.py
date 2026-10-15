"""Single-cycle sync drainer.

Claims a batch of pending sync tasks for a device and executes each
one through pyzk. Reports the outcome back to attendance-service via
/complete or /fail.

Task types it handles:
  UPSERT_USER     — set_user(card_number, name, privilege); if
                    oldCardNumber is in the payload (card change),
                    delete the prior id first.
  DELETE_USER     — delete_user(user_id=card_number).
  PUSH_TEMPLATE   — fetch bytes via GET /templates/{id}, then push to
                    the device. FINGERPRINT goes through
                    save_user_template; FACE is not implemented in
                    pyzk 0.9 — the task fails non-retryably with a
                    clear message until firmware specifics are nailed
                    down.
  CAPTURE_TEMPLATE— read the employee's enrolled fingerprints OFF this
                    device (the enrollment device) via get_user_template
                    and POST each to /templates (store-only — the service
                    saves them centrally, no auto-push). Provisioning to
                    devices is a separate operator-chosen step. FINGERPRINT
                    only; fails non-retryably if the user isn't on the
                    device or has no enrolled finger.
  SNAPSHOT_DEVICE — dump device_info / users / attendance and POST
                    back via /devices/{id}/snapshots/{snapshotId}/
                    finalize. The finalize endpoint flips both the
                    snapshot row AND the linked task in one go, so
                    the drain loop skips the generic complete_task
                    for this type. Failures route through
                    /snapshots/{id}/fail for the same reason.

Idempotency: ZK set_user / delete_user are no-ops on missing /
existing users, so a re-claimed task (after a HR retry) re-applies
cleanly. Same for fingerprint save against a known uid. Snapshot
re-runs simply over-write the snapshot row's payloads.
"""
from __future__ import annotations

import argparse
import logging
import sys
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Iterator

import httpx
from zk.finger import Finger

from .client import AttendanceClient, SyncTask
from .device import DeviceConfig, connect, device_info, list_attendance, list_users

log = logging.getLogger(__name__)

DEFAULT_PRIVILEGE = 0


class NonRetryable(Exception):
    """Raised when a task can't possibly succeed against this device.

    Examples: face push on a firmware that doesn't support it,
    template format mismatch, malformed payload. The syncer reports
    these to the service with retryable=false so they don't burn
    backoff cycles.
    """


@dataclass
class SyncSettings:
    device_id: int
    base_url: str
    token: str | None
    host: str
    port: int
    password: int
    batch_size: int


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    settings = _settings(args)

    with AttendanceClient(settings.base_url, token=settings.token) as svc:
        try:
            tasks = svc.claim_next_tasks(settings.device_id, limit=settings.batch_size)
        except httpx.HTTPError as ex:
            # §7.poller #25 — include the response body so a 4xx points
            # the operator at the reason instead of just the status line.
            body = ""
            response = getattr(ex, "response", None)
            if response is not None:
                try:
                    body = response.text[:500]
                except Exception:  # noqa: BLE001 — opportunistic
                    body = ""
            log.error("failed to claim tasks for device %s: %s | body=%s",
                      settings.device_id, ex, body)
            return 2

        if not tasks:
            log.info("device %s: no sync tasks ready", settings.device_id)
            return 0

        log.info("device %s: claimed %d task(s)", settings.device_id, len(tasks))
        with _open_device(settings) as conn:
            done, failed = drain_tasks(conn, svc, settings.device_id, tasks)
        log.info("device %s: cycle done — %d done, %d failed", settings.device_id, done, failed)
        return 0 if failed == 0 else 3


# ---------------------------------------------------------------------------
# Drain loop
# ---------------------------------------------------------------------------


def drain_tasks(conn, svc: AttendanceClient, device_id: int,
                tasks: list[SyncTask]) -> tuple[int, int]:
    """Execute a claimed batch of sync tasks against an open device
    connection, reporting each outcome back to the service. Reused by the
    standalone sync entrypoint and by the pull loop's per-device pass.
    Returns (done, failed). Per-task errors are reported, not raised."""
    done = 0
    failed = 0
    for task in tasks:
        try:
            detail = _execute(conn, svc, task, device_id)
            # SNAPSHOT_DEVICE finalize flips the task DONE in the same
            # transaction as the snapshot row; calling complete_task
            # would 422 with "task is not IN_PROGRESS".
            if task.task_type != "SNAPSHOT_DEVICE":
                svc.complete_task(device_id, task.id, detail)
            done += 1
            log.info("task %s (%s) done", task.id, task.task_type)
        except NonRetryable as ex:
            _report_fail(svc, device_id, task, str(ex), retryable=False)
            failed += 1
            log.warning("task %s (%s) FAILED non-retryable: %s",
                        task.id, task.task_type, ex)
        except Exception as ex:  # noqa: BLE001 — pyzk raises various
            _report_fail(svc, device_id, task, repr(ex), retryable=True)
            failed += 1
            log.exception("task %s (%s) failed (retryable): %s", task.id, task.task_type, ex)
    return done, failed


def _report_fail(svc: AttendanceClient, device_id: int, task: SyncTask,
                 error: str, *, retryable: bool) -> None:
    if task.task_type == "SNAPSHOT_DEVICE":
        snapshot_id = task.payload.get("snapshotId")
        if snapshot_id is not None:
            svc.fail_snapshot(device_id, int(snapshot_id),
                              error=error, retryable=retryable)
            return
        # Fall through to the generic fail when payload is malformed —
        # the snapshot row stays PENDING but at least the task row gets
        # its retry/backoff applied so HR sees the failure on a list.
    svc.fail_task(device_id, task.id, error=error, retryable=retryable)


def _execute(conn, svc: AttendanceClient, task: SyncTask, device_id: int) -> str | None:
    if task.task_type == "UPSERT_USER":
        return _upsert_user(conn, task.payload)
    if task.task_type == "DELETE_USER":
        return _delete_user(conn, task.payload)
    if task.task_type == "PUSH_TEMPLATE":
        return _push_template(conn, svc, task.payload)
    if task.task_type == "CAPTURE_TEMPLATE":
        return _capture_template(conn, svc, task, device_id)
    if task.task_type == "SNAPSHOT_DEVICE":
        return _snapshot_device(conn, svc, device_id, task.payload)
    raise NonRetryable(f"unknown task_type: {task.task_type}")


def _upsert_user(conn, payload: dict) -> str:
    code = _required(payload, "employeeCode")
    name = payload.get("name") or ""
    privilege = int(payload.get("privilege") or DEFAULT_PRIVILEGE)
    old_code = payload.get("oldEmployeeCode")
    if old_code and str(old_code) != str(code):
        conn.delete_user(user_id=str(old_code))
    conn.set_user(uid=_derive_uid(code), name=name, privilege=privilege,
                  user_id=str(code))
    return f"upserted user_id={code}"


def _delete_user(conn, payload: dict) -> str:
    code = _required(payload, "employeeCode")
    conn.delete_user(user_id=str(code))
    return f"deleted user_id={code}"


def _push_template(conn, svc: AttendanceClient, payload: dict) -> str:
    code = _required(payload, "employeeCode")
    template_id = int(_required(payload, "templateId"))
    template = svc.get_template(template_id)
    if template.kind == "FACE":
        raise NonRetryable("FACE template push is not implemented in pyzk 0.9 — "
                           "wire firmware-specific call before re-enabling")
    if template.kind != "FINGERPRINT":
        raise NonRetryable(f"unknown template kind: {template.kind}")
    finger_id = template.finger_id
    if finger_id is None or not (0 <= finger_id <= 9):
        raise NonRetryable(f"FINGERPRINT requires fingerId in [0..9], got {finger_id}")

    user = _find_user(conn, str(code))
    if user is None:
        raise NonRetryable(f"user_id={code} not found on device — "
                           "expected UPSERT_USER to run first")
    finger = Finger(uid=user.uid, fid=finger_id, valid=1, template=template.bytes)
    conn.save_user_template(user, fingers=[finger])
    return f"pushed FINGERPRINT user_id={code} finger={finger_id} ({len(template.bytes)}B)"


def _capture_template(conn, svc: AttendanceClient, task: SyncTask, device_id: int) -> str:
    """Read the employee's enrolled fingerprints off this device and
    upload each to /templates (store-only — the service saves them
    centrally; pushing to devices is a separate operator-chosen step).
    The mirror image of _push_template: get_user_template here,
    save_user_template there. FINGERPRINT only — FACE capture is
    pointless until FACE push is implemented (see _push_template).

    employeeId comes off the task row (the upload needs it; the device
    only knows the employee_code). Probes finger slots 0..9; empty slots
    come back as None and are skipped."""
    code = _required(task.payload, "employeeCode")
    user = _find_user(conn, str(code))
    if user is None:
        raise NonRetryable(f"user_id={code} not found on device — enroll on the "
                           "keypad (or run UPSERT_USER) before capturing")
    template_format = _fp_format(conn)
    captured = 0
    for finger_id in range(10):
        finger = conn.get_user_template(uid=user.uid, temp_id=finger_id, user_id=str(code))
        if finger is None or not getattr(finger, "template", None):
            continue
        svc.upload_template(
            employee_id=task.employee_id,
            kind="FINGERPRINT",
            finger_id=finger_id,
            template_format=template_format,
            template_bytes=finger.template,
            captured_device_id=device_id,
        )
        captured += 1
    if captured == 0:
        raise NonRetryable(f"user_id={code} has no enrolled fingerprint on device — "
                           "enroll a finger on the keypad first")
    return f"captured {captured} fingerprint(s) for user_id={code} (fmt={template_format})"


def _fp_format(conn) -> int:
    """The device's fingerprint algorithm version, recorded as the
    template's format code so a later push can check compatibility.
    Best-effort — firmwares that don't expose it default to 0."""
    try:
        version = conn.get_fp_version()
        return int(version) if version is not None else 0
    except Exception:  # noqa: BLE001 — pyzk raises various; format is informational
        return 0


def _snapshot_device(conn, svc: AttendanceClient, device_id: int, payload: dict) -> None:
    snapshot_id = payload.get("snapshotId")
    if snapshot_id is None:
        raise NonRetryable("payload missing required field: snapshotId")
    info = device_info(conn)
    users = list_users(conn)
    attendance = list_attendance(conn)
    svc.finalize_snapshot(
        device_id, int(snapshot_id),
        device_info=info,
        users={"count": len(users), "users": users},
        attendance={"count": len(attendance), "records": attendance},
        user_count=len(users),
        record_count=len(attendance),
    )
    # Return None — the drain loop knows SNAPSHOT_DEVICE is finalized
    # in-line and skips the generic complete_task call.
    return None


def _find_user(conn, user_id: str):
    for u in conn.get_users():
        if str(u.user_id) == user_id:
            return u
    return None


def _derive_uid(employee_code) -> int:
    """Derive a stable device-side slot from the employee_code.

    The device's ``user_id`` field stores the employee_code as a string,
    but it also has a numeric ``uid`` slot used internally. Numeric
    employee_codes (legacy / migrated systems) map straight through;
    everything else hashes into the 16-bit slot space. Collisions are
    rare and surface as a clear ZK error.
    """
    s = str(employee_code)
    if s.isdigit():
        return int(s)
    return abs(hash(s)) % 65535


def _required(payload: dict, key: str):
    if key not in payload or payload[key] is None:
        raise NonRetryable(f"payload missing required field: {key}")
    return payload[key]


# ---------------------------------------------------------------------------
# CLI plumbing
# ---------------------------------------------------------------------------


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Drain one batch of sync tasks for a device "
                    "(UPSERT_USER / DELETE_USER / PUSH_TEMPLATE / "
                    "CAPTURE_TEMPLATE / SNAPSHOT_DEVICE).")
    p.add_argument("--device-id", type=int, required=True)
    p.add_argument("--base-url", required=True)
    p.add_argument("--token", default=None)
    p.add_argument("--host", required=True, help="Device IP, e.g. 10.10.107.21")
    p.add_argument("--port", type=int, default=4370)
    p.add_argument("--password", type=int, default=0)
    p.add_argument("--batch-size", type=int, default=10)
    p.add_argument("-v", "--verbose", action="store_true")
    return p.parse_args(argv)


def _settings(args: argparse.Namespace) -> SyncSettings:
    return SyncSettings(
        device_id=args.device_id,
        base_url=args.base_url,
        token=args.token,
        host=args.host,
        port=args.port,
        password=args.password,
        batch_size=args.batch_size,
    )


@contextmanager
def _open_device(s: SyncSettings) -> Iterator:
    cfg = DeviceConfig(host=s.host, port=s.port, password=s.password)
    with connect(cfg) as conn:
        yield conn


if __name__ == "__main__":
    sys.exit(main())

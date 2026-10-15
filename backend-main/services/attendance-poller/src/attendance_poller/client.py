"""Thin HTTP client over attendance-service for the poller's REST surface.

Talks JSON-only and surfaces small typed results so callers don't
unpack raw response dicts. Auth is a static bearer token for now —
service-account JWT login + refresh is wired in later (see
attendance.md §9 "Auth").
"""
from __future__ import annotations

import base64
import logging
import os
from dataclasses import dataclass
from datetime import datetime
from typing import Iterable

import httpx

log = logging.getLogger(__name__)

DEFAULT_TIMEOUT = httpx.Timeout(connect=5.0, read=15.0, write=15.0, pool=5.0)

# Attendance-service runs in header-trust mode (CLAUDE.md §"Auth"): the
# gateway terminates the JWT and forwards X-User-* identity headers on
# every request. The poller calls the service directly, bypassing the
# gateway, so it must inject the same headers itself — otherwise the
# security chain rejects every call with 403. Values come from env so
# the same image works in dev (system-poller, attendance.poller) and in
# UAT/prod (a real service-account user id).
_SERVICE_IDENTITY_HEADERS_ENV = {
    "X-User-Id": "POLLER_IDENTITY_USER_ID",
    "X-User-Name": "POLLER_IDENTITY_USERNAME",
    "X-User-Permissions": "POLLER_IDENTITY_PERMISSIONS",
    "X-User-Roles": "POLLER_IDENTITY_ROLES",
}


@dataclass
class IngestPunch:
    """A punch on its way to attendance-service. ``employee_code`` is
    the value the device emits in its ``user_id`` field — per project
    convention the device's user_id IS the employee_code, so attendance
    matches it directly against the attendance_employees snapshot."""
    employee_code: str
    device_id: int
    punched_at: datetime

    def to_payload(self) -> dict:
        return {
            "employeeCode": self.employee_code,
            "deviceId": self.device_id,
            # Naive ISO-8601 — server runs TZ=Asia/Karachi and stores as
            # plain timestamp; no offset/Z suffix.
            "punchedAt": self.punched_at.isoformat(),
        }


@dataclass
class PollerTarget:
    """One row from GET /devices/poller-targets — what the service tells
    the poller to dial. ``last_punched_at`` is the high-water-mark; the
    poller filters its ZK pull to records strictly after it. Null on a
    fresh device or after an HR-triggered HWM reset → pull everything."""
    id: int
    code: str
    device_ip: str
    comm_port: int
    comm_password: int
    last_punched_at: datetime | None

    @classmethod
    def from_json(cls, raw: dict) -> "PollerTarget":
        ts_raw = raw.get("lastPunchedAt")
        ts: datetime | None = None
        if ts_raw is not None:
            ts = datetime.fromisoformat(str(ts_raw).replace("Z", "+00:00"))
            if ts.tzinfo is not None:
                # Server runs Asia/Karachi as plain timestamp; strip any
                # offset that snuck through ISO parsing so comparisons
                # with naive pyzk timestamps stay correct.
                ts = ts.replace(tzinfo=None)
        return cls(
            id=int(raw["id"]),
            code=str(raw["code"]),
            device_ip=str(raw["deviceIp"]),
            comm_port=int(raw["commPort"]),
            comm_password=int(raw["commPassword"]),
            last_punched_at=ts,
        )


@dataclass
class IngestResult:
    ingested_count: int
    skipped: list[dict]

    @property
    def skipped_count(self) -> int:
        return len(self.skipped)


@dataclass
class SyncTask:
    """A claimed sync task as the syncer sees it.

    payload shape varies by task_type — see the V0008 SQL comment and the
    DeviceSyncTaskType javadoc for the per-type contract (UPSERT_USER,
    DELETE_USER, PUSH_TEMPLATE, CAPTURE_TEMPLATE, SNAPSHOT_DEVICE).
    """
    id: int
    device_id: int
    employee_id: int
    task_type: str
    payload: dict
    attempts: int

    @classmethod
    def from_json(cls, raw: dict) -> "SyncTask":
        return cls(
            id=int(raw["id"]),
            device_id=int(raw["deviceId"]),
            employee_id=int(raw["employeeId"]),
            task_type=str(raw["taskType"]),
            payload=raw.get("payload") or {},
            attempts=int(raw.get("attempts", 0)),
        )


@dataclass
class Template:
    id: int
    employee_id: int
    kind: str
    finger_id: int | None
    format: int
    bytes: bytes

    @classmethod
    def from_json(cls, raw: dict) -> "Template":
        return cls(
            id=int(raw["id"]),
            employee_id=int(raw["employeeId"]),
            kind=str(raw["kind"]),
            finger_id=raw.get("fingerId"),
            format=int(raw["format"]),
            bytes=base64.b64decode(raw["bytes"]),
        )


class AttendanceClient:
    def __init__(self, base_url: str, token: str | None = None,
                 timeout: httpx.Timeout = DEFAULT_TIMEOUT) -> None:
        self._base = base_url.rstrip("/")
        self._client = httpx.Client(base_url=self._base, timeout=timeout, headers=self._headers(token))

    @staticmethod
    def _headers(token: str | None) -> dict[str, str]:
        h = {"Content-Type": "application/json", "Accept": "application/json"}
        if token:
            h["Authorization"] = f"Bearer {token}"
        for header_name, env_var in _SERVICE_IDENTITY_HEADERS_ENV.items():
            value = os.environ.get(env_var)
            if value:
                h[header_name] = value
        return h

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> "AttendanceClient":
        return self

    def __exit__(self, *_exc) -> None:
        self.close()

    # ------------------------------------------------------------------
    # Poller targets (table-driven mode)
    # ------------------------------------------------------------------

    def poller_targets(self) -> list[PollerTarget]:
        # AttendanceDeviceController is @RequestMapping("/devices") — no
        # /attendance prefix here, unlike /attendance/punches/ingest
        # (AttendanceController is @RequestMapping("/attendance")).
        r = self._client.get("/devices/poller-targets")
        r.raise_for_status()
        return [PollerTarget.from_json(t) for t in r.json()]

    # ------------------------------------------------------------------
    # Punch ingest
    # ------------------------------------------------------------------

    def ingest_punches(self, punches: Iterable[IngestPunch]) -> IngestResult:
        body = {"punches": [p.to_payload() for p in punches]}
        if not body["punches"]:
            return IngestResult(0, [])
        r = self._client.post("/attendance/punches/ingest", json=body)
        r.raise_for_status()
        data = r.json()
        ingested = data.get("ingested") or []
        skipped = data.get("skipped") or []
        return IngestResult(ingested_count=len(ingested), skipped=skipped)

    # ------------------------------------------------------------------
    # Heartbeat
    # ------------------------------------------------------------------

    def heartbeat(self, device_id: int, status: str, *,
                  device_time: datetime | None = None,
                  firmware_version: str | None = None,
                  serial_number: str | None = None) -> None:
        body: dict = {"status": status}
        if device_time is not None:
            body["deviceTime"] = device_time.isoformat()
        if firmware_version is not None:
            body["firmwareVersion"] = firmware_version
        if serial_number is not None:
            body["serialNumber"] = serial_number
        r = self._client.post(f"/devices/{device_id}/heartbeat", json=body)
        r.raise_for_status()

    # ------------------------------------------------------------------
    # Sync queue
    # ------------------------------------------------------------------

    def claim_next_tasks(self, device_id: int, limit: int = 10) -> list[SyncTask]:
        r = self._client.get(f"/devices/{device_id}/sync-tasks/next",
                             params={"limit": limit})
        r.raise_for_status()
        return [SyncTask.from_json(t) for t in r.json()]

    def complete_task(self, device_id: int, task_id: int, detail: str | None = None) -> None:
        body = {"detail": detail} if detail else {}
        r = self._client.post(
            f"/devices/{device_id}/sync-tasks/{task_id}/complete", json=body)
        r.raise_for_status()

    def fail_task(self, device_id: int, task_id: int, error: str,
                  retryable: bool | None = None) -> None:
        body: dict = {"error": error}
        if retryable is not None:
            body["retryable"] = retryable
        r = self._client.post(
            f"/devices/{device_id}/sync-tasks/{task_id}/fail", json=body)
        r.raise_for_status()

    # ------------------------------------------------------------------
    # Templates
    # ------------------------------------------------------------------

    def get_template(self, template_id: int) -> Template:
        r = self._client.get(f"/templates/{template_id}")
        r.raise_for_status()
        return Template.from_json(r.json())

    def upload_template(self, *, employee_id: int, kind: str,
                        finger_id: int | None, template_format: int,
                        template_bytes: bytes, captured_device_id: int) -> Template:
        """POST a captured template to /templates. Store-only — the
        service saves it centrally (replacing any prior row for the same
        employee/kind/finger); it does NOT push to any device. Pushing is
        a separate, operator-chosen step (push-templates endpoint). Bytes
        go up base64-encoded; the service caps the decoded size."""
        body = {
            "employeeId": employee_id,
            "kind": kind,
            "fingerId": finger_id,
            "format": template_format,
            "bytes": base64.b64encode(template_bytes).decode("ascii"),
            "capturedDeviceId": captured_device_id,
        }
        r = self._client.post("/templates", json=body)
        r.raise_for_status()
        return Template.from_json(r.json())

    # ------------------------------------------------------------------
    # Device snapshots
    # ------------------------------------------------------------------

    def finalize_snapshot(self, device_id: int, snapshot_id: int, *,
                          device_info: dict, users: dict, attendance: dict,
                          user_count: int | None = None,
                          record_count: int | None = None) -> None:
        """POST the captured dump back to the service. The service flips
        the snapshot row to COMPLETED AND marks the linked sync task
        DONE in the same transaction — the syncer's drain loop must
        skip the generic complete_task call for SNAPSHOT_DEVICE."""
        body: dict = {
            "deviceInfo": device_info,
            "users": users,
            "attendance": attendance,
        }
        if user_count is not None:
            body["userCount"] = user_count
        if record_count is not None:
            body["recordCount"] = record_count
        r = self._client.post(
            f"/devices/{device_id}/snapshots/{snapshot_id}/finalize", json=body)
        r.raise_for_status()

    def fail_snapshot(self, device_id: int, snapshot_id: int, error: str,
                      retryable: bool | None = None) -> None:
        """Counterpart to fail_task for SNAPSHOT_DEVICE. Same retry/
        backoff policy as the generic fail; the snapshot row flips to
        FAILED only on terminal failure (retryable=false or max
        attempts exhausted)."""
        body: dict = {"error": error}
        if retryable is not None:
            body["retryable"] = retryable
        r = self._client.post(
            f"/devices/{device_id}/snapshots/{snapshot_id}/fail", json=body)
        r.raise_for_status()

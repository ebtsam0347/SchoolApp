"""Pull entrypoint — table-driven, stateless.

Loops every ``--interval`` seconds. Each tick:
    1. ``GET /attendance/devices/poller-targets`` — active devices with
       a non-null ``device_ip``, joined to their high-water-mark.
    2. For each target, open the ZK socket, pull all records strictly
       after ``last_punched_at``, batch-POST to ``/attendance/punches/ingest``,
       then heartbeat.
    3. On the same open connection, claim and drain that device's pending
       sync tasks (UPSERT_USER / DELETE_USER / PUSH_TEMPLATE /
       CAPTURE_TEMPLATE / SNAPSHOT_DEVICE)
       so enrollments and snapshots pick up on the same ~interval cadence as
       punches — no separate scheduler. Bounded per tick; backlogs drain over
       successive ticks.

No local state. No retry buffer. The service owns:
    - the high-water-mark (advanced inside the ingest transaction),
    - the unmatched-card replay queue (drained by a scheduled job),
    - the sync-task queue (claim/complete/fail with retry+backoff).
"""
from __future__ import annotations

import argparse
import logging
import sys
import time
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime
from typing import Iterator

import httpx

from .client import AttendanceClient, IngestPunch, PollerTarget
from .device import DeviceConfig, connect
from .sync import drain_tasks

log = logging.getLogger(__name__)


@dataclass
class PullSettings:
    base_url: str
    token: str | None
    interval_seconds: int
    batch_size: int
    sync_batch_size: int
    once: bool


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    settings = _settings(args)

    with AttendanceClient(settings.base_url, token=settings.token) as svc:
        if settings.once:
            return _tick(svc, settings)
        log.info("poller starting — base_url=%s interval=%ds",
                 settings.base_url, settings.interval_seconds)
        while True:
            # Top-level catch so a transient attendance-service outage
            # (recreate, 5xx, network blip) doesn't kill the process and
            # spin up the docker restart loop. _tick already swallows
            # per-device errors; this protects the calls that live
            # outside that loop (poller_targets, etc.).
            try:
                _tick(svc, settings)
            except Exception:
                log.exception("poller tick failed; sleeping and retrying")
            time.sleep(settings.interval_seconds)


# ---------------------------------------------------------------------------
# Per-tick logic
# ---------------------------------------------------------------------------


def _tick(svc: AttendanceClient, settings: PullSettings) -> int:
    """One sweep across all active devices. Returns 0 on success,
    non-zero on any device failure (still continues the sweep).

    Per-device errors don't abort the sweep — the poller is a sidecar,
    not a transaction. A device that's down today shouldn't block
    the others.
    """
    targets = svc.poller_targets()
    if not targets:
        log.info("poller tick: no active devices")
        return 0

    failures = 0
    for target in targets:
        try:
            _pull_one(svc, target, settings)
        except httpx.HTTPError as ex:
            failures += 1
            body = _extract_body(ex)
            log.error("device %s (%s): attendance-service call failed: %s | body=%s",
                      target.id, target.code, ex, body)
            _try_heartbeat(svc, target.id, "ERROR")
        except Exception as ex:
            failures += 1
            log.exception("device %s (%s): pull cycle failed: %s",
                          target.id, target.code, ex)
            _try_heartbeat(svc, target.id, "ERROR")
    return 0 if failures == 0 else 1


def _pull_one(svc: AttendanceClient, target: PollerTarget, settings: PullSettings) -> None:
    log.info("device %s (%s): pulling — last_punched_at=%s",
             target.id, target.code, target.last_punched_at)

    with _open_device(target) as conn:
        # Drain sync tasks FIRST, before the slow attendance dump below.
        # Snapshots / enrollments are interactive and must not be gated behind
        # (or skipped by a failure in) the punch pull — device 173 accepts TCP
        # but its get_attendance hangs, which previously stranded its snapshot.
        # Self-contained: failures are reported per-task, never raised here.
        _drain_sync_tasks(svc, target.id, conn, settings.sync_batch_size)

        new_punches, new_high = _collect_new(conn, target.last_punched_at)
        log.info("device %s: %d new punches", target.id, len(new_punches))

        ingested_total = 0
        skipped_total = 0
        payload = [
            IngestPunch(employee_code=str(a.user_id),
                        device_id=target.id,
                        punched_at=a.timestamp)
            for a in new_punches
        ]
        for batch in _batched(payload, settings.batch_size):
            result = svc.ingest_punches(batch)
            ingested_total += result.ingested_count
            skipped_total += result.skipped_count
            if result.skipped:
                log.warning("device %s: %d punches skipped (e.g. %s)",
                            target.id, result.skipped_count, result.skipped[0])

        svc.heartbeat(target.id, status="ONLINE",
                      firmware_version=_safe_call(conn, "get_firmware_version"),
                      serial_number=_safe_call(conn, "get_serialnumber"))
        log.info("device %s: cycle done — ingested=%d skipped=%d new_high=%s",
                 target.id, ingested_total, skipped_total,
                 new_high.isoformat() if new_high is not None else "n/a")


def _drain_sync_tasks(svc: AttendanceClient, device_id: int, conn, limit: int) -> None:
    try:
        tasks = svc.claim_next_tasks(device_id, limit=limit)
    except Exception as ex:  # noqa: BLE001 — never let sync abort the punch cycle
        log.warning("device %s: claim sync tasks failed: %s", device_id, ex)
        return
    if not tasks:
        return
    log.info("device %s: claimed %d sync task(s)", device_id, len(tasks))
    try:
        done, failed = drain_tasks(conn, svc, device_id, tasks)
        log.info("device %s: sync drain done — %d done, %d failed", device_id, done, failed)
    except Exception as ex:  # noqa: BLE001 — drain_tasks reports per-task; this is belt-and-suspenders
        log.exception("device %s: sync drain aborted: %s", device_id, ex)


@contextmanager
def _open_device(target: PollerTarget) -> Iterator:
    cfg = DeviceConfig(host=target.device_ip,
                       port=target.comm_port,
                       password=target.comm_password)
    with connect(cfg) as conn:
        yield conn


def _collect_new(conn, last_punched_at: datetime | None) -> tuple[list, datetime | None]:
    """Pull all attendance and return only those past the high-water-mark.

    pyzk doesn't support server-side filtering — the device hands us
    its full ring buffer every time. We sort and trim client-side.
    """
    records = sorted(conn.get_attendance(), key=lambda a: a.timestamp)
    if last_punched_at is not None:
        records = [a for a in records if a.timestamp > last_punched_at]
    new_high = records[-1].timestamp if records else None
    return records, new_high


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Pull biometric attendance from every active device "
                    "and forward to attendance-service.")
    p.add_argument("--base-url", required=True,
                   help="attendance-service base URL, e.g. http://localhost:8083")
    p.add_argument("--token", default=None,
                   help="Bearer token for the poller's service account (attendance.poller perm).")
    p.add_argument("--interval", type=int, default=60,
                   help="Seconds between sweeps. Default 60.")
    p.add_argument("--once", action="store_true",
                   help="Run a single sweep and exit (cron / one-shot mode).")
    p.add_argument("--batch-size", type=int, default=200)
    p.add_argument("--sync-batch-size", type=int, default=50,
                   help="Max sync tasks claimed per device per tick (server caps at 100). "
                        "Backlogs drain over successive ticks. Default 50.")
    p.add_argument("-v", "--verbose", action="store_true")
    return p.parse_args(argv)


def _settings(args: argparse.Namespace) -> PullSettings:
    return PullSettings(
        base_url=args.base_url,
        token=args.token,
        interval_seconds=args.interval,
        batch_size=args.batch_size,
        sync_batch_size=args.sync_batch_size,
        once=args.once,
    )


def _batched(seq: list, size: int) -> Iterator[list]:
    for i in range(0, len(seq), size):
        yield seq[i:i + size]


def _safe_call(conn, attr: str):
    fn = getattr(conn, attr, None)
    if fn is None:
        return None
    try:
        return fn()
    except Exception as ex:  # noqa: BLE001 — pyzk raises various
        log.debug("%s() failed: %s", attr, ex)
        return None


def _try_heartbeat(svc: AttendanceClient, device_id: int, status: str) -> None:
    try:
        svc.heartbeat(device_id, status=status)
    except Exception as ex:  # noqa: BLE001
        log.warning("heartbeat (%s) post-failure also failed: %s", status, ex)


def _extract_body(ex: httpx.HTTPError) -> str:
    response = getattr(ex, "response", None)
    if response is None:
        return ""
    try:
        return response.text[:500]
    except Exception:  # noqa: BLE001 — opportunistic
        return ""


if __name__ == "__main__":
    sys.exit(main())

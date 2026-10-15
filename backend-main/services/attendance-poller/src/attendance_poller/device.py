"""ZK protocol wrapper around pyzk.

Keeps the raw library at arm's length so the rest of the project sees
plain dicts/datetimes instead of pyzk's internal record types.
"""
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime
from typing import Iterator

from zk import ZK
from zk.base import ZK as ZKConn


@dataclass(frozen=True)
class DeviceConfig:
    host: str
    port: int = 4370
    password: int = 0
    timeout: int = 10
    force_udp: bool = False
    ommit_ping: bool = True  # we already verified TCP reachability


@contextmanager
def connect(cfg: DeviceConfig) -> Iterator[ZKConn]:
    """Open a ZK session, disable the device for the duration of bulk reads,
    and always re-enable + disconnect on exit."""
    zk = ZK(
        cfg.host,
        port=cfg.port,
        timeout=cfg.timeout,
        password=cfg.password,
        force_udp=cfg.force_udp,
        ommit_ping=cfg.ommit_ping,
    )
    conn = zk.connect()
    try:
        conn.disable_device()
        yield conn
    finally:
        try:
            conn.enable_device()
        finally:
            conn.disconnect()


def device_info(conn: ZKConn) -> dict:
    """Best-effort metadata dump. Different firmwares expose different
    subsets, so each lookup is wrapped — a missing field is normal."""

    def safe(fn):
        try:
            return fn()
        except Exception as exc:  # noqa: BLE001 — pyzk raises various
            return f"<error: {exc!r}>"

    return {
        "firmware_version": safe(conn.get_firmware_version),
        "serial_number": safe(conn.get_serialnumber),
        "platform": safe(conn.get_platform),
        "device_name": safe(conn.get_device_name),
        "mac": safe(conn.get_mac),
        "face_version": safe(conn.get_face_version),
        "fp_version": safe(conn.get_fp_version),
        "extend_fmt": safe(conn.get_extend_fmt),
        "user_extend_fmt": safe(conn.get_user_extend_fmt),
        "face_fun_on": safe(conn.get_face_fun_on),
        "compat_old_firmware": safe(conn.get_compat_old_firmware),
        "network_params": safe(conn.get_network_params),
        "pin_width": safe(conn.get_pin_width),
        "device_time": safe(lambda: _iso(conn.get_time())),
    }


def list_users(conn: ZKConn) -> list[dict]:
    return [
        {
            "uid": u.uid,
            "user_id": u.user_id,
            "name": u.name,
            "privilege": u.privilege,
            "group_id": u.group_id,
            "card": u.card,
            # password intentionally not dumped
        }
        for u in conn.get_users()
    ]


def list_attendance(conn: ZKConn) -> list[dict]:
    return [
        {
            "user_id": a.user_id,
            "timestamp": _iso(a.timestamp),
            "status": a.status,
            "punch": a.punch,
            "uid": getattr(a, "uid", None),
        }
        for a in conn.get_attendance()
    ]


def _iso(value) -> str | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.isoformat()
    return str(value)

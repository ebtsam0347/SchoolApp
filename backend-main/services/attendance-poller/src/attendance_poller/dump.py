"""One-shot device dump.

Connects to a ZKTeco-style device, pulls users + attendance + metadata,
and writes JSON files into a per-run directory. Used to gather a
representative sample so the live ingestion pipeline can be designed
offline.
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from .device import DeviceConfig, connect, device_info, list_attendance, list_users

DEFAULT_OUT = Path(__file__).resolve().parents[3].parent / "migration" / "attendance_device"


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Dump a ZK attendance device's users + punches to JSON.")
    p.add_argument("--host", required=True, help="Device IP, e.g. 10.10.107.21")
    p.add_argument("--port", type=int, default=4370)
    p.add_argument("--password", type=int, default=0, help="Device comm password (numeric)")
    p.add_argument("--timeout", type=int, default=10)
    p.add_argument("--force-udp", action="store_true", help="Use UDP instead of TCP")
    p.add_argument(
        "--out",
        type=Path,
        default=DEFAULT_OUT,
        help=f"Output root directory (default: {DEFAULT_OUT})",
    )
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    cfg = DeviceConfig(
        host=args.host,
        port=args.port,
        password=args.password,
        timeout=args.timeout,
        force_udp=args.force_udp,
    )

    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run_dir = args.out / f"{args.host.replace('.', '_')}_{stamp}"
    run_dir.mkdir(parents=True, exist_ok=True)

    print(f"connecting to {cfg.host}:{cfg.port} (udp={cfg.force_udp}) …", flush=True)
    with connect(cfg) as conn:
        print("connected. pulling device info …", flush=True)
        info = device_info(conn)
        _write(run_dir / "device_info.json", info)

        print("pulling users …", flush=True)
        users = list_users(conn)
        _write(run_dir / "users.json", {"count": len(users), "users": users})
        print(f"  {len(users)} users", flush=True)

        print("pulling attendance log …", flush=True)
        att = list_attendance(conn)
        _write(run_dir / "attendance.json", {"count": len(att), "records": att})
        print(f"  {len(att)} attendance records", flush=True)

    print(f"\ndumped to {run_dir}", flush=True)
    return 0


def _write(path: Path, payload) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False, default=str))


if __name__ == "__main__":
    sys.exit(main())

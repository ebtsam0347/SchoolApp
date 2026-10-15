"""
Load legacy employee photos into MinIO and set `employees.profile_picture_key`.

Source of truth is `staging.apis_employeinfo.emppic` — the legacy Django
MEDIA_ROOT filename per employee — NOT the filenames on disk. The folder
also holds EducationDocs/Announcements and ~850 orphaned images, and the
top-level names are inconsistent ("001Amina4510.jpg"), so matching by
filename pattern would be guesswork. The column is authoritative.

Writes straight to MinIO + Postgres rather than driving
POST /employees/{id}/profile-picture/upload-url. The service only ever
reads the column back through `signAvatarGet(objectKey)`, so a correctly
placed object is indistinguishable from an API upload — and the API path
would mean a JWT, two round-trips per photo and a 10-minute URL TTL to
race, ~16k calls to reach the same two writes.

  - Postgres:  PGURI env var, defaults to the replicate.py default
  - MinIO:     MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY
  - Photos:    --media-dir (the legacy MEDIA_ROOT)

Object key is deterministic — `employees/{id}/profile-picture/legacy_{code}.{ext}`
— so re-running overwrites in place instead of orphaning a previous object
per run. It still satisfies the prefix check in
`EmployeeDocumentService.setProfilePicture`, so a later change through the
UI deletes this object correctly.

Usage:
  .venv/bin/python 09_profile_pictures.py --media-dir /path/to/media --dry-run
  .venv/bin/python 09_profile_pictures.py --media-dir /path/to/media
"""

from __future__ import annotations

import argparse
import io
import os
import sys
from dataclasses import dataclass, field
from pathlib import Path

import psycopg
from minio import Minio
from minio.error import S3Error
from PIL import Image

PG_URI = os.environ.get("PGURI", "postgresql://postgres:postgres@localhost:5432/fshrms")
BUCKET = "employee-avatars"
AVATAR_MAX_BYTES = 2 * 1024 * 1024        # DocumentProperties.avatarMaxSizeBytes default
RESIZE_TO = (512, 512)                    # only applied to files over the cap
ALLOWED_EXT = {".jpg", ".jpeg", ".png"}


@dataclass
class Counts:
    uploaded: int = 0
    skipped_exists: int = 0
    no_emppic: int = 0
    file_missing: int = 0
    bad_ext: int = 0
    resized: int = 0
    failed: int = 0
    shared_file: int = 0
    missing_samples: list[str] = field(default_factory=list)
    failed_samples: list[str] = field(default_factory=list)


def build_file_index(media_dir: Path) -> dict[str, Path]:
    """Lowercased basename -> path, top level only.

    Case-insensitive because the legacy column and the filesystem disagree
    on case for ~140 rows; matching case-sensitively silently loses them.
    """
    index: dict[str, Path] = {}
    for p in media_dir.iterdir():
        if p.is_file():
            index.setdefault(p.name.lower(), p)
    return index


def fetch_targets(conn) -> list[tuple[int, str, str | None]]:
    """(employee_id, employee_code, emppic) for every migrated employee."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT e.id, e.employee_code, NULLIF(TRIM(ei.emppic), '')
              FROM employee.employees e
              JOIN migration.id_map m
                ON m.target_table = 'employees' AND m.target_id = e.id
               AND m.source_table = 'apis_employeinfo'
              JOIN staging.apis_employeinfo ei ON ei.id = m.source_id
             ORDER BY e.id
        """)
        return cur.fetchall()


def payload_for(path: Path, counts: Counts) -> tuple[bytes, str]:
    """File bytes + content type, downscaling only if over the avatar cap."""
    ext = path.suffix.lower()
    content_type = "image/png" if ext == ".png" else "image/jpeg"
    data = path.read_bytes()
    if len(data) <= AVATAR_MAX_BYTES:
        return data, content_type

    img = Image.open(io.BytesIO(data))
    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")
    img.thumbnail(RESIZE_TO)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85, optimize=True)
    counts.resized += 1
    return buf.getvalue(), "image/jpeg"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--media-dir", required=True, type=Path)
    ap.add_argument("--dry-run", action="store_true",
                    help="resolve and report, upload nothing, write nothing")
    ap.add_argument("--overwrite", action="store_true",
                    help="re-upload even when profile_picture_key is already set")
    ap.add_argument("--limit", type=int, help="stop after N uploads (smoke test)")
    args = ap.parse_args()

    if not args.media_dir.is_dir():
        sys.exit(f"not a directory: {args.media_dir}")

    endpoint = os.environ.get("MINIO_ENDPOINT", "http://localhost:9000")
    secure = endpoint.startswith("https://")
    host = endpoint.split("://", 1)[-1]
    client = Minio(
        host,
        access_key=os.environ.get("MINIO_ACCESS_KEY", "minioadmin"),
        secret_key=os.environ.get("MINIO_SECRET_KEY", "minioadmin"),
        secure=secure,
    )

    if not args.dry_run and not client.bucket_exists(BUCKET):
        # Normally created on startup via hrms.storage.ensure-buckets; the
        # loader may run before the service has ever booted on a fresh host.
        client.make_bucket(BUCKET)
        print(f"created bucket {BUCKET}")

    files = build_file_index(args.media_dir)
    print(f"{len(files)} files indexed under {args.media_dir}")

    counts = Counts()
    seen_source: dict[str, int] = {}

    with psycopg.connect(PG_URI) as conn:
        targets = fetch_targets(conn)
        print(f"{len(targets)} migrated employees to consider\n")

        if not args.overwrite:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM employee.employees "
                            "WHERE profile_picture_key IS NOT NULL")
                already = {r[0] for r in cur.fetchall()}
        else:
            already = set()

        for emp_id, code, emppic in targets:
            if emp_id in already:
                counts.skipped_exists += 1
                continue
            if not emppic:
                counts.no_emppic += 1
                continue

            path = files.get(emppic.lower())
            if path is None:
                counts.file_missing += 1
                if len(counts.missing_samples) < 10:
                    counts.missing_samples.append(f"{code} -> {emppic}")
                continue

            ext = path.suffix.lower()
            if ext not in ALLOWED_EXT:
                counts.bad_ext += 1
                continue

            key_ext = ".png" if ext == ".png" else ".jpg"
            key = f"employees/{emp_id}/profile-picture/legacy_{code}{key_ext}"

            seen_source[emppic.lower()] = seen_source.get(emppic.lower(), 0) + 1
            if seen_source[emppic.lower()] == 2:
                counts.shared_file += 1

            if args.dry_run:
                counts.uploaded += 1
            else:
                try:
                    data, content_type = payload_for(path, counts)
                    client.put_object(BUCKET, key, io.BytesIO(data), len(data),
                                      content_type=content_type)
                    with conn.cursor() as cur:
                        cur.execute(
                            "UPDATE employee.employees "
                            "   SET profile_picture_key = %s, updated_by = 'legacy-migration' "
                            " WHERE id = %s", (key, emp_id))
                    conn.commit()
                    counts.uploaded += 1
                except (S3Error, OSError) as ex:
                    conn.rollback()
                    counts.failed += 1
                    if len(counts.failed_samples) < 10:
                        counts.failed_samples.append(f"{code} -> {path.name}: {ex}")

            if counts.uploaded % 500 == 0 and counts.uploaded:
                print(f"  {counts.uploaded} uploaded...", flush=True)
            if args.limit and counts.uploaded >= args.limit:
                print(f"  stopping at --limit {args.limit}")
                break

    verb = "would upload" if args.dry_run else "uploaded"
    print(f"\n{verb:>16}: {counts.uploaded}")
    print(f"{'already set':>16}: {counts.skipped_exists}  (re-run with --overwrite to replace)")
    print(f"{'no emppic':>16}: {counts.no_emppic}")
    print(f"{'file missing':>16}: {counts.file_missing}")
    print(f"{'bad extension':>16}: {counts.bad_ext}")
    print(f"{'resized >2MB':>16}: {counts.resized}")
    print(f"{'shared source':>16}: {counts.shared_file}  (same legacy file on >1 employee)")
    print(f"{'FAILED':>16}: {counts.failed}")

    # Unresolved rows are reported, never silently dropped: a photo load
    # that quietly covers half the staff looks identical to one that worked.
    for label, samples in (("missing files", counts.missing_samples),
                           ("failures", counts.failed_samples)):
        if samples:
            print(f"\nsample {label}:")
            for s in samples:
                print(f"  {s}")

    return 1 if counts.failed else 0


if __name__ == "__main__":
    raise SystemExit(main())

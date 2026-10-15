"""
Replicate selected MSSQL tables into local Postgres `staging` schema.

  - Source creds:  ../.env.db  (SOURCE_DB_IP / USER / PASS, SOURCE_DB)
  - Target Postgres: PGURI env var, defaults to
        postgresql://postgres:postgres@localhost:5432/fshrms
  - Table list:    tables.yml (per-table `since_date` pins an absolute
                   cutoff; `since_days` is the rolling-window fallback)

Behavior:
  - Truncate-and-reload per table: the staging table is DROP+CREATE every run.
  - All staging columns are TEXT — type coercion is your transform layer's job.
  - Source identifiers preserve case; staging identifiers are lowercased.
  - Streams via pymssql.fetchmany + psycopg3 COPY FROM STDIN — bounded memory.

Usage:
  .venv/bin/python replicate.py                 # all tables in tables.yml
  .venv/bin/python replicate.py --only T1 T2    # only these tables
  .venv/bin/python replicate.py --skip T1 T2    # all except these
  .venv/bin/python replicate.py --list          # print the resolved table list and exit
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal
from pathlib import Path

import psycopg
import pymssql
import yaml

HERE = Path(__file__).parent
ENV_FILE = HERE.parent / ".env.db"
TABLES_YML = HERE / "tables.yml"

PG_URI = os.environ.get(
    "PGURI", "postgresql://postgres:postgres@localhost:5432/fshrms"
)
BATCH = 5_000  # rows per fetchmany / per progress tick


# --- config -----------------------------------------------------------------


def load_env(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        k, _, v = line.partition("=")
        out[k.strip()] = v.strip()
    return out


@dataclass
class TableSpec:
    name: str
    since_column: str | None = None
    since_days: int | None = None   # rolling window; None = no rolling filter
    since_date: date | None = None  # absolute cutoff; wins over since_days

    @property
    def filtered(self) -> bool:
        return self.since_column is not None and (
            self.since_date is not None or self.since_days is not None
        )

    def cutoff(self) -> date | None:
        """Absolute date this table is filtered from, or None if unfiltered."""
        if self.since_column is None:
            return None
        if self.since_date is not None:
            return self.since_date
        if self.since_days is not None:
            return date.today() - timedelta(days=self.since_days)
        return None

    def window_label(self) -> str:
        if not self.filtered:
            return ""
        if self.since_date is not None:
            return f"{self.since_column} >= {self.since_date.isoformat()}"
        return f"{self.since_column} >= now-{self.since_days}d"

    def label(self) -> str:
        if self.filtered:
            return f"{self.name} ({self.window_label()})"
        return self.name


def _as_date(v: object) -> date | None:
    """YAML gives a `date` for an unquoted ISO date, a str if it was quoted."""
    if v is None:
        return None
    if isinstance(v, datetime):
        return v.date()
    if isinstance(v, date):
        return v
    return date.fromisoformat(str(v).strip())


def load_yaml() -> tuple[str, str, list[TableSpec]]:
    cfg = yaml.safe_load(TABLES_YML.read_text())
    defaults = cfg.get("defaults") or {}
    default_days = defaults.get("since_days")
    default_date = _as_date(defaults.get("since_date"))

    specs: list[TableSpec] = []
    for entry in cfg["tables"]:
        if isinstance(entry, str):
            specs.append(TableSpec(name=entry))
            continue
        if not isinstance(entry, dict) or "name" not in entry:
            raise SystemExit(f"bad table entry in tables.yml: {entry!r}")
        has_col = entry.get("since_column") is not None
        spec = TableSpec(
            name=entry["name"],
            since_column=entry.get("since_column"),
            since_days=entry.get("since_days", default_days if has_col else None),
            since_date=_as_date(entry.get("since_date")) or (default_date if has_col else None),
        )
        specs.append(spec)
    return cfg["source_schema"], cfg["target_schema"], specs


def connect_mssql() -> "pymssql.Connection":
    e = load_env(ENV_FILE)
    return pymssql.connect(
        server=e["SOURCE_DB_IP"],
        user=e["SOURCE_DB_USER"],
        password=e["SOURCE_DB_PASS"],
        database=e["SOURCE_DB"],
        tds_version="7.4",
    )


# --- type adaptation --------------------------------------------------------


def to_text(v: object) -> str | None:
    """Stringify a pymssql cell for an all-TEXT staging table.

    NULL stays NULL. bytes are utf-8 decoded if possible, hex otherwise.
    Everything else uses str(); Decimal, datetime, bool all serialize fine.
    """
    if v is None:
        return None
    if isinstance(v, bytes):
        try:
            return v.decode("utf-8")
        except UnicodeDecodeError:
            return v.hex()
    if isinstance(v, (datetime, date)):
        return v.isoformat()
    return str(v)


# --- replication ------------------------------------------------------------


def get_columns(mss_cur, schema: str, table: str) -> list[str]:
    mss_cur.execute(
        """
        SELECT COLUMN_NAME
          FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s
         ORDER BY ORDINAL_POSITION
        """,
        (schema, table),
    )
    return [r[0] for r in mss_cur.fetchall()]


def replicate_table(
    mss, pg, source_schema: str, target_schema: str, spec: TableSpec
) -> int:
    source_table = spec.name
    target_table = source_table.lower()
    label = f"{source_schema}.{source_table}"
    suffix = f"  [since {spec.window_label()}]" if spec.filtered else ""
    print(f"  {label:55s} -> {target_schema}.{target_table}{suffix}", flush=True)

    mss_cur = mss.cursor()
    cols = get_columns(mss_cur, source_schema, source_table)
    if not cols:
        print("    !!  table not found in source, skipping")
        return 0

    if spec.filtered and spec.since_column not in cols:
        raise SystemExit(
            f"  !!  since_column {spec.since_column!r} not found in {source_table}"
        )

    target_cols = [c.lower() for c in cols]
    if len(set(target_cols)) != len(target_cols):
        raise SystemExit(
            f"  !!  column case-collision in {source_table}: {cols}"
        )

    pg_cur = pg.cursor()
    cols_ddl = ",\n  ".join(f'"{c}" text' for c in target_cols)
    pg_cur.execute(
        f'DROP TABLE IF EXISTS {target_schema}."{target_table}"'
    )
    pg_cur.execute(
        f'CREATE TABLE {target_schema}."{target_table}" (\n  {cols_ddl}\n)'
    )

    select_cols = ", ".join(f"[{c}]" for c in cols)
    if spec.filtered:
        cutoff = spec.cutoff()
        mss_cur.execute(
            f"SELECT {select_cols} FROM [{source_schema}].[{source_table}] "
            f"WHERE [{spec.since_column}] >= %s",
            (cutoff,),
        )
    else:
        mss_cur.execute(
            f"SELECT {select_cols} FROM [{source_schema}].[{source_table}]"
        )

    target_col_list = ", ".join(f'"{c}"' for c in target_cols)
    copy_sql = (
        f'COPY {target_schema}."{target_table}" ({target_col_list}) FROM STDIN'
    )

    rows_total = 0
    t0 = time.time()
    with pg_cur.copy(copy_sql) as cp:
        while True:
            chunk = mss_cur.fetchmany(BATCH)
            if not chunk:
                break
            for row in chunk:
                cp.write_row(tuple(to_text(v) for v in row))
            rows_total += len(chunk)
            if rows_total and rows_total % (BATCH * 10) == 0:
                rate = rows_total / max(time.time() - t0, 1e-3)
                print(
                    f"      {rows_total:>12,} rows  ({rate:,.0f} rows/s)",
                    flush=True,
                )

    pg.commit()
    elapsed = time.time() - t0
    rate = rows_total / max(elapsed, 1e-3)
    print(
        f"    ok  {rows_total:>12,} rows  in {elapsed:>5.1f}s  ({rate:,.0f} rows/s)"
    )
    return rows_total


# --- main -------------------------------------------------------------------


def parse_args() -> argparse.Namespace:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument(
        "--only", nargs="+", metavar="TABLE",
        help="only replicate these tables (matches case-insensitively)",
    )
    ap.add_argument(
        "--skip", nargs="+", metavar="TABLE",
        help="skip these tables",
    )
    ap.add_argument(
        "--list", action="store_true",
        help="print the resolved table list and exit",
    )
    return ap.parse_args()


def filter_tables(
    specs: list[TableSpec], only: list[str] | None, skip: list[str] | None
) -> list[TableSpec]:
    if only:
        wanted = {t.lower() for t in only}
        specs = [s for s in specs if s.name.lower() in wanted]
        missing = wanted - {s.name.lower() for s in specs}
        if missing:
            print(f"warning: --only had no match for: {sorted(missing)}", file=sys.stderr)
    if skip:
        unwanted = {t.lower() for t in skip}
        specs = [s for s in specs if s.name.lower() not in unwanted]
    return specs


def main() -> None:
    args = parse_args()
    source_schema, target_schema, specs = load_yaml()
    specs = filter_tables(specs, args.only, args.skip)

    if args.list:
        for s in specs:
            print(s.label())
        print(f"\n{len(specs)} table(s)")
        return

    if not specs:
        print("no tables selected", file=sys.stderr)
        sys.exit(1)

    print(
        f"Replicating {len(specs)} table(s) from {source_schema} -> {target_schema}"
    )
    print(f"  source: ../.env.db")
    print(f"  target: {PG_URI}")
    print()

    mss = connect_mssql()
    grand_total = 0
    started = time.time()
    with psycopg.connect(PG_URI) as pg:
        with pg.cursor() as cur:
            cur.execute(f'CREATE SCHEMA IF NOT EXISTS "{target_schema}"')
        pg.commit()

        for spec in specs:
            try:
                grand_total += replicate_table(
                    mss, pg, source_schema, target_schema, spec
                )
            except Exception as exc:
                pg.rollback()
                print(f"    FAIL  {spec.name}: {exc}", file=sys.stderr)
                raise

    elapsed = time.time() - started
    print(
        f"\nDone. {grand_total:,} rows in {elapsed:,.1f}s "
        f"({grand_total / max(elapsed, 1e-3):,.0f} rows/s avg)."
    )


if __name__ == "__main__":
    main()

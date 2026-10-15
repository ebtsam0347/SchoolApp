"""
For each table marked "last N days" in tables.yml, list its date/datetime
columns plus min/max values, so we can pick the right `since_column`.
"""

from __future__ import annotations

from pathlib import Path

import pymssql

HERE = Path(__file__).parent

ENV_FILE = HERE.parent / ".env.db"

TABLES_TO_PROBE = [
    "PayRoll_payroll_attendance",
    "PayRoll_payroll_leaves",
    "PayRoll_payroll_shortleaves",
    "PayRoll_payroll_absentrecord",
    "Account_acc_salary_transactions",
    "Account_acc_permanent_advances",
    "Account_acc_permanent_advance_deductions",
    "Account_acc_short_term_advances",
    "Account_acc_short_term_advances_head",
    "Account_pessi_salary",
    "Account_overtime_record",
    "Account_overtime_calculation",
    "PayRoll_overtime_head",
    "PayRoll_payroll_overtime_request",
]

DATE_TYPES = {"date", "datetime", "datetime2", "smalldatetime", "datetimeoffset"}


def load_env():
    env = {}
    for line in ENV_FILE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        k, _, v = line.partition("=")
        env[k.strip()] = v.strip()
    return env


def main():
    e = load_env()
    conn = pymssql.connect(
        server=e["SOURCE_DB_IP"], user=e["SOURCE_DB_USER"],
        password=e["SOURCE_DB_PASS"], database=e["SOURCE_DB"],
        tds_version="7.4",
    )
    cur = conn.cursor()

    for tbl in TABLES_TO_PROBE:
        print(f"\n## {tbl}")
        cur.execute(
            """
            SELECT COLUMN_NAME, DATA_TYPE
              FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = %s
             ORDER BY ORDINAL_POSITION
            """,
            (tbl,),
        )
        cols = [(c, t) for c, t in cur.fetchall() if t.lower() in DATE_TYPES]
        if not cols:
            print("  (no date columns)")
            continue

        for col, typ in cols:
            try:
                cur.execute(
                    f"SELECT MIN([{col}]), MAX([{col}]), COUNT([{col}]) "
                    f"FROM [dbo].[{tbl}]"
                )
                mn, mx, cnt = cur.fetchone()
                print(f"  {col:30s} {typ:14s}  min={mn}  max={mx}  non_null={cnt}")
            except Exception as exc:
                print(f"  {col:30s} {typ:14s}  ERROR {exc}")

    conn.close()


if __name__ == "__main__":
    main()

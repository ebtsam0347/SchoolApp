"""
Profile every base table in the legacy MSSQL DB and write:
  - profile.csv: schema, table, rowcount, size_mb, last_user_update
  - triage.md:   tables bucketed by naming heuristics so we can quickly
                 separate junk from real domain tables.

Reads source creds from ../.env.db. Run from the migration/ dir:
    .venv/bin/python profile_source.py
"""

from __future__ import annotations

import csv
import os
import re
from dataclasses import dataclass
from pathlib import Path

import pymssql

HERE = Path(__file__).parent
ENV_FILE = HERE.parent / ".env.db"


def load_env(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        k, _, v = line.partition("=")
        out[k.strip()] = v.strip()
    return out


PROFILE_SQL = """
SELECT
    s.name                                                AS schema_name,
    t.name                                                AS table_name,
    SUM(CASE WHEN p.index_id IN (0, 1) THEN p.rows ELSE 0 END) AS row_count,
    CAST(SUM(a.total_pages) * 8.0 / 1024 AS DECIMAL(18, 2))    AS size_mb
FROM sys.tables t
JOIN sys.schemas    s ON s.schema_id  = t.schema_id
JOIN sys.partitions p ON p.object_id  = t.object_id
JOIN sys.allocation_units a ON a.container_id = p.partition_id
WHERE t.is_ms_shipped = 0
GROUP BY s.name, t.name
ORDER BY s.name, t.name
"""


@dataclass
class TableInfo:
    schema: str
    name: str
    rows: int
    size_mb: float

    @property
    def qname(self) -> str:
        return f"{self.schema}.{self.name}"


def fetch_tables() -> list[TableInfo]:
    env = load_env(ENV_FILE)
    conn = pymssql.connect(
        server=env["SOURCE_DB_IP"],
        user=env["SOURCE_DB_USER"],
        password=env["SOURCE_DB_PASS"],
        database=env["SOURCE_DB"],
        tds_version="7.4",
    )
    cur = conn.cursor()
    cur.execute(PROFILE_SQL)
    rows = cur.fetchall()
    conn.close()

    tables: list[TableInfo] = []
    for r in rows:
        schema, name, rowcount, size_mb = r
        tables.append(
            TableInfo(
                schema=schema,
                name=name,
                rows=int(rowcount or 0),
                size_mb=float(size_mb or 0),
            )
        )
    return tables


# --- Naming heuristics -------------------------------------------------------

# Names we always keep — overrides any junk pattern below.
ALLOWLIST = {
    "tbl_ATT_LOG",  # raw device punches (1.3 GB) — biggest attendance source
}

JUNK_PATTERNS = [
    re.compile(r"^Table_?\d+", re.I),                # Table_1..Table_102
    re.compile(r"_(backup|bkp|bak)\d*$", re.I),
    re.compile(r"_logNew$", re.I),
    re.compile(r"_log_temp$", re.I),
    re.compile(r"^tbl_log_file$", re.I),
    re.compile(r"_(temp|tmp)\d*$", re.I),
    re.compile(r"^temp_", re.I),
    re.compile(r"^tbl_TEMP", re.I),
    re.compile(r"^TEMP", re.I),
    re.compile(r"_(test)\d*$", re.I),
    re.compile(r"placeholder", re.I),
    re.compile(r"_Compliance\d+$"),                  # numbered Compliance copies (1, 2, …) — keep bare _Compliance
    re.compile(r"_(april|may|june|july|august|september|october|november|december|january|february|march)_?\d{4}", re.I),
    re.compile(r"^Emp_Attendance_(January|February|March|April|May|June|July|August|September|October|November|December|test)\d{4}$", re.I),
    re.compile(r"^tbl_(FEB|APRIL|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER|JANUARY|MARCH|MAY)", re.I),
    re.compile(r"_2024_2025$", re.I),
    re.compile(r"_for_report", re.I),
    re.compile(r"^waqar$|^tt$|^student$|^Tbl_zainDuration$|^tbl_Ammar_Temp$|^tbl_KK$|^tbl_DDD$|^tbl_TEMPPP$", re.I),
    re.compile(r"^Table_Faiz_SSNO$|^Table_SSNO$|^Table_tempCard$|^Table_Pending_Arears$|^Table_INC$|^Table_2_INC$|^Table_70_bonus$|^Table_50_(gradeUpdate|prevGrade)$|^Table_78_GRADE_OLD$|^Table_47_Security_Petrolium.*$|^Table_4[89]_Security_Petrolium$|^Table_IT_Systems$", re.I),
    re.compile(r"_calculation_errors$", re.I),
    re.compile(r"_estimate_errors$", re.I),
    re.compile(r"_upload_errors$", re.I),
    re.compile(r"managementallowancesbackup", re.I),
    re.compile(r"^view_", re.I),                     # a view leaked in via base-table list
    re.compile(r"^sysdiagrams$", re.I),
    # Scratch / unnamed working tables found in review bucket
    re.compile(r"^tbl$", re.I),
    re.compile(r"^tbl_[12]$", re.I),
    re.compile(r"^tbl_Sal$|^tbl_SAL_1$|^tbl_Acc_Salary$|^Account_acc_salary_transactions1$|^apis_salary_combined_1$", re.I),
    re.compile(r"^tbl_HRM_Emp_(ATT1?|Info1)$", re.I),
    re.compile(r"^tbl_OT(_Adj|_ADJ_DATA|_N(_(11|2|))?)?$", re.I),
    re.compile(r"^tbj_OT(_1)?$", re.I),
    re.compile(r"^tbl_ot_req_n$", re.I),
    re.compile(r"^TBL_OT_REQ$", re.I),
    re.compile(r"^tbl_extraOT$", re.I),
    re.compile(r"^Tbl_Over60$", re.I),
    re.compile(r"^Tbl_LastWorkingDate$", re.I),
    re.compile(r"^Tbl_placeholder1?$", re.I),
    re.compile(r"^tbl_SALPROB$|^tbl_SSC$|^tbl_GRAD_LOGG$|^tbl_LG_Comp_INFO$|^tbl_Comp_INFO_GRADE_LOG$|^tbl_Grade_LOG_TEMP$|^tbl_Log_Grade$|^tbl_Salary_(EMP_LOG|Trans_Log|September)$|^tbl_PayRoll_LV_LOG$|^tbl_dept_sec_desig_log$|^tbl_DDD$|^tbl_Hrm_ExtraWorkingDays$", re.I),
    re.compile(r"^tbl_Emp_(REQ|REQ_New|Attendance_Final03|Attendance_FinalCopy(3March)?)$", re.I),
    re.compile(r"^tbl_BC_ATT$|^tbl_ATT_(OCTOBER|SEPTEMBER|Novemvber)$|^tbl_ANNUAL_LEAVES$|^tbl_EMP_BANk$|^Tbl_Inv_Items$|^Tbl_Inv_TranIN$|^Ab_Rec$|^Table_salarytransact$|^Account_accountrfdpay$|^Account_acc_salary_transactions_ADJ$", re.I),
    re.compile(r"^tbl_working_hour_reports_with_Cards", re.I),  # report scratch
    re.compile(r"^tbl_monthly_record_backup", re.I),
    re.compile(r"^tbl_OCtober_2024", re.I),
    re.compile(r"^tbl_OCTOBER_2024", re.I),
    re.compile(r"^TEMPPfeb_2026TEMP$", re.I),
    re.compile(r"^tbl_main_Compliance$", re.I),
    re.compile(r"^managementallowancesbackup$", re.I),
    re.compile(r"^tbl_bonus_backup$", re.I),
]

# Change-history / audit-log tables — domain-relevant but separate decision (you may
# not want to migrate audit history into a fresh audit service).
CHANGE_HISTORY_PATTERNS = [
    re.compile(r"^apis_(department|designation|empcompanyinfo|empReportTo|GradeLog|ShiftLog|salary_log|salary_combined_log|section)_log$", re.I),
    re.compile(r"^apis_GradeLog$", re.I),
    re.compile(r"^apis_ShiftLog$", re.I),
    re.compile(r"^apis_salary_log$", re.I),
    re.compile(r"^apis_salary_combined_log$", re.I),
    re.compile(r"^Account_acctermination_log$", re.I),
    re.compile(r"^PayRoll_payroll_attendance_log$", re.I),
    re.compile(r"^PayRoll_payroll_leaves_log$", re.I),
    re.compile(r"^Documentum_.*log$", re.I),
]

FRAMEWORK_PATTERNS = [
    re.compile(r"^django_"),
    re.compile(r"^auth_"),
    re.compile(r"^authtoken_"),
    re.compile(r"^Login_"),
    re.compile(r"^TaskScheduling_"),
    re.compile(r"^TriggerDeleteLog$"),
    re.compile(r"^AllowedDeleters$"),
    re.compile(r"^SourceTable$"),
    re.compile(r"^tbl_log_file$"),
    re.compile(r"^tbl_Login_Track$"),
]

# Domain buckets — first match wins. Patterns are intentionally conservative;
# anything that doesn't match lands in "review".
DOMAIN_BUCKETS: list[tuple[str, list[re.Pattern]]] = [
    ("employee_master", [
        re.compile(r"^apis_employeinfo$", re.I),
        re.compile(r"^apis_empcompanyinfo$", re.I),
        re.compile(r"^apis_empeducation$", re.I),
        re.compile(r"^apis_emp_doc_info$", re.I),
        re.compile(r"^apis_empsuspendinfo$", re.I),
        re.compile(r"^apis_experience$", re.I),
        re.compile(r"^employees?$", re.I),
        re.compile(r"^TerminationInfo$", re.I),
        re.compile(r"^users$", re.I),
    ]),
    ("catalog_lookups", [
        re.compile(r"^apis_(department|section|designations|addgrade|addqualification|adddisability|addnoticereasons|addnoticetype|city|country|company|companybranch|shift|bankdetail|employeecategory|reason|skills|certificate|education)$", re.I),
        re.compile(r"^apis_(financialperiod|appauthority)$", re.I),
    ]),
    ("attendance", [
        re.compile(r"^PayRoll_payroll_attendance(_\d)?$", re.I),
        re.compile(r"^PayRoll_payroll_attendance_log$", re.I),
        re.compile(r"^PayRoll_payroll_deviceinfo$", re.I),
        re.compile(r"^PayRoll_payroll_securityroster$", re.I),
        re.compile(r"^PayRoll_regularization$", re.I),
        re.compile(r"^Tbl_Att_devices$", re.I),
        re.compile(r"^tbl_ATT_LOG$", re.I),
        re.compile(r"^tbl_Attendance_(Transactions|Short)$", re.I),
        re.compile(r"^Tbl_Attendance_PreCopy$", re.I),
        re.compile(r"^liveAttendance$", re.I),
        re.compile(r"^NewAttendanceTable_2024_2025$", re.I),  # candidate, but already matches junk pattern; left here as fallback
    ]),
    ("leaves", [
        re.compile(r"^PayRoll_payroll_leaves$", re.I),
        re.compile(r"^PayRoll_payroll_leaves_log$", re.I),
        re.compile(r"^PayRoll_payroll_shortleaves$", re.I),
        re.compile(r"^Account_account_annualleavetransactions$", re.I),
        re.compile(r"^apis_hrm_hr_leavesquota$", re.I),
        re.compile(r"^apis_hrm_hr_holidays$", re.I),
        re.compile(r"^Leaves_Compliance$", re.I),
        re.compile(r"^Payroll_Leaves_Compliance$", re.I),
        re.compile(r"^Payroll_Absent_Compliance$", re.I),
        re.compile(r"^PayRoll_payroll_absentrecord$", re.I),
        re.compile(r"^PayRoll_meal$", re.I),
    ]),
    ("payroll_salary", [
        re.compile(r"^apis_salary(_combined|_ded|_policy|_log)?$", re.I),
        re.compile(r"^apis_managementallowances(transaction)?$", re.I),
        re.compile(r"^manageAllEmpAllow$", re.I),
        re.compile(r"^Account_acc_salary_transactions$", re.I),
        re.compile(r"^Account_account_bonusno(setting)?$", re.I),
        re.compile(r"^Account_account_bonustransactions$", re.I),
        re.compile(r"^Account_acctermination$", re.I),
        re.compile(r"^Account_pessi_salary$", re.I),
        re.compile(r"^Account_acc_permanent_advance(_deductions|s)?$", re.I),
        re.compile(r"^Account_acc_short_term_advances(_head)?$", re.I),
        re.compile(r"^Account_acc_employee_grade_history$", re.I),
        re.compile(r"^Account_overtime_(record|calculation)$", re.I),
        re.compile(r"^PayRoll_overtime_(head|detail)$", re.I),
        re.compile(r"^PayRoll_payroll_overtime_request(_detail)?$", re.I),
    ]),
    ("misc_hr", [
        re.compile(r"^apis_hr_(empgroup|group|group_members)$", re.I),
        re.compile(r"^apis_(blooddonner|contact|bankissue|bankissuecat|bankissuesolutions|busowner|busroute|businfo|card|card1)$", re.I),
        re.compile(r"^apis_(financialperiod)$", re.I),
        re.compile(r"^Visitors_factoryduty(detail|extend|head)$", re.I),
        re.compile(r"^tbl_Hrm_kaizen_activity_record(_users)?$", re.I),
        re.compile(r"^tbl_Hrm_ExtraWorkingDays$", re.I),
        re.compile(r"^tbl_Working_Day$", re.I),
        re.compile(r"^tbl_HRM_Shift$", re.I),
        re.compile(r"^tbl_user_permissions$", re.I),
        re.compile(r"^tbl_Prayer_(Class|ClassMember|Counter)$", re.I),
        re.compile(r"^tbl_Medical_", re.I),
        re.compile(r"^tbl_MedicineUnits$", re.I),
        re.compile(r"^Tbl_Med_Wastage$", re.I),
        re.compile(r"^FairPrice_", re.I),
    ]),
    ("non_hr_modules", [
        re.compile(r"^article_", re.I),
        re.compile(r"^material_", re.I),
        re.compile(r"^production_order_", re.I),
        re.compile(r"^purchase_order_", re.I),
        re.compile(r"^settings_", re.I),
        re.compile(r"^warehouse_", re.I),
        re.compile(r"^fit_", re.I),
        re.compile(r"^Tbl_Inv_", re.I),
        # Access control / facility / welfare modules — out of FSHRMS scope
        re.compile(r"^tbl_ANPR_", re.I),
        re.compile(r"^tbl_(visitors|vehi)_", re.I),
        re.compile(r"^Visitors_factoryduty", re.I),
        re.compile(r"^tbl_Canteen_", re.I),
        re.compile(r"^tbl_Hrm_kaizen", re.I),
        re.compile(r"^tbl_Prayer_", re.I),
        re.compile(r"^tbl_Medical_", re.I),
        re.compile(r"^tbl_MedicineUnits$", re.I),
        re.compile(r"^Tbl_Med_Wastage$", re.I),
        re.compile(r"^FairPrice_", re.I),
        re.compile(r"^apis_(blooddonner|busowner|busroute|businfo|bankissue|bankissuecat|bankissuesolutions|card1?|contact|hr_(empgroup|group|group_members))$", re.I),
        re.compile(r"^Assets_", re.I),
        re.compile(r"^Documentum_", re.I),  # all empty
        re.compile(r"^Evaluation_|^Eva_Comp_|^apis_evaluationkpi|^apis_rewardpolicy$", re.I),
        re.compile(r"^apis_requisition|^apis_requisitiongrade$|^apis_noticeinfo$|^apis_announcement$|^apis_employeequery$", re.I),
        re.compile(r"^tbl_HRM_Shift$|^tbl_Working_Day$|^tbl_user_permissions$", re.I),
        re.compile(r"^userdata_", re.I),
        re.compile(r"^temp_account$", re.I),
    ]),
]


def classify(t: TableInfo) -> str:
    name = t.name
    if name in ALLOWLIST:
        # Force into the most relevant bucket — currently only the attendance log
        return "attendance"
    if any(p.search(name) for p in CHANGE_HISTORY_PATTERNS):
        return "change_history"
    if any(p.search(name) for p in JUNK_PATTERNS):
        return "junk"
    if any(p.search(name) for p in FRAMEWORK_PATTERNS):
        return "framework"
    for bucket, patterns in DOMAIN_BUCKETS:
        if any(p.search(name) for p in patterns):
            return bucket
    return "review"


def write_csv(tables: list[TableInfo], path: Path) -> None:
    with path.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["schema", "table", "rows", "size_mb", "bucket"])
        for t in tables:
            w.writerow([t.schema, t.name, t.rows, f"{t.size_mb:.2f}", classify(t)])


def write_triage(tables: list[TableInfo], path: Path) -> None:
    by_bucket: dict[str, list[TableInfo]] = {}
    for t in tables:
        by_bucket.setdefault(classify(t), []).append(t)

    order = [
        "employee_master", "catalog_lookups", "attendance", "leaves",
        "payroll_salary", "misc_hr", "change_history",
        "review",
        "non_hr_modules", "framework", "junk",
    ]
    counts = {b: len(by_bucket.get(b, [])) for b in order}
    total = sum(counts.values())

    lines: list[str] = []
    lines.append(f"# Legacy DB triage — {total} base tables\n")
    lines.append("Buckets are heuristic. `review` is what the script couldn't auto-classify and most needs your eyes.\n")
    lines.append("`non_hr_modules`, `framework`, `junk` are exclusion candidates by default.\n\n")
    lines.append("## Summary\n\n")
    lines.append("| Bucket | Tables |\n|---|---:|\n")
    for b in order:
        lines.append(f"| {b} | {counts[b]} |\n")
    lines.append(f"| **total** | **{total}** |\n\n")

    for b in order:
        rows = sorted(by_bucket.get(b, []), key=lambda r: (-r.rows, r.name.lower()))
        if not rows:
            continue
        lines.append(f"## {b} ({len(rows)})\n\n")
        lines.append("| Table | Rows | Size MB |\n")
        lines.append("|---|---:|---:|\n")
        for r in rows:
            lines.append(f"| `{r.qname}` | {r.rows:,} | {r.size_mb:,.1f} |\n")
        lines.append("\n")

    path.write_text("".join(lines))


def main() -> None:
    print("Connecting to legacy MSSQL...")
    tables = fetch_tables()
    print(f"  fetched {len(tables)} tables")

    csv_path = HERE / "profile.csv"
    md_path = HERE / "triage.md"
    write_csv(tables, csv_path)
    write_triage(tables, md_path)
    print(f"  wrote {csv_path.relative_to(HERE.parent)}")
    print(f"  wrote {md_path.relative_to(HERE.parent)}")

    by_bucket: dict[str, int] = {}
    for t in tables:
        by_bucket[classify(t)] = by_bucket.get(classify(t), 0) + 1
    print("\nBucket counts:")
    for b, n in sorted(by_bucket.items(), key=lambda x: -x[1]):
        print(f"  {b:28s} {n:4d}")


if __name__ == "__main__":
    main()

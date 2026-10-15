"""
ESS provisioning for legacy employees who had a Django login.

Replaces the SQL-only 07: hashing 2,500 passwords with bcrypt cost 12
inside one SQL statement is single-threaded and takes ~12 minutes. Cost 12
is ~0.3s per hash BY DESIGN — that slowness is what makes a stolen hash
table expensive to crack, so the cost stays. What changes is doing it
across all cores instead of one Postgres backend: ~12 min -> under 2.

Passwords come from the legacy export (--passwords CSV, columns
`username,decrypted_password`) so staff keep the password they already
use and cutover doesn't feel like a reset.

ELIGIBILITY IS THE CSV. An ACTIVE employee gets an ESS account iff their
employee_code appears in the password file. The obvious-looking
alternative — `apis_employeinfo.loginname_id IS NOT NULL`, which the
original SQL 07 used — is wrong: loginname_id is not per-employee (2,325
employees share the value 4181), so it is a shared FK, not "this person
has a login". The two criteria overlapped on only 547 of ~2,600 people;
the rule would have denied accounts to 2,050 staff who have a working
password today. The CSV is the account list.

  - Postgres:  PGURI env var
  - Passwords: --passwords (required)

TERMINATED employees do NOT get ESS (FSHRMS termination policy) even if
they appear in the file.

NOTE ON POLICY. Imported passwords bypass `PasswordPolicy.validate` (10+
chars, upper+lower+digit, no banned terms, must not contain the username)
because login does not re-validate. Passwords that DO comply are imported
with must_change_pw=false — nothing changes for that user. Passwords that
do NOT comply get must_change_pw=true, so those users are made to pick a
compliant one at first login rather than hitting the rule later at a
voluntary change and reading it as a fault. Run with --report-policy for
the breakdown, and --write-forced-list to get the affected codes for HR.

Usage:
  .venv/bin/python 07_ess_provisioning.py --passwords /path/pw.csv --dry-run
  .venv/bin/python 07_ess_provisioning.py --passwords /path/pw.csv
"""

from __future__ import annotations

import argparse
import csv
import os
import secrets
import sys
from concurrent.futures import ProcessPoolExecutor

import bcrypt
import psycopg

PG_URI = os.environ.get("PGURI", "postgresql://postgres:postgres@localhost:5432/fshrms")
BCRYPT_ROUNDS = 12                                   # CLAUDE.md: locked
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"        # no I/O/0/1 for printed slips
RANDOM_LEN = 10

BANNED = ("password", "changeme", "qwerty", "12345678", "letmein",
          "welcome", "admin", "forwardsports", "fshrms")


def random_password() -> str:
    return "".join(secrets.choice(ALPHABET) for _ in range(RANDOM_LEN))


def hash_one(pw: str) -> str:
    """Match auth's DelegatingPasswordEncoder format exactly.

    auth wires `new DelegatingPasswordEncoder("bcrypt", ...)`, which STORES
    hashes prefixed `{bcrypt}`. It still *matches* a bare `$2a$`/`$2b$`
    hash today only because AuthCryptoConfig also calls
    setDefaultPasswordEncoderForMatches(bcrypt) — drop that one line and
    every unprefixed hash this script wrote stops authenticating. Write the
    prefix so the rows are indistinguishable from ones the service made.
    """
    return "{bcrypt}" + bcrypt.hashpw(pw.encode("utf-8"),
                                      bcrypt.gensalt(BCRYPT_ROUNDS)).decode()


def policy_violations(pw: str, username: str) -> list[str]:
    """Mirrors auth's PasswordPolicy so we can report, not enforce."""
    v = []
    if len(pw) < 10:
        v.append("too_short")
    if not (any(c.isupper() for c in pw) and any(c.islower() for c in pw)
            and any(c.isdigit() for c in pw)):
        v.append("missing_char_class")
    lc = pw.lower()
    if any(b in lc for b in BANNED):
        v.append("banned_term")
    if username and username.lower() in lc:
        v.append("contains_username")
    return v


def load_passwords(path: str) -> dict[str, str]:
    out: dict[str, str] = {}
    with open(path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            u = (row.get("username") or "").strip()
            p = row.get("decrypted_password") or ""
            if u and p.strip():
                out[u] = p
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--passwords", required=True,
                    help="CSV: username,decrypted_password (this is the eligibility list)")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--report-policy", action="store_true",
                    help="report PasswordPolicy violations in the CSV and exit")
    ap.add_argument("--write-forced-list", metavar="PATH",
                    help="write employee codes forced to change at first login (for HR)")
    args = ap.parse_args()

    supplied = load_passwords(args.passwords)
    print(f"{len(supplied)} passwords loaded from {args.passwords}")

    if args.report_policy:
        import collections
        c = collections.Counter()
        clean = 0
        for u, p in supplied.items():
            v = policy_violations(p, u)
            if v:
                for x in v:
                    c[x] += 1
            else:
                clean += 1
        print(f"meet PasswordPolicy : {clean} ({clean/max(len(supplied),1)*100:.1f}%)")
        print(f"violate             : {len(supplied)-clean}")
        for k, n in c.most_common():
            print(f"   {k:<20} {n}")
        return 0

    with psycopg.connect(PG_URI) as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM auth.roles WHERE code = 'EMPLOYEE'")
            row = cur.fetchone()
            if not row:
                sys.exit("EMPLOYEE role not found — run auth's Flyway migrations first")
            employee_role_id = row[0]

            # Eligibility is the password file: ACTIVE employees whose code
            # appears in it. See the module docstring for why loginname_id
            # is not usable here.
            cur.execute("""
                SELECT e.id, e.employee_code, e.full_name
                  FROM employee.employees e
                 WHERE e.employment_status = 'ACTIVE'
                   AND e.user_id IS NULL
                   AND e.employee_code = ANY(%s)
                 ORDER BY e.id
            """, (list(supplied.keys()),))
            eligible = cur.fetchall()

        print(f"{len(eligible)} eligible employees")
        if not eligible:
            print("nothing to do")
            return 0

        # (employee_id, code, full_name, plaintext, must_change_pw)
        plans = []
        forced = []
        for emp_id, code, name in eligible:
            pw = supplied[code]
            violations = policy_violations(pw, code)
            if violations:
                forced.append((code, ",".join(violations)))
            plans.append((emp_id, code, name, pw, bool(violations)))

        print(f"  keep existing password : {len(plans)-len(forced)}  (must_change_pw=false)")
        print(f"  forced change at login : {len(forced)}  (password breaks PasswordPolicy)")

        if args.write_forced_list and forced:
            with open(args.write_forced_list, "w", newline="") as f:
                w = csv.writer(f)
                w.writerow(["employee_code", "reasons"])
                w.writerows(sorted(forced))
            print(f"  forced-change list -> {args.write_forced_list}")

        if args.dry_run:
            print("\ndry run — nothing written")
            return 0

        print(f"\nhashing {len(plans)} passwords with bcrypt cost {BCRYPT_ROUNDS} "
              f"across {os.cpu_count()} cores...", flush=True)
        with ProcessPoolExecutor() as pool:
            hashes = list(pool.map(hash_one, [p[3] for p in plans], chunksize=16))

        print("writing...", flush=True)
        with conn.cursor() as cur:
            cur.executemany("""
                INSERT INTO auth.users (username, full_name, email, password_hash,
                                        status, must_change_pw, employee_linked,
                                        created_by, updated_by)
                VALUES (%s, left(%s,128), NULL, %s, 'ACTIVE', %s, true,
                        'legacy-migration', 'legacy-migration')
            """, [(p[1], p[2], h, p[4]) for p, h in zip(plans, hashes)])

            cur.execute("""
                INSERT INTO auth.user_roles (user_id, role_id, granted_by)
                SELECT u.id, %s, 'legacy-migration'
                  FROM auth.users u WHERE u.created_by = 'legacy-migration'
                    AND NOT EXISTS (SELECT 1 FROM auth.user_roles r
                                     WHERE r.user_id = u.id AND r.role_id = %s)
            """, (employee_role_id, employee_role_id))

            cur.execute("""
                UPDATE employee.employees e
                   SET user_id = u.id, updated_at = e.updated_at,
                       updated_by = 'legacy-migration'
                  FROM auth.users u
                 WHERE u.username = e.employee_code
                   AND u.created_by = 'legacy-migration'
                   AND e.user_id IS NULL
            """)

            # Nothing is written to migration.ess_initial_passwords: every
            # user here keeps a password they already know, so there is
            # nothing to hand out and no reason to put live credentials on
            # disk. The table stays empty unless a random-password path is
            # reintroduced.
        conn.commit()

        with conn.cursor() as cur:
            cur.execute("SELECT count(*) FROM auth.users WHERE created_by='legacy-migration'")
            users, = cur.fetchone()
            cur.execute("SELECT count(*) FROM employee.employees WHERE user_id IS NOT NULL")
            linked, = cur.fetchone()
            cur.execute("SELECT count(*) FROM auth.users "
                        "WHERE created_by='legacy-migration' AND must_change_pw")
            forced_n, = cur.fetchone()
        print(f"\nusers created          : {users}")
        print(f"employees linked       : {linked}")
        print(f"forced change at login : {forced_n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

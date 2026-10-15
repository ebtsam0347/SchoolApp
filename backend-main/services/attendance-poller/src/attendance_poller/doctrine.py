"""§5.5 doctrine enforcement for attendance-poller.

Bans timezone APIs that contradict the trust-server doctrine
(CLAUDE.md §0.5.B): the poller, the JVM, Postgres, and the host all
run TZ=Asia/Karachi end-to-end, so a naive ``datetime.now()`` returns
the correct wall-clock value. Any ``tz=`` argument or ``astimezone``
call is wrong.

Ruff's built-in ``flake8-datetimez`` (DTZ) rules push the OPPOSITE
convention, so we can't just enable them. This script is the
enforcement substitute — wire it into CI.

Usage::

    attendance-poller-doctrine

Exit code 0 = clean, 1 = violations (printed with file:line).
Fixtures and tests are excluded.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

# Banned patterns. Conservative regexes so a comment containing the
# banned string doesn't fire (the # prefix excludes the line entirely).
_BANNED = (
    (re.compile(r"^[^#]*\bdatetime\.now\(.*\btz\s*="), "datetime.now(tz=...)"),
    (re.compile(r"^[^#]*\bdatetime\.utcnow\("), "datetime.utcnow()"),
    (re.compile(r"^[^#]*\.astimezone\("), ".astimezone(...)"),
    (re.compile(r"^[^#]*\bZoneInfo\("), "ZoneInfo(...)"),
    (re.compile(r"^[^#]*\bpytz\."), "pytz.*"),
)

_SKIP_DIRS = {"__pycache__", "fixtures", "tests"}


def _scan(root: Path) -> list[str]:
    violations: list[str] = []
    for path in sorted(root.rglob("*.py")):
        if any(part in _SKIP_DIRS for part in path.parts):
            continue
        if path.name == "doctrine.py":
            # The script itself names the banned patterns in regexes.
            continue
        text = path.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), start=1):
            stripped = line.strip()
            if stripped.startswith("#"):
                continue
            for pattern, label in _BANNED:
                if pattern.search(line):
                    violations.append(f"{path}:{lineno}: {label} — {stripped}")
                    break
    return violations


def main() -> int:
    here = Path(__file__).resolve().parent
    violations = _scan(here)
    if violations:
        print("§5.5 doctrine violations (timezone APIs banned):", file=sys.stderr)
        for v in violations:
            print(f"  {v}", file=sys.stderr)
        print(
            "\nThe poller runs with TZ=Asia/Karachi end-to-end (CLAUDE.md §0.5.B).\n"
            "Use naive `datetime.now()` and `LocalDate`-shaped values.",
            file=sys.stderr,
        )
        return 1
    print("attendance-poller §5.5 doctrine check: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())

// Canonical date formatters used across modules.
//
// Three rendering shapes; pick by intent:
//   formatDateShort     "12 Apr 2026"      — most table cells, dense rows
//   formatDateLong      "Mon, 12 Apr 2026" — page headers, summaries
//   formatDateMonthDay  "Mon, 12 Apr"      — current-context UIs (this-month
//                                            inbox rows, day strips) where
//                                            the year is implied
//   formatDateTime      "12 Apr 2026 14:30"
//   formatDateNumeric / formatDateTimeNumeric
//                       "12/04/2026" / "12/04/2026 14:30" — dd/mm/yyyy screens
//
// Wire-time policy: every HR-domain timestamp on the wire is a Java
// `LocalDateTime` — bare ISO `YYYY-MM-DDTHH:mm:ss` with no `Z` and no offset.
// The server's wall clock IS the truth; we never convert. formatDateTime
// renders the literal numbers from the string so the display is identical
// regardless of the browser's timezone, and `toLocalDateTime` produces the
// matching bare-ISO shape when posting back. Don't reach for `.toISOString()`
// on a Dayjs holding a LocalDateTime value — it inserts a UTC shift the
// server can't deserialize.
//
// Why these names live in @/lib and not in /attendance and /payroll:
// previously those two modules each defined `formatShortDate` / `formatLongDate`
// with *different* outputs, which made the wrong-import bug invisible. The
// names below are unambiguous; do not reintroduce a "short" / "long" pair
// inside a feature module.

import type { Dayjs } from 'dayjs';

const MONTHS_SHORT = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

const BARE_DATETIME_RE = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/;

const SHORT_DATE = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

const LONG_DATE = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

const MONTH_DAY = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

const DATE_TIME = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** "12 Apr 2026". */
export function formatDateShort(iso: string | null | undefined): string {
  const d = toDate(iso);
  return d ? SHORT_DATE.format(d) : '—';
}

/** "Mon, 12 Apr 2026". */
export function formatDateLong(iso: string | null | undefined): string {
  const d = toDate(iso);
  return d ? LONG_DATE.format(d) : '—';
}

/** "Mon, 12 Apr". */
export function formatDateMonthDay(iso: string | null | undefined): string {
  const d = toDate(iso);
  return d ? MONTH_DAY.format(d) : '—';
}

/** "12 Apr 2026 14:30". For bare `LocalDateTime` strings the literal HH:mm
 * is rendered verbatim — no timezone conversion. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const bare = BARE_DATETIME_RE.exec(iso);
  if (bare) {
    const [, y, mo, d, h, mi] = bare;
    return `${Number(d)} ${MONTHS_SHORT[Number(mo) - 1]} ${y} ${h}:${mi}`;
  }
  const dt = new Date(iso);
  return Number.isNaN(dt.getTime()) ? '—' : DATE_TIME.format(dt);
}

/** "12/04/2026". */
export function formatDateNumeric(iso: string | null | undefined): string {
  const d = toDate(iso);
  if (!d) return '—';
  const day = String(d.getDate()).padStart(2, '0');
  const mo = String(d.getMonth() + 1).padStart(2, '0');
  return `${day}/${mo}/${d.getFullYear()}`;
}

/** "12/04/2026 14:30". Same no-conversion rule as formatDateTime. */
export function formatDateTimeNumeric(iso: string | null | undefined): string {
  if (!iso) return '—';
  const bare = BARE_DATETIME_RE.exec(iso);
  if (bare) {
    const [, y, mo, d, h, mi] = bare;
    return `${d}/${mo}/${y} ${h}:${mi}`;
  }
  const dt = new Date(iso);
  if (Number.isNaN(dt.getTime())) return '—';
  const hh = String(dt.getHours()).padStart(2, '0');
  const mm = String(dt.getMinutes()).padStart(2, '0');
  return `${formatDateNumeric(iso)} ${hh}:${mm}`;
}

/** Format a Dayjs as a bare `LocalDateTime` string (`YYYY-MM-DDTHH:mm:ss`)
 * for posting to the server. Use this instead of `.toISOString()` for any
 * field the backend models as `LocalDateTime` — `.toISOString()` shifts the
 * wall clock into UTC and appends `Z`, which Jackson's `LocalDateTime`
 * deserializer rejects (and which silently re-zones the stored time if it
 * doesn't). */
export function toLocalDateTime(d: Dayjs): string {
  return d.format('YYYY-MM-DDTHH:mm:ss');
}

/** Parse a YYYY-MM-DD or full ISO into a local-noon Date so DST never bites. */
export function parseDateOnly(iso: string): Date | null {
  if (!iso) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (m) {
    const [, y, mo, d] = m;
    return new Date(Number(y), Number(mo) - 1, Number(d), 12, 0, 0);
  }
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** YYYY-MM-DD in local time (the format every attendance endpoint uses). */
export function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** YYYY-MM. */
export function toIsoMonth(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/** Whole calendar days from now until `iso` (negative = past). */
export function daysUntil(iso: string | Date | null | undefined): number {
  const t = iso instanceof Date ? iso : (iso ? toDate(iso) : null);
  if (!t) return 0;
  // Strip time-of-day on both sides so 23:59 vs 00:01 don't flip the result.
  const target0 = new Date(t.getFullYear(), t.getMonth(), t.getDate()).getTime();
  const now = new Date();
  const now0 = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  return Math.round((target0 - now0) / MS_PER_DAY);
}

/** Whole calendar days from `iso` to now (negative = future). Floors at 0. */
export function daysSince(iso: string | Date | null | undefined): number {
  return Math.max(0, -daysUntil(iso));
}

/** Inclusive calendar-day span from `from` to `to` (≥1 when both valid). */
export function daysBetween(
  from: string | Date | null | undefined,
  to: string | Date | null | undefined,
): number {
  const a = from instanceof Date ? from : (from ? toDate(from) : null);
  const b = to instanceof Date ? to : (to ? toDate(to) : null);
  if (!a || !b) return 0;
  const a0 = new Date(a.getFullYear(), a.getMonth(), a.getDate()).getTime();
  const b0 = new Date(b.getFullYear(), b.getMonth(), b.getDate()).getTime();
  return Math.round((b0 - a0) / MS_PER_DAY) + 1;
}

function toDate(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  // Date-only strings → parse via parseDateOnly so we never drift from
  // server-stored YYYY-MM-DD.
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) return parseDateOnly(iso);
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

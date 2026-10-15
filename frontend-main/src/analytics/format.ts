// Safe formatters for the analytics pages. The service-direct DTOs are
// modelled from the FE-facing spec doc rather than the live OpenAPI, so
// field names can drift — `presentCount` on the scorecard is the
// classic example. Rather than litter every callsite with `?? 0`, we
// route every value through one of these helpers that returns "—" when
// the input isn't a finite number.
//
// Tradeoff: a missing field reads as "—" instead of "0", which is the
// more honest UX (we don't know the value, we shouldn't pretend zero).
// Once the live OpenAPI lands and we lock the types, callers can drop
// these helpers and use the raw values directly.

const PK = 'en-PK';

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

/** `1,234` for whole-number counts. */
export function formatCount(value: unknown): string {
  return isFiniteNumber(value)
    ? value.toLocaleString(PK)
    : '—';
}

/** `1.2 h` for hour totals. */
export function formatHours(value: unknown): string {
  return isFiniteNumber(value) ? `${value.toFixed(1)} h` : '—';
}

/** `12.34%` — pass `digits` to override the fraction count. */
export function formatPct(value: unknown, digits = 2): string {
  return isFiniteNumber(value) ? `${value.toFixed(digits)}%` : '—';
}

/** `Rs 1.2M` compact, or `—`. */
export function formatMoneyCompact(value: unknown): string {
  if (!isFiniteNumber(value)) return '—';
  return new Intl.NumberFormat(PK, {
    style: 'currency',
    currency: 'PKR',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}

/** `Rs 12,345` non-compact, or `—`. */
export function formatMoneyExact(value: unknown): string {
  if (!isFiniteNumber(value)) return '—';
  return new Intl.NumberFormat(PK, {
    style: 'currency',
    currency: 'PKR',
    maximumFractionDigits: 0,
  }).format(value);
}

/** Compact axis ticks for currency (`1.2M`, `4k`). */
export function formatCompactNumber(value: unknown): string {
  if (!isFiniteNumber(value)) return '';
  return new Intl.NumberFormat(PK, {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}

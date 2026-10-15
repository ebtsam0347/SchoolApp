import type { CSSProperties } from 'react';

import { LOCALE } from '@/config/locale';
import { daysUntil } from '@/lib/dates';
import { StatusPill, type StatusPillTone } from './StatusPill';

export type ExpiryBadgeProps = {
  /** Target date (expiry date for a cert, end date for probation). */
  date: Date | string;
  /**
   * "expiry" — the date is when something stops being valid (default).
   * "deadline" — the date is when something is due (probation due, etc.).
   * Wording switches: "Expired" vs "Overdue", "Expires in" vs "Due in".
   */
  mode?: 'expiry' | 'deadline';
  /** Force a specific tone instead of deriving from days-remaining. */
  toneOverride?: StatusPillTone;
  /** Show the formatted date instead of the relative phrase. */
  showDate?: boolean;
  /** Pass-through to StatusPill — use 'solid' on colored hero surfaces. */
  variant?: 'soft' | 'solid';
  className?: string;
  style?: CSSProperties;
};

function toDate(value: Date | string): Date {
  return value instanceof Date ? value : new Date(value);
}

function toneFromDays(days: number): StatusPillTone {
  if (days < 0) return 'error';
  if (days <= 7) return 'error';
  if (days <= 30) return 'warning';
  if (days <= 90) return 'info';
  return 'neutral';
}

function relativeLabel(days: number, mode: 'expiry' | 'deadline'): string {
  if (days < 0) {
    const ago = Math.abs(days);
    return mode === 'expiry'
      ? `Expired ${ago}d ago`
      : `Overdue · ${ago}d`;
  }
  if (days === 0) return mode === 'expiry' ? 'Expires today' : 'Due today';
  if (days === 1) return mode === 'expiry' ? 'Expires tomorrow' : 'Due tomorrow';
  return mode === 'expiry' ? `Expires in ${days}d` : `Due in ${days}d`;
}

const DATE_FORMATTER = new Intl.DateTimeFormat(LOCALE, {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
});

function formatDate(date: Date): string {
  return DATE_FORMATTER.format(date);
}

export function ExpiryBadge({
  date,
  mode = 'expiry',
  toneOverride,
  showDate = false,
  variant = 'soft',
  className,
  style,
}: ExpiryBadgeProps) {
  const target = toDate(date);
  const days = daysUntil(target);
  const tone = toneOverride ?? toneFromDays(days);
  const label = showDate ? formatDate(target) : relativeLabel(days, mode);

  return (
    <StatusPill
      tone={tone}
      size="sm"
      variant={variant}
      className={className}
      style={style}
    >
      {label}
    </StatusPill>
  );
}

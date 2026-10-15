// Wrap the BFF's per-day list in a 6-week Sunday-first grid, padding
// the leading + trailing days with `kind: 'OUT'` cells. The BFF's
// pre-merged `kind` is the source of truth for in-month days; padding
// cells are non-interactive.

import dayjs, { type Dayjs } from 'dayjs';
import { toIsoDate, type MonthDay } from '@/attendance';
import type { DayCell } from './types';

export function buildMonthGrid(monthDate: Date, bffDays: MonthDay[]): DayCell[] {
  const dayMap = new Map(bffDays.map((d) => [d.workDate, d]));
  const todayIso = toIsoDate(new Date());

  const firstOfMonth = new Date(monthDate);
  firstOfMonth.setDate(1);
  firstOfMonth.setHours(12, 0, 0, 0);
  const gridStart = new Date(firstOfMonth);
  gridStart.setDate(1 - firstOfMonth.getDay());

  const cells: DayCell[] = [];
  for (let i = 0; i < 42; i++) {
    const d = new Date(gridStart);
    d.setDate(gridStart.getDate() + i);
    const workDate = toIsoDate(d);
    const inMonth = d.getMonth() === monthDate.getMonth();
    const isFuture = workDate > todayIso;
    const bff = inMonth ? (dayMap.get(workDate) ?? null) : null;

    // Future in-month days are FUTURE regardless of what the backend sends —
    // backend returns null/missing for future dates, and we don't want those
    // to render as ABSENT.
    const kind: DayCell['kind'] = inMonth
      ? isFuture
        ? 'FUTURE'
        : (bff?.kind ?? 'ABSENT')
      : 'OUT';

    cells.push({
      workDate,
      inMonth,
      weekday: d.getDay(),
      kind,
      record: isFuture ? null : (bff?.record ?? null),
      holiday: isFuture ? null : (bff?.holiday ?? null),
      leave: isFuture ? null : (bff?.leave ?? null),
    });
  }

  // Trim trailing blank week if the month doesn't reach into it.
  while (cells.length > 35 && !cells[cells.length - 7]?.inMonth) {
    cells.splice(-7, 7);
  }

  return cells;
}

/** Combine a workDate (YYYY-MM-DD) with a wall-clock time into a Dayjs on that day. */
export function dateOnDay(workDate: string, time: Dayjs): Dayjs {
  const d = dayjs(workDate, 'YYYY-MM-DD');
  return d.hour(time.hour()).minute(time.minute()).second(0).millisecond(0);
}

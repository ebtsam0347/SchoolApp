import { parseDateOnly } from '../format';
import type { HolidayDto, HolidayType } from '../types';

export const HOLIDAY_TYPE_OPTIONS: HolidayType[] = [
  'NATIONAL',
  'COMPANY',
  'PROVINCIAL',
];

export function weekdayOf(iso: string): string {
  const d = parseDateOnly(iso);
  if (!d) return '';
  return d.toLocaleDateString('en-GB', { weekday: 'long' });
}

export type HolidayMonthGroup = {
  monthLabel: string;
  items: HolidayDto[];
};

export function groupByMonth(holidays: HolidayDto[]): HolidayMonthGroup[] {
  const sorted = holidays
    .slice()
    .sort((a, b) => a.holidayDate.localeCompare(b.holidayDate));
  const groups = new Map<string, HolidayDto[]>();
  for (const h of sorted) {
    const d = parseDateOnly(h.holidayDate)!;
    const key = d.toLocaleDateString('en-GB', {
      month: 'long',
      year: 'numeric',
    });
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(h);
  }
  return [...groups.entries()].map(([monthLabel, items]) => ({
    monthLabel,
    items,
  }));
}

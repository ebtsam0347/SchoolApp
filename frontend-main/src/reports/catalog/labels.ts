// Display strings + service ordering for the catalog UI. Centralised so
// the page, the segmented filter, and any future palette entries all
// agree on the labels and the order rows appear in.

import type { ReportFormat, ReportMode, ReportService } from '../types';
import type { StatusPillTone } from '@/components';

export const SERVICE_ORDER: ReportService[] = [
  'employee',
  'attendance',
  'leaves',
  'payroll',
  'accounts',
];

export const SERVICE_LABEL: Record<ReportService, string> = {
  employee: 'People',
  attendance: 'Attendance',
  leaves: 'Leaves',
  payroll: 'Payroll',
  accounts: 'Accounts',
};

export const FORMAT_LABEL: Record<ReportFormat, string> = {
  xlsx: 'Excel',
  pdf: 'PDF',
  csv: 'CSV',
  zip: 'ZIP',
  json: 'JSON',
};

/** Pill tone per output format — chosen so a row of cards has visual
 *  variety without being a rainbow. */
export const FORMAT_TONE: Record<ReportFormat, StatusPillTone> = {
  xlsx: 'success',
  pdf: 'error',
  csv: 'info',
  zip: 'primary',
  json: 'neutral',
};

export const MODE_LABEL: Record<ReportMode, string> = {
  sync: 'Direct download',
  async: 'Background job',
};

export const MODE_TONE: Record<ReportMode, StatusPillTone> = {
  sync: 'neutral',
  async: 'warning',
};

/** `payrollRunId` → `Payroll run id`. Words split on camel/Pascal/snake.
 *  First letter uppercased, the rest lowercased so `from` reads "From"
 *  not "from" without forcing a per-parameter human label. */
export function humanizeParameterName(name: string): string {
  const words = name
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z\d])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .trim()
    .split(/\s+/);
  if (words.length === 0) return name;
  words[0] = words[0]!.charAt(0).toUpperCase() + words[0]!.slice(1);
  return words.join(' ');
}

/** `BONUS` → `Bonus`, `EID_UL_FITR` → `Eid ul fitr`. Used inside enum
 *  selects so users see human values instead of upper-snake. */
export function humanizeEnumValue(value: string): string {
  if (!/[a-z]/.test(value) && /_/.test(value)) {
    const lower = value.toLowerCase().replace(/_/g, ' ');
    return lower.charAt(0).toUpperCase() + lower.slice(1);
  }
  return value;
}

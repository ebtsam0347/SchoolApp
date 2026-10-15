// Compact range selector used as the page-level time control on every
// analytics domain page. The four service-side endpoints all accept the
// same `?months=` parameter (1..24); rather than spread a different
// picker per page, we use one Segmented across all of them.
//
// Stays a controlled component — the page owns the state and threads it
// into every chart query so a single change refetches them all in
// parallel.

import { Segmented } from 'antd';

export const ANALYTICS_MONTHS_OPTIONS = [3, 6, 12, 24] as const;

export type AnalyticsMonths = (typeof ANALYTICS_MONTHS_OPTIONS)[number];

export interface MonthsRangePickerProps {
  value: AnalyticsMonths;
  onChange: (next: AnalyticsMonths) => void;
}

export function MonthsRangePicker({ value, onChange }: MonthsRangePickerProps) {
  return (
    <Segmented
      value={value}
      onChange={(v) => onChange(v as AnalyticsMonths)}
      options={ANALYTICS_MONTHS_OPTIONS.map((m) => ({
        label: `${m}m`,
        value: m,
      }))}
    />
  );
}

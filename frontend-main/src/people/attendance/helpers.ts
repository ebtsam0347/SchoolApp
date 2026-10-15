import { toIsoDate, type AttendanceRecordDto } from '@/attendance';

export function lastNDays(n: number) {
  const today = new Date();
  const start = new Date(today);
  start.setDate(today.getDate() - (n - 1));
  return { from: toIsoDate(start), to: toIsoDate(today) };
}

export type RangeSummary = {
  totalWorked: number;
  totalOt: number;
  totalLate: number;
  lateDays: number;
  presentDays: number;
  absentDays: number;
};

export function computeRangeSummary(records: AttendanceRecordDto[]): RangeSummary {
  let totalWorked = 0;
  let totalOt = 0;
  let totalLate = 0;
  let lateDays = 0;
  let presentDays = 0;
  let absentDays = 0;
  for (const r of records) {
    totalWorked += r.workedMinutes;
    totalOt += r.otNormalMinutes + r.otExtraMinutes + r.otSpecialMinutes;
    totalLate += r.lateMinutes;
    if (r.lateMinutes > 0) lateDays += 1;
    if (r.status === 'PRESENT' || r.status === 'HALF_DAY') presentDays += 1;
    if (r.status === 'ABSENT') absentDays += 1;
  }
  return { totalWorked, totalOt, totalLate, lateDays, presentDays, absentDays };
}

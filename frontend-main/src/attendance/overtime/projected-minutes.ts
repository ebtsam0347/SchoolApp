// The OT a day's punches earn, whatever the request's status — what
// approving a pending request would credit. Mirrors the backend's
// AttendanceRecomputeService.overtimeMinutes: the whole worked day on a
// Sunday / holiday, otherwise minutes worked beyond the expected shift
// (shift span less its break). Only a fallback for API builds whose OT rows
// don't carry `minutes.projectedOtMinutes` yet.

import type { AttendanceRecordDto } from '../types';

const toMinutes = (hhmm: string) => {
  const [h = 0, m = 0] = hhmm.split(':').map(Number);
  return h * 60 + m;
};

export function projectedOtMinutes(rec: AttendanceRecordDto): number {
  const sunday = new Date(`${rec.workDate}T00:00:00`).getDay() === 0;
  if (sunday || rec.status === 'WEEKEND' || rec.status === 'HOLIDAY') return rec.workedMinutes;
  if (!rec.shiftStartTime || !rec.shiftEndTime) return 0;
  const start = toMinutes(rec.shiftStartTime);
  const end = toMinutes(rec.shiftEndTime);
  const span = end > start ? end - start : 24 * 60 - (start - end);
  const expected = Math.max(0, span - (rec.shiftBreakMinutes ?? 0));
  return Math.max(0, rec.workedMinutes - expected);
}

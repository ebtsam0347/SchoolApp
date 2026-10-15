// Shared types for the /time/attendance month grid.

import type {
  AttendanceRecordDto,
  HolidayDto,
  MonthDayKind,
  MonthDayLeaveRef,
} from '@/attendance';

export type DayCell = {
  workDate: string;
  inMonth: boolean;
  weekday: number; // 0 = Sun
  /** BFF kind, or 'OUT' for grid-padding cells outside the displayed month. */
  kind: MonthDayKind | 'OUT';
  record: AttendanceRecordDto | null;
  holiday: HolidayDto | null;
  leave: MonthDayLeaveRef | null;
};

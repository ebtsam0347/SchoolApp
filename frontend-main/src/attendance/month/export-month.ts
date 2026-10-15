// Client-side .xlsx export of one employee's month — the same
// AttendanceMonthResponse the month calendar renders, so no extra fetch and
// no backend report. `xlsx` is loaded on demand so it stays out of the
// route's main chunk.

import {
  ATTENDANCE_STATUS_LABEL,
  formatClock,
  formatMinutes,
  toIsoDate,
  type AttendanceMonthResponse,
  type MonthDay,
  type MonthDayKind,
} from '@/attendance';

const KIND_LABEL: Record<MonthDayKind, string> = {
  ...ATTENDANCE_STATUS_LABEL,
  FUTURE: 'Upcoming',
};

const WEEKDAY = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** "0m" reads as noise in a sheet — blank it instead. */
const mins = (m: number | null | undefined) => (m && m > 0 ? formatMinutes(m) : '');

const shift = (start: string | null, end: string | null) =>
  start && end ? `${start.slice(0, 5)} - ${end.slice(0, 5)}` : '';

export async function downloadMonthXlsx(
  employee: { fullName: string; employeeCode?: string | null },
  /** YYYY-MM */
  month: string,
  data: AttendanceMonthResponse,
  payableDays: number | null,
): Promise<void> {
  const XLSX = await import('xlsx');

  const header = [
    'Date',
    'Day',
    'Status',
    'Check in',
    'Check out',
    'Shift',
    'Worked',
    'Late',
    'Early out',
    'OT normal',
    'OT extra',
    'OT special',
    'Total OT',
    'Remarks',
  ];
  // The org-wide records source only returns days that have a record, so
  // walk every date of the month and mark the gaps instead of dropping them.
  const byDate = new Map(data.days.map((d) => [d.workDate, d]));
  const [year, monthNo] = month.split('-').map(Number);
  const length = new Date(year, monthNo, 0).getDate();
  const today = toIsoDate(new Date());
  const rows = Array.from({ length }, (_, i) => {
    const workDate = toIsoDate(new Date(year, monthNo - 1, i + 1));
    const d: MonthDay | undefined = byDate.get(workDate);
    const weekday = WEEKDAY[new Date(year, monthNo - 1, i + 1).getDay()];
    if (!d) {
      return [workDate, weekday, workDate > today ? 'Upcoming' : 'No record'];
    }
    const r = d.record;
    const totalOt = r ? r.otNormalMinutes + r.otExtraMinutes + r.otSpecialMinutes : 0;
    const remarks = [
      d.holiday?.name,
      d.leave
        ? `Leave: ${d.leave.leaveTypeCode}${d.leave.halfDay ? ` (half day${d.leave.halfDayPart ? ` ${d.leave.halfDayPart}` : ''})` : ''}`
        : null,
      r?.notes,
    ]
      .filter(Boolean)
      .join(' · ');
    return [
      d.workDate,
      weekday,
      KIND_LABEL[d.kind] ?? d.kind,
      r?.checkIn ? formatClock(r.checkIn) : '',
      r?.checkOut ? formatClock(r.checkOut) : '',
      r ? shift(r.shiftStartTime, r.shiftEndTime) : '',
      mins(r?.workedMinutes),
      mins(r?.lateMinutes),
      mins(r?.earlyMinutes),
      mins(r?.otNormalMinutes),
      mins(r?.otExtraMinutes),
      mins(r?.otSpecialMinutes),
      mins(totalOt),
      remarks,
    ];
  });

  const s = data.summary;
  const summaryRows: (string | number)[][] = [
    ['Employee', employee.fullName],
    ['Employee code', employee.employeeCode ?? ''],
    ['Month', month],
    [],
    ['Present', s.present],
    ['Half day', s.halfDay],
    ['On leave', s.leave],
    ['Absent', s.absent],
    ['Holidays', s.holiday],
    ['Weekends', s.weekend],
    ['Payable days', payableDays ?? ''],
    ['Total worked', formatMinutes(s.totalWorkedMinutes)],
    ['Total late', formatMinutes(s.totalLateMinutes)],
    ['Total overtime', formatMinutes(s.totalOtMinutes)],
  ];

  const daily = XLSX.utils.aoa_to_sheet([header, ...rows]);
  daily['!cols'] = header.map((h) => ({ wch: h === 'Remarks' ? 36 : h === 'Shift' ? 14 : 11 }));
  const summary = XLSX.utils.aoa_to_sheet(summaryRows);
  summary['!cols'] = [{ wch: 16 }, { wch: 28 }];

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, daily, 'Attendance');
  XLSX.utils.book_append_sheet(wb, summary, 'Summary');

  const who = (employee.employeeCode || employee.fullName).replace(/[^\w-]+/g, '_');
  XLSX.writeFile(wb, `attendance-${who}-${month}.xlsx`);
}

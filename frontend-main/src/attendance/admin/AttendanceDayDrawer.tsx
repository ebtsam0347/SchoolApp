// Shared "click an employee → quick-glance drawer" shell, used everywhere
// an employee row shows up in an attendance table (Operator dashboard's
// action queues + not-checked-in roster, and the org-wide Records / Punches
// browsers) instead of navigating away to the employee directory.
//
// The day-specific body (check-in/out, late/worked, punch detail, etc.) is
// left to the caller via `children`; this shell only owns the day↔month
// toggle and the month calendar itself.
//
// Month data comes from the org-wide records browser
// (`/attendance/records/with-employee`, admin/operator gated) rather than
// the manager-scoped `/bff/attendance/team/{id}/month` Team Attendance uses
// — callers here (operators, HR, admins) don't necessarily manage the
// employee, and that endpoint 403s in that case.

import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, DatePicker, Drawer, Space, Typography } from 'antd';
import dayjs from 'dayjs';
import {
  ArrowLeft,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock,
  LogIn,
  LogOut,
  UserCheck,
  type LucideIcon,
} from 'lucide-react';

import {
  PersonCell,
  RequestState,
  KpiGrid,
  StatCard,
  StatusPill,
  type PersonCellPerson,
} from '@/components';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  attendanceBff,
  formatClock,
  formatClockOn,
  formatDateLong,
  formatDateTime,
  formatMinutes,
  parseDateOnly,
  PUNCH_SOURCE_LABEL,
  toIsoMonth,
  type AttendanceRecordDto,
  type EmployeeSummary,
  type MonthDay,
  type MonthSummary,
  type PunchDto,
} from '@/attendance';
import { MonthExportButton, MonthGrid, buildMonthGrid } from '../month';

const { Text } = Typography;

export function DetailTile({
  icon: Icon,
  label,
  value,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: 'var(--app-radius-sm)',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'var(--app-bg-tertiary, rgba(0,0,0,0.04))',
        }}
      >
        <Icon size={16} strokeWidth={1.75} aria-hidden />
      </div>
      <div>
        <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>
          {label}
        </Text>
        <Text style={{ fontSize: 16, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
          {value}
        </Text>
      </div>
    </div>
  );
}

/** Day body for a `{record, employee}` row — Operator dashboard's action
 *  queues and the Records browser share this exact shape. */
export function AttendanceRecordDayBody({ record }: { record: AttendanceRecordDto }) {
  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <Card styles={{ body: { padding: 16 } }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <DetailTile icon={LogIn} label="Check in" value={formatClock(record.checkIn)} />
          <DetailTile icon={LogOut} label="Check out" value={formatClockOn(record.checkOut, record.workDate)} />
        </div>
      </Card>

      <KpiGrid minColumnWidth={200} gap={12}>
        <StatCard
          variant="plain"
          tone={record.lateMinutes > 0 ? 'warning' : 'neutral'}
          icon={Clock}
          label="Late"
          value={record.lateMinutes > 0 ? formatMinutes(record.lateMinutes) : 'On time'}
        />
        <StatCard
          variant="plain"
          tone={
            record.status === 'PRESENT' || record.status === 'HALF_DAY'
              ? 'success'
              : record.status === 'ABSENT'
                ? 'error'
                : 'neutral'
          }
          icon={UserCheck}
          label="Worked"
          value={record.workedMinutes > 0 ? formatMinutes(record.workedMinutes) : '—'}
        />
      </KpiGrid>
    </Space>
  );
}

/** Day body for a single punch event — the Punches browser's row shape. */
export function PunchDayBody({ punch }: { punch: PunchDto }) {
  return (
    <Card styles={{ body: { padding: 16 } }}>
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <DetailTile icon={LogIn} label="Punched at" value={formatDateTime(punch.punchedAt)} />
          <DetailTile icon={Clock} label="Source" value={PUNCH_SOURCE_LABEL[punch.source]} />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <DetailTile
            icon={LogOut}
            label="Device"
            value={punch.deviceId ? String(punch.deviceId) : '—'}
          />
          <DetailTile icon={UserCheck} label="Reason" value={punch.reason || '—'} />
        </div>
      </Space>
    </Card>
  );
}

export type AttendanceRecordRow = {
  record: AttendanceRecordDto;
  employee: EmployeeSummary | null;
};

/** Convenience wrapper: builds the drawer directly from a `{record, employee}`
 *  row — the shape shared by Operator dashboard's action queues and the
 *  Records browser. */
export function AttendanceRecordDrawer({
  row,
  date,
  onClose,
}: {
  row: AttendanceRecordRow;
  date: string;
  onClose: () => void;
}) {
  const { record, employee } = row;
  const person: PersonCellPerson = employee
    ? {
        fullName: employee.fullName,
        employeeCode: employee.employeeCode,
        designation: employee.designationName ?? employee.departmentName ?? null,
        profilePictureUrl: employee.profilePictureUrl ?? undefined,
      }
    : { fullName: `#${record.employeeId}`, subtitle: 'Employee not found' };

  return (
    <AttendanceDayDrawer
      employeeId={record.employeeId}
      person={person}
      date={date}
      statusPill={
        <StatusPill tone={ATTENDANCE_STATUS_TONE[record.status]}>
          {ATTENDANCE_STATUS_LABEL[record.status]}
        </StatusPill>
      }
      onClose={onClose}
    >
      <AttendanceRecordDayBody record={record} />
    </AttendanceDayDrawer>
  );
}

/** Convenience wrapper: builds the drawer directly from a `{punch, employee}`
 *  row — the Punches browser's shape. No day-level status pill, since a
 *  single punch isn't PRESENT/ABSENT/etc. */
export function PunchRecordDrawer({
  row,
  date,
  onClose,
}: {
  row: { punch: PunchDto; employee: EmployeeSummary | null };
  date: string;
  onClose: () => void;
}) {
  const { punch, employee } = row;
  const person: PersonCellPerson = employee
    ? {
        fullName: employee.fullName,
        employeeCode: employee.employeeCode,
        designation: employee.designationName ?? employee.departmentName ?? null,
        profilePictureUrl: employee.profilePictureUrl ?? undefined,
      }
    : { fullName: `#${punch.employeeId}`, subtitle: 'Employee not found' };

  return (
    <AttendanceDayDrawer
      employeeId={punch.employeeId}
      person={person}
      date={date}
      onClose={onClose}
    >
      <PunchDayBody punch={punch} />
    </AttendanceDayDrawer>
  );
}

export type AttendanceDayDrawerProps = {
  employeeId: number;
  person: PersonCellPerson;
  /** The day the drawer opened on — anchors "View full month"'s starting month. */
  date: string;
  /** Status pill for the day header (tone + label are the caller's call). Omit when the subject has no day-level status (e.g. a single punch event). */
  statusPill?: ReactNode;
  /** Day-specific detail body (cards / stat grids) rendered above the "View full month" button. */
  children: ReactNode;
  /**
   * Month data source for the "View full month" toggle:
   *  - 'org' (default) — org-wide records browser; needs attendance.admin/operator.
   *  - 'team' — manager-scoped /bff/attendance/team/{id}/month (what the
   *    Team Attendance drawer uses); works for plain managers and carries
   *    server-side weekend/holiday counts for the payable-days formula.
   */
  monthScope?: 'org' | 'team';
  onClose: () => void;
};

export function AttendanceDayDrawer({
  employeeId,
  person,
  date,
  statusPill,
  children,
  monthScope = 'org',
  onClose,
}: AttendanceDayDrawerProps) {
  const [view, setView] = useState<'day' | 'month'>('day');
  const [month, setMonth] = useState(() => toIsoMonth(new Date(date)));

  return (
    <Drawer
      open
      onClose={onClose}
      size={view === 'month' ? 760 : 480}
      destroyOnHidden
      title={<PersonCell person={person} size="compact" />}
    >
      {view === 'month' ? (
        <EmployeeMonthView
          employeeId={employeeId}
          person={person}
          month={month}
          scope={monthScope}
          onMonthChange={setMonth}
          onBack={() => setView('day')}
        />
      ) : (
        <Space orientation="vertical" size={16} style={{ width: '100%' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {formatDateLong(date)}
            </Text>
            {statusPill}
          </div>

          {children}

          <Button
            icon={<CalendarDays size={14} />}
            onClick={() => setView('month')}
            block
            type="primary"
          >
            View full month
          </Button>
        </Space>
      )}
    </Drawer>
  );
}

function EmployeeMonthView({
  employeeId,
  person,
  month,
  scope,
  onMonthChange,
  onBack,
}: {
  employeeId: number;
  person: PersonCellPerson;
  month: string;
  scope: 'org' | 'team';
  onMonthChange: (month: string) => void;
  onBack: () => void;
}) {
  const monthDate = parseDateOnly(`${month}-01`);
  const monthStart = `${month}-01`;
  const monthEnd = dayjs(monthStart).endOf('month').format('YYYY-MM-DD');

  // Org scope: org-wide records page (admin/operator gated). Team scope:
  // manager-scoped team-month — same MonthDay/MonthSummary shape, with
  // real weekend/holiday counts from the server.
  const orgQuery = useQuery({
    queryKey: ['attendance', 'org', 'records', 'month', employeeId, month],
    queryFn: ({ signal }) =>
      attendanceBff.records({ employeeId, from: monthStart, to: monthEnd, size: 40 }, signal),
    enabled: scope === 'org',
  });
  const teamQuery = useQuery({
    queryKey: ['bff', 'attendance', 'team', employeeId, 'month', month],
    queryFn: ({ signal }) => attendanceBff.teamMonth(employeeId, month, signal),
    enabled: scope === 'team',
  });
  const monthQuery = scope === 'team' ? teamQuery : orgQuery;

  const records =
    scope === 'team'
      ? (teamQuery.data?.days ?? [])
          .map((d) => d.record)
          .filter((r): r is AttendanceRecordDto => r != null)
      : (orgQuery.data?.content ?? []).map((r) => r.record);
  const days: MonthDay[] =
    scope === 'team'
      ? (teamQuery.data?.days ?? [])
      : records.map((record) => ({
          workDate: record.workDate,
          kind: record.status,
          record,
          holiday: null,
          leave: null,
        }));
  const gridDays = monthDate ? buildMonthGrid(monthDate, days) : [];
  const summary = scope === 'team' ? teamQuery.data?.summary : summarizeMonth(records);

  // Payable days — same formula the Team Attendance drawer uses:
  // 26 - (working days - present) + leaves, where working days exclude
  // Sundays/weekends and holidays.
  const totalDaysInMonth = monthDate
    ? new Date(monthDate.getFullYear(), monthDate.getMonth() + 1, 0).getDate()
    : 0;
  const workingDays = summary ? totalDaysInMonth - summary.weekend - summary.holiday : 0;
  const payableDays = summary ? 26 - (workingDays - summary.present) + summary.leave : null;

  const shiftMonth = (delta: number) => {
    if (!monthDate) return;
    const next = new Date(monthDate);
    next.setMonth(next.getMonth() + delta);
    onMonthChange(toIsoMonth(next));
  };

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Button type="text" icon={<ArrowLeft size={14} />} onClick={onBack} size="small">
          Back to day
        </Button>
        <Space size={4}>
          <MonthExportButton
            employee={person}
            month={month}
            data={monthQuery.data && summary ? { days, summary } : undefined}
            payableDays={payableDays}
          />
          <Button
            icon={<ChevronLeft size={14} />}
            onClick={() => shiftMonth(-1)}
            aria-label="Previous month"
            size="small"
          />
          <DatePicker
            picker="month"
            value={dayjs(month, 'YYYY-MM')}
            allowClear={false}
            format="MMM YYYY"
            size="small"
            onChange={(d) => {
              if (d) onMonthChange(d.format('YYYY-MM'));
            }}
          />
          <Button
            icon={<ChevronRight size={14} />}
            onClick={() => shiftMonth(1)}
            aria-label="Next month"
            size="small"
          />
        </Space>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(5, 1fr)',
          gap: 8,
        }}
      >
        <StatCard
          variant="plain"
          tone="success"
          label="Present"
          value={summary ? summary.present + summary.halfDay : 0}
          styles={{ body: { padding: 12 } }}
        />
        <StatCard
          variant="plain"
          tone="info"
          label="On leave"
          value={summary?.leave ?? 0}
          styles={{ body: { padding: 12 } }}
        />
        <StatCard
          variant="plain"
          tone={summary && summary.absent > 0 ? 'error' : 'neutral'}
          label="Absent"
          value={summary?.absent ?? 0}
          styles={{ body: { padding: 12 } }}
        />
        <StatCard
          variant="plain"
          tone="primary"
          label="Payable days"
          value={payableDays ?? '—'}
          styles={{ body: { padding: 12 } }}
        />
        <StatCard
          variant="plain"
          tone={summary && summary.totalOtMinutes > 0 ? 'info' : 'neutral'}
          label="Overtime"
          value={summary ? formatMinutes(summary.totalOtMinutes) : '0m'}
          styles={{ body: { padding: 12 } }}
        />
      </div>

      <RequestState
        loading={false}
        error={monthQuery.isError ? monthQuery.error : undefined}
        errorTitle="Couldn't load this month"
      >
        <MonthGrid days={gridDays} onSelect={() => {}} loading={monthQuery.isLoading} />
      </RequestState>
    </Space>
  );
}

function summarizeMonth(records: AttendanceRecordDto[]): MonthSummary {
  const summary: MonthSummary = {
    present: 0,
    absent: 0,
    leave: 0,
    holiday: 0,
    halfDay: 0,
    weekend: 0,
    totalWorkedMinutes: 0,
    totalOtMinutes: 0,
    totalLateMinutes: 0,
  };
  for (const record of records) {
    switch (record.status) {
      case 'PRESENT':
        summary.present++;
        break;
      case 'ABSENT':
        summary.absent++;
        break;
      case 'LEAVE':
        summary.leave++;
        break;
      case 'HOLIDAY':
        summary.holiday++;
        break;
      case 'WEEKEND':
        summary.weekend++;
        break;
      case 'HALF_DAY':
        summary.halfDay++;
        break;
    }
    summary.totalWorkedMinutes += record.workedMinutes;
    summary.totalOtMinutes +=
      record.otNormalMinutes + record.otExtraMinutes + record.otSpecialMinutes;
    summary.totalLateMinutes += record.lateMinutes;
  }
  return summary;
}
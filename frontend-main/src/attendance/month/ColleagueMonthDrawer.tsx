// Month calendar for a department colleague — the team-request-generation
// Regularization tab (/time/team-requests?type=regularization).
//
// Mirrors the "Full month" view of the manager's team attendance drawer
// (/time/team): same summary tiles, same MonthGrid, and clicking a past or
// current day opens RegularizeDrawer for that colleague and date.
//
// Data: not the manager's /bff/attendance/team/{id}/month — that 403s unless
// the caller manages the target, and a request generator manages nobody.
// Instead the month is assembled here from the same two reads the BFF uses,
// both gated on `attendance.read` (which every EMPLOYEE holds):
// GET /attendance/employee/{id}?from&to and GET /holidays?year. `buildMonth`
// mirrors the BFF's builder, so the grid and tiles match /time/team.
//
// Filing runs in `routing="team"`: attendance-service resolves it as
// TEAM_ON_BEHALF, so nothing is auto-approved and the chain starts at the
// filer's own manager.

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, DatePicker, Drawer, Space, Typography } from 'antd';
import dayjs from 'dayjs';
import { ChevronLeft, ChevronRight, Users } from 'lucide-react';

import { PersonCell, RequestState, StatCard } from '@/components';
import {
  attendanceApi,
  formatMinutes,
  holidaysApi,
  parseDateOnly,
  toIsoDate,
  toIsoMonth,
  type AttendanceMonthResponse,
  type AttendanceRecordDto,
  type HolidayDto,
  type MonthDay,
  type MonthSummary,
} from '@/attendance';
import { forbiddenOr } from '@/lib/api-error';
import { MonthExportButton } from './MonthExportButton';
import { MonthGrid } from './MonthGrid';
import { RegularizeDrawer } from './RegularizeDrawer';
import { buildMonthGrid } from './build-grid';

const { Text } = Typography;

export type ColleagueMonthDrawerProps = {
  /** Colleague whose month is open; null closes the drawer. */
  employee: {
    id: number;
    fullName: string;
    employeeCode?: string | null;
    designation?: string | null;
    profilePictureUrl?: string | null;
  } | null;
  onClose: () => void;
};

export function ColleagueMonthDrawer({ employee, onClose }: ColleagueMonthDrawerProps) {
  return (
    <Drawer
      open={employee != null}
      onClose={onClose}
      size={760}
      destroyOnHidden
      title={
        employee ? (
          <PersonCell
            person={{
              fullName: employee.fullName,
              employeeCode: employee.employeeCode ?? null,
              designation: employee.designation ?? null,
              profilePictureUrl: employee.profilePictureUrl ?? undefined,
            }}
            size="compact"
          />
        ) : (
          <Users size={16} />
        )
      }
    >
      {/* Keyed so a different colleague starts on the current month. */}
      {employee ? <MonthBody key={employee.id} employee={employee} /> : null}
    </Drawer>
  );
}

function MonthBody({
  employee,
}: {
  employee: NonNullable<ColleagueMonthDrawerProps['employee']>;
}) {
  const [month, setMonthValue] = useState(() => toIsoMonth(new Date()));
  /** Day being regularized for this colleague, if any. */
  const [regularizeDate, setRegularizeDate] = useState<string | null>(null);
  const monthDate = parseDateOnly(`${month}-01`);

  const monthQuery = useQuery({
    queryKey: ['attendance', 'colleague', employee.id, 'month', month],
    queryFn: async ({ signal }) => {
      if (!monthDate) throw new Error(`Invalid month ${month}`);
      const from = toIsoDate(monthDate);
      const to = toIsoDate(
        new Date(monthDate.getFullYear(), monthDate.getMonth() + 1, 0),
      );
      const [records, holidays] = await Promise.all([
        attendanceApi.forEmployee(employee.id, { from, to }, signal),
        holidaysApi.list(monthDate.getFullYear(), signal),
      ]);
      return buildMonth(monthDate, records, holidays);
    },
  });

  const days = monthDate ? buildMonthGrid(monthDate, monthQuery.data?.days ?? []) : [];
  const summary = monthQuery.data?.summary ?? null;

  // Same payable-days rule as /time/team: 26 - (working days - present) +
  // leaves, where working days = calendar days - weekends - holidays.
  const totalDaysInMonth = monthDate
    ? new Date(monthDate.getFullYear(), monthDate.getMonth() + 1, 0).getDate()
    : 0;
  const workingDays = summary ? totalDaysInMonth - summary.weekend - summary.holiday : 0;
  const payableDays = summary ? 26 - (workingDays - summary.present) + summary.leave : null;

  const shiftMonth = (delta: number) => {
    if (!monthDate) return;
    const next = new Date(monthDate);
    next.setMonth(next.getMonth() + delta);
    setMonthValue(toIsoMonth(next));
  };

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Space size={4}>
          <MonthExportButton
            employee={employee}
            month={month}
            data={monthQuery.data}
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
              if (d) setMonthValue(d.format('YYYY-MM'));
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

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 8 }}>
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
        errorTitle={forbiddenOr(
          monthQuery.error,
          "You can't view this employee's attendance",
          "Couldn't load this month",
        )}
      >
        <MonthGrid
          days={days}
          onSelect={(workDate) => {
            // Nothing to regularize on a day that hasn't happened yet, or on
            // the grid's padding cells.
            const day = days.find((d) => d.workDate === workDate);
            if (!day || !day.inMonth || day.kind === 'FUTURE') return;
            setRegularizeDate(workDate);
          }}
          loading={monthQuery.isLoading}
        />
      </RequestState>

      <Text type="secondary" style={{ fontSize: 12 }}>
        Click a day to regularize it for {employee.fullName}.
      </Text>

      <RegularizeDrawer
        open={regularizeDate != null}
        workDate={regularizeDate ?? ''}
        // The composite carries no per-day punch list, so no earliest-punch
        // hint here.
        existingPunches={[]}
        onBehalfOf={{
          id: employee.id,
          fullName: employee.fullName,
          employeeCode: employee.employeeCode ?? null,
        }}
        routing="team"
        onClose={() => setRegularizeDate(null)}
        onDone={() => setRegularizeDate(null)}
      />
    </Space>
  );
}

/**
 * Same rules as the BFF's team-month builder (AttendanceBffService
 * #buildMonth): HOLIDAY > the record's status > Sunday WEEKEND > ABSENT.
 * Like the BFF, no leave overlay — a leave day shows through its record.
 */
function buildMonth(
  monthStart: Date,
  records: AttendanceRecordDto[],
  holidays: HolidayDto[],
): AttendanceMonthResponse {
  const recordByDate = new Map(records.map((r) => [r.workDate, r]));
  const holidayByDate = new Map(holidays.map((h) => [h.holidayDate, h]));
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
  const days: MonthDay[] = [];
  const year = monthStart.getFullYear();
  const monthIndex = monthStart.getMonth();
  const length = new Date(year, monthIndex + 1, 0).getDate();

  for (let n = 1; n <= length; n++) {
    const date = new Date(year, monthIndex, n);
    const workDate = toIsoDate(date);
    const record = recordByDate.get(workDate) ?? null;
    const holiday = holidayByDate.get(workDate) ?? null;
    const kind: MonthDay['kind'] = holiday
      ? 'HOLIDAY'
      : record
        ? record.status
        : date.getDay() === 0
          ? 'WEEKEND'
          : 'ABSENT';

    if (kind === 'PRESENT') summary.present++;
    else if (kind === 'ABSENT') summary.absent++;
    else if (kind === 'LEAVE') summary.leave++;
    else if (kind === 'HOLIDAY') summary.holiday++;
    else if (kind === 'HALF_DAY') summary.halfDay++;
    else if (kind === 'WEEKEND') summary.weekend++;

    if (record) {
      summary.totalWorkedMinutes += record.workedMinutes;
      summary.totalOtMinutes +=
        record.otNormalMinutes + record.otExtraMinutes + record.otSpecialMinutes;
      summary.totalLateMinutes += record.lateMinutes;
    }
    days.push({ workDate, kind, record, holiday, leave: null });
  }
  return { days, summary };
}

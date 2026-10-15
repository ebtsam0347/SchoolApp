// /time/team — manager's team attendance.
//
// Two surfaces in one route:
//
//  1. Roll-call table for the day (?date=YYYY-MM-DD, defaults to today,
//     Asia/Karachi). One row per direct report + delegated report, with
//     status, check-in/out, lateness. Backed by
//     /bff/attendance/team/today.
//  2. Click a row → drawer; default view is **the day's detail** for that
//     employee (status + check-in/out + late, all from the existing row
//     so no extra fetch). Toggle to "Full month" pulls
//     /bff/attendance/team/{id}/month and reuses MonthGrid. Clicking a
//     past or current day there opens RegularizeDrawer for that employee
//     and date — the manager files the regularization on their behalf.
//
// "Regularization history" (next to Late only) lists the regularizations
// this manager filed that way — /regularizations/filed-by-me.
//
// `truncated=true` from the BFF means coverage exceeded 500 rows; we
// surface a soft hint pointing at the org-wide attendance browser.

import { useState } from 'react';
import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Drawer,
  Select,
  Space,
  Switch,
  Table,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import {
  AlertTriangle,
  ArrowLeft,
  Calendar,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock,
  History,
  LogIn,
  LogOut,
  Plane,
  UserCheck,
  Users,
} from 'lucide-react';

import {
  FilterBar,
  KpiGrid,
  PageHeader,
  PersonCell,
  RequestState,
  Section,
  StatCard,
  StatusPill,
  type StatusPillTone,
  personColumn,
} from '@/components';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  attendanceBff,
  formatClock,
  formatClockOn,
  formatDateLong,
  formatMinutes,
  parseDateOnly,
  toIsoDate,
  toIsoMonth,
  type TeamAttendanceStatus,
  type TeamAttendanceTodayRow,
} from '@/attendance';
import {
  MonthGrid,
  RegularizationHistoryDrawer,
  RegularizeDrawer,
  buildMonthGrid,
  MonthExportButton,
} from '@/attendance/month';
import { forbiddenOr } from '@/lib/api-error';
import type { ColumnsType } from 'antd/es/table';

const { Text } = Typography;

type TeamView = 'day' | 'month';

type TeamSearch = {
  date?: string;
  employee?: number;
  view?: TeamView;
  month?: string;
  // Filters — applied client-side over the team-today rows (≤500).
  q?: string;
  status?: TeamAttendanceStatus;
  sectionId?: number;
  lateOnly?: boolean;
};

const TEAM_STATUS_VALUES: TeamAttendanceStatus[] = [
  'PRESENT',
  'PENDING_PUNCH',
  'ABSENT',
  'LEAVE',
  'HALF_DAY',
  'HOLIDAY',
  'WEEKEND',
];

function isTeamStatus(v: unknown): v is TeamAttendanceStatus {
  return typeof v === 'string' && (TEAM_STATUS_VALUES as string[]).includes(v);
}

const TEAM_STATUS_LABEL: Record<TeamAttendanceStatus, string> = {
  ...ATTENDANCE_STATUS_LABEL,
  PENDING_PUNCH: 'No punch yet',
};

const TEAM_STATUS_TONE: Record<TeamAttendanceStatus, StatusPillTone> = {
  ...ATTENDANCE_STATUS_TONE,
  PENDING_PUNCH: 'warning',
};

export const Route = createFileRoute('/_app/time/team')({
  validateSearch: (search): TeamSearch => ({
    date: typeof search.date === 'string' ? search.date : undefined,
    employee:
      typeof search.employee === 'number'
        ? search.employee
        : typeof search.employee === 'string' && search.employee
          ? Number(search.employee) || undefined
          : undefined,
    view: search.view === 'month' ? 'month' : search.view === 'day' ? 'day' : undefined,
    month: typeof search.month === 'string' ? search.month : undefined,
    q:
      typeof search.q === 'string' && search.q.length > 0
        ? search.q
        : undefined,
    status: isTeamStatus(search.status) ? search.status : undefined,
    sectionId:
      typeof search.sectionId === 'number'
        ? search.sectionId
        : typeof search.sectionId === 'string' && search.sectionId
          ? Number(search.sectionId) || undefined
          : undefined,
    lateOnly: search.lateOnly === true || search.lateOnly === 'true' ? true : undefined,
  }),
  component: TeamAttendancePage,
});

function TeamAttendancePage() {
  const search = useSearch({ from: '/_app/time/team' });
  const navigate = useNavigate({ from: '/time/team' });

  const date = search.date ?? toIsoDate(new Date());
  /** Regularizations this manager filed for their reports. */
  const [historyOpen, setHistoryOpen] = useState(false);

  const todayQuery = useQuery({
    queryKey: ['bff', 'attendance', 'team', 'today', date],
    queryFn: ({ signal }) => attendanceBff.teamToday(date, signal),
  });

  const rows = todayQuery.data?.rows ?? [];
  const truncated = todayQuery.data?.truncated ?? false;
  const counts = countByStatus(rows);

  // Section options come from the visible rows themselves — keeps the
  // filter aligned with coverage (no orphan sections where the manager
  // has no reports). De-dupe by sectionId, alphabetise by name.
  const sectionOptions = collectSections(rows);

  // Lowercase once for the q-match — small list, but tidier.
  const qLower = (search.q ?? '').trim().toLowerCase();
  const filteredRows = rows.filter((row) => {
    if (search.status && row.status !== search.status) return false;
    if (
      typeof search.sectionId === 'number' &&
      row.employee.sectionId !== search.sectionId
    ) {
      return false;
    }
    if (search.lateOnly && row.lateMinutes <= 0) return false;
    if (qLower) {
      const haystack = [
        row.employee.fullName,
        row.employee.employeeCode ?? '',
      ]
        .join(' ')
        .toLowerCase();
      if (!haystack.includes(qLower)) return false;
    }
    return true;
  });

  const filtersDirty =
    !!search.q ||
    !!search.status ||
    typeof search.sectionId === 'number' ||
    !!search.lateOnly;

  const updateFilter = (patch: Partial<TeamSearch>) =>
    navigate({
      search: (prev) => ({ ...prev, ...patch }),
      replace: true,
    });

  const resetFilters = () =>
    updateFilter({
      q: undefined,
      status: undefined,
      sectionId: undefined,
      lateOnly: undefined,
    });

  const setDate = (delta: number) => {
    const next = new Date(date);
    next.setDate(next.getDate() + delta);
    navigate({
      search: (prev) => ({ ...prev, date: toIsoDate(next) }),
      replace: true,
    });
  };

  const openMember = (employeeId: number) =>
    navigate({
      search: (prev) => ({
        ...prev,
        employee: employeeId,
        view: 'day',
      }),
    });

  const closeDrawer = () =>
    navigate({
      search: (prev) => ({
        ...prev,
        employee: undefined,
        view: undefined,
        month: undefined,
      }),
    });

  const switchToMonth = () =>
    navigate({
      search: (prev) => ({
        ...prev,
        view: 'month',
        month: prev.month ?? toIsoMonth(new Date(date)),
      }),
      replace: true,
    });

  const switchToDay = () =>
    navigate({
      search: (prev) => ({ ...prev, view: 'day' }),
      replace: true,
    });

  const columns: ColumnsType<TeamAttendanceTodayRow> = [
    personColumn<TeamAttendanceTodayRow>({
      title: 'Employee',
      dataIndex: (row) => ({
        fullName: row.employee.fullName,
        employeeCode: row.employee.employeeCode,
        // Prefer designation; fall through to department so the cell always
        // pairs a label with the code.
        designation:
          row.employee.designationName ?? row.employee.departmentName ?? null,
        profilePictureUrl: row.employee.profilePictureUrl ?? undefined,
      }),
    }),
    {
      title: 'Status',
      key: 'status',
      width: 140,
      render: (_, row) => (
        <StatusPill tone={TEAM_STATUS_TONE[row.status]} size="sm">
          {TEAM_STATUS_LABEL[row.status]}
        </StatusPill>
      ),
    },
    {
      title: 'Check in',
      key: 'checkIn',
      width: 110,
      render: (_, row) => formatClock(row.checkIn),
    },
    {
      title: 'Check out',
      key: 'checkOut',
      width: 110,
      render: (_, row) => formatClockOn(row.checkOut, row.checkIn),
    },
    {
      title: 'Late',
      key: 'lateMinutes',
      width: 90,
      align: 'right',
      render: (_, row) =>
        row.lateMinutes > 0 ? (
          <Text type="warning" style={{ fontVariantNumeric: 'tabular-nums' }}>
            {formatMinutes(row.lateMinutes)}
          </Text>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
  ];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Time"
        title="Team attendance"
        description="Roll-call for your direct reports + delegated reports on the selected date."
        actions={
          <Space size={4}>
            <Button
              icon={<ChevronLeft size={14} />}
              onClick={() => setDate(-1)}
              aria-label="Previous day"
            />
            <DatePicker
              value={dayjs(date)}
              allowClear={false}
              format="DD MMM YYYY"
              onChange={(d) => {
                if (d) {
                  navigate({
                    search: (prev) => ({ ...prev, date: d.format('YYYY-MM-DD') }),
                    replace: true,
                  });
                }
              }}
              disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
            />
            <Button
              icon={<ChevronRight size={14} />}
              onClick={() => setDate(1)}
              aria-label="Next day"
              disabled={dayjs(date).isSame(dayjs(), 'day')}
            />
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="success"
          accentCorner="tr"
          icon={UserCheck}
          label="Present"
          value={counts.PRESENT + counts.HALF_DAY}
          delta={counts.HALF_DAY > 0 ? `${counts.HALF_DAY} half day` : undefined}
        />
        <StatCard
          variant="plain"
          tone={counts.PENDING_PUNCH > 0 ? 'warning' : 'neutral'}
          icon={Clock}
          label="No punch yet"
          value={counts.PENDING_PUNCH}
          delta={counts.PENDING_PUNCH === 0 ? 'Everyone checked in' : undefined}
        />
        <StatCard
          variant="plain"
          tone={counts.ABSENT > 0 ? 'error' : 'neutral'}
          icon={AlertTriangle}
          label="Absent"
          value={counts.ABSENT}
        />
        <StatCard variant="plain" tone="info" icon={Plane} label="On leave" value={counts.LEAVE} />
        <StatCard
          variant="plain"
          tone="neutral"
          icon={Calendar}
          label="Holiday / weekend"
          value={counts.HOLIDAY + counts.WEEKEND}
        />
      </KpiGrid>

      {truncated ? (
        <Alert
          type="warning"
          showIcon
          title="Coverage truncated"
          description="Your team list exceeded 500 employees — switch to the org attendance browser for full results."
        />
      ) : null}

      <Section
        title={
          filtersDirty
            ? `${dayjs(date).format('dddd, DD MMM YYYY')} · ${filteredRows.length} of ${rows.length} reports`
            : `${dayjs(date).format('dddd, DD MMM YYYY')} · ${rows.length} reports`
        }
        subtitle="Click a row to see that day's detail. Use the drawer to switch to the full month."
      >
        <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <FilterBar
            search={{
              value: search.q ?? '',
              onChange: (v) => updateFilter({ q: v ? v : undefined }),
              placeholder: 'Search name or employee code',
              width: 280,
            }}
            filters={
              <>
                <Select<TeamAttendanceStatus | undefined>
                  allowClear
                  placeholder="Status"
                  value={search.status}
                  onChange={(v) => updateFilter({ status: v ?? undefined })}
                  style={{ minWidth: 140 }}
                  options={TEAM_STATUS_VALUES.map((s) => ({
                    value: s,
                    label: TEAM_STATUS_LABEL[s],
                  }))}
                />
                <Select<number | undefined>
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="Section"
                  value={search.sectionId}
                  onChange={(v) => updateFilter({ sectionId: v ?? undefined })}
                  style={{ minWidth: 200 }}
                  disabled={sectionOptions.length === 0}
                  options={sectionOptions.map((s) => ({
                    value: s.id,
                    label: s.name,
                  }))}
                />
                <Space size={6} align="center">
                  <Switch
                    checked={!!search.lateOnly}
                    onChange={(v) => updateFilter({ lateOnly: v ? true : undefined })}
                    size="small"
                  />
                  <Text style={{ fontSize: 13 }}>Late only</Text>
                </Space>
                <Button
                  size="small"
                  icon={<History size={14} />}
                  onClick={() => setHistoryOpen(true)}
                >
                  Regularization history
                </Button>
              </>
            }
            onReset={filtersDirty ? resetFilters : undefined}
          />

          <RequestState
            loading={todayQuery.isLoading}
            error={todayQuery.error}
            empty={!todayQuery.isLoading && !todayQuery.isError && rows.length === 0}
            errorTitle={forbiddenOr(
              todayQuery.error,
              "You don't have permission to view team attendance",
              "Couldn't load team attendance",
            )}
            emptyTitle="No team yet"
            emptyDescription="You don't have any direct reports or covering delegations on this date."
          >
            <Table<TeamAttendanceTodayRow>
              rowKey={(row) => row.employee.id}
              columns={columns}
              dataSource={filteredRows}
              pagination={false}
              // Keep the roll-call columns scrolling inside the table on a
              // phone rather than dragging the page sideways.
              scroll={{ x: 'max-content' }}
              locale={{
                emptyText: filtersDirty
                  ? 'No reports match the current filters.'
                  : undefined,
              }}
              onRow={(row) => ({
                onClick: () => openMember(row.employee.id),
                style: { cursor: 'pointer' },
              })}
            />
          </RequestState>
        </Space>
      </Section>

      <RegularizationHistoryDrawer
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
      />

      <MemberDrawer
        employeeId={search.employee}
        view={search.view ?? 'day'}
        date={date}
        month={search.month ?? toIsoMonth(new Date(date))}
        rows={rows}
        onClose={closeDrawer}
        onSwitchToMonth={switchToMonth}
        onSwitchToDay={switchToDay}
        onMonthChange={(nextMonth) =>
          navigate({ search: (prev) => ({ ...prev, month: nextMonth }), replace: true })
        }
      />
    </div>
  );
}

/**
 * Build the section-filter options from the visible rows. The BFF ships
 * `sectionId` + `sectionName` on `EmployeeSummary` so we de-dupe by id
 * and use the real catalog id as the filter value — no synthesised
 * hashes. Rows without a section are skipped (the option list shows
 * only sections actually represented in the coverage set).
 */
function collectSections(
  rows: TeamAttendanceTodayRow[],
): { id: number; name: string }[] {
  const seen = new Set<number>();
  const out: { id: number; name: string }[] = [];
  for (const row of rows) {
    const id = row.employee.sectionId;
    const name = row.employee.sectionName;
    if (id == null || !name || seen.has(id)) continue;
    seen.add(id);
    out.push({ id, name });
  }
  out.sort((a, b) => a.name.localeCompare(b.name));
  return out;
}

function countByStatus(rows: TeamAttendanceTodayRow[]): Record<TeamAttendanceStatus, number> {
  const out: Record<TeamAttendanceStatus, number> = {
    PRESENT: 0,
    ABSENT: 0,
    LEAVE: 0,
    HOLIDAY: 0,
    WEEKEND: 0,
    HALF_DAY: 0,
    PENDING_PUNCH: 0,
  };
  for (const row of rows) out[row.status]++;
  return out;
}

/* ------------------------------ Drawer ------------------------------ */

type MemberDrawerProps = {
  employeeId: number | undefined;
  view: TeamView;
  date: string;
  month: string;
  rows: TeamAttendanceTodayRow[];
  onClose: () => void;
  onSwitchToMonth: () => void;
  onSwitchToDay: () => void;
  onMonthChange: (nextMonth: string) => void;
};

function MemberDrawer({
  employeeId,
  view,
  date,
  month,
  rows,
  onClose,
  onSwitchToMonth,
  onSwitchToDay,
  onMonthChange,
}: MemberDrawerProps) {
  const open = employeeId !== undefined;
  const member = rows.find((r) => r.employee.id === employeeId);

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size={view === 'month' ? 760 : 480}
      destroyOnHidden
      title={
        member ? (
          <PersonCell
            person={{
              fullName: member.employee.fullName,
              employeeCode: member.employee.employeeCode,
              designation:
                member.employee.designationName ??
                member.employee.departmentName ??
                null,
              profilePictureUrl: member.employee.profilePictureUrl ?? undefined,
            }}
            size="compact"
          />
        ) : (
          <Users size={16} />
        )
      }
    >
      {!member ? null : view === 'day' ? (
        <DayView row={member} date={date} onSwitchToMonth={onSwitchToMonth} />
      ) : (
        <MonthView
          member={member}
          month={month}
          onMonthChange={onMonthChange}
          onSwitchToDay={onSwitchToDay}
        />
      )}
    </Drawer>
  );
}

/* ------------------------------ Day view ------------------------------ */

function DayView({
  row,
  date,
  onSwitchToMonth,
}: {
  row: TeamAttendanceTodayRow;
  date: string;
  onSwitchToMonth: () => void;
}) {
  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatDateLong(date)}
        </Text>
        <StatusPill tone={TEAM_STATUS_TONE[row.status]}>
          {TEAM_STATUS_LABEL[row.status]}
        </StatusPill>
      </div>

      <Card styles={{ body: { padding: 16 } }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 16,
          }}
        >
          <PunchRow icon={LogIn} label="Check in" time={row.checkIn} />
          <PunchRow icon={LogOut} label="Check out" time={row.checkOut} />
        </div>
      </Card>

      <KpiGrid minColumnWidth={200} gap={12}>
        <StatCard
          variant="plain"
          tone={row.lateMinutes > 0 ? 'warning' : 'neutral'}
          icon={Clock}
          label="Late"
          value={row.lateMinutes > 0 ? formatMinutes(row.lateMinutes) : 'On time'}
        />
        <StatCard
          variant="plain"
          tone={
            row.status === 'PRESENT' || row.status === 'HALF_DAY'
              ? 'success'
              : row.status === 'ABSENT'
                ? 'error'
                : 'neutral'
          }
          icon={UserCheck}
          label="Worked"
          value={
            row.checkIn && row.checkOut
              ? formatMinutes(diffMinutes(row.checkIn, row.checkOut))
              : '—'
          }
        />
      </KpiGrid>

      <Text type="secondary" style={{ fontSize: 12 }}>
        Per-punch detail isn't available in this view yet — the BFF returns the
        check-in/check-out summary the day-grid uses elsewhere.
      </Text>

      <Button
        icon={<CalendarDays size={14} />}
        onClick={onSwitchToMonth}
        block
        type="primary"
      >
        View full month
      </Button>
    </Space>
  );
}

function PunchRow({
  icon: Icon,
  label,
  time,
}: {
  icon: typeof LogIn;
  label: string;
  time: string | null;
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
          {formatClock(time)}
        </Text>
      </div>
    </div>
  );
}

function diffMinutes(start: string, end: string): number {
  const a = new Date(start).getTime();
  const b = new Date(end).getTime();
  if (Number.isNaN(a) || Number.isNaN(b) || b <= a) return 0;
  return Math.round((b - a) / 60000);
}

/* ----------------------------- Month view ---------------------------- */

function MonthView({
  member,
  month,
  onMonthChange,
  onSwitchToDay,
}: {
  member: TeamAttendanceTodayRow;
  month: string;
  onMonthChange: (m: string) => void;
  onSwitchToDay: () => void;
}) {
  const employeeId = member.employee.id;
  const monthDate = parseDateOnly(`${month}-01`);
  /** Day the manager is regularizing for this report, if any. */
  const [regularizeDate, setRegularizeDate] = useState<string | null>(null);

  const monthQuery = useQuery({
    queryKey: ['bff', 'attendance', 'team', employeeId, 'month', month],
    queryFn: ({ signal }) => attendanceBff.teamMonth(employeeId, month, signal),
  });

  const days = monthDate
    ? buildMonthGrid(monthDate, monthQuery.data?.days ?? [])
    : [];
  const summary = monthQuery.data?.summary ?? null;

  // Payable days = 26 - (working days - present) + leaves, where working
  // days = calendar days in the month minus Sundays/weekends minus holidays.
  const totalDaysInMonth = monthDate
    ? new Date(monthDate.getFullYear(), monthDate.getMonth() + 1, 0).getDate()
    : 0;
  const workingDays = summary ? totalDaysInMonth - summary.weekend - summary.holiday : 0;
  const payableDays = summary ? 26 - (workingDays - summary.present) + summary.leave : null;

  const setMonth = (delta: number) => {
    if (!monthDate) return;
    const next = new Date(monthDate);
    next.setMonth(next.getMonth() + delta);
    onMonthChange(toIsoMonth(next));
  };

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Button
          type="text"
          icon={<ArrowLeft size={14} />}
          onClick={onSwitchToDay}
          size="small"
        >
          Back to day
        </Button>
        <Space size={4}>
          <MonthExportButton
            employee={member.employee}
            month={month}
            data={monthQuery.data}
            payableDays={payableDays}
          />
          <Button
            icon={<ChevronLeft size={14} />}
            onClick={() => setMonth(-1)}
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
            onClick={() => setMonth(1)}
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
        {/* <StatCard
          variant="plain"
          tone={summary && summary.totalLateMinutes > 0 ? 'warning' : 'neutral'}
          label="Late"
          value={summary ? formatMinutes(summary.totalLateMinutes) : '0m'}
          styles={{ body: { padding: 12 } }}
        /> */}
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
          "You don't manage this employee on this month",
          "Couldn't load this month",
        )}
      >
        <MonthGrid
          days={days}
          onSelect={(workDate) => {
            // Same rule as My attendance: nothing to regularize on a day
            // that hasn't happened yet, or on the grid's padding cells.
            const day = days.find((d) => d.workDate === workDate);
            if (!day || !day.inMonth || day.kind === 'FUTURE') return;
            setRegularizeDate(workDate);
          }}
          loading={monthQuery.isLoading}
        />
      </RequestState>

      <Text type="secondary" style={{ fontSize: 12 }}>
        Click a day to regularize it for {member.employee.fullName}.
      </Text>

      <RegularizeDrawer
        open={regularizeDate != null}
        workDate={regularizeDate ?? ''}
        // The team month composite carries no per-day punch list, so no
        // earliest-punch hint here.
        existingPunches={[]}
        onBehalfOf={{
          id: employeeId,
          fullName: member.employee.fullName,
          employeeCode: member.employee.employeeCode,
        }}
        onClose={() => setRegularizeDate(null)}
        onDone={() => setRegularizeDate(null)}
      />
    </Space>
  );
}

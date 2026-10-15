// /attendance — operator landing dashboard.
//
// One-shot composite (`/attendance/operator/dashboard`) — pulse counts,
// today's volume stats, and the two action queues operators most often work
// (missing checkouts, late arrivals). Date picker re-fetches; everything
// else is read-only on this route.

import { useContext, useEffect, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  DatePicker,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';
import {
  AlertCircle,
  CalendarClock,
  CheckCircle2,
  Clock,
  Coffee,
  Fingerprint,
  Hourglass,
  LogIn,
  PauseCircle,
  Plane,
  Plus,
  ShieldAlert,
  Timer,
  UserCheck,
  UserMinus,
  type LucideIcon,
} from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  PageHeader,
  PersonCell,
  RequestState,
  Section,
  StatCard,
  StatusPill,
  type PersonCellPerson,
} from '@/components';
import { AbilityContext } from '@/auth';
import { forbiddenOr, useDebounced } from '@/lib';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  attendanceBff,
  formatClock,
  formatDateLong,
  formatMinutes,
  orgAttendanceApi,
  SHIFT_PERIOD_LABEL,
  type BffPage,
  type NotCheckedInDto,
  type OperatorAttendanceRow,
  type OperatorDashboardResponse,
  type ShiftPeriod,
} from '@/attendance';
import {
  AttendanceDayDrawer,
  AttendanceRecordDrawer,
  AttendanceTabs,
  BulkOperatorPunchDrawer,
  DetailTile,
  EmployeeSummaryCell,
  PunchAddForRecordModal,
} from '@/attendance/admin';

const { Text } = Typography;

const NOT_CHECKED_IN_PAGE_SIZE = 25;

export const Route = createFileRoute('/_app/attendance/')({
  component: OperatorDashboardPage,
});

function OperatorDashboardPage() {
  const ability = useContext(AbilityContext);
  const canOperate = ability.can('operator', 'attendance');

  const [date, setDate] = useState<dayjs.Dayjs>(() => dayjs());
  const [period, setPeriod] = useState<ShiftPeriod | undefined>(undefined);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [addPunchTarget, setAddPunchTarget] = useState<OperatorAttendanceRow | null>(
    null,
  );
  const [selectedActionRow, setSelectedActionRow] = useState<OperatorAttendanceRow | null>(
    null,
  );
  const [selectedNotCheckedIn, setSelectedNotCheckedIn] = useState<NotCheckedInDto | null>(
    null,
  );
  const dateIso = date.format('YYYY-MM-DD');

  const queryClient = useQueryClient();
  const dashboardQuery = useQuery({
    queryKey: ['attendance', 'operator-dashboard', dateIso, period ?? 'ALL'],
    queryFn: ({ signal }) => attendanceBff.operatorDashboard(dateIso, period, signal),
    placeholderData: (prev) => prev,
  });

  // /attendance/org/not-checked-in is intrinsically "today" — it's a live
  // triage list that self-clears when a punch arrives. Skip the query on
  // historical dates; the section is hidden in that case anyway.
  const isToday = dateIso === dayjs().format('YYYY-MM-DD');
  // Org-wide roster (576+ rows org-wide) — paginate server-side. 1-based here;
  // the endpoint is 0-based, so we send `page - 1`.
  const [notCheckedInPage, setNotCheckedInPage] = useState(1);
  const [notCheckedInCode, setNotCheckedInCode] = useState('');
  const notCheckedInCodeQ = useDebounced(notCheckedInCode.trim(), 300);
  const notCheckedInQuery = useQuery({
    queryKey: ['attendance', 'org', 'not-checked-in', notCheckedInPage, notCheckedInCodeQ],
    queryFn: ({ signal }) =>
      orgAttendanceApi.notCheckedIn(
        {
          employeeCode: notCheckedInCodeQ || undefined,
          page: notCheckedInPage - 1,
          size: NOT_CHECKED_IN_PAGE_SIZE,
        },
        signal,
      ),
    enabled: isToday,
    // Keep the prior page visible while the next loads — no flash to empty.
    placeholderData: (prev) => prev,
    // Roster shrinks through the morning as people punch in — refresh on a
    // gentle cadence so operators see it drain without manual reload.
    refetchInterval: 60_000,
  });

  // The not-checked-in queryKey isn't date-keyed (endpoint has no date param —
  // it's always "now"). So when the operator toggles the date picker away
  // from today and back, RQ would otherwise reuse the cached roster from
  // the prior visit. Invalidate on every date-change so returning to today
  // always shows a fresh roster.
  useEffect(() => {
    if (isToday) {
      setNotCheckedInPage(1);
      void queryClient.invalidateQueries({
        queryKey: ['attendance', 'org', 'not-checked-in'],
      });
    }
  }, [dateIso, isToday, queryClient]);

  const data = dashboardQuery.data;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <AttendanceTabs />
      <PageHeader
        eyebrow="Attendance"
        title="Operator dashboard"
        description={formatDateLong(dateIso)}
        level={4}
        actions={
          <Space>
            <DatePicker
              value={date}
              onChange={(d) => d && setDate(d)}
              format="DD MMM YYYY"
              allowClear={false}
            />
            <Select<ShiftPeriod | 'ALL'>
              value={period ?? 'ALL'}
              onChange={(v) => setPeriod(v === 'ALL' ? undefined : v)}
              style={{ width: 140 }}
              options={[
                { value: 'ALL', label: 'All shifts' },
                { value: 'DAY', label: `${SHIFT_PERIOD_LABEL.DAY} shift` },
                { value: 'NIGHT', label: `${SHIFT_PERIOD_LABEL.NIGHT} shift` },
              ]}
            />
            {canOperate ? (
              <Button
                type="primary"
                icon={<Plus size={16} strokeWidth={1.75} />}
                onClick={() => setBulkOpen(true)}
              >
                Bulk add punches
              </Button>
            ) : null}
          </Space>
        }
      />

      <RequestState
        loading={false}
        error={dashboardQuery.isError ? dashboardQuery.error : undefined}
        errorTitle={forbiddenOr(
          dashboardQuery.error,
          "You don't have permission to view the operator dashboard.",
          "Couldn't load the operator dashboard",
        )}
      >
        <PulseGrid data={data} loading={dashboardQuery.isLoading} />

        <div
          style={{
            display: 'grid',
            gap: 16,
            gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))',
          }}
        >
          <ActionQueueSection
            title="Missing checkouts"
            description="Employees who checked in but haven't checked out yet."
            icon={Hourglass}
            tone="warning"
            page={data?.missingCheckouts}
            loading={dashboardQuery.isLoading}
            mode="missing"
            onAddPunch={canOperate ? setAddPunchTarget : undefined}
            onSelectRow={setSelectedActionRow}
          />
          <ActionQueueSection
            title="Late arrivals"
            description="Late minutes against the assigned shift after grace."
            icon={Clock}
            tone="error"
            page={data?.lateArrivals}
            loading={dashboardQuery.isLoading}
            mode="late"
            onAddPunch={canOperate ? setAddPunchTarget : undefined}
            onSelectRow={setSelectedActionRow}
          />
        </div>

        {isToday ? (
          <NotCheckedInSection
            rows={notCheckedInQuery.data?.content}
            total={notCheckedInQuery.data?.totalElements}
            page={notCheckedInPage}
            onPageChange={setNotCheckedInPage}
            employeeCode={notCheckedInCode}
            onEmployeeCodeChange={(v) => {
              setNotCheckedInCode(v);
              setNotCheckedInPage(1);
            }}
            loading={notCheckedInQuery.isLoading}
            fetching={notCheckedInQuery.isFetching}
            error={notCheckedInQuery.isError ? notCheckedInQuery.error : undefined}
            onSelectRow={setSelectedNotCheckedIn}
          />
        ) : null}
      </RequestState>

      {bulkOpen ? (
        <BulkOperatorPunchDrawer open={bulkOpen} onClose={() => setBulkOpen(false)} />
      ) : null}
      {addPunchTarget ? (
        <PunchAddForRecordModal
          record={addPunchTarget.record}
          employee={addPunchTarget.employee}
          onClose={() => setAddPunchTarget(null)}
        />
      ) : null}
      {selectedActionRow ? (
        <AttendanceRecordDrawer
          row={selectedActionRow}
          date={dateIso}
          onClose={() => setSelectedActionRow(null)}
        />
      ) : null}
      {selectedNotCheckedIn ? (
        <NotCheckedInDrawer
          row={selectedNotCheckedIn}
          date={dateIso}
          onClose={() => setSelectedNotCheckedIn(null)}
        />
      ) : null}
    </div>
  );
}

function PulseGrid({
  data,
  loading,
}: {
  data: OperatorDashboardResponse | undefined;
  loading: boolean;
}) {
  const pulse = data?.pulse;
  const fmt = (v: number | undefined): string =>
    loading && v === undefined ? '—' : (v ?? 0).toLocaleString('en-PK');

  return (
    <KpiGrid minColumnWidth={200}>
      <StatCard
        variant="hero"
        tone="success"
        icon={CheckCircle2}
        label="Present"
        value={fmt(pulse?.present)}
        delta={pulse ? `of ${pulse.total.toLocaleString('en-PK')} employees` : undefined}
      />
      <StatCard
        variant="accent-radial"
        tone="warning"
        accentCorner="tr"
        icon={AlertCircle}
        label="Late"
        value={fmt(pulse?.late)}
      />
      <StatCard
        variant="accent-radial"
        tone="error"
        accentCorner="tr"
        icon={UserMinus}
        label="Absent"
        value={fmt(pulse?.absent)}
      />
      <StatCard
        variant="plain"
        tone="info"
        icon={Plane}
        label="On leave"
        value={fmt(pulse?.onLeave)}
      />
      <StatCard
        variant="plain"
        tone="warning"
        icon={PauseCircle}
        label="Half day"
        value={fmt(pulse?.halfDay)}
      />
      <StatCard
        variant="plain"
        tone="primary"
        icon={Coffee}
        label="Holiday / weekend"
        value={fmt((pulse?.holiday ?? 0) + (pulse?.weekend ?? 0))}
      />
      <StatCard
        variant="plain"
        tone="primary"
        icon={Fingerprint}
        label="Punches today"
        value={fmt(data?.totalPunchesToday)}
        delta="across every source"
      />
      <StatCard
        variant="plain"
        tone="info"
        icon={CalendarClock}
        label="Active overrides"
        value={fmt(data?.activeOverridesToday)}
      />
      <StatCard
        variant="plain"
        tone="warning"
        icon={ShieldAlert}
        label="Pending regularizations"
        value={fmt(data?.pendingRegularizations)}
      />
    </KpiGrid>
  );
}

function ActionQueueSection({
  title,
  description,
  icon: Icon,
  tone,
  page,
  loading,
  mode,
  onAddPunch,
  onSelectRow,
}: {
  title: string;
  description: string;
  icon: LucideIcon;
  tone: 'warning' | 'error';
  /** First page of the queue; `totalElements` is the org-wide count. */
  page: BffPage<OperatorAttendanceRow> | undefined;
  loading: boolean;
  mode: 'missing' | 'late';
  /** When set, renders a per-row "Add punch" button. Undefined hides the column. */
  onAddPunch?: (row: OperatorAttendanceRow) => void;
  /** Row click opens the day-detail drawer instead of navigating to the directory. */
  onSelectRow: (row: OperatorAttendanceRow) => void;
}) {
  const data = page?.content ?? [];
  // The endpoint returns only the first page (no paging params), so `total`
  // can exceed the rows we hold. Drives the count badge + a "first N of M" hint.
  const total = page?.totalElements ?? data.length;
  const columns: TableProps<OperatorAttendanceRow>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, row) => (
        <EmployeeSummaryCell
          summary={row.employee}
          fallbackEmployeeId={row.record.employeeId}
          linkToProfile={false}
        />
      ),
    },
    mode === 'missing'
      ? {
          title: 'Checked in',
          key: 'checkIn',
          width: 110,
          render: (_, row) => (
            <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
              {formatClock(row.record.checkIn)}
            </span>
          ),
        }
      : {
          title: 'Late by',
          key: 'lateMinutes',
          width: 110,
          render: (_, row) =>
            row.record.lateMinutes > 0 ? (
              <Tag color="orange" style={{ margin: 0 }}>
                {formatMinutes(row.record.lateMinutes)}
              </Tag>
            ) : (
              <Text type="secondary" style={{ fontSize: 12 }}>
                —
              </Text>
            ),
        },
    {
      title: mode === 'missing' ? 'Shift end' : 'Checked in',
      key: mode === 'missing' ? 'shiftEnd' : 'checkIn',
      width: 110,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {mode === 'missing'
            ? row.record.shiftEndTime?.slice(0, 5) ?? '—'
            : formatClock(row.record.checkIn)}
        </span>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 100,
      render: (_, row) => (
        <StatusPill tone={ATTENDANCE_STATUS_TONE[row.record.status]} size="sm">
          {ATTENDANCE_STATUS_LABEL[row.record.status]}
        </StatusPill>
      ),
    },
    ...(onAddPunch
      ? ([
          {
            title: '',
            key: 'addPunch',
            width: 56,
            render: (_, row) => (
              <Button
                size="small"
                type="text"
                title="Add operator punch for this day"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={(e) => {
                  e.stopPropagation();
                  onAddPunch(row);
                }}
              />
            ),
          },
        ] satisfies TableProps<OperatorAttendanceRow>['columns'])
      : []),
  ];

  return (
    <Section
      title={
        <Space size={8} align="center">
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Icon size={16} strokeWidth={1.75} />
            {title}
          </span>
          <Tag
            color={tone === 'error' ? 'red' : 'orange'}
            style={{ margin: 0, fontVariantNumeric: 'tabular-nums' }}
          >
            {total.toLocaleString('en-PK')}
          </Tag>
        </Space>
      }
      subtitle={description}
      level={5}
    >
      <Card styles={{ body: { padding: data.length === 0 ? 18 : 0 } }}>
        {data.length === 0 && !loading ? (
          <EmptyState
            size="md"
            icon={mode === 'missing' ? Hourglass : Clock}
            title={mode === 'missing' ? 'No missing checkouts' : 'No late arrivals'}
            description={
              mode === 'missing'
                ? 'Everyone who checked in has also checked out.'
                : 'Nobody arrived past their shift start (after grace).'
            }
          />
        ) : (
          <Table<OperatorAttendanceRow>
            rowKey={(row) => row.record.id}
            loading={loading}
            dataSource={data}
            columns={columns}
            size="small"
            pagination={false}
            scroll={{ y: 360 }}
            onRow={(row) => ({
              onClick: () => onSelectRow(row),
              style: { cursor: 'pointer' },
            })}
            footer={
              total > data.length
                ? () => (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Showing the first {data.length} of{' '}
                      {total.toLocaleString('en-PK')}.
                    </Text>
                  )
                : undefined
            }
          />
        )}
      </Card>
    </Section>
  );
}

function NotCheckedInSection({
  rows,
  total,
  page,
  onPageChange,
  employeeCode,
  onEmployeeCodeChange,
  loading,
  fetching,
  error,
  onSelectRow,
}: {
  rows: NotCheckedInDto[] | undefined;
  total: number | undefined;
  page: number;
  onPageChange: (page: number) => void;
  employeeCode: string;
  onEmployeeCodeChange: (value: string) => void;
  loading: boolean;
  fetching: boolean;
  error: unknown;
  /** Row click opens the day-detail drawer instead of navigating to the directory. */
  onSelectRow: (row: NotCheckedInDto) => void;
}) {
  const data = rows ?? [];
  // `total` is totalElements across all pages; fall back to the loaded count.
  const count = total ?? data.length;
  const columns: TableProps<NotCheckedInDto>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, row) => (
        <PersonCell
          size="compact"
          person={{
            fullName: row.fullName,
            employeeCode: row.employeeCode,
          }}
        />
      ),
    },
    {
      title: 'Shift start',
      key: 'shiftStart',
      width: 100,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {row.shiftStartTime.slice(0, 5)}
          {row.graceMinutes > 0 ? (
            <Text type="secondary" style={{ marginLeft: 4, fontSize: 11 }}>
              +{row.graceMinutes}m
            </Text>
          ) : null}
        </span>
      ),
    },
    {
      title: 'Expected by',
      key: 'cutoff',
      width: 120,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {row.cutoff.slice(0, 5)}
        </span>
      ),
    },
    {
      title: 'Overdue',
      key: 'overdue',
      width: 120,
      render: (_, row) => (
        <Tag color="red" style={{ margin: 0, fontVariantNumeric: 'tabular-nums' }}>
          {formatMinutes(row.minutesOverdue)}
        </Tag>
      ),
    },
  ];

  return (
    <Section
      title={
        <Space size={8} align="center">
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Timer size={16} strokeWidth={1.75} />
            Not yet checked in
          </span>
          <Tag
            color="red"
            style={{ margin: 0, fontVariantNumeric: 'tabular-nums' }}
          >
            {count.toLocaleString('en-PK')}
          </Tag>
        </Space>
      }
      subtitle="Active employees past shift start + grace with no check-in punch. Refreshes every minute."
      level={5}
      actions={
        <Input
          allowClear
          placeholder="Employee code"
          value={employeeCode}
          onChange={(e) => onEmployeeCodeChange(e.target.value)}
          style={{ width: 180 }}
        />
      }
    >
      <Card styles={{ body: { padding: count === 0 ? 18 : 0 } }}>
        {error ? (
          <EmptyState
            size="md"
            icon={Timer}
            title="Couldn't load the roster"
            description="Try refreshing in a moment."
          />
        ) : count === 0 && !loading && employeeCode.trim() ? (
          <EmptyState
            size="md"
            icon={Timer}
            title="No match"
            description={`No one with a code containing "${employeeCode.trim()}" is waiting to check in.`}
          />
        ) : count === 0 && !loading ? (
          <EmptyState
            size="md"
            icon={CheckCircle2}
            title="Everyone is checked in"
            description="No active employee is past shift start + grace without a punch."
          />
        ) : (
          <Table<NotCheckedInDto>
            rowKey={(row) => row.employeeId}
            loading={loading || fetching}
            dataSource={data}
            columns={columns}
            size="small"
            onRow={(row) => ({
              onClick: () => onSelectRow(row),
              style: { cursor: 'pointer' },
            })}
            // Server-side pagination: dataSource holds only the current page
            // (≤ pageSize, so AntD won't re-slice it); `total` drives the pager.
            pagination={{
              current: page,
              pageSize: NOT_CHECKED_IN_PAGE_SIZE,
              total: count,
              showSizeChanger: false,
              hideOnSinglePage: true,
              showTotal: (n, [start, end]) =>
                `${start}–${end} of ${n.toLocaleString('en-PK')}`,
              onChange: onPageChange,
            }}
          />
        )}
      </Card>
    </Section>
  );
}

/* --------------------------- Day-detail drawers ------------------------ */
//
// Both action queues + the not-checked-in roster open the same kind of
// quick-glance drawer /time/team uses for a roll-call row, instead of
// navigating to the employee directory. The shell (day↔month toggle, month
// calendar) lives in shared `AttendanceDayDrawer` — only the day-specific
// body content is built here.

function NotCheckedInDrawer({
  row,
  date,
  onClose,
}: {
  row: NotCheckedInDto;
  date: string;
  onClose: () => void;
}) {
  const person: PersonCellPerson = {
    fullName: row.fullName,
    employeeCode: row.employeeCode,
  };

  return (
    <AttendanceDayDrawer
      employeeId={row.employeeId}
      person={person}
      date={date}
      statusPill={<StatusPill tone="error">Not checked in</StatusPill>}
      onClose={onClose}
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <Card styles={{ body: { padding: 16 } }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <DetailTile icon={LogIn} label="Shift start" value={row.shiftStartTime.slice(0, 5)} />
            <DetailTile icon={Timer} label="Expected by" value={row.cutoff.slice(0, 5)} />
          </div>
        </Card>

        <KpiGrid minColumnWidth={200} gap={12}>
          <StatCard
            variant="plain"
            tone="error"
            icon={Clock}
            label="Overdue"
            value={formatMinutes(row.minutesOverdue)}
          />
          <StatCard
            variant="plain"
            tone="neutral"
            icon={UserCheck}
            label="Grace period"
            value={row.graceMinutes > 0 ? formatMinutes(row.graceMinutes) : '—'}
          />
        </KpiGrid>
      </Space>
    </AttendanceDayDrawer>
  );
}

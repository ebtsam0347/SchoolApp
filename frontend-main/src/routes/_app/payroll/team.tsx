// /payroll/team — manager's team payslips.
//
// Manager-facing list of the caller's direct reports' payslips for a
// calendar year (optionally a single month). Backed by
// GET /payslips/team — server resolves the team via employee-service
// /me/team and hides DRAFT (managers see POSTED only; HR / ACCOUNTS with
// payroll.read see DRAFT too). Click a row → the same shared detail
// drawer the ESS page uses; download the PDF from there.
//
// Employee names aren't on PayslipDto (employeeId + employeeCode only), so
// we fetch /employees/me/team in parallel and join client-side for
// PersonCell. A caller with no direct reports gets an empty-state hint
// rather than the empty payslip page.

import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Empty,
  Segmented,
  Select,
  Space,
  Table,
  Tooltip,
  type TableColumnsType,
} from 'antd';
import { Download, Receipt, RefreshCcw, Users, Wallet } from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  MoneyCell,
  PageHeader,
  PersonCell,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { useAuth } from '@/auth';
import { forbiddenOr, getErrorMessage, isForbidden } from '@/lib';
import { employeesApi } from '@/people/api';
import type { EmployeeDto } from '@/people/types';
import {
  PAYSLIP_STATUS_LABEL,
  PAYSLIP_STATUS_TONE,
  PayslipDetailDrawer,
  formatPeriod,
  payslipsApi,
  type PayslipDto,
} from '@/payroll';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  attendanceBff,
  toIsoDate,
  toIsoMonth,
} from '@/attendance';
import {
  AttendanceDayDrawer,
  AttendanceRecordDayBody,
} from '@/attendance/admin';
import { Typography } from 'antd';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/team')({
  component: TeamPayslipsPage,
});

const NOW = new Date();
const CURRENT_YEAR = NOW.getFullYear();
const CURRENT_MONTH = NOW.getMonth() + 1;
const YEARS = [CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2, CURRENT_YEAR - 3];

const ALL_MONTHS = 0;
const MONTH_OPTIONS = [
  { value: ALL_MONTHS, label: 'All months' },
  ...Array.from({ length: 12 }, (_, i) => ({
    value: i + 1,
    label: new Date(2000, i, 1).toLocaleString('en-GB', { month: 'long' }),
  })),
];

function TeamPayslipsPage() {
  const { user } = useAuth();
  const [year, setYear] = useState<number>(CURRENT_YEAR);
  const [month, setMonth] = useState<number>(CURRENT_MONTH);
  const [openId, setOpenId] = useState<number | null>(null);
  // Employee-cell click opens the shared attendance day drawer
  // (manager-scoped month data, same as the Team Attendance drawer).
  const [attendanceId, setAttendanceId] = useState<number | null>(null);

  const teamQuery = useQuery({
    queryKey: ['employees', 'me', 'team'],
    queryFn: ({ signal }) => employeesApi.myTeam(signal),
    staleTime: 5 * 60_000,
    enabled: !!user?.employeeLinked,
    retry: false,
  });

  const payslipsQuery = useQuery({
    queryKey: ['payroll', 'payslips', 'team', year, month],
    queryFn: ({ signal }) =>
      payslipsApi.forTeam(
        { year, month: month === ALL_MONTHS ? undefined : month, size: 200 },
        signal,
      ),
    enabled: !!user?.employeeLinked,
  });

  const team = useMemo(() => teamQuery.data ?? [], [teamQuery.data]);
  const teamById = useMemo(() => {
    const m = new Map<number, EmployeeDto>();
    for (const e of team) if (e.id != null) m.set(e.id, e);
    return m;
  }, [team]);

  const rows = payslipsQuery.data?.content ?? [];
  const totals = rows.reduce(
    (acc, r) => ({
      net: acc.net + r.net,
      gross: acc.gross + r.gross,
      held: acc.held + (r.status === 'HELD' ? 1 : 0),
    }),
    { net: 0, gross: 0, held: 0 },
  );
  const distinctEmployees = new Set(rows.map((r) => r.employeeId)).size;

  const columns: TableColumnsType<PayslipDto> = [
    {
      title: 'Employee',
      key: 'employee',
      // Clicking the employee cell opens the attendance day drawer; the
      // rest of the row still opens the payslip detail drawer. Stop
      // bubble so the two handlers don't fight.
      onCell: (r) => ({
        onClick: (e) => {
          e.stopPropagation();
          setAttendanceId(r.employeeId);
        },
      }),
      render: (_v, r) => {
        const e = teamById.get(r.employeeId);
        return (
          <PersonCell
            size="compact"
            person={{
              fullName: e?.fullName ?? `Employee #${r.employeeId}`,
              employeeCode: r.employeeCode,
              profilePictureUrl: e?.profilePictureUrl ?? null,
            }}
          />
        );
      },
    },
    {
      title: 'Period',
      key: 'period',
      render: (_v, r) => (
        <span style={{ fontWeight: 500 }}>
          {formatPeriod(r.periodYear, r.periodMonth)}
        </span>
      ),
    },
    {
      title: 'Gross',
      dataIndex: 'gross',
      key: 'gross',
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} />,
    },
    {
      title: 'Deductions',
      key: 'deductions',
      align: 'right',
      render: (_v, r) => <MoneyCell amount={r.deductions + r.tax + r.eobi} />,
    },
    {
      title: 'Net',
      dataIndex: 'net',
      key: 'net',
      align: 'right',
      render: (v: number) => (
        <MoneyCell amount={v} style={{ fontWeight: 600 }} />
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: PayslipDto['status']) => (
        <StatusPill tone={PAYSLIP_STATUS_TONE[s]}>
          {PAYSLIP_STATUS_LABEL[s]}
        </StatusPill>
      ),
    },
    {
      title: '',
      key: 'pdf',
      width: 56,
      align: 'right',
      render: (_v, r) =>
        r.pdfObjectKey ? (
          <Tooltip title="Download PDF">
            <Button
              type="text"
              size="small"
              icon={<Download size={14} strokeWidth={1.75} />}
              onClick={async (e) => {
                e.stopPropagation();
                try {
                  const { url } = await payslipsApi.pdfUrl(r.id);
                  window.open(url, '_blank', 'noopener');
                } catch (err) {
                  console.error(err);
                }
              }}
              aria-label="Download PDF"
            />
          </Tooltip>
        ) : null,
    },
  ];

  const headerActions = user?.employeeLinked ? (
    <Tooltip title="Refresh">
      <Button
        icon={<RefreshCcw size={14} strokeWidth={1.75} />}
        onClick={() => payslipsQuery.refetch()}
        loading={payslipsQuery.isFetching}
        aria-label="Refresh"
      />
    </Tooltip>
  ) : null;

  // Forbidden trumps every other state — surface a single clean message.
  const forbidden =
    isForbidden(payslipsQuery.error) || isForbidden(teamQuery.error);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="Money"
        title="Team payslips"
        description="Posted payslips for your direct reports. Click a row for the line breakdown and PDF."
        actions={headerActions}
      />

      {!user?.employeeLinked ? (
        <EmptyState
          icon={Users}
          title="No employee record linked to your account"
          description="Team payslips are scoped to your direct reports, so we need a linked employee profile first."
        />
      ) : forbidden ? (
        <Alert
          type="error"
          showIcon
          title="You don't have access to team payslips"
          description="Ask HR to grant `payroll.read.team` if you should be managing this team."
        />
      ) : (
        <>
          <KpiGrid>
            <StatCard
              label="Net (filtered)"
              value={<MoneyCell amount={totals.net} compact />}
              icon={Wallet}
              tone="primary"
              variant="accent-radial"
            />
            <StatCard
              label="Gross (filtered)"
              value={<MoneyCell amount={totals.gross} compact />}
              icon={Receipt}
              tone="success"
            />
            <StatCard
              label="Employees on slips"
              value={`${distinctEmployees}${
                team.length ? ` / ${team.length}` : ''
              }`}
              tone="info"
            />
            <StatCard
              label="Held"
              value={totals.held.toString()}
              tone={totals.held > 0 ? 'warning' : 'info'}
            />
          </KpiGrid>

          <Section
            title={
              month === ALL_MONTHS
                ? `${year} payslips`
                : `${formatPeriod(year, month)} payslips`
            }
            actions={
              <Space wrap>
                <Segmented
                  value={year}
                  onChange={(v) => setYear(Number(v))}
                  options={YEARS.map((y) => ({ label: String(y), value: y }))}
                />
                <Select
                  value={month}
                  onChange={(v) => setMonth(v)}
                  options={MONTH_OPTIONS}
                  style={{ width: 160 }}
                />
              </Space>
            }
          >
            {payslipsQuery.isError ? (
              <Alert
                type="error"
                showIcon
                title="Couldn't load team payslips"
                description={forbiddenOr(
                  payslipsQuery.error,
                  "You don't have permission to view team payslips.",
                  getErrorMessage(payslipsQuery.error),
                )}
              />
            ) : team.length === 0 && !teamQuery.isLoading ? (
              <EmptyState
                icon={Users}
                title="You have no direct reports"
                description="This page lists payslips for employees who report to you. None are currently assigned."
              />
            ) : rows.length === 0 && !payslipsQuery.isLoading ? (
              <EmptyState
                icon={Receipt}
                title={
                  month === ALL_MONTHS
                    ? `No payslips for ${year}`
                    : `No payslips for ${formatPeriod(year, month)}`
                }
                description="Payslips appear here once HR posts the monthly payroll run."
              />
            ) : (
              <Table<PayslipDto>
                rowKey="id"
                loading={payslipsQuery.isLoading}
                dataSource={rows}
                columns={columns}
                // Keep the columns scrolling inside the table on a phone
                // rather than dragging the page sideways.
                scroll={{ x: 'max-content' }}
                pagination={{
                  pageSize: 25,
                  showSizeChanger: true,
                  pageSizeOptions: [25, 50, 100],
                  showTotal: (t, [from, to]) => `${from}–${to} of ${t}`,
                }}
                onRow={(r) => ({
                  onClick: () => setOpenId(r.id),
                  style: { cursor: 'pointer' },
                })}
                locale={{ emptyText: <Empty description="No payslips" /> }}
              />
            )}
          </Section>
        </>
      )}

      <PayslipDetailDrawer
        open={openId != null}
        id={openId}
        onClose={() => setOpenId(null)}
      />
      {attendanceId != null ? (
        <TeamEmployeeAttendanceDrawer
          employeeId={attendanceId}
          employee={teamById.get(attendanceId)}
          onClose={() => setAttendanceId(null)}
        />
      ) : null}
    </div>
  );
}

/** Employee-cell drawer: reuses the shared attendance day↔month shell
 *  with manager-scoped month data (teamMonth) — the same endpoints the
 *  Team Attendance drawer uses, so plain managers don't 403. Today's
 *  record comes from the current month's days. */
function TeamEmployeeAttendanceDrawer({
  employeeId,
  employee,
  onClose,
}: {
  employeeId: number;
  employee: EmployeeDto | undefined;
  onClose: () => void;
}) {
  const today = toIsoDate(new Date());
  const monthQuery = useQuery({
    queryKey: ['bff', 'attendance', 'team', employeeId, 'month', toIsoMonth(new Date())],
    queryFn: ({ signal }) =>
      attendanceBff.teamMonth(employeeId, toIsoMonth(new Date()), signal),
  });
  const record =
    monthQuery.data?.days.find((d) => d.workDate === today)?.record ?? null;

  return (
    <AttendanceDayDrawer
      employeeId={employeeId}
      person={{
        fullName: employee?.fullName ?? `Employee #${employeeId}`,
        employeeCode: employee?.employeeCode ?? null,
        profilePictureUrl: employee?.profilePictureUrl ?? undefined,
      }}
      date={today}
      monthScope="team"
      statusPill={
        record ? (
          <StatusPill tone={ATTENDANCE_STATUS_TONE[record.status]}>
            {ATTENDANCE_STATUS_LABEL[record.status]}
          </StatusPill>
        ) : undefined
      }
      onClose={onClose}
    >
      {record ? (
        <AttendanceRecordDayBody record={record} />
      ) : (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {monthQuery.isLoading
            ? 'Loading today\'s attendance…'
            : 'No attendance record for today — open the full month below.'}
        </Text>
      )}
    </AttendanceDayDrawer>
  );
}

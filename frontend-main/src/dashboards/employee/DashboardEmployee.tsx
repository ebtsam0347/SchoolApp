import { Button, Card, Progress, Space, theme as antdTheme, Tooltip, Typography } from 'antd';
import { Link } from '@tanstack/react-router';
import { CalendarCheck, Eye, EyeOff, FileText, Inbox, Plane, Wallet } from 'lucide-react';

import {
  AttendanceHeatmap,
  ChartCard,
  KpiGrid,
  LineSpark,
  MetricRing,
  MoneyCell,
  NeedsYouList,
  PageHeader,
  RequestState,
  StatCard,
  StatusPill,
} from '@/components';
import type { ChartTone, NeedsYouItem } from '@/components';
import { forbiddenOr } from '@/lib/api-error';
import {
  formatDateLong,
  formatDateMonthDay,
  formatDateShort,
} from '@/lib/dates';

import { useAuth } from '@/auth';

import { useEmployeeDashboard } from './api';
import { useSalaryMasked } from './use-salary-masked';
import { welcomeTitle } from '../greeting';
import { RoleSwitcher } from '../RoleSwitcher';
import type {
  EmployeeDashboardDto,
  EmployeeTodayStatus,
  LeaveBalanceTypeDto,
} from '../types';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

function MaskedAmount({
  amount,
  masked,
  compact,
}: {
  amount: number;
  masked: boolean;
  compact?: boolean;
}) {
  if (masked) {
    return (
      <span
        aria-label="Salary hidden"
        style={{ fontVariantNumeric: 'tabular-nums', letterSpacing: '0.12em' }}
      >
        ••••••
      </span>
    );
  }
  return <MoneyCell amount={amount} compact={compact} />;
}

function MaskToggleButton({
  masked,
  onToggle,
  onSolid,
}: {
  masked: boolean;
  onToggle: () => void;
  onSolid?: boolean;
}) {
  const Icon = masked ? Eye : EyeOff;
  const label = masked ? 'Show salary' : 'Hide salary';
  return (
    <Tooltip title={label}>
      <Button
        type="text"
        size="small"
        aria-label={label}
        onClick={onToggle}
        icon={<Icon size={16} strokeWidth={1.75} />}
        style={onSolid ? { color: 'rgba(255,255,255,0.9)' } : undefined}
      />
    </Tooltip>
  );
}

export function DashboardEmployee() {
  const query = useEmployeeDashboard();
  const { user } = useAuth();

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow={`Today · ${formatDateMonthDay(new Date().toISOString())}`}
        title={welcomeTitle(user?.fullName)}
        description="Your day at a glance — what's happening, what needs you, and what's next."
        actions={<RoleSwitcher />}
      />

      <RequestState
        loading={query.isLoading}
        error={query.error}
        empty={!query.data}
        errorTitle={forbiddenOr(
          query.error,
          "You don't have a dashboard yet",
          "Couldn't load your dashboard",
        )}
        emptyTitle="Nothing to show yet"
      >
        {query.data ? <EmployeeBody data={query.data} /> : null}
      </RequestState>
    </Space>
  );
}

function EmployeeBody({ data }: { data: EmployeeDashboardDto }) {
  const [salaryMasked, toggleSalaryMasked] = useSalaryMasked();

  const needsYouItems: NeedsYouItem[] = [
    ...data.needsYou.pendingRequests.items.map((it) => ({
      id: it.id,
      title: it.kindLabel,
      description: it.summary,
      meta: <StatusPill tone="info">Pending</StatusPill>,
      tone: 'info' as ChartTone,
      to: it.href,
    })),
    ...data.needsYou.missingPunches.items.map((it, idx) => ({
      id: `mp-${idx}`,
      title: 'Missing punch',
      description: `${formatDateShort(it.date)} · ${it.hint ?? 'Submit a regularization'}`,
      meta: <StatusPill tone="warning">Action</StatusPill>,
      tone: 'warning' as ChartTone,
      to: it.href,
    })),
    ...data.needsYou.documentsToAck.items.map((it) => ({
      id: it.id,
      title: it.title,
      description: it.effectiveOn
        ? `Effective ${formatDateShort(it.effectiveOn)}`
        : 'Acknowledgement required',
      meta: <StatusPill tone="neutral">Acknowledge</StatusPill>,
      tone: 'primary' as ChartTone,
      to: it.href,
    })),
  ];

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      {/* Hero band */}
      <KpiGrid minColumnWidth={320}>
        <StatCard
          variant="hero"
          tone="primary"
          icon={Wallet}
          label="Previous Month Salary"
          value={
            data.nextPayday.projectedNet ? (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
                <MaskedAmount
                  amount={data.nextPayday.projectedNet.amount}
                  masked={salaryMasked}
                  compact
                />
                <MaskToggleButton
                  masked={salaryMasked}
                  onToggle={toggleSalaryMasked}
                  onSolid
                />
              </span>
            ) : (
              <span style={{ opacity: 0.85 }}>Not yet scheduled</span>
            )
          }
          delta={
            data.nextPayday.date ? (
              <Space size={4}>
                <span>{formatDateLong(data.nextPayday.date)}</span>
                <span>·</span>
                <span style={{ opacity: 0.85 }}>
                  {data.nextPayday.runStatus === 'POSTED'
                    ? 'Posted'
                    : data.nextPayday.runStatus === 'LOCKED'
                      ? 'Locked'
                      : data.nextPayday.runStatus === 'RUNNING'
                        ? 'Processing'
                        : 'Draft run'}
                </span>
              </Space>
            ) : (
              'No active run yet'
            )
          }
        />
        <TodayCard data={data.today} />
      </KpiGrid>

      {/* Leave balance — inline rings, the signature pattern */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <SectionHead
          title="Leave balance"
          subtitle={`As of ${formatDateShort(data.leaveBalance.asOf)} · used + pending out of total days.`}
        />
        <KpiGrid minColumnWidth={200}>
          {data.leaveBalance.types.map((lb) => (
            <LeaveBalanceCard key={lb.code} balance={lb} />
          ))}
        </KpiGrid>
      </div>

      {/* Needs you + Attendance heatmap */}
      <KpiGrid minColumnWidth={420} gap={16}>
        <Card
          title="Needs you"
          extra={
            <Text type="secondary" style={{ fontSize: 12 }}>
              {needsYouItems.length} item{needsYouItems.length === 1 ? '' : 's'}
            </Text>
          }
          styles={{ body: { padding: '8px 8px 14px' } }}
        >
          <NeedsYouList
            items={needsYouItems}
            emptyTitle="All clear"
            emptyDescription="Nothing waiting on you right now — go enjoy the day."
            emptyIcon={Inbox}
          />
        </Card>

        <ChartCard
          title="Your month at a glance"
          subtitle="Coloured by punch-in variance vs your shift start. The drift surfaces 'creeping later' before it becomes a habit."
          height={360}
        >
          <AttendanceHeatmap
            month={data.attendanceHeatmap.month}
            data={data.attendanceHeatmap.days}
            scale="diverging"
          />
        </ChartCard>
      </KpiGrid>

      {/* Payslips + Upcoming holidays */}
      <KpiGrid minColumnWidth={380} gap={16}>
        <PayslipsCard
          data={data.payslips}
          masked={salaryMasked}
          onToggleMask={toggleSalaryMasked}
        />
        <UpcomingHolidaysCard holidays={data.upcomingHolidays} />
      </KpiGrid>
    </Space>
  );
}

/* ——— sections ——— */

function SectionHead({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div>
      <Title level={5} style={{ margin: 0 }}>
        {title}
      </Title>
      {subtitle ? (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {subtitle}
        </Text>
      ) : null}
    </div>
  );
}

function TodayCard({ data }: { data: EmployeeDashboardDto['today'] }) {
  const { token } = useToken();
  const hasShift = !!data.shift;
  const elapsed = data.elapsedMinutes ?? 0;
  const shiftStartMinutes = data.shift ? parseHHMMToMinutes(data.shift.start) : 0;
  const shiftEndMinutes = data.shift ? parseHHMMToMinutes(data.shift.end) : 0;
  const shiftLength = Math.max(1, shiftEndMinutes - shiftStartMinutes);
  const elapsedPct = Math.max(
    0,
    Math.min(100, Math.round((elapsed / shiftLength) * 100)),
  );

  return (
    <Card styles={{ body: { padding: 22 } }}>
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        <Space size={10} align="center">
          <TodayStatusBadge status={data.status} />
          {hasShift ? (
            <Text type="secondary" style={{ fontSize: 13 }}>
              {data.shift!.name} · {data.shift!.start} → {data.shift!.end}
            </Text>
          ) : (
            <Text type="secondary" style={{ fontSize: 13 }}>
              No shift assigned today
            </Text>
          )}
        </Space>
        <Title level={3} style={{ margin: 0, letterSpacing: '-0.01em' }}>
          {data.checkInAt ? `Checked in at ${data.checkInAt}` : 'Not checked in yet'}
        </Title>
        {hasShift ? (
          <div>
            <Progress
              percent={elapsedPct}
              showInfo={false}
              strokeColor={token.colorPrimary}
              trailColor={token.colorFillTertiary}
              size={['default', 8]}
            />
            <div
              style={{
                marginTop: 6,
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: 12,
                color: token.colorTextSecondary,
              }}
            >
              <span>
                {minutesAsHM(elapsed)} of {minutesAsHM(shiftLength)} done
              </span>
              <span>{minutesAsHM(Math.max(0, shiftLength - elapsed))} to EOD</span>
            </div>
          </div>
        ) : null}
      </Space>
    </Card>
  );
}

function TodayStatusBadge({ status }: { status: EmployeeTodayStatus }) {
  switch (status) {
    case 'CHECKED_IN':
      return <StatusPill tone="success">Checked in</StatusPill>;
    case 'ON_LEAVE':
      return <StatusPill tone="info">On leave</StatusPill>;
    case 'PENDING_PUNCH':
      return <StatusPill tone="warning">Pending punch</StatusPill>;
    case 'OFF':
    default:
      return <StatusPill tone="neutral">Off today</StatusPill>;
  }
}

function LeaveBalanceCard({ balance }: { balance: LeaveBalanceTypeDto }) {
  const { token } = useToken();
  const tone: ChartTone = balance.code === 'SICK' ? 'warning' : 'primary';
  const tonePending: ChartTone = 'info';
  const toneRemaining: ChartTone = 'success';

  return (
    <Card
      styles={{
        body: {
          padding: 16,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 10,
        },
      }}
    >
      <MetricRing
        size={116}
        thickness={0.18}
        segments={[
          balance.used > 0 ? { value: balance.used, tone, label: 'Used' } : null,
          balance.pending > 0
            ? { value: balance.pending, tone: tonePending, label: 'Pending' }
            : null,
          { value: Math.max(0, balance.remaining), tone: toneRemaining, label: 'Remaining' },
        ].filter((x): x is NonNullable<typeof x> => x !== null)}
        label={`${balance.remaining}`}
        caption="left"
        tooltipValueFormatter={(v, name) =>
          `${v} day${v === 1 ? '' : 's'}${name === 'Remaining' ? ` of ${balance.total}` : ''}`
        }
      />
      <div style={{ textAlign: 'center' }}>
        <Text style={{ fontSize: 13, fontWeight: 600, color: token.colorTextHeading }}>
          {balance.label}
        </Text>
        <div
          style={{
            fontSize: 11,
            color: token.colorTextTertiary,
            display: 'flex',
            justifyContent: 'center',
            gap: 8,
            marginTop: 2,
          }}
        >
          <span>{balance.used} used</span>
          {balance.pending > 0 ? <span>· {balance.pending} pending</span> : null}
          <span>· {balance.total} total</span>
        </div>
      </div>
    </Card>
  );
}

function PayslipsCard({
  data,
  masked,
  onToggleMask,
}: {
  data: EmployeeDashboardDto['payslips'];
  masked: boolean;
  onToggleMask: () => void;
}) {
  const { token } = useToken();
  const trend = data.netTrend12m.map((p) => p.amount);

  return (
    <Card
      title="Recent payslips"
      extra={
        <Space size={8}>
          <MaskToggleButton masked={masked} onToggle={onToggleMask} />
          <Link to="/payroll/payslips" style={{ fontSize: 13, color: token.colorPrimary }}>
            All payslips
          </Link>
        </Space>
      }
      styles={{ body: { padding: 0 } }}
    >
      <div
        style={{
          padding: '12px 18px 4px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <div>
          <Text type="secondary" style={{ fontSize: 11, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
            Net pay · last 12 months
          </Text>
          <div
            style={{
              fontSize: 22,
              fontWeight: 700,
              letterSpacing: '-0.01em',
              color: token.colorTextHeading,
              fontVariantNumeric: 'tabular-nums',
              lineHeight: 1.1,
            }}
          >
            <MaskedAmount amount={trend[trend.length - 1] ?? 0} masked={masked} compact />
          </div>
        </div>
        <div
          aria-hidden={masked}
          style={{
            filter: masked ? 'blur(8px)' : undefined,
            opacity: masked ? 0.5 : 1,
            transition: 'filter 200ms ease, opacity 200ms ease',
            pointerEvents: masked ? 'none' : undefined,
          }}
        >
          <LineSpark data={trend} tone="success" area height={36} width={140} />
        </div>
      </div>
      <ul
        style={{
          listStyle: 'none',
          margin: 0,
          padding: 0,
          borderTop: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        {data.recent.map((p, i) => (
          <li
            key={p.id}
            style={{
              padding: '10px 18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderTop: i === 0 ? 'none' : `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            <Space size={10}>
              <FileText size={16} strokeWidth={1.75} color={token.colorTextTertiary} aria-hidden />
              <Text style={{ fontSize: 14, fontWeight: 500, color: token.colorTextHeading }}>
                {formatPeriod(p.period)}
              </Text>
            </Space>
            <Space size={12}>
              <Text style={{ fontSize: 14, fontWeight: 500 }}>
                <MaskedAmount amount={p.net.amount} masked={masked} />
              </Text>
              {p.pdfUrl ? (
                <Button size="small" type="link" href={p.pdfUrl}>
                  Download
                </Button>
              ) : null}
            </Space>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function UpcomingHolidaysCard({
  holidays,
}: {
  holidays: EmployeeDashboardDto['upcomingHolidays'];
}) {
  const { token } = useToken();

  if (holidays.length === 0) {
    return (
      <Card
        title="Upcoming holidays"
        styles={{ body: { padding: 24, display: 'grid', placeItems: 'center', minHeight: 180 } }}
      >
        <Space orientation="vertical" align="center">
          <CalendarCheck size={28} color={token.colorTextTertiary} aria-hidden />
          <Text type="secondary" style={{ fontSize: 13 }}>
            No public holidays in the next 90 days.
          </Text>
        </Space>
      </Card>
    );
  }

  return (
    <Card
      title="Upcoming holidays"
      extra={
        <Link to="/time/holidays" style={{ fontSize: 13, color: token.colorPrimary }}>
          Calendar
        </Link>
      }
      styles={{ body: { padding: 0 } }}
    >
      <ul
        style={{
          listStyle: 'none',
          margin: 0,
          padding: 0,
        }}
      >
        {holidays.map((h, i) => (
          <li
            key={h.date}
            style={{
              padding: '14px 18px',
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              borderTop: i === 0 ? 'none' : `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            <div
              aria-hidden
              style={{
                width: 44,
                height: 44,
                borderRadius: 8,
                background: token.colorFillTertiary,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <span
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  letterSpacing: '0.08em',
                  textTransform: 'uppercase',
                  color: token.colorTextTertiary,
                }}
              >
                {h.dayOfWeek}
              </span>
              <span
                style={{
                  fontSize: 16,
                  fontWeight: 700,
                  color: token.colorTextHeading,
                  lineHeight: 1,
                }}
              >
                {new Date(h.date + 'T12:00:00').getDate()}
              </span>
            </div>
            <div style={{ minWidth: 0, flex: 1 }}>
              <Text style={{ fontSize: 14, fontWeight: 500, color: token.colorTextHeading }}>
                {h.name}
              </Text>
              <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                {formatDateLong(h.date)}
              </Text>
            </div>
            <Plane
              size={18}
              strokeWidth={1.75}
              color={token.colorTextTertiary}
              aria-hidden
              style={{ flexShrink: 0 }}
            />
          </li>
        ))}
      </ul>
    </Card>
  );
}

/* ——— small helpers ——— */

function parseHHMMToMinutes(hhmm: string): number {
  const m = /^(\d{1,2}):(\d{2})/.exec(hhmm);
  if (!m) return 0;
  return Number(m[1]) * 60 + Number(m[2]);
}

function minutesAsHM(min: number): string {
  const h = Math.floor(min / 60);
  const m = min % 60;
  if (h === 0) return `${m} m`;
  if (m === 0) return `${h} h`;
  return `${h} h ${m} m`;
}

function formatPeriod(ym: string): string {
  // YYYY-MM → "Mar 2026"
  const m = /^(\d{4})-(\d{2})$/.exec(ym);
  if (!m) return ym;
  const date = new Date(Number(m[1]), Number(m[2]) - 1, 1, 12, 0, 0);
  return date.toLocaleDateString('en-PK', { month: 'short', year: 'numeric' });
}

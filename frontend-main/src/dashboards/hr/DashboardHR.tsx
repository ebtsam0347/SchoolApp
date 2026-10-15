import { Card, Space, theme as antdTheme, Typography } from 'antd';
import { Link } from '@tanstack/react-router';
import {
  AlertTriangle,
  Briefcase,
  CalendarClock,
  ChevronRight,
  Clock,
  FileWarning,
  Plane,
  ShieldAlert,
  UserPlus,
  Users,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import {
  ChartCard,
  HorizontalBars,
  KpiGrid,
  NeedsYouList,
  PageHeader,
  RequestState,
  StatCard,
  StatusPill,
  TrendLine,
  alphaMix,
  useChartTheme,
} from '@/components';
import type { ChartTone, NeedsYouItem } from '@/components';
import { forbiddenOr } from '@/lib/api-error';
import {
  formatDateMonthDay,
  formatDateShort,
  formatDateTime,
} from '@/lib/dates';

import { useAuth } from '@/auth';

import { useHrDashboard } from './api';
import { welcomeTitle } from '../greeting';
import { RoleSwitcher } from '../RoleSwitcher';
import type { HrDashboardDto } from '../types';

const { Text } = Typography;
const { useToken } = antdTheme;

export function DashboardHR() {
  const query = useHrDashboard();
  const { user } = useAuth();

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow={`People ops · ${formatDateMonthDay(new Date().toISOString())}`}
        title={welcomeTitle(user?.fullName)}
        description="Org-wide pulse, queues that need triage, and headcount + audit signal worth watching."
        actions={<RoleSwitcher />}
      />
      <RequestState
        loading={query.isLoading}
        error={query.error}
        empty={!query.data}
        errorTitle={forbiddenOr(
          query.error,
          "You don't have access to org-wide views",
          "Couldn't load the HR dashboard",
        )}
        emptyTitle="No org data yet"
      >
        {query.data ? <HrBody data={query.data} /> : null}
      </RequestState>
    </Space>
  );
}

function HrBody({ data }: { data: HrDashboardDto }) {
  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      {/* Hero band */}
      <KpiGrid minColumnWidth={240}>
        <StatCard
          variant="hero"
          tone="primary"
          icon={Users}
          label="Org pulse today"
          value={`${Math.round((data.orgPulse.presentToday / data.orgPulse.headcount) * 100)}%`}
          delta={
            <Space size={6}>
              <span>{data.orgPulse.presentToday} of {data.orgPulse.headcount} present</span>
            </Space>
          }
        />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone="warning"
          icon={Clock}
          label="Late today"
          value={data.orgPulse.lateToday}
          delta="punch-in past shift start"
        />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone="info"
          icon={Plane}
          label="On leave today"
          value={data.orgPulse.onLeaveToday}
          delta="approved leave"
        />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone="error"
          icon={AlertTriangle}
          label="Absent today"
          value={data.orgPulse.absentToday}
          delta="no punch + no leave"
          trend={data.orgPulse.absentToday > 0 ? 'down' : 'neutral'}
        />
      </KpiGrid>

      {/* Headline trend with anomaly markers */}
      <ChartCard
        title="Org attendance · last 30 days"
        subtitle="Anomaly dots mark days outside 1σ of the trailing 14-day mean."
        actions={
          <Link to="/time/attendance" style={{ fontSize: 13 }}>
            Open attendance
          </Link>
        }
        height={340}
      >
        <TrendLine
          data={data.attendance30d.days as unknown as Array<Record<string, unknown>>}
          xKey="date"
          xTickFormatter={(v) => formatDateMonthDay(String(v))}
          yTickFormatter={(v) => `${Math.round(v * 100)}%`}
          valueFormatter={(v) => `${Math.round(v * 100)}%`}
          isAnomaly={(row) => Boolean(row.anomaly)}
          series={[{ key: 'rate', label: 'Attendance', tone: 'primary' }]}
          emptyTitle="No attendance signal yet"
          emptyDescription="Punch data hasn't accumulated for this window. Trend appears once the org has at least a few working days logged."
        />
      </ChartCard>

      {/* Triage queues + dept leave hotspots */}
      <KpiGrid minColumnWidth={420} gap={16}>
        <Card
          title="Needs HR triage"
          extra={
            <Text type="secondary" style={{ fontSize: 12 }}>
              {totalQueueCount(data.queues)} open
            </Text>
          }
          styles={{ body: { padding: '8px 8px 14px' } }}
        >
          <NeedsYouList
            items={triageItems(data)}
            emptyTitle="Inbox zero"
            emptyDescription="No queues need attention right now."
          />
        </Card>

        <ChartCard
          title="Leave usage by department · last 30 days"
          subtitle="Total leave days taken. Horizontal bars sort by usage; toggle to see all."
          height={Math.max(320, data.departmentLeave30d.length * 52 + 60)}
        >
          <HorizontalBars
            rows={data.departmentLeave30d.map((d, i) => ({
              id: d.departmentId,
              label: d.name,
              value: d.leaveDays,
              caption:
                d.headcount !== undefined
                  ? `${(d.leaveDays / d.headcount).toFixed(2)} per head · ${d.headcount} people`
                  : undefined,
              tone: pickDeptTone(i, d.leaveDays, data.departmentLeave30d[0]?.leaveDays ?? 1),
            }))}
            valueFormatter={(v) => `${v} d`}
            topN={6}
            emptyTitle="No leave logged"
            emptyDescription="Departments haven't taken any leave in the last 30 days."
          />
        </ChartCard>
      </KpiGrid>

      {/* Headcount + audit anomalies */}
      <KpiGrid minColumnWidth={420} gap={16}>
        <ChartCard
          title="Headcount · last 12 months"
          subtitle="Joiners (success) stacked on leavers (error). Net headcount drives the running total."
          height={300}
        >
          <HeadcountArea data={data.headcount12m.months} />
        </ChartCard>

        <Card
          title="Recent audit anomalies"
          extra={
            <Link to="/admin/audit" style={{ fontSize: 13 }}>
              Audit explorer
            </Link>
          }
          styles={{ body: { padding: 0 } }}
        >
          <AuditAnomaliesList rows={data.recentAuditAnomalies} />
        </Card>
      </KpiGrid>
    </Space>
  );
}

/* —————— sub-components —————— */

function HeadcountArea({
  data,
}: {
  data: HrDashboardDto['headcount12m']['months'];
}) {
  const t = useChartTheme();
  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
        <CartesianGrid
          stroke={t.grid.stroke}
          strokeDasharray={t.grid.strokeDasharray}
          vertical={false}
        />
        <XAxis
          dataKey="month"
          tick={t.axis.tick}
          stroke={t.axis.stroke}
          tickLine={false}
          axisLine={{ stroke: t.axis.stroke }}
          tickFormatter={(v) => formatYM(String(v))}
        />
        <YAxis
          tick={t.axis.tick}
          stroke={t.axis.stroke}
          tickLine={false}
          axisLine={false}
          width={40}
        />
        <Tooltip
          contentStyle={t.tooltip.contentStyle}
          itemStyle={t.tooltip.itemStyle}
          labelStyle={t.tooltip.labelStyle}
          labelFormatter={(v) => formatYM(String(v))}
        />
        <Legend
          verticalAlign="bottom"
          height={28}
          iconType="circle"
          iconSize={8}
          wrapperStyle={{ fontSize: 12, color: t.textMuted }}
        />
        <Area
          type="monotone"
          dataKey="joiners"
          name="Joiners"
          stackId="1"
          stroke={t.tones.success}
          fill={alphaMix(t.tones.success, 28)}
          strokeWidth={2}
        />
        <Area
          type="monotone"
          dataKey="leavers"
          name="Leavers"
          stackId="1"
          stroke={t.tones.error}
          fill={alphaMix(t.tones.error, 28)}
          strokeWidth={2}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}

function AuditAnomaliesList({ rows }: { rows: HrDashboardDto['recentAuditAnomalies'] }) {
  const { token } = useToken();

  if (rows.length === 0) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Text type="secondary" style={{ fontSize: 13 }}>
          No anomalies in the last 24 hours.
        </Text>
      </div>
    );
  }

  return (
    <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
      {rows.map((row, i) => (
        <li
          key={row.id}
          style={{
            padding: '14px 18px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            borderTop: i === 0 ? 'none' : `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <ShieldAlert
            size={16}
            strokeWidth={1.75}
            color={token.colorWarning}
            aria-hidden
            style={{ flexShrink: 0, marginTop: 3 }}
          />
          <div style={{ minWidth: 0, flex: 1 }}>
            <Text style={{ fontSize: 14, fontWeight: 500, color: token.colorTextHeading }}>
              {row.summary}
            </Text>
            <Text
              type="secondary"
              style={{ display: 'block', fontSize: 12, marginTop: 2 }}
            >
              {formatDateTime(row.occurredAt)}
            </Text>
          </div>
          {row.href ? (
            <Link
              to={row.href}
              style={{
                color: token.colorPrimary,
                display: 'flex',
                alignItems: 'center',
                fontSize: 12,
                gap: 2,
                flexShrink: 0,
              }}
            >
              Inspect
              <ChevronRight size={14} strokeWidth={2} aria-hidden />
            </Link>
          ) : null}
        </li>
      ))}
    </ul>
  );
}

/* —————— helpers —————— */

function totalQueueCount(q: HrDashboardDto['queues']): number {
  return (
    q.onboardingStuck.count +
    q.contractsExpiring.count +
    q.disciplinaryOpen.count +
    q.leaveHotspots.count
  );
}

function triageItems(data: HrDashboardDto): NeedsYouItem[] {
  const items: NeedsYouItem[] = [];

  for (const r of data.queues.onboardingStuck.items) {
    items.push({
      id: `onb-${r.id}`,
      title: `Onboarding stuck · ${r.personName}`,
      description: `${r.stage} · ${r.ageDays} days in stage`,
      meta: <StatusPill tone="warning">Onboarding</StatusPill>,
      tone: 'warning',
      to: r.href,
      leading: <UserPlus size={16} strokeWidth={1.75} aria-hidden />,
    });
  }

  for (const r of data.queues.contractsExpiring.items.slice(0, 3)) {
    items.push({
      id: `ct-${r.id}`,
      title: `Contract expires · ${r.personName}`,
      description: `${r.contractType} · in ${r.daysLeft} days (${formatDateShort(r.expiresOn)})`,
      meta: (
        <StatusPill tone={r.daysLeft <= 14 ? 'error' : 'warning'}>
          {r.daysLeft <= 7 ? 'Urgent' : `${r.daysLeft} d`}
        </StatusPill>
      ),
      tone: r.daysLeft <= 14 ? 'error' : 'warning',
      to: r.href,
      leading: <CalendarClock size={16} strokeWidth={1.75} aria-hidden />,
    });
  }

  for (const r of data.queues.disciplinaryOpen.items) {
    items.push({
      id: `dp-${r.id}`,
      title: `Disciplinary open · ${r.personName}`,
      description: `${r.level} · ${r.status}`,
      meta: <StatusPill tone="error">Open</StatusPill>,
      tone: 'error',
      to: r.href,
      leading: <FileWarning size={16} strokeWidth={1.75} aria-hidden />,
    });
  }

  for (const r of data.queues.leaveHotspots.items.slice(0, 2)) {
    items.push({
      id: `lh-${r.personId}`,
      title: `Leave hotspot · ${r.personName}`,
      description: `${Math.round(r.annualUsedPct * 100)}% of annual used · ${r.remainingDays} d left · ${r.departmentName}`,
      meta: <StatusPill tone="warning">{`${Math.round(r.annualUsedPct * 100)}%`}</StatusPill>,
      tone: 'warning',
      to: r.href,
      leading: <Briefcase size={16} strokeWidth={1.75} aria-hidden />,
    });
  }

  return items;
}

function pickDeptTone(index: number, value: number, max: number): ChartTone {
  // First couple of departments visualised as warning (hotspots), then
  // primary, then neutral as values trail off. Keeps the chart from
  // looking like every department has the same severity.
  const ratio = max > 0 ? value / max : 0;
  if (ratio >= 0.7) return 'warning';
  if (ratio >= 0.4) return 'primary';
  if (index < 4) return 'info';
  return 'neutral';
}

function formatYM(ym: string): string {
  const m = /^(\d{4})-(\d{2})$/.exec(ym);
  if (!m) return ym;
  const date = new Date(Number(m[1]), Number(m[2]) - 1, 1, 12, 0, 0);
  return date.toLocaleDateString('en-PK', { month: 'short' });
}

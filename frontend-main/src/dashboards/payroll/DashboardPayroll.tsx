import { Card, Space, Typography, theme as antdTheme } from 'antd';
import { Link } from '@tanstack/react-router';
import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  PauseCircle,
  Receipt,
  TrendingUp,
  Users,
  Wallet,
} from 'lucide-react';

import {
  AnomalyScatter,
  ChartCard,
  HorizontalBars,
  KpiGrid,
  LineSpark,
  MoneyCell,
  NeedsYouList,
  PageHeader,
  RequestState,
  StackedBars,
  StatCard,
  StatusPill,
} from '@/components';
import type { ChartTone, NeedsYouItem } from '@/components';
import { forbiddenOr } from '@/lib/api-error';
import { formatDateMonthDay, formatDateShort } from '@/lib/dates';
import { PAYROLL_RUN_STATUS_LABEL, PAYROLL_RUN_STATUS_TONE } from '@/payroll';

import { useAuth } from '@/auth';

import { usePayrollDashboard } from './api';
import { welcomeTitle } from '../greeting';
import { RoleSwitcher } from '../RoleSwitcher';
import type { PayrollDashboardDto, PayrollRunStatus } from '../types';

const { Text } = Typography;
const { useToken } = antdTheme;

export function DashboardPayroll() {
  const query = usePayrollDashboard();
  const { user } = useAuth();

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow={`Money · ${formatDateMonthDay(new Date().toISOString())}`}
        title={welcomeTitle(user?.fullName)}
        description="Active run state, what needs your sign-off, and where this month is drifting from prior."
        actions={<RoleSwitcher />}
      />

      <RequestState
        loading={query.isLoading}
        error={query.error}
        empty={!query.data}
        errorTitle={forbiddenOr(
          query.error,
          "You don't have payroll access",
          "Couldn't load the payroll dashboard",
        )}
        emptyTitle="No payroll data yet"
      >
        {query.data ? <PayrollBody data={query.data} /> : null}
      </RequestState>
    </Space>
  );
}

function PayrollBody({ data }: { data: PayrollDashboardDto }) {
  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      {/* Hero band — current run */}
      <KpiGrid minColumnWidth={260}>
        <StatCard
          variant="hero"
          tone="primary"
          icon={Wallet}
          label={`Current run · ${formatPeriod(data.currentRun.period)}`}
          value={<MoneyCell amount={data.currentRun.rollup.net.amount} compact />}
          delta={
            <Space size={6}>
              <RunStatusPill status={data.currentRun.status} />
              <span>·</span>
              <span style={{ opacity: 0.85 }}>
                {data.currentRun.daysToCutoff} d to cutoff
              </span>
            </Space>
          }
        />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone="info"
          icon={Users}
          label="Employees this run"
          value={data.currentRun.rollup.employeeCount}
          delta="active payslips"
        />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone="warning"
          icon={AlertTriangle}
          label="Anomalies this run"
          value={data.needsYou.anomaliesOnRun.count}
          delta="employees outside ±15%"
          trend={data.needsYou.anomaliesOnRun.count > 0 ? 'down' : 'neutral'}
        />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone="error"
          icon={PauseCircle}
          label="Held payslips"
          value={data.needsYou.payslipsHeld.count}
          delta="awaiting release"
        />
      </KpiGrid>

      {/* Headline: run-over-run + anomaly scatter */}
      <KpiGrid minColumnWidth={420} gap={16}>
        <ChartCard
          title="Run-over-run · last 6 months"
          subtitle="Stacked: net (success) over deductions (warning). Gross is the implicit total."
          actions={
            <Link to="/payroll/runs" style={{ fontSize: 13 }}>
              All runs
            </Link>
          }
          height={320}
        >
          <StackedBars
            data={data.runs6m.runs.map((r) => ({
              period: r.period,
              net: r.net.amount,
              deductions: r.deductions.amount,
            })) as unknown as Array<Record<string, unknown>>}
            xKey="period"
            xTickFormatter={(v) => formatPeriod(String(v))}
            yTickFormatter={(v) => `${(v / 1_000_000).toFixed(0)}M`}
            valueFormatter={(v) => `Rs ${(v / 1_000_000).toFixed(2)}M`}
            series={[
              { key: 'deductions', label: 'Deductions', tone: 'warning' },
              { key: 'net', label: 'Net', tone: 'success' },
            ]}
            emptyTitle="No completed runs yet"
            emptyDescription="The 6-month trail populates once at least one run posts."
          />
        </ChartCard>

        <ChartCard
          title="Variance scatter · this run vs prior"
          subtitle={
            data.anomalyScatter.points.length > 0
              ? 'Each dot is one employee. ±15% threshold band; outliers in red are the anomaly queue below.'
              : 'Not yet computed for this run — check back once payroll closes the period.'
          }
          actions={
            <Link to="/payroll/runs" style={{ fontSize: 13 }}>
              Open run
            </Link>
          }
          height={320}
        >
          <AnomalyScatter
            data={data.anomalyScatter.points.map((p) => ({
              id: p.employeeId,
              label: p.name,
              caption: `${p.departmentName} · prior Rs ${formatCompact(p.priorNet)} → current Rs ${formatCompact(p.currentNet)}`,
              x: p.priorNet,
              y: p.variancePct,
            }))}
            threshold={0.15}
            xLabel="Prior net pay (Rs)"
            yLabel="Variance vs prior"
            xTickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
            yTickFormatter={(v) => `${Math.round(v * 100)}%`}
            formatX={(v) => `Rs ${formatCompact(v)}`}
            formatY={(v) =>
              `${v > 0 ? '+' : ''}${(v * 100).toFixed(1)}%`
            }
            emptyTitle="Variance scatter not ready"
            emptyDescription="The backend hasn't computed this run's variance against the prior period yet. The anomalies queue below still flags individual rows you can act on."
          />
        </ChartCard>
      </KpiGrid>

      {/* Needs you queue */}
      <Card
        title="Needs payroll attention"
        extra={
          <Text type="secondary" style={{ fontSize: 12 }}>
            {totalQueueCount(data.needsYou)} open
          </Text>
        }
        styles={{ body: { padding: '8px 8px 14px' } }}
      >
        <NeedsYouList
          items={payrollQueueItems(data)}
          emptyTitle="Nothing pending"
          emptyDescription="The current run has no outstanding items."
          emptyIcon={CheckCircle2}
        />
      </Card>

      {/* Bonuses + YoY trend */}
      <KpiGrid minColumnWidth={420} gap={16}>
        <ChartCard
          title="Bonus payouts · last 90 days"
          subtitle="Total awarded by department."
          height={Math.max(320, data.bonusByDepartment90d.length * 56 + 60)}
        >
          <HorizontalBars
            rows={data.bonusByDepartment90d.map((d, i) => ({
              id: d.departmentId,
              label: d.name,
              value: d.total.amount,
              tone: i === 0 ? 'primary' : i < 3 ? 'info' : 'neutral',
            }))}
            valueFormatter={(v) => `Rs ${formatCompact(v)}`}
            topN={6}
            emptyTitle="No bonuses paid yet"
            emptyDescription="Bonus event payouts in the last 90 days will appear here, ranked by department."
          />
        </ChartCard>

        <YoyCard data={data.netYoYTrend12m} />
      </KpiGrid>
    </Space>
  );
}

/* —————— sub-components —————— */

function YoyCard({ data }: { data: PayrollDashboardDto['netYoYTrend12m'] }) {
  const { token } = useToken();
  const last = data[data.length - 1]?.amount ?? 0;
  const yoy = data[0]?.amount ?? 0;
  const delta = yoy > 0 ? ((last - yoy) / yoy) * 100 : 0;

  return (
    <Card
      title="Total net pay · 12 months"
      styles={{ body: { padding: 22 } }}
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Text
            type="secondary"
            style={{
              fontSize: 11,
              letterSpacing: '0.06em',
              textTransform: 'uppercase',
            }}
          >
            Latest month
          </Text>
          <div
            style={{
              fontSize: 28,
              fontWeight: 700,
              letterSpacing: '-0.01em',
              color: token.colorTextHeading,
              fontVariantNumeric: 'tabular-nums',
              lineHeight: 1.1,
            }}
          >
            <MoneyCell amount={last} compact />
          </div>
          <Space size={4} style={{ marginTop: 4 }}>
            <TrendingUp size={14} color={token.colorSuccess} aria-hidden />
            <Text style={{ fontSize: 13, color: token.colorSuccess, fontWeight: 500 }}>
              {delta >= 0 ? '+' : ''}
              {delta.toFixed(1)}% YoY
            </Text>
          </Space>
        </div>
        <LineSpark data={data.map((p) => p.amount)} tone="success" area height={64} width="100%" />
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            fontSize: 11,
            color: token.colorTextTertiary,
          }}
        >
          <span>{formatPeriod(data[0]?.month ?? '')}</span>
          <span>{formatPeriod(data[data.length - 1]?.month ?? '')}</span>
        </div>
      </Space>
    </Card>
  );
}

function RunStatusPill({ status }: { status: PayrollRunStatus }) {
  return (
    <StatusPill tone={PAYROLL_RUN_STATUS_TONE[status]}>
      {PAYROLL_RUN_STATUS_LABEL[status]}
    </StatusPill>
  );
}

/* —————— helpers —————— */

function totalQueueCount(needs: PayrollDashboardDto['needsYou']): number {
  return (
    needs.anomaliesOnRun.count +
    needs.offCyclePending.count +
    needs.salaryChangesPending.count +
    needs.payslipsHeld.count
  );
}

function payrollQueueItems(data: PayrollDashboardDto): NeedsYouItem[] {
  const items: NeedsYouItem[] = [];
  const toneFor = (variance: number | null): ChartTone =>
    variance === null
      ? 'warning'
      : Math.abs(variance) >= 0.3
        ? 'error'
        : 'warning';

  for (const a of data.needsYou.anomaliesOnRun.items.slice(0, 3)) {
    const tone = toneFor(a.variancePct);
    items.push({
      id: `an-${a.id}`,
      title: `Anomaly · ${a.personName}`,
      description: a.departmentName
        ? `${a.departmentName} · ${a.summary}`
        : a.summary,
      meta:
        a.variancePct !== null ? (
          <StatusPill tone={tone}>{`${a.variancePct >= 0 ? '+' : ''}${(a.variancePct * 100).toFixed(0)}%`}</StatusPill>
        ) : (
          <StatusPill tone="warning">Anomaly</StatusPill>
        ),
      tone,
      to: a.href,
      leading: <AlertTriangle size={16} strokeWidth={1.75} aria-hidden />,
    });
  }
  for (const o of data.needsYou.offCyclePending.items.slice(0, 2)) {
    items.push({
      id: `oc-${o.id}`,
      title: `Off-cycle · ${o.personName}`,
      description: `${o.category} · submitted ${formatDateShort(o.submittedAt)}`,
      meta: o.amount ? (
        <Text style={{ fontSize: 13, fontWeight: 600 }}>
          <MoneyCell amount={o.amount.amount} />
        </Text>
      ) : (
        <StatusPill tone="info">Off-cycle</StatusPill>
      ),
      tone: 'info',
      to: o.href,
      leading: <Receipt size={16} strokeWidth={1.75} aria-hidden />,
    });
  }
  for (const s of data.needsYou.salaryChangesPending.items) {
    items.push({
      id: `sc-${s.id}`,
      title: `Salary change · ${s.personName}`,
      description: `${s.summary} · effective ${formatDateShort(s.effectiveOn)}`,
      meta: <StatusPill tone="info">Pending</StatusPill>,
      tone: 'primary',
      to: s.href,
      leading: <CalendarClock size={16} strokeWidth={1.75} aria-hidden />,
    });
  }
  for (const h of data.needsYou.payslipsHeld.items.slice(0, 2)) {
    items.push({
      id: `pl-${h.id}`,
      title: `Held payslip · ${h.personName}`,
      description: h.runPeriod
        ? `${formatPeriod(h.runPeriod)} · ${h.reason}`
        : h.reason,
      meta: h.amount ? (
        <Text style={{ fontSize: 13, fontWeight: 600 }}>
          <MoneyCell amount={h.amount.amount} />
        </Text>
      ) : (
        <StatusPill tone="error">Held</StatusPill>
      ),
      tone: 'error',
      to: h.href,
      leading: <PauseCircle size={16} strokeWidth={1.75} aria-hidden />,
    });
  }
  return items;
}

function formatPeriod(ym: string): string {
  const m = /^(\d{4})-(\d{2})$/.exec(ym);
  if (!m) return ym;
  const date = new Date(Number(m[1]), Number(m[2]) - 1, 1, 12, 0, 0);
  return date.toLocaleDateString('en-PK', { month: 'short', year: 'numeric' });
}

function formatCompact(amount: number): string {
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(1)}M`;
  if (amount >= 1_000) return `${(amount / 1_000).toFixed(0)}k`;
  return new Intl.NumberFormat('en-PK').format(amount);
}

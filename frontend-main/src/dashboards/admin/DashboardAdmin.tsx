import { App as AntdApp, Button, Card, Space, Typography, theme as antdTheme } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import {
  Activity,
  AlertTriangle,
  ChevronRight,
  Clock,
  KeyRound,
  Lock,
  RotateCw,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
} from 'lucide-react';

import {
  ChartCard,
  KpiGrid,
  NeedsYouList,
  PageHeader,
  RequestState,
  SagaStatusGrid,
  StackedBars,
  StatCard,
  StatusPill,
  TrendLine,
} from '@/components';
import type { ChartTone, NeedsYouItem, SagaStatusRow } from '@/components';
import { forbiddenOr } from '@/lib/api-error';
import { getErrorMessage } from '@/lib/errors';
import { http } from '@/lib/http';
import { formatDateMonthDay, formatDateTime } from '@/lib/dates';

import { useAuth } from '@/auth';

import { useAdminDashboard } from './api';
import { welcomeTitle } from '../greeting';
import { RoleSwitcher } from '../RoleSwitcher';
import type {
  AdminDashboardDto,
  SystemHealthOverall,
} from '../types';

const { Text } = Typography;
const { useToken } = antdTheme;

export function DashboardAdmin() {
  const query = useAdminDashboard();
  const { user } = useAuth();

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow={`System · ${formatDateMonthDay(new Date().toISOString())}`}
        title={welcomeTitle(user?.fullName)}
        description="Today's system health, recent activity, and changes worth reviewing."
        actions={<RoleSwitcher />}
      />
      <RequestState
        loading={query.isLoading}
        error={query.error}
        empty={!query.data}
        errorTitle={forbiddenOr(
          query.error,
          "You don't have operator access",
          "Couldn't load the system dashboard",
        )}
        emptyTitle="No operator data yet"
      >
        {query.data ? <AdminBody data={query.data} /> : null}
      </RequestState>
    </Space>
  );
}

function AdminBody({ data }: { data: AdminDashboardDto }) {
  const qc = useQueryClient();
  const { message } = AntdApp.useApp();

  const sagaRows: SagaStatusRow[] = data.systemHealth.sagas.list.map((s) => ({
    id: s.id,
    name: s.name,
    status: s.status,
    failureRate24h: s.failureRate24h,
  }));

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      {/* Hero band — system health */}
      <KpiGrid minColumnWidth={260}>
        <SystemHealthHero overall={data.systemHealth.overall} />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone={data.systemHealth.sagas.failed24h > 0 ? 'warning' : 'success'}
          icon={Activity}
          label="Background jobs · last 24h"
          value={data.systemHealth.sagas.failed24h}
          delta={`${data.systemHealth.sagas.running} running now`}
          trend={data.systemHealth.sagas.failed24h > 0 ? 'down' : 'neutral'}
        />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone={data.systemHealth.auditReplayQueue.depth > 0 ? 'warning' : 'success'}
          icon={Clock}
          label="Audit replay queue"
          value={data.systemHealth.auditReplayQueue.depth}
          delta={
            data.systemHealth.auditReplayQueue.oldestSeconds > 0
              ? `oldest ${formatDuration(data.systemHealth.auditReplayQueue.oldestSeconds)}`
              : 'queue empty'
          }
        />
        <StatCard
          variant="accent-radial"
          accentCorner="tr"
          tone={data.needsYou.lockedAccounts.count > 0 ? 'error' : 'success'}
          icon={Lock}
          label="Locked accounts"
          value={data.needsYou.lockedAccounts.count}
          delta="awaiting unlock"
        />
      </KpiGrid>

      {/* Background-job health grid */}
      <Card
        title="Background-job health"
        extra={
          <Text type="secondary" style={{ fontSize: 12 }}>
            24h hourly failure buckets
          </Text>
        }
      >
        <SagaStatusGrid rows={sagaRows} />
      </Card>

      {/* Audit volume + auth-events-by-hour */}
      <KpiGrid minColumnWidth={420} gap={16}>
        <ChartCard
          title="Audit event volume · last 7 days"
          subtitle="Server-side audit events. Spikes worth watching: bulk imports, mass permission changes, sustained failure storms."
          actions={
            <Link to="/admin/audit" style={{ fontSize: 13 }}>
              Audit explorer
            </Link>
          }
          height={300}
        >
          <TrendLine
            data={data.auditVolume7d as unknown as Array<Record<string, unknown>>}
            xKey="date"
            xTickFormatter={(v) => formatDateMonthDay(String(v))}
            yTickFormatter={(v) => `${(v / 1000).toFixed(1)}k`}
            valueFormatter={(v) => `${v.toLocaleString('en-PK')} events`}
            series={[{ key: 'count', label: 'Events', tone: 'primary' }]}
            emptyTitle="Audit stream is quiet"
            emptyDescription="No audit events ingested in the last 7 days. The stream resumes once a service publishes."
          />
        </ChartCard>

        <ChartCard
          title="Auth events by hour-of-day"
          subtitle="Logins (success) over failures (error). Sustained failure outside working hours is a credential-stuffing signal."
          height={300}
        >
          <StackedBars
            data={data.authEventsByHour as unknown as Array<Record<string, unknown>>}
            xKey="hour"
            xTickFormatter={(v) => `${String(v).padStart(2, '0')}h`}
            stacked={false}
            barGap="10%"
            series={[
              { key: 'logins', label: 'Logins', tone: 'success' },
              { key: 'failures', label: 'Failures', tone: 'error' },
            ]}
            emptyTitle="No auth activity yet"
            emptyDescription="No logins or failed attempts in the last 24 hours."
          />
        </ChartCard>
      </KpiGrid>

      {/* Needs you queue + role changes */}
      <KpiGrid minColumnWidth={420} gap={16}>
        <Card
          title="Needs operator review"
          extra={
            <Text type="secondary" style={{ fontSize: 12 }}>
              {totalQueueCount(data.needsYou)} open
            </Text>
          }
          styles={{ body: { padding: '8px 8px 14px' } }}
        >
          <NeedsYouList
            items={adminQueueItems(data, async (label, retryHref) => {
              try {
                await http.post(retryHref, undefined);
                message.success(`Retry started · ${label}`);
                qc.invalidateQueries({ queryKey: ['dashboard', 'admin'] });
              } catch (err) {
                message.error(`Couldn't retry · ${getErrorMessage(err)}`);
              }
            })}
            emptyTitle="System quiet"
            emptyDescription="No operator-level items waiting in the last 24 hours."
            emptyIcon={ShieldCheck}
          />
        </Card>

        <Card
          title="Recent role changes"
          extra={
            <Link to="/admin/audit" style={{ fontSize: 13 }}>
              Full audit
            </Link>
          }
          styles={{ body: { padding: 0 } }}
        >
          <RoleChangesList rows={data.recentRoleChanges} />
        </Card>
      </KpiGrid>
    </Space>
  );
}

/* —————— sub-components —————— */

function SystemHealthHero({ overall }: { overall: SystemHealthOverall }) {
  const tone: ChartTone =
    overall === 'OK' ? 'success' : overall === 'DEGRADED' ? 'warning' : 'error';
  const Icon =
    overall === 'OK' ? ShieldCheck : overall === 'DEGRADED' ? ShieldAlert : ShieldX;
  const label =
    overall === 'OK'
      ? 'All systems healthy'
      : overall === 'DEGRADED'
        ? 'Partially degraded'
        : 'Active incident';
  return (
    <StatCard
      variant="hero"
      tone={tone}
      icon={Icon}
      label="System health"
      value={label}
      delta={
        overall === 'OK'
          ? 'No active incidents'
          : overall === 'DEGRADED'
            ? 'One or more background jobs need attention'
            : 'Investigate immediately'
      }
    />
  );
}

function RoleChangesList({ rows }: { rows: AdminDashboardDto['recentRoleChanges'] }) {
  const { token } = useToken();
  if (rows.length === 0) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Text type="secondary" style={{ fontSize: 13 }}>
          No role changes in the last 24 hours.
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
            padding: '12px 18px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            borderTop: i === 0 ? 'none' : `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <KeyRound
            size={16}
            strokeWidth={1.75}
            color={token.colorTextTertiary}
            aria-hidden
            style={{ flexShrink: 0, marginTop: 3 }}
          />
          <div style={{ minWidth: 0, flex: 1 }}>
            <Text style={{ fontSize: 14, fontWeight: 500, color: token.colorTextHeading }}>
              {row.actorName} · {row.summary}
            </Text>
            <Text
              type="secondary"
              style={{ display: 'block', fontSize: 12, marginTop: 2 }}
            >
              {row.subjectName ? `affecting ${row.subjectName} · ` : ''}
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

function totalQueueCount(needs: AdminDashboardDto['needsYou']): number {
  return (
    needs.failedSagas.count +
    needs.permissionChanges24h.count +
    needs.lockedAccounts.count
  );
}

function adminQueueItems(
  data: AdminDashboardDto,
  onRetry: (label: string, retryHref: string) => Promise<void>,
): NeedsYouItem[] {
  const items: NeedsYouItem[] = [];

  for (const f of data.needsYou.failedSagas.items) {
    items.push({
      id: `fs-${f.id}`,
      title: `Failed background job · ${f.label}`,
      description: f.summary,
      meta: <StatusPill tone="error">Failed</StatusPill>,
      tone: 'error',
      leading: <AlertTriangle size={16} strokeWidth={1.75} aria-hidden />,
      actions: f.retryHref ? (
        <Button
          size="small"
          icon={<RotateCw size={12} />}
          onClick={() => {
            void onRetry(f.label, f.retryHref!);
          }}
        >
          Retry
        </Button>
      ) : undefined,
    });
  }

  for (const p of data.needsYou.permissionChanges24h.items.slice(0, 3)) {
    items.push({
      id: `pc-${p.id}`,
      title: `Permission change · ${p.label}`,
      description: p.summary,
      meta: <StatusPill tone="warning">Review</StatusPill>,
      tone: 'warning',
      to: '/admin/audit',
      leading: <KeyRound size={16} strokeWidth={1.75} aria-hidden />,
    });
  }

  for (const a of data.needsYou.lockedAccounts.items) {
    items.push({
      id: `la-${a.id}`,
      title: `Locked · ${a.label}`,
      description: a.summary,
      meta: <StatusPill tone="error">Locked</StatusPill>,
      tone: 'error',
      to: '/admin/users',
      leading: <Lock size={16} strokeWidth={1.75} aria-hidden />,
    });
  }

  return items;
}

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
  if (seconds < 86_400) return `${Math.round(seconds / 3600)}h`;
  return `${Math.round(seconds / 86_400)}d`;
}

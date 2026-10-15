import { App as AntdApp, Card, Space, theme as antdTheme, Typography } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import {
  AlertTriangle,
  CalendarClock,
  Clock,
  Inbox,
  Plane,
  Users,
} from 'lucide-react';

import {
  ApproveRejectActions,
  AvatarStack,
  ChartCard,
  ChartTone,
  KpiGrid,
  LeaveCalendarStrip,
  LineSpark,
  NeedsYouList,
  PageHeader,
  PersonCell,
  RequestState,
  StackedBars,
  StatCard,
  StatusPill,
} from '@/components';
import { leavesApi, overtimeApi, regularizationsApi, shortLeavesApi } from '@/attendance';
import { forbiddenOr, getErrorMessage } from '@/lib';
import { formatDateMonthDay } from '@/lib/dates';

import { useAuth } from '@/auth';

import { useManagerDashboard } from './api';
import { welcomeTitle } from '../greeting';
import { RoleSwitcher } from '../RoleSwitcher';
import type { ApprovalKind, ManagerDashboardDto } from '../types';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

export function DashboardManager() {
  const { token } = useToken();
  const query = useManagerDashboard();
  const { message } = AntdApp.useApp();
  const { user } = useAuth();
  const decide = useApprovalDecide();

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      <PageHeader
        eyebrow={`Manager · ${formatDateMonthDay(new Date().toISOString())}`}
        title={welcomeTitle(user?.fullName)}
        description="A glance at today's pulse, what needs your decisions, and who's away in the next 30 days."
        actions={<RoleSwitcher />}
      />

      <RequestState
        loading={query.isLoading}
        error={query.error}
        empty={!query.data}
        errorTitle={forbiddenOr(
          query.error,
          'No team to summarise yet',
          "Couldn't load your dashboard",
        )}
        emptyTitle="No team to summarise"
        emptyFallback={
          <Card>
            <Text type="secondary">
              You don't have any direct reports yet. The team dashboard
              will populate once your team is set up.
            </Text>
          </Card>
        }
      >
        {query.data ? (
          <Space orientation="vertical" size={24} style={{ width: '100%' }}>
            {/* Hero band — pulse */}
            <KpiGrid minColumnWidth={260}>
              <StatCard
                variant="hero"
                tone="primary"
                icon={Users}
                label="Team present today"
                value={`${query.data.team.today.present} / ${query.data.team.size}`}
                delta={
                  <Space size={6} align="center">
                    <AvatarStack
                      people={query.data.team.today.people
                        .filter((p) => p.status === 'PRESENT' || p.status === 'LATE')
                        .slice(0, 6)
                        .map((p) => ({
                          name: p.name,
                          src: p.profilePictureUrl ?? undefined,
                        }))}
                      max={5}
                      // Explicit size overrides AvatarStack's default, so this
                      // call site needs its own bump — these were the smallest
                      // photos in the app, on the first screen a manager sees.
                      size={32}
                    />
                    <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 13 }}>
                      checked in
                    </Text>
                  </Space>
                }
              />
              <StatCard
                variant="accent-radial"
                accentCorner="tr"
                tone="warning"
                icon={Clock}
                label="Late"
                value={query.data.team.today.late}
                delta={query.data.team.today.late === 0 ? 'Everyone on time' : 'Past shift start'}
                trend={query.data.team.today.late === 0 ? 'neutral' : 'down'}
              />
              <StatCard
                variant="accent-radial"
                accentCorner="tr"
                tone="error"
                icon={AlertTriangle}
                label="Absent"
                value={query.data.team.today.absent}
                delta={
                  query.data.team.today.absent === 0
                    ? 'Full attendance'
                    : 'No punch + no leave'
                }
                trend={query.data.team.today.absent === 0 ? 'neutral' : 'down'}
              />
              <StatCard
                variant="accent-radial"
                accentCorner="tr"
                tone="info"
                icon={Plane}
                label="On leave"
                value={query.data.team.today.onLeave}
                delta="Approved leaves today"
                trend="neutral"
              />
            </KpiGrid>

            {/* Headline chart */}
            <ChartCard
              title="Team attendance · last 14 days"
              subtitle="Stacked daily counts. Weekends collapse to zero by design — surface only working-day signal."
              actions={
                <Link
                  to="/time/team"
                  style={{
                    fontSize: 13,
                    fontWeight: 500,
                    color: token.colorPrimary,
                  }}
                >
                  Open team attendance
                </Link>
              }
            >
              <StackedBars
                data={query.data.attendance14d.days as unknown as Array<Record<string, unknown>>}
                xKey="date"
                xTickFormatter={(v) => formatDateMonthDay(String(v))}
                series={[
                  { key: 'present', label: 'Present', tone: 'success' },
                  { key: 'late', label: 'Late', tone: 'warning' },
                  { key: 'absent', label: 'Absent', tone: 'error' },
                  { key: 'leave', label: 'On leave', tone: 'info' },
                ]}
                emptyTitle="No team attendance yet"
                emptyDescription="Attendance bars appear once your team starts logging punches."
              />
            </ChartCard>

            {/* Two-column: Needs you + Leave calendar */}
            <KpiGrid minColumnWidth={420} gap={16}>
              <Card
                title="Needs your decision"
                extra={
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {query.data.approvalQueue.totalPending} pending
                  </Text>
                }
                styles={{ body: { padding: '8px 8px 14px' } }}
              >
                <NeedsYouList
                  items={query.data.approvalQueue.items.map((row) => ({
                    id: row.id,
                    title: (
                      <Space size={8} align="center">
                        <span>{row.kindLabel}</span>
                        <Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>
                          ·
                        </Text>
                        <Text style={{ fontSize: 13, fontWeight: 500 }}>
                          {row.person.name}
                        </Text>
                      </Space>
                    ),
                    description: (
                      <Space size={6}>
                        <span>{formatRange(row.start, row.end)}</span>
                        {row.reason ? (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            · {row.reason}
                          </Text>
                        ) : null}
                      </Space>
                    ),
                    leading: (
                      <PersonCell
                        person={{
                          fullName: row.person.name,
                          profilePictureUrl: row.person.profilePictureUrl ?? undefined,
                        }}
                        size="compact"
                        avatarOnly
                      />
                    ),
                    meta: <KindChip kind={row.kindLabel} tone={row.tone ?? 'primary'} />,
                    tone: row.tone ?? 'primary',
                    actions: (
                      <ApproveRejectActions
                        actions={['APPROVE', 'REJECT']}
                        size="small"
                        rejectTitle={`Reject ${row.kindLabel.toLowerCase()}?`}
                        rejectDescription={`The submitter (${row.person.name}) is notified.`}
                        onApprove={async () => {
                          try {
                            await decide({ rowId: row.id, kind: row.kind, action: 'APPROVE' });
                            message.success(`Approved · ${row.kindLabel} for ${row.person.name}`);
                          } catch (err) {
                            message.error(getErrorMessage(err));
                          }
                        }}
                        onReject={async (reason) => {
                          try {
                            await decide({ rowId: row.id, kind: row.kind, action: 'REJECT', reason });
                            message.success(`Rejected · "${truncate(reason, 40)}"`);
                          } catch (err) {
                            message.error(getErrorMessage(err));
                          }
                        }}
                      />
                    ),
                  }))}
                  emptyTitle="Inbox zero"
                  emptyDescription="No leaves, OT, or shift overrides waiting on you right now."
                  emptyIcon={Inbox}
                  viewAllTo="/inbox"
                  viewAllLabel="Open full inbox"
                />
              </Card>

              <ChartCard
                title="Who's out · next 30 days"
                subtitle="Approved leaves. Today's column is highlighted; weekends in light grey."
                height={Math.max(220, query.data.team.size * 26 + 60)}
              >
                <LeaveCalendarStrip
                  from={query.data.leaveCalendar30d.from}
                  to={query.data.leaveCalendar30d.to}
                  items={query.data.leaveCalendar30d.items.map((it) => ({
                    id: it.id,
                    personId: it.personId,
                    personName: it.personName,
                    start: it.start,
                    end: it.end,
                    label: it.leaveType,
                    tone: it.tone,
                  }))}
                  emptyTitle="Quiet 30 days"
                  emptyDescription="No approved leaves in the next month."
                />
              </ChartCard>
            </KpiGrid>

            {/* Context strip */}
            <Section
              title="This week"
              subtitle="Sparklines pull from the last 7 working days."
            >
              <KpiGrid minColumnWidth={220}>
                <StatCard
                  tone="primary"
                  variant="accent-border"
                  accentSide="left"
                  label="Punctuality"
                  value={`${query.data.context.punctualityWeek.avgVarianceMin} min`}
                  delta={
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <LineSpark
                        data={query.data.context.punctualityWeek.trend7d}
                        tone="primary"
                        height={20}
                        width={70}
                      />
                      avg variance vs shift start
                    </span>
                  }
                />
                <StatCard
                  tone="warning"
                  variant="accent-border"
                  accentSide="left"
                  label="Approval velocity"
                  value={formatMinutes(query.data.approvalQueue.velocity.avgDecisionMinutes7d)}
                  delta={
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <LineSpark
                        data={query.data.approvalQueue.velocity.trend7d}
                        tone="warning"
                        height={20}
                        width={70}
                      />
                      avg time to decide
                    </span>
                  }
                />
                <StatCard
                  tone="error"
                  variant="accent-border"
                  accentSide="left"
                  label="Exceptions"
                  value={query.data.context.exceptionsWeek.count}
                  delta="late + absent + missing punch"
                  icon={AlertTriangle}
                />
                <StatCard
                  tone="info"
                  variant="accent-border"
                  accentSide="left"
                  label="Direct reports"
                  value={query.data.team.directReports}
                  delta="Active head count"
                  icon={CalendarClock}
                />
              </KpiGrid>
            </Section>
          </Space>
        ) : null}
      </RequestState>
    </Space>
  );
}

/* ——— small helpers, kept local to the layout ——— */

function Section({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
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
      {children}
    </div>
  );
}

function KindChip({ kind, tone }: { kind: string; tone: ChartTone }) {
  const toneToPill: Record<ChartTone, React.ComponentProps<typeof StatusPill>['tone']> = {
    primary: 'info',
    success: 'success',
    warning: 'warning',
    error: 'error',
    info: 'info',
    neutral: 'neutral',
  };
  return <StatusPill tone={toneToPill[tone]}>{kind}</StatusPill>;
}

function formatRange(start: string, end: string): string {
  if (start === end) return formatDateMonthDay(start);
  return `${formatDateMonthDay(start)} → ${formatDateMonthDay(end)}`;
}

function formatMinutes(min: number): string {
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  if (m === 0) return `${h} h`;
  return `${h} h ${m} m`;
}

function truncate(s: string, max: number): string {
  if (s.length <= max) return s;
  return `${s.slice(0, max - 1)}…`;
}

/**
 * Optimistic approve/reject for the manager dashboard "Needs your decision"
 * card. Splices the row out of the cached dashboard payload and decrements
 * the pending count, then fires the actual approve/reject call. On error,
 * the previous snapshot is restored.
 *
 * Why optimistic-only (no refetch on settle): the dashboard composite fans
 * out across employee + attendance + audit. Refetching it for a single
 * decision wastes a multi-service call to confirm what the action response
 * already told us. The dashboard naturally reconciles on next legitimate
 * fetch (route change, window focus revalidation, staleTime expiry).
 */
type DecideArgs =
  | { rowId: string; kind: ApprovalKind; action: 'APPROVE' }
  | { rowId: string; kind: ApprovalKind; action: 'REJECT'; reason: string };

function useApprovalDecide() {
  const qc = useQueryClient();
  const key = ['dashboard', 'manager'] as const;

  return async (args: DecideArgs): Promise<void> => {
    const id = Number(args.rowId);
    if (!Number.isFinite(id)) {
      throw new Error(`Invalid approval row id: ${args.rowId}`);
    }

    await qc.cancelQueries({ queryKey: key });
    const snapshot = qc.getQueryData<ManagerDashboardDto>(key);
    if (snapshot) {
      qc.setQueryData<ManagerDashboardDto>(key, {
        ...snapshot,
        approvalQueue: {
          ...snapshot.approvalQueue,
          items: snapshot.approvalQueue.items.filter((r) => r.id !== args.rowId),
          totalPending: Math.max(0, snapshot.approvalQueue.totalPending - 1),
        },
      });
    }

    try {
      if (args.action === 'APPROVE') {
        await callApprove(args.kind, id);
      } else {
        await callReject(args.kind, id, args.reason);
      }
    } catch (err) {
      if (snapshot) qc.setQueryData(key, snapshot);
      throw err;
    }
  };
}

function callApprove(kind: ApprovalKind, id: number): Promise<unknown> {
  switch (kind) {
    case 'LEAVE':
      return leavesApi.approve(id);
    case 'SHORT_LEAVE':
      return shortLeavesApi.approve(id);
    case 'OVERTIME':
      return overtimeApi.approve(id);
    case 'REGULARIZATION':
    case 'SHIFT_OVERRIDE':
      return regularizationsApi.approve(id);
  }
}

function callReject(kind: ApprovalKind, id: number, reason: string): Promise<unknown> {
  switch (kind) {
    case 'LEAVE':
      return leavesApi.reject(id, { reason });
    case 'SHORT_LEAVE':
      return shortLeavesApi.reject(id, { reason });
    case 'OVERTIME':
      return overtimeApi.reject(id, { reason });
    case 'REGULARIZATION':
    case 'SHIFT_OVERRIDE':
      return regularizationsApi.reject(id, { reason });
  }
}

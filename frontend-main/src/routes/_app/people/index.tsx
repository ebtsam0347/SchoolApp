// /people — overview hub for the People module.
//
// Single composite read: GET /employees/stats (one round trip for 6 KPIs).
// Plus a 5-row probation preview from /employees/directory and the three
// other HR queues rendered as compact tiles linking into /people/queues/*.
//
// The page answers HR's first question: "what needs me?" (overdue probations,
// open warnings, expiring certs). Browsing the workforce is one click into
// the Directory.

import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Skeleton,
  Space,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  ArrowRight,
  CalendarClock,
  ClipboardList,
  ShieldAlert,
  ShieldX,
  Users as UsersIcon,
  UserPlus,
  type LucideIcon,
} from 'lucide-react';

import {
  EmptyState,
  ExpiryBadge,
  KpiGrid,
  PageHeader,
  PersonCell,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { Can } from '@/auth';
import { forbiddenOr, getErrorMessage, isForbidden } from '@/lib';
import {
  EMPLOYMENT_STATUS_LABEL,
  peopleBff,
  type EmployeeStats,
} from '@/people';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/people/')({
  component: PeopleOverview,
});

function PeopleOverview() {
  const { token } = useToken();
  const navigate = useNavigate();

  const statsQuery = useQuery({
    queryKey: ['people', 'stats'],
    queryFn: ({ signal }) => peopleBff.stats(signal),
  });

  const probationQuery = useQuery({
    queryKey: ['people', 'directory', 'probationDueWithin=14', 'size=5'],
    queryFn: ({ signal }) =>
      peopleBff.directory(
        { probationDueWithin: 14, size: 5, sort: 'joiningDate,asc' },
        signal,
      ),
  });

  const stats = statsQuery.data;

  const errorAlert = statsQuery.isError ? (
    <Alert
      type={isForbidden(statsQuery.error) ? 'info' : 'error'}
      showIcon
      title={forbiddenOr(
        statsQuery.error,
        "You don't have permission to view People stats.",
        "Couldn't load People stats",
      )}
      description={getErrorMessage(statsQuery.error)}
    />
  ) : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="Workforce"
        title="People"
        description="Directory, queues, and recent activity for the HR back-office."
        actions={
          <Space>
            <Can I="read" a="employee">
              <Button
                onClick={() => navigate({ to: '/people/directory' })}
                icon={<UsersIcon size={16} strokeWidth={1.75} />}
              >
                Directory
              </Button>
            </Can>
            <Can I="write" a="employee">
              <Button
                type="primary"
                onClick={() => navigate({ to: '/people/onboard' })}
                icon={<UserPlus size={16} strokeWidth={1.75} />}
              >
                Onboard employee
              </Button>
            </Can>
          </Space>
        }
      />

      {errorAlert}

      <StatRow stats={stats} loading={statsQuery.isLoading} />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: 16,
        }}
      >
        <QueueTile
          icon={CalendarClock}
          tone="warning"
          to="/people/queues/probation"
          title="Probation queue"
          metric={
            stats?.probation
              ? `${stats.probation.dueWithin14 ?? 0} due · ${stats.probation.overdue ?? 0} overdue`
              : '—'
          }
          subtitle={`${stats?.probation?.active ?? 0} active probations`}
        />
        <QueueTile
          icon={ShieldAlert}
          tone="error"
          to="/people/queues/disciplinary"
          title="Disciplinary"
          metric={`${stats?.byStatus?.SUSPENDED ?? 0} suspended`}
          subtitle="Open warnings + active suspensions"
        />
        <QueueTile
          icon={ClipboardList}
          tone="info"
          to="/people/queues/certifications"
          title="Certifications expiring"
          metric={`${stats?.certifications?.expiringWithin30 ?? 0} in 30d`}
          subtitle={`${stats?.certifications?.expired ?? 0} already expired`}
        />
      </div>

      <Section
        title="Probation queue"
        subtitle="Next 14 days — confirm, extend, or escalate."
        actions={
          <Link to="/people/queues/probation">
            <Button type="link" icon={<ArrowRight size={14} />}>
              View all
            </Button>
          </Link>
        }
      >
        {probationQuery.isLoading ? (
          <Skeleton paragraph={{ rows: 3 }} />
        ) : probationQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load probation queue"
            description={getErrorMessage(probationQuery.error)}
          />
        ) : (probationQuery.data?.content?.length ?? 0) === 0 ? (
          <EmptyState
            icon={CalendarClock}
            title="No probations due in the next 14 days"
            description="Confirmations and extensions will land here as the dates approach."
            size="sm"
          />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {probationQuery.data?.content?.map((row) => {
              const e = row.employee;
              const probation = e?.activeProbation;
              if (!e?.id) return null;
              return (
                <div
                  key={e.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 16,
                    padding: '12px 4px',
                    borderBottom: `1px solid ${token.colorBorderSecondary}`,
                  }}
                >
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <PersonCell
                      person={{
                        fullName: e.fullName ?? '—',
                        employeeCode: e.employeeCode,
                        designation: row.resolved?.designation?.title ?? null,
                        profilePictureUrl: e.profilePictureUrl,
                      }}
                      to={`/people/${e.id}`}
                    />
                  </div>
                  {probation?.endDate ? (
                    <ExpiryBadge date={probation.endDate} mode="deadline" />
                  ) : null}
                  <Space>
                    <Can I="write" a="employee">
                      <Button size="small" onClick={() => navigate({ to: `/people/${e.id}/employment` })}>
                        Confirm
                      </Button>
                      <Button size="small" type="link" onClick={() => navigate({ to: `/people/${e.id}/employment` })}>
                        Extend
                      </Button>
                    </Can>
                  </Space>
                </div>
              );
            })}
          </div>
        )}
      </Section>
    </div>
  );
}

function StatRow({
  stats,
  loading,
}: {
  stats: EmployeeStats | undefined;
  loading: boolean;
}) {
  if (loading) {
    return (
      <KpiGrid>
        {Array.from({ length: 6 }).map((_, i) => (
          <Card key={i}>
            <Skeleton active paragraph={{ rows: 2 }} title={false} />
          </Card>
        ))}
      </KpiGrid>
    );
  }

  const totalActive = stats?.byStatus?.ACTIVE ?? 0;
  const onNotice = stats?.byStatus?.ON_NOTICE ?? 0;
  const onLeave = stats?.byStatus?.ON_LEAVE ?? 0;
  const suspended = stats?.byStatus?.SUSPENDED ?? 0;
  const probationOverdue = stats?.probation?.overdue ?? 0;
  const probationDue = stats?.probation?.dueWithin14 ?? 0;
  const certsExpiring = stats?.certifications?.expiringWithin30 ?? 0;
  const certsExpired = stats?.certifications?.expired ?? 0;
  const essPct = stats?.ess?.coveragePct ?? 0;
  const essGranted = stats?.ess?.granted ?? 0;
  const essTotal = (stats?.ess?.granted ?? 0) + (stats?.ess?.ungranted ?? 0);

  return (
    <KpiGrid>
      <StatCard
        variant="hero"
        label="Active workforce"
        value={totalActive.toLocaleString('en-PK')}
        delta={`${stats?.total ?? 0} total · ${onLeave} on leave · ${onNotice} on notice`}
        icon={UsersIcon}
      />
      <StatCard
        variant="accent-radial"
        tone="warning"
        accentCorner="tr"
        label="Probation overdue"
        value={probationOverdue}
        delta={`${probationDue} due within 14d`}
        trend={probationOverdue > 0 ? 'down' : 'neutral'}
        icon={CalendarClock}
      />
      <StatCard
        variant="accent-corner"
        tone="error"
        accentCorner="tr"
        label="Suspended"
        value={suspended}
        delta={EMPLOYMENT_STATUS_LABEL.SUSPENDED}
        icon={ShieldX}
      />
      <StatCard
        variant="accent-corner"
        tone="info"
        accentCorner="tr"
        label="On notice"
        value={onNotice}
        delta="Resignation in progress"
        icon={ShieldAlert}
      />
      <StatCard
        variant="accent-corner"
        tone="warning"
        accentCorner="tr"
        label="Certifications expiring"
        value={certsExpiring}
        delta={`${certsExpired} already expired`}
        trend={certsExpiring > 0 ? 'down' : 'neutral'}
        icon={ClipboardList}
      />
      <StatCard
        label="ESS coverage"
        value={`${essPct.toFixed(0)}%`}
        delta={`${essGranted} of ${essTotal} granted`}
        icon={UsersIcon}
      />
    </KpiGrid>
  );
}

function QueueTile({
  icon: Icon,
  tone,
  to,
  title,
  metric,
  subtitle,
}: {
  icon: LucideIcon;
  tone: 'warning' | 'error' | 'info' | 'success';
  to: '/people/queues/probation' | '/people/queues/disciplinary' | '/people/queues/certifications' | '/people/queues/suspensions';
  title: string;
  metric: string;
  subtitle: string;
}) {
  const { token } = useToken();
  const toneColor =
    tone === 'warning'
      ? token.colorWarning
      : tone === 'error'
        ? token.colorError
        : tone === 'info'
          ? token.colorInfo
          : token.colorSuccess;

  return (
    <Link to={to} style={{ display: 'block', textDecoration: 'none' }}>
      <Card
        hoverable
        styles={{ body: { padding: 16, display: 'flex', alignItems: 'center', gap: 14 } }}
      >
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 10,
            background: `color-mix(in srgb, ${toneColor} 14%, transparent)`,
            color: toneColor,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Icon size={20} strokeWidth={1.75} aria-hidden />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: token.colorTextHeading,
            }}
          >
            {title}
          </div>
          <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
            {subtitle}
          </Text>
        </div>
        <StatusPill tone={tone} size="sm">
          {metric}
        </StatusPill>
      </Card>
    </Link>
  );
}

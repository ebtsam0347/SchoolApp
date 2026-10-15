// /admin — overview hub.
//
// At-a-glance KPIs (total users, active users, locked accounts, role count)
// plus quick-jump cards into the deeper admin sections. User counts come
// from `/admin/users/stats` in one round trip; roles and permissions are
// catalog reads; consumer lag is the audit-service health probe.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Alert, Skeleton, Typography, theme as antdTheme } from 'antd';
import {
  Activity,
  ArrowRight,
  KeyRound,
  Lock,
  ShieldCheck,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { adminUsersApi, rolesApi, permissionsApi } from '@/auth';
import { auditApi } from '@/audit';
import { KpiGrid, Section, StatCard } from '@/components';
import { forbiddenOr, getErrorMessage, isForbidden } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/admin/')({
  component: AdminOverview,
});

function AdminOverview() {
  const { token } = useToken();
  const statsQuery = useQuery({
    queryKey: ['admin', 'users', 'stats'],
    queryFn: ({ signal }) => adminUsersApi.stats(signal),
  });
  const rolesQuery = useQuery({
    queryKey: ['admin', 'roles'],
    queryFn: ({ signal }) => rolesApi.list(signal),
    staleTime: 24 * 60 * 60 * 1000,
  });
  const permsQuery = useQuery({
    queryKey: ['admin', 'permissions'],
    queryFn: ({ signal }) => permissionsApi.list(signal),
    staleTime: 24 * 60 * 60 * 1000,
  });
  const healthQuery = useQuery({
    queryKey: ['admin', 'audit', 'health'],
    queryFn: ({ signal }) => auditApi.health(signal),
  });

  const stats = statsQuery.data;
  const totalUsers = stats?.total;
  const active = stats?.active;
  const locked = stats?.locked;
  const disabled = stats?.disabled;
  const mustChangePw = stats?.withMustChangePw;
  const linked = stats?.employeeLinked;
  const standalone = stats?.standalone;
  const roles = rolesQuery.data?.length;
  const perms = permsQuery.data?.length;
  const totalLag = healthQuery.data?.topics.reduce((acc, t) => acc + t.totalLag, 0);
  const lagTone = totalLag === undefined ? 'neutral' : totalLag === 0 ? 'success' : 'warning';

  const errorAlert = statsQuery.isError ? (
    <Alert
      type={isForbidden(statsQuery.error) ? 'info' : 'error'}
      showIcon
      title={forbiddenOr(
        statsQuery.error,
        "You don't have permission to view user counts.",
        "Couldn't load user stats",
      )}
      description={getErrorMessage(statsQuery.error)}
    />
  ) : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <header
        style={{
          paddingBottom: 20,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <Text
          type="secondary"
          style={{
            display: 'block',
            fontSize: 11,
            fontWeight: 500,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            color: token.colorTextTertiary,
            marginBottom: 6,
          }}
        >
          Control plane
        </Text>
        <h1
          style={{
            margin: 0,
            fontSize: 28,
            fontWeight: 600,
            lineHeight: 1.15,
            letterSpacing: '-0.01em',
            color: token.colorText,
          }}
        >
          Admin
        </h1>
        <Text
          type="secondary"
          style={{
            display: 'block',
            marginTop: 6,
            fontSize: 13,
            maxWidth: 720,
            lineHeight: 1.55,
          }}
        >
          Manage users, roles, sessions, and the audit pipeline. Every change is
          recorded as an audit event visible to compliance.
        </Text>
      </header>

      {errorAlert}

      <Section
        title="Population"
        subtitle={
          stats
            ? `${fmt(linked)} employee-linked · ${fmt(standalone)} standalone${
                mustChangePw && mustChangePw > 0 ? ` · ${fmt(mustChangePw)} pending password change` : ''
              }`
            : undefined
        }
      >
        <KpiGrid>
          <StatCard
            label="Total users"
            value={fmt(totalUsers)}
            icon={Users}
            tone="primary"
            variant="accent-border"
            accentSide="left"
          />
          <StatCard
            label="Active"
            value={fmt(active)}
            icon={ShieldCheck}
            tone="success"
            variant="accent-border"
            accentSide="left"
          />
          <StatCard
            label="Locked"
            value={fmt(locked)}
            icon={Lock}
            tone="warning"
            variant="accent-border"
            accentSide="left"
          />
          <StatCard
            label="Disabled"
            value={fmt(disabled)}
            icon={Lock}
            tone="neutral"
            variant="accent-border"
            accentSide="left"
          />
        </KpiGrid>
      </Section>

      <Section title="Authorization">
        <KpiGrid>
          <StatCard
            label="Roles"
            value={fmt(roles)}
            icon={ShieldCheck}
            tone="info"
            variant="accent-corner"
            accentCorner="tr"
          />
          <StatCard
            label="Permissions"
            value={fmt(perms)}
            icon={KeyRound}
            tone="primary"
            variant="accent-corner"
            accentCorner="tr"
          />
          <StatCard
            label="Audit ingestion lag"
            value={totalLag === undefined ? '—' : String(totalLag)}
            icon={Activity}
            tone={lagTone === 'success' ? 'success' : lagTone === 'warning' ? 'warning' : 'neutral'}
            variant="accent-corner"
            accentCorner="tr"
          />
        </KpiGrid>
      </Section>

      <Section title="Jump in">
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            gap: 12,
          }}
        >
          <JumpCard
            to="/admin/users"
            icon={Users}
            title="Users"
            description="Create users, reset passwords, revoke sessions, review login history."
          />
          <JumpCard
            to="/admin/roles"
            icon={ShieldCheck}
            title="Roles & Permissions"
            description="Manage roles and the permissions they grant."
          />
          <JumpCard
            to="/admin/audit"
            icon={Activity}
            title="Audit"
            description="Search activity, monitor ingestion, replay missed events."
          />
        </div>
      </Section>

      {statsQuery.isLoading && <Skeleton active paragraph={{ rows: 1 }} />}
    </div>
  );
}

function JumpCard({
  to,
  icon: Icon,
  title,
  description,
}: {
  to: '/admin/users' | '/admin/roles' | '/admin/audit';
  icon: LucideIcon;
  title: string;
  description: string;
}) {
  const { token } = useToken();
  return (
    <Link
      to={to}
      style={{
        display: 'flex',
        gap: 14,
        alignItems: 'flex-start',
        padding: 16,
        borderRadius: 12,
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        color: 'inherit',
        textDecoration: 'none',
        transition: 'transform 200ms cubic-bezier(0.22, 0.61, 0.36, 1), box-shadow 200ms',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translateY(-2px)';
        e.currentTarget.style.boxShadow = `0 8px 20px color-mix(in srgb, ${token.colorText} 8%, transparent)`;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'translateY(0)';
        e.currentTarget.style.boxShadow = 'none';
      }}
    >
      <span
        aria-hidden="true"
        style={{
          width: 36,
          height: 36,
          borderRadius: 10,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `color-mix(in srgb, ${token.colorPrimary} 14%, transparent)`,
          color: token.colorPrimary,
          flexShrink: 0,
        }}
      >
        <Icon size={18} strokeWidth={1.75} />
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 8,
            marginBottom: 4,
          }}
        >
          <span style={{ fontSize: 14, fontWeight: 600, color: token.colorText }}>{title}</span>
          <ArrowRight size={14} strokeWidth={1.75} color={token.colorTextTertiary} />
        </div>
        <Text type="secondary" style={{ fontSize: 12.5, lineHeight: 1.5 }}>
          {description}
        </Text>
      </div>
    </Link>
  );
}

function fmt(n: number | undefined): string {
  if (n === undefined) return '…';
  return n.toLocaleString();
}

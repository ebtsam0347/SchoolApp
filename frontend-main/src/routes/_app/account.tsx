// /account — layout for the four self-service auth surfaces:
//   index    → Profile (identity, roles, permissions)
//   activity → recent account events feed
//   sessions → active devices
//   security → password change + sign-in history
//
// Profile sits at the index slot because identity is the most enduring view
// of the account. Activity and Security are stream-style data with their own
// separate concerns; they get their own tabs.
//
// The header is a "user stripe": avatar + identity + inline meta (last sign-in,
// active sessions, email). Replaces a separate PageHeader + KpiGrid since the
// at-a-glance facts now live in one elegant row at the top of every tab.
//
// Tabs are real router links (not AntD `Tabs` activeKey), so back/forward
// buttons and deep-linking work without manual sync.

import { createFileRoute, Link, Outlet, useLocation } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Avatar, Tag, Typography, theme as antdTheme } from 'antd';
import {
  Activity,
  Bell,
  Briefcase,
  Clock3,
  IdCard,
  Mail,
  Monitor,
  ShieldCheck,
  UserCheck,
  type LucideIcon,
} from 'lucide-react';
import { bffApi, useAuth } from '@/auth';
import { useMyEmployee } from '@/people';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/account')({
  component: AccountLayout,
});

type TabDef = {
  to:
    | '/account'
    | '/account/employment'
    | '/account/activity'
    | '/account/sessions'
    | '/account/security'
    | '/account/notifications'
    | '/account/delegations';
  label: string;
  icon: LucideIcon;
};

const TABS: TabDef[] = [
  { to: '/account', label: 'Profile', icon: IdCard },
  { to: '/account/employment', label: 'Employment', icon: Briefcase },
  { to: '/account/delegations', label: 'Delegations', icon: UserCheck },
  { to: '/account/activity', label: 'Activity', icon: Activity },
  { to: '/account/sessions', label: 'Sessions', icon: Monitor },
  { to: '/account/notifications', label: 'Notifications', icon: Bell },
  { to: '/account/security', label: 'Security', icon: ShieldCheck },
];

function AccountLayout() {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
        width: '100%',
      }}
    >
      <UserStripe />
      <TabNav />
      <Outlet />
    </div>
  );
}

function UserStripe() {
  const auth = useAuth();
  const { token } = useToken();
  // Auth-side identity carries no picture; the employee read does. Standalone
  // users keep their initials — there's no employee row to hang one on.
  const { profilePictureUrl } = useMyEmployee();
  const summaryQuery = useQuery({
    queryKey: ['bff', 'account', 'summary'],
    queryFn: ({ signal }) => bffApi.accountSummary(signal),
  });

  // Until the BFF summary lands, fall back to the AuthProvider snapshot so
  // the stripe doesn't flash placeholders for already-known facts.
  const identity = summaryQuery.data?.identity ?? auth.user;
  const fullName = identity?.fullName ?? '—';
  const username = identity?.username ?? '';
  const email = identity?.email ?? null;
  const employeeLinked = identity?.employeeLinked ?? false;
  const roles = identity?.roles ?? [];
  const sessionCount = summaryQuery.data?.sessions.active;
  const lastLoginAt = identity?.lastLoginAt ?? null;

  return (
    <header
      style={{
        display: 'grid',
        gridTemplateColumns: 'auto minmax(0, 1fr) auto',
        alignItems: 'center',
        gap: 20,
        padding: '20px 24px',
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 16,
      }}
    >
      <Avatar
        size={60}
        src={profilePictureUrl ?? undefined}
        style={{
          background: token.colorPrimary,
          color: token.colorBgContainer,
          fontSize: 22,
          fontWeight: 600,
          flexShrink: 0,
          boxShadow: `0 6px 16px color-mix(in srgb, ${token.colorPrimary} 25%, transparent)`,
        }}
      >
        {initialsOf(fullName)}
      </Avatar>

      <div style={{ minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1
          style={{
            margin: 0,
            fontSize: 22,
            fontWeight: 600,
            lineHeight: 1.2,
            color: token.colorText,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {fullName}
        </h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <Text
            type="secondary"
            style={{
              fontSize: 13,
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            }}
          >
            @{username}
          </Text>
          {roles.length > 0 && (
            <Tag color="blue" style={{ margin: 0 }}>
              {primaryRole(roles)}
            </Tag>
          )}
          <Tag style={{ margin: 0 }}>
            {employeeLinked ? 'Employee‑linked' : 'Standalone'}
          </Tag>
        </div>
      </div>

      <div
        className="account-stripe-stats"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 20,
          flexShrink: 0,
        }}
      >
        <StatLine
          icon={Clock3}
          label="Last sign‑in"
          value={lastLoginAt ? relTime(lastLoginAt) : 'First sign‑in'}
        />
        <StripeDivider />
        <StatLine
          icon={Monitor}
          label="Sessions"
          value={typeof sessionCount === 'number' ? `${sessionCount} active` : '…'}
        />
        {email && (
          <>
            <StripeDivider />
            <StatLine icon={Mail} label="Email" value={email} mono />
          </>
        )}
      </div>
    </header>
  );
}

function StatLine({
  icon: Icon,
  label,
  value,
  mono,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
  mono?: boolean;
}) {
  const { token } = useToken();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <Icon size={16} strokeWidth={1.75} color={token.colorTextTertiary} />
      <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.25 }}>
        <span
          style={{
            fontSize: 10,
            letterSpacing: '0.06em',
            textTransform: 'uppercase',
            color: token.colorTextTertiary,
          }}
        >
          {label}
        </span>
        <span
          style={{
            fontSize: 13,
            fontWeight: 500,
            color: token.colorText,
            fontFamily: mono ? 'ui-monospace, SFMono-Regular, Menlo, monospace' : undefined,
          }}
        >
          {value}
        </span>
      </div>
    </div>
  );
}

function StripeDivider() {
  const { token } = useToken();
  return (
    <span
      aria-hidden="true"
      style={{
        width: 1,
        height: 28,
        background: token.colorBorderSecondary,
      }}
    />
  );
}

function TabNav() {
  const { token } = useToken();
  const location = useLocation();
  const currentPath = location.pathname.replace(/\/$/, '') || '/';

  return (
    <nav
      aria-label="Account sections"
      style={{
        display: 'flex',
        gap: 4,
        overflowX: 'auto',
        padding: 4,
        background: token.colorFillQuaternary,
        borderRadius: 12,
        width: 'fit-content',
        maxWidth: '100%',
      }}
    >
      {TABS.map(({ to, label, icon: Icon }) => {
        const active =
          to === '/account' ? currentPath === '/account' : currentPath.startsWith(to);
        return (
          <Link
            key={to}
            to={to}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 14px',
              fontSize: 13,
              fontWeight: 500,
              borderRadius: 8,
              color: active ? token.colorText : token.colorTextSecondary,
              background: active ? token.colorBgContainer : 'transparent',
              boxShadow: active
                ? `0 1px 0 ${token.colorBorderSecondary}, 0 4px 12px color-mix(in srgb, ${token.colorText} 6%, transparent)`
                : 'none',
              transition: 'background 160ms ease, color 160ms ease',
              whiteSpace: 'nowrap',
            }}
          >
            <Icon size={15} strokeWidth={1.75} />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '·';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

const ROLE_DISPLAY: Record<string, string> = {
  ADMIN: 'Admin',
  HR: 'HR',
  PAYROLL: 'Payroll',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
};

function primaryRole(roles: readonly string[]): string {
  const order = ['ADMIN', 'HR', 'PAYROLL', 'MANAGER', 'EMPLOYEE'];
  const upper = roles.map((r) => r.toUpperCase());
  for (const code of order) {
    if (upper.includes(code)) return ROLE_DISPLAY[code];
  }
  return ROLE_DISPLAY[upper[0]] ?? roles[0];
}

function relTime(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(ms)) return '—';
  const sec = Math.round(ms / 1000);
  if (sec < 60) return 'just now';
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const day = Math.round(hr / 24);
  if (day < 30) return `${day}d ago`;
  const mo = Math.round(day / 30);
  if (mo < 12) return `${mo}mo ago`;
  return `${Math.round(mo / 12)}y ago`;
}

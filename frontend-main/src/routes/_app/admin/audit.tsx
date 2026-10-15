// /admin/audit — layout for the audit-service admin surfaces.
//
// Three tabs:
//   Explorer — search audit_events with q + entityType + action + range
//   Health   — per-topic consumer lag (blocks deploys when non-zero)
//   Replay   — bounded Kafka offset replay tool (idempotent; dryRun preview)
//
// Tabs are router links so deep-linking works. The tab strip mirrors
// /account and /admin top-level so admins recognize the pattern.

import { createFileRoute, Link, Outlet, useLocation } from '@tanstack/react-router';
import { Typography, theme as antdTheme } from 'antd';
import { Activity, Heart, Mailbox, RotateCcw, Search, type LucideIcon } from 'lucide-react';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/admin/audit')({
  component: AuditLayout,
});

type AuditTab = {
  to:
    | '/admin/audit'
    | '/admin/audit/health'
    | '/admin/audit/replay'
    | '/admin/audit/dead-letters';
  label: string;
  icon: LucideIcon;
};

const TABS: AuditTab[] = [
  { to: '/admin/audit', label: 'Explorer', icon: Search },
  { to: '/admin/audit/health', label: 'Health', icon: Heart },
  { to: '/admin/audit/dead-letters', label: 'Dead-letters', icon: Mailbox },
  { to: '/admin/audit/replay', label: 'Replay', icon: RotateCcw },
];

function AuditLayout() {
  const { token } = useToken();
  const location = useLocation();
  const currentPath = location.pathname.replace(/\/$/, '') || '/';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '16px 20px',
          background: token.colorBgContainer,
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: 12,
        }}
      >
        <span
          aria-hidden="true"
          style={{
            width: 40,
            height: 40,
            borderRadius: 10,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: `color-mix(in srgb, ${token.colorPrimary} 12%, transparent)`,
            color: token.colorPrimary,
          }}
        >
          <Activity size={20} strokeWidth={1.75} />
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: token.colorText }}>
            Audit
          </div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Search events, monitor ingestion, and replay missed events.
          </Text>
        </div>
      </header>

      <nav
        aria-label="Audit sections"
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
            to === '/admin/audit' ? currentPath === '/admin/audit' : currentPath.startsWith(to);
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

      <Outlet />
    </div>
  );
}

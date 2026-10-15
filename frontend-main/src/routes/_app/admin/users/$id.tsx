// /admin/users/$id — single-user admin console.
//
// Five panels that map 1:1 to the auth-service admin API:
//   Identity     — fullName / email / status / employee link
//   Roles        — attach + detach against the catalog
//   Sessions     — refresh-token rows, force-revoke
//   Login history — auth.login.* + user.locked|unlocked from audit
//   Audit (by-actor) — everything this user did, across services
//
// One BFF round trip (`/bff/admin/users/{id}/overview`) replaces the
// previous fan-out of five queries. Mutations invalidate that single key,
// so all panels stay in sync without per-panel refresh affordances.
//
// Each panel is its own component in src/auth/user-detail/. This route
// owns just the layout: the back link, the header strip, KPI tiles, and
// the responsive 2-column grid for login history + actor audit.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Spin, theme as antdTheme } from 'antd';
import { ArrowLeft } from 'lucide-react';
import { bffApi } from '@/auth';
import {
  ActorAuditPanel,
  IdentityPanel,
  LoginHistoryPanel,
  RolesPanel,
  ServiceTokensPanel,
  SessionsPanel,
  UserKpiStrip,
  UserStripe,
  overviewKey,
} from '@/auth/user-detail';
import { getErrorMessage } from '@/lib';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/admin/users/$id')({
  component: UserDetailPage,
});

function UserDetailPage() {
  const { id: idStr } = Route.useParams();
  const id = Number(idStr);
  const { token } = useToken();

  const overviewQuery = useQuery({
    queryKey: overviewKey(id),
    queryFn: ({ signal }) => bffApi.adminUserOverview(id, signal),
    enabled: Number.isFinite(id),
  });

  if (!Number.isFinite(id)) {
    return <Alert type="error" showIcon title="Invalid user id" />;
  }

  if (overviewQuery.isLoading) {
    return (
      <div style={{ padding: 24, display: 'flex', justifyContent: 'center' }}>
        <Spin />
      </div>
    );
  }

  if (overviewQuery.isError || !overviewQuery.data) {
    return (
      <Alert
        type="error"
        showIcon
        title="Couldn't load user"
        description={getErrorMessage(overviewQuery.error)}
        action={
          <Link to="/admin/users">
            <Button>Back to users</Button>
          </Link>
        }
      />
    );
  }

  const overview = overviewQuery.data;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
      }}
    >
      <Link
        to="/admin/users"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          fontSize: 12,
          color: token.colorTextTertiary,
          textDecoration: 'none',
        }}
      >
        <ArrowLeft size={14} strokeWidth={1.75} /> All users
      </Link>

      <UserStripe user={overview.user} />

      <UserKpiStrip
        user={overview.user}
        sessions={overview.sessions}
        loginHistory={overview.loginHistory}
        recentActions={overview.recentActions}
      />

      <IdentityPanel userId={id} user={overview.user} />

      <RolesPanel userId={id} user={overview.user} catalog={overview.roles} />

      <ServiceTokensPanel
        userId={id}
        user={overview.user}
        catalog={overview.roles}
      />

      <SessionsPanel userId={id} sessions={overview.sessions} />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
          gap: 'var(--app-space-lg)',
        }}
      >
        <LoginHistoryPanel userId={id} initial={overview.loginHistory} />
        <ActorAuditPanel userId={id} actions={overview.recentActions} />
      </div>
    </div>
  );
}

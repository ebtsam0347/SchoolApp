// 5 stat cards under the hero stripe — counts derived client-side from
// the BFF overview payload. Pure presentational; no queries here.

import {
  Activity,
  AlertTriangle,
  LogIn,
  Monitor,
  ShieldCheck,
} from 'lucide-react';
import { KpiGrid, StatCard } from '@/components';
import type { LoginHistoryEntry, SessionDto, UserDto } from '@/auth';
import type { AuditEventDto } from '@/audit/api';

export type UserKpiStripProps = {
  user: UserDto;
  sessions: SessionDto[];
  loginHistory: LoginHistoryEntry[];
  recentActions: AuditEventDto[];
};

export function UserKpiStrip({
  user,
  sessions,
  loginHistory,
  recentActions,
}: UserKpiStripProps) {
  const now = Date.now();
  const sevenDays = now - 7 * 24 * 60 * 60_000;
  const thirtyDays = now - 30 * 24 * 60 * 60_000;

  const activeSessions = sessions.filter(
    (s) => !s.revokedAt && new Date(s.expiresAt).getTime() > now,
  ).length;

  const successfulSignIns = loginHistory.filter(
    (e) => e.action === 'login.success' && new Date(e.occurredAt).getTime() >= thirtyDays,
  ).length;

  const failedSignIns = loginHistory.filter(
    (e) => e.action === 'login.failed' && new Date(e.occurredAt).getTime() >= sevenDays,
  ).length;

  const recentActionCount = recentActions.filter(
    (a) => new Date(a.occurredAt).getTime() >= thirtyDays,
  ).length;

  return (
    <KpiGrid>
      <StatCard
        label="Roles"
        value={user.roles.length.toLocaleString()}
        icon={ShieldCheck}
        tone={user.roles.length === 0 ? 'warning' : 'info'}
        variant="accent-border"
      />
      <StatCard
        label="Active sessions"
        value={activeSessions.toLocaleString()}
        icon={Monitor}
        tone={activeSessions > 0 ? 'success' : 'neutral'}
        variant="accent-border"
      />
      <StatCard
        label="Sign-ins (30d)"
        value={successfulSignIns.toLocaleString()}
        icon={LogIn}
        tone="primary"
        variant="accent-border"
      />
      <StatCard
        label="Failed (7d)"
        value={failedSignIns.toLocaleString()}
        icon={AlertTriangle}
        tone={failedSignIns > 0 ? 'error' : 'neutral'}
        variant="accent-border"
      />
      <StatCard
        label="Activity (30d)"
        value={recentActionCount.toLocaleString()}
        icon={Activity}
        tone="neutral"
        variant="accent-border"
      />
    </KpiGrid>
  );
}

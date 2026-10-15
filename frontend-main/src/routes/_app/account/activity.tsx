// /account/activity — single consolidated feed of everything that's happened
// on the account.
//
// Two audit-service endpoints feed this page:
//   - /timeline/self/sign-in-events — sign-in successes, failures, locks,
//     unlocks. Bypasses `userVisible` so failed attempts show.
//   - /bff/account/summary.recentEvents — audit timeline for user-visible
//     events (password changes, profile edits, etc.). Excludes failed
//     sign-ins by design.
//
// We merge them chronologically and dedupe `login.success` (it appears in
// both). Sign-in events get IP / device chips; account-change events get
// the actor label. Filter chips let the user drill into failures only.
//
// This is intentionally the single home for "what's happened on my account."
// /account/security is just the password form now.

import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Segmented,
  Skeleton,
  Tag,
  Tooltip,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  AlertTriangle,
  CheckCircle2,
  History,
  KeyRound,
  Lock,
  RefreshCcw,
  ShieldAlert,
  ShieldCheck,
  UserCog,
  type LucideIcon,
} from 'lucide-react';
import {
  bffApi,
  selfSignInEventsApi,
  useAuth,
  type SelfSignInEvent,
  type TimelineEntry,
} from '@/auth';
import { isApiError } from '@/lib';
import { parseUserAgent } from '@/lib/user-agent';
import { EmptyState } from '@/components';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/account/activity')({
  component: ActivityPage,
});

// Unified shape so the row component doesn't have to branch on source.
type UnifiedEvent = {
  key: string;
  source: 'sign-in' | 'audit';
  occurredAt: string;
  action: string;
  title: string;
  /** True for any sign-in row that wasn't a success. */
  failed?: boolean;
  reason?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
  actor?: string | null;
};

type Filter = 'all' | 'sign-ins' | 'failures' | 'changes';

function ActivityPage() {
  const auth = useAuth();
  const { token } = useToken();
  const userId = auth.user?.userId;
  const [filter, setFilter] = useState<Filter>('all');

  const signInQuery = useInfiniteQuery({
    queryKey: ['self', 'sign-in-events', userId],
    enabled: typeof userId === 'number',
    queryFn: ({ signal, pageParam }) =>
      selfSignInEventsApi.list(
        { limit: 100, cursor: pageParam ?? undefined },
        signal,
      ),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });

  const summaryQuery = useQuery({
    queryKey: ['bff', 'account', 'summary'],
    queryFn: ({ signal }) => bffApi.accountSummary(signal),
  });

  const signInEvents = signInQuery.data?.pages.flatMap((p) => p.content) ?? [];

  const isLoading = signInQuery.isLoading || summaryQuery.isLoading;
  const isFetching = signInQuery.isFetching || summaryQuery.isFetching;
  const errored = signInQuery.error ?? summaryQuery.error;
  const refresh = () => {
    void signInQuery.refetch();
    void summaryQuery.refetch();
  };

  const events = mergeEvents(signInEvents, summaryQuery.data?.recentEvents);
  const failedCount = events.filter((e) => e.failed).length;
  const visible = applyFilter(events, filter);

  const filterOptions: { label: string; value: Filter }[] = [
    { label: `All (${events.length})`, value: 'all' },
    { label: 'Sign‑ins', value: 'sign-ins' },
    { label: `Failures (${failedCount})`, value: 'failures' },
    { label: 'Changes', value: 'changes' },
  ];

  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <div>
          <h2
            style={{
              margin: 0,
              fontSize: 16,
              fontWeight: 600,
              color: token.colorText,
            }}
          >
            Account activity
          </h2>
          <Text type="secondary" style={{ fontSize: 13 }}>
            Sign‑ins (including failed attempts), account locks, password
            changes — everything in one feed, newest first.
          </Text>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {events.length > 0 && (
            <Segmented<Filter>
              size="small"
              value={filter}
              onChange={(v) => setFilter(v)}
              options={filterOptions}
            />
          )}
          <Tooltip title="Refresh">
            <Button
              size="small"
              icon={<RefreshCcw size={13} strokeWidth={1.75} />}
              onClick={refresh}
              loading={isFetching && !isLoading}
              aria-label="Refresh"
            />
          </Tooltip>
        </div>
      </div>

      {failedCount > 0 && filter !== 'failures' && (
        <Alert
          type="warning"
          showIcon
          icon={<ShieldAlert size={14} strokeWidth={1.75} />}
          title={`${failedCount} failed sign‑in attempt${failedCount === 1 ? '' : 's'} in the last 100 events`}
          description={
            <span>
              If any of these weren't you,{' '}
              <a
                onClick={() => setFilter('failures')}
                style={{ color: token.colorPrimary, cursor: 'pointer' }}
              >
                review them
              </a>
              , then change your password and revoke unfamiliar sessions.
            </span>
          }
        />
      )}

      {isLoading ? (
        <Card styles={{ body: { padding: 16 } }}>
          <Skeleton active title={false} paragraph={{ rows: 6 }} />
        </Card>
      ) : errored ? (
        <Alert
          type="warning"
          showIcon
          title="Couldn't load account activity"
          description={
            isApiError(errored)
              ? errored.problem.detail ?? errored.message
              : 'Audit service is briefly unavailable.'
          }
        />
      ) : events.length === 0 ? (
        <EmptyState
          icon={History}
          title="No activity yet"
          description="As you sign in and your record changes, an entry will appear here."
        />
      ) : visible.length === 0 ? (
        <Card styles={{ body: { padding: 16 } }}>
          <Text type="secondary" style={{ fontSize: 13 }}>
            No matching events.
          </Text>
        </Card>
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {visible.map((evt, i) => (
              <ActivityRow key={evt.key} evt={evt} isLast={i === visible.length - 1} />
            ))}
          </ol>
          {signInQuery.hasNextPage && (
            <div
              style={{
                display: 'flex',
                justifyContent: 'center',
                padding: '12px 16px',
                borderTop: `1px solid ${token.colorBorderSecondary}`,
              }}
            >
              <Button
                size="small"
                onClick={() => void signInQuery.fetchNextPage()}
                loading={signInQuery.isFetchingNextPage}
              >
                Load older sign‑in events
              </Button>
            </div>
          )}
        </Card>
      )}
    </section>
  );
}

function ActivityRow({ evt, isLast }: { evt: UnifiedEvent; isLast: boolean }) {
  const { token } = useToken();
  const { Icon, color } = visualForAction(evt.action, token, evt.failed);
  const device = evt.userAgent ? parseUserAgent(evt.userAgent).label : '';

  return (
    <li
      style={{
        display: 'grid',
        gridTemplateColumns: 'auto minmax(0, 1fr) auto',
        alignItems: 'center',
        gap: 12,
        padding: '10px 16px',
        borderBottom: isLast ? 'none' : `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      <span
        aria-hidden
        style={{
          width: 24,
          height: 24,
          borderRadius: '50%',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `color-mix(in srgb, ${color} 14%, transparent)`,
          color,
          flexShrink: 0,
        }}
      >
        <Icon size={13} strokeWidth={2} />
      </span>

      <div
        style={{
          minWidth: 0,
          display: 'flex',
          alignItems: 'baseline',
          gap: 8,
          flexWrap: 'wrap',
        }}
      >
        <Text strong style={{ fontSize: 13 }}>
          {evt.title}
        </Text>
        {evt.reason && (
          <Tag
            color={evt.failed ? 'error' : 'default'}
            style={{ margin: 0, fontSize: 11 }}
          >
            {evt.reason}
          </Tag>
        )}
        {evt.ipAddress && (
          <Text
            type="secondary"
            style={{
              fontSize: 12,
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            }}
            title={`IP ${evt.ipAddress}`}
          >
            {evt.ipAddress}
          </Text>
        )}
        {device && (
          <Text
            type="secondary"
            style={{
              fontSize: 12,
              color: token.colorTextTertiary,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: 320,
            }}
            title={evt.userAgent ?? undefined}
          >
            · {device}
          </Text>
        )}
        {evt.actor && evt.source === 'audit' && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            · {evt.actor}
          </Text>
        )}
      </div>

      <Text
        type="secondary"
        style={{
          fontSize: 12,
          fontVariantNumeric: 'tabular-nums',
          flexShrink: 0,
        }}
        title={new Date(evt.occurredAt).toLocaleString()}
      >
        {shortStamp(evt.occurredAt)}
      </Text>
    </li>
  );
}

function mergeEvents(
  signIns: SelfSignInEvent[] | undefined,
  audit: TimelineEntry[] | undefined,
): UnifiedEvent[] {
  const out: UnifiedEvent[] = [];

  // Sign-in events are authoritative for login/lock actions: they include
  // failures (which audit timeline filters out) and bring IP / UA chips.
  for (const e of signIns ?? []) {
    out.push({
      key: `sign-in:${e.occurredAt}:${e.action}:${e.ipAddress ?? ''}`,
      source: 'sign-in',
      occurredAt: e.occurredAt,
      action: e.action,
      title: signInTitle(e.action),
      failed: !e.success,
      reason: e.reason,
      ipAddress: e.ipAddress,
      userAgent: e.userAgent,
    });
  }

  // Audit timeline fills in everything that ISN'T a sign-in/lock event —
  // password changes, profile edits, etc. Skip duplicates of sign-in actions
  // which the sign-ins source already covers.
  for (const e of audit ?? []) {
    if (isSignInAction(e.action)) continue;
    out.push({
      key: `audit:${e.id}`,
      source: 'audit',
      occurredAt: e.occurredAt,
      action: e.action,
      title: e.message || prettyAction(e.action),
      actor: e.actorLabel || e.actorUsername || null,
    });
  }

  out.sort((a, b) => (a.occurredAt < b.occurredAt ? 1 : a.occurredAt > b.occurredAt ? -1 : 0));
  return out;
}

function applyFilter(events: UnifiedEvent[], filter: Filter): UnifiedEvent[] {
  switch (filter) {
    case 'sign-ins':
      return events.filter((e) => isSignInAction(e.action));
    case 'failures':
      return events.filter((e) => e.failed);
    case 'changes':
      return events.filter((e) => !isSignInAction(e.action));
    default:
      return events;
  }
}

function isSignInAction(action: string): boolean {
  return (
    action === 'login.success' ||
    action === 'login.failed' ||
    action === 'user.locked' ||
    action === 'user.unlocked'
  );
}

function signInTitle(action: SelfSignInEvent['action']): string {
  switch (action) {
    case 'login.success':
      return 'Signed in';
    case 'login.failed':
      return 'Failed sign‑in attempt';
    case 'user.locked':
      return 'Account locked';
    case 'user.unlocked':
      return 'Account unlocked';
    default:
      return action;
  }
}

function visualForAction(
  action: string,
  token: ReturnType<typeof useToken>['token'],
  failed: boolean | undefined,
): { Icon: LucideIcon; color: string } {
  if (action === 'login.success')
    return { Icon: CheckCircle2, color: token.colorSuccess };
  if (action === 'login.failed')
    return { Icon: AlertTriangle, color: token.colorError };
  if (action === 'user.locked') return { Icon: Lock, color: token.colorWarning };
  if (action === 'user.unlocked')
    return { Icon: ShieldCheck, color: token.colorInfo };
  if (action.includes('password'))
    return { Icon: KeyRound, color: token.colorWarning };
  if (action.includes('user.') || action.includes('profile'))
    return { Icon: UserCog, color: token.colorInfo };
  return failed
    ? { Icon: AlertTriangle, color: token.colorError }
    : { Icon: History, color: token.colorTextTertiary };
}

// Map raw audit action codes to the same human titles `/people/$id/activity`
// uses. Mirror new entries on both sides when the audit emitter grows new
// actions; the fallback humanizes the trailing segment so unknown codes stay
// readable instead of leaking dots/underscores.
const ACCOUNT_ACTION_TITLES: Record<string, string> = {
  'password.changed': 'Password changed',
  'password.reset_by_admin': 'Password reset by admin',
  'role.assigned': 'Role assigned',
  'role.revoked': 'Role revoked',
  'ess.provisioned': 'ESS account provisioned',
  ess_granted: 'ESS access granted',
  ess_revoked: 'ESS access revoked',
  unlocked: 'Account unlocked',
  deactivated: 'Account deactivated',
  updated: 'Profile updated',
  self_updated: 'Self-service update',
  profile_picture_updated: 'Profile picture updated',
  'full_name.synced': 'Name synced from HR',
};

function prettyAction(action: string): string {
  if (ACCOUNT_ACTION_TITLES[action]) return ACCOUNT_ACTION_TITLES[action];
  // Unknown action — humanize the trailing segment so it reads as a sentence.
  return action
    .split('.')
    .pop()!
    .replace(/_/g, ' ')
    .replace(/^./, (c) => c.toUpperCase());
}

function shortStamp(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const now = new Date();
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
  return sameDay
    ? new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(d)
    : new Intl.DateTimeFormat(undefined, {
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      }).format(d);
}

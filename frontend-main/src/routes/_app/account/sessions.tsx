// /account/sessions — every active session for the caller, plus a tail of
// recently-revoked devices. The session backing this request is highlighted
// ("This device") and has its own revoke flow (it logs you out). All other
// active sessions can be revoked individually, or in bulk via "Sign out
// everywhere else".
//
// auth-service ships parsed `deviceFamily` / `browser` / `os` on SessionDto
// so we don't sniff the raw UA here. Revoked sessions stay in `listOwn` for
// ~7 days carrying `revokedAt` / `revokedBy`, which we render in a dedicated
// "Recently revoked" section so the user has a record of "you ended this
// device 2 hours ago."

import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Skeleton,
  Tag,
  Tooltip,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  CircleCheck,
  Globe,
  History,
  LogOut,
  Monitor,
  RefreshCcw,
  Trash2,
} from 'lucide-react';
import { authApi, useAuth, type SessionDto } from '@/auth';
import { getErrorMessage, isApiError } from '@/lib';
import { EmptyState, Section } from '@/components';
import { deviceFamilyIcon, formatBrowser, formatOs, parseUserAgent } from '@/lib/user-agent';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/account/sessions')({
  component: SessionsPage,
});

function SessionsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const { token } = useToken();

  const sessionsQuery = useQuery({
    queryKey: ['auth', 'sessions'],
    queryFn: ({ signal }) => authApi.listSessions(signal),
  });

  const revokeOne = useMutation({
    mutationFn: (id: number) => authApi.revokeSession(id),
    onSuccess: (revoked) => {
      void queryClient.invalidateQueries({ queryKey: ['auth', 'sessions'] });
      void queryClient.invalidateQueries({ queryKey: ['bff', 'account', 'summary'] });
      message.success(`Revoked ${describeDevice(revoked)}.`);
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const revokeOthers = useMutation({
    mutationFn: () => authApi.revokeAllSessions(true),
    onSuccess: ({ revoked }) => {
      void queryClient.invalidateQueries({ queryKey: ['auth', 'sessions'] });
      void queryClient.invalidateQueries({ queryKey: ['bff', 'account', 'summary'] });
      if (revoked.length === 0) {
        message.info('No other sessions were active.');
      } else {
        const list = revoked.map(describeDevice).join(', ');
        message.success(
          `Signed out ${revoked.length} session${revoked.length === 1 ? '' : 's'}: ${list}`,
        );
      }
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const revokeAll = useMutation({
    mutationFn: () => authApi.revokeAllSessions(false),
    onSuccess: async () => {
      message.success('Signed out everywhere.');
      await auth.logout();
      void navigate({ to: '/login', replace: true });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const sessions = sessionsQuery.data ?? [];
  const active = sessions.filter((s) => !s.revokedAt);
  const others = active.filter((s) => !s.current);
  const current = active.find((s) => s.current);
  const revoked = sessions
    .filter((s) => s.revokedAt)
    .sort((a, b) => (a.revokedAt! < b.revokedAt! ? 1 : -1));

  function confirmRevokeOne(s: SessionDto) {
    if (s.current) {
      modal.confirm({
        title: 'Sign out of this device?',
        content: 'You will be returned to the sign‑in screen.',
        okText: 'Sign out',
        okButtonProps: { danger: true },
        onOk: () => revokeAll.mutateAsync(),
      });
      return;
    }
    modal.confirm({
      title: `Revoke "${describeDevice(s)}"?`,
      content:
        'The device will need to sign in again. The access token already on it will continue to work for up to 15 minutes.',
      okText: 'Revoke',
      okButtonProps: { danger: true },
      onOk: () => revokeOne.mutateAsync(s.id),
    });
  }

  function confirmRevokeOthers() {
    modal.confirm({
      title: 'Sign out everywhere else?',
      content: `This will end ${others.length} other session${others.length === 1 ? '' : 's'} and keep this one active.`,
      okText: `Sign out ${others.length} session${others.length === 1 ? '' : 's'}`,
      okButtonProps: { danger: true },
      onOk: () => revokeOthers.mutateAsync(),
    });
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <Section
        title="Active sessions"
        subtitle="Each row is a device that completed sign‑in for your account. Revoke any device you don't recognise."
        actions={
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => sessionsQuery.refetch()}
                loading={sessionsQuery.isFetching && !sessionsQuery.isLoading}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button
              danger
              icon={<LogOut size={14} strokeWidth={1.75} />}
              disabled={others.length === 0}
              loading={revokeOthers.isPending}
              onClick={confirmRevokeOthers}
            >
              Sign out everywhere else
            </Button>
          </div>
        }
      >
        {sessionsQuery.isLoading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Skeleton active title={false} paragraph={{ rows: 2 }} />
            <Skeleton active title={false} paragraph={{ rows: 2 }} />
          </div>
        ) : sessionsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load sessions"
            description={
              isApiError(sessionsQuery.error)
                ? sessionsQuery.error.problem.detail ?? sessionsQuery.error.message
                : 'Try refreshing in a moment.'
            }
          />
        ) : active.length === 0 ? (
          <EmptyState
            icon={Monitor}
            title="No active sessions"
            description="That's unusual — you should see at least the device you're using right now."
          />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {current && (
              <SessionCard
                session={current}
                onRevoke={() => confirmRevokeOne(current)}
                isRevoking={revokeAll.isPending}
              />
            )}
            {others.map((s) => (
              <SessionCard
                key={s.id}
                session={s}
                onRevoke={() => confirmRevokeOne(s)}
                isRevoking={revokeOne.isPending && revokeOne.variables === s.id}
              />
            ))}
          </div>
        )}
      </Section>

      {revoked.length > 0 && (
        <Section
          title="Recently revoked"
          subtitle="Devices ended in the last 7 days. Kept here so you have a record of what you signed out — they cannot reconnect."
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {revoked.map((s) => (
              <RevokedSessionRow key={s.id} session={s} />
            ))}
          </div>
        </Section>
      )}

      <Card
        styles={{
          body: {
            display: 'flex',
            gap: 16,
            alignItems: 'center',
            padding: 20,
          },
        }}
        style={{
          borderColor: `color-mix(in srgb, ${token.colorError} 35%, ${token.colorBorderSecondary})`,
          background: `color-mix(in srgb, ${token.colorError} 4%, ${token.colorBgContainer})`,
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
            background: `color-mix(in srgb, ${token.colorError} 14%, transparent)`,
            color: token.colorError,
            flexShrink: 0,
          }}
        >
          <LogOut size={18} strokeWidth={1.75} />
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: token.colorText }}>
            Lost a device?
          </div>
          <Text type="secondary" style={{ fontSize: 13 }}>
            Sign out of every session — including this one — and force a fresh sign‑in everywhere.
          </Text>
        </div>
        <Button
          danger
          icon={<Trash2 size={14} strokeWidth={1.75} />}
          loading={revokeAll.isPending}
          onClick={() =>
            modal.confirm({
              title: 'Sign out of everything?',
              content:
                'This ends every active session including the one on this device. You will be redirected to sign‑in.',
              okText: 'Sign out everywhere',
              okButtonProps: { danger: true },
              onOk: () => revokeAll.mutateAsync(),
            })
          }
        >
          Sign out everywhere
        </Button>
      </Card>
    </div>
  );
}

function SessionCard({
  session,
  onRevoke,
  isRevoking,
}: {
  session: SessionDto;
  onRevoke: () => void;
  isRevoking: boolean;
}) {
  const { token } = useToken();
  const Icon = deviceFamilyIcon(session.deviceFamily);
  const browser = formatBrowser(session.browser);
  const os = formatOs(session.os);
  const lastSeen = relTime(session.lastSeenAt);
  const issued = new Date(session.issuedAt).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });

  const accent = session.current ? token.colorSuccess : token.colorTextTertiary;

  return (
    <Card
      style={{
        borderColor: session.current
          ? `color-mix(in srgb, ${token.colorSuccess} 35%, ${token.colorBorderSecondary})`
          : token.colorBorderSecondary,
      }}
      styles={{
        body: {
          padding: 18,
          display: 'flex',
          gap: 16,
          alignItems: 'flex-start',
          flexWrap: 'wrap',
        },
      }}
    >
      <span
        aria-hidden="true"
        style={{
          width: 44,
          height: 44,
          borderRadius: 10,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `color-mix(in srgb, ${accent} 12%, transparent)`,
          color: accent,
          flexShrink: 0,
        }}
      >
        <Icon size={20} strokeWidth={1.75} />
      </span>

      <div style={{ flex: 1, minWidth: 240, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 14, fontWeight: 600, color: token.colorText }}>
            {session.deviceLabel || 'Unknown device'}
          </span>
          {session.current && (
            <Tag
              color="success"
              icon={<CircleCheck size={12} strokeWidth={1.75} />}
              style={{ margin: 0 }}
            >
              This device
            </Tag>
          )}
          {browser && os && !session.deviceLabel?.includes(os.split(' ')[0]) && (
            <Tag style={{ margin: 0, fontSize: 11 }}>
              {browser} on {os}
            </Tag>
          )}
        </div>

        <div
          style={{
            display: 'flex',
            gap: 16,
            flexWrap: 'wrap',
            fontSize: 12,
            color: token.colorTextSecondary,
          }}
        >
          <Detail icon={Globe} label="IP" value={session.ipAddress || '—'} mono />
          <Detail label="Last seen" value={lastSeen} />
          <Detail label="Signed in" value={issued} />
          <Detail
            label="Expires"
            value={new Date(session.expiresAt).toLocaleDateString(undefined, {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            })}
          />
        </div>

        {session.userAgent && (
          <Tooltip title={session.userAgent}>
            <span
              style={{
                fontSize: 11,
                color: token.colorTextTertiary,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                maxWidth: '100%',
                display: 'inline-block',
              }}
            >
              {parseUserAgent(session.userAgent).label}
            </span>
          </Tooltip>
        )}
      </div>

      <Button
        danger
        type={session.current ? 'default' : 'primary'}
        ghost={!session.current}
        icon={<LogOut size={14} strokeWidth={1.75} />}
        loading={isRevoking}
        onClick={onRevoke}
      >
        {session.current ? 'Sign out' : 'Revoke'}
      </Button>
    </Card>
  );
}

function RevokedSessionRow({ session }: { session: SessionDto }) {
  const { token } = useToken();
  const Icon = deviceFamilyIcon(session.deviceFamily);
  const revokedAt = session.revokedAt ? relTime(session.revokedAt) : '—';
  const byLabel = session.revokedBy ?? 'you';

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'auto minmax(0, 1fr) auto',
        gap: 12,
        alignItems: 'center',
        padding: '10px 14px',
        borderRadius: 10,
        background: token.colorFillQuaternary,
        border: `1px solid ${token.colorBorderSecondary}`,
        opacity: 0.85,
      }}
    >
      <span
        aria-hidden="true"
        style={{
          width: 32,
          height: 32,
          flexShrink: 0,
          borderRadius: 8,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `color-mix(in srgb, ${token.colorTextTertiary} 12%, transparent)`,
          color: token.colorTextTertiary,
          textDecoration: 'line-through',
        }}
      >
        <Icon size={15} strokeWidth={1.75} />
      </span>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, color: token.colorText, fontWeight: 500 }}>
          {session.deviceLabel || 'Unknown device'}
        </div>
        <div
          style={{
            fontSize: 12,
            color: token.colorTextTertiary,
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <span>
            <History size={11} style={{ verticalAlign: -1, marginRight: 4 }} />
            Revoked {revokedAt} by {byLabel}
          </span>
          <span style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
            {session.ipAddress || '—'}
          </span>
        </div>
      </div>
      <Tag style={{ margin: 0, fontSize: 11 }}>Revoked</Tag>
    </div>
  );
}

function Detail({
  label,
  value,
  icon: Icon,
  mono,
}: {
  label: string;
  value: string;
  icon?: typeof Globe;
  mono?: boolean;
}) {
  const { token } = useToken();
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      {Icon ? <Icon size={12} strokeWidth={1.75} color={token.colorTextTertiary} /> : null}
      <span style={{ color: token.colorTextTertiary, fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {label}
      </span>
      <span
        style={{
          color: token.colorText,
          fontFamily: mono ? 'ui-monospace, SFMono-Regular, Menlo, monospace' : undefined,
        }}
      >
        {value}
      </span>
    </span>
  );
}

function describeDevice(s: SessionDto): string {
  if (s.deviceLabel) return s.deviceLabel;
  const browser = formatBrowser(s.browser);
  const os = formatOs(s.os);
  if (browser && os) return `${browser} on ${os}`;
  return browser ?? os ?? 'unknown device';
}

function relTime(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(ms)) return '—';
  const sec = Math.round(ms / 1000);
  if (sec < 60) return 'just now';
  const min = Math.round(sec / 60);
  if (min < 60) return `${min} min ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr} hr ago`;
  const day = Math.round(hr / 24);
  if (day < 30) return `${day} day${day === 1 ? '' : 's'} ago`;
  return new Date(iso).toLocaleDateString();
}

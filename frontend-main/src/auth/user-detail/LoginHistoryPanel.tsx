// Login-history feed (login.success / login.failed / user.locked /
// user.unlocked) for one user. The first page comes from the BFF
// overview; older pages are paged in via the cursor endpoint and
// concatenated.

import { Button, Empty, Tag, Tooltip, theme as antdTheme } from 'antd';
import { useInfiniteQuery } from '@tanstack/react-query';
import { CircleCheck, Lock, LockOpen, ShieldAlert } from 'lucide-react';
import { Section } from '@/components';
import {
  adminUsersApi,
  prettyLoginAction,
  prettyLoginReason,
  type LoginHistoryEntry,
} from '@/auth';
import { parseUserAgent } from '@/lib/user-agent';

const { useToken } = antdTheme;

export type LoginHistoryPanelProps = {
  userId: number;
  initial: LoginHistoryEntry[];
};

export function LoginHistoryPanel({ userId, initial }: LoginHistoryPanelProps) {
  const { token } = useToken();

  // The BFF overview only ships the first page. We page back from there
  // using the cursor-paginated /admin/users/{id}/login-history endpoint —
  // subsequent pages are appended without disturbing the BFF cache.
  const olderQuery = useInfiniteQuery({
    queryKey: ['admin', 'users', userId, 'login-history', 'older'],
    enabled: false,
    queryFn: ({ signal, pageParam }) =>
      adminUsersApi.loginHistory(
        userId,
        { cursor: pageParam ?? undefined, limit: 100 },
        signal,
      ),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });

  const older = olderQuery.data?.pages.flatMap((p) => p.content) ?? [];
  const history = [...initial, ...older];

  // We don't know the very-first nextCursor from BFF alone, so once a user
  // clicks "Load older" we let the cursor query take over.
  const canLoadMore = !olderQuery.isFetched || olderQuery.hasNextPage;

  return (
    <Section title="Login history">
      {history.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No sign‑ins yet." />
      ) : (
        <>
          <ul
            style={{
              listStyle: 'none',
              margin: 0,
              padding: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              maxHeight: 360,
              overflowY: 'auto',
              paddingRight: 4,
            }}
          >
            {history.map((e, idx) => (
              <li key={`${e.occurredAt}:${idx}`}>
                <LoginRow entry={e} />
              </li>
            ))}
          </ul>
          {canLoadMore && (
            <div
              style={{
                display: 'flex',
                justifyContent: 'center',
                marginTop: 'var(--app-space-sm)',
              }}
            >
              <Button
                size="small"
                onClick={() => void olderQuery.fetchNextPage()}
                loading={olderQuery.isFetching}
              >
                Load older
              </Button>
            </div>
          )}
        </>
      )}
    </Section>
  );

  // Inner so we get a fresh `token` from useToken without prop-passing.
  function LoginRow({ entry }: { entry: LoginHistoryEntry }) {
    const tone =
      entry.action === 'login.success'
        ? 'success'
        : entry.action === 'user.unlocked'
          ? 'info'
          : entry.action === 'user.locked'
            ? 'warning'
            : 'error';
    const color =
      tone === 'success'
        ? token.colorSuccess
        : tone === 'info'
          ? token.colorInfo
          : tone === 'warning'
            ? token.colorWarning
            : token.colorError;

    const Icon =
      entry.action === 'login.success'
        ? CircleCheck
        : entry.action === 'user.unlocked'
          ? LockOpen
          : entry.action === 'user.locked'
            ? Lock
            : ShieldAlert;

    return (
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'flex-start',
          padding: '10px 14px',
          borderRadius: 10,
          background: `color-mix(in srgb, ${color} 5%, ${token.colorBgContainer})`,
          border: `1px solid color-mix(in srgb, ${color} 22%, ${token.colorBorderSecondary})`,
        }}
      >
        <span
          aria-hidden="true"
          style={{
            width: 28,
            height: 28,
            flexShrink: 0,
            borderRadius: 'var(--app-radius-sm)',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: `color-mix(in srgb, ${color} 14%, transparent)`,
            color,
          }}
        >
          <Icon size={14} strokeWidth={2} />
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: token.colorText }}>
              {prettyLoginAction(entry.action)}
            </span>
            {entry.reason && (
              <Tag style={{ margin: 0, fontSize: 10 }}>{prettyLoginReason(entry.reason)}</Tag>
            )}
            <span
              style={{
                fontSize: 11,
                color: token.colorTextTertiary,
                marginLeft: 'auto',
              }}
            >
              {new Date(entry.occurredAt).toLocaleString()}
            </span>
          </div>
          <div
            style={{
              display: 'flex',
              gap: 16,
              fontSize: 12,
              color: token.colorTextSecondary,
              flexWrap: 'wrap',
              marginTop: 4,
            }}
          >
            <span>
              IP <code>{entry.ipAddress ?? '—'}</code>
            </span>
            {entry.userAgent && (
              <Tooltip title={entry.userAgent}>
                <span
                  style={{
                    minWidth: 0,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {parseUserAgent(entry.userAgent).label}
                </span>
              </Tooltip>
            )}
          </div>
        </div>
      </div>
    );
  }
}

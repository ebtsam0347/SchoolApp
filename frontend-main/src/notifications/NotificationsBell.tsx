// Live bell feed in the Topbar.
//
// Drives off two TanStack Query keys:
//   ['notifications', 'unread-count']      — bell badge
//   ['notifications', 'list', { unread: false, size: 8 }] — popover preview
//
// On click of an item: optimistic mark-read, invalidate both keys, navigate
// to the deep `link` if present (else stay put).
//
// Live updates ride `/bff/notifications/stream` (mounted once in `_app.tsx`
// via `useNotificationsStream`); on each `notification` SSE event the
// QueryClient invalidates everything under `['notifications', …]`. We keep a
// 5-minute background refetch as a safety net for the rare case the stream
// is down — short enough that a stuck connection self-heals, long enough not
// to clutter network logs.

import { useState } from 'react';
import { Badge, Button, Empty, Popover, Skeleton, Tooltip, theme as antdTheme } from 'antd';
import { Bell, CheckCheck, Settings2 } from 'lucide-react';
import { useNavigate } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/auth';
import { notificationsApi } from './api';
import { notificationMeta } from './types-meta';
import { relTime } from './rel-time';
import { resolveNotificationLink } from './resolve-link';
import { PushBellBanner } from './push';
import type { NotificationDto } from './types';

const { useToken } = antdTheme;

const UNREAD_KEY = ['notifications', 'unread-count'] as const;
const PREVIEW_KEY = ['notifications', 'list', { unread: false, size: 8 }] as const;

export function NotificationsBell() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const qc = useQueryClient();
  // Admins without an employee record can't receive notifications today;
  // the unread-count endpoint 403s for them and pollutes the console.
  const employeeLinked = useAuth().user?.employeeLinked === true;

  const unreadQuery = useQuery({
    queryKey: UNREAD_KEY,
    queryFn: ({ signal }) => notificationsApi.unreadCount(signal),
    // SSE owns freshness; this is a slow safety-net refetch for the case
    // where the stream is wedged. See `useNotificationsStream`.
    refetchInterval: 5 * 60_000,
    staleTime: 30_000,
    enabled: employeeLinked,
  });

  const listQuery = useQuery({
    queryKey: PREVIEW_KEY,
    queryFn: ({ signal }) => notificationsApi.list({ size: 8 }, signal),
    enabled: open && employeeLinked,
    staleTime: 15_000,
  });

  const markRead = useMutation({
    mutationFn: (id: number) => notificationsApi.markRead(id),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: PREVIEW_KEY });
      const prevList = qc.getQueryData<typeof listQuery.data>(PREVIEW_KEY);
      const prevCount = qc.getQueryData<typeof unreadQuery.data>(UNREAD_KEY);
      if (prevList) {
        qc.setQueryData(PREVIEW_KEY, {
          ...prevList,
          content: prevList.content.map((n) =>
            n.id === id && !n.readAt ? { ...n, readAt: new Date().toISOString() } : n,
          ),
        });
      }
      if (prevCount && prevList?.content.find((n) => n.id === id && !n.readAt)) {
        qc.setQueryData(UNREAD_KEY, { count: Math.max(0, prevCount.count - 1) });
      }
      return { prevList, prevCount };
    },
    onError: (_e, _id, ctx) => {
      if (ctx?.prevList) qc.setQueryData(PREVIEW_KEY, ctx.prevList);
      if (ctx?.prevCount) qc.setQueryData(UNREAD_KEY, ctx.prevCount);
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const markAll = useMutation({
    mutationFn: () => notificationsApi.markAllRead(),
    onSuccess: () => {
      qc.setQueryData(UNREAD_KEY, { count: 0 });
      void qc.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const unread = unreadQuery.data?.count ?? 0;

  function handleItemClick(n: NotificationDto) {
    if (!n.readAt) markRead.mutate(n.id);
    setOpen(false);
    if (n.link) {
      // Backend ships API-shaped paths ("/approvals/overtime/67") — map them
      // onto real routes first. See resolve-link.ts.
      const { to, search } = resolveNotificationLink(n.link);
      void navigate({ to, search });
    }
  }

  return (
    <Popover
      open={open}
      onOpenChange={setOpen}
      trigger="click"
      placement="bottomRight"
      arrow={false}
      styles={{ content: { padding: 0 } }}
      content={
        <BellPanel
          items={listQuery.data?.content ?? []}
          loading={listQuery.isLoading}
          unread={unread}
          onItemClick={handleItemClick}
          onMarkAll={() => markAll.mutate()}
          markingAll={markAll.isPending}
          onSeeAll={() => {
            setOpen(false);
            void navigate({ to: '/notifications' });
          }}
          onPreferences={() => {
            setOpen(false);
            void navigate({ to: '/account/notifications' });
          }}
        />
      }
    >
      <Tooltip title="Notifications" placement="bottom" mouseEnterDelay={0.4}>
        <button
          type="button"
          aria-label={`Notifications${unread ? ` (${unread} unread)` : ''}`}
          style={{
            width: 36,
            height: 36,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'transparent',
            border: 'none',
            borderRadius: 8,
            cursor: 'pointer',
            color: 'var(--app-topbar-text)',
            flexShrink: 0,
          }}
        >
          <Badge count={unread} size="small" offset={[-2, 2]} overflowCount={99}>
            <Bell size={18} strokeWidth={1.75} color="currentColor" />
          </Badge>
        </button>
      </Tooltip>
    </Popover>
  );
}

function BellPanel({
  items,
  loading,
  unread,
  onItemClick,
  onMarkAll,
  markingAll,
  onSeeAll,
  onPreferences,
}: {
  items: NotificationDto[];
  loading: boolean;
  unread: number;
  onItemClick: (n: NotificationDto) => void;
  onMarkAll: () => void;
  markingAll: boolean;
  onSeeAll: () => void;
  onPreferences: () => void;
}) {
  const { token } = useToken();
  return (
    <div style={{ width: 380 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 14px 10px',
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <span style={{ fontSize: 13, fontWeight: 600, color: token.colorText }}>
          Notifications
          {unread > 0 && (
            <span style={{ marginLeft: 8, color: token.colorTextSecondary, fontWeight: 400 }}>
              {unread} unread
            </span>
          )}
        </span>
        <div style={{ display: 'flex', gap: 4 }}>
          <Tooltip title="Notification preferences" placement="bottom">
            <Button
              type="text"
              size="small"
              icon={<Settings2 size={14} strokeWidth={1.75} />}
              onClick={onPreferences}
              aria-label="Notification preferences"
            />
          </Tooltip>
          <Button
            type="text"
            size="small"
            icon={<CheckCheck size={14} strokeWidth={1.75} />}
            onClick={onMarkAll}
            disabled={unread === 0}
            loading={markingAll}
            style={{ fontSize: 12 }}
          >
            Mark all read
          </Button>
        </div>
      </div>

      <PushBellBanner />

      <ul
        style={{
          listStyle: 'none',
          margin: 0,
          padding: 4,
          maxHeight: 420,
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {loading ? (
          <li style={{ padding: 12 }}>
            <Skeleton active title={false} paragraph={{ rows: 4 }} />
          </li>
        ) : items.length === 0 ? (
          <li style={{ padding: 24 }}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span style={{ fontSize: 12, color: token.colorTextSecondary }}>
                  You're all caught up
                </span>
              }
            />
          </li>
        ) : (
          items.map((n) => <BellRow key={n.id} n={n} onClick={() => onItemClick(n)} />)
        )}
      </ul>

      <div
        style={{
          padding: '8px 12px',
          borderTop: `1px solid ${token.colorBorderSecondary}`,
          textAlign: 'center',
        }}
      >
        <Button type="link" size="small" onClick={onSeeAll} style={{ fontSize: 12 }}>
          View all notifications
        </Button>
      </div>
    </div>
  );
}

function BellRow({ n, onClick }: { n: NotificationDto; onClick: () => void }) {
  const { token } = useToken();
  const meta = notificationMeta(n.type, n.category);
  const Icon = meta.icon;
  const tint = toneColor(token, meta.tone);
  const unread = !n.readAt;

  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 12,
          width: '100%',
          padding: '10px 12px',
          borderRadius: 8,
          border: 'none',
          background: unread ? `color-mix(in srgb, ${token.colorPrimary} 5%, transparent)` : 'transparent',
          textAlign: 'left',
          cursor: 'pointer',
          color: token.colorText,
          transition: 'background 120ms',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.background = token.colorFillTertiary)}
        onMouseLeave={(e) =>
          (e.currentTarget.style.background = unread
            ? `color-mix(in srgb, ${token.colorPrimary} 5%, transparent)`
            : 'transparent')
        }
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
            background: `color-mix(in srgb, ${tint} 14%, transparent)`,
            color: tint,
          }}
        >
          <Icon size={16} strokeWidth={1.75} />
        </span>
        <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span
              style={{
                fontSize: 13,
                fontWeight: unread ? 600 : 500,
                color: token.colorText,
                flex: 1,
                minWidth: 0,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {n.title}
            </span>
            {unread && (
              <span
                aria-hidden="true"
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  background: token.colorPrimary,
                  flexShrink: 0,
                }}
              />
            )}
          </span>
          {n.body && (
            <span
              style={{
                fontSize: 12,
                color: token.colorTextSecondary,
                lineHeight: 1.4,
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {n.body}
            </span>
          )}
          <span style={{ fontSize: 11, color: token.colorTextTertiary }}>
            {relTime(n.createdAt)}
          </span>
        </span>
      </button>
    </li>
  );
}

type Token = ReturnType<typeof useToken>['token'];
function toneColor(t: Token, tone: ReturnType<typeof notificationMeta>['tone']): string {
  switch (tone) {
    case 'info':
      return t.colorInfo;
    case 'success':
      return t.colorSuccess;
    case 'warning':
      return t.colorWarning;
    case 'error':
      return t.colorError;
    case 'neutral':
      return t.colorTextSecondary;
  }
}

// /notifications — full bell-feed inbox for the caller.
//
// The Topbar bell shows the latest 8 in a popover; this page is the place
// to scan everything, filter by category or unread state, and bulk-clear.
// Server pagination uses Spring's `page`/`size` tuple (`PageResponseNotificationDto`).
//
// Click a row → mark-read + navigate to the deep link the backend persisted.
// "Mark all read" hits `/notifications/read-all` and zeros the unread badge.

import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Empty,
  Pagination,
  Segmented,
  Skeleton,
  Tag,
  Tooltip,
  theme as antdTheme,
} from 'antd';
import {
  CheckCheck,
  CircleAlert,
  RefreshCcw,
  Settings2,
  type LucideIcon,
} from 'lucide-react';
import {
  notificationsApi,
  notificationMeta,
  categoryLabel,
  ALL_CATEGORIES,
  relTime,
  type NotificationCategory,
  type NotificationDto,
  resolveNotificationLink,
} from '@/notifications';
import { PageHeader } from '@/components';
import { formatDateTime, getErrorMessage, isForbidden } from '@/lib';

const { useToken } = antdTheme;

type ReadFilter = 'all' | 'unread';

export const Route = createFileRoute('/_app/notifications')({
  component: NotificationsInbox,
});

function NotificationsInbox() {
  const { token } = useToken();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [readFilter, setReadFilter] = useState<ReadFilter>('all');
  const [category, setCategory] = useState<NotificationCategory | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const size = 25;

  const listQuery = useQuery({
    queryKey: [
      'notifications',
      'list',
      { page, size, unread: readFilter === 'unread', category },
    ],
    queryFn: ({ signal }) =>
      notificationsApi.list(
        {
          page,
          size,
          unread: readFilter === 'unread' ? true : undefined,
          category: category === 'ALL' ? undefined : category,
        },
        signal,
      ),
    placeholderData: keepPreviousData,
  });

  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: ({ signal }) => notificationsApi.unreadCount(signal),
  });

  const markRead = useMutation({
    mutationFn: (id: number) => notificationsApi.markRead(id),
    onSettled: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const markAll = useMutation({
    mutationFn: () => notificationsApi.markAllRead(),
    onSuccess: () => {
      qc.setQueryData(['notifications', 'unread-count'], { count: 0 });
      void qc.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const data = listQuery.data;
  const visible = data?.content ?? [];

  function handleClick(n: NotificationDto) {
    if (!n.readAt) markRead.mutate(n.id);
    if (n.link) {
      const { to, search } = resolveNotificationLink(n.link);
      void navigate({ to, search });
    }
  }

  const unread = unreadQuery.data?.count ?? 0;
  const readOptions = [
    { label: 'All', value: 'all' as const },
    { label: `Unread${unread ? ` (${unread})` : ''}`, value: 'unread' as const },
  ];
  const categoryOptions = [
    { label: 'All', value: 'ALL' as const },
    ...ALL_CATEGORIES.map((c) => ({ label: categoryLabel(c), value: c })),
  ];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Inbox"
        title="Notifications"
        description="Approvals you need to act on, status updates on requests you've made, and announcements from your team."
        actions={
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Button
              icon={<Settings2 size={14} strokeWidth={1.75} />}
              onClick={() => void navigate({ to: '/account/notifications' })}
            >
              Preferences
            </Button>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching && !listQuery.isLoading}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button
              type="primary"
              icon={<CheckCheck size={14} strokeWidth={1.75} />}
              onClick={() => markAll.mutate()}
              disabled={unread === 0}
              loading={markAll.isPending}
            >
              Mark all read
            </Button>
          </div>
        }
      />

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Segmented<ReadFilter>
          value={readFilter}
          onChange={(v) => {
            setReadFilter(v);
            setPage(0);
          }}
          options={readOptions}
        />
        <Segmented<NotificationCategory | 'ALL'>
          value={category}
          onChange={(v) => {
            setCategory(v);
            setPage(0);
          }}
          options={categoryOptions}
        />
      </div>

      {listQuery.isLoading ? (
        <Card styles={{ body: { padding: 16 } }}>
          <Skeleton active title={false} paragraph={{ rows: 6 }} />
        </Card>
      ) : listQuery.isError ? (
        <Alert
          type={isForbidden(listQuery.error) ? 'info' : 'error'}
          showIcon
          title="Couldn't load notifications"
          description={getErrorMessage(listQuery.error)}
        />
      ) : visible.length === 0 ? (
        <Card styles={{ body: { padding: 32 } }}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              readFilter === 'unread'
                ? "You're all caught up"
                : category === 'ALL'
                  ? 'No notifications yet'
                  : `No ${categoryLabel(category as NotificationCategory).toLowerCase()} notifications`
            }
          />
        </Card>
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {visible.map((n, i) => (
              <InboxRow
                key={n.id}
                n={n}
                onClick={() => handleClick(n)}
                isLast={i === visible.length - 1}
              />
            ))}
          </ol>
        </Card>
      )}

      {data && data.totalElements > size && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '8px 0' }}>
          <Pagination
            current={page + 1}
            total={data.totalElements}
            pageSize={size}
            onChange={(p) => setPage(p - 1)}
            showSizeChanger={false}
            size="small"
          />
        </div>
      )}

      <div style={{ fontSize: 12, color: token.colorTextTertiary, textAlign: 'center' }}>
        Showing {visible.length} of {data?.totalElements ?? 0} · Notifications older than 180 days
        are archived.
      </div>
    </div>
  );
}

function InboxRow({
  n,
  onClick,
  isLast,
}: {
  n: NotificationDto;
  onClick: () => void;
  isLast: boolean;
}) {
  const { token } = useToken();
  const meta = notificationMeta(n.type, n.category);
  const Icon = meta.icon;
  const tint = toneColor(token, meta.tone);
  const unread = !n.readAt;
  const PriorityIcon: LucideIcon | null = n.priority === 'HIGH' ? CircleAlert : null;

  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        style={{
          width: '100%',
          display: 'grid',
          gridTemplateColumns: 'auto minmax(0, 1fr) auto',
          alignItems: 'flex-start',
          gap: 14,
          padding: '14px 18px',
          border: 'none',
          background: unread
            ? `color-mix(in srgb, ${token.colorPrimary} 4%, transparent)`
            : 'transparent',
          textAlign: 'left',
          cursor: 'pointer',
          color: token.colorText,
          borderBottom: isLast ? 'none' : `1px solid ${token.colorBorderSecondary}`,
          transition: 'background 120ms',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.background = token.colorFillTertiary)}
        onMouseLeave={(e) =>
          (e.currentTarget.style.background = unread
            ? `color-mix(in srgb, ${token.colorPrimary} 4%, transparent)`
            : 'transparent')
        }
      >
        <span
          aria-hidden="true"
          style={{
            width: 36,
            height: 36,
            flexShrink: 0,
            borderRadius: 10,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: `color-mix(in srgb, ${tint} 14%, transparent)`,
            color: tint,
          }}
        >
          <Icon size={18} strokeWidth={1.75} />
        </span>

        <div style={{ minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span
              style={{
                fontSize: 14,
                fontWeight: unread ? 600 : 500,
                color: token.colorText,
              }}
            >
              {n.title}
            </span>
            {PriorityIcon && (
              <Tag
                color="error"
                icon={<PriorityIcon size={11} strokeWidth={1.75} />}
                style={{ margin: 0, fontSize: 11 }}
              >
                Priority
              </Tag>
            )}
            {n.category && (
              <Tag style={{ margin: 0, fontSize: 11 }}>{categoryLabel(n.category)}</Tag>
            )}
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
          </div>
          {n.body && (
            <span style={{ fontSize: 13, color: token.colorTextSecondary, lineHeight: 1.5 }}>
              {n.body}
            </span>
          )}
        </div>

        <span
          style={{
            fontSize: 12,
            color: token.colorTextTertiary,
            fontVariantNumeric: 'tabular-nums',
            flexShrink: 0,
            paddingTop: 2,
          }}
          title={formatDateTime(n.createdAt)}
        >
          {relTime(n.createdAt)}
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


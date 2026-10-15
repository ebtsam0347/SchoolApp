// Activity timeline for an approval request (leave / short leave / overtime /
// regularization).
//
// Backed by GET /bff/timeline/{requestType}/{id} (BFF T17). The BFF probes
// the upstream request first (404 if it doesn't exist or the caller can't
// see it) and then proxies the audit-service feed. Cursor-paginated.
//
// Renders a compact list with action chip + actor + relative timestamp.
// "Load older" reveals an opaque cursor when more rows are available.

import { useInfiniteQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Tag, Typography, theme as antdTheme } from 'antd';
import {
  Activity,
  CheckCircle2,
  Clock,
  FileEdit,
  PlayCircle,
  Send,
  XCircle,
  type LucideIcon,
} from 'lucide-react';

import { TimelineItem } from '@/components';
import { getErrorMessage, isNotFound } from '@/lib';
import { timelineBff, type RequestTimelineKind, type TimelineEntryView } from './bff-api';

const { Text } = Typography;
const { useToken } = antdTheme;

type Tone = 'success' | 'error' | 'warning' | 'info' | 'neutral';

const ACTION_META: Record<string, { label: string; icon: LucideIcon; tone: Tone }> = {
  SUBMITTED: { label: 'Submitted', icon: Send, tone: 'info' },
  STAGE_APPROVED: { label: 'Stage approved', icon: CheckCircle2, tone: 'success' },
  STAGE_REJECTED: { label: 'Stage rejected', icon: XCircle, tone: 'error' },
  APPROVED: { label: 'Approved', icon: CheckCircle2, tone: 'success' },
  REJECTED: { label: 'Rejected', icon: XCircle, tone: 'error' },
  PARTIALLY_APPROVED: { label: 'Partially approved', icon: CheckCircle2, tone: 'info' },
  DAY_APPROVED: { label: 'Day approved', icon: CheckCircle2, tone: 'success' },
  DAY_REJECTED: { label: 'Day rejected', icon: XCircle, tone: 'error' },
  CANCELLED: { label: 'Cancelled', icon: XCircle, tone: 'neutral' },
  CANCELLATION_RECOMPUTED: { label: 'Recomputed', icon: PlayCircle, tone: 'warning' },
  REGULARIZED: { label: 'Regularized', icon: FileEdit, tone: 'warning' },
};

export type RequestTimelineProps = {
  /** What kind of request to load. Resolved against `/bff/timeline/{kind}/{id}`. */
  kind: RequestTimelineKind;
  requestId: number;
  /** Whether to actually fetch. Lets the caller delay until tab is opened. */
  enabled?: boolean;
};

export function RequestTimeline({ kind, requestId, enabled = true }: RequestTimelineProps) {
  const query = useInfiniteQuery({
    queryKey: ['bff', 'timeline', kind, requestId],
    enabled,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) =>
      timelineBff.forRequest(kind, requestId, { cursor: pageParam, limit: 50 }, signal),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

  const all = query.data?.pages.flatMap((p) => p.content) ?? [];

  if (query.isError) {
    if (isNotFound(query.error)) {
      return (
        <Empty
          description={
            <Text type="secondary">
              Activity is not available for this request.
            </Text>
          }
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      );
    }
    return (
      <Alert
        type="error"
        showIcon
        title="Couldn't load activity"
        description={getErrorMessage(query.error)}
      />
    );
  }

  if (query.isLoading) {
    return (
      <Text type="secondary" style={{ fontSize: 12 }}>
        Loading activity…
      </Text>
    );
  }

  if (all.length === 0) {
    return (
      <Empty
        description={<Text type="secondary">No activity recorded yet.</Text>}
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {all.map((entry, i) => (
        <TimelineRow
          key={entry.id}
          entry={entry}
          isLast={i === all.length - 1 && !query.hasNextPage}
        />
      ))}
      {query.hasNextPage ? (
        <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 8 }}>
          <Button
            type="link"
            size="small"
            loading={query.isFetchingNextPage}
            onClick={() => query.fetchNextPage()}
          >
            Load older
          </Button>
        </div>
      ) : null}
    </div>
  );
}

function TimelineRow({ entry, isLast }: { entry: TimelineEntryView; isLast: boolean }) {
  const { token } = useToken();
  const meta = ACTION_META[entry.action] ?? {
    label: humanize(entry.action),
    icon: Activity,
    tone: 'neutral' as const,
  };
  return (
    <TimelineItem
      isLast={isLast}
      tone={meta.tone}
      icon={meta.icon}
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <strong>{meta.label}</strong>
          {entry.actorLabel ? (
            <Tag style={{ margin: 0, fontSize: 11 }}>{entry.actorLabel}</Tag>
          ) : entry.actorUsername ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              @{entry.actorUsername}
            </Text>
          ) : null}
          <Text
            type="secondary"
            style={{
              fontSize: 11,
              fontVariantNumeric: 'tabular-nums',
              color: token.colorTextTertiary,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <Clock size={11} strokeWidth={1.75} />
            {relTime(entry.occurredAt)}
          </Text>
        </span>
      }
      description={entry.message ?? undefined}
    />
  );
}

function humanize(s: string): string {
  return s.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ');
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

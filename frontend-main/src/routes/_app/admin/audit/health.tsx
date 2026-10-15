// /admin/audit/health — per-topic Kafka consumer lag.
//
// One card per topic. Each card shows totalLag prominently with tone-coded
// color (green = caught up, amber = lagging, red = stalling), then per-
// partition rows with end / committed / lag. Auto-refreshes every 10s while
// the page is open.

import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Empty,
  Skeleton,
  Space,
  Tag,
  Tooltip,
  theme as antdTheme,
} from 'antd';
import { CheckCircle2, RefreshCcw, TriangleAlert, Zap } from 'lucide-react';
import { auditApi, type TopicHealth } from '@/audit';
import { Section } from '@/components';
import { forbiddenOr, getErrorMessage, isForbidden } from '@/lib';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/admin/audit/health')({
  component: HealthPage,
});

function HealthPage() {
  const healthQuery = useQuery({
    queryKey: ['admin', 'audit', 'health'],
    queryFn: ({ signal }) => auditApi.health(signal),
    refetchInterval: 10_000,
  });

  const topics = healthQuery.data?.topics ?? [];
  const grandTotalLag = topics.reduce((acc, t) => acc + t.totalLag, 0);

  return (
    <Section
      title={
        <Space>
          <Zap size={16} strokeWidth={1.75} />
          Consumer health
        </Space>
      }
      subtitle="Per-topic and per-partition consumer state. Refreshes every 10 seconds."
      actions={
        <Space>
          <SummaryTag totalLag={grandTotalLag} count={topics.length} />
          <Tooltip title="Refresh">
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              onClick={() => healthQuery.refetch()}
              loading={healthQuery.isFetching && !healthQuery.isLoading}
              aria-label="Refresh"
            />
          </Tooltip>
        </Space>
      }
    >
      {healthQuery.isError ? (
        <Alert
          type={isForbidden(healthQuery.error) ? 'info' : 'error'}
          showIcon
          title={forbiddenOr(
            healthQuery.error,
            "You don't have permission to view audit health",
            "Couldn't load audit health",
          )}
          description={getErrorMessage(healthQuery.error)}
        />
      ) : healthQuery.isLoading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : topics.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No topics reported." />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
            gap: 12,
          }}
        >
          {topics.map((t) => (
            <TopicCard key={t.topic} topic={t} />
          ))}
        </div>
      )}
    </Section>
  );
}

function SummaryTag({ totalLag, count }: { totalLag: number; count: number }) {
  if (count === 0) return null;
  if (totalLag === 0) {
    return (
      <Tag color="success" icon={<CheckCircle2 size={11} strokeWidth={2} />} style={{ margin: 0 }}>
        Caught up across {count} topic{count === 1 ? '' : 's'}
      </Tag>
    );
  }
  return (
    <Tag color="warning" icon={<TriangleAlert size={11} strokeWidth={2} />} style={{ margin: 0 }}>
      {totalLag.toLocaleString()} total lag · {count} topic{count === 1 ? '' : 's'}
    </Tag>
  );
}

function TopicCard({ topic }: { topic: TopicHealth }) {
  const { token } = useToken();
  const tone =
    topic.totalLag === 0 ? 'success' : topic.totalLag < 100 ? 'warning' : 'error';
  const color =
    tone === 'success'
      ? token.colorSuccess
      : tone === 'warning'
        ? token.colorWarning
        : token.colorError;

  return (
    <article
      style={{
        padding: 16,
        borderRadius: 12,
        background: token.colorBgContainer,
        border: `1px solid color-mix(in srgb, ${color} 22%, ${token.colorBorderSecondary})`,
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          marginBottom: 12,
        }}
      >
        <span
          aria-hidden="true"
          style={{
            width: 32,
            height: 32,
            borderRadius: 8,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: `color-mix(in srgb, ${color} 14%, transparent)`,
            color,
            flexShrink: 0,
          }}
        >
          {tone === 'success' ? (
            <CheckCircle2 size={16} strokeWidth={2} />
          ) : (
            <TriangleAlert size={16} strokeWidth={2} />
          )}
        </span>
        <div style={{ minWidth: 0, flex: 1 }}>
          <code style={{ fontSize: 13, color: token.colorText }}>{topic.topic}</code>
          <div style={{ fontSize: 11, color: token.colorTextTertiary }}>
            group <code>{topic.consumerGroup}</code>
          </div>
        </div>
        <div
          style={{
            textAlign: 'right',
            fontSize: 22,
            fontWeight: 700,
            color,
            fontVariantNumeric: 'tabular-nums',
            lineHeight: 1,
          }}
        >
          {topic.totalLag.toLocaleString()}
        </div>
      </header>

      <table
        style={{
          width: '100%',
          fontSize: 11,
          borderCollapse: 'collapse',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        <thead>
          <tr style={{ color: token.colorTextTertiary, textTransform: 'uppercase', letterSpacing: '0.04em', fontSize: 10 }}>
            <th style={{ textAlign: 'left', padding: '4px 0' }}>Partition</th>
            <th style={{ textAlign: 'right', padding: '4px 0' }}>End</th>
            <th style={{ textAlign: 'right', padding: '4px 0' }}>Committed</th>
            <th style={{ textAlign: 'right', padding: '4px 0' }}>Lag</th>
          </tr>
        </thead>
        <tbody>
          {topic.partitions.map((p) => (
            <tr key={p.partition} style={{ color: token.colorText }}>
              <td style={{ padding: '3px 0' }}>p{p.partition}</td>
              <td style={{ padding: '3px 0', textAlign: 'right' }}>{p.endOffset.toLocaleString()}</td>
              <td style={{ padding: '3px 0', textAlign: 'right' }}>
                {p.committedOffset.toLocaleString()}
              </td>
              <td
                style={{
                  padding: '3px 0',
                  textAlign: 'right',
                  color: p.lag === 0 ? token.colorTextTertiary : color,
                  fontWeight: p.lag === 0 ? 400 : 600,
                }}
              >
                {p.lag.toLocaleString()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </article>
  );
}

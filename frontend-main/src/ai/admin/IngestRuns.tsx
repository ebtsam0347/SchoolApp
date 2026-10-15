// Ops view of the embedding pipeline. Lists recent runs of the nightly
// ingest job (POLICY + EMPLOYEE), with a panel describing the index
// composition. The trigger button posts to /ai/ingest/{sourceType} on
// the live service; here it just logs a toast.

import {
  Alert,
  Button,
  Card,
  Col,
  Row,
  Space,
  Table,
  Tag,
  Typography,
  message,
  theme as antdTheme,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  BookOpen,
  Database,
  HelpCircle,
  RefreshCcw,
  ScanSearch,
  Users,
} from 'lucide-react';

import { KpiGrid, PageHeader, Section, StatCard } from '@/components';
import { formatDateTime } from '@/lib/dates';

import { PreviewNotice } from '../PreviewNotice';
import { ingestRuns, indexHealth, type IngestRun, type IngestStatus } from '../mock/data';

const { Text, Title } = Typography;
const { useToken } = antdTheme;

const SOURCE_ICONS: Record<IngestRun['sourceType'], typeof BookOpen> = {
  POLICY: BookOpen,
  EMPLOYEE: Users,
  TIMELINE: Database,
  AUDIT: Database,
};

const STATUS_COLORS: Record<IngestStatus, string> = {
  OK: 'green',
  RUNNING: 'blue',
  FAILED: 'red',
};

function durationMs(run: IngestRun): number | null {
  if (!run.finishedAt) return null;
  return new Date(run.finishedAt).getTime() - new Date(run.startedAt).getTime();
}

function formatDuration(ms: number | null): string {
  if (ms === null) return '—';
  const total = Math.round(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

export function IngestRuns() {
  const { token } = useToken();
  const [api, contextHolder] = message.useMessage();

  const trigger = (sourceType: IngestRun['sourceType']) => {
    api.open({
      type: 'info',
      content: `Trigger ${sourceType} ingest — wired to /ai/ingest/${sourceType.toLowerCase()} on the live service.`,
      duration: 3,
    });
  };

  const columns: ColumnsType<IngestRun> = [
    {
      title: 'Run',
      dataIndex: 'id',
      key: 'id',
      width: 220,
      render: (id: string, row) => {
        const Icon = SOURCE_ICONS[row.sourceType];
        return (
          <Space size={8}>
            <Icon size={14} strokeWidth={1.75} color={token.colorPrimary} />
            <Space orientation="vertical" size={0}>
              <Text style={{ fontFamily: 'var(--font-mono, ui-monospace)', fontSize: 12 }}>
                {id}
              </Text>
              <Text type="secondary" style={{ fontSize: 11 }}>
                {row.sourceType}
              </Text>
            </Space>
          </Space>
        );
      },
    },
    {
      title: 'Started',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 180,
      render: (s: string) => (
        <Text style={{ fontSize: 12 }}>{formatDateTime(s)}</Text>
      ),
    },
    {
      title: 'Duration',
      key: 'duration',
      width: 100,
      render: (_: unknown, row) => (
        <Text style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {formatDuration(durationMs(row))}
        </Text>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: IngestStatus) => (
        <Tag color={STATUS_COLORS[s]} style={{ margin: 0, fontSize: 11 }}>
          {s}
        </Tag>
      ),
    },
    {
      title: 'Inserted',
      dataIndex: 'inserted',
      key: 'inserted',
      width: 90,
      align: 'right',
      render: (n: number) => (
        <Text style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
          {n.toLocaleString()}
        </Text>
      ),
    },
    {
      title: 'Updated',
      dataIndex: 'updated',
      key: 'updated',
      width: 90,
      align: 'right',
      render: (n: number) => (
        <Text style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
          {n.toLocaleString()}
        </Text>
      ),
    },
    {
      title: 'Skipped',
      dataIndex: 'skipped',
      key: 'skipped',
      width: 90,
      align: 'right',
      render: (n: number) => (
        <Text type="secondary" style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
          {n.toLocaleString()}
        </Text>
      ),
    },
    {
      title: 'Error',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      ellipsis: true,
      render: (e?: string) =>
        e ? (
          <Text type="danger" style={{ fontSize: 12 }}>
            {e}
          </Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
  ];

  const lastFailed = ingestRuns.find((r) => r.status === 'FAILED');

  return (
    <Space orientation="vertical" size={24} style={{ width: '100%' }}>
      {contextHolder}
      <PageHeader
        eyebrow="ASSISTANT · ADMIN"
        title="Ingest runs"
        description="Recent runs of the nightly embedding pipeline that keeps the policy and employee indexes fresh. Idempotent on re-run — chunks with an unchanged source etag are skipped automatically."
        actions={
          <Space>
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              onClick={() => trigger('POLICY')}
            >
              Run policy ingest
            </Button>
            <Button
              type="primary"
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              onClick={() => trigger('EMPLOYEE')}
            >
              Run employee ingest
            </Button>
          </Space>
        }
      />

      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-corner"
          tone="primary"
          icon={ScanSearch}
          label="Total embeddings"
          value={indexHealth.totalEmbeddings.toLocaleString()}
          delta={`${indexHealth.vectorDim}-dim · ${indexHealth.pgvectorIndex.toUpperCase()}`}
        />
        <StatCard
          variant="accent-corner"
          tone="info"
          icon={BookOpen}
          label="Policy chunks"
          value={indexHealth.bySource.find((s) => s.sourceType === 'POLICY')!.count.toString()}
          delta="last refreshed today 02:04"
        />
        <StatCard
          variant="accent-corner"
          tone="success"
          icon={Users}
          label="Employee cards"
          value={indexHealth.bySource
            .find((s) => s.sourceType === 'EMPLOYEE')!
            .count.toLocaleString()}
          delta="nightly run 02:05"
        />
        <StatCard
          variant="accent-corner"
          tone="warning"
          icon={Database}
          label="Embedding model"
          value={indexHealth.model}
          delta="Ollama, CPU inference"
        />
      </KpiGrid>

      {lastFailed ? (
        <Alert
          type="warning"
          showIcon
          title={`Last failure: ${lastFailed.id}`}
          description={lastFailed.errorMessage}
        />
      ) : null}

      <Section title="Recent runs" subtitle="Newest first.">
        <Card styles={{ body: { padding: 0 } }}>
          <Table<IngestRun>
            rowKey="id"
            dataSource={ingestRuns}
            columns={columns}
            pagination={false}
            size="middle"
          />
        </Card>
      </Section>

      <Section title="Index composition">
        <Row gutter={[16, 16]}>
          {indexHealth.bySource.map((s) => {
            const Icon = SOURCE_ICONS[s.sourceType as IngestRun['sourceType']];
            return (
              <Col key={s.sourceType} xs={24} sm={12} md={6}>
                <Card
                  styles={{
                    body: { padding: 16, display: 'flex', flexDirection: 'column', gap: 6 },
                  }}
                >
                  <Space size={8}>
                    <Icon size={14} strokeWidth={1.75} color={token.colorPrimary} />
                    <Title level={5} style={{ margin: 0 }}>
                      {s.sourceType}
                    </Title>
                  </Space>
                  <Text style={{ fontSize: 22, fontWeight: 700 }}>
                    {s.count.toLocaleString()}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    last embedded {formatDateTime(s.lastEmbeddedAt)}
                  </Text>
                </Card>
              </Col>
            );
          })}
        </Row>
      </Section>

      <Card
        styles={{
          body: {
            padding: 16,
            display: 'flex',
            gap: 10,
            alignItems: 'flex-start',
          },
        }}
      >
        <HelpCircle
          size={16}
          strokeWidth={1.75}
          color={token.colorPrimary}
          style={{ marginTop: 2, flexShrink: 0 }}
        />
        <Text style={{ fontSize: 13, lineHeight: 1.6 }}>
          Timeline and audit summaries are <Text strong>not</Text> embedded
          on a nightly schedule — those are read just-in-time when an HR user
          asks for a summary, embedded for the duration of the request, and
          then discarded. Cheaper than maintaining a global index over rows
          that are read once.
        </Text>
      </Card>
    </Space>
  );
}

// /admin/audit/dead-letters — parked envelopes that ingest rejected.
//
// Operator surface for envelopes audit-service couldn't accept (malformed
// JSON, missing event_id, etc.). Each row can be replayed (re-runs through
// ingest; on success the row is removed) or discarded (drops the parked
// envelope). Selecting a row opens a drawer with the full raw payload so an
// operator can confirm the data before acting.
//
// Backed by the /admin/audit/dead-letters endpoints. Refreshes every 30s.

import { useMemo, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  DatePicker,
  Drawer,
  Input,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';
import {
  Inbox,
  Mailbox,
  Play,
  RefreshCcw,
  ShieldAlert,
  Trash2,
} from 'lucide-react';

import {
  EmptyState,
  JsonViewer,
  KpiGrid,
  RequestState,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { forbiddenOr, getErrorMessage } from '@/lib';
import { formatDateTime } from '@/lib/dates';
import { relTime } from '@/auth/format';
import {
  auditApi,
  type AuditDeadLetterDto,
  type DeadLetterReason,
  type DeadLetterReplayResult,
} from '@/audit';

const { Text } = Typography;

export const Route = createFileRoute('/_app/admin/audit/dead-letters')({
  component: DeadLettersPage,
});

const REASON_OPTIONS: { value: DeadLetterReason | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Any reason' },
  { value: 'MALFORMED_JSON', label: 'Malformed JSON' },
  { value: 'MISSING_EVENT_ID', label: 'Missing event id' },
  { value: 'OVERSIZED_EVENT_ID', label: 'Oversized event id' },
  { value: 'MISSING_REQUIRED_PAYLOAD', label: 'Missing payload' },
  { value: 'UNPARSEABLE_OCCURRED_AT', label: 'Unparseable timestamp' },
];

const REASON_LABEL: Record<DeadLetterReason, string> = {
  MALFORMED_JSON: 'Malformed JSON',
  MISSING_EVENT_ID: 'Missing event id',
  OVERSIZED_EVENT_ID: 'Oversized event id',
  MISSING_REQUIRED_PAYLOAD: 'Missing payload',
  UNPARSEABLE_OCCURRED_AT: 'Unparseable timestamp',
};

const SOURCE_OPTIONS = [
  { value: 'ALL', label: 'Any source' },
  { value: 'auth', label: 'auth' },
  { value: 'employee', label: 'employee' },
  { value: 'attendance', label: 'attendance' },
  { value: 'payroll', label: 'payroll' },
  { value: 'notification', label: 'notification' },
];

const SINCE_PRESETS: { value: '24h' | '7d' | '30d' | 'ALL'; label: string }[] = [
  { value: '24h', label: 'Last 24h' },
  { value: '7d', label: 'Last 7 days' },
  { value: '30d', label: 'Last 30 days' },
  { value: 'ALL', label: 'All time' },
];

function sinceFromPreset(p: '24h' | '7d' | '30d' | 'ALL'): string | undefined {
  if (p === 'ALL') return undefined;
  const days = p === '24h' ? 1 : p === '7d' ? 7 : 30;
  return dayjs().subtract(days, 'day').toISOString();
}

function DeadLettersPage() {
  const queryClient = useQueryClient();
  const { message, modal } = AntdApp.useApp();

  const [source, setSource] = useState<string>('ALL');
  const [reason, setReason] = useState<DeadLetterReason | 'ALL'>('ALL');
  const [sincePreset, setSincePreset] = useState<'24h' | '7d' | '30d' | 'ALL'>(
    '7d',
  );
  const [customSince, setCustomSince] = useState<dayjs.Dayjs | null>(null);
  const [keyFilter, setKeyFilter] = useState('');
  const [openId, setOpenId] = useState<number | null>(null);

  const since =
    customSince !== null ? customSince.toISOString() : sinceFromPreset(sincePreset);

  const listQuery = useQuery({
    queryKey: [
      'admin',
      'audit',
      'dead-letters',
      'list',
      { source, reason, since },
    ],
    queryFn: ({ signal }) =>
      auditApi.deadLetters.list(
        {
          source: source === 'ALL' ? undefined : source,
          reason: reason === 'ALL' ? undefined : reason,
          since,
        },
        signal,
      ),
    refetchInterval: 30_000,
  });

  const detailQuery = useQuery({
    queryKey: ['admin', 'audit', 'dead-letters', 'detail', openId],
    enabled: openId !== null,
    queryFn: ({ signal }) => auditApi.deadLetters.get(openId!, signal),
  });

  const replayMutation = useMutation({
    mutationFn: (id: number) => auditApi.deadLetters.replay(id),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({
        queryKey: ['admin', 'audit', 'dead-letters'],
      });
      describeReplay(result);
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const discardMutation = useMutation({
    mutationFn: (id: number) => auditApi.deadLetters.discard(id),
    onSuccess: (_, id) => {
      void queryClient.invalidateQueries({
        queryKey: ['admin', 'audit', 'dead-letters'],
      });
      message.success(`Discarded #${id}.`);
      if (openId === id) setOpenId(null);
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  function describeReplay(result: DeadLetterReplayResult) {
    if (result.status === 'INSERTED') {
      message.success('Replayed. Envelope was inserted into audit_events.');
      setOpenId(null);
    } else if (result.status === 'DUPLICATE') {
      message.success(
        'Already in audit_events — dead-letter row was cleaned up.',
      );
      setOpenId(null);
    } else {
      message.warning(
        `Still failing: ${result.reason ? REASON_LABEL[result.reason] : 'unknown'}${
          result.detail ? ` — ${result.detail}` : ''
        }`,
      );
    }
  }

  const rows = listQuery.data?.content ?? [];
  const filteredRows = useMemo(() => {
    const k = keyFilter.trim().toLowerCase();
    if (k.length === 0) return rows;
    return rows.filter(
      (r) =>
        (r.key ?? '').toLowerCase().includes(k) ||
        r.topic.toLowerCase().includes(k),
    );
  }, [rows, keyFilter]);

  const total = rows.length;
  const reasonCounts = useMemo(() => {
    const map = new Map<DeadLetterReason, number>();
    for (const r of rows) map.set(r.reason, (map.get(r.reason) ?? 0) + 1);
    return map;
  }, [rows]);
  const sourceCount = useMemo(() => new Set(rows.map((r) => r.sourceService)).size, [rows]);
  const replayedRecently = useMemo(
    () => rows.filter((r) => r.replayAttempts > 0).length,
    [rows],
  );

  const columns: TableProps<AuditDeadLetterDto>['columns'] = [
    {
      title: 'Source',
      key: 'source',
      width: 110,
      render: (_, row) => (
        <code style={{ fontSize: 12 }}>{row.sourceService}</code>
      ),
    },
    {
      title: 'Reason',
      key: 'reason',
      width: 180,
      render: (_, row) => (
        <StatusPill tone="warning" size="sm">
          {REASON_LABEL[row.reason]}
        </StatusPill>
      ),
    },
    {
      title: 'Topic',
      key: 'topic',
      render: (_, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <code style={{ fontSize: 12 }}>{row.topic}</code>
          <Text type="secondary" style={{ fontSize: 11, fontVariantNumeric: 'tabular-nums' }}>
            p{row.partition} · offset {row.offset.toLocaleString('en-PK')}
            {row.key ? ` · key ${row.key}` : ''}
          </Text>
        </div>
      ),
    },
    {
      title: 'Received',
      key: 'receivedAt',
      width: 170,
      render: (_, row) => (
        <Tooltip title={formatDateTime(row.receivedAt)}>
          <Text style={{ fontSize: 12 }}>{relTime(row.receivedAt)}</Text>
        </Tooltip>
      ),
    },
    {
      title: 'Replays',
      key: 'replayAttempts',
      width: 110,
      align: 'right',
      render: (_, row) =>
        row.replayAttempts > 0 ? (
          <Tooltip
            title={
              row.lastReplayAt
                ? `Last replayed ${relTime(row.lastReplayAt)}`
                : undefined
            }
          >
            <Tag color="orange" style={{ margin: 0, fontVariantNumeric: 'tabular-nums' }}>
              {row.replayAttempts}×
            </Tag>
          </Tooltip>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: '',
      key: 'actions',
      width: 110,
      render: (_, row) => (
        <Space size={4}>
          <Tooltip title="Re-run through ingest">
            <Button
              size="small"
              icon={<Play size={13} strokeWidth={1.75} />}
              loading={
                replayMutation.isPending && replayMutation.variables === row.id
              }
              onClick={(e) => {
                e.stopPropagation();
                replayMutation.mutate(row.id);
              }}
            />
          </Tooltip>
          <Tooltip title="Discard — drop the parked envelope permanently">
            <Button
              size="small"
              danger
              icon={<Trash2 size={13} strokeWidth={1.75} />}
              loading={
                discardMutation.isPending && discardMutation.variables === row.id
              }
              onClick={(e) => {
                e.stopPropagation();
                modal.confirm({
                  title: `Discard dead-letter #${row.id}?`,
                  content:
                    'The envelope is dropped permanently. Use only when ingest can never succeed (e.g. data is genuinely corrupt).',
                  okText: 'Discard',
                  okButtonProps: { danger: true },
                  onOk: () => discardMutation.mutateAsync(row.id),
                });
              }}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Section
        title={
          <Space>
            <Mailbox size={16} strokeWidth={1.75} />
            Dead-letters
          </Space>
        }
        subtitle="Envelopes ingest rejected. Replay to re-run through ingest, discard to drop. Refreshes every 30 seconds."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching && !listQuery.isLoading}
                aria-label="Refresh"
              />
            </Tooltip>
          </Space>
        }
      >
        <Space wrap size={[12, 12]} style={{ marginBottom: 16 }}>
          <Select
            value={source}
            onChange={setSource}
            options={SOURCE_OPTIONS}
            style={{ width: 150 }}
          />
          <Select<DeadLetterReason | 'ALL'>
            value={reason}
            onChange={setReason}
            options={REASON_OPTIONS}
            style={{ width: 220 }}
          />
          <Segmented<'24h' | '7d' | '30d' | 'ALL'>
            value={customSince ? 'ALL' : sincePreset}
            onChange={(v) => {
              setSincePreset(v);
              setCustomSince(null);
            }}
            options={SINCE_PRESETS}
          />
          <DatePicker
            value={customSince}
            onChange={(d) => setCustomSince(d)}
            placeholder="or pick a date"
            allowClear
            style={{ width: 180 }}
          />
          <Input
            prefix={<ShieldAlert size={13} strokeWidth={1.75} />}
            placeholder="Filter by key or topic"
            value={keyFilter}
            onChange={(e) => setKeyFilter(e.target.value)}
            style={{ width: 220 }}
            allowClear
          />
        </Space>

        <KpiGrid minColumnWidth={200}>
          <StatCard
            label="Parked"
            value={total.toLocaleString('en-PK')}
            tone={total === 0 ? 'success' : 'warning'}
            icon={Inbox}
          />
          <StatCard
            label="Distinct sources"
            value={sourceCount.toLocaleString('en-PK')}
            tone="info"
          />
          <StatCard
            label="Already retried"
            value={replayedRecently.toLocaleString('en-PK')}
            tone={replayedRecently > 0 ? 'warning' : 'info'}
          />
          {[...reasonCounts.entries()].slice(0, 1).map(([r, n]) => (
            <StatCard
              key={r}
              label="Top reason"
              value={`${REASON_LABEL[r]} (${n})`}
              tone="warning"
            />
          ))}
        </KpiGrid>

        <div style={{ marginTop: 16 }}>
          <RequestState
            loading={listQuery.isLoading}
            error={listQuery.isError ? listQuery.error : undefined}
            empty={
              !listQuery.isLoading && filteredRows.length === 0 && total === 0
            }
            errorTitle={forbiddenOr(
              listQuery.error,
              "You don't have permission to view dead-letters",
              "Couldn't load dead-letters",
            )}
            emptyTitle="No parked envelopes"
            emptyDescription="Ingest hasn't rejected anything in the selected window. Nice."
            emptyIcon={Inbox}
          >
            {filteredRows.length === 0 ? (
              <EmptyState
                size="md"
                icon={Inbox}
                title="No matches in this window"
                description="Try a wider time range or clear the source / reason filters."
              />
            ) : (
              <Table<AuditDeadLetterDto>
                rowKey="id"
                size="small"
                dataSource={filteredRows}
                columns={columns}
                // Scroll wide columns inside the table, not the page.
                scroll={{ x: 'max-content' }}
                pagination={
                  filteredRows.length > 25 ? { pageSize: 25 } : false
                }
                onRow={(row) => ({
                  onClick: () => setOpenId(row.id),
                  style: { cursor: 'pointer' },
                })}
              />
            )}
          </RequestState>
        </div>
      </Section>

      <Drawer
        open={openId !== null}
        title={
          openId !== null ? <span>Dead-letter #{openId}</span> : 'Dead-letter'
        }
        size={620}
        onClose={() => setOpenId(null)}
        destroyOnHidden
        loading={detailQuery.isLoading}
        extra={
          openId !== null ? (
            <Space>
              <Button
                icon={<Play size={14} strokeWidth={1.75} />}
                loading={
                  replayMutation.isPending &&
                  replayMutation.variables === openId
                }
                onClick={() => replayMutation.mutate(openId)}
              >
                Replay
              </Button>
              <Button
                danger
                icon={<Trash2 size={14} strokeWidth={1.75} />}
                loading={
                  discardMutation.isPending &&
                  discardMutation.variables === openId
                }
                onClick={() =>
                  modal.confirm({
                    title: `Discard dead-letter #${openId}?`,
                    content:
                      'The envelope is dropped permanently. Use only when ingest can never succeed.',
                    okText: 'Discard',
                    okButtonProps: { danger: true },
                    onOk: () => discardMutation.mutateAsync(openId),
                  })
                }
              >
                Discard
              </Button>
            </Space>
          ) : null
        }
      >
        {detailQuery.data ? (
          <DeadLetterDetail row={detailQuery.data} />
        ) : null}
      </Drawer>
    </>
  );
}

function DeadLetterDetail({ row }: { row: AuditDeadLetterDto }) {
  const parsedPayload = useMemo(() => {
    if (!row.rawPayload) return null;
    try {
      return JSON.parse(row.rawPayload) as unknown;
    } catch {
      return undefined;
    }
  }, [row.rawPayload]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Section title="Why" level={5}>
        <Space direction="vertical" size={6} style={{ width: '100%' }}>
          <StatusPill tone="warning">{REASON_LABEL[row.reason]}</StatusPill>
          {row.reasonDetail ? (
            <Text style={{ fontSize: 13 }}>{row.reasonDetail}</Text>
          ) : (
            <Text type="secondary" style={{ fontSize: 12 }}>
              No further detail captured.
            </Text>
          )}
        </Space>
      </Section>

      <Section title="Origin" level={5}>
        <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '6px 16px', margin: 0, fontSize: 12 }}>
          <dt style={{ textTransform: 'uppercase', letterSpacing: '0.04em', opacity: 0.6 }}>Source</dt>
          <dd style={{ margin: 0 }}><code>{row.sourceService}</code></dd>
          <dt style={{ textTransform: 'uppercase', letterSpacing: '0.04em', opacity: 0.6 }}>Topic</dt>
          <dd style={{ margin: 0 }}><code>{row.topic}</code></dd>
          <dt style={{ textTransform: 'uppercase', letterSpacing: '0.04em', opacity: 0.6 }}>Partition</dt>
          <dd style={{ margin: 0, fontVariantNumeric: 'tabular-nums' }}>p{row.partition}</dd>
          <dt style={{ textTransform: 'uppercase', letterSpacing: '0.04em', opacity: 0.6 }}>Offset</dt>
          <dd style={{ margin: 0, fontVariantNumeric: 'tabular-nums' }}>{row.offset.toLocaleString('en-PK')}</dd>
          {row.key ? (
            <>
              <dt style={{ textTransform: 'uppercase', letterSpacing: '0.04em', opacity: 0.6 }}>Key</dt>
              <dd style={{ margin: 0 }}><code>{row.key}</code></dd>
            </>
          ) : null}
          <dt style={{ textTransform: 'uppercase', letterSpacing: '0.04em', opacity: 0.6 }}>Received</dt>
          <dd style={{ margin: 0 }}>{formatDateTime(row.receivedAt)}</dd>
          <dt style={{ textTransform: 'uppercase', letterSpacing: '0.04em', opacity: 0.6 }}>Replays</dt>
          <dd style={{ margin: 0, fontVariantNumeric: 'tabular-nums' }}>
            {row.replayAttempts}
            {row.lastReplayAt ? ` (last ${relTime(row.lastReplayAt)})` : ''}
          </dd>
        </dl>
      </Section>

      <Section title="Raw payload" level={5}>
        {parsedPayload === null ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            No payload captured.
          </Text>
        ) : parsedPayload === undefined ? (
          <pre
            style={{
              maxHeight: 320,
              overflow: 'auto',
              fontSize: 11,
              padding: 12,
              borderRadius: 8,
              background: 'var(--app-bg-subtle, rgba(127,127,127,0.06))',
              margin: 0,
            }}
          >
            {row.rawPayload}
          </pre>
        ) : (
          <JsonViewer value={parsedPayload} maxHeight={320} />
        )}
      </Section>
    </div>
  );
}

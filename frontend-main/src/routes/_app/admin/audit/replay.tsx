// /admin/audit/replay — bounded Kafka offset replay tool.
//
// Replays a (topic, partition?, [fromOffset, toOffset)) range. Idempotent
// because audit_events.event_id is unique — duplicates count as `skipped`.
//
// Safety net (audit-service C3):
//   1. Optional "Preview count" — `dryRun: true, countOnly: true` returns an
//      upper-bound row count without polling Kafka, so the operator sees
//      "About to replay ~412 records" before doing anything.
//   2. Mandatory "Dry run" — `dryRun: true`. Issues a 5-min single-use
//      `replayToken` that the real run requires.
//   3. "Commit replay" — `dryRun: false`. Requires the dry-run's token; the
//      backend rejects the call without it. The token is consumed on success.
//
// We model these as three stacked buttons with progressive enablement: the
// real-run button doesn't activate until a fresh dry-run token is on the
// state machine.

import { useEffect, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Form,
  InputNumber,
  Select,
  Space,
  Tag,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  AlertTriangle,
  CheckCircle2,
  Hash,
  Play,
  RotateCcw,
  ShieldCheck,
  Sparkles,
  X,
} from 'lucide-react';
import { auditApi, type ReplayRequest, type ReplayResult } from '@/audit';
import { Section } from '@/components';
import { applyApiErrorToForm, getErrorMessage } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/admin/audit/replay')({
  component: ReplayPage,
});

const REPLAY_TOKEN_TTL_MS = 5 * 60_000;

type ReplayFormValues = {
  topic: string;
  partition?: number;
  fromOffset: number;
  toOffset: number;
  dryRun: boolean;
};

type ApprovedToken = {
  token: string;
  topic: string;
  fromOffset: number;
  toOffset: number;
  partition: number | null;
  issuedAt: number;
};

function ReplayPage() {
  const { modal, message } = App.useApp();
  const [form] = Form.useForm<ReplayFormValues>();
  const [lastResult, setLastResult] = useState<{ result: ReplayResult; req: ReplayRequest } | null>(
    null,
  );
  const [approvedToken, setApprovedToken] = useState<ApprovedToken | null>(null);
  const [previewCount, setPreviewCount] = useState<number | null>(null);

  const healthQuery = useQuery({
    queryKey: ['admin', 'audit', 'health'],
    queryFn: ({ signal }) => auditApi.health(signal),
  });

  // Token is single-use server-side, but we also expire it on the client so
  // the "Commit replay" button never lies about being ready.
  useEffect(() => {
    if (!approvedToken) return;
    const remaining = REPLAY_TOKEN_TTL_MS - (Date.now() - approvedToken.issuedAt);
    if (remaining <= 0) {
      setApprovedToken(null);
      return;
    }
    const t = window.setTimeout(() => setApprovedToken(null), remaining);
    return () => window.clearTimeout(t);
  }, [approvedToken]);

  const previewMutation = useMutation({
    mutationFn: (body: ReplayRequest) =>
      auditApi.replay({ ...body, dryRun: true, countOnly: true }),
    onSuccess: (result) => {
      setPreviewCount(result.replayed);
    },
    onError: (e) => {
      const { applied, unmapped } = applyApiErrorToForm(form, e);
      if (applied.length === 0) message.error(getErrorMessage(e));
      else if (unmapped.length > 0) message.error(unmapped[0].message);
    },
  });

  const dryRunMutation = useMutation({
    mutationFn: (body: ReplayRequest) => auditApi.replay({ ...body, dryRun: true }),
    onSuccess: (result, req) => {
      setLastResult({ result, req: { ...req, dryRun: true } });
      if (result.replayToken) {
        setApprovedToken({
          token: result.replayToken,
          topic: req.topic,
          fromOffset: req.fromOffset,
          toOffset: req.toOffset,
          partition: req.partition ?? null,
          issuedAt: Date.now(),
        });
      } else {
        // Backend didn't issue a token — likely a misconfiguration. Surface
        // it instead of silently letting the operator click "Commit".
        message.warning(
          'Dry-run completed but no replay token was issued. Commit will be rejected.',
        );
      }
    },
    onError: (e) => {
      const { applied, unmapped } = applyApiErrorToForm(form, e);
      if (applied.length === 0) message.error(getErrorMessage(e));
      else if (unmapped.length > 0) message.error(unmapped[0].message);
    },
  });

  const commitMutation = useMutation({
    mutationFn: (body: ReplayRequest) => auditApi.replay({ ...body, dryRun: false }),
    onSuccess: (result, req) => {
      setLastResult({ result, req: { ...req, dryRun: false } });
      setApprovedToken(null);
    },
    onError: (e) => {
      // Token consumed even on error in some backends; safest to require a
      // fresh dry-run.
      setApprovedToken(null);
      const { applied, unmapped } = applyApiErrorToForm(form, e);
      if (applied.length === 0) message.error(getErrorMessage(e));
      else if (unmapped.length > 0) message.error(unmapped[0].message);
    },
  });

  const topics = healthQuery.data?.topics ?? [];

  const matchesApprovedRange = (values: ReplayFormValues): boolean => {
    if (!approvedToken) return false;
    return (
      approvedToken.topic === values.topic &&
      approvedToken.fromOffset === values.fromOffset &&
      approvedToken.toOffset === values.toOffset &&
      (approvedToken.partition ?? null) === ((values.partition ?? null) as number | null)
    );
  };

  // Edits to the offset range invalidate the dry-run token. The state owner
  // is the user, so we just clear the token on any meaningful field change
  // rather than pop a confirm.
  const onValuesChange = (changed: Partial<ReplayFormValues>, values: ReplayFormValues) => {
    setPreviewCount(null);
    if (!approvedToken) return;
    if (
      changed.topic !== undefined ||
      changed.fromOffset !== undefined ||
      changed.toOffset !== undefined ||
      changed.partition !== undefined
    ) {
      if (!matchesApprovedRange(values)) setApprovedToken(null);
    }
  };

  const buildBody = (values: ReplayFormValues): ReplayRequest => ({
    topic: values.topic,
    partition: values.partition,
    fromOffset: values.fromOffset,
    toOffset: values.toOffset,
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <Section
        title={
          <Space>
            <RotateCcw size={16} strokeWidth={1.75} />
            Replay
          </Space>
        }
        subtitle="Re-ingest a range of events. Always dry-run first — the real run unlocks only after a successful dry-run."
      >
        <Alert
          type="warning"
          showIcon
          icon={<AlertTriangle size={16} strokeWidth={2} />}
          title="Operator-only tool"
          description="Use this only to fill in missing events. Duplicates are detected automatically and won't be inserted twice."
          style={{ marginBottom: 16 }}
        />

        <Form
          layout="vertical"
          form={form}
          initialValues={{ dryRun: true, fromOffset: 0, toOffset: 0 }}
          onValuesChange={onValuesChange}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: 16,
            }}
          >
            <Form.Item label="Topic" name="topic" rules={[{ required: true, message: 'Required' }]}>
              <Select
                placeholder="Select topic"
                options={
                  topics.length === 0
                    ? [
                        { value: 'auth.audit', label: 'auth.audit' },
                        { value: 'employee.audit', label: 'employee.audit' },
                        { value: 'attendance.audit', label: 'attendance.audit' },
                        { value: 'payroll.audit', label: 'payroll.audit' },
                        { value: 'notification.audit', label: 'notification.audit' },
                      ]
                    : topics.map((t) => ({
                        value: t.topic,
                        label: `${t.topic} (lag: ${t.totalLag})`,
                      }))
                }
                showSearch
              />
            </Form.Item>
            <Form.Item
              label="Partition"
              name="partition"
              extra="Optional — omit to replay across all partitions in the range."
            >
              <InputNumber min={0} placeholder="all" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="From offset (inclusive)"
              name="fromOffset"
              rules={[{ required: true, message: 'Required' }]}
            >
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="To offset (exclusive)"
              name="toOffset"
              rules={[{ required: true, message: 'Required' }]}
            >
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <Form.Item shouldUpdate noStyle>
            {() => {
              const values = form.getFieldsValue() as ReplayFormValues;
              const hasRange =
                !!values.topic &&
                typeof values.fromOffset === 'number' &&
                typeof values.toOffset === 'number' &&
                values.toOffset > values.fromOffset;
              const tokenStillValid = approvedToken && matchesApprovedRange(values);

              return (
                <Space wrap size={8} style={{ marginTop: 8 }}>
                  <Button
                    icon={<Hash size={14} strokeWidth={1.75} />}
                    disabled={!hasRange}
                    loading={previewMutation.isPending}
                    onClick={() => {
                      form.validateFields().then((v) => {
                        previewMutation.mutate(buildBody(v as ReplayFormValues));
                      }).catch(() => {});
                    }}
                  >
                    Preview count
                  </Button>
                  <Button
                    type={tokenStillValid ? 'default' : 'primary'}
                    icon={<Sparkles size={14} strokeWidth={1.75} />}
                    disabled={!hasRange}
                    loading={dryRunMutation.isPending}
                    onClick={() => {
                      form.validateFields().then((v) => {
                        modal.confirm({
                          title: 'Run dry-run?',
                          content: `Counts what would be replayed for ${(v as ReplayFormValues).topic} (${(v as ReplayFormValues).fromOffset}–${(v as ReplayFormValues).toOffset}). The dry-run unlocks the commit step for 5 minutes.`,
                          okText: 'Dry-run',
                          onOk: () => dryRunMutation.mutateAsync(buildBody(v as ReplayFormValues)),
                        });
                      }).catch(() => {});
                    }}
                  >
                    Dry run
                  </Button>
                  <Button
                    danger
                    type="primary"
                    icon={<Play size={14} strokeWidth={2} />}
                    disabled={!tokenStillValid}
                    loading={commitMutation.isPending}
                    onClick={() => {
                      form.validateFields().then((v) => {
                        modal.confirm({
                          title: 'Commit replay for real?',
                          content: `This will replay ${(v as ReplayFormValues).topic} from ${(v as ReplayFormValues).fromOffset} to ${(v as ReplayFormValues).toOffset}. Duplicates are skipped automatically.`,
                          okText: 'Commit',
                          okButtonProps: { danger: true },
                          onOk: () =>
                            commitMutation.mutateAsync({
                              ...buildBody(v as ReplayFormValues),
                              replayToken: approvedToken?.token ?? '',
                            }),
                        });
                      }).catch(() => {});
                    }}
                  >
                    Commit replay
                  </Button>
                  <Button
                    onClick={() => {
                      form.resetFields();
                      setLastResult(null);
                      setPreviewCount(null);
                      setApprovedToken(null);
                    }}
                    icon={<X size={14} strokeWidth={1.75} />}
                  >
                    Reset
                  </Button>
                </Space>
              );
            }}
          </Form.Item>

          {previewCount !== null && (
            <Alert
              showIcon
              type="info"
              icon={<Hash size={14} strokeWidth={1.75} />}
              title={
                <span>
                  About to replay <strong>~{previewCount.toLocaleString()}</strong> record
                  {previewCount === 1 ? '' : 's'}.{' '}
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Approximate — the actual count may differ slightly at run time.
                  </Text>
                </span>
              }
              style={{ marginTop: 12 }}
            />
          )}

          {approvedToken && (
            <Alert
              type="success"
              showIcon
              icon={<ShieldCheck size={14} strokeWidth={1.75} />}
              title={<TokenBanner token={approvedToken} />}
              style={{ marginTop: 12 }}
            />
          )}
        </Form>
      </Section>

      {lastResult && (
        <Section
          title={
            <Space>
              {lastResult.result.dryRun ? (
                <Sparkles size={16} strokeWidth={1.75} />
              ) : (
                <CheckCircle2 size={16} strokeWidth={1.75} />
              )}
              {lastResult.result.dryRun ? 'Dry-run result' : 'Replay result'}
            </Space>
          }
          subtitle={
            <span>
              <code>{lastResult.req.topic}</code>{' '}
              <Text type="secondary">offsets {lastResult.req.fromOffset}–{lastResult.req.toOffset}</Text>
              {lastResult.req.partition !== undefined && (
                <> · partition {lastResult.req.partition}</>
              )}
            </span>
          }
          actions={
            lastResult.result.dryRun ? (
              <Tag color="blue" style={{ margin: 0 }}>
                DRY-RUN
              </Tag>
            ) : (
              <Tag color="green" style={{ margin: 0 }}>
                COMMITTED
              </Tag>
            )
          }
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
              gap: 12,
            }}
          >
            <ResultStat
              label="Replayed"
              value={lastResult.result.replayed}
              tone="success"
              hint={lastResult.result.dryRun ? 'Would have inserted' : 'Inserted'}
            />
            <ResultStat
              label="Skipped"
              value={lastResult.result.skipped}
              tone="neutral"
              hint="Duplicates / malformed"
            />
            <ResultStat
              label="Errors"
              value={lastResult.result.errors}
              tone={lastResult.result.errors > 0 ? 'error' : 'neutral'}
              hint={lastResult.result.errors > 0 ? 'Investigate logs' : 'None'}
            />
          </div>
        </Section>
      )}

    </div>
  );
}

function TokenBanner({ token }: { token: ApprovedToken }) {
  const remaining = Math.max(0, REPLAY_TOKEN_TTL_MS - (Date.now() - token.issuedAt));
  const mins = Math.floor(remaining / 60_000);
  const secs = Math.floor((remaining % 60_000) / 1000);
  return (
    <span>
      Dry-run token issued — Commit unlocked for{' '}
      <strong>
        {mins}m {secs.toString().padStart(2, '0')}s
      </strong>
      . Editing any offset clears the token.
    </span>
  );
}

function ResultStat({
  label,
  value,
  tone,
  hint,
}: {
  label: string;
  value: number;
  tone: 'success' | 'error' | 'neutral';
  hint: string;
}) {
  const { token } = useToken();
  const color =
    tone === 'success'
      ? token.colorSuccess
      : tone === 'error'
        ? token.colorError
        : token.colorTextTertiary;

  return (
    <div
      style={{
        padding: 16,
        borderRadius: 12,
        background: token.colorBgContainer,
        border: `1px solid color-mix(in srgb, ${color} 18%, ${token.colorBorderSecondary})`,
      }}
    >
      <div style={{ fontSize: 11, color: token.colorTextTertiary, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        {label}
      </div>
      <div
        style={{
          fontSize: 28,
          fontWeight: 700,
          color,
          marginTop: 4,
          fontVariantNumeric: 'tabular-nums',
          lineHeight: 1,
        }}
      >
        {value.toLocaleString()}
      </div>
      <div style={{ fontSize: 11, color: token.colorTextSecondary, marginTop: 4 }}>{hint}</div>
    </div>
  );
}

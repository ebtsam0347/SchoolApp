// Warnings panel — FY counts + per-action list, with Issue + Decide modals.
// Originally the /people/$id/disciplinary route; folded into the Employment
// tab in the May-2026 tab consolidation since warnings are an employment-
// state concern, not their own domain.
//
// Usage: render <WarningsPanel employeeId={n} /> inside the Employment tab.

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Skeleton,
  Space,
  Table,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';
import { ShieldAlert, ShieldX } from 'lucide-react';
import { useState } from 'react';

import {
  KpiGrid,
  RequestState,
  Section,
  StatCard,
  WarningPill,
} from '@/components';
import {
  applyApiErrorToForm,
  getErrorMessage,
  isApiError,
} from '@/lib';
import {
  DISCIPLINARY_CATEGORY_LABEL,
  disciplinaryApi,
  type DecideDisciplinaryRequest,
  type DisciplinaryActionDto,
  type DisciplinaryCategory,
  type DisciplinaryDecision,
  type DisciplinaryLevel,
  type IssueDisciplinaryRequest,
} from '@/people';

const { Text, Paragraph } = Typography;

export type WarningsPanelProps = {
  employeeId: number;
};

export function WarningsPanel({ employeeId }: WarningsPanelProps) {
  const [issueOpen, setIssueOpen] = useState(false);
  const [decideTarget, setDecideTarget] = useState<DisciplinaryActionDto | null>(null);

  const listQuery = useQuery({
    queryKey: ['people', 'disciplinary', employeeId],
    queryFn: ({ signal }) => disciplinaryApi.perEmployee(employeeId, {}, signal),
    enabled: Number.isFinite(employeeId),
  });

  const data = listQuery.data;
  const counts = data?.warningCounts ?? {};
  const actions = data?.actions ?? [];

  if (listQuery.isLoading || listQuery.isError) {
    return (
      <RequestState
        loading={listQuery.isLoading}
        error={listQuery.isError ? listQuery.error : undefined}
        loadingFallback={<Skeleton active paragraph={{ rows: 5 }} />}
        errorTitle="Couldn't load disciplinary actions"
      >
        {null}
      </RequestState>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <KpiGrid>
        <StatCard
          variant="accent-corner"
          tone="warning"
          accentCorner="tr"
          label="First warnings"
          value={counts.FIRST_WARNING ?? 0}
          delta={data?.fyCode ? `Upheld in FY ${data.fyCode}` : 'Upheld this FY'}
          icon={ShieldAlert}
        />
        <StatCard
          variant="accent-corner"
          tone="warning"
          accentCorner="tr"
          label="Second warnings"
          value={counts.SECOND_WARNING ?? 0}
          delta={data?.fyCode ? `Upheld in FY ${data.fyCode}` : 'Upheld this FY'}
          icon={ShieldAlert}
        />
        <StatCard
          variant="accent-radial"
          tone="error"
          accentCorner="tr"
          label="Final warnings"
          value={counts.FINAL_WARNING ?? 0}
          delta={data?.fyCode ? `Upheld in FY ${data.fyCode}` : 'Upheld this FY'}
          icon={ShieldX}
        />
      </KpiGrid>

      <Section
        title="All warnings"
        subtitle={
          <span>
            ISSUED rows pending decision. Withdrawn rows are kept for transparency
            but don't count toward FY totals.
          </span>
        }
        actions={
          <Button
            type="primary"
            danger
            icon={<ShieldAlert size={16} strokeWidth={1.75} />}
            onClick={() => setIssueOpen(true)}
          >
            Issue warning
          </Button>
        }
      >
        {actions.length === 0 ? (
          <Text type="secondary">No disciplinary actions on file.</Text>
        ) : (
          <ActionsTable
            actions={actions}
            onDecide={(action) => setDecideTarget(action)}
          />
        )}
      </Section>

      {issueOpen ? (
        <IssueWarningModal
          employeeId={employeeId}
          onClose={() => setIssueOpen(false)}
        />
      ) : null}
      {decideTarget ? (
        <DecideModal
          employeeId={employeeId}
          action={decideTarget}
          onClose={() => setDecideTarget(null)}
        />
      ) : null}
    </div>
  );
}

/* --------------------- Table --------------------- */

function ActionsTable({
  actions,
  onDecide,
}: {
  actions: DisciplinaryActionDto[];
  onDecide: (action: DisciplinaryActionDto) => void;
}) {
  const sorted = [...actions].sort((a, b) =>
    (b.issuedOn ?? '').localeCompare(a.issuedOn ?? ''),
  );

  const columns: TableProps<DisciplinaryActionDto>['columns'] = [
    {
      title: 'Level',
      key: 'level',
      width: 180,
      render: (_v, row) =>
        row.level ? (
          <WarningPill
            level={row.level}
            status={row.status}
            decision={row.decision ?? null}
          />
        ) : null,
    },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      width: 160,
      render: (v?: DisciplinaryCategory) =>
        v ? DISCIPLINARY_CATEGORY_LABEL[v] : '—',
    },
    {
      title: 'Issued',
      dataIndex: 'issuedOn',
      key: 'issuedOn',
      width: 140,
      render: formatDate,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: 'Decision',
      key: 'decision',
      width: 200,
      render: (_v, row) =>
        row.decision ? (
          <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
            <Text strong>{row.decision}</Text>
            {row.closedOn ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {formatDate(row.closedOn)}
              </Text>
            ) : null}
          </span>
        ) : (
          <Text type="secondary">Pending</Text>
        ),
    },
    {
      title: '',
      key: 'actions',
      width: 100,
      render: (_v, row) =>
        row.status === 'ISSUED' ? (
          <Button size="small" onClick={() => onDecide(row)}>
            Decide
          </Button>
        ) : null,
    },
  ];

  return (
    <Table<DisciplinaryActionDto>
      rowKey={(row) => String(row.id ?? Math.random())}
      dataSource={sorted}
      columns={columns}
      pagination={{ pageSize: 8, showSizeChanger: false }}
      size="small"
      scroll={{ x: 'max-content' }}
    />
  );
}

/* --------------------- Issue modal --------------------- */

export function IssueWarningModal({
  employeeId,
  onClose,
}: {
  employeeId: number;
  onClose: () => void;
}) {
  type IssueForm = {
    level: DisciplinaryLevel;
    category: DisciplinaryCategory;
    incidentDate?: dayjs.Dayjs;
    issuedOn: dayjs.Dayjs;
    reason: string;
  };

  const [form] = Form.useForm<IssueForm>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [topLevel, setTopLevel] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: IssueDisciplinaryRequest) =>
      disciplinaryApi.issue(employeeId, body),
    onSuccess: () => {
      message.success('Warning issued.');
      void qc.invalidateQueries({ queryKey: ['people', 'disciplinary', employeeId] });
      void qc.invalidateQueries({ queryKey: ['people', 'detail', employeeId] });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setTopLevel(getErrorMessage(e));
      }
    },
  });

  return (
    <Modal
      open
      title="Issue warning"
      okType="danger"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Issue warning"
      confirmLoading={mutation.isPending}
      destroyOnHidden
      width={520}
    >
      <Paragraph type="secondary">
        Recorded as the system of record. The notice itself is delivered offline
        — HR hands the employee a printed letter. The decision (uphold / withdraw)
        is captured later via the row's <Text strong>Decide</Text> action.
      </Paragraph>
      {topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ level: 'FIRST_WARNING', category: 'MISCONDUCT', issuedOn: dayjs() }}
        onFinish={(values) => {
          setTopLevel(null);
          mutation.mutate({
            level: values.level,
            category: values.category,
            incidentDate: values.incidentDate?.format('YYYY-MM-DD'),
            issuedOn: values.issuedOn.format('YYYY-MM-DD'),
            reason: values.reason.trim(),
          });
        }}
      >
        <Space.Compact block>
          <Form.Item name="level" label="Level" rules={[{ required: true }]} style={{ flex: 1, marginRight: 8 }}>
            <Select
              options={[
                { value: 'FIRST_WARNING', label: 'First warning' },
                { value: 'SECOND_WARNING', label: 'Second warning' },
                { value: 'FINAL_WARNING', label: 'Final warning' },
              ]}
            />
          </Form.Item>
          <Form.Item name="category" label="Category" rules={[{ required: true }]} style={{ flex: 1 }}>
            <Select
              options={(Object.keys(DISCIPLINARY_CATEGORY_LABEL) as DisciplinaryCategory[]).map((c) => ({
                value: c,
                label: DISCIPLINARY_CATEGORY_LABEL[c],
              }))}
            />
          </Form.Item>
        </Space.Compact>
        <Space.Compact block>
          <Form.Item name="issuedOn" label="Issued on" rules={[{ required: true }]} style={{ flex: 1, marginRight: 8 }}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="incidentDate" label="Incident date (optional)" style={{ flex: 1 }}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Space.Compact>
        <Form.Item
          name="reason"
          label="Reason"
          rules={[{ required: true, message: 'Reason is required' }]}
        >
          <Input.TextArea
            rows={4}
            placeholder="What did the employee do? Stick to facts; the letter delivered offline can elaborate."
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- Decide modal --------------------- */

function DecideModal({
  employeeId,
  action,
  onClose,
}: {
  employeeId: number;
  action: DisciplinaryActionDto;
  onClose: () => void;
}) {
  type DecideForm = {
    decision: DisciplinaryDecision;
    decisionNotes?: string;
    closedOn: dayjs.Dayjs;
  };

  const [form] = Form.useForm<DecideForm>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [topLevel, setTopLevel] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: DecideDisciplinaryRequest) =>
      disciplinaryApi.decide(action.id!, body),
    onSuccess: () => {
      message.success('Decision recorded.');
      void qc.invalidateQueries({ queryKey: ['people', 'disciplinary', employeeId] });
      void qc.invalidateQueries({ queryKey: ['people', 'detail', employeeId] });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setTopLevel(getErrorMessage(e));
      }
    },
  });

  return (
    <Modal
      open
      title="Decide warning"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Record decision"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Paragraph type="secondary">
        Choose <Text strong>Uphold</Text> to count this warning toward FY totals,
        or <Text strong>Withdraw</Text> to keep the row for transparency without
        counting it.
      </Paragraph>
      {topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ decision: 'UPHELD', closedOn: dayjs() }}
        onFinish={(values) => {
          setTopLevel(null);
          mutation.mutate({
            decision: values.decision,
            decisionNotes: values.decisionNotes?.trim() || undefined,
            closedOn: values.closedOn.format('YYYY-MM-DD'),
          });
        }}
      >
        <Form.Item name="decision" label="Decision" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'UPHELD', label: 'Upheld — counts toward FY total' },
              { value: 'WITHDRAWN', label: 'Withdrawn — does not count' },
            ]}
          />
        </Form.Item>
        <Form.Item name="closedOn" label="Closed on">
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="decisionNotes" label="Notes (optional)">
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- helpers --------------------- */

function formatDate(value: string | undefined): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-PK', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value));
}

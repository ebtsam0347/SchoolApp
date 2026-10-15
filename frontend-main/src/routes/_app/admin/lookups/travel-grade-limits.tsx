// /admin/lookups/travel-grade-limits — "Grade Limits" tab.
//
// Grade-specific entitlement limits (one per grade + expense type) on a
// travel policy version. Wired to GET/POST
// /travel/policies/{policyVersionId}/grade-limits (services/employee,
// gateway path /travel/**). Reads require travel.read; adding a limit
// requires travel.admin and only works while the version is DRAFT.

import { useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { Pencil, Plus, RefreshCcw, Sliders, Trash2 } from 'lucide-react';

import { DrawerForm, EmptyState, PageHeader, RequestState, Section } from '@/components';
import { getErrorMessage, handleFormError } from '@/lib';
import { catalogsApi } from '@/people';
import {
  travelPolicyApi,
  type CreateGradeLimitRequest,
  type GradeLimitResponse,
} from '@/travel';

type GradeLimitFormValues = {
  gradeId: number;
  expenseTypeCode: string;
  limitType: string;
  limitAmount: number;
  currencyCode: string;
  isActive: boolean;
  remarks?: string;
};

export const Route = createFileRoute('/_app/admin/lookups/travel-grade-limits')({
  component: TravelGradeLimitsTab,
});

function TravelGradeLimitsTab() {
  const [policyVersionId, setPolicyVersionId] = useState<number | undefined>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingLimit, setEditingLimit] = useState<GradeLimitResponse | null>(null);
  const [form] = Form.useForm<GradeLimitFormValues>();
  const { message, modal } = AntdApp.useApp();
  const qc = useQueryClient();

  const policiesQuery = useQuery({
    queryKey: ['travel-policies', 'all'],
    queryFn: ({ signal }) => travelPolicyApi.search({}, signal),
  });

  const gradesQuery = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: 5 * 60 * 1000,
  });

  const limitsQuery = useQuery({
    queryKey: ['travel-policy-grade-limits', policyVersionId],
    queryFn: ({ signal }) => travelPolicyApi.gradeLimits(policyVersionId as number, signal),
    enabled: policyVersionId != null,
  });

  const createMutation = useMutation({
    mutationFn: (body: CreateGradeLimitRequest) =>
      travelPolicyApi.createGradeLimit(policyVersionId as number, body),
    onSuccess: () => {
      message.success('Grade limit added');
      qc.invalidateQueries({ queryKey: ['travel-policy-grade-limits', policyVersionId] });
      setDrawerOpen(false);
      form.resetFields();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, gradeCode, values }: { id: number; gradeCode: string; values: GradeLimitFormValues }) =>
      travelPolicyApi.updateGradeLimit(id, {
        gradeCode,
        expenseTypeCode: values.expenseTypeCode,
        limitType: values.limitType,
        limitAmount: values.limitAmount,
        currencyCode: values.currencyCode,
        isActive: values.isActive,
        remarks: values.remarks,
      }),
    onSuccess: () => {
      message.success('Grade limit updated');
      qc.invalidateQueries({ queryKey: ['travel-policy-grade-limits', policyVersionId] });
      setDrawerOpen(false);
      setEditingLimit(null);
      form.resetFields();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => travelPolicyApi.deleteGradeLimit(id),
    onSuccess: () => {
      message.success('Grade limit deleted');
      qc.invalidateQueries({ queryKey: ['travel-policy-grade-limits', policyVersionId] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const policyOptions = useMemo(
    () =>
      (policiesQuery.data?.content ?? []).map((p) => ({
        value: p.id,
        label: `${p.policyCode} · v${p.versionNo ?? '?'} — ${p.policyName ?? ''} (${p.status ?? ''})`,
      })),
    [policiesQuery.data],
  );

  const gradeOptions = useMemo(
    () =>
      (gradesQuery.data ?? []).map((g) => ({
        value: g.id as number,
        label: g.name ?? g.code ?? String(g.id),
      })),
    [gradesQuery.data],
  );

  const rows = limitsQuery.data ?? [];

  const columns: ColumnsType<GradeLimitResponse> = [
    { title: 'Grade', key: 'grade', render: (_, r) => r.gradeName ?? r.gradeCode },
    { title: 'Expense type', dataIndex: 'expenseTypeCode', key: 'expenseTypeCode' },
    { title: 'Limit type', dataIndex: 'limitType', key: 'limitType' },
    {
      title: 'Amount',
      key: 'limitAmount',
      render: (_, r) => `${r.limitAmount.toLocaleString('en-PK')} ${r.currencyCode}`,
    },
    {
      title: 'Active',
      dataIndex: 'isActive',
      key: 'isActive',
      width: 90,
      render: (v?: boolean | null) => (v === false ? 'No' : 'Yes'),
    },
    { title: 'Remarks', dataIndex: 'remarks', key: 'remarks', ellipsis: true },
    {
      title: 'Actions',
      key: 'actions',
      width: 110,
      render: (_, r) => (
        <Space size={4}>
          <Tooltip title="Edit">
            <Button
              size="small"
              icon={<Pencil size={13} />}
              onClick={() => {
                setEditingLimit(r);
                form.setFieldsValue({
                  gradeId: r.gradeId,
                  expenseTypeCode: r.expenseTypeCode,
                  limitType: r.limitType,
                  limitAmount: r.limitAmount,
                  currencyCode: r.currencyCode,
                  isActive: r.isActive !== false,
                  remarks: r.remarks ?? undefined,
                });
                setDrawerOpen(true);
              }}
            />
          </Tooltip>
          <Tooltip title="Delete">
            <Button
              size="small"
              danger
              icon={<Trash2 size={13} />}
              loading={deleteMutation.isPending && deleteMutation.variables === r.id}
              onClick={() =>
                modal.confirm({
                  title: 'Delete this grade limit?',
                  content: 'This cannot be undone. The parent version must still be DRAFT.',
                  okText: 'Delete',
                  okButtonProps: { danger: true },
                  cancelText: 'Keep',
                  onOk: () => deleteMutation.mutate(r.id),
                })
              }
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, paddingBottom: 32 }}>
      <PageHeader
        eyebrow=""
        title="Grade Limits"
        description="Grade-specific travel entitlement limits on a policy version — one limit per grade and expense type. Adding a limit requires the version to still be DRAFT."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => limitsQuery.refetch()}
                loading={limitsQuery.isFetching}
                disabled={policyVersionId == null}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button
              type="primary"
              icon={<Plus size={14} />}
              disabled={policyVersionId == null}
              onClick={() => {
                setEditingLimit(null);
                form.resetFields();
                form.setFieldsValue({ isActive: true } as Partial<GradeLimitFormValues>);
                setDrawerOpen(true);
              }}
            >
              New
            </Button>
          </Space>
        }
      />

      <Section title="Policy version">
        <Select
          showSearch
          allowClear
          placeholder="Select a policy version to view its grade limits"
          style={{ width: '100%', maxWidth: 480 }}
          loading={policiesQuery.isLoading}
          value={policyVersionId}
          onChange={setPolicyVersionId}
          options={policyOptions}
          optionFilterProp="label"
        />
      </Section>

      <Section
        title="Grade limits"
        subtitle={policyVersionId != null ? `${rows.length.toLocaleString('en-PK')} limits` : undefined}
      >
        {policyVersionId == null ? (
          <EmptyState
            icon={Sliders}
            title="Pick a policy version"
            description="Select a policy version above to see its grade-specific entitlement limits."
          />
        ) : (
          <RequestState
            loading={limitsQuery.isLoading}
            variant="table"
            error={limitsQuery.isError ? limitsQuery.error : undefined}
            errorTitle="Couldn't load grade limits"
            onRetry={() => limitsQuery.refetch()}
            empty={!limitsQuery.isLoading && rows.length === 0}
            emptyFallback={
              <EmptyState
                icon={Sliders}
                title="No grade limits yet"
                description="Add a limit to define this version's per-grade entitlement caps."
              />
            }
          >
            <Table<GradeLimitResponse>
              rowKey="id"
              columns={columns}
              dataSource={rows}
              loading={limitsQuery.isFetching}
              pagination={false}
              // Scroll wide columns inside the table, not the page.
              scroll={{ x: 'max-content' }}
            />
          </RequestState>
        )}
      </Section>

      <DrawerForm<GradeLimitFormValues>
        title={editingLimit ? 'Edit grade limit' : 'New grade limit'}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setEditingLimit(null);
        }}
        form={form}
        submitting={editingLimit ? updateMutation.isPending : createMutation.isPending}
        onFinish={(values) => {
          // Both create and update address the grade by code, not id.
          const gradeCode =
            gradesQuery.data?.find((g) => g.id === values.gradeId)?.code ?? editingLimit?.gradeCode ?? '';
          if (editingLimit) {
            updateMutation.mutate({ id: editingLimit.id, gradeCode, values });
            return;
          }
          createMutation.mutate({
            gradeCode,
            expenseTypeCode: values.expenseTypeCode,
            limitType: values.limitType,
            limitAmount: values.limitAmount,
            currencyCode: values.currencyCode,
            isActive: values.isActive,
            remarks: values.remarks,
          });
        }}
      >
        <Form.Item
          name="gradeId"
          label="Grade"
          rules={[{ required: true, message: 'Select a grade' }]}
        >
          <Select
            showSearch
            placeholder="Select grade"
            options={gradeOptions}
            optionFilterProp="label"
            loading={gradesQuery.isLoading}
          />
        </Form.Item>
        <Form.Item
          name="expenseTypeCode"
          label="Expense type code"
          rules={[{ required: true, message: 'Enter an expense type code' }]}
        >
          <Input placeholder="e.g. LODGING, MEALS, FUEL" />
        </Form.Item>
        <Form.Item
          name="limitType"
          label="Limit type"
          rules={[{ required: true, message: 'Enter a limit type' }]}
        >
          <Input placeholder="e.g. PER_DAY" />
        </Form.Item>
        <Form.Item
          name="limitAmount"
          label="Limit amount"
          rules={[{ required: true, message: 'Enter a limit amount' }]}
        >
          <InputNumber style={{ width: '100%' }} min={0} />
        </Form.Item>
        <Form.Item
          name="currencyCode"
          label="Currency code"
          rules={[{ required: true, message: 'Enter a currency code' }]}
        >
          <Input placeholder="e.g. PKR" />
        </Form.Item>
        <Form.Item name="isActive" label="Active" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="remarks" label="Remarks">
          <Input.TextArea rows={3} placeholder="Optional notes" />
        </Form.Item>
      </DrawerForm>
    </div>
  );
}

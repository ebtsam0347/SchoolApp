// /admin/lookups/travel-policies — "Policy
// Administration" tab.
//
// Versioned travel policies and their grade-specific entitlement limits.
// Lifecycle: Draft → Active → Inactive → Retired; a version and its grade
// limits are editable only while Draft. Wired to GET/POST /travel/policies
// (services/employee, gateway path /travel/**). Reads require travel.read;
// creating, editing and deleting a version require travel.admin, and edit /
// delete only work while the version is DRAFT (delete takes its grade limits
// with it). Lifecycle transitions (activate/retire) aren't wired here yet.

import { useState } from 'react';
import { App as AntdApp, Button, Checkbox, DatePicker, Form, Input, Select, Space, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import dayjs from 'dayjs';
import { Pencil, Plus, RefreshCcw, ScrollText, Trash2 } from 'lucide-react';

import { DrawerForm, EmptyState, FilterBar, PageHeader, RequestState, Section } from '@/components';
import { getErrorMessage, handleFormError } from '@/lib';
import {
  travelPolicyApi,
  type CreatePolicyVersionRequest,
  type PolicyVersionResponse,
  type PolicyVersionStatus,
} from '@/travel';

const STATUS_VALUES: PolicyVersionStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED'];

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ACTIVE: 'success',
  INACTIVE: 'warning',
  RETIRED: 'error',
};

type PolicyFormValues = {
  policyCode: string;
  policyName: string;
  effectiveFrom: dayjs.Dayjs;
  /** Required when `hasEndDate` is ticked. */
  effectiveTo?: dayjs.Dayjs | null;
  /** "Set an end date" checkbox; unticked sends `effectiveTo: null`. Form-only, not sent. */
  hasEndDate?: boolean;
  description?: string;
};

export const Route = createFileRoute('/_app/admin/lookups/travel-policies')({
  component: TravelPolicyAdministrationTab,
});

function TravelPolicyAdministrationTab() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  // Version being edited; null while creating a new one.
  const [editing, setEditing] = useState<PolicyVersionResponse | null>(null);
  const [policyCode, setPolicyCode] = useState('');
  const [status, setStatus] = useState<PolicyVersionStatus | undefined>();
  const [form] = Form.useForm<PolicyFormValues>();
  const hasEndDate = Form.useWatch('hasEndDate', form) ?? false;
  const { message, modal } = AntdApp.useApp();
  const qc = useQueryClient();

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditing(null);
    form.resetFields();
  };

  const listQuery = useQuery({
    queryKey: ['travel-policies', { policyCode, status }],
    queryFn: ({ signal }) =>
      travelPolicyApi.search({ policyCode: policyCode || undefined, status }, signal),
  });

  const createMutation = useMutation({
    mutationFn: (body: CreatePolicyVersionRequest) => travelPolicyApi.create(body),
    onSuccess: () => {
      message.success('Policy version created');
      qc.invalidateQueries({ queryKey: ['travel-policies'] });
      closeDrawer();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: CreatePolicyVersionRequest }) => travelPolicyApi.update(id, body),
    onSuccess: () => {
      message.success('Policy version updated');
      qc.invalidateQueries({ queryKey: ['travel-policies'] });
      closeDrawer();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => travelPolicyApi.remove(id),
    onSuccess: () => {
      message.success('Policy version deleted');
      qc.invalidateQueries({ queryKey: ['travel-policies'] });
      // Its grade limits went with it.
      qc.invalidateQueries({ queryKey: ['travel-policy-grade-limits'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const rows = listQuery.data?.content ?? [];

  const columns: ColumnsType<PolicyVersionResponse> = [
    { title: 'Code', dataIndex: 'policyCode', key: 'policyCode', width: 140 },
    { title: 'Name', dataIndex: 'policyName', key: 'policyName' },
    { title: 'Version', dataIndex: 'versionNo', key: 'versionNo', width: 90 },
    {
      title: 'Effective from',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      width: 130,
      render: (v?: string | null) => (v ? dayjs(v).format('DD MMM YYYY') : '—'),
    },
    {
      title: 'Effective to',
      dataIndex: 'effectiveTo',
      key: 'effectiveTo',
      width: 130,
      // No end date = the version stays in force until further notice.
      render: (v?: string | null) => (v ? dayjs(v).format('DD MMM YYYY') : <Tag>Until further notice</Tag>),
    },
    { title: 'Description', dataIndex: 'description', key: 'description', ellipsis: true },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s?: string | null) => (s ? <Tag color={STATUS_COLOR[s] ?? 'default'}>{s}</Tag> : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 110,
      // Only DRAFT versions are editable; later states are frozen so travel
      // requests keep resolving against the rules that applied to them.
      render: (_, r) => {
        const isDraft = r.status === 'DRAFT';
        const locked = `Only DRAFT versions can be changed — this one is ${r.status ?? 'not DRAFT'}`;
        return (
          <Space size={4}>
            <Tooltip title={isDraft ? 'Edit' : locked}>
              <Button
                size="small"
                icon={<Pencil size={13} />}
                disabled={!isDraft}
                aria-label="Edit policy version"
                onClick={() => {
                  setEditing(r);
                  form.setFieldsValue({
                    policyCode: r.policyCode,
                    policyName: r.policyName ?? '',
                    effectiveFrom: r.effectiveFrom ? dayjs(r.effectiveFrom) : undefined,
                    effectiveTo: r.effectiveTo ? dayjs(r.effectiveTo) : null,
                    hasEndDate: !!r.effectiveTo,
                    description: r.description ?? undefined,
                  });
                  setDrawerOpen(true);
                }}
              />
            </Tooltip>
            <Tooltip title={isDraft ? 'Delete' : locked}>
              <Button
                size="small"
                danger
                icon={<Trash2 size={13} />}
                disabled={!isDraft}
                aria-label="Delete policy version"
                loading={deleteMutation.isPending && deleteMutation.variables === r.id}
                onClick={() =>
                  modal.confirm({
                    title: `Delete ${r.policyCode} v${r.versionNo ?? ''}?`,
                    content: 'Its grade limits will be deleted too. This cannot be undone.',
                    okText: 'Delete',
                    okButtonProps: { danger: true },
                    cancelText: 'Keep',
                    onOk: () => deleteMutation.mutateAsync(r.id),
                  })
                }
              />
            </Tooltip>
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, paddingBottom: 32 }}>
      <PageHeader
        eyebrow=""
        title="Policy Administration"
        description="Versioned travel policies and their grade-specific entitlement limits. Lifecycle: Draft → Active → Inactive → Retired; a version and its grade limits are editable only while Draft."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button
              type="primary"
              icon={<Plus size={14} />}
              onClick={() => {
                setEditing(null);
                form.resetFields();
                setDrawerOpen(true);
              }}
            >
              New
            </Button>
          </Space>
        }
      />

      <Section
        title="Policy versions"
        subtitle={`${rows.length.toLocaleString('en-PK')} shown`}
        actions={
          <Select<PolicyVersionStatus | undefined>
            allowClear
            placeholder="Status"
            style={{ width: 'clamp(140px, 45vw, 180px)' }}
            value={status}
            onChange={setStatus}
            options={STATUS_VALUES.map((s) => ({ value: s, label: s }))}
          />
        }
      >
        <FilterBar
          search={{
            value: policyCode,
            onChange: setPolicyCode,
            placeholder: 'Search policy code…',
          }}
          style={{ marginBottom: 12 }}
        />

        <RequestState
          loading={listQuery.isLoading}
          variant="table"
          error={listQuery.isError ? listQuery.error : undefined}
          errorTitle="Couldn't load travel policies"
          onRetry={() => listQuery.refetch()}
          empty={!listQuery.isLoading && rows.length === 0}
          filterActive={Boolean(policyCode) || Boolean(status)}
          onClearFilters={() => {
            setPolicyCode('');
            setStatus(undefined);
          }}
          emptyFallback={
            <EmptyState
              icon={ScrollText}
              title="No travel policies yet"
              description="Create a policy version to define grade-specific travel entitlement limits."
            />
          }
        >
          <Table<PolicyVersionResponse>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={listQuery.isFetching}
            pagination={false}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
          />
        </RequestState>
      </Section>

      <DrawerForm<PolicyFormValues>
        title={editing ? `Edit ${editing.policyCode} v${editing.versionNo ?? ''}` : 'New policy version'}
        open={drawerOpen}
        onClose={closeDrawer}
        form={form}
        submitting={editing ? updateMutation.isPending : createMutation.isPending}
        onFinish={(values) => {
          const body: CreatePolicyVersionRequest = {
            policyCode: editing?.policyCode ?? values.policyCode,
            policyName: values.policyName,
            effectiveFrom: values.effectiveFrom.format('YYYY-MM-DD'),
            effectiveTo: values.hasEndDate && values.effectiveTo ? values.effectiveTo.format('YYYY-MM-DD') : null,
            description: values.description,
          };
          if (editing) updateMutation.mutate({ id: editing.id, body });
          else createMutation.mutate(body);
        }}
      >
        <Form.Item
          name="policyCode"
          label="Policy code"
          rules={[{ required: true, message: 'Enter a policy code' }]}
          extra={editing ? 'The policy code cannot be changed — it ties the versions together.' : undefined}
        >
          <Input placeholder="e.g. TRAVEL-STD" disabled={!!editing} />
        </Form.Item>
        <Form.Item
          name="policyName"
          label="Policy name"
          rules={[{ required: true, message: 'Enter a policy name' }]}
        >
          <Input placeholder="e.g. Standard Travel Policy" />
        </Form.Item>
        <Form.Item
          name="effectiveFrom"
          label="Effective from"
          rules={[{ required: true, message: 'Pick an effective-from date' }]}
        >
          <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
        </Form.Item>
        <Form.Item
          name="hasEndDate"
          valuePropName="checked"
          style={{ marginBottom: hasEndDate ? 8 : 4 }}
        >
          <Checkbox
            onChange={(e) => {
              // Unticking drops any picked date — the version runs until further notice.
              if (!e.target.checked) form.setFieldValue('effectiveTo', null);
            }}
          >
            Set an end date
          </Checkbox>
        </Form.Item>
        {hasEndDate ? (
          <Form.Item
            name="effectiveTo"
            label="Effective to"
            required
            dependencies={['effectiveFrom']}
            rules={[
              ({ getFieldValue }) => ({
                validator: (_, value?: dayjs.Dayjs | null) => {
                  if (!value) return Promise.reject(new Error('Pick an end date, or untick "Set an end date"'));
                  const from = getFieldValue('effectiveFrom') as dayjs.Dayjs | undefined;
                  if (from && value.isBefore(from, 'day')) {
                    return Promise.reject(new Error('Effective to cannot be before effective from'));
                  }
                  return Promise.resolve();
                },
              }),
            ]}
          >
            <DatePicker
              style={{ width: '100%' }}
              format="DD MMM YYYY"
              placeholder="Select end date"
              disabledDate={(d) => {
                const from = form.getFieldValue('effectiveFrom') as dayjs.Dayjs | undefined;
                return !!from && d.isBefore(from, 'day');
              }}
            />
          </Form.Item>
        ) : (
          <div style={{ marginBottom: 24, fontSize: 12, color: 'var(--ant-color-text-secondary, #8c8c8c)' }}>
            No end date — this version stays in force until further notice.
          </div>
        )}
        <Form.Item name="description" label="Description">
          <Input.TextArea rows={3} placeholder="Optional notes about this version" />
        </Form.Item>
      </DrawerForm>
    </div>
  );
}

// /admin/notifications/templates — list, create, edit, activate/deactivate.
//
// Templates are Mustache-style; the placeholders depend on the type's event
// payload (e.g. `{{actorName}}`, `{{stageNo}}`). Backend renders at dispatch
// time, so changing a template doesn't backfill existing rows. We display
// the raw template strings as-is — preview-with-fixture is a nice-to-have
// but isn't urgent.

import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Skeleton,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  theme as antdTheme,
  type TableProps,
} from 'antd';
import { Pencil, Plus, RefreshCcw } from 'lucide-react';
import {
  adminNotificationsApi,
  ALL_CATEGORIES,
  categoryLabel,
  type CreateTemplateRequest,
  type NotificationCategory,
  type TemplateDto,
  type UpdateTemplateRequest,
} from '@/notifications';
import { PageHeader } from '@/components';
import { applyApiErrorToForm, getErrorMessage } from '@/lib';
import { NotificationsTabs } from './index';

const { Text } = Typography;
const { useToken } = antdTheme;
const { TextArea } = Input;

export const Route = createFileRoute('/_app/admin/notifications/templates')({
  component: TemplatesPage,
});

function TemplatesPage() {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<TemplateDto | null>(null);

  const listQuery = useQuery({
    queryKey: ['admin', 'notifications', 'templates'],
    queryFn: ({ signal }) => adminNotificationsApi.listTemplates(signal),
  });

  const setActive = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      adminNotificationsApi.updateTemplate(id, { active }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin', 'notifications', 'templates'] });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const columns: TableProps<TemplateDto>['columns'] = [
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      render: (v: string) => (
        <code style={{ fontSize: 12, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
          {v}
        </code>
      ),
      sorter: (a, b) => a.type.localeCompare(b.type),
      defaultSortOrder: 'ascend',
    },
    {
      title: 'Title template',
      dataIndex: 'titleTpl',
      key: 'titleTpl',
      render: (v: string) => <span style={{ fontSize: 13 }}>{v}</span>,
    },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      width: 130,
      render: (v: NotificationCategory | null) =>
        v ? <Tag style={{ margin: 0 }}>{categoryLabel(v)}</Tag> : <Text type="secondary">—</Text>,
      filters: ALL_CATEGORIES.map((c) => ({ text: categoryLabel(c), value: c })),
      onFilter: (value, record) => record.category === value,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (v: boolean, row) => (
        <Switch
          checked={v}
          onChange={(checked) => setActive.mutate({ id: row.id, active: checked })}
          loading={setActive.isPending && setActive.variables?.id === row.id}
          size="small"
        />
      ),
      filters: [
        { text: 'Active', value: true },
        { text: 'Inactive', value: false },
      ],
      onFilter: (value, record) => record.active === value,
    },
    {
      title: '',
      key: 'actions',
      width: 60,
      render: (_, row) => (
        <Button
          type="text"
          size="small"
          icon={<Pencil size={14} strokeWidth={1.75} />}
          onClick={() => setEditing(row)}
          aria-label="Edit template"
        />
      ),
    },
  ];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Notifications"
        title="Rendering templates"
        description="Mustache-style title / body / link templates per notification type. Inactive templates are skipped at dispatch time — events still flow through, they just don't produce a row."
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
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
              icon={<Plus size={14} strokeWidth={1.75} />}
              onClick={() => setCreateOpen(true)}
            >
              New template
            </Button>
          </div>
        }
      />

      <NotificationsTabs current="templates" />

      {listQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load templates"
          description={getErrorMessage(listQuery.error)}
        />
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          {listQuery.isLoading ? (
            <div style={{ padding: 16 }}>
              <Skeleton active title={false} paragraph={{ rows: 6 }} />
            </div>
          ) : (
            <Table<TemplateDto>
              dataSource={listQuery.data ?? []}
              columns={columns}
              rowKey="id"
              // Scroll wide columns inside the table, not the page.
              scroll={{ x: 'max-content' }}
              pagination={{
                defaultPageSize: 25,
                pageSizeOptions: [10, 25, 50, 100],
                showSizeChanger: true,
                showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
                hideOnSinglePage: true,
              }}
              size="middle"
              expandable={{
                expandedRowRender: (row) => <TemplateDetail template={row} />,
                rowExpandable: (row) => Boolean(row.bodyTpl || row.linkTpl),
              }}
            />
          )}
        </Card>
      )}

      <CreateTemplateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false);
          void qc.invalidateQueries({ queryKey: ['admin', 'notifications', 'templates'] });
        }}
      />
      <EditTemplateModal
        template={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          void qc.invalidateQueries({ queryKey: ['admin', 'notifications', 'templates'] });
        }}
      />
    </div>
  );
}

function TemplateDetail({ template }: { template: TemplateDto }) {
  const { token } = useToken();
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: '8px 16px' }}>
      {template.bodyTpl && (
        <>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Body
          </Text>
          <pre
            style={{
              margin: 0,
              fontSize: 12,
              color: token.colorText,
              background: token.colorFillQuaternary,
              padding: 10,
              borderRadius: 6,
              whiteSpace: 'pre-wrap',
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            }}
          >
            {template.bodyTpl}
          </pre>
        </>
      )}
      {template.linkTpl && (
        <>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Link
          </Text>
          <code
            style={{
              fontSize: 12,
              color: token.colorText,
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            }}
          >
            {template.linkTpl}
          </code>
        </>
      )}
    </div>
  );
}

function CreateTemplateModal({
  open,
  onClose,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<CreateTemplateRequest>();
  const { message } = App.useApp();
  const create = useMutation({
    mutationFn: (body: CreateTemplateRequest) => adminNotificationsApi.createTemplate(body),
    onSuccess: () => {
      message.success('Template created.');
      form.resetFields();
      onSaved();
    },
    onError: (e) => applyApiErrorToForm(form, e),
  });

  return (
    <Modal
      title="New notification template"
      open={open}
      onCancel={() => {
        form.resetFields();
        onClose();
      }}
      onOk={() => form.submit()}
      okText="Create"
      okButtonProps={{ loading: create.isPending }}
      width={620}
      destroyOnHidden
      mask={{ closable: false }}
    >
      <Form<CreateTemplateRequest>
        form={form}
        layout="vertical"
        onFinish={(v) =>
          create.mutate({
            type: v.type.trim().toUpperCase(),
            titleTpl: v.titleTpl,
            bodyTpl: v.bodyTpl?.trim() || undefined,
            linkTpl: v.linkTpl?.trim() || undefined,
            category: v.category,
          })
        }
        requiredMark="optional"
      >
        <Form.Item
          name="type"
          label="Type"
          rules={[
            { required: true, message: 'Type is required.' },
            { max: 64, message: 'Type is too long.' },
          ]}
          extra="Canonical key (e.g. LEAVE_APPROVED). Uppercase, no spaces."
        >
          <Input placeholder="LEAVE_APPROVED" />
        </Form.Item>
        <Form.Item
          name="titleTpl"
          label="Title template"
          rules={[{ required: true, message: 'Title template is required.' }]}
          extra="Use {{placeholders}} for dynamic fields."
        >
          <Input placeholder="Your leave was approved" />
        </Form.Item>
        <Form.Item name="bodyTpl" label="Body template">
          <TextArea autoSize={{ minRows: 2, maxRows: 6 }} placeholder="{{actorName}} approved your leave at stage {{stageNo}}." />
        </Form.Item>
        <Form.Item
          name="linkTpl"
          label="Link template"
          rules={[{ max: 512, message: 'Link is too long (max 512).' }]}
        >
          <Input placeholder="/leaves/{{requestId}}" />
        </Form.Item>
        <Form.Item name="category" label="Category">
          <Select
            allowClear
            placeholder="—"
            options={ALL_CATEGORIES.map((c) => ({ label: categoryLabel(c), value: c }))}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function EditTemplateModal({
  template,
  onClose,
  onSaved,
}: {
  template: TemplateDto | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<UpdateTemplateRequest>();
  const { message } = App.useApp();
  const update = useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateTemplateRequest }) =>
      adminNotificationsApi.updateTemplate(id, body),
    onSuccess: () => {
      message.success('Template updated.');
      onSaved();
    },
    onError: (e) => applyApiErrorToForm(form, e),
  });

  return (
    <Modal
      title={template ? `Edit ${template.type}` : 'Edit template'}
      open={Boolean(template)}
      onCancel={() => {
        form.resetFields();
        onClose();
      }}
      onOk={() => form.submit()}
      okText="Save"
      okButtonProps={{ loading: update.isPending }}
      width={620}
      destroyOnHidden
      mask={{ closable: false }}
    >
      {template && (
        <Form<UpdateTemplateRequest>
          form={form}
          layout="vertical"
          initialValues={{
            titleTpl: template.titleTpl,
            bodyTpl: template.bodyTpl ?? undefined,
            linkTpl: template.linkTpl ?? undefined,
            category: template.category ?? undefined,
            active: template.active,
          }}
          onFinish={(v) =>
            update.mutate({
              id: template.id,
              body: {
                titleTpl: v.titleTpl,
                bodyTpl: v.bodyTpl,
                linkTpl: v.linkTpl?.trim() || undefined,
                category: v.category,
                active: v.active,
              },
            })
          }
        >
          <Form.Item
            name="titleTpl"
            label="Title template"
            rules={[{ required: true, message: 'Title template is required.' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="bodyTpl" label="Body template">
            <TextArea autoSize={{ minRows: 2, maxRows: 6 }} />
          </Form.Item>
          <Form.Item
            name="linkTpl"
            label="Link template"
            rules={[{ max: 512, message: 'Link is too long (max 512).' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="category" label="Category">
            <Select
              allowClear
              placeholder="—"
              options={ALL_CATEGORIES.map((c) => ({ label: categoryLabel(c), value: c }))}
            />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}

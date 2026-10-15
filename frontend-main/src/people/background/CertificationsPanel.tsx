// Certifications panel — list with expiry-tone visual cues + add modal.
// Used by /people/$id/background; surfaced as one of four sub-sections.

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Skeleton,
  Space,
  Tooltip,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  FileText,
  Sparkles,
  Trash2,
} from 'lucide-react';
import dayjs from 'dayjs';

import { EmptyState, ExpiryBadge } from '@/components';
import {
  applyApiErrorToForm,
  getErrorMessage,
  isApiError,
} from '@/lib';
import {
  certificationsApi,
  type AddCertificationRequest,
  type EmployeeCertificationDto,
} from '@/people';

const { Text } = Typography;
const { useToken } = antdTheme;

export type CertificationsPanelProps = {
  employeeId: number;
  addOpen?: boolean;
  onAddOpenChange?: (open: boolean) => void;
};

export function CertificationsPanel({
  employeeId,
  addOpen = false,
  onAddOpenChange,
}: CertificationsPanelProps) {
  const listQuery = useQuery({
    queryKey: ['people', 'certifications', employeeId],
    queryFn: ({ signal }) => certificationsApi.perEmployee(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  if (listQuery.isLoading) return <Skeleton active paragraph={{ rows: 4 }} />;
  if (listQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        title="Couldn't load certifications"
        description={getErrorMessage(listQuery.error)}
      />
    );
  }

  const certs = [...(listQuery.data ?? [])].sort(
    (a, b) => (a.expiryDate ?? 'z').localeCompare(b.expiryDate ?? 'z'),
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {certs.length === 0 ? (
        <EmptyState
          icon={Sparkles}
          title="No certifications recorded"
          description="Add ISO auditor / forklift / first-aid / FIFA ball-quality / any issuing-body cert."
        />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
            gap: 12,
          }}
        >
          {certs.map((c) => (
            <CertificationRow
              key={c.id}
              employeeId={employeeId}
              certification={c}
            />
          ))}
        </div>
      )}

      {addOpen ? (
        <AddCertificationModal
          employeeId={employeeId}
          onClose={() => onAddOpenChange?.(false)}
        />
      ) : null}
    </div>
  );
}

function CertificationRow({
  employeeId,
  certification,
}: {
  employeeId: number;
  certification: EmployeeCertificationDto;
}) {
  const { token } = useToken();
  const { message } = App.useApp();
  const qc = useQueryClient();

  const deleteMutation = useMutation({
    mutationFn: () => certificationsApi.remove(employeeId, certification.id!),
    onSuccess: () => {
      message.success('Certification removed.');
      void qc.invalidateQueries({ queryKey: ['people', 'certifications', employeeId] });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  return (
    <Card size="small" styles={{ body: { padding: 14 } }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: token.colorTextHeading }}>
            {certification.name ?? '—'}
          </div>
          {certification.issuingBody ? (
            <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
              {certification.issuingBody}
            </Text>
          ) : null}
          <div style={{ marginTop: 6, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            {certification.expiryDate ? (
              <ExpiryBadge date={certification.expiryDate} mode="expiry" />
            ) : (
              <Text type="secondary" style={{ fontSize: 11 }}>No expiry</Text>
            )}
            {certification.credentialId ? (
              <Text type="secondary" style={{ fontSize: 11 }}>ID: {certification.credentialId}</Text>
            ) : null}
          </div>
        </div>
        <Space>
          {certification.documentUrl ? (
            <Tooltip title="View document">
              <Button
                type="text"
                size="small"
                href={certification.documentUrl}
                target="_blank"
                rel="noreferrer"
                icon={<FileText size={14} />}
              />
            </Tooltip>
          ) : null}
          <Popconfirm title="Remove certification?" okType="danger" onConfirm={() => deleteMutation.mutate()}>
            <Button
              type="text"
              size="small"
              danger
              loading={deleteMutation.isPending}
              icon={<Trash2 size={14} />}
            />
          </Popconfirm>
        </Space>
      </div>
    </Card>
  );
}

function AddCertificationModal({
  employeeId,
  onClose,
}: {
  employeeId: number;
  onClose: () => void;
}) {
  type CertForm = {
    name: string;
    issuingBody?: string;
    issueDate?: dayjs.Dayjs;
    expiryDate?: dayjs.Dayjs;
    credentialId?: string;
  };

  const [form] = Form.useForm<CertForm>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [topLevel, setTopLevel] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: AddCertificationRequest) =>
      certificationsApi.add(employeeId, body),
    onSuccess: () => {
      message.success('Certification added.');
      void qc.invalidateQueries({ queryKey: ['people', 'certifications', employeeId] });
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
      title="Add certification"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Add"
      confirmLoading={mutation.isPending}
      destroyOnHidden
      width={520}
    >
      {topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => {
          setTopLevel(null);
          mutation.mutate({
            name: values.name.trim(),
            issuingBody: values.issuingBody?.trim() || undefined,
            issueDate: values.issueDate?.format('YYYY-MM-DD'),
            expiryDate: values.expiryDate?.format('YYYY-MM-DD'),
            credentialId: values.credentialId?.trim() || undefined,
          });
        }}
      >
        <Form.Item
          name="name"
          label="Certification"
          rules={[{ required: true, message: 'Required' }]}
        >
          <Input placeholder="ISO 9001 Internal Auditor" />
        </Form.Item>
        <Form.Item name="issuingBody" label="Issuing body">
          <Input placeholder="DNV / TÜV / ASNT / FIFA…" />
        </Form.Item>
        <Space.Compact block>
          <Form.Item name="issueDate" label="Issued" style={{ flex: 1, marginRight: 8 }}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="expiryDate"
            label="Expires"
            style={{ flex: 1 }}
            extra="Leave blank if the cert doesn't expire."
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Space.Compact>
        <Form.Item name="credentialId" label="Credential ID / verification">
          <Input placeholder="Cert # or verification URL" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

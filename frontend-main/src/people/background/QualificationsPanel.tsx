// Academic record panel — degree timeline + add modal.
// Used by /people/$id/background; surfaced as one of four sub-sections.

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Skeleton,
  Space,
  theme as antdTheme,
  Tooltip,
  Typography,
} from 'antd';

const { useToken } = antdTheme;
import {
  ExternalLink,
  FileText,
  GraduationCap,
  Trash2,
} from 'lucide-react';

import { RequestState } from '@/components';
import {
  applyApiErrorToForm,
  getErrorMessage,
  isApiError,
} from '@/lib';
import {
  qualificationsApi,
  type AddEmployeeQualificationRequest,
  type EmployeeQualificationDto,
} from '@/people';

const { Text } = Typography;

export type QualificationsPanelProps = {
  employeeId: number;
  /** Add-modal open state. Lifted to the parent so the Add button can
   *  live in the surrounding card header (and stay visible on scroll). */
  addOpen?: boolean;
  onAddOpenChange?: (open: boolean) => void;
};

export function QualificationsPanel({
  employeeId,
  addOpen = false,
  onAddOpenChange,
}: QualificationsPanelProps) {
  const listQuery = useQuery({
    queryKey: ['people', 'qualifications', employeeId],
    queryFn: ({ signal }) => qualificationsApi.perEmployee(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  const quals = [...(listQuery.data ?? [])].sort(
    (a, b) => (b.yearOfCompletion ?? 0) - (a.yearOfCompletion ?? 0),
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <RequestState
        loading={listQuery.isLoading}
        error={listQuery.isError ? listQuery.error : undefined}
        empty={quals.length === 0}
        loadingFallback={<Skeleton active paragraph={{ rows: 4 }} />}
        errorTitle="Couldn't load qualifications"
        emptyIcon={GraduationCap}
        emptyTitle="No qualifications recorded"
        emptyDescription="Add a degree, diploma, or matric record to start the academic timeline."
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {quals.map((q) => (
            <QualificationCard key={q.id} employeeId={employeeId} qualification={q} />
          ))}
        </div>
      </RequestState>

      {addOpen ? (
        <AddQualificationModal
          employeeId={employeeId}
          onClose={() => onAddOpenChange?.(false)}
        />
      ) : null}
    </div>
  );
}

function QualificationCard({
  employeeId,
  qualification: q,
}: {
  employeeId: number;
  qualification: EmployeeQualificationDto;
}) {
  const { token } = useToken();
  const subtitle = [q.institution, q.boardOrUniversity].filter(Boolean).join(' · ');
  return (
    <Card size="small" styles={{ body: { padding: 14 } }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: token.colorTextHeading }}>
            {q.qualificationName ?? `#${q.qualificationId}`}
            {q.grade ? <Text type="secondary"> · {q.grade}</Text> : null}
          </div>
          {subtitle ? (
            <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
              {subtitle}
            </Text>
          ) : null}
          {q.documentUrl ? (
            <div style={{ marginTop: 6 }}>
              <a
                href={q.documentUrl}
                target="_blank"
                rel="noreferrer"
                style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12 }}
              >
                <FileText size={12} /> Transcript <ExternalLink size={10} />
              </a>
            </div>
          ) : null}
        </div>
        <Space>
          {q.yearOfCompletion ? (
            <Text type="secondary" style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
              {q.yearOfCompletion}
            </Text>
          ) : null}
          <DeleteQualificationButton employeeId={employeeId} qualification={q} />
        </Space>
      </div>
    </Card>
  );
}

function DeleteQualificationButton({
  employeeId,
  qualification,
}: {
  employeeId: number;
  qualification: EmployeeQualificationDto;
}) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const mutation = useMutation({
    mutationFn: () => qualificationsApi.remove(employeeId, qualification.id!),
    onSuccess: () => {
      message.success('Qualification removed.');
      void qc.invalidateQueries({ queryKey: ['people', 'qualifications', employeeId] });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });
  return (
    <Popconfirm title="Remove qualification?" okType="danger" onConfirm={() => mutation.mutate()}>
      <Tooltip title="Remove">
        <Button
          type="text"
          size="small"
          danger
          loading={mutation.isPending}
          icon={<Trash2 size={14} />}
        />
      </Tooltip>
    </Popconfirm>
  );
}

function AddQualificationModal({
  employeeId,
  onClose,
}: {
  employeeId: number;
  onClose: () => void;
}) {
  type QualForm = {
    qualificationId: number;
    institution: string;
    boardOrUniversity?: string;
    yearOfCompletion?: number;
    grade?: string;
  };

  const [form] = Form.useForm<QualForm>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [topLevel, setTopLevel] = useState<string | null>(null);

  const catalogQuery = useQuery({
    queryKey: ['catalog', 'qualifications'],
    queryFn: ({ signal }) => qualificationsApi.catalog(signal),
    staleTime: 24 * 60 * 60 * 1000,
  });

  const mutation = useMutation({
    mutationFn: (body: AddEmployeeQualificationRequest) =>
      qualificationsApi.add(employeeId, body),
    onSuccess: () => {
      message.success('Qualification added.');
      void qc.invalidateQueries({ queryKey: ['people', 'qualifications', employeeId] });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setTopLevel(getErrorMessage(e));
      }
    },
  });

  const options = (catalogQuery.data ?? [])
    .filter((q) => q.active !== false)
    .map((q) => ({
      value: q.id!,
      label: `${q.name}${q.level ? ` · ${q.level.toLowerCase()}` : ''}`,
    }));

  return (
    <Modal
      open
      title="Add qualification"
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
            qualificationId: values.qualificationId,
            institution: values.institution.trim(),
            boardOrUniversity: values.boardOrUniversity?.trim() || undefined,
            yearOfCompletion: values.yearOfCompletion,
            grade: values.grade?.trim() || undefined,
          });
        }}
      >
        <Form.Item
          name="qualificationId"
          label="Degree"
          rules={[{ required: true, message: 'Pick a degree from the catalog' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            loading={catalogQuery.isLoading}
            options={options}
            placeholder="e.g. BSc Computer Science, FSc Pre-Engineering"
          />
        </Form.Item>
        <Form.Item
          name="institution"
          label="Institution"
          rules={[{ required: true, message: 'Required' }]}
        >
          <Input placeholder="e.g. Govt. College Sialkot" />
        </Form.Item>
        <Form.Item name="boardOrUniversity" label="Board / University">
          <Input placeholder="e.g. BISE Lahore, University of Punjab" />
        </Form.Item>
        <Space.Compact block>
          <Form.Item name="yearOfCompletion" label="Year" style={{ flex: 1, marginRight: 8 }}>
            <InputNumber min={1950} max={new Date().getFullYear()} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="grade" label="Grade" style={{ flex: 1 }}>
            <Input placeholder='"A", "First Division", "3.6 CGPA"' />
          </Form.Item>
        </Space.Compact>
      </Form>
    </Modal>
  );
}

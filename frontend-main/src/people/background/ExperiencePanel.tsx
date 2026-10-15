// Pre-joining work history panel — timeline + add modal.
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
  theme as antdTheme,
  Tooltip,
  Typography,
} from 'antd';
import { Briefcase, Trash2 } from 'lucide-react';
import dayjs from 'dayjs';

import { DateRangeBadge, RequestState } from '@/components';

const { useToken } = antdTheme;
import {
  applyApiErrorToForm,
  getErrorMessage,
  isApiError,
} from '@/lib';
import {
  experienceApi,
  type AddPriorExperienceRequest,
  type EmployeePriorExperienceDto,
} from '@/people';

const { Text } = Typography;

export type ExperiencePanelProps = {
  employeeId: number;
  addOpen?: boolean;
  onAddOpenChange?: (open: boolean) => void;
};

export function ExperiencePanel({
  employeeId,
  addOpen = false,
  onAddOpenChange,
}: ExperiencePanelProps) {

  const listQuery = useQuery({
    queryKey: ['people', 'experience', employeeId],
    queryFn: ({ signal }) => experienceApi.perEmployee(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  const items = [...(listQuery.data ?? [])].sort(
    (a, b) => (b.startDate ?? '').localeCompare(a.startDate ?? ''),
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <RequestState
        loading={listQuery.isLoading}
        error={listQuery.isError ? listQuery.error : undefined}
        empty={items.length === 0}
        loadingFallback={<Skeleton active paragraph={{ rows: 4 }} />}
        errorTitle="Couldn't load experience"
        emptyIcon={Briefcase}
        emptyTitle="No prior experience recorded"
        emptyDescription="Add roles held before joining the school."
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {items.map((it) => (
            <ExperienceCard key={it.id} employeeId={employeeId} item={it} />
          ))}
        </div>
      </RequestState>

      {addOpen ? (
        <AddExperienceModal
          employeeId={employeeId}
          onClose={() => onAddOpenChange?.(false)}
        />
      ) : null}
    </div>
  );
}

function ExperienceCard({
  employeeId,
  item: it,
}: {
  employeeId: number;
  item: EmployeePriorExperienceDto;
}) {
  const { token } = useToken();
  return (
    <Card size="small" styles={{ body: { padding: 14 } }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: token.colorTextHeading }}>
            {it.designation ?? 'Role'}
            <Text type="secondary" style={{ fontWeight: 400 }}> at </Text>
            {it.employerName}
          </div>
          {it.responsibilities ? (
            <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 2 }}>
              {it.responsibilities}
            </Text>
          ) : null}
          {it.reasonForLeaving ? (
            <Text type="secondary" style={{ fontSize: 11, display: 'block', marginTop: 6 }}>
              Left: {it.reasonForLeaving}
            </Text>
          ) : null}
        </div>
        <Space>
          {it.startDate && it.endDate ? (
            <DateRangeBadge start={it.startDate} end={it.endDate} format="medium" />
          ) : null}
          <DeleteExperienceButton employeeId={employeeId} item={it} />
        </Space>
      </div>
    </Card>
  );
}

function DeleteExperienceButton({
  employeeId,
  item,
}: {
  employeeId: number;
  item: EmployeePriorExperienceDto;
}) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const mutation = useMutation({
    mutationFn: () => experienceApi.remove(employeeId, item.id!),
    onSuccess: () => {
      message.success('Experience removed.');
      void qc.invalidateQueries({ queryKey: ['people', 'experience', employeeId] });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });
  return (
    <Popconfirm title="Remove this entry?" okType="danger" onConfirm={() => mutation.mutate()}>
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

function AddExperienceModal({
  employeeId,
  onClose,
}: {
  employeeId: number;
  onClose: () => void;
}) {
  type ExpForm = {
    employerName: string;
    designation?: string;
    range: [dayjs.Dayjs, dayjs.Dayjs];
    responsibilities?: string;
    reasonForLeaving?: string;
  };

  const [form] = Form.useForm<ExpForm>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [topLevel, setTopLevel] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: AddPriorExperienceRequest) =>
      experienceApi.add(employeeId, body),
    onSuccess: () => {
      message.success('Experience added.');
      void qc.invalidateQueries({ queryKey: ['people', 'experience', employeeId] });
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
      title="Add prior experience"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Add"
      confirmLoading={mutation.isPending}
      destroyOnHidden
      width={560}
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
            employerName: values.employerName.trim(),
            designation: values.designation?.trim() || undefined,
            startDate: values.range[0].format('YYYY-MM-DD'),
            endDate: values.range[1].format('YYYY-MM-DD'),
            responsibilities: values.responsibilities?.trim() || undefined,
            reasonForLeaving: values.reasonForLeaving?.trim() || undefined,
          });
        }}
      >
        <Space.Compact block>
          <Form.Item
            name="employerName"
            label="Employer"
            rules={[{ required: true, message: 'Required' }]}
            style={{ flex: 1, marginRight: 8 }}
          >
            <Input placeholder="Previous employer name" />
          </Form.Item>
          <Form.Item name="designation" label="Designation" style={{ flex: 1 }}>
            <Input placeholder="Role at that employer" />
          </Form.Item>
        </Space.Compact>
        <Form.Item
          name="range"
          label="Tenure"
          rules={[{ required: true, message: 'Pick the start and end dates' }]}
        >
          <DatePicker.RangePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="responsibilities" label="Responsibilities">
          <Input.TextArea rows={3} placeholder="Top responsibilities or achievements." />
        </Form.Item>
        <Form.Item name="reasonForLeaving" label="Reason for leaving">
          <Input placeholder="Better opportunity / Company closed / Personal…" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

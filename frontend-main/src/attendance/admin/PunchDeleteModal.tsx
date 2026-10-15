// Operator delete-a-punch modal. Hard delete with a mandatory audit reason;
// the server recomputes the day. Re-uses the same Form-driven pattern as
// PunchAmendModal so error mapping behaves consistently.

import { useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Form,
  Input,
  Modal,
  Typography,
} from 'antd';

import { handleFormError } from '@/lib';
import {
  PUNCH_SOURCE_LABEL,
  attendanceApi,
  formatDateTime,
  type PunchDto,
} from '@/attendance';

const { Text } = Typography;

export type PunchDeleteModalProps = {
  punch: PunchDto | null;
  onClose: () => void;
};

type FormShape = {
  reason: string;
};

export function PunchDeleteModal({ punch, onClose }: PunchDeleteModalProps) {
  const [form] = Form.useForm<FormShape>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    if (!punch) form.resetFields();
  }, [punch, form]);

  const mutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { reason: string } }) =>
      attendanceApi.deletePunch(id, body),
    onSuccess: () => {
      message.success('Punch deleted.');
      void queryClient.invalidateQueries({ queryKey: ['admin', 'attendance'] });
      void queryClient.invalidateQueries({ queryKey: ['attendance'] });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <Modal
      title="Delete punch"
      open={punch !== null}
      onCancel={() => (mutation.isPending ? undefined : onClose())}
      okText="Delete"
      okButtonProps={{ danger: true, loading: mutation.isPending }}
      onOk={() => form.submit()}
      destroyOnHidden
      mask={{ closable: !mutation.isPending }}
    >
      {punch ? (
        <>
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            title="The punch is removed and the day is recomputed."
            description="The reason is the audit trail — please be specific."
          />
          <div style={{ marginBottom: 12 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {formatDateTime(punch.punchedAt)} ·{' '}
              {PUNCH_SOURCE_LABEL[punch.source]}
              {punch.deviceId ? ` · device ${punch.deviceId}` : ''}
            </Text>
          </div>
          <Form
            form={form}
            layout="vertical"
            onFinish={(v) =>
              mutation.mutate({ id: punch.id, body: { reason: v.reason } })
            }
          >
            <Form.Item
              label="Reason (audit trail)"
              name="reason"
              rules={[
                { required: true, message: 'Reason is mandatory' },
                { min: 4, message: 'Be specific — this is the audit trail' },
              ]}
            >
              <Input.TextArea
                autoSize={{ minRows: 3, maxRows: 5 }}
                placeholder="duplicate from device retry; verified with biometric log"
              />
            </Form.Item>
          </Form>
        </>
      ) : null}
    </Modal>
  );
}

// Operator amend-a-punch modal. Edits punchedAt + reason on a single
// PunchDto; the server recomputes the day (and the prior day if the new
// instant moves the work-date window).

import { useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  DatePicker,
  Form,
  Input,
  Modal,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';

import { handleFormError } from '@/lib';
import {
  PUNCH_SOURCE_LABEL,
  attendanceApi,
  formatDateTime,
  type PunchDto,
} from '@/attendance';
import { toLocalDateTime } from '@/lib/dates';

const { Text } = Typography;

export type PunchAmendModalProps = {
  punch: PunchDto | null;
  onClose: () => void;
};

type FormShape = {
  punchedAt: Dayjs;
  reason: string;
};

export function PunchAmendModal({ punch, onClose }: PunchAmendModalProps) {
  const [form] = Form.useForm<FormShape>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    if (punch) {
      form.setFieldsValue({
        punchedAt: dayjs(punch.punchedAt),
        reason: '',
      });
    } else {
      form.resetFields();
    }
  }, [punch, form]);

  const mutation = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { punchedAt: string; reason: string } }) =>
      attendanceApi.amendPunch(id, body),
    onSuccess: (updated) => {
      message.success(`Punch amended to ${formatDateTime(updated.punchedAt)}.`);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'attendance'] });
      void queryClient.invalidateQueries({ queryKey: ['attendance'] });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <Modal
      title="Amend punch"
      open={punch !== null}
      onCancel={() => (mutation.isPending ? undefined : onClose())}
      okText="Save amendment"
      okButtonProps={{ loading: mutation.isPending }}
      onOk={() => form.submit()}
      destroyOnHidden
      mask={{ closable: !mutation.isPending }}
    >
      {punch ? (
        <>
          <div style={{ marginBottom: 12 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Current: {formatDateTime(punch.punchedAt)} ·{' '}
              {PUNCH_SOURCE_LABEL[punch.source]}
              {punch.deviceId ? ` · device ${punch.deviceId}` : ''}
            </Text>
          </div>
          <Form
            form={form}
            layout="vertical"
            onFinish={(v) =>
              mutation.mutate({
                id: punch.id,
                body: {
                  punchedAt: toLocalDateTime(v.punchedAt),
                  reason: v.reason,
                },
              })
            }
          >
            <Form.Item
              label="New instant"
              name="punchedAt"
              rules={[{ required: true, message: 'Pick the corrected instant' }]}
            >
              {/* needConfirm={false} so each hour/minute cell-click pushes
                  straight into the form value. Default AntD UX gates commit
                  on the popup's OK button, which is a silent footgun: the
                  picker visibly shows the new time but the form still holds
                  the old dayjs if the user clicks Save without OK. */}
              <DatePicker
                showTime={{ format: 'HH:mm' }}
                format="DD MMM YYYY HH:mm"
                needConfirm={false}
                style={{ width: '100%' }}
                disabledDate={(d) => d && d.isAfter(dayjs(), 'minute')}
              />
            </Form.Item>
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
                placeholder="corrected per paper form #43, dial-pad lag"
              />
            </Form.Item>
          </Form>
        </>
      ) : null}
    </Modal>
  );
}

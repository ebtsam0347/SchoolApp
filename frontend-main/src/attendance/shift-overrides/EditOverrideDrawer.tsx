// Edit drawer — patch recomputes the union of old + new ranges in the
// same transaction. Identity (employeeId) is read-only; to move an
// override to a different employee, delete + recreate.

import { useEffect } from 'react';
import {
  App as AntdApp,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Space,
  Tag,
  TimePicker,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import {
  shiftOverridesApi,
  type ShiftOverrideDto,
  type UpdateShiftOverrideRequest,
} from '@/attendance';
import { DrawerForm } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';

type EditForm = {
  range: [Dayjs, Dayjs];
  startTime: Dayjs;
  endTime: Dayjs;
  breakStartTime: Dayjs;
  breakEndTime: Dayjs;
  graceMinutes: number;
  reason?: string;
};

export type EditOverrideDrawerProps = {
  override: ShiftOverrideDto | null;
  onClose: () => void;
};

export function EditOverrideDrawer({ override, onClose }: EditOverrideDrawerProps) {
  const [form] = Form.useForm<EditForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const open = !!override;

  useEffect(() => {
    if (override) {
      form.setFieldsValue({
        range: [dayjs(override.fromDate), dayjs(override.toDate)],
        startTime: dayjs(override.shiftStartTime, 'HH:mm'),
        endTime: dayjs(override.shiftEndTime, 'HH:mm'),
        breakStartTime: dayjs(override.shiftBreakStartTime, 'HH:mm'),
        breakEndTime: dayjs(override.shiftBreakEndTime, 'HH:mm'),
        graceMinutes: override.shiftGraceMinutes,
        reason: override.reason ?? undefined,
      });
    }
  }, [override, form]);

  const mutation = useMutation({
    mutationFn: (body: UpdateShiftOverrideRequest) =>
      shiftOverridesApi.update(override?.id ?? 0, body),
    onSuccess: () => {
      message.success(
        'Override updated; recompute ran on the union of old + new ranges.',
      );
      queryClient.invalidateQueries({ queryKey: ['shift-overrides'] });
      onClose();
    },
    onError: (err) => {
      if (isApiError(err)) {
        if (err.code === 'OVERRIDE_OVERLAP') {
          form.setFields([
            {
              name: 'range',
              errors: ['New range overlaps another override for this employee.'],
            },
          ]);
        } else {
          applyApiErrorToForm(form, err);
        }
      }
      message.error(getErrorMessage(err));
    },
  });

  if (!override) {
    return (
      <DrawerForm
        open={false}
        onClose={onClose}
        title="Edit override"
        size={460}
        hideSubmit
      >
        <></>
      </DrawerForm>
    );
  }

  return (
    <DrawerForm<EditForm>
      title={`Edit override #${override.id}`}
      open={open}
      onClose={onClose}
      size={460}
      form={form}
      submitting={mutation.isPending}
      submitText="Save changes"
      onFinish={(v) =>
        mutation.mutate({
          fromDate: v.range[0].format('YYYY-MM-DD'),
          toDate: v.range[1].format('YYYY-MM-DD'),
          shiftStartTime: v.startTime.format('HH:mm'),
          shiftEndTime: v.endTime.format('HH:mm'),
          shiftBreakStartTime: v.breakStartTime.format('HH:mm'),
          shiftBreakEndTime: v.breakEndTime.format('HH:mm'),
          shiftGraceMinutes: v.graceMinutes,
          reason: v.reason,
        })
      }
    >
      <Card
        size="small"
        styles={{ body: { padding: 12, marginBottom: 16 } }}
        style={{ background: 'transparent' }}
      >
        <Tag>Employee #{override.employeeId}</Tag>
      </Card>

      <Form.Item
        label="Range"
        name="range"
        rules={[{ required: true, message: 'Pick the range' }]}
      >
        <DatePicker.RangePicker style={{ width: '100%' }} format="DD MMM YYYY" />
      </Form.Item>
      <Space.Compact style={{ width: '100%' }}>
        <Form.Item
          label="Start"
          name="startTime"
          rules={[{ required: true }]}
          style={{ flex: 1 }}
        >
          <TimePicker format="HH:mm" minuteStep={5} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          label="End"
          name="endTime"
          rules={[{ required: true }]}
          style={{ flex: 1 }}
        >
          <TimePicker format="HH:mm" minuteStep={5} style={{ width: '100%' }} />
        </Form.Item>
      </Space.Compact>
      <Space.Compact style={{ width: '100%' }}>
        <Form.Item
          label="Break start"
          name="breakStartTime"
          rules={[{ required: true }]}
          style={{ flex: 1 }}
        >
          <TimePicker format="HH:mm" minuteStep={5} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          label="Break end"
          name="breakEndTime"
          rules={[{ required: true }]}
          style={{ flex: 1 }}
        >
          <TimePicker format="HH:mm" minuteStep={5} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          label="Grace (mins)"
          name="graceMinutes"
          rules={[{ required: true }]}
          style={{ flex: 1 }}
        >
          <InputNumber min={0} max={60} step={5} style={{ width: '100%' }} />
        </Form.Item>
      </Space.Compact>
      <Form.Item label="Reason" name="reason">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="Optional context for the audit trail."
        />
      </Form.Item>
    </DrawerForm>
  );
}

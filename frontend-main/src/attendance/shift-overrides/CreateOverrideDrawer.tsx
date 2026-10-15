// Drawer for creating a new shift override. Triggers a same-tx
// recompute of every attendance row in the range. OVERRIDE_OVERLAP
// surfaces inline on the range field.

import {
  Alert,
  App as AntdApp,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Space,
  TimePicker,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import {
  shiftOverridesApi,
  type CreateShiftOverrideRequest,
} from '@/attendance';
import { DrawerForm } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { EmployeePicker } from '@/people';

type CreateForm = {
  employeeIds: number[];
  range: [Dayjs, Dayjs];
  startTime: Dayjs;
  endTime: Dayjs;
  breakStartTime: Dayjs;
  breakEndTime: Dayjs;
  graceMinutes: number;
  reason?: string;
};

export type CreateOverrideDrawerProps = {
  open: boolean;
  onClose: () => void;
};

export function CreateOverrideDrawer({ open, onClose }: CreateOverrideDrawerProps) {
  const [form] = Form.useForm<CreateForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const mutation = useMutation({
    mutationFn: (body: CreateShiftOverrideRequest) =>
      shiftOverridesApi.create(body),
    onSuccess: (rows) => {
      message.success(
        `Created ${rows.length} override${rows.length === 1 ? '' : 's'}`,
      );
      queryClient.invalidateQueries({ queryKey: ['shift-overrides'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => {
      if (isApiError(err)) {
        if (err.code === 'OVERRIDE_OVERLAP') {
          form.setFields([
            {
              name: 'range',
              errors: [
                'Range overlaps an existing override for one or more employees.',
              ],
            },
          ]);
        } else {
          applyApiErrorToForm(form, err);
        }
      }
      message.error(getErrorMessage(err));
    },
  });

  return (
    <DrawerForm<CreateForm>
      title="New shift override"
      open={open}
      onClose={onClose}
      size={520}
      form={form}
      submitting={mutation.isPending}
      submitText="Create"
      initialValues={{
        breakStartTime: dayjs('13:00', 'HH:mm'),
        breakEndTime: dayjs('14:00', 'HH:mm'),
        graceMinutes: 10,
        startTime: dayjs('09:00', 'HH:mm'),
        endTime: dayjs('18:00', 'HH:mm'),
      }}
      onFinish={(v) => {
        mutation.mutate({
          employeeIds: v.employeeIds,
          fromDate: v.range[0].format('YYYY-MM-DD'),
          toDate: v.range[1].format('YYYY-MM-DD'),
          shiftStartTime: v.startTime.format('HH:mm'),
          shiftEndTime: v.endTime.format('HH:mm'),
          shiftBreakStartTime: v.breakStartTime.format('HH:mm'),
          shiftBreakEndTime: v.breakEndTime.format('HH:mm'),
          shiftGraceMinutes: v.graceMinutes,
          reason: v.reason,
        });
      }}
    >
      <Alert
        type="warning"
        showIcon
        title="Triggers a recompute"
        description="Every attendance row in the range is rewritten in the same transaction. If any date is already in a closed payroll run, payroll will get an event to recompute."
        style={{ marginBottom: 16 }}
      />
      <Form.Item
        label="Employees"
        name="employeeIds"
        rules={[
          {
            required: true,
            type: 'array',
            min: 1,
            message: 'Add at least one employee',
          },
        ]}
        help="Search by name or code, then add as many as you need."
      >
        <EmployeePicker multiple placeholder="Search by name or code" />
      </Form.Item>

      <Form.Item
        label="Range"
        name="range"
        rules={[{ required: true, message: 'Pick the date range' }]}
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
          placeholder="Eid season — extended packing shift across the line."
        />
      </Form.Item>
    </DrawerForm>
  );
}

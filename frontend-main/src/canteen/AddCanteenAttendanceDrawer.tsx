// Manual canteen attendance — the single-entry path.
//
// Flow the canteen desk actually follows: read the card, type the code,
// confirm the person on screen, then stamp the entry. So the drawer is two
// steps in one column — search + selected-employee details on top, entry
// detail below, with the detail fields disabled until someone resolves.
//
// Everything written here is tagged MANUAL so it stays separable from
// device reads and file uploads during reconciliation.
//
// Cancel and submit sit in the body under the form rather than in the drawer
// footer, matching the upload drawer: it keeps both clear of the bottom-right
// corner, where the router devtools button sits in dev builds.

import { useEffect, useState, type ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  TimePicker,
  Typography,
} from 'antd';
import { PencilLine } from 'lucide-react';
import dayjs, { type Dayjs } from 'dayjs';

import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';
import type { EmployeePickerCandidate } from '@/people';
import { canteenAttendanceApi } from './api';
import { CanteenEmployeeLookup } from './CanteenEmployeeLookup';
import type { CreateCanteenAttendanceRequest } from './types';

const { Text } = Typography;

/** Small uppercase divider between the two halves of the drawer. */
function GroupLabel({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: '0.06em',
        textTransform: 'uppercase',
        opacity: 0.6,
        marginBottom: 10,
      }}
    >
      {children}
    </div>
  );
}

export type AddCanteenAttendanceDrawerProps = {
  open: boolean;
  onClose: () => void;
};

type Values = {
  attendanceDate: Dayjs;
  attendanceTime: Dayjs;
  remarks?: string;
};

export function AddCanteenAttendanceDrawer({
  open,
  onClose,
}: AddCanteenAttendanceDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const [employeeId, setEmployeeId] = useState<number | undefined>(undefined);
  const [employee, setEmployee] = useState<EmployeePickerCandidate | null>(null);

  // Drives the usage summary range: whichever month the entry is being filed
  // into, so back-dating the entry re-scopes the totals shown above it. The
  // date field carries an `extra` note saying so — the link is not obvious
  // when the two sit in different halves of the drawer.
  const attendanceDate = Form.useWatch('attendanceDate', form);

  useEffect(() => {
    if (!open) {
      setEmployeeId(undefined);
      setEmployee(null);
      form.resetFields();
    }
  }, [open, form]);

  const mutation = useMutation({
    mutationFn: (body: CreateCanteenAttendanceRequest) =>
      canteenAttendanceApi.create(body),
    onSuccess: () => {
      message.success(
        `Canteen entry recorded for ${employee?.fullName ?? 'the employee'}`,
      );
      queryClient.invalidateQueries({ queryKey: ['canteen'] });
      // Keep the drawer open for the next diner in the queue — clear only
      // the person, not the date/time the clerk just set.
      setEmployeeId(undefined);
      setEmployee(null);
      form.resetFields(['remarks']);
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const now = dayjs();

  return (
    <DrawerForm<Values>
      open={open}
      onClose={onClose}
      title="Add canteen attendance manually"
      size={560}
      form={form}
      submitting={mutation.isPending}
      renderActions={({ requestClose }) => (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 12,
            marginTop: 4,
          }}
        >
          <Text type="secondary" style={{ fontSize: 12, marginRight: 'auto' }}>
            {employeeId === undefined ? (
              'Select an employee to enable saving.'
            ) : (
              <>
                Saving against <strong>{employee?.fullName}</strong>
              </>
            )}
          </Text>
          <Button onClick={requestClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            icon={<PencilLine size={15} strokeWidth={1.75} />}
            loading={mutation.isPending}
            disabled={employeeId === undefined}
          >
            Record manual entry
          </Button>
        </div>
      )}
      initialValues={{
        attendanceDate: now,
        attendanceTime: now,
      }}
      onFinish={(v) => {
        if (employeeId === undefined) return;
        mutation.mutate({
          employeeId,
          attendanceDate: v.attendanceDate.format('YYYY-MM-DD'),
          attendanceTime: v.attendanceTime.format('HH:mm:ss'),
          remarks: v.remarks?.trim() || undefined,
        });
      }}
    >
      <GroupLabel>Search employee</GroupLabel>
      <div style={{ marginBottom: 20 }}>
        <CanteenEmployeeLookup
          value={employeeId}
          summaryMonth={attendanceDate ?? undefined}
          onChange={(id, candidate) => {
            setEmployeeId(id);
            setEmployee(candidate);
          }}
        />
      </div>

      <GroupLabel>Canteen attendance detail</GroupLabel>
      {employeeId === undefined ? (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          title="Select an employee first"
          description="Entry detail unlocks once an employee is selected above."
        />
      ) : null}

      <div style={{ display: 'flex', gap: 12 }}>
        <Form.Item
          label="Attendance date"
          name="attendanceDate"
          rules={[{ required: true, message: 'Pick the date' }]}
          style={{ flex: 1 }}
          extra="Also sets the month the canteen usage above is counted over."
        >
          <DatePicker
            style={{ width: '100%' }}
            format="DD MMM YYYY"
            disabled={employeeId === undefined}
            disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
          />
        </Form.Item>
        <Form.Item
          label="Attendance time"
          name="attendanceTime"
          rules={[{ required: true, message: 'Pick the time' }]}
          style={{ flex: 1 }}
        >
          <TimePicker
            style={{ width: '100%' }}
            format="HH:mm"
            minuteStep={1}
            needConfirm={false}
            disabled={employeeId === undefined}
          />
        </Form.Item>
      </div>

      <Form.Item label="Remarks" name="remarks">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          maxLength={255}
          showCount
          disabled={employeeId === undefined}
          placeholder="Optional — guest meal, replacement card, supervisor approval…"
        />
      </Form.Item>

      <Text type="secondary" style={{ fontSize: 12 }}>
        Entries added here are tagged <strong>Manual</strong> in the ledger so
        they stay separable from device reads during reconciliation.
      </Text>
    </DrawerForm>
  );
}

// Correct one canteen ledger row.
//
// Only the clock time and the count are editable. The employee and the date
// are history, not settings: an entry on the wrong day or against the wrong
// person is deleted and re-added, so the trail stays honest about what was
// originally captured. Both show read-only at the top so the clerk can see
// which row they are on.
//
// Cancel and Save sit in the body under the fields, matching the other two
// canteen drawers.

import { useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  Form,
  InputNumber,
  TimePicker,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Save } from 'lucide-react';

import { DrawerForm, KvList, PersonCell } from '@/components';
import { handleFormError } from '@/lib';
import { canteenAttendanceApi } from './api';
import { formatDateShort } from '@/attendance';
import type { CanteenAttendanceRow, UpdateCanteenAttendanceRequest } from './types';

const { Text } = Typography;

export type EditCanteenAttendanceDrawerProps = {
  /** Row being edited. `null` closes the drawer. */
  row: CanteenAttendanceRow | null;
  onClose: () => void;
};

type Values = {
  attendanceTime: Dayjs;
  count: number;
};

/** "13:45:00" / "13:45" → a Dayjs on an arbitrary date, for TimePicker. */
function parseTime(time: string | null | undefined): Dayjs {
  const [h = '0', m = '0', s = '0'] = (time ?? '').split(':');
  return dayjs().hour(Number(h)).minute(Number(m)).second(Number(s));
}

export function EditCanteenAttendanceDrawer({
  row,
  onClose,
}: EditCanteenAttendanceDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const entry = row?.entry;

  // Reload the form whenever a different row is opened — the drawer is
  // reused across rows, so stale values would otherwise carry over.
  useEffect(() => {
    if (!entry) return;
    form.setFieldsValue({
      attendanceTime: parseTime(entry.attendanceTime),
      count: entry.count ?? 1,
    });
  }, [entry, form]);

  const mutation = useMutation({
    mutationFn: (body: UpdateCanteenAttendanceRequest) =>
      canteenAttendanceApi.update(entry?.id ?? 0, body),
    onSuccess: () => {
      message.success('Canteen entry updated');
      queryClient.invalidateQueries({ queryKey: ['canteen'] });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm<Values>
      open={row !== null}
      onClose={onClose}
      title="Edit canteen attendance"
      size={480}
      form={form}
      submitting={mutation.isPending}
      onFinish={(v) =>
        mutation.mutate({
          attendanceTime: v.attendanceTime.format('HH:mm:ss'),
          count: v.count,
        })
      }
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
          <Button onClick={requestClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            icon={<Save size={15} strokeWidth={1.75} />}
            loading={mutation.isPending}
          >
            Save changes
          </Button>
        </div>
      )}
    >
      <div style={{ marginBottom: 18 }}>
        <PersonCell
          size="default"
          person={{
            fullName:
              row?.employee?.fullName ??
              (entry?.employeeId != null
                ? `Employee #${entry.employeeId}`
                : 'Unknown employee'),
            employeeCode: row?.employee?.employeeCode,
            profilePictureUrl: row?.employee?.profilePictureUrl,
            designation: row?.employee?.designationName,
          }}
        />
        <div style={{ marginTop: 12 }}>
          <KvList
            size="sm"
            layout="inline"
            items={[
              {
                label: 'Department',
                value: row?.employee?.departmentName ?? '—',
              },
              {
                label: 'Attendance date',
                value: formatDateShort(entry?.attendanceDate),
              },
            ]}
          />
        </div>
      </div>

      <Form.Item
        label="Attendance time"
        name="attendanceTime"
        rules={[{ required: true, message: 'Pick the time' }]}
      >
        <TimePicker
          style={{ width: '100%' }}
          format="HH:mm"
          minuteStep={1}
          needConfirm={false}
        />
      </Form.Item>

      <Form.Item
        label="Count"
        name="count"
        rules={[{ required: true, message: 'Count is required' }]}
        extra="Meals this entry represents."
      >
        <InputNumber min={1} precision={0} style={{ width: '100%' }} />
      </Form.Item>

      <Text type="secondary" style={{ fontSize: 12 }}>
        The employee and the date cannot be changed here — delete the row and
        re-add it if it belongs to someone else or to another day.
      </Text>
    </DrawerForm>
  );
}

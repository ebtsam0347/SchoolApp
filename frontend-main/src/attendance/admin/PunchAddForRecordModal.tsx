// Operator add-a-punch modal scoped to an existing attendance record.
//
// Triggered from a record row in the browser (or the dashboard's missing-
// checkouts queue). Pre-fills employeeId + workDate from the record so the
// operator only picks In / Out, the time and a reason. Hits the single-row
// endpoint (`POST /attendance/operator/punch`) with that `direction`; the
// server pins the punch as the day's check-in (In) or check-out (Out) —
// even when other punches are earlier / later — and recomputes the day.
//
// We also fetch the employee's existing punches for the workDate so the
// operator can eyeball what's already in the system before adding — same
// affordance the standalone /attendance/operator-punch page provides.

import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Form,
  Input,
  Modal,
  Radio,
  Tag,
  TimePicker,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';

import { handleFormError } from '@/lib';
import {
  PUNCH_SOURCE_LABEL,
  attendanceApi,
  formatClock,
  formatClockOn,
  punchInstantFor,
  formatDateLong,
  type AttendanceRecordDto,
  type EmployeeSummary,
  type PunchDirection,
} from '@/attendance';

const { Text } = Typography;

export type PunchAddForRecordModalProps = {
  record: AttendanceRecordDto | null;
  employee: EmployeeSummary | null;
  onClose: () => void;
};

type FormShape = {
  direction: PunchDirection;
  time: Dayjs;
  reason: string;
};

/** Comparable `YYYY-MM-DDTHH:mm` of a local date-time string. */
const minuteKey = (iso: string) => iso.slice(0, 16).replace(' ', 'T');

export function PunchAddForRecordModal({
  record,
  employee,
  onClose,
}: PunchAddForRecordModalProps) {
  const [form] = Form.useForm<FormShape>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    if (record) {
      form.setFieldsValue({
        direction: 'IN',
        time: dayjs().second(0).millisecond(0),
        reason: '',
      });
    } else {
      form.resetFields();
    }
  }, [record, form]);

  // Read off `record` once, null-safe. The modal renders closed with
  // record === null, and the React Compiler evaluates what closures read
  // (as memo dependencies) during render — so `record!.x` inside a callback
  // throws there even though the callback never runs.
  const employeeId = record?.employeeId ?? 0;
  const workDate = record?.workDate ?? '';
  const shiftStartTime = record?.shiftStartTime ?? null;

  const punchesQuery = useQuery({
    queryKey: ['attendance', 'employee', employeeId, 'punches', workDate],
    queryFn: ({ signal }) =>
      attendanceApi.punchesForEmployee(employeeId, { from: workDate, to: workDate }, signal),
    enabled: !!record,
    staleTime: 10_000,
  });

  const mutation = useMutation({
    mutationFn: (body: {
      employeeId: number;
      punchedAt: string;
      reason: string;
      direction: PunchDirection;
    }) => attendanceApi.operatorPunch(body),
    onSuccess: (created, { direction }) => {
      message.success(
        `${direction === 'IN' ? 'Check-in' : 'Check-out'} set to ${formatClock(created.punchedAt)} (operator).`,
      );
      void queryClient.invalidateQueries({ queryKey: ['admin', 'attendance'] });
      void queryClient.invalidateQueries({ queryKey: ['attendance'] });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const direction = Form.useWatch('direction', form);
  const time = Form.useWatch('time', form);
  // The calendar instant the picked time lands on — the next morning for an
  // OT / night-shift check-out, by the same 2h pre-shift rule as the server.
  const instantOf = (t: Dayjs) => punchInstantFor(workDate, t.format('HH:mm'), shiftStartTime);
  const pickedInstant = record && time ? instantOf(time) : null;

  const subjectName = employee?.fullName ?? `Employee #${record?.employeeId ?? ''}`;
  const subjectCode = employee?.employeeCode;

  return (
    <Modal
      title={`Add punch · ${subjectName}`}
      open={record !== null}
      onCancel={() => (mutation.isPending ? undefined : onClose())}
      okText={
        direction === 'IN' ? 'Set check-in' : direction === 'OUT' ? 'Set check-out' : 'Record punch'
      }
      okButtonProps={{ loading: mutation.isPending }}
      onOk={() => form.submit()}
      destroyOnHidden
      mask={{ closable: !mutation.isPending }}
    >
      {record ? (
        <>
          <div style={{ marginBottom: 12 }}>
            <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
              {formatDateLong(record.workDate)}
              {subjectCode ? ` · ${subjectCode}` : ''}
            </Text>
            <div style={{ marginTop: 6, display: 'flex', gap: 12, fontSize: 12 }}>
              <span>
                <Text type="secondary">Check-in: </Text>
                <strong>{formatClockOn(record.checkIn, record.workDate)}</strong>
              </span>
              <span>
                <Text type="secondary">Check-out: </Text>
                <strong>{formatClockOn(record.checkOut, record.workDate)}</strong>
              </span>
              {record.shiftStartTime && record.shiftEndTime ? (
                <span>
                  <Text type="secondary">Shift: </Text>
                  <strong>
                    {record.shiftStartTime.slice(0, 5)} – {record.shiftEndTime.slice(0, 5)}
                  </strong>
                </span>
              ) : null}
            </div>
          </div>

          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            title="Tagged as OPERATOR — bypasses the WEB 2/day cap; same-instant dedup still applies."
          />

          {punchesQuery.data && punchesQuery.data.length > 0 ? (
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 11 }}>
                Existing punches today
              </Text>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 4 }}>
                {punchesQuery.data
                  .slice()
                  .sort((a, b) => a.punchedAt.localeCompare(b.punchedAt))
                  .map((p) => (
                    <Tag key={p.id} style={{ margin: 0 }}>
                      {formatClockOn(p.punchedAt, record.workDate)} · {PUNCH_SOURCE_LABEL[p.source]}
                      {p.direction ? ` · ${p.direction}` : ''}
                    </Tag>
                  ))}
              </div>
            </div>
          ) : null}

          <Form
            form={form}
            layout="vertical"
            onFinish={(v) => {
              mutation.mutate({
                employeeId: record.employeeId,
                punchedAt: instantOf(v.time),
                reason: v.reason,
                direction: v.direction,
              });
            }}
          >
            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
              <Form.Item
                label="Punch is"
                name="direction"
                rules={[{ required: true, message: 'Pick In or Out' }]}
                extra={
                  direction === 'IN'
                    ? `Replaces check-in${record.checkIn ? ` ${formatClockOn(record.checkIn, record.workDate)}` : ''}`
                    : direction === 'OUT'
                      ? `Replaces check-out${record.checkOut ? ` ${formatClockOn(record.checkOut, record.workDate)}` : ''}`
                      : undefined
                }
              >
                <Radio.Group
                  optionType="button"
                  buttonStyle="solid"
                  options={[
                    { value: 'IN', label: 'In' },
                    { value: 'OUT', label: 'Out' },
                  ]}
                />
              </Form.Item>
              <Form.Item
                label="Time"
                name="time"
                style={{ flex: 1 }}
                dependencies={['direction']}
                extra={
                  pickedInstant && pickedInstant.slice(0, 10) !== record.workDate
                    ? `Saved as ${formatClockOn(pickedInstant, record.workDate)} — counts for ${formatDateLong(record.workDate)}`
                    : undefined
                }
                rules={[
                  { required: true, message: 'Pick the punch time' },
                  ({ getFieldValue }) => ({
                    validator: (_r, t: Dayjs | undefined) => {
                      if (!t) return Promise.resolve();
                      const at = minuteKey(instantOf(t));
                      const dir = getFieldValue('direction') as PunchDirection | undefined;
                      if (dir === 'IN' && record.checkOut && at >= minuteKey(record.checkOut)) {
                        return Promise.reject(
                          new Error(
                            `Check-in must be before check-out (${formatClockOn(record.checkOut, record.workDate)})`,
                          ),
                        );
                      }
                      if (dir === 'OUT' && record.checkIn && at <= minuteKey(record.checkIn)) {
                        return Promise.reject(
                          new Error(
                            `Check-out must be after check-in (${formatClockOn(record.checkIn, record.workDate)})`,
                          ),
                        );
                      }
                      return Promise.resolve();
                    },
                  }),
                ]}
              >
                <TimePicker
                  format="HH:mm"
                  minuteStep={1}
                  needConfirm={false}
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </div>
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
                placeholder="missed checkout, paper form #12 / approved by supervisor X"
              />
            </Form.Item>
          </Form>
        </>
      ) : null}
    </Modal>
  );
}

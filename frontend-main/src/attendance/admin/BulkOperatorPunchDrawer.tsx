// Bulk operator-punch drawer.
//
// Operators key in N rows off a paper sheet (end-of-shift slips, supervisor
// note for a missing biometric run). Server caps the batch at 100 and runs
// it in one transaction — a single bad row rolls back the lot, so the UX
// surfaces failures as one banner rather than per-row.
//
// Speed knobs: a "Default reason" + "Default date" applied at submit-time
// to rows that didn't override, plus "+ Add row" that copies the previous
// row's date / reason so a sequence of slips for the same shift is a few
// keystrokes per row.

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  Space,
  TimePicker,
  Typography,
  theme as antdTheme,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Copy, Plus, Trash2 } from 'lucide-react';

import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';
import { toLocalDateTime } from '@/lib/dates';
import { EmployeePicker } from '@/people';
import {
  attendanceApi,
  type BulkOperatorPunchRequest,
  type OperatorPunchRequest,
} from '@/attendance';

const { Text } = Typography;
const { useToken } = antdTheme;

/** Server cap. Mirror of the @Size(max = 100) on BulkOperatorPunchRequest. */
const MAX_ROWS = 100;

export type BulkOperatorPunchDrawerProps = {
  open: boolean;
  onClose: () => void;
};

type RowShape = {
  employeeId?: number;
  date?: Dayjs;
  time?: Dayjs;
  reason?: string;
};

type FormShape = {
  defaultDate?: Dayjs;
  defaultReason?: string;
  rows: RowShape[];
};

const EMPTY_ROW: RowShape = {};

export function BulkOperatorPunchDrawer({
  open,
  onClose,
}: BulkOperatorPunchDrawerProps) {
  const [form] = Form.useForm<FormShape>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const { token } = useToken();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: BulkOperatorPunchRequest) =>
      attendanceApi.operatorPunchBulk(body),
    onSuccess: (created) => {
      message.success(
        `${created.length} punch${created.length === 1 ? '' : 'es'} recorded.`,
      );
      void queryClient.invalidateQueries({ queryKey: ['admin', 'attendance'] });
      void queryClient.invalidateQueries({ queryKey: ['attendance'] });
      form.resetFields();
      setSubmitError(null);
      onClose();
    },
    onError: (err) => {
      const result = handleFormError(form, err, message);
      // Server says "the whole batch rolls back" — surface that explicitly so
      // the operator doesn't think a partial insert succeeded.
      if (result.applied.length === 0) {
        setSubmitError(
          'Nothing was recorded — the whole batch rolled back. Fix the offending row and resubmit.',
        );
      }
    },
  });

  const handleSubmit = (values: FormShape) => {
    setSubmitError(null);
    const rows = values.rows ?? [];
    const punches: OperatorPunchRequest[] = [];
    const violations: string[] = [];

    rows.forEach((row, index) => {
      const employeeId = row.employeeId;
      const date = row.date ?? values.defaultDate;
      const time = row.time;
      const reason = (row.reason ?? values.defaultReason ?? '').trim();

      if (!employeeId) {
        violations.push(`Row ${index + 1}: pick an employee.`);
        return;
      }
      if (!date || !time) {
        violations.push(`Row ${index + 1}: date and time are required.`);
        return;
      }
      if (reason.length < 4) {
        violations.push(
          `Row ${index + 1}: reason is required (min 4 chars). Set a default reason or fill the row.`,
        );
        return;
      }

      const punchedAt = toLocalDateTime(
        date
          .hour(time.hour())
          .minute(time.minute())
          .second(0)
          .millisecond(0),
      );

      punches.push({ employeeId, punchedAt, reason });
    });

    if (violations.length > 0) {
      setSubmitError(violations.slice(0, 3).join(' '));
      return;
    }
    if (punches.length === 0) {
      setSubmitError('Add at least one row.');
      return;
    }

    mutation.mutate({ punches });
  };

  return (
    <DrawerForm<FormShape>
      open={open}
      onClose={() => (mutation.isPending ? undefined : onClose())}
      title="Bulk add punches"
      size={760}
      form={form}
      initialValues={{
        defaultDate: dayjs(),
        rows: [EMPTY_ROW],
      }}
      onFinish={handleSubmit}
      submitText={`Record batch`}
      submitting={mutation.isPending}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        title="The whole batch lands in one transaction"
        description="Any single row failing rolls back everything. Server cap is 100 rows per submit."
      />

      <Space.Compact block style={{ marginBottom: 4 }}>
        <Form.Item
          label="Default date"
          name="defaultDate"
          tooltip="Applied to any row that doesn't pick its own date."
          style={{ flex: 1 }}
        >
          <DatePicker
            style={{ width: '100%' }}
            format="DD MMM YYYY"
            disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
          />
        </Form.Item>
        <Form.Item
          label="Default reason"
          name="defaultReason"
          tooltip="Applied to any row that doesn't write its own reason."
          style={{ flex: 2 }}
        >
          <Input placeholder="missed end-of-shift checkout, paper sheet #12" />
        </Form.Item>
      </Space.Compact>

      <Form.List name="rows">
        {(fields, { add, remove }) => {
          const rowCount = fields.length;
          const overCap = rowCount >= MAX_ROWS;

          const duplicate = (sourceIndex: number) => {
            const all = form.getFieldValue('rows') as RowShape[];
            const src = all?.[sourceIndex] ?? {};
            add({ ...src, employeeId: undefined });
          };

          return (
            <>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginTop: 8,
                  marginBottom: 8,
                }}
              >
                <Text strong>Punches</Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {rowCount} / {MAX_ROWS}
                </Text>
              </div>

              {fields.map((field, index) => (
                <div
                  key={field.key}
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'minmax(220px, 1.6fr) minmax(150px, 1fr) 110px minmax(180px, 1.4fr) auto',
                    gap: 8,
                    alignItems: 'flex-start',
                    padding: '12px 0',
                    borderTop:
                      index === 0
                        ? `1px solid ${token.colorBorderSecondary}`
                        : 'none',
                    borderBottom: `1px solid ${token.colorBorderSecondary}`,
                  }}
                >
                  <Form.Item
                    {...field}
                    label={index === 0 ? 'Employee' : undefined}
                    name={[field.name, 'employeeId']}
                    rules={[{ required: true, message: 'Required' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <EmployeePicker
                      placeholder="Search by name or code"
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    label={index === 0 ? 'Date' : undefined}
                    name={[field.name, 'date']}
                    style={{ marginBottom: 0 }}
                  >
                    <DatePicker
                      format="DD MMM YYYY"
                      style={{ width: '100%' }}
                      disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
                      placeholder="Default"
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    label={index === 0 ? 'Time' : undefined}
                    name={[field.name, 'time']}
                    rules={[{ required: true, message: 'Required' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <TimePicker
                      format="HH:mm"
                      minuteStep={1}
                      needConfirm={false}
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    label={index === 0 ? 'Reason' : undefined}
                    name={[field.name, 'reason']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Default" />
                  </Form.Item>
                  <Space size={2} style={{ paddingTop: index === 0 ? 30 : 4 }}>
                    <Button
                      size="small"
                      type="text"
                      title="Duplicate row"
                      icon={<Copy size={14} strokeWidth={1.75} />}
                      onClick={() => duplicate(index)}
                      disabled={overCap}
                    />
                    <Button
                      size="small"
                      type="text"
                      danger
                      title="Remove row"
                      icon={<Trash2 size={14} strokeWidth={1.75} />}
                      onClick={() => remove(field.name)}
                      disabled={fields.length <= 1}
                    />
                  </Space>
                </div>
              ))}

              <Button
                type="dashed"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => {
                  const all = form.getFieldValue('rows') as RowShape[];
                  const last = all?.[all.length - 1] ?? {};
                  add({
                    date: last.date,
                    reason: last.reason,
                  });
                }}
                style={{ marginTop: 12, width: '100%' }}
                disabled={overCap}
              >
                {overCap ? `Server cap reached (${MAX_ROWS})` : 'Add row'}
              </Button>
            </>
          );
        }}
      </Form.List>

      {submitError ? (
        <Alert
          type="error"
          showIcon
          style={{ marginTop: 16 }}
          title="Couldn't record the batch"
          description={submitError}
        />
      ) : null}
    </DrawerForm>
  );
}

// Regularization request — manager + HR will review. The form takes a
// missed check-in / check-out + a reason; the day's totals re-derive
// automatically once the punches are approved and replayed.
//
// Targeted mode (`onBehalfOf` prop): a manager filing for a direct report
// from the team attendance calendar. The target is fixed, shown read-only,
// and passed straight to the mutation. attendance-service auto-approves the
// MANAGER stage (the manager is the one submitting), so it goes on to HR.
//
// `routing="team"` is the team-request-generation variant: a clerk filing for
// a department colleague. Nothing is auto-approved there — the chain runs in
// full, starting with the filer's own manager — so only the copy differs.

import { Alert, App as AntdApp, Form, Input, TimePicker } from 'antd';
import { useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { attendanceApi, formatClock, type PunchDto } from '@/attendance';
import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';
import { formatDateShort, toLocalDateTime } from '@/lib/dates';
import { dateOnDay } from './build-grid';

type RegularizeForm = {
  checkIn: Dayjs | null;
  checkOut: Dayjs | null;
  reason: string;
};

/** Who a targeted-mode request is for. */
export type RegularizeOnBehalfTarget = {
  id: number;
  fullName: string;
  employeeCode?: string | null;
};

export type RegularizeDrawerProps = {
  open: boolean;
  workDate: string;
  existingPunches: PunchDto[];
  onClose: () => void;
  onDone: () => void;
  /** File for this direct report instead of the caller. */
  onBehalfOf?: RegularizeOnBehalfTarget | null;
  /**
   * Who approves an `onBehalfOf` filing, for the copy only. `manager`
   * (default): the filer is the target's manager, so their step is skipped.
   * `team`: a team-request generator — the full chain runs.
   */
  routing?: 'manager' | 'team';
};

export function RegularizeDrawer({
  open,
  workDate,
  existingPunches,
  onClose,
  onDone,
  onBehalfOf,
  routing = 'manager',
}: RegularizeDrawerProps) {
  const [form] = Form.useForm<RegularizeForm>();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const targetId = onBehalfOf?.id ?? null;

  // A different person or day starts from a blank form.
  useEffect(() => {
    if (open) form.resetFields();
  }, [open, targetId, workDate, form]);

  const mutation = useMutation({
    mutationFn: (body: {
      checkIn?: string;
      checkOut?: string;
      reason: string;
    }) =>
      attendanceApi.regularize({
        workDate,
        ...body,
        onBehalfOf: targetId ?? undefined,
      }),
    onSuccess: () => {
      message.success(
        onBehalfOf
          ? `Regularization submitted for ${onBehalfOf.fullName} — ${
              routing === 'team' ? 'pending approval' : 'pending HR approval'
            }.`
          : 'Regularization submitted — pending approval.',
      );
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      queryClient.invalidateQueries({ queryKey: ['bff', 'attendance'] });
      form.resetFields();
      onDone();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm<RegularizeForm>
      title="Regularize day"
      open={open}
      onClose={onClose}
      size={420}
      form={form}
      submitting={mutation.isPending}
      submitText="Submit for approval"
      onFinish={(v) => {
        if (!v.checkIn && !v.checkOut) {
          form.setFields([
            {
              name: 'checkIn',
              errors: ['At least one of check-in or check-out is required'],
            },
          ]);
          return;
        }
        mutation.mutate({
          checkIn: v.checkIn ? toLocalDateTime(v.checkIn) : undefined,
          checkOut: v.checkOut ? toLocalDateTime(v.checkOut) : undefined,
          reason: v.reason,
        });
      }}
    >
      {onBehalfOf ? (
        <Alert
          type="info"
          showIcon
          title={`Regularizing ${formatDateShort(workDate)} for ${onBehalfOf.fullName}${
            onBehalfOf.employeeCode ? ` · ${onBehalfOf.employeeCode}` : ''
          }`}
          description={
            routing === 'team'
              ? "Add their missed check-in or check-out — at least one time is required. It goes to your manager for approval, then on through the usual chain. Approved punches replay automatically, so the day's totals update."
              : "Add their missed check-in or check-out — at least one time is required. Your approval step is skipped since you're filing it; HR still reviews. Approved punches replay automatically, so the day's totals update."
          }
          style={{ marginBottom: 16 }}
        />
      ) : (
        <Alert
          type="info"
          showIcon
          title="Add a missed check-in or check-out"
          description="At least one time is required. The request flows through your manager and HR. Approved punches replay through the standard re-derivation, so the day's totals update automatically."
          style={{ marginBottom: 16 }}
        />
      )}
      <Form.Item
        label="Check-in"
        name="checkIn"
        help={
          existingPunches.length > 0
            ? `Earliest existing punch: ${formatClock(existingPunches[0]!.punchedAt)}`
            : undefined
        }
      >
        <TimePicker
          format="HH:mm"
          minuteStep={5}
          placeholder="Time"
          style={{ width: '100%' }}
          // Bind the date portion to workDate so the resulting timestamp is on the right day.
          onChange={(t) => {
            if (t) form.setFieldValue('checkIn', dateOnDay(workDate, t));
          }}
        />
      </Form.Item>
      <Form.Item label="Check-out" name="checkOut">
        <TimePicker
          format="HH:mm"
          minuteStep={5}
          placeholder="Time"
          style={{ width: '100%' }}
          onChange={(t) => {
            if (t) form.setFieldValue('checkOut', dateOnDay(workDate, t));
          }}
        />
      </Form.Item>
      <Form.Item
        label="Reason"
        name="reason"
        rules={[{ required: true, min: 4, message: 'Tell us why this needs adjusting' }]}
      >
        <Input.TextArea
          autoSize={{ minRows: 3, maxRows: 5 }}
          placeholder="Forgot to punch out — left at 18:30 after the team meeting."
        />
      </Form.Item>
    </DrawerForm>
  );
}

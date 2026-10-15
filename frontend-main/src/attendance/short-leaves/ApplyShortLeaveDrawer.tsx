// Drawer for "apply for short leave" — sub-day leave between (startTime,
// endTime) on a given date. No balance/preview involved, unlike full-day
// leave; the request routes straight into the approval chain.
//
// A manager (or HR with the override) also gets an "Apply for" field — the
// same `OnBehalfPicker` the leave drawer uses — to file for a direct report.
// attendance-service auto-approves the MANAGER stage in that case.
//
// Targeted mode (`onBehalfOf` prop): Team requests opens the drawer from a
// department roster row. The target is fixed, shown read-only, and passed
// straight to the mutation — never through the form store (see the same
// note in ApplyLeaveDrawer). A team-request filing is not auto-approved: it
// opens at the filer's own manager.

import { useEffect } from 'react';
import { Alert, App as AntdApp, DatePicker, Form, Input, TimePicker } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';

import { DrawerForm } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { OnBehalfPicker, useCanSubmitOnBehalf } from '../OnBehalfPicker';
import { shortLeavesApi } from '../api';
import type { CreateShortLeaveRequest } from '../types';

/** Who a targeted-mode request is for. */
export type ShortLeaveOnBehalfTarget = {
  id: number;
  fullName: string;
  employeeCode?: string | null;
};

export type ApplyShortLeaveDrawerProps = {
  open: boolean;
  onClose: () => void;
  /**
   * Fixed on-behalf target. Supplying it switches the drawer to targeted
   * mode: the picker is replaced by a read-only "applying for" banner and
   * this id is always what gets submitted.
   */
  onBehalfOf?: ShortLeaveOnBehalfTarget | null;
};

type Values = {
  leaveDate: Dayjs;
  range: [Dayjs, Dayjs];
  reason?: string;
  /** "Apply for" picker. Undefined = myself. */
  onBehalfOf?: number;
};

export function ApplyShortLeaveDrawer({
  open,
  onClose,
  onBehalfOf,
}: ApplyShortLeaveDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const onBehalf = useCanSubmitOnBehalf();
  const showPicker = !onBehalfOf && onBehalf.enabled;
  const pickedId = Form.useWatch('onBehalfOf', form);
  // The picker's value is only honoured while it's on screen.
  const targetId = onBehalfOf?.id ?? (showPicker ? (pickedId ?? null) : null);

  // A new fixed target starts from a blank form — the previous row's date
  // and times must not follow the next employee in. Not keyed on the
  // picker, so choosing someone there doesn't wipe what's been typed.
  const fixedTargetId = onBehalfOf?.id ?? null;
  useEffect(() => {
    if (open) form.resetFields();
  }, [open, fixedTargetId, form]);

  const mutation = useMutation({
    mutationFn: (body: CreateShortLeaveRequest) => shortLeavesApi.apply(body),
    onSuccess: () => {
      message.success(
        onBehalfOf
          ? `Short leave submitted for ${onBehalfOf.fullName}`
          : targetId
            ? 'Short leave submitted for your team member'
            : 'Short leave submitted',
      );
      queryClient.invalidateQueries({ queryKey: ['short-leaves'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => {
      if (isApiError(err)) {
        if (err.code === 'SHORT_LEAVE_OVERLAPS_EXISTING') {
          form.setFields([
            {
              name: 'range',
              errors: [
                targetId
                  ? 'This overlaps an active short leave they already have on file.'
                  : 'This overlaps an active short leave you already have on file.',
              ],
            },
          ]);
          return;
        }
        if (err.code === 'SHORT_LEAVE_RANGE_INVALID') {
          form.setFields([
            { name: 'range', errors: ['End time must be after the start time.'] },
          ]);
          return;
        }
        applyApiErrorToForm(form, err);
      }
      message.error(getErrorMessage(err));
    },
  });

  return (
    <DrawerForm<Values>
      title="Apply for short leave"
      open={open}
      onClose={onClose}
      form={form}
      submitText="Submit"
      submitting={mutation.isPending}
      onFinish={(v) =>
        mutation.mutate({
          leaveDate: v.leaveDate.format('YYYY-MM-DD'),
          startTime: v.range[0].format('HH:mm'),
          endTime: v.range[1].format('HH:mm'),
          reason: v.reason,
          onBehalfOf: targetId ?? undefined,
        })
      }
    >
      {onBehalfOf ? (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 'var(--app-space-md)' }}
          title={`Applying for ${onBehalfOf.fullName}${
            onBehalfOf.employeeCode ? ` · ${onBehalfOf.employeeCode}` : ''
          }`}
          description="Close this drawer and pick another row to file for someone else."
        />
      ) : null}

      {showPicker ? (
        <Form.Item
          label="Apply for"
          name="onBehalfOf"
          extra={
            onBehalf.hasOverride
              ? 'Submitting for someone else uses your HR override — the chain is auto-approved.'
              : 'Submitting for a direct report skips your approval step.'
          }
        >
          <OnBehalfPicker />
        </Form.Item>
      ) : null}

      <Form.Item
        label="Date"
        name="leaveDate"
        rules={[{ required: true, message: 'Pick the date' }]}
      >
        <DatePicker
          style={{ width: '100%' }}
          format="DD MMM YYYY"
          disabledDate={(d) => d && d.isBefore(dayjs(), 'day')}
        />
      </Form.Item>

      <Form.Item
        label="Time away"
        name="range"
        rules={[{ required: true, message: 'Pick the start and end time' }]}
        help="Sub-day only — for a full day off, apply for leave instead."
      >
        <TimePicker.RangePicker
          format="HH:mm"
          minuteStep={5}
          style={{ width: '100%' }}
        />
      </Form.Item>

      <Form.Item label="Reason" name="reason">
        <Input.TextArea
          autoSize={{ minRows: 3, maxRows: 5 }}
          placeholder={
            targetId
              ? 'Optional — their approver will see this on the request.'
              : 'Optional — your approver will see this on the request.'
          }
        />
      </Form.Item>
    </DrawerForm>
  );
}
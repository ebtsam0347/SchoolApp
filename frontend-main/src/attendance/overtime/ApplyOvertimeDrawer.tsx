// Request overtime, for yourself or — when the caller already picked someone
// (Team requests opens this straight from a roster row) — for a colleague.
//
// The target is fixed by the `onBehalfOf` prop and handed straight to the
// mutation rather than seeded into a form field: rc-field-form drops store
// values whose Form.Item never mounted, which previously let the request land
// against the submitter.

import { Alert, App as AntdApp, DatePicker, Form, Input, Radio, Select } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import dayjs from 'dayjs';

import { DrawerForm } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { overtimeApi } from '../api';
import { FACTORY_CODES, OVERTIME_TYPE_DESCRIPTION, OVERTIME_TYPE_LABEL } from '../format';
import type { CreateOvertimeRequest, FactoryCode, OvertimeType } from '../types';

/** Who a targeted request is for. */
export type OvertimeOnBehalfTarget = {
  id: number;
  fullName: string;
  employeeCode?: string | null;
};

export type ApplyOvertimeDrawerProps = {
  open: boolean;
  onClose: () => void;
  /**
   * Fixed on-behalf target. Supplying it shows a read-only "applying for"
   * banner and makes this id what gets submitted.
   */
  onBehalfOf?: OvertimeOnBehalfTarget | null;
};

type Values = {
  workDate: dayjs.Dayjs;
  type: OvertimeType;
  factoryCode: FactoryCode;
  reason?: string;
};

const TYPE_OPTIONS: OvertimeType[] = ['NORMAL', 'EXTRA', 'SPECIAL'];

export function ApplyOvertimeDrawer({
  open,
  onClose,
  onBehalfOf,
}: ApplyOvertimeDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const targetId = onBehalfOf?.id ?? null;

  // The drawer stays mounted between rows, so clear the previous person's
  // answers rather than letting them follow the next one in.
  useEffect(() => {
    if (open) form.resetFields();
  }, [open, targetId, form]);

  const mutation = useMutation({
    mutationFn: (body: CreateOvertimeRequest) => overtimeApi.apply(body),
    onSuccess: () => {
      message.success(
        onBehalfOf
          ? `OT request submitted for ${onBehalfOf.fullName}`
          : 'OT request submitted',
      );
      queryClient.invalidateQueries({ queryKey: ['overtime'] });
      queryClient.invalidateQueries({ queryKey: ['bff', 'overtime'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => {
      if (isApiError(err)) {
        // attendance-service spells it `OVERTIME_NO_PUNCH_FOR_DATE` (422,
        // title "Overtime no punch for date") — verified against the running
        // service. The unprefixed spelling never matched, so this branch was
        // dead and the raw server detail leaked to a toast instead.
        if (err.code === 'OVERTIME_NO_PUNCH_FOR_DATE') {
          form.setFields([
            {
              name: 'workDate',
              errors: [
                onBehalfOf
                  ? 'No punch on this date — they must have worked at least once.'
                  : 'No punch on this date — you must have worked at least once.',
              ],
            },
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
      title="Request overtime"
      open={open}
      onClose={onClose}
      form={form}
      submitText="Submit"
      submitting={mutation.isPending}
      onFinish={(v) =>
        mutation.mutate({
          workDate: v.workDate.format('YYYY-MM-DD'),
          overtimeType: v.type,
          factoryCode: v.factoryCode,
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
      <Form.Item
        label="Work date"
        name="workDate"
        rules={[{ required: true, message: 'Pick the date you worked extra' }]}
        help={
          onBehalfOf
            ? 'Must be a date where they have at least one punch.'
            : 'Must be a date where you have at least one punch.'
        }
      >
        <DatePicker
          style={{ width: '100%' }}
          format="DD MMM YYYY"
          disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
        />
      </Form.Item>
      <Form.Item
        label="OT type"
        name="type"
        rules={[{ required: true, message: 'Pick which OT bucket to pay' }]}
        extra={
          <Form.Item noStyle shouldUpdate={(p, c) => p.type !== c.type}>
            {({ getFieldValue }) => {
              const t = getFieldValue('type') as OvertimeType | undefined;
              return t ? OVERTIME_TYPE_DESCRIPTION[t] : 'Only the picked bucket is paid — anything in the other buckets is ignored.';
            }}
          </Form.Item>
        }
      >
        <Radio.Group optionType="button" buttonStyle="solid">
          {TYPE_OPTIONS.map((t) => (
            <Radio.Button key={t} value={t}>
              {OVERTIME_TYPE_LABEL[t]}
            </Radio.Button>
          ))}
        </Radio.Group>
      </Form.Item>
      <Form.Item
        label="Factory code"
        name="factoryCode"
        rules={[{ required: true, message: 'Pick the factory code' }]}
      >
        <Select
          placeholder="Select factory code"
          options={FACTORY_CODES.map((c) => ({ value: c, label: c }))}
        />
      </Form.Item>
      <Form.Item label="Reason" name="reason">
        <Input.TextArea
          autoSize={{ minRows: 3, maxRows: 5 }}
          placeholder="Optional — what kept you on shift past hours?"
        />
      </Form.Item>
    </DrawerForm>
  );
}

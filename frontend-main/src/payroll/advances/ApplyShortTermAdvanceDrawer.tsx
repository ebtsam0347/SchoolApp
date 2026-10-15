// Self-service short-term advance request.
//
// Short-term advances only have an HR-side batch endpoint
// (POST /short-term-advance-batches, N employees at once) — there's no
// singular self-apply endpoint yet. This drawer works around that by
// submitting a one-line batch with the caller as the sole employee, so an
// employee can request their own advance without a new backend endpoint.
// HR still reviews it exactly like any other batch, then Accounts approves.

import { App as AntdApp, DatePicker, Form, Input, InputNumber } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useEffect } from 'react';

import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';
import { employeesApi } from '@/people/api';
import { shortTermAdvancesApi } from '../api';

export type ApplyShortTermAdvanceDrawerProps = {
  open: boolean;
  onClose: () => void;
};

type Values = {
  targetMonth: dayjs.Dayjs;
  amount: number;
  notes?: string;
};

export function ApplyShortTermAdvanceDrawer({
  open,
  onClose,
}: ApplyShortTermAdvanceDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const meQuery = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: ({ signal }) => employeesApi.me(signal),
    staleTime: 5 * 60_000,
    enabled: open,
    retry: false,
  });

  useEffect(() => {
    if (!open) form.resetFields();
  }, [open, form]);

  const mutation = useMutation({
    mutationFn: (body: { targetYear: number; targetMonth: number; notes?: string; employeeId: number; amount: number }) =>
      shortTermAdvancesApi.create({
        targetYear: body.targetYear,
        targetMonth: body.targetMonth,
        notes: body.notes,
        lines: [{ employeeId: body.employeeId, amount: body.amount }],
      }),
    onSuccess: () => {
      message.success('Advance request submitted');
      queryClient.invalidateQueries({
        queryKey: ['payroll', 'short-term-advance-batches'],
        exact: false,
      });
      form.resetFields();
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm<Values>
      title="Request short-term advance"
      open={open}
      onClose={onClose}
      form={form}
      size={480}
      submitText="Submit request"
      submitting={mutation.isPending}
      initialValues={{ targetMonth: dayjs().add(1, 'month').startOf('month') }}
      onFinish={(v) => {
        const employeeId = meQuery.data?.id;
        if (!employeeId) {
          message.error("Couldn't resolve your employee record");
          return;
        }
        const period = v.targetMonth;
        mutation.mutate({
          targetYear: period.year(),
          targetMonth: period.month() + 1,
          notes: v.notes?.trim() || undefined,
          employeeId,
          amount: v.amount,
        });
      }}
    >
      <Form.Item
        label="Deduct in"
        name="targetMonth"
        rules={[{ required: true, message: 'Pick the payroll month' }]}
      >
        <DatePicker
          picker="month"
          style={{ width: '100%' }}
          format="MMM YYYY"
          allowClear={false}
        />
      </Form.Item>

      <Form.Item
        label="Amount"
        name="amount"
        rules={[
          { required: true, message: 'Enter the amount' },
          { type: 'number', min: 1, message: 'Must be greater than zero' },
        ]}
      >
        <InputNumber
          style={{ width: '100%' }}
          min={0}
          step={500}
          addonBefore="Rs"
          placeholder="5000"
        />
      </Form.Item>

      <Form.Item label="Notes" name="notes">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="What the advance is for."
          maxLength={1024}
          showCount
        />
      </Form.Item>
    </DrawerForm>
  );
}

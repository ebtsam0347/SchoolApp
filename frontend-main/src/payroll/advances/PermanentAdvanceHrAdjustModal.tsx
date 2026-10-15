// HR's "Adjust" step on a long-term advance waiting on HR (MANAGER_APPROVED):
// revise the amount and monthly deduction, with a note, before approving.
// Opened from the approvals inbox's Long-term advances table.
//
// POST /permanent-advances/{id}/hr-adjust-before-approval. Status and the
// approval chain don't change — HR still approves afterwards — so on success
// the inbox and advance lists are refreshed to show the new figures.

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Form, Input, InputNumber, Modal } from 'antd';

import { getErrorMessage } from '@/lib';
import { permanentAdvancesApi } from '../api';
import type { PermanentAdvanceDto } from '../types';

export type PermanentAdvanceHrAdjustModalProps = {
  /** The advance being adjusted — `null` keeps the modal closed. */
  row: PermanentAdvanceDto | null;
  onClose: () => void;
};

type FormValues = {
  amount: number;
  monthlyDeduction: number;
  note?: string;
};

export function PermanentAdvanceHrAdjustModal({ row, onClose }: PermanentAdvanceHrAdjustModalProps) {
  const [form] = Form.useForm<FormValues>();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      permanentAdvancesApi.hrAdjustBeforeApproval(row?.id ?? 0, {
        amount: values.amount,
        monthlyDeduction: values.monthlyDeduction,
        note: values.note?.trim() || undefined,
      }),
    onSuccess: () => {
      message.success('Advance adjusted');
      void queryClient.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'permanent-advances'] });
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  return (
    <Modal
      title={row ? `Adjust advance ${row.requestCode ?? `#${row.id}`}` : 'Adjust advance'}
      open={row != null}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Save"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Form
        // Remount per row (and on every open, via destroyOnHidden) so
        // `initialValues` re-applies the advance's current figures.
        key={row?.id ?? 'none'}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={{
          amount: row?.amount,
          monthlyDeduction: row?.monthlyDeduction,
          note: '',
        }}
        onFinish={(values) => mutation.mutate(values)}
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            label="Amount"
            name="amount"
            rules={[
              { required: true, message: 'Enter the amount' },
              { type: 'number', min: 1, message: 'Must be greater than zero' },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={0} step={1000} addonBefore="Rs" autoFocus />
          </Form.Item>
          <Form.Item
            label="Monthly deduction"
            name="monthlyDeduction"
            dependencies={['amount']}
            rules={[
              { required: true, message: 'Enter the monthly deduction' },
              { type: 'number', min: 1, message: 'Must be greater than zero' },
              ({ getFieldValue }) => ({
                validator: (_rule, value?: number) => {
                  const amount = getFieldValue('amount') as number | undefined;
                  return value != null && amount != null && value > amount
                    ? Promise.reject(new Error("Can't exceed the amount"))
                    : Promise.resolve();
                },
              }),
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={0} step={500} addonBefore="Rs" />
          </Form.Item>
        </div>
        <Form.Item
          label="Note"
          name="note"
          rules={[{ max: 1024, message: 'Keep it under 1024 characters' }]}
          style={{ marginBottom: 0 }}
        >
          <Input.TextArea rows={3} placeholder="Why the figures were changed" maxLength={1024} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

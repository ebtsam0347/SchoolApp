// Accounts' "Record deduction" on an APPROVED long-term advance: an
// out-of-band recovery (cash repaid, etc.) entered by hand as a MANUAL row
// in the employee's permanent-advance ledger.
//
// POST /permanent-advances/{id}/deductions. The balance is per employee
// (all APPROVED advances − all deductions), and the server refuses an amount
// above it with 409 — the message is surfaced as-is rather than re-checked
// here, so the server stays the single source of the cap.

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, DatePicker, Form, Input, InputNumber, Modal } from 'antd';
import dayjs from 'dayjs';

import { getErrorMessage } from '@/lib';
import { permanentAdvancesApi } from '../api';
import type { PermanentAdvanceDto } from '../types';

export type RecordPermanentAdvanceDeductionModalProps = {
  /** The advance the deduction is recorded against — `null` keeps the modal closed. */
  row: PermanentAdvanceDto | null;
  /** Overrides the default "Record deduction — {requestCode}" title. */
  title?: string;
  onClose: () => void;
};

type FormValues = {
  deductedAmount: number;
  transactionDate: dayjs.Dayjs;
  description?: string;
};

export function RecordPermanentAdvanceDeductionModal({
  row,
  title,
  onClose,
}: RecordPermanentAdvanceDeductionModalProps) {
  const [form] = Form.useForm<FormValues>();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      permanentAdvancesApi.recordDeduction(row?.id ?? 0, {
        deductedAmount: values.deductedAmount,
        transactionDate: values.transactionDate.format('YYYY-MM-DD'),
        description: values.description?.trim() || undefined,
      }),
    onSuccess: () => {
      message.success('Deduction recorded');
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'permanent-advances'] });
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  return (
    <Modal
      title={title ?? (row ? `Record deduction — ${row.requestCode ?? `#${row.id}`}` : 'Record deduction')}
      open={row != null}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Record"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Form
        key={row?.id ?? 'none'}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={{ transactionDate: dayjs(), description: '' }}
        onFinish={(values) => mutation.mutate(values)}
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            label="Amount"
            name="deductedAmount"
            rules={[
              { required: true, message: 'Enter the amount' },
              { type: 'number', min: 1, message: 'Must be greater than zero' },
            ]}
          >
            <InputNumber style={{ width: '100%' }} min={0} step={500} addonBefore="Rs" autoFocus />
          </Form.Item>
          <Form.Item
            label="Transaction date"
            name="transactionDate"
            rules={[{ required: true, message: 'Pick the date' }]}
          >
            <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" allowClear={false} />
          </Form.Item>
        </div>
        <Form.Item
          label="Description"
          name="description"
          rules={[{ max: 1024, message: 'Keep it under 1024 characters' }]}
          style={{ marginBottom: 0 }}
        >
          <Input.TextArea
            rows={3}
            placeholder="Defaults to “Manual deduction for {employee code}”"
            maxLength={1024}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

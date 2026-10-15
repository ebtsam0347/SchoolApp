// Add / Edit modals for an effective-dated salary line
// (allowance/earning/deduction). Both share the same pattern: pick from
// the catalog (add only — edit is locked to the existing component),
// amount in rupees, effective-from date, optional notes.

import {
  Alert,
  App as AntdApp,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect } from 'react';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  COMPONENT_CALC_LABEL,
  COMPONENT_TYPE_LABEL,
  COMPONENT_TYPE_TONE,
  employeeSalaryLinesApi,
  type AddSalaryLineRequest,
  type EmployeeSalaryComponentDto,
  type SalaryComponentDto,
} from '@/payroll';
import { StatusPill } from '@/components';
import { salaryTabKey } from './query-keys';

type AddLineForm = {
  componentId: number;
  amountRupees?: number;
  effectiveFrom: Dayjs;
  notes?: string;
};

export function AddLineModal({
  employeeId,
  open,
  onClose,
  catalog,
}: {
  employeeId: number;
  open: boolean;
  onClose: () => void;
  catalog: SalaryComponentDto[];
}) {
  const [form] = Form.useForm<AddLineForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const componentId = Form.useWatch('componentId', form);
  const selectedComponent = catalog.find((c) => c.id === componentId);

  const add = useMutation({
    mutationFn: (v: AddLineForm) => {
      const body: AddSalaryLineRequest = {
        componentId: v.componentId,
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        notes: v.notes?.trim() || undefined,
      };
      if (v.amountRupees != null) body.amount = v.amountRupees;
      return employeeSalaryLinesApi.add(employeeId, body);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: salaryTabKey(employeeId) });
      message.success('Line added.');
      form.resetFields();
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  return (
    <Modal
      title="Add salary linee"
      open={open}
      onCancel={onClose}
      okText="Add"
      confirmLoading={add.isPending}
      onOk={() => form.validateFields().then((v) => add.mutateAsync(v))}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ effectiveFrom: dayjs().startOf('month') }}
      >
        <Form.Item
          name="componentId"
          label="Component"
          rules={[{ required: true }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="Pick from the catalog"
            options={catalog.map((c) => ({
              value: c.id,
              label: `${c.code} — ${c.name}`,
              data: c,
            }))}
          />
        </Form.Item>
        {selectedComponent && (
          <Alert
            style={{ marginBottom: 24 }}
            type={selectedComponent.calculateWithSalary ? 'info' : 'warning'}
            showIcon
            message={
              <Space size={8} wrap>
                <StatusPill tone={COMPONENT_TYPE_TONE[selectedComponent.type]}>
                  {COMPONENT_TYPE_LABEL[selectedComponent.type]}
                </StatusPill>
                <StatusPill tone="neutral">
                  {COMPONENT_CALC_LABEL[selectedComponent.calcType]}
                </StatusPill>
              </Space>
            }
            description={
              selectedComponent.calculateWithSalary
                ? 'Calculated with salary — this line is included in the monthly payroll run.'
                : 'Not calculated with salary — this line is paid separately and is left out of the monthly payroll run.'
            }
          />
        )}
        <Form.Item
          name="amountRupees"
          label={
            selectedComponent?.calcType === 'PER_DAY'
              ? 'Per-day rate (Rs)'
              : 'Monthly amount (Rs)'
          }
          tooltip={
            selectedComponent?.defaultAmount != null
              ? `Catalog default: Rs ${selectedComponent.defaultAmount.toLocaleString()}. Leave blank to use it.`
              : 'Required when the catalog row has no default.'
          }
        >
          <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
        </Form.Item>
        <Form.Item
          name="effectiveFrom"
          label="Effective from"
          rules={[{ required: true }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="notes" label="Notes">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

type EditLineForm = {
  amountRupees: number;
  effectiveFrom: Dayjs;
  notes?: string;
};

export function EditLineModal({
  employeeId,
  line,
  onClose,
}: {
  employeeId: number;
  line: EmployeeSalaryComponentDto | null;
  onClose: () => void;
}) {
  const [form] = Form.useForm<EditLineForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  // Update form values when line changes
  useEffect(() => {
    if (line) {
      form.setFieldsValue({
        amountRupees: line.amount,
        effectiveFrom: dayjs(line.effectiveFrom),
        notes: line.notes ?? undefined,
      });
    }
  }, [line, form]);

  const update = useMutation({
    mutationFn: (v: EditLineForm) =>
      employeeSalaryLinesApi.update(employeeId, line?.id ?? 0, {
        amount: v.amountRupees,
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        notes: v.notes?.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: salaryTabKey(employeeId) });
      message.success('Line updated.');
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  return (
    <Modal
      title={line ? `Edit ${line.componentCode}` : 'Edit'}
      open={line !== null}
      onCancel={onClose}
      okText="Save"
      confirmLoading={update.isPending}
      onOk={() => form.validateFields().then((v) => update.mutateAsync(v))}
      destroyOnHidden
    >
      {line && (
        <Form
          key={line.id}
          form={form}
          layout="vertical"
          initialValues={{
            amountRupees: line.amount,
            effectiveFrom: dayjs(line.effectiveFrom),
            notes: line.notes ?? undefined,
          }}
        >
          <Form.Item
            name="amountRupees"
            label={line.calcType === 'PER_DAY' ? 'Per-day rate (Rs)' : 'Monthly amount (Rs)'}
            rules={[{ required: true }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
          </Form.Item>
          <Form.Item
            name="effectiveFrom"
            label="Effective from"
            rules={[{ required: true }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}

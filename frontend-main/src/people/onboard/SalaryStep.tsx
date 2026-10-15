// Step: salary setup. Basic salary is required (drives every subsequent
// payslip). Allowance / deduction lines and bank-disbursement details
// are still optional — HR can fill them in later from the profile.
//
// Payment mode defaults to Cash — bank fields only appear when the
// operator switches to Bank transfer. The onboarding wire schema doesn't
// carry payment mode; the parent route PATCHes /employees/{id}/payroll-settings
// after the create succeeds.
//
// On the wire:
//   1. Basic salary → SET_BASIC_SALARY saga step
//   2. Component lines (allowances, deductions, income tax) →
//      ASSIGN_SALARY_COMPONENTS
//   3. Bank disbursement details → SET_BANK_DETAILS (skipped for Cash)
//
// Amounts are rupees end-to-end (form, request body, wire).

import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Typography,
  type FormInstance,
} from 'antd';
import { useQuery } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { Section } from '@/components';
import { CURRENCY_SYMBOL } from '@/config/locale';
import {
  banksApi,
  EMPLOYEE_PAYMENT_MODE_LABEL,
  salaryComponentsApi,
} from '@/payroll';
import type { WizardForm } from './types';

const { Text } = Typography;

export type SalaryStepProps = {
  form: FormInstance<WizardForm>;
};

export function SalaryStep({ form }: SalaryStepProps) {
  const paymentMode =
    Form.useWatch('paymentMode', form) ?? 'CASH';
  const isBankTransfer = paymentMode === 'BANK_TRANSFER';

  const componentsQuery = useQuery({
    queryKey: ['salary-components', 'active'],
    queryFn: ({ signal }) => salaryComponentsApi.list({ active: true }, signal),
    staleTime: 5 * 60 * 1000,
  });

  const banksQuery = useQuery({
    queryKey: ['banks', 'catalog'],
    queryFn: ({ signal }) => banksApi.list({}, signal),
    staleTime: 5 * 60 * 1000,
    enabled: isBankTransfer,
  });

  return (
    <Section
      title="Salary"
      subtitle="Monthly basic is required. Allowances and deductions are optional — you can add them now or later from the employee's profile."
    >
      <Alert
        type="info"
        showIcon
        title="Amounts are in whole rupees."
        style={{ marginBottom: 16 }}
      />

      <Form.Item
        name="salaryBasic"
        label="Monthly basic"
        rules={[
          { required: true, message: 'Required' },
          { type: 'number', min: 0, message: "Can't be negative" },
        ]}
      >
        <InputNumber
          min={0}
          step={1000}
          prefix={CURRENCY_SYMBOL}
          style={{ width: 240 }}
        />
      </Form.Item>

      <Section title="Allowances & deductions">
        <Form.List name="salaryComponents">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <RowGrid key={field.key} columns="1.6fr 1.2fr 1.4fr auto">
                  <Form.Item
                    {...field}
                    name={[field.name, 'componentId']}
                    style={{ marginBottom: 0 }}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="Component"
                      loading={componentsQuery.isLoading}
                      options={(componentsQuery.data ?? []).map((c) => ({
                        value: c.id,
                        label: `${c.name} · ${c.type === 'EARNING' ? '+' : '−'}${
                          c.calcType === 'PER_DAY' ? ' (per day)' : ''
                        }`,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'amount']}
                    style={{ marginBottom: 0 }}
                  >
                    <InputNumber
                      min={0}
                      step={500}
                      placeholder="Catalog default"
                      prefix={CURRENCY_SYMBOL}
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'notes']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Notes" />
                  </Form.Item>
                  <RemoveButton onClick={() => remove(field.name)} />
                </RowGrid>
              ))}
              <Button
                type="dashed"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => add({})}
                style={{ width: '100%' }}
                disabled={componentsQuery.isLoading}
              >
                Add allowance or deduction
              </Button>
            </>
          )}
        </Form.List>
      </Section>

      <Section
        title="Payment"
        subtitle="How this employee gets paid. Switch to Bank transfer if you have their bank details handy — otherwise leave it on Cash and HR can add bank details later from the profile."
      >
        <Form.Item name="paymentMode" initialValue="CASH" style={{ marginBottom: 16 }}>
          <Radio.Group optionType="button" buttonStyle="solid">
            <Radio.Button value="CASH">
              {EMPLOYEE_PAYMENT_MODE_LABEL.CASH}
            </Radio.Button>
            <Radio.Button value="BANK_TRANSFER">
              {EMPLOYEE_PAYMENT_MODE_LABEL.BANK_TRANSFER}
            </Radio.Button>
          </Radio.Group>
        </Form.Item>

        {isBankTransfer ? (
          <>
            <RowGrid columns="1fr 1fr">
              <Form.Item
                name={['salaryBank', 'bankId']}
                label="Bank"
                rules={[{ required: true, message: 'Required for bank transfer' }]}
                extra={
                  banksQuery.isError ? (
                    <Text type="danger" style={{ fontSize: 12 }}>
                      Couldn't load banks — switch to Cash for now and HR
                      can add bank details later.
                    </Text>
                  ) : undefined
                }
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  allowClear
                  placeholder="Bank"
                  loading={banksQuery.isLoading}
                  options={(banksQuery.data ?? [])
                    .filter((b) => b.active)
                    .map((b) => ({ value: b.id, label: b.name }))}
                />
              </Form.Item>
              <Form.Item name={['salaryBank', 'branch']} label="Branch">
                <Input placeholder="Sialkot Main" />
              </Form.Item>
            </RowGrid>
            <Form.Item
              name={['salaryBank', 'accountTitle']}
              label="Account title"
              rules={[{ required: true, message: 'Required for bank transfer' }]}
            >
              <Input placeholder="As printed on the bank statement" />
            </Form.Item>
            <RowGrid columns="1.5fr 1fr">
              <Form.Item
                name={['salaryBank', 'iban']}
                label="IBAN"
                rules={[
                  { required: true, message: 'Required for bank transfer' },
                  {
                    validator(_, value) {
                      if (!value) return Promise.resolve();
                      return /^PK\d{2}[A-Z0-9]{20}$/i.test(value)
                        ? Promise.resolve()
                        : Promise.reject(
                            new Error(
                              '24-char Pakistan IBAN, e.g. PK36SCBL0000001123456702',
                            ),
                          );
                    },
                  },
                ]}
              >
                <Input placeholder="PK36SCBL0000001123456702" />
              </Form.Item>
              <Form.Item name={['salaryBank', 'accountNumber']} label="Account number">
                <Input placeholder="Optional" />
              </Form.Item>
            </RowGrid>
          </>
        ) : null}
      </Section>
    </Section>
  );
}

function RowGrid({
  columns,
  children,
}: {
  columns: string;
  children: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: columns,
        gap: 8,
        alignItems: 'flex-start',
        padding: '4px 0',
      }}
    >
      {children}
    </div>
  );
}

function RemoveButton({ onClick }: { onClick: () => void }) {
  return (
    <Button
      type="text"
      danger
      title="Remove row"
      icon={<Trash2 size={14} strokeWidth={1.75} />}
      onClick={onClick}
    />
  );
}

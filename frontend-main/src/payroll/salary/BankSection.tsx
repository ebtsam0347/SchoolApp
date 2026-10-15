// Bank-details panel with inline edit/add modal. Required for
// bank-transfer employees; optional (and removable) for cash. The IBAN
// follows the Pakistan format (PK + 22 chars).

import { useState } from 'react';
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Banknote, Building2, Edit3, Trash2 } from 'lucide-react';
import { Can } from '@/auth';
import { EmptyState, KpiGrid, Section, StatCard } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { banksApi, employeeBankDetailsApi } from '@/payroll';
import { salaryTabKey, useSalaryTab } from './query-keys';

type BankForm = {
  bankId: number;
  branch?: string;
  accountTitle: string;
  iban: string;
  accountNumber?: string;
};

export function BankSection({ employeeId }: { employeeId: number }) {
  const [openEdit, setOpenEdit] = useState(false);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const tabQuery = useSalaryTab(employeeId);
  const [form] = Form.useForm<BankForm>();

  const banksQuery = useQuery({
    queryKey: ['banks', 'catalog'],
    queryFn: ({ signal }) => banksApi.list({}, signal),
    staleTime: 5 * 60 * 1000,
    enabled: openEdit,
    select: (rows) => rows.filter((b) => b.active),
  });

  const upsert = useMutation({
    mutationFn: (v: BankForm) =>
      employeeBankDetailsApi.upsert(employeeId, {
        bankId: v.bankId,
        branch: v.branch?.trim() || undefined,
        accountTitle: v.accountTitle.trim(),
        iban: v.iban.trim().toUpperCase(),
        accountNumber: v.accountNumber?.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: salaryTabKey(employeeId) });
      message.success('Bank details saved.');
      setOpenEdit(false);
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  const remove = useMutation({
    mutationFn: () => employeeBankDetailsApi.remove(employeeId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: salaryTabKey(employeeId) });
      message.success('Bank details removed.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const bank = tabQuery.data?.bankDetails ?? null;
  const isCash = tabQuery.data?.payrollSettings?.paymentMode === 'CASH';

  return (
    <Section
      title="Bank details"
      subtitle={
        isCash
          ? 'Optional for cash employees. If recorded, they sit in payroll storage encrypted.'
          : 'Required for bank-transfer employees. Account number is encrypted at rest.'
      }
      actions={
        <Can I="write" a="payroll">
          <Space>
            <Button
              icon={<Edit3 size={14} strokeWidth={1.75} />}
              onClick={() => {
                form.setFieldsValue(
                  bank
                    ? {
                        bankId: bank.bankId,
                        branch: bank.branch ?? undefined,
                        accountTitle: bank.accountTitle,
                        iban: bank.iban,
                        accountNumber: bank.accountNumber ?? undefined,
                      }
                    : {},
                );
                setOpenEdit(true);
              }}
            >
              {bank ? 'Edit' : 'Add'}
            </Button>
            {bank && isCash && (
              <Popconfirm
                title="Remove bank details? Allowed only when payment mode is cash."
                okText="Remove"
                okButtonProps={{ danger: true }}
                onConfirm={() => remove.mutateAsync()}
              >
                <Button danger icon={<Trash2 size={14} strokeWidth={1.75} />}>
                  Remove
                </Button>
              </Popconfirm>
            )}
          </Space>
        </Can>
      }
    >
      {!bank ? (
        <EmptyState
          icon={Banknote}
          title="No bank details on file"
          description={
            isCash
              ? "This employee is on cash payment — bank details aren't required."
              : 'Add bank details before the next payroll run; the compute step will fail otherwise.'
          }
        />
      ) : (
        <KpiGrid>
          <StatCard label="Bank" value={bank.bankName} tone="info" icon={Building2} />
          {bank.branch && <StatCard label="Branch" value={bank.branch} tone="neutral" />}
          <StatCard label="Account title" value={bank.accountTitle} tone="neutral" />
          <StatCard
            label="IBAN"
            value={
              bank.iban === 'LEGACY_PENDING' ? (
                <span style={{ color: 'var(--ant-color-warning)' }}>Not on file</span>
              ) : (
                <span style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
                  {bank.iban}
                </span>
              )
            }
            tone={bank.iban === 'LEGACY_PENDING' ? 'warning' : 'neutral'}
          />
          {bank.accountNumber && (
            <StatCard
              label="Account #"
              value={
                <span style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
                  {bank.accountNumber}
                </span>
              }
              tone="neutral"
            />
          )}
        </KpiGrid>
      )}

      <Modal
        title={bank ? 'Edit bank details' : 'Add bank details'}
        open={openEdit}
        onCancel={() => setOpenEdit(false)}
        okText="Save"
        confirmLoading={upsert.isPending}
        onOk={() => form.validateFields().then((v) => upsert.mutateAsync(v))}
        destroyOnHidden
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="bankId" label="Bank" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Pick from the catalog"
              loading={banksQuery.isLoading}
              options={(banksQuery.data ?? []).map((b) => ({
                value: b.id,
                label: `${b.code} — ${b.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="branch" label="Branch">
            <Input />
          </Form.Item>
          <Form.Item
            name="accountTitle"
            label="Account title"
            rules={[{ required: true, max: 128 }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="iban"
            label="IBAN"
            rules={[
              { required: true },
              { pattern: /^PK\d{2}[A-Z0-9]{20}$/i, message: 'Pakistan IBAN: PK + 22 chars.' },
            ]}
          >
            <Input
              placeholder="PK36SCBL0000001123456702"
              style={{ fontFamily: 'ui-monospace' }}
            />
          </Form.Item>
          <Form.Item
            name="accountNumber"
            label="Account number"
            tooltip="Encrypted at rest. Visible only to HR and payroll roles."
          >
            <Input style={{ fontFamily: 'ui-monospace' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Section>
  );
}

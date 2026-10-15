// EOBI enrollment + payment mode toggles. Hire date is read-only (it's
// effectively immutable; correcting it requires HR re-onboarding).

import { Alert, App as AntdApp, Select, Space, Switch, Typography } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Can } from '@/auth';
import { Section } from '@/components';
import { getErrorMessage } from '@/lib';
import {
  EMPLOYEE_PAYMENT_MODE_LABEL,
  employeePayrollSettingsApi,
  formatDateShort,
  type EmployeePaymentMode,
} from '@/payroll';
import { salaryTabKey, useSalaryTab } from './query-keys';

const { Text } = Typography;

export function SettingsSection({ employeeId }: { employeeId: number }) {
  const tabQuery = useSalaryTab(employeeId);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const update = useMutation({
    mutationFn: (body: { eobiEnrolled?: boolean; paymentMode?: EmployeePaymentMode }) =>
      employeePayrollSettingsApi.update(employeeId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: salaryTabKey(employeeId) });
      message.success('Saved.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const settings = tabQuery.data?.payrollSettings ?? null;

  return (
    <Section
      title="Payroll settings"
      subtitle="EOBI enrollment and payment mode. Bank details are required when payment mode is bank transfer."
    >
      {tabQuery.isLoading ? null : !settings ? (
        <Alert
          type="warning"
          showIcon
          title="No payroll settings on file"
          description="A skeleton record is created automatically when the employee is onboarded."
        />
      ) : (
        <Space wrap size={24}>
          <Can I="write" a="payroll">
            <Space>
              <Switch
                checked={settings.eobiEnrolled}
                onChange={(v) => update.mutateAsync({ eobiEnrolled: v })}
                loading={update.isPending}
              />
              <Text>EOBI enrolled</Text>
            </Space>
          </Can>
          <Space>
            <Text type="secondary">Payment mode:</Text>
            <Can I="write" a="payroll">
              <Select<EmployeePaymentMode>
                value={settings.paymentMode as EmployeePaymentMode}
                onChange={(v) => update.mutateAsync({ paymentMode: v })}
                style={{ minWidth: 180 }}
                options={[
                  {
                    label: EMPLOYEE_PAYMENT_MODE_LABEL.BANK_TRANSFER,
                    value: 'BANK_TRANSFER',
                  },
                  { label: EMPLOYEE_PAYMENT_MODE_LABEL.CASH, value: 'CASH' },
                ]}
              />
            </Can>
          </Space>
          <Space>
            <Text type="secondary">Hire date:</Text>
            <Text>{formatDateShort(settings.hireDate)}</Text>
          </Space>
        </Space>
      )}
    </Section>
  );
}

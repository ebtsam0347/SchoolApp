
import { getErrorMessage } from '@/lib';
import {
  BankSection,
  BasicSection,
  ComponentsSection,
  GratuitySection,
  OffCycleSection,
  SettingsSection,
  useSalaryTab,
} from '@/payroll/salary';
import { createFileRoute, useParams } from '@tanstack/react-router';
import { Alert } from 'antd';

export const Route = createFileRoute('/_app/people/$id/salary')({
  component: SalaryTab,
});

function SalaryTab() {
  const { id: idParam } = useParams({ from: '/_app/people/$id/salary' });
  const employeeId = Number(idParam);

  const tabQuery = useSalaryTab(employeeId);

  if (tabQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        title="Couldn't load salary tab"
        description={getErrorMessage(tabQuery.error)}
      />
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
      }}
    >
      <BasicSection employeeId={employeeId} />
      <ComponentsSection employeeId={employeeId} />
      <SettingsSection employeeId={employeeId} />
      <BankSection employeeId={employeeId} />
      <GratuitySection employeeId={employeeId} />
      <OffCycleSection employeeId={employeeId} />
    </div>
  );
}

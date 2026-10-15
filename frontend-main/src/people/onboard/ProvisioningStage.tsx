// Saga-progress mode for the onboarding wizard. After the employee row
// (and per-row arrays — qualifications, skills, etc.) lands in PG, the
// wizard pivots here so the operator can watch the cross-service work
// run: ESS provisioning, then payroll setup (basic salary → component
// lines → bank), then attendance shift assignment. Each step is
// independent and skipped when its section was omitted from the
// kickoff payload. Failures don't roll back the employee record — HR
// can finish the missing pieces by hand from the detail page or hit
// Retry to re-arm a transient failure.
//
// User-visible copy avoids saga vocabulary; lastError is shown verbatim
// because operators need it to debug a stuck step.

import { Alert, App, Button, Space, Typography } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { PageHeader, SagaProgress, type SagaProgressStep } from '@/components';
import { sagasApi, type EmployeeDto } from '@/people';
import { getErrorMessage } from '@/lib';

const { Text } = Typography;

// Onboarding saga step labels — server uses these as `currentStep`
// values (OnboardingSaga.java). Steps run in order; each one no-ops
// when its section wasn't provided in the kickoff payload, so the
// progress UI shows them sequentially regardless of skipped sections.
const SAGA_STEPS: SagaProgressStep[] = [
  { key: 'PROVISION_ESS', label: 'Provision ESS access' },
  { key: 'SET_BASIC_SALARY', label: 'Set basic salary' },
  { key: 'ASSIGN_SALARY_COMPONENTS', label: 'Assign allowances & deductions' },
  { key: 'SET_BANK_DETAILS', label: 'Save bank details' },
  { key: 'ASSIGN_SHIFT', label: 'Assign shift' },
];

export type ProvisioningStageProps = {
  employee: EmployeeDto;
  sagaId: number;
  onDone: (employeeId: number) => void;
  onCancel: () => void;
  onOnboardAnother?: () => void;
};

export function ProvisioningStage({
  employee,
  sagaId,
  onDone,
  onCancel,
  onOnboardAnother,
}: ProvisioningStageProps) {
  const { message } = App.useApp();

  const sagaQuery = useQuery({
    queryKey: ['sagas', sagaId],
    queryFn: ({ signal }) => sagasApi.get(sagaId, signal),
    // Poll every 1.5s while RUNNING; stop once terminal.
    refetchInterval: (q) => {
      const status = q.state.data?.status;
      return status === 'RUNNING' || status === undefined ? 1500 : false;
    },
  });

  const retryMutation = useMutation({
    mutationFn: () => sagasApi.retry(sagaId),
    onSuccess: () => {
      message.success('Re-armed the failed step. Re-running…');
      void sagaQuery.refetch();
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const status = sagaQuery.data?.status;
  const completed = status === 'COMPLETED';
  const failed = status === 'FAILED';

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
      }}
    >
      <PageHeader
        eyebrow="Onboarding"
        title={
          completed
            ? `${employee.fullName} added`
            : failed
              ? `${employee.fullName} added — some setup needs attention`
              : `Setting up ${employee.fullName}…`
        }
        description={
          completed
            ? `${employee.employeeCode} is live. Salary, attendance, and login were set up successfully.`
            : failed
              ? `${employee.employeeCode} is in the directory. The steps below didn't finish — retry or finish them manually from the profile.`
              : `${employee.employeeCode} is in the directory. Finishing salary, attendance, and login setup — usually takes a few seconds.`
        }
        actions={
          <Space>
            {onOnboardAnother && completed ? (
              <Button onClick={onOnboardAnother}>Onboard another</Button>
            ) : null}
            <Button onClick={onCancel}>Open profile</Button>
            {completed && employee.id ? (
              <Button type="primary" onClick={() => onDone(employee.id!)}>
                Continue to profile
              </Button>
            ) : null}
          </Space>
        }
      />

      {sagaQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load provisioning status"
          description={getErrorMessage(sagaQuery.error)}
        />
      ) : null}

      <SagaProgress
        status={status ?? 'RUNNING'}
        currentStep={sagaQuery.data?.currentStep}
        steps={SAGA_STEPS}
        attemptCount={sagaQuery.data?.attemptCount}
        lastError={sagaQuery.data?.lastError}
        actions={
          failed ? (
            <Button
              type="primary"
              danger
              loading={retryMutation.isPending}
              onClick={() => retryMutation.mutate()}
            >
              Retry failed step
            </Button>
          ) : completed && employee.id ? (
            <Button type="primary" onClick={() => onDone(employee.id!)}>
              Continue
            </Button>
          ) : null
        }
      />

      <Text type="secondary" style={{ fontSize: 12 }}>
        Each step runs independently. The employee record is already saved —
        if a step doesn't finish, retry it here or fix it later from the
        employee's profile.
      </Text>
    </div>
  );
}

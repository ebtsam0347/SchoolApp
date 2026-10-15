// /people/onboard — onboarding wizard.
//
// Single composite read on mount: GET /employees/onboarding-lookups
// (departments, sections, designations, grades, provinces, cities,
// qualifications, skills). One AntD Form drives all five steps so
// validation, state, and the final submit body share one shape. ESS
// provisioning is gated by the caller's `auth.users.write` ability — the
// roles catalog is no longer part of the bundle.
//
// On submit: POST /employees → if ESS provisioning fired off, pivot to
// the saga-progress mode; otherwise jump to /people/{newId}. ESS is
// best-effort per backend status (failures don't roll back the row).
//
// Step bodies, the saga-progress stage, the toRequest mapper, and the
// shared types live in src/people/onboard/.
//
// User-visible copy in this file (page subtitle, footer note, error / confirm
// messages) avoids dev vocabulary — see CLAUDE.md "UI copy".

import { useContext, useState } from 'react';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Skeleton,
  Steps,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  Briefcase,
  CheckCircle2,
  IdCard,
  UserCog,
  Wallet,
} from 'lucide-react';
import dayjs from 'dayjs';

import { AbilityContext } from '@/auth';
import { PageHeader } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { employeesApi, peopleBff, type EmployeeDto } from '@/people';
import { employeePayrollSettingsApi } from '@/payroll';
import {
  BackgroundStep,
  ComplianceStep,
  EmploymentStep,
  EssStep,
  IdentityStep,
  ProbationStep,
  ProvisioningStage,
  ReviewSummary,
  SalaryStep,
  ShiftStep,
  SkillsStep,
  STEP_FIELDS,
  toRequest,
  type WizardForm,
  type WizardStep,
} from '@/people/onboard';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/people/onboard')({
  component: OnboardWizard,
});

const STEPS: readonly WizardStep[] = [
  { key: 'identity', title: 'Identity', icon: <IdCard size={14} /> },
  { key: 'org-placement', title: 'Org placement', icon: <Briefcase size={14} /> },
  { key: 'profile', title: 'Profile (optional)', icon: <UserCog size={14} /> },
  { key: 'pay-and-login', title: 'Pay & login', icon: <Wallet size={14} /> },
  { key: 'review', title: 'Review', icon: <CheckCircle2 size={14} /> },
] as const;

// Form initialValues set joiningDate, employmentType, probationDays,
// provisionEss, paymentMode — those are seeded by us, not user input, so
// the cancel guard ignores them when deciding whether to confirm.
const SEEDED_KEYS = new Set<keyof WizardForm>([
  'joiningDate',
  'employmentType',
  'probationDays',
  'provisionEss',
  'paymentMode',
]);

type SubmittedSummary = {
  joiningDate?: string;
  salarySet: boolean;
  shiftSet: boolean;
  essProvisioned: boolean;
};

function buildSummary(v: WizardForm): SubmittedSummary {
  return {
    joiningDate: v.joiningDate?.format('YYYY-MM-DD'),
    salarySet: !!v.salaryBasic,
    shiftSet: !!v.shiftId,
    essProvisioned: !!v.provisionEss,
  };
}

function hasMeaningfulInput(v: WizardForm): boolean {
  for (const [key, value] of Object.entries(v)) {
    if (SEEDED_KEYS.has(key as keyof WizardForm)) continue;
    if (value === undefined || value === null) continue;
    if (typeof value === 'string' && value.trim() === '') continue;
    if (Array.isArray(value) && value.length === 0) continue;
    return true;
  }
  return false;
}

function OnboardWizard() {
  const { token } = useToken();
  const navigate = useNavigate();
  const { message, modal } = App.useApp();
  const [stepIdx, setStepIdx] = useState(0);
  const [maxStepReached, setMaxStepReached] = useState(0);
  const [topLevel, setTopLevel] = useState<string | null>(null);
  const [managerSearch, setManagerSearch] = useState('');
  /** When set, the wizard is in saga-progress mode (form hidden). */
  const [provisioning, setProvisioning] = useState<{
    employee: EmployeeDto;
    sagaId: number;
  } | null>(null);
  /** When set, submission succeeded without ESS provisioning — show the
   *  success stage with follow-up CTAs instead of navigating immediately. */
  const [submitted, setSubmitted] = useState<{
    employee: EmployeeDto;
    summary: SubmittedSummary;
  } | null>(null);

  const ability = useContext(AbilityContext);
  const canProvisionEss = ability.can('write', 'auth.users');

  const lookupsQuery = useQuery({
    queryKey: ['onboarding', 'lookups'],
    queryFn: ({ signal }) => peopleBff.onboardingLookups(signal),
    staleTime: 5 * 60 * 1000,
  });

  const [form] = Form.useForm<WizardForm>();
  const managerDepartmentId = Form.useWatch('departmentId', form);
  const managerSectionId = Form.useWatch('sectionId', form);

  // Manager candidates are scoped to the department/section picked above —
  // narrowed further by name/code search once typed.
  const managerQuery = useQuery({
    queryKey: [
      'employees',
      'manager-search',
      managerDepartmentId,
      managerSectionId,
      managerSearch,
    ],
    queryFn: ({ signal }) =>
      employeesApi.list(
        {
          departmentId: managerDepartmentId,
          sectionId: managerSectionId,
          q: managerSearch || undefined,
          status: 'ACTIVE',
          size: 20,
        },
        signal,
      ),
    enabled: managerDepartmentId != null,
    staleTime: 30_000,
  });

  const lookups = lookupsQuery.data;

  const mutation = useMutation({
    mutationFn: (body: ReturnType<typeof toRequest>) => employeesApi.create(body),
    onSuccess: (response) => {
      // Three paths:
      //   1. ESS provisioning fired off — pivot to saga-progress mode so
      //      the operator can watch RUNNING → COMPLETED / FAILED.
      //   2. No saga, employee created — show the success stage with
      //      follow-up CTAs (View profile / Onboard another / Directory).
      //   3. No employee in the response (defensive) — fall back to a
      //      toast + directory navigate.
      const employee = response.employee;
      const v = form.getFieldsValue(true) as WizardForm;
      // Backend defaults paymentMode to BANK_TRANSFER. Push the operator's
      // choice (CASH by default in the wizard) — fire-and-forget so a
      // settings hiccup doesn't block the success UI.
      if (employee?.id && v.paymentMode) {
        void employeePayrollSettingsApi
          .update(employee.id, { paymentMode: v.paymentMode })
          .catch(() => {});
      }
      if (response.sagaId && employee?.id) {
        setProvisioning({ employee, sagaId: response.sagaId });
        return;
      }
      if (employee?.id) {
        setSubmitted({ employee, summary: buildSummary(v) });
        return;
      }
      message.success('Employee onboarded.');
      navigate({ to: '/people/directory' });
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setTopLevel(getErrorMessage(e));
      }
    },
  });

  async function handleNext() {
    const fields = STEP_FIELDS[STEPS[stepIdx].key];
    if (fields.length > 0) {
      try {
        await form.validateFields(fields as string[]);
      } catch (err) {
        const first = (err as { errorFields?: { name: (string | number)[] }[] })
          ?.errorFields?.[0]?.name;
        if (first) form.scrollToField(first, { behavior: 'smooth', block: 'center' });
        return;
      }
    }
    setStepIdx((i) => {
      const next = Math.min(i + 1, STEPS.length - 1);
      setMaxStepReached((m) => Math.max(m, next));
      return next;
    });
  }

  function handlePrev() {
    setStepIdx((i) => Math.max(0, i - 1));
  }

  function handleStepJump(target: number) {
    if (target === stepIdx) return;
    if (target < stepIdx || target <= maxStepReached) {
      setStepIdx(target);
    }
  }

  function handleSubmit() {
    const values = form.getFieldsValue(true) as WizardForm;
    setTopLevel(null);
    mutation.mutate(toRequest(values));
  }

  function handleCancel() {
    if (!hasMeaningfulInput(form.getFieldsValue(true) as WizardForm)) {
      navigate({ to: '/people/directory' });
      return;
    }
    modal.confirm({
      title: 'Discard this onboarding?',
      content:
        "You'll lose what you've entered so far. The new employee won't be created.",
      okText: 'Discard',
      okButtonProps: { danger: true },
      cancelText: 'Keep editing',
      onOk: () => navigate({ to: '/people/directory' }),
    });
  }

  if (lookupsQuery.isLoading) {
    return <Skeleton active paragraph={{ rows: 8 }} />;
  }

  if (lookupsQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        title="Couldn't load onboarding lookups"
        description={getErrorMessage(lookupsQuery.error)}
      />
    );
  }

  // Saga-progress mode: short-circuits the rest of the wizard.
  if (provisioning) {
    return (
      <ProvisioningStage
        employee={provisioning.employee}
        sagaId={provisioning.sagaId}
        onDone={(employeeId) =>
          navigate({ to: '/people/$id', params: { id: String(employeeId) } })
        }
        onCancel={() =>
          navigate({
            to: '/people/$id',
            params: { id: String(provisioning.employee.id) },
          })
        }
        onOnboardAnother={() => {
          form.resetFields();
          // resetFields() restores AntD's captured initialValues, where
          // joiningDate is whatever dayjs() returned at first mount. Stamp
          // a fresh "today" so a long-lived wizard tab still defaults the
          // next hire to the current day.
          form.setFieldsValue({ joiningDate: dayjs() });
          setStepIdx(0);
          setMaxStepReached(0);
          setTopLevel(null);
          setProvisioning(null);
        }}
      />
    );
  }

  // Success stage: shown after a non-saga create. The saga-progress
  // stage already has its own success copy, so this branch is only for
  // the no-ESS path.
  if (submitted) {
    return (
      <SuccessStage
        employee={submitted.employee}
        summary={submitted.summary}
        onViewProfile={() =>
          navigate({
            to: '/people/$id',
            params: { id: String(submitted.employee.id) },
          })
        }
        onOnboardAnother={() => {
          form.resetFields();
          // resetFields() restores AntD's captured initialValues, where
          // joiningDate is whatever dayjs() returned at first mount. Stamp
          // a fresh "today" so a long-lived wizard tab still defaults the
          // next hire to the current day.
          form.setFieldsValue({ joiningDate: dayjs() });
          setStepIdx(0);
          setMaxStepReached(0);
          setTopLevel(null);
          setSubmitted(null);
        }}
        onBackToDirectory={() => navigate({ to: '/people/directory' })}
      />
    );
  }

  const currentStep = STEPS[stepIdx];
  const isReview = currentStep.key === 'review';

  return (
    <div
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-lg)' }}
    >
      <PageHeader
        eyebrow="People"
        title="Onboard employee"
        description="Add a new hire. Required fields are marked * — you can edit anything later from the employee's profile."
        actions={<Button onClick={handleCancel}>Cancel</Button>}
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(220px, 280px) 1fr',
          gap: 'var(--app-space-lg)',
          alignItems: 'flex-start',
        }}
      >
        <Card>
          <Steps
            current={stepIdx}
            onChange={handleStepJump}
            orientation="vertical"
            size="small"
            items={STEPS.map((s, i) => ({
              title: s.title,
              icon: s.icon,
              status: i === stepIdx ? 'process' : i < stepIdx ? 'finish' : 'wait',
              disabled: i > maxStepReached,
            }))}
          />
        </Card>

        <Card>
          {topLevel ? (
            <Alert
              type="error"
              showIcon
              style={{ marginBottom: 16 }}
              title={topLevel}
              closable
              onClose={() => setTopLevel(null)}
            />
          ) : null}

          <Form
            form={form}
            layout="vertical"
            initialValues={{
              joiningDate: dayjs(),
              employmentType: 'PERMANENT',
              probationDays: 90,
              provisionEss: true,
              paymentMode: 'CASH',
            }}
            preserve
          >
            {currentStep.key === 'identity' ? (
              <IdentityStep lookups={lookups} form={form} />
            ) : null}
            {currentStep.key === 'org-placement' ? (
              <>
                <EmploymentStep
                  lookups={lookups}
                  form={form}
                  managerSearch={managerSearch}
                  setManagerSearch={setManagerSearch}
                  managerQuery={managerQuery}
                />
                <ProbationStep />
                <ShiftStep />
              </>
            ) : null}
            {currentStep.key === 'profile' ? (
              <>
                <BackgroundStep lookups={lookups} />
                <SkillsStep lookups={lookups} />
                <ComplianceStep />
              </>
            ) : null}
            {currentStep.key === 'pay-and-login' ? (
              <>
                <SalaryStep form={form} />
                <EssStep canProvisionEss={canProvisionEss} form={form} />
              </>
            ) : null}
            {isReview ? (
              <ReviewSummary
                form={form}
                lookups={lookups}
                onEdit={(stepKey) => {
                  const idx = STEPS.findIndex((s) => s.key === stepKey);
                  if (idx >= 0) setStepIdx(idx);
                }}
              />
            ) : null}
          </Form>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              marginTop: 'var(--app-space-lg)',
            }}
          >
            <Button onClick={handlePrev} disabled={stepIdx === 0}>
              Back
            </Button>
            {isReview ? (
              <Button
                type="primary"
                loading={mutation.isPending}
                onClick={handleSubmit}
              >
                Submit onboarding
              </Button>
            ) : (
              <Button type="primary" onClick={handleNext}>
                Next
              </Button>
            )}
          </div>
        </Card>
      </div>

      <Text type="secondary" style={{ fontSize: 12, color: token.colorTextTertiary }}>
        Submitting creates the employee, attendance, and payroll records. If
        the self-service login can't be set up, the employee is still saved
        and you can invite them later from their profile.
      </Text>
    </div>
  );
}

function SuccessStage({
  employee,
  summary,
  onViewProfile,
  onOnboardAnother,
  onBackToDirectory,
}: {
  employee: EmployeeDto;
  summary: SubmittedSummary;
  onViewProfile: () => void;
  onOnboardAnother: () => void;
  onBackToDirectory: () => void;
}) {
  const { token } = useToken();
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        padding: 'var(--app-space-lg) 0',
      }}
    >
      <Card style={{ maxWidth: 560, width: '100%' }}>
        <div style={{ textAlign: 'center', padding: '8px 8px 16px' }}>
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: '50%',
              margin: '0 auto 16px',
              background: token.colorSuccessBg,
              color: token.colorSuccess,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <CheckCircle2 size={28} strokeWidth={1.75} />
          </div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {employee.fullName} added
          </Typography.Title>
          <Typography.Text type="secondary">
            {employee.employeeCode ? `${employee.employeeCode} · ` : ''}
            Joining {summary.joiningDate ?? 'TBD'}
          </Typography.Text>
        </div>

        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            gap: 8,
            flexWrap: 'wrap',
            marginBottom: 20,
          }}
        >
          <SummaryChip label="Salary" set={summary.salarySet} />
          <SummaryChip label="Shift" set={summary.shiftSet} />
          <SummaryChip
            label="Self-service login"
            set={summary.essProvisioned}
            unsetLabel="invite later"
          />
        </div>

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <Button type="primary" size="large" onClick={onViewProfile}>
            View {employee.fullName?.split(' ')[0] ?? 'profile'}'s profile
          </Button>
          <Button size="large" onClick={onOnboardAnother}>
            Onboard another employee
          </Button>
          <Button type="text" onClick={onBackToDirectory}>
            Back to directory
          </Button>
        </div>
      </Card>
    </div>
  );
}

function SummaryChip({
  label,
  set,
  unsetLabel = 'set later',
}: {
  label: string;
  set: boolean;
  unsetLabel?: string;
}) {
  const { token } = useToken();
  return (
    <span
      style={{
        fontSize: 12,
        padding: '4px 10px',
        borderRadius: 999,
        background: set ? token.colorSuccessBg : token.colorFillTertiary,
        color: set ? token.colorSuccess : token.colorTextTertiary,
        border: `1px solid ${set ? token.colorSuccessBorder : token.colorBorderSecondary}`,
      }}
    >
      {label}: {set ? 'set' : unsetLabel}
    </span>
  );
}

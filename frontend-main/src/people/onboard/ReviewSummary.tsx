// Last step: read-only review of the wizard's current values, with
// catalog labels resolved from the lookups payload (so we show
// "Engineering" rather than `departmentId: 4`). Each subsection has an
// Edit action that jumps the parent wizard back to the relevant step.

import { Button, Tag, Typography, type FormInstance } from 'antd';
import { Pencil } from 'lucide-react';
import { Section } from '@/components';
import { CURRENCY_SYMBOL } from '@/config/locale';
import {
  EMPLOYMENT_TYPE_LABEL,
  type OnboardingLookups,
} from '@/people';
import { EMPLOYEE_PAYMENT_MODE_LABEL } from '@/payroll';
import type { WizardForm, WizardStepKey } from './types';

const { Text } = Typography;

export type ReviewSummaryProps = {
  form: FormInstance<WizardForm>;
  lookups: OnboardingLookups | undefined;
  onEdit: (step: WizardStepKey) => void;
};

export function ReviewSummary({ form, lookups, onEdit }: ReviewSummaryProps) {
  const v = form.getFieldsValue(true) as WizardForm;
  const dep = lookups?.departments?.find((d) => d.id === v.departmentId);
  const sec = lookups?.sections?.find((s) => s.id === v.sectionId);
  const desig = lookups?.designations?.find((d) => d.id === v.designationId);
  const grade = lookups?.grades?.find((g) => g.id === v.gradeId);
  const city = lookups?.cities?.find((c) => c.id === v.cityId);

  const qualCount = countNonEmpty(v.qualifications, (r) => !!r?.qualificationId);
  const expCount = countNonEmpty(v.experiences, (r) => !!r?.employerName);
  const skillCount = countNonEmpty(v.skills, (r) => !!r?.skillId);
  const certCount = countNonEmpty(v.certifications, (r) => !!r?.name);
  const ecCount = countNonEmpty(v.emergencyContacts, (r) => !!r?.name && !!r?.phone);
  const disCount = countNonEmpty(v.disabilities, (r) => !!r?.disabilityId);

  return (
    <Section
      title="Review"
      subtitle="Submit to create the employee. Click Edit on any section to jump back."
    >
      <Section
        title="Identity"
        level={5}
        actions={<EditAction onClick={() => onEdit('identity')} />}
      >
        <Row label="Full name" value={v.fullName} />
        <Row label="CNIC" value={v.cnic} />
        <Row
          label="Date of birth"
          value={v.dateOfBirth?.format('YYYY-MM-DD')}
        />
        <Row label="Gender" value={v.gender ? prettyEnum(v.gender) : null} />
        <Row label="Phone" value={v.phone} />
        <Row label="Work email" value={v.workEmail} />
        <Row label="Father's name" value={v.fatherName} />
        <Row label="Religion" value={v.religion} />
        <Row label="Permanent address" value={v.permanentAddress} />
        <Row label="City" value={city?.name} />
      </Section>

      <Section
        title="Employment"
        level={5}
        actions={<EditAction onClick={() => onEdit('org-placement')} />}
      >
        <Row label="Joining" value={v.joiningDate?.format('YYYY-MM-DD')} />
        <Row
          label="Employment type"
          value={v.employmentType ? EMPLOYMENT_TYPE_LABEL[v.employmentType] : null}
        />
        <Row label="Department" value={dep?.name} />
        <Row label="Section" value={sec?.name} />
        <Row label="Designation" value={desig?.title} />
        <Row label="Grade" value={grade?.name} />
        <Row
          label="Manager"
          value={v.managerId ? (v.managerLabel ?? `#${v.managerId}`) : null}
        />
      </Section>

      <Section
        title="Contract"
        level={5}
        actions={<EditAction onClick={() => onEdit('org-placement')} />}
      >
        <Row
          label="Probation"
          value={v.probationDays ? `${v.probationDays} days` : null}
        />
      </Section>

      {qualCount + expCount > 0 ? (
        <Section
          title="Education & history"
          level={5}
          actions={<EditAction onClick={() => onEdit('profile')} />}
        >
          <Row
            label="Summary"
            value={
              <Tags>
                {qualCount ? <Tag>{qualCount} qualifications</Tag> : null}
                {expCount ? <Tag>{expCount} prior employments</Tag> : null}
              </Tags>
            }
          />
        </Section>
      ) : null}

      {skillCount + certCount > 0 ? (
        <Section
          title="Skills & certs"
          level={5}
          actions={<EditAction onClick={() => onEdit('profile')} />}
        >
          <Row
            label="Summary"
            value={
              <Tags>
                {skillCount ? <Tag>{skillCount} skills</Tag> : null}
                {certCount ? <Tag>{certCount} certifications</Tag> : null}
              </Tags>
            }
          />
        </Section>
      ) : null}

      {ecCount + disCount > 0 ? (
        <Section
          title="Emergency & disabilities"
          level={5}
          actions={<EditAction onClick={() => onEdit('profile')} />}
        >
          <Row
            label="Summary"
            value={
              <Tags>
                {ecCount ? <Tag>{ecCount} emergency contacts</Tag> : null}
                {disCount ? <Tag>{disCount} disabilities</Tag> : null}
              </Tags>
            }
          />
        </Section>
      ) : null}

      <Section
        title="Salary"
        level={5}
        actions={<EditAction onClick={() => onEdit('pay-and-login')} />}
      >
        <Row
          label="Monthly basic"
          value={
            v.salaryBasic
              ? `${CURRENCY_SYMBOL} ${formatRupees(v.salaryBasic)}`
              : null
          }
        />
        {v.salaryComponents?.length ? (
          <Row
            label="Allowances/deductions"
            value={`${countNonEmpty(v.salaryComponents, (r) => !!r?.componentId)} line items`}
          />
        ) : null}
        <Row
          label="Payment mode"
          value={EMPLOYEE_PAYMENT_MODE_LABEL[v.paymentMode ?? 'CASH']}
        />
        {v.paymentMode === 'BANK_TRANSFER' && v.salaryBank?.bankId ? (
          <Row label="Bank" value="Set on this step" />
        ) : null}
      </Section>

      <Section
        title="Shift"
        level={5}
        actions={<EditAction onClick={() => onEdit('org-placement')} />}
      >
        <Row
          label="Initial shift"
          value={v.shiftId ? `Shift #${v.shiftId}` : null}
        />
      </Section>

      <Section
        title="Self-service login"
        level={5}
        actions={<EditAction onClick={() => onEdit('pay-and-login')} />}
      >
        <Row
          label="Provision now?"
          value={v.provisionEss ? 'Yes' : 'No — invite from profile later'}
        />
        {v.provisionEss ? <Row label="Login username" value={v.username} /> : null}
      </Section>
    </Section>
  );
}

function EditAction({ onClick }: { onClick: () => void }) {
  return (
    <Button
      type="link"
      size="small"
      icon={<Pencil size={12} strokeWidth={1.75} />}
      onClick={onClick}
      style={{ padding: 0, height: 'auto' }}
    >
      Edit
    </Button>
  );
}

function Row({
  label,
  value,
}: {
  label: string;
  value?: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '180px 1fr',
        alignItems: 'baseline',
        gap: 12,
        padding: '4px 0',
      }}
    >
      <Text type="secondary" style={{ fontSize: 12 }}>
        {label}
      </Text>
      <span style={{ fontSize: 13, fontWeight: 500 }}>
        {value === undefined || value === null || value === '' ? (
          <Text type="secondary">—</Text>
        ) : (
          value
        )}
      </span>
    </div>
  );
}

function Tags({ children }: { children: React.ReactNode }) {
  return <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>{children}</span>;
}

function countNonEmpty<T>(rows: T[] | undefined, valid: (r: T) => boolean): number {
  if (!rows) return 0;
  let n = 0;
  for (const r of rows) if (valid(r)) n++;
  return n;
}

function prettyEnum(s: string): string {
  return s.charAt(0) + s.slice(1).toLowerCase();
}

function formatRupees(n: number): string {
  return n.toLocaleString('en-PK');
}

// Step: skills + certifications. Both optional. Skill rows need
// skillId+proficiency; cert rows need name. toRequest drops incomplete
// rows on submit.

import { Button, DatePicker, Form, Input, InputNumber, Select } from 'antd';
import { Plus, Trash2 } from 'lucide-react';
import { Section } from '@/components';
import {
  type OnboardingLookups,
  type ProficiencyLevelEnum,
} from '@/people';

const PROFICIENCY_OPTIONS: { value: ProficiencyLevelEnum; label: string }[] = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
  { value: 'EXPERT', label: 'Expert' },
];

export type SkillsStepProps = {
  lookups: OnboardingLookups | undefined;
};

export function SkillsStep({ lookups }: SkillsStepProps) {
  return (
    <>
      <Section
        title="Skills"
        subtitle="Optional. Powers internal mobility search and skill-gap reporting."
      >
        <Form.List name="skills">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <RowGrid key={field.key} columns="1.6fr 1fr 100px 1.6fr auto">
                  <Form.Item
                    {...field}
                    name={[field.name, 'skillId']}
                    style={{ marginBottom: 0 }}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="Skill"
                      options={(lookups?.skills ?? [])
                        .filter((s) => s.active !== false)
                        .map((s) => ({
                          value: s.id!,
                          label: s.name ?? s.code ?? `#${s.id}`,
                        }))}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'proficiency']}
                    style={{ marginBottom: 0 }}
                  >
                    <Select placeholder="Proficiency" options={PROFICIENCY_OPTIONS} />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'years']}
                    style={{ marginBottom: 0 }}
                  >
                    <InputNumber
                      placeholder="Years"
                      min={0}
                      max={60}
                      step={0.5}
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
              >
                Add skill
              </Button>
            </>
          )}
        </Form.List>
      </Section>

      <Section
        title="Certifications"
        subtitle="Optional. Expiring certs surface in HR's compliance queue automatically."
      >
        <Form.List name="certifications">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <RowGrid key={field.key} columns="1.6fr 1.2fr 1.4fr 1fr auto">
                  <Form.Item
                    {...field}
                    name={[field.name, 'name']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Certification name" />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'issuingBody']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Issuing body" />
                  </Form.Item>
                  <RowGrid columns="1fr 1fr">
                    <Form.Item
                      {...field}
                      name={[field.name, 'issueDate']}
                      style={{ marginBottom: 0 }}
                    >
                      <DatePicker style={{ width: '100%' }} placeholder="Issued" />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'expiryDate']}
                      style={{ marginBottom: 0 }}
                    >
                      <DatePicker style={{ width: '100%' }} placeholder="Expires" />
                    </Form.Item>
                  </RowGrid>
                  <Form.Item
                    {...field}
                    name={[field.name, 'credentialId']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Credential ID" />
                  </Form.Item>
                  <RemoveButton onClick={() => remove(field.name)} />
                </RowGrid>
              ))}
              <Button
                type="dashed"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => add({})}
                style={{ width: '100%' }}
              >
                Add certification
              </Button>
            </>
          )}
        </Form.List>
      </Section>
    </>
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
        padding: '8px 0',
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

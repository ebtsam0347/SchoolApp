// Step: academic qualifications + prior employment. Both sections are
// optional; rows missing required fields (qualificationId+institution
// for quals, employerName+date range for experience) are dropped on
// submit by toRequest.

import { Button, DatePicker, Form, Input, InputNumber, Select, Space } from 'antd';
import { Plus, Trash2 } from 'lucide-react';
import { Section } from '@/components';
import type { OnboardingLookups } from '@/people';

export type BackgroundStepProps = {
  lookups: OnboardingLookups | undefined;
};

export function BackgroundStep({ lookups }: BackgroundStepProps) {
  return (
    <>
      <Section
        title="Qualifications"
        subtitle="Optional. Pick from the catalog and supply the institution + year. Skip rows you don't have."
      >
        <Form.List name="qualifications">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <RowGrid key={field.key} columns="1.4fr 1.6fr 100px 0.8fr auto">
                  <Form.Item
                    {...field}
                    name={[field.name, 'qualificationId']}
                    style={{ marginBottom: 0 }}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="Qualification"
                      options={(lookups?.qualifications ?? [])
                        .filter((q) => q.active !== false)
                        .map((q) => ({
                          value: q.id!,
                          label: q.name ?? q.code ?? `#${q.id}`,
                        }))}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'institution']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Institution" />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'yearOfCompletion']}
                    style={{ marginBottom: 0 }}
                  >
                    <InputNumber
                      placeholder="Year"
                      min={1950}
                      max={2100}
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'grade']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Grade / GPA" />
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
                Add qualification
              </Button>
            </>
          )}
        </Form.List>
      </Section>

      <Section
        title="Prior employment"
        subtitle="Optional. Used for tenure, references, and audit trail."
      >
        <Form.List name="experiences">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <Space
                  key={field.key}
                  orientation="vertical"
                  size={4}
                  style={{
                    width: '100%',
                    padding: '12px 0',
                    borderBottom: '1px solid var(--app-border-secondary, transparent)',
                  }}
                >
                  <RowGrid columns="1.4fr 1.4fr 1.6fr auto">
                    <Form.Item
                      {...field}
                      name={[field.name, 'employerName']}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder="Employer" />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'designation']}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder="Designation" />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'range']}
                      style={{ marginBottom: 0 }}
                    >
                      <DatePicker.RangePicker
                        style={{ width: '100%' }}
                        format="MMM YYYY"
                        picker="month"
                        placeholder={['Start', 'End']}
                      />
                    </Form.Item>
                    <RemoveButton onClick={() => remove(field.name)} />
                  </RowGrid>
                  <Form.Item
                    {...field}
                    name={[field.name, 'responsibilities']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input.TextArea rows={2} placeholder="Responsibilities (optional)" />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'reasonForLeaving']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Reason for leaving (optional)" />
                  </Form.Item>
                </Space>
              ))}
              <Button
                type="dashed"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => add({})}
                style={{ width: '100%', marginTop: 12 }}
              >
                Add prior employment
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

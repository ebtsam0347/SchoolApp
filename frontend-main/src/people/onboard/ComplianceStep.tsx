// Step: emergency contacts + disabilities. Backend recommends ≥1
// emergency contact for compliance — we surface a soft hint but don't
// hard-require it (the wire schema allows zero).
//
// Disability catalog has its own endpoint (/disabilities) so we fetch
// it lazily here rather than bloating /employees/onboarding-lookups.

import { Button, Checkbox, Form, Input, Select, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { Section } from '@/components';
import { disabilitiesApi, type DisabilitySeverity } from '@/people';

const { Text } = Typography;

const SEVERITY_OPTIONS: { value: DisabilitySeverity; label: string }[] = [
  { value: 'MILD', label: 'Mild' },
  { value: 'MODERATE', label: 'Moderate' },
  { value: 'SEVERE', label: 'Severe' },
];

export function ComplianceStep() {
  const disabilitiesQuery = useQuery({
    queryKey: ['disabilities', 'catalog'],
    queryFn: ({ signal }) => disabilitiesApi.catalog({}, signal),
    staleTime: 24 * 60 * 60 * 1000,
  });

  return (
    <>
      <Section
        title="Emergency contacts"
        subtitle="Recommended. Mark exactly one as primary — others are stored as non-primary regardless."
      >
        <Form.List name="emergencyContacts">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <RowGrid key={field.key} columns="1.4fr 1fr 1.2fr 110px auto">
                  <Form.Item
                    {...field}
                    name={[field.name, 'name']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Full name" />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'relation']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Relation (Spouse, Father…)" />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'phone']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Phone" />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'primary']}
                    valuePropName="checked"
                    style={{ marginBottom: 0, paddingTop: 6 }}
                  >
                    <Checkbox>Primary</Checkbox>
                  </Form.Item>
                  <RemoveButton onClick={() => remove(field.name)} />
                </RowGrid>
              ))}
              <Button
                type="dashed"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => add({ primary: fields.length === 0 })}
                style={{ width: '100%' }}
              >
                Add emergency contact
              </Button>
            </>
          )}
        </Form.List>
      </Section>

      <Section
        title="Disabilities"
        subtitle="Optional. Used for PWD ordinance compliance reporting."
      >
        <Form.List name="disabilities">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <RowGrid key={field.key} columns="1.4fr 1fr 1.6fr auto">
                  <Form.Item
                    {...field}
                    name={[field.name, 'disabilityId']}
                    style={{ marginBottom: 0 }}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="Category"
                      loading={disabilitiesQuery.isLoading}
                      options={(disabilitiesQuery.data ?? [])
                        .filter((d) => d.active !== false)
                        .map((d) => ({ value: d.id, label: d.name }))}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'severity']}
                    style={{ marginBottom: 0 }}
                  >
                    <Select
                      placeholder="Severity"
                      allowClear
                      options={SEVERITY_OPTIONS}
                    />
                  </Form.Item>
                  <Form.Item
                    {...field}
                    name={[field.name, 'description']}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="Description (optional)" />
                  </Form.Item>
                  <RemoveButton onClick={() => remove(field.name)} />
                </RowGrid>
              ))}
              <Button
                type="dashed"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => add({})}
                style={{ width: '100%' }}
                disabled={!disabilitiesQuery.data?.length}
              >
                {disabilitiesQuery.data?.length === 0
                  ? 'No disability catalog entries yet'
                  : 'Add disability'}
              </Button>
            </>
          )}
        </Form.List>
        {disabilitiesQuery.isError ? (
          <Text type="danger" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
            Couldn't load the disability catalog — leaving this section empty is fine.
          </Text>
        ) : null}
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

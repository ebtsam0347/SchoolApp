// Step 2: department / section / designation / grade / manager. Sections
// are filtered by the selected department; clearing department resets
// section. Manager search hits the employees list as the user types.

import { DatePicker, Form, Select, Space, type FormInstance } from 'antd';
import type { UseQueryResult } from '@tanstack/react-query';
import { Section } from '@/components';
import {
  EMPLOYMENT_TYPE_LABEL,
  type EmploymentType,
  type OnboardingLookups,
  type PageResponse,
  type EmployeeDto,
} from '@/people';
import type { WizardForm } from './types';

export type EmploymentStepProps = {
  lookups: OnboardingLookups | undefined;
  form: FormInstance<WizardForm>;
  managerSearch: string;
  setManagerSearch: (q: string) => void;
  managerQuery: UseQueryResult<PageResponse<EmployeeDto>>;
};

export function EmploymentStep({
  lookups,
  form,
  setManagerSearch,
  managerQuery,
}: EmploymentStepProps) {
  const departmentId = Form.useWatch('departmentId', form);
  const sections = (lookups?.sections ?? []).filter(
    (s) => !departmentId || s.departmentId === departmentId,
  );

  return (
    <Section
      title="Employment"
      subtitle="Where this person sits in the org. Joining date, department, section, designation, and grade are all required for payroll grading."
    >
      <Space.Compact block>
        <Form.Item
          name="joiningDate"
          label="Joining date"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="employmentType" label="Employment type" style={{ flex: 1 }}>
          <Select
            options={(['PERMANENT', 'CONTRACT', 'INTERN'] as EmploymentType[]).map(
              (t) => ({
                value: t,
                label: EMPLOYMENT_TYPE_LABEL[t],
              }),
            )}
          />
        </Form.Item>
      </Space.Compact>
      <Space.Compact block>
        <Form.Item
          name="departmentId"
          label="Department"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            onChange={() => form.setFieldValue('sectionId', undefined)}
            options={(lookups?.departments ?? [])
              .filter((d) => d.active !== false)
              .map((d) => ({ value: d.id!, label: d.name }))}
          />
        </Form.Item>
        <Form.Item
          name="sectionId"
          label="Section"
          rules={[{ required: true, message: 'Required' }]}
          extra={!departmentId ? 'Pick a department first.' : undefined}
          style={{ flex: 1 }}
        >
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            disabled={!departmentId}
            options={sections
              .filter((s) => s.active !== false)
              .map((s) => ({ value: s.id!, label: s.name }))}
          />
        </Form.Item>
      </Space.Compact>
      <Space.Compact block>
        <Form.Item
          name="designationId"
          label="Designation"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            options={(lookups?.designations ?? [])
              .filter((d) => d.active !== false)
              .map((d) => ({
                value: d.id!,
                label: d.title ?? d.code ?? `#${d.id}`,
              }))}
          />
        </Form.Item>
        <Form.Item
          name="gradeId"
          label="Grade"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1 }}
        >
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            options={(lookups?.grades ?? [])
              .filter((g) => g.active !== false)
              .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
              .map((g) => ({ value: g.id!, label: g.name ?? g.code ?? `#${g.id}` }))}
          />
        </Form.Item>
      </Space.Compact>
      <Form.Item
        name="managerId"
        label="Manager"
        extra={
          departmentId
            ? 'Scoped to the department/section picked above — search to narrow further.'
            : 'Pick a department first.'
        }
      >
        <Select
          showSearch
          filterOption={false}
          onSearch={setManagerSearch}
          loading={managerQuery.isFetching}
          disabled={!departmentId}
          allowClear
          onSelect={(_, option) =>
            form.setFieldValue('managerLabel', option.label as string)
          }
          onClear={() => form.setFieldValue('managerLabel', undefined)}
          notFoundContent={
            !departmentId
              ? 'Pick a department first'
              : managerQuery.isFetching
                ? 'Searching…'
                : 'No matches'
          }
          options={(managerQuery.data?.content ?? []).map((m) => ({
            value: m.id!,
            label: `${m.fullName} · ${m.employeeCode}`,
          }))}
        />
      </Form.Item>
    </Section>
  );
}

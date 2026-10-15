// Step: initial shift assignment. Optional. When set, the
// ASSIGN_SHIFT saga step runs after salary/bank are in place.

import { Form, Select, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { Section } from '@/components';
import { shiftsApi } from '@/attendance';

const { Text } = Typography;

export function ShiftStep() {
  const shiftsQuery = useQuery({
    queryKey: ['shifts', 'catalog'],
    queryFn: ({ signal }) => shiftsApi.list(signal),
    staleTime: 5 * 60 * 1000,
  });

  return (
    <Section
      title="Shift"
      subtitle="The shift drives attendance computations from the joining date. HR can change it later from the employee's profile."
    >
      <Form.Item
        name="shiftId"
        label="Initial shift"
        rules={[{ required: true, message: 'Required' }]}
      >
        <Select
          showSearch
          optionFilterProp="label"
          loading={shiftsQuery.isLoading}
          placeholder="Pick a shift"
          options={(shiftsQuery.data ?? [])
            .filter((s) => s.active)
            .map((s) => ({
              value: s.id,
              label: `${s.name} · ${s.startTime}–${s.endTime}`,
            }))}
        />
      </Form.Item>
      {shiftsQuery.isError ? (
        <Text type="danger" style={{ fontSize: 12 }}>
          Couldn't load shifts — ask an admin to add at least one shift before
          onboarding employees.
        </Text>
      ) : null}
    </Section>
  );
}

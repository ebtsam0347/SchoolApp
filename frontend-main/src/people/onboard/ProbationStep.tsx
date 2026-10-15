// Step 3: probation length. The onboarding saga creates a probation
// contract starting on the joining date.

import { Form, Select, Typography } from 'antd';
import { Section } from '@/components';

const { Paragraph } = Typography;

export function ProbationStep() {
  return (
    <Section
      title="Contract"
      subtitle="A probation contract is created automatically, starting on the joining date."
    >
      <Form.Item
        name="probationDays"
        label="Probation length"
        rules={[{ required: true, message: 'Required' }]}
      >
        <Select
          options={[
            { value: 30, label: '30 days' },
            { value: 60, label: '60 days' },
            { value: 90, label: '90 days (default)' },
          ]}
        />
      </Form.Item>
      <Paragraph type="secondary" style={{ marginTop: 12 }}>
        Confirm or extend probation later from the employee's Employment tab.
        Confirmation creates a permanent contract starting on the confirmation date.
      </Paragraph>
    </Section>
  );
}

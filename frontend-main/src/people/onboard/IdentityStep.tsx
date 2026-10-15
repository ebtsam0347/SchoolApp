// Step 1 of the onboarding wizard: personal details. Several fields are
// required for compliance / payroll: full name, CNIC, date of birth,
// gender, phone, father's name, religion, permanent address, city.
// Everything else (CNIC dates, marital status, mother's name,
// addresses, work email, NTN/EOBI/SSC) is optional.
//
// DOB enforces minimum-age-at-joining (18y) — disabledDate keeps the
// picker honest, the validator catches the case where the user goes
// back to Employment and changes joining date after picking a DOB.
//
// Renders Form.Items inside the parent's <Form>; the parent owns the
// Form instance.

import { DatePicker, Form, Input, Select, Space, type FormInstance } from 'antd';
import dayjs from 'dayjs';
import { Section } from '@/components';
import type { OnboardingLookups } from '@/people';
import type { WizardForm } from './types';

const MIN_AGE_AT_JOINING = 18;

export function IdentityStep({
  lookups,
  form,
}: {
  lookups: OnboardingLookups | undefined;
  form: FormInstance<WizardForm>;
}) {
  const joiningDate = Form.useWatch('joiningDate', form);
  const minDob = (joiningDate ?? dayjs()).subtract(MIN_AGE_AT_JOINING, 'year');
  return (
    <Section
      title="Personal details"
      subtitle="Fields marked * are required. The rest improves search and reporting and can be added later."
    >
      <Form.Item
        name="fullName"
        label="Full name"
        rules={[{ required: true, message: 'Required' }]}
      >
        <Input placeholder="e.g. Ahmed Khan" />
      </Form.Item>
      <Space.Compact block>
        <Form.Item
          name="cnic"
          label="CNIC"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <Input placeholder="35202-1234567-1" />
        </Form.Item>
        <Form.Item name="cnicDates" label="CNIC issued / expires" style={{ flex: 1.4 }}>
          <DatePicker.RangePicker style={{ width: '100%' }} />
        </Form.Item>
      </Space.Compact>
      <Space.Compact block>
        <Form.Item
          name="dateOfBirth"
          label="Date of birth"
          rules={[
            { required: true, message: 'Required' },
            {
              validator: (_, value: dayjs.Dayjs | undefined) => {
                if (!value) return Promise.resolve();
                const joining =
                  (form.getFieldValue('joiningDate') as dayjs.Dayjs | undefined) ??
                  dayjs();
                const ageAtJoining = joining.diff(value, 'year');
                if (ageAtJoining < MIN_AGE_AT_JOINING) {
                  return Promise.reject(
                    new Error(
                      `Must be at least ${MIN_AGE_AT_JOINING} on the joining date (${joining.format('YYYY-MM-DD')}).`,
                    ),
                  );
                }
                return Promise.resolve();
              },
            },
          ]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <DatePicker
            style={{ width: '100%' }}
            disabledDate={(current) => !!current && current.isAfter(minDob, 'day')}
            defaultPickerValue={minDob}
          />
        </Form.Item>
        <Form.Item
          name="gender"
          label="Gender"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <Select
            allowClear
            options={[
              { value: 'MALE', label: 'Male' },
              { value: 'FEMALE', label: 'Female' },
              { value: 'OTHER', label: 'Other' },
            ]}
          />
        </Form.Item>
        <Form.Item name="maritalStatus" label="Marital status" style={{ flex: 1 }}>
          <Select
            allowClear
            options={[
              { value: 'SINGLE', label: 'Single' },
              { value: 'MARRIED', label: 'Married' },
              { value: 'OTHER', label: 'Other' },
            ]}
          />
        </Form.Item>
      </Space.Compact>
      <Space.Compact block>
        <Form.Item
          name="phone"
          label="Phone"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <Input placeholder="0300-1234567" />
        </Form.Item>
        <Form.Item
          name="workEmail"
          label="Work email"
          rules={[{ type: 'email', message: 'Looks invalid' }]}
          style={{ flex: 1 }}
        >
          <Input placeholder="@forwardsports.com" />
        </Form.Item>
      </Space.Compact>
      <Form.Item
        name="personalEmail"
        label="Personal email"
        rules={[{ type: 'email', message: 'Looks invalid' }]}
      >
        <Input placeholder="optional" />
      </Form.Item>
      <Space.Compact block>
        <Form.Item
          name="fatherName"
          label="Father's name"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <Input />
        </Form.Item>
        <Form.Item name="motherName" label="Mother's name" style={{ flex: 1 }}>
          <Input />
        </Form.Item>
      </Space.Compact>
      <Space.Compact block>
        <Form.Item name="spouseName" label="Spouse's name" style={{ flex: 1, marginRight: 8 }}>
          <Input />
        </Form.Item>
        <Form.Item
          name="religion"
          label="Religion"
          rules={[{ required: true, message: 'Required' }]}
          style={{ flex: 1, marginRight: 8 }}
        >
          <Input />
        </Form.Item>
        <Form.Item name="bloodGroup" label="Blood group" style={{ flex: 0.6 }}>
          <Select
            allowClear
            options={[
              'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-',
            ].map((bg) => ({ value: bg, label: bg }))}
          />
        </Form.Item>
      </Space.Compact>
      <Space.Compact block>
        <Form.Item name="ntn" label="NTN" style={{ flex: 1, marginRight: 8 }}>
          <Input />
        </Form.Item>
        <Form.Item name="eobiNumber" label="EOBI" style={{ flex: 1, marginRight: 8 }}>
          <Input />
        </Form.Item>
        <Form.Item name="sscNumber" label="PESSI / SSC" style={{ flex: 1 }}>
          <Input />
        </Form.Item>
      </Space.Compact>
      <Form.Item
        name="permanentAddress"
        label="Permanent address"
        rules={[{ required: true, message: 'Required' }]}
      >
        <Input.TextArea rows={2} />
      </Form.Item>
      <Form.Item name="currentAddress" label="Current address (if different)">
        <Input.TextArea rows={2} />
      </Form.Item>
      <Form.Item
        name="cityId"
        label="City"
        rules={[{ required: true, message: 'Required' }]}
      >
        <Select
          showSearch
          optionFilterProp="label"
          allowClear
          placeholder="Sialkot, Lahore, …"
          options={(lookups?.cities ?? [])
            .filter((c) => c.active !== false)
            .map((c) => ({
              value: c.id!,
              label: `${c.name}${c.provinceCode ? ` · ${c.provinceCode}` : ''}`,
            }))}
        />
      </Form.Item>
    </Section>
  );
}

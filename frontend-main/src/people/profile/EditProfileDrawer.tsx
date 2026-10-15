// Edit-profile drawer — single form covering everything in
// UpdateEmployeeRequest (contact, identity, personal). Server-state-only:
// reading the same detail-query the parent page already cached, mutating
// via employeesApi.update, then invalidating the composite so every tab
// (and the hero) reflects new values.

import { useEffect } from 'react';
import { App, DatePicker, Form, Input, Select } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';

import { DrawerForm, Section } from '@/components';
import { employeesApi, peopleBff } from '@/people';
import { invalidateEmployeeReads } from '@/people/cache';
import { handleFormError } from '@/lib';
import type { EmployeeDto, UpdateEmployeeRequest } from '@/people/types';

type FormValues = {
  fullName?: string;
  phone?: string;
  workEmail?: string;
  personalEmail?: string;
  permanentAddress?: string;
  currentAddress?: string;
  cityId?: number;
  cnic?: string;
  cnicIssueDate?: dayjs.Dayjs | null;
  cnicExpiryDate?: dayjs.Dayjs | null;
  ntn?: string;
  insuranceNumber?: string;
  sscNumber?: string;
  eobiNumber?: string;
  dateOfBirth?: dayjs.Dayjs | null;
  gender?: string;
  maritalStatus?: string;
  fatherName?: string;
  motherName?: string;
  spouseName?: string;
  religion?: string;
  bloodGroup?: string;
};

export type EditProfileDrawerProps = {
  open: boolean;
  onClose: () => void;
  employee: EmployeeDto;
};

export function EditProfileDrawer({ open, onClose, employee }: EditProfileDrawerProps) {
  const [form] = Form.useForm<FormValues>();
  const qc = useQueryClient();
  const { message } = App.useApp();
  const employeeId = employee.id!;

  // Cities live on the onboarding lookups composite; reuse it so we don't
  // open a per-drawer round-trip and so the picker matches Onboard exactly.
  const lookupsQuery = useQuery({
    queryKey: ['onboarding', 'lookups'],
    queryFn: ({ signal }) => peopleBff.onboardingLookups(signal),
    staleTime: 5 * 60_000,
    enabled: open,
  });

  // Reset form whenever the drawer opens with the latest employee snapshot.
  // initialValues only applies on first mount — we need this so re-opening
  // after a save reflects the patched data.
  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      fullName: employee.fullName,
      phone: employee.phone,
      workEmail: employee.workEmail,
      personalEmail: employee.personalEmail,
      permanentAddress: employee.permanentAddress,
      currentAddress: employee.currentAddress,
      cityId: employee.cityId,
      cnic: employee.cnic,
      cnicIssueDate: employee.cnicIssueDate ? dayjs(employee.cnicIssueDate) : null,
      cnicExpiryDate: employee.cnicExpiryDate ? dayjs(employee.cnicExpiryDate) : null,
      ntn: employee.ntn,
      insuranceNumber: employee.insuranceNumber,
      sscNumber: employee.sscNumber,
      eobiNumber: employee.eobiNumber,
      dateOfBirth: employee.dateOfBirth ? dayjs(employee.dateOfBirth) : null,
      gender: employee.gender,
      maritalStatus: employee.maritalStatus,
      fatherName: employee.fatherName,
      motherName: employee.motherName,
      spouseName: employee.spouseName,
      religion: employee.religion,
      bloodGroup: employee.bloodGroup,
    });
  }, [open, employee, form]);

  const mutation = useMutation({
    mutationFn: (body: UpdateEmployeeRequest) => employeesApi.update(employeeId, body),
    onSuccess: () => {
      message.success('Profile updated');
      invalidateEmployeeReads(qc, employeeId);
      onClose();
    },
    onError: (err) => {
      handleFormError(form, err, message);
    },
  });

  const handleFinish = (values: FormValues) => {
    const body: UpdateEmployeeRequest = {
      fullName: values.fullName?.trim() || undefined,
      phone: values.phone?.trim() || undefined,
      workEmail: values.workEmail?.trim() || undefined,
      personalEmail: values.personalEmail?.trim() || undefined,
      permanentAddress: values.permanentAddress?.trim() || undefined,
      currentAddress: values.currentAddress?.trim() || undefined,
      cityId: values.cityId,
      cnic: values.cnic?.trim() || undefined,
      cnicIssueDate: values.cnicIssueDate ? values.cnicIssueDate.format('YYYY-MM-DD') : undefined,
      cnicExpiryDate: values.cnicExpiryDate ? values.cnicExpiryDate.format('YYYY-MM-DD') : undefined,
      ntn: values.ntn?.trim() || undefined,
      insuranceNumber: values.insuranceNumber?.trim() || undefined,
      sscNumber: values.sscNumber?.trim() || undefined,
      eobiNumber: values.eobiNumber?.trim() || undefined,
      dateOfBirth: values.dateOfBirth ? values.dateOfBirth.format('YYYY-MM-DD') : undefined,
      gender: values.gender || undefined,
      maritalStatus: values.maritalStatus || undefined,
      fatherName: values.fatherName?.trim() || undefined,
      motherName: values.motherName?.trim() || undefined,
      spouseName: values.spouseName?.trim() || undefined,
      religion: values.religion?.trim() || undefined,
      bloodGroup: values.bloodGroup?.trim() || undefined,
    };
    mutation.mutate(body);
  };

  const cityOptions = (lookupsQuery.data?.cities ?? [])
    .filter((c: { active?: boolean }) => c.active !== false)
    .map((c: { id?: number; name?: string; provinceCode?: string }) => ({
      value: c.id!,
      label: `${c.name}${c.provinceCode ? ` · ${c.provinceCode}` : ''}`,
    }));

  return (
    <DrawerForm<FormValues>
      open={open}
      onClose={onClose}
      title="Edit profile"
      size={520}
      form={form}
      onFinish={handleFinish}
      submitting={mutation.isPending}
      submitText="Save changes"
    >
      <Section title="Contact" level={5}>
        <Form.Item name="fullName" label="Full name" rules={[{ required: true, message: 'Name is required' }]}>
          <Input placeholder="Abdul Hameed" autoComplete="name" />
        </Form.Item>
        <Form.Item name="phone" label="Phone">
          <Input placeholder="03001234567" inputMode="tel" autoComplete="tel" />
        </Form.Item>
        <Form.Item name="workEmail" label="Work email" rules={[{ type: 'email', message: 'Enter a valid email' }]}>
          <Input placeholder="abdul@forwardsports.com" autoComplete="email" />
        </Form.Item>
        <Form.Item name="personalEmail" label="Personal email" rules={[{ type: 'email', message: 'Enter a valid email' }]}>
          <Input placeholder="abdul@gmail.com" autoComplete="email" />
        </Form.Item>
        <Form.Item name="cityId" label="City">
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            placeholder="Sialkot, Lahore, …"
            options={cityOptions}
            loading={lookupsQuery.isLoading}
          />
        </Form.Item>
        <Form.Item name="permanentAddress" label="Permanent address">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item name="currentAddress" label="Current address (if different)">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Section>

      <Section title="Identity" level={5}>
        <Form.Item name="cnic" label="CNIC">
          <Input placeholder="12345-1234567-1" />
        </Form.Item>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item name="cnicIssueDate" label="CNIC issued">
            <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
          </Form.Item>
          <Form.Item name="cnicExpiryDate" label="CNIC expiry">
            <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
          </Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item name="ntn" label="NTN">
            <Input />
          </Form.Item>
          <Form.Item name="eobiNumber" label="EOBI">
            <Input />
          </Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item name="sscNumber" label="SSC / PESSI">
            <Input />
          </Form.Item>
          <Form.Item name="insuranceNumber" label="Insurance #">
            <Input />
          </Form.Item>
        </div>
      </Section>

      <Section title="Personal" level={5}>
        <Form.Item name="dateOfBirth" label="Date of birth">
          <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
        </Form.Item>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item name="gender" label="Gender">
            <Select
              allowClear
              options={[
                { value: 'MALE', label: 'Male' },
                { value: 'FEMALE', label: 'Female' },
                { value: 'OTHER', label: 'Other' },
              ]}
            />
          </Form.Item>
          <Form.Item name="maritalStatus" label="Marital status">
            <Select
              allowClear
              options={[
                { value: 'SINGLE', label: 'Single' },
                { value: 'MARRIED', label: 'Married' },
                { value: 'DIVORCED', label: 'Divorced' },
                { value: 'WIDOWED', label: 'Widowed' },
              ]}
            />
          </Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item name="fatherName" label="Father's name">
            <Input />
          </Form.Item>
          <Form.Item name="motherName" label="Mother's name">
            <Input />
          </Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item name="spouseName" label="Spouse's name">
            <Input />
          </Form.Item>
          <Form.Item name="religion" label="Religion">
            <Input />
          </Form.Item>
        </div>
        <Form.Item name="bloodGroup" label="Blood group">
          <Select
            allowClear
            options={[
              'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-',
            ].map((v) => ({ value: v, label: v }))}
          />
        </Form.Item>
      </Section>
    </DrawerForm>
  );
}

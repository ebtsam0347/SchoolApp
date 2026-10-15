// Create / edit drawer for an Employee Requisition — supports master head + multi-position detail requests.

import { useEffect } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Typography,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { Plus, Trash2 } from 'lucide-react';

import { shiftsApi } from '@/attendance';
import { DrawerForm } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  catalogsApi,
  employeesApi,
  EMPLOYMENT_TYPE_LABEL,
  type EmploymentType,
} from '@/people';
import { requisitionApi } from '../api';
import { REQUISITION_TYPE_LABEL } from '../format';
import type {
  CreateNewHiringRequest,
  CreateReplacementRequest,
  EmployeeCategory,
  RequisitionDto,
  RequisitionGender,
  RequisitionType,
} from '../types';

const { Text } = Typography;

export type RequisitionFormDrawerProps = {
  open: boolean;
  onClose: () => void;
  /** Pass a DRAFT requisition to edit it in place; omit to create a new one. */
  requisition?: RequisitionDto | null;
};

type DetailValue = {
  noOfPosition?: number;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  replacingEmployeeId?: number;
  employmentType: EmploymentType;
  employeeCategory: EmployeeCategory;
  requiredDate: dayjs.Dayjs;
  employeeGender: RequisitionGender;
  shiftId?: number;
};

type Values = {
  type: RequisitionType;
  // Head fields for New Hire
  departmentId: number;
  minimumEducation?: string;
  minimumExperience?: number;
  minimumSkill?: string;
  jobDescription?: string;
  requestReason?: string;
  hodNotes?: string;

  // Details array for New Hire
  details: DetailValue[];

  // Fields for Replacement
  replacingEmployeeId?: number;
  vacancies?: number;
  requiredBy?: dayjs.Dayjs;
  reason?: string;
  notes?: string;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  employmentType?: EmploymentType;
};

const GENDER_OPTIONS: { value: RequisitionGender; label: string }[] = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'OTHER', label: 'Other' },
];

const TYPE_OPTIONS: RequisitionType[] = ['NEW_HIRE', 'REPLACEMENT'];
const EMPLOYMENT_TYPE_OPTIONS: EmploymentType[] = ['PERMANENT', 'CONTRACT', 'INTERN'];

const NEW_HIRE_REASON_OPTIONS = [
  'Capacity Enhancement',
  'Addition of New Process/Department',
  'Process Changed',
];

const EDUCATION_OPTIONS = [
  "Bachelor's Degree",
  "Master's Degree",
  'Intermediate / Higher Secondary',
  'Matric / Secondary',
  'Doctorate / Ph.D.',
  'Diploma / Certification',
];

const EMP_CATEGORY_OPTIONS: { value: EmployeeCategory; label: string }[] = [
  { value: 'DIRECT', label: 'Direct' },
  { value: 'INDIRECT', label: 'In-Direct' },
];

type SubmitData =
  | { type: 'NEW_HIRE'; payload: CreateNewHiringRequest }
  | { type: 'REPLACEMENT'; payload: CreateReplacementRequest };

export function RequisitionFormDrawer({
  open,
  onClose,
  requisition,
}: RequisitionFormDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const editing = requisition ?? null;

  const type = Form.useWatch('type', form);
  const departmentId = Form.useWatch('departmentId', form);

  const meQuery = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: ({ signal }) => employeesApi.me(signal),
    staleTime: 5 * 60_000,
  });

  const myTeamQuery = useQuery({
    queryKey: ['employees', 'me', 'team', 'including-terminated', meQuery.data?.id],
    queryFn: ({ signal }) =>
      employeesApi.list(
        {
          managerId: meQuery.data?.id ?? 0,
          includeTerminated: true,
          size: 100,
        },
        signal,
      ),
    enabled: type === 'REPLACEMENT' && !!meQuery.data?.id,
    staleTime: 60_000,
  });

  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
  });
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections', departmentId],
    queryFn: ({ signal }) => catalogsApi.sections(departmentId, signal),
    staleTime: 5 * 60 * 1000,
    enabled: !!departmentId,
  });
  const designationsQuery = useQuery({
    queryKey: ['catalog', 'designations'],
    queryFn: ({ signal }) => catalogsApi.designations(signal),
    staleTime: 5 * 60 * 1000,
  });
  const gradesQuery = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: 5 * 60 * 1000,
  });
  const shiftsQuery = useQuery({
    queryKey: ['shifts'],
    queryFn: ({ signal }) => shiftsApi.list(signal),
    staleTime: 5 * 60 * 1000,
  });

  // Populate/reset fields when open or meQuery resolves
  useEffect(() => {
    if (!open) return;

    const userDeptId = editing?.departmentId ?? meQuery.data?.departmentId;

    if (editing) {
      const detailList: DetailValue[] =
        editing.details && editing.details.length > 0
          ? editing.details.map((d) => ({
              replacingEmployeeId: d.replacingEmployeeId ?? editing.replacingEmployeeId,
              noOfPosition: d.noOfPosition ?? 1,
              sectionId: d.sectionId,
              designationId: d.designationId,
              gradeId: d.gradeId,
              employmentType: d.employmentType ?? 'PERMANENT',
              employeeCategory: d.employeeCategory ?? 'DIRECT',
              requiredDate: d.requiredDate ? dayjs(d.requiredDate) : dayjs(),
              employeeGender: d.employeeGender ?? 'MALE',
              shiftId: d.shiftId,
            }))
          : [
              {
                replacingEmployeeId: editing.replacingEmployeeId,
                noOfPosition: editing.vacancies ?? 1,
                sectionId: editing.sectionId,
                designationId: editing.designationId,
                gradeId: editing.gradeId,
                employmentType: editing.employmentType ?? 'PERMANENT',
                employeeCategory: 'DIRECT',
                requiredDate: editing.requiredBy ? dayjs(editing.requiredBy) : dayjs(),
                employeeGender: 'MALE',
              },
            ];

      form.setFieldsValue({
        type: editing.type,
        departmentId: userDeptId,
        minimumEducation: editing.minimumEducation,
        minimumExperience: editing.minimumExperience,
        minimumSkill: editing.minimumSkill,
        jobDescription: editing.jobDescription,
        requestReason: editing.requestReason ?? editing.reason,
        hodNotes: editing.hotNotes ?? editing.hodNotes ?? editing.notes,
        details: detailList,
      });
    } else {
      const existingDetails = form.getFieldValue('details');
      if (!existingDetails || existingDetails.length === 0) {
        form.setFieldsValue({
          type: 'NEW_HIRE',
          departmentId: userDeptId,
          requestReason: 'Capacity Enhancement',
          minimumEducation: "Bachelor's Degree",
          minimumExperience: 1,
          details: [
            {
              noOfPosition: 1,
              employmentType: 'PERMANENT',
              employeeCategory: 'DIRECT',
              employeeGender: 'MALE',
              requiredDate: dayjs(),
            },
          ],
        });
      } else if (userDeptId != null) {
        form.setFieldValue('departmentId', userDeptId);
      }
    }
  }, [open, editing, meQuery.data?.departmentId, form]);

  const mutation = useMutation({
    mutationFn: async (data: SubmitData) => {
      if (editing) {
        const { type: _type, ...rest } = data.payload as any;
        return requisitionApi.update(editing.id, rest);
      }
      if (data.type === 'NEW_HIRE') {
        const created = await requisitionApi.createNewHiring(data.payload);
        if (created?.id) {
          try {
            await requisitionApi.submit(created.id);
          } catch {
            // auto-submitted or draft
          }
        }
        return created;
      } else {
        const created = await requisitionApi.createReplacement(data.payload);
        if (created?.id) {
          try {
            await requisitionApi.submit(created.id);
          } catch {
            // auto-submitted or draft
          }
        }
        return created;
      }
    },
    onSuccess: () => {
      message.success(editing ? 'Requisition updated' : 'Requisition submitted for approval');
      queryClient.invalidateQueries({ queryKey: ['requisitions'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => {
      if (isApiError(err)) applyApiErrorToForm(form, err);
      message.error(getErrorMessage(err));
    },
  });

  return (
    <DrawerForm<Values>
      title={editing ? 'Edit requisition' : 'New requisition'}
      open={open}
      onClose={onClose}
      form={form}
      width="clamp(400px, 90vw, 960px)"
      submitText={editing ? 'Save' : 'Submit for approval'}
      submitting={mutation.isPending}
      onFinish={(v) => {
        if (v.type === 'NEW_HIRE') {
          const newHiringPayload: CreateNewHiringRequest = {
            departmentId: v.departmentId,
            minimumEducation: v.minimumEducation,
            minimumExperience:
              v.minimumExperience != null ? Number(v.minimumExperience) : undefined,
            minimumSkill: v.minimumSkill,
            jobDescription: v.jobDescription,
            requestReason: v.requestReason,
            hodNotes: v.hodNotes,
            hotNotes: v.hodNotes,
            details: (v.details || []).map((d) => ({
              noOfPosition: d.noOfPosition ?? 1,
              sectionId: d.sectionId,
              designationId: d.designationId,
              gradeId: d.gradeId,
              employmentType: d.employmentType,
              employeeCategory: d.employeeCategory ?? 'DIRECT',
              requiredDate: d.requiredDate
                ? d.requiredDate.format('YYYY-MM-DD')
                : dayjs().format('YYYY-MM-DD'),
              employeeGender: d.employeeGender ?? 'MALE',
              shiftId: d.shiftId,
            })),
          };
          mutation.mutate({ type: 'NEW_HIRE', payload: newHiringPayload });
        } else {
          const replacementPayload: CreateReplacementRequest = {
            minimumEducation: v.minimumEducation,
            minimumExperience:
              v.minimumExperience != null ? Number(v.minimumExperience) : undefined,
            minimumSkill: v.minimumSkill,
            jobDescription: v.jobDescription,
            requestReason: v.requestReason,
            hodNotes: v.hodNotes,
            hotNotes: v.hodNotes,
            details: (v.details || []).map((d) => ({
              replacingEmployeeId: d.replacingEmployeeId,
              employmentType: d.employmentType,
              employeeCategory: d.employeeCategory ?? 'DIRECT',
              requiredDate: d.requiredDate
                ? d.requiredDate.format('YYYY-MM-DD')
                : dayjs().format('YYYY-MM-DD'),
              employeeGender: d.employeeGender ?? 'MALE',
              shiftId: d.shiftId,
            })),
          };
          mutation.mutate({ type: 'REPLACEMENT', payload: replacementPayload });
        }
      }}
    >
      <Form.Item
        label="Type"
        name="type"
        rules={[{ required: true, message: 'Pick a requisition type' }]}
        extra={editing ? "Can't be changed after creation." : undefined}
      >
        <Radio.Group
          optionType="button"
          buttonStyle="solid"
          disabled={!!editing}
          onChange={(e) => {
            const nextType = e.target.value as RequisitionType;
            if (nextType === 'REPLACEMENT') {
              form.setFieldsValue({
                details: [
                  {
                    employmentType: 'PERMANENT',
                    employeeCategory: 'DIRECT',
                    employeeGender: 'MALE',
                    requiredDate: dayjs(),
                  },
                ],
              });
            } else {
              form.setFieldsValue({
                details: [
                  {
                    noOfPosition: 1,
                    employmentType: 'PERMANENT',
                    employeeCategory: 'DIRECT',
                    employeeGender: 'MALE',
                    requiredDate: dayjs(),
                  },
                ],
              });
            }
          }}
        >
          {TYPE_OPTIONS.map((t) => (
            <Radio.Button key={t} value={t}>
              {REQUISITION_TYPE_LABEL[t]}
            </Radio.Button>
          ))}
        </Radio.Group>
      </Form.Item>

      {/* Head section: Department is locked/read-only for NEW_HIRE */}
      {type === 'NEW_HIRE' ? (
        <Form.Item
          label="Department"
          name="departmentId"
          rules={[{ required: true, message: 'Department is required' }]}
          extra="Locked to your assigned department."
        >
          <Select
            disabled
            loading={departmentsQuery.isLoading || meQuery.isLoading}
            placeholder="Loading department..."
            options={(departmentsQuery.data ?? [])
              .filter(
                (d) =>
                  d.id === (editing?.departmentId ?? meQuery.data?.departmentId) ||
                  d.active !== false,
              )
              .map((d) => ({ value: d.id!, label: d.name }))}
          />
        </Form.Item>
      ) : null}

      <div
        style={{
          padding: '12px 16px',
          marginBottom: 16,
          background: 'var(--app-color-bg-subtle, #f8fafc)',
          borderRadius: 8,
          border: '1px solid var(--app-color-border, #e2e8f0)',
        }}
      >
        <Text style={{ display: 'block', marginBottom: 12, fontSize: 14, fontWeight: 600 }}>
          Requisition Head Details
        </Text>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '0 16px',
          }}
        >
          <Form.Item
            label="Request Reason"
            name="requestReason"
            rules={[{ required: true, message: 'Select or enter a reason' }]}
          >
            <Select
              showSearch
              allowClear
              placeholder="Select a reason"
              options={NEW_HIRE_REASON_OPTIONS.map((r) => ({ value: r, label: r }))}
            />
          </Form.Item>

          <Form.Item label="Minimum Education" name="minimumEducation">
            <Select
              showSearch
              allowClear
              placeholder="e.g. Bachelor's Degree"
              options={EDUCATION_OPTIONS.map((e) => ({ value: e, label: e }))}
            />
          </Form.Item>

          <Form.Item label="Minimum Experience (Years)" name="minimumExperience">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="e.g. 1" />
          </Form.Item>

          <Form.Item label="Minimum Skill" name="minimumSkill">
            <Input placeholder="e.g. Java, React, SQL" />
          </Form.Item>
        </div>

        <Form.Item label="Job Description" name="jobDescription">
          <Input.TextArea
            autoSize={{ minRows: 2, maxRows: 4 }}
            placeholder="Responsible for developing and maintaining applications."
          />
        </Form.Item>

        <Form.Item
          label="HOD Notes"
          name="hodNotes"
          rules={[{ required: true, message: "Enter the HOD's notes" }]}
          style={{ marginBottom: 0 }}
        >
          <Input.TextArea
            autoSize={{ minRows: 2, maxRows: 4 }}
            placeholder="Need to fill the position urgently"
          />
        </Form.Item>
      </div>

      <Divider style={{ margin: '16px 0' }} />

      <Text style={{ display: 'block', marginBottom: 12, fontSize: 14, fontWeight: 600 }}>
        Position Details ({type === 'NEW_HIRE' ? 'New Hire Positions' : 'Replacement Positions'})
      </Text>

      <Form.List name="details">
        {(fields, { add, remove }) => (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {fields.map((field, index) => (
              <Card
                key={field.key}
                size="small"
                title={`Position Detail #${index + 1}`}
                extra={
                  fields.length > 1 ? (
                    <Button
                      type="text"
                      danger
                      icon={<Trash2 size={14} />}
                      onClick={() => remove(field.name)}
                    >
                      Remove
                    </Button>
                  ) : null
                }
                style={{ background: '#ffffff', borderColor: '#e2e8f0' }}
              >
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                    gap: '0 16px',
                  }}
                >
                  {type === 'REPLACEMENT' ? (
                    <Form.Item
                      label="Replacing Employee"
                      name={[field.name, 'replacingEmployeeId']}
                      rules={[{ required: true, message: 'Pick who is being replaced' }]}
                    >
                      <Select
                        showSearch
                        optionFilterProp="label"
                        loading={myTeamQuery.isLoading}
                        placeholder="Pick who is being replaced"
                        options={(myTeamQuery.data?.content ?? []).map((e) => ({
                          value: e.id!,
                          label: [e.fullName, e.employeeCode].filter(Boolean).join(' · '),
                        }))}
                      />
                    </Form.Item>
                  ) : (
                    <>
                      <Form.Item
                        label="Section"
                        name={[field.name, 'sectionId']}
                      >
                        <Select
                          allowClear
                          showSearch
                          optionFilterProp="label"
                          loading={sectionsQuery.isLoading}
                          disabled={!departmentId}
                          placeholder={
                            departmentId ? 'Pick a section' : 'Pick department first'
                          }
                          options={(sectionsQuery.data ?? [])
                            .filter((s) => s.active !== false)
                            .map((s) => ({ value: s.id!, label: s.name }))}
                        />
                      </Form.Item>

                      <Form.Item
                        label="Designation"
                        name={[field.name, 'designationId']}
                        rules={[{ required: true, message: 'Pick a designation' }]}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          loading={designationsQuery.isLoading}
                          placeholder="Pick a designation"
                          options={(designationsQuery.data ?? [])
                            .filter((d) => d.active !== false)
                            .map((d) => ({ value: d.id!, label: d.title }))}
                        />
                      </Form.Item>

                      <Form.Item label="Grade" name={[field.name, 'gradeId']}>
                        <Select
                          allowClear
                          showSearch
                          optionFilterProp="label"
                          loading={gradesQuery.isLoading}
                          placeholder="Pick a grade"
                          options={(gradesQuery.data ?? [])
                            .filter((g) => g.active !== false)
                            .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
                            .map((g) => ({ value: g.id!, label: g.name }))}
                        />
                      </Form.Item>

                      <Form.Item
                        label="No. of Positions"
                        name={[field.name, 'noOfPosition']}
                        rules={[{ required: true, message: 'Enter positions count' }]}
                      >
                        <InputNumber min={1} style={{ width: '100%' }} />
                      </Form.Item>
                    </>
                  )}

                  <Form.Item
                    label="Employment Type"
                    name={[field.name, 'employmentType']}
                    rules={[{ required: true, message: 'Pick employment type' }]}
                  >
                    <Radio.Group optionType="button" buttonStyle="solid">
                      {EMPLOYMENT_TYPE_OPTIONS.map((t) => (
                        <Radio.Button key={t} value={t}>
                          {EMPLOYMENT_TYPE_LABEL[t]}
                        </Radio.Button>
                      ))}
                    </Radio.Group>
                  </Form.Item>

                  <Form.Item
                    label="Employee Category"
                    name={[field.name, 'employeeCategory']}
                    rules={[{ required: true, message: 'Pick employee category' }]}
                  >
                    <Select
                      placeholder="Select category"
                      options={EMP_CATEGORY_OPTIONS}
                    />
                  </Form.Item>

                  <Form.Item
                    label="Gender"
                    name={[field.name, 'employeeGender']}
                    rules={[{ required: true, message: 'Pick gender' }]}
                  >
                    <Radio.Group optionType="button" buttonStyle="solid">
                      {GENDER_OPTIONS.map((g) => (
                        <Radio.Button key={g.value} value={g.value}>
                          {g.label}
                        </Radio.Button>
                      ))}
                    </Radio.Group>
                  </Form.Item>

                  <Form.Item
                    label="Required Date"
                    name={[field.name, 'requiredDate']}
                    rules={[{ required: true, message: 'Select required date' }]}
                  >
                    <DatePicker
                      style={{ width: '100%' }}
                      format="DD MMM YYYY"
                      disabledDate={(d) => !!d && d.isBefore(dayjs(), 'day')}
                    />
                  </Form.Item>

                  <Form.Item
                    label="Shift"
                    name={[field.name, 'shiftId']}
                  >
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      loading={shiftsQuery.isLoading}
                      placeholder="Select a shift"
                      options={(shiftsQuery.data ?? [])
                        .filter((s) => s.active !== false)
                        .map((s) => ({
                          value: s.id,
                          label: `${s.name} (${s.startTime} - ${s.endTime})`,
                        }))}
                    />
                  </Form.Item>
                </div>
              </Card>
            ))}

            <Button
              type="dashed"
              onClick={() =>
                add({
                  noOfPosition: 1,
                  employmentType: 'PERMANENT',
                  employeeCategory: 'DIRECT',
                  employeeGender: 'MALE',
                  requiredDate: dayjs(),
                })
              }
              block
              icon={<Plus size={14} />}
            >
              Add More employees
            </Button>
          </div>
        )}
      </Form.List>
    </DrawerForm>
  );
}
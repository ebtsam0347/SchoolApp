// Single modal driving all 5 change-* flows (manager / department /
// section / designation / grade). The `kind` prop selects which
// catalog to load, which API to hit, and which label/options to render.
//
// On success we patch the cached BFF detail synchronously from the
// mutation response + the catalog data the modal already has, *then*
// invalidate so the eventual fresh BFF response reconciles in the
// background. The composite endpoint caches and busts on Kafka events,
// which lag our write by a beat — without the optimistic patch the
// strip would briefly carry a stale label.

import { useState } from 'react';
import { Alert, App, Form, Input, Modal, Select, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  catalogsApi,
  employeesApi,
  invalidateEmployeeReads,
  patchEmployeeDetail,
  type AssignmentChangeRequest,
  type EmployeeDto,
} from '@/people';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { KIND_LABEL, type ChangeKind } from './format';
import { pickResolvedPatch } from './pick-resolved-patch';

const { Paragraph } = Typography;

export type ChangeAssignmentModalProps = {
  kind: ChangeKind;
  employeeId: number;
  currentValueId?: number;
  /** Friendly label for `currentValueId` so the Select renders a name on
   *  open instead of the bare numeric id. Especially needed for `manager`,
   *  whose option list only loads after a search. */
  currentValueLabel?: string;
  currentDepartmentId?: number;
  onClose: () => void;
};

export function ChangeAssignmentModal({
  kind,
  employeeId,
  currentValueId,
  currentValueLabel,
  currentDepartmentId,
  onClose,
}: ChangeAssignmentModalProps) {
  const [form] = Form.useForm<{ targetId: number; notes?: string }>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [topLevel, setTopLevel] = useState<string | null>(null);
  const [managerSearch, setManagerSearch] = useState('');

  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
    enabled: kind === 'department',
  });
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections', currentDepartmentId],
    queryFn: ({ signal }) => catalogsApi.sections(currentDepartmentId, signal),
    staleTime: 5 * 60 * 1000,
    enabled: kind === 'section',
  });
  const designationsQuery = useQuery({
    queryKey: ['catalog', 'designations'],
    queryFn: ({ signal }) => catalogsApi.designations(signal),
    staleTime: 5 * 60 * 1000,
    enabled: kind === 'designation',
  });
  const gradesQuery = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: 5 * 60 * 1000,
    enabled: kind === 'grade',
  });
  const managerSearchQuery = useQuery({
    queryKey: ['employees', 'manager-search', managerSearch],
    queryFn: ({ signal }) =>
      employeesApi.list({ q: managerSearch, size: 10 }, signal),
    enabled: kind === 'manager' && managerSearch.length >= 2,
    staleTime: 30_000,
  });

  const apiCall: (body: AssignmentChangeRequest) => Promise<EmployeeDto> =
    kind === 'manager'
      ? (body) => employeesApi.changeManager(employeeId, body)
      : kind === 'department'
        ? (body) => employeesApi.changeDepartment(employeeId, body)
        : kind === 'section'
          ? (body) => employeesApi.changeSection(employeeId, body)
          : kind === 'designation'
            ? (body) => employeesApi.changeDesignation(employeeId, body)
            : (body) => employeesApi.changeGrade(employeeId, body);

  const mutation = useMutation({
    mutationFn: apiCall,
    onSuccess: (response, variables) => {
      message.success(
        `${KIND_LABEL[kind][0]!.toUpperCase() + KIND_LABEL[kind].slice(1)} updated.`,
      );
      const targetId = variables.targetId;
      patchEmployeeDetail(qc, employeeId, {
        employee: response,
        resolved: pickResolvedPatch(kind, targetId, {
          departments: departmentsQuery.data,
          sections: sectionsQuery.data,
          designations: designationsQuery.data,
          grades: gradesQuery.data,
          managers: managerSearchQuery.data?.content,
        }),
      });
      invalidateEmployeeReads(qc, employeeId);
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setTopLevel(getErrorMessage(e));
      }
    },
  });

  const options = (() => {
    let base: { value: number; label: string }[];
    if (kind === 'department') {
      base = (departmentsQuery.data ?? [])
        .filter((d) => d.active !== false)
        .map((d) => ({
          value: d.id!,
          label: `${d.name} · ${d.code ?? ''}`.trim(),
        }));
    } else if (kind === 'section') {
      base = (sectionsQuery.data ?? [])
        .filter((s) => s.active !== false)
        .map((s) => ({
          value: s.id!,
          label: `${s.name} · ${s.code ?? ''}`.trim(),
        }));
    } else if (kind === 'designation') {
      base = (designationsQuery.data ?? [])
        .filter((d) => d.active !== false)
        .map((d) => ({
          value: d.id!,
          label: `${d.title} · ${d.code ?? ''}`.trim(),
        }));
    } else if (kind === 'grade') {
      base = (gradesQuery.data ?? [])
        .filter((g) => g.active !== false)
        .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
        .map((g) => ({
          value: g.id!,
          label: `${g.name} · ${g.code ?? ''}`.trim(),
        }));
    } else {
      base = (managerSearchQuery.data?.content ?? []).map((emp) => ({
        value: emp.id!,
        label: `${emp.fullName} · ${emp.employeeCode}`,
      }));
    }
    // Seed with the current value's label so the Select renders a name on
    // open, even before the catalog (or manager-search) loads. Otherwise the
    // bare numeric id leaks into the field.
    if (
      currentValueId &&
      currentValueLabel &&
      !base.some((o) => o.value === currentValueId)
    ) {
      return [{ value: currentValueId, label: currentValueLabel }, ...base];
    }
    return base;
  })();

  return (
    <Modal
      open
      title={`Change ${KIND_LABEL[kind]}`}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Apply change"
      confirmLoading={mutation.isPending}
      destroyOnHidden
      width={520}
    >
      <Paragraph type="secondary">
        Saved changes appear on the assignment history below.
        {kind === 'manager'
          ? ' The new manager must not be in this employee’s reporting line.'
          : kind === 'section'
            ? ' Section must belong to the current department.'
            : ''}
      </Paragraph>
      {topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ targetId: currentValueId }}
        onFinish={(values) => {
          setTopLevel(null);
          mutation.mutate({
            targetId: values.targetId,
            notes: values.notes?.trim() || undefined,
          });
        }}
      >
        <Form.Item
          name="targetId"
          label={`New ${KIND_LABEL[kind]}`}
          rules={[{ required: true, message: 'Pick a target' }]}
        >
          {kind === 'manager' ? (
            <Select
              showSearch
              filterOption={false}
              onSearch={(v) => setManagerSearch(v)}
              loading={managerSearchQuery.isFetching}
              placeholder="Search by name or employee code"
              notFoundContent={
                managerSearch.length < 2
                  ? 'Type at least 2 characters'
                  : managerSearchQuery.isFetching
                    ? 'Searching…'
                    : 'No matches'
              }
              options={options}
            />
          ) : (
            <Select
              showSearch
              optionFilterProp="label"
              loading={
                kind === 'department'
                  ? departmentsQuery.isLoading
                  : kind === 'section'
                    ? sectionsQuery.isLoading
                    : kind === 'designation'
                      ? designationsQuery.isLoading
                      : gradesQuery.isLoading
              }
              options={options}
              placeholder={`Pick a ${KIND_LABEL[kind]}`}
            />
          )}
        </Form.Item>
        <Form.Item
          name="notes"
          label="Notes (optional)"
          extra="Recorded on the history row."
        >
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

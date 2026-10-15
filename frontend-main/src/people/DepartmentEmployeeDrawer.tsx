// Read-only profile for one colleague in the caller's department.
//
// Everything rendered here comes from the row the roster already loaded
// (`GET /employees/me/department/employees`) — there is no per-employee
// fetch, because `GET /employees/{id}` requires `employee.read` and the
// people this drawer exists for don't have it.
//
// That row carries catalog ids rather than labels, so designation / section /
// grade / city are resolved against the shared catalogs (auth-only, cached
// under the same query keys the rest of the app uses, so they're usually
// already warm).
//
// National identifiers — CNIC, NTN, EOBI, insurance and SSC numbers — are on
// the payload but deliberately NOT shown: this drawer is visible to every
// colleague in a department, which is a wider audience than an HR screen.
// Add them here if that's the intent.

import { useQuery } from '@tanstack/react-query';
import { Tag, Typography } from 'antd';
import type { ReactNode } from 'react';

import { DetailDrawer, KvList, PersonCell, StatusPill } from '@/components';
import { formatDateShort } from '@/lib/dates';
import { catalogsApi } from './api';
import type { EmployeeDto } from './types';

const { Text } = Typography;

export type DepartmentEmployeeDrawerProps = {
  open: boolean;
  onClose: () => void;
  /** The roster row to show. Null closes the drawer. */
  employee: EmployeeDto | null;
  /** Department name, already known by the caller (every row shares it). */
  departmentName?: string | null;
};

/** ACTIVE reads as good; anything else is worth flagging. */
const STATUS_TONE: Record<string, 'success' | 'warning' | 'neutral'> = {
  ACTIVE: 'success',
  ON_NOTICE: 'warning',
  SUSPENDED: 'warning',
  PROBATION: 'warning',
  TERMINATED: 'neutral',
  RESIGNED: 'neutral',
};

export function DepartmentEmployeeDrawer({
  open,
  onClose,
  employee,
  departmentName,
}: DepartmentEmployeeDrawerProps) {
  const enabled = open && !!employee;

  const designationsQuery = useQuery({
    queryKey: ['catalog', 'designations'],
    queryFn: ({ signal }) => catalogsApi.designations(signal),
    staleTime: 5 * 60_000,
    enabled,
  });
  const gradesQuery = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: 5 * 60_000,
    enabled,
  });
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections', employee?.departmentId],
    queryFn: ({ signal }) => catalogsApi.sections(employee?.departmentId, signal),
    staleTime: 5 * 60_000,
    enabled: enabled && employee?.departmentId != null,
  });
  const citiesQuery = useQuery({
    queryKey: ['catalog', 'cities'],
    queryFn: ({ signal }) => catalogsApi.cities(undefined, signal),
    staleTime: 5 * 60_000,
    enabled: enabled && employee?.cityId != null,
  });

  const designation =
    designationsQuery.data?.find((d) => d.id === employee?.designationId)?.title ?? null;
  const grade = gradesQuery.data?.find((g) => g.id === employee?.gradeId)?.code ?? null;
  const section =
    sectionsQuery.data?.find((s) => s.id === employee?.sectionId)?.name ?? null;
  const city = citiesQuery.data?.find((c) => c.id === employee?.cityId)?.name ?? null;

  return (
    <DetailDrawer
      open={open}
      onClose={onClose}
      width="min(620px, 100vw)"
      title={employee?.fullName ?? 'Employee'}
      extra={
        employee?.employmentStatus ? (
          <StatusPill tone={STATUS_TONE[employee.employmentStatus] ?? 'neutral'}>
            {prettyEnum(employee.employmentStatus)}
          </StatusPill>
        ) : undefined
      }
    >
      {!employee ? null : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <PersonCell
            size="large"
            person={{
              fullName: employee.fullName ?? `#${employee.id}`,
              employeeCode: employee.employeeCode,
              designation,
              profilePictureUrl: employee.profilePictureUrl,
            }}
          />

          <Group title="Work">
            <KvList
              items={[
                { label: 'Employee code', value: text(employee.employeeCode) },
                { label: 'Department', value: text(departmentName) },
                { label: 'Section', value: text(section) },
                { label: 'Designation', value: text(designation) },
                { label: 'Grade', value: text(grade) },
                { label: 'Employment type', value: text(prettyEnum(employee.employmentType)) },
                { label: 'Date of joining', value: date(employee.joiningDate) },
                { label: 'Confirmed on', value: date(employee.confirmationDate) },
                {
                  label: 'Direct reports',
                  value: employee.directReportsCount ? String(employee.directReportsCount) : '—',
                },
                {
                  label: 'Self-service',
                  value: employee.hasEss ? (
                    <Tag color="green">Enabled</Tag>
                  ) : (
                    <Tag>Not enabled</Tag>
                  ),
                },
              ]}
            />
          </Group>

          <Group title="Contact">
            <KvList
              items={[
                { label: 'Work email', value: text(employee.workEmail) },
                { label: 'Personal email', value: text(employee.personalEmail) },
                { label: 'Phone', value: text(employee.phone) },
                { label: 'City', value: text(city) },
                { label: 'Country', value: text(employee.country) },
                {
                  label: 'Current address',
                  value: text(employee.currentAddress),
                  span: 2,
                },
                {
                  label: 'Permanent address',
                  value: text(employee.permanentAddress),
                  span: 2,
                },
              ]}
            />
          </Group>

          <Group title="Personal">
            <KvList
              items={[
                { label: 'Father name', value: text(employee.fatherName) },
                { label: 'Mother name', value: text(employee.motherName) },
                { label: 'Spouse name', value: text(employee.spouseName) },
                { label: 'Date of birth', value: date(employee.dateOfBirth) },
                { label: 'Gender', value: text(prettyEnum(employee.gender)) },
                { label: 'Marital status', value: text(prettyEnum(employee.maritalStatus)) },
                { label: 'Religion', value: text(employee.religion) },
                { label: 'Blood group', value: text(employee.bloodGroup) },
              ]}
            />
          </Group>
        </div>
      )}
    </DetailDrawer>
  );
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <Text
        type="secondary"
        style={{
          fontSize: 11,
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          display: 'block',
          marginBottom: 10,
        }}
      >
        {title}
      </Text>
      {children}
    </div>
  );
}

function text(v: string | null | undefined): string {
  return v?.trim() ? v : '—';
}

function date(v: string | null | undefined): string {
  return v ? formatDateShort(v) : '—';
}

/** ON_NOTICE → "On notice". Leaves already-pretty values alone. */
function prettyEnum(v: string | null | undefined): string | null {
  if (!v) return null;
  if (!/^[A-Z0-9_]+$/.test(v)) return v;
  const lower = v.toLowerCase().replace(/_/g, ' ');
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

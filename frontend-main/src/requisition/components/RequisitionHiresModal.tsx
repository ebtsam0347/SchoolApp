// "Employees hired against this requisition" — there is no backend link
// between a requisition and the employee(s) onboarded to fill it (no
// requisitionId on EmployeeDto, no dedicated endpoint), so this is an
// approximate, client-side match: same department/section/designation/
// grade, joined on or after the requisition's approval. Good enough for a
// quick sanity check; not a source of truth for headcount reconciliation.

import { useMemo } from 'react';
import { Alert, Modal, Table, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';

import { PersonCell, RequestState } from '@/components';
import { formatDateShort } from '@/lib/dates';
import {
  employeesApi,
  EMPLOYMENT_TYPE_LABEL,
  type DepartmentDto,
  type DesignationDto,
  type EmployeeDto,
  type GradeDto,
  type SectionDto,
} from '@/people';
import type { RequisitionDto } from '../types';

const { Paragraph } = Typography;

export type RequisitionHiresModalProps = {
  row: RequisitionDto | null;
  onClose: () => void;
  departments?: DepartmentDto[];
  sections?: SectionDto[];
  designations?: DesignationDto[];
  grades?: GradeDto[];
};

export function RequisitionHiresModal({
  row,
  onClose,
  departments,
  sections,
  designations,
  grades,
}: RequisitionHiresModalProps) {
  const query = useQuery({
    queryKey: [
      'requisitions',
      'hires',
      row?.id,
      row?.departmentId,
      row?.sectionId,
      row?.designationId,
      row?.gradeId,
    ],
    queryFn: ({ signal }) =>
      employeesApi.list(
        {
          departmentId: row?.departmentId ?? undefined,
          sectionId: row?.sectionId ?? undefined,
          designationId: row?.designationId ?? undefined,
          gradeId: row?.gradeId ?? undefined,
          size: 100,
        },
        signal,
      ),
    enabled: !!row,
  });

  const since = row?.approvedAt ?? row?.createdAt;

  const matches = useMemo(() => {
    const all = query.data?.content ?? [];
    return all
      .filter((e) => e.id !== row?.replacingEmployeeId)
      .filter((e) => !since || !e.joiningDate || !dayjs(e.joiningDate).isBefore(dayjs(since), 'day'))
      .sort((a, b) => (a.joiningDate ?? '').localeCompare(b.joiningDate ?? ''));
  }, [query.data, row, since]);

  const deptName = (id?: number) => departments?.find((d) => d.id === id)?.name ?? '—';
  const sectionName = (id?: number) => sections?.find((s) => s.id === id)?.name ?? '—';
  const designationTitle = (id?: number) => designations?.find((d) => d.id === id)?.title;
  const gradeName = (id?: number) => grades?.find((g) => g.id === id)?.name ?? '—';

  return (
    <Modal
      open={!!row}
      onCancel={onClose}
      title={row ? `Employees hired — Req #${row.id}` : ''}
      footer={null}
      width={760}
      destroyOnHidden
    >
      {row ? (
        <>
          <Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 16 }}>
            Approximate match: employees in the same department, section,
            designation and grade who joined on or after this requisition was
            approved. Verify manually — this isn't a tracked link.
          </Paragraph>

          <RequestState
            loading={query.isLoading}
            variant="table"
            error={query.isError ? query.error : undefined}
            errorTitle="Couldn't load employees"
            onRetry={() => query.refetch()}
            empty={!query.isLoading && matches.length === 0}
            emptyFallback={
              <Alert type="info" showIcon message="No matching hires found yet" />
            }
          >
            <Table<EmployeeDto>
              rowKey="id"
              size="small"
              pagination={false}
              // Scroll wide columns inside the table, not the modal.
              scroll={{ x: 'max-content' }}
              dataSource={matches}
              columns={[
                {
                  title: 'Employee',
                  key: 'employee',
                  render: (_v, e) => (
                    <PersonCell
                      size="compact"
                      to={e.id ? `/people/${e.id}` : undefined}
                      person={{
                        fullName: e.fullName ?? '—',
                        employeeCode: e.employeeCode,
                        designation: designationTitle(e.designationId),
                        profilePictureUrl: e.profilePictureUrl,
                      }}
                    />
                  ),
                },
                {
                  title: 'Department',
                  key: 'department',
                  render: (_v, e) => deptName(e.departmentId),
                },
                {
                  title: 'Section',
                  key: 'section',
                  render: (_v, e) => sectionName(e.sectionId),
                },
                {
                  title: 'Grade',
                  key: 'grade',
                  render: (_v, e) => gradeName(e.gradeId),
                },
                {
                  title: 'Employment',
                  key: 'employmentType',
                  render: (_v, e) =>
                    e.employmentType ? EMPLOYMENT_TYPE_LABEL[e.employmentType] : '—',
                },
                {
                  title: 'Joined',
                  key: 'joiningDate',
                  render: (_v, e) => (e.joiningDate ? formatDateShort(e.joiningDate) : '—'),
                },
              ]}
            />
          </RequestState>
        </>
      ) : null}
    </Modal>
  );
}

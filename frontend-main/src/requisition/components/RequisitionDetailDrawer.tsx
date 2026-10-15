// Drawer that opens when the user clicks a requisition row — mirrors
// LeaveDetailDrawer's pattern (facts + status up top, row-scoped actions
// pinned to the footer) so the two request types read as one system.
//
// No extra fetch: the list query already returns the full RequisitionDto,
// so this takes the row directly instead of re-querying by id.
//
// The approval chain (dept head -> D-2 grade sign-off -> Director for
// NEW_HIRE; dept head -> HR -> D-2 grade sign-off for REPLACEMENT) is
// entirely server-driven — `row.steps`/`row.currentStage` already carry its
// live state, so this just renders them via the same vertical timeline the
// leave detail drawer uses, instead of hardcoding a single stage.

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Divider, Input, Spin, Table, Typography, type TableProps } from 'antd';
import { Users } from 'lucide-react';
import { useState, type ReactNode } from 'react';

import { shiftsApi } from '@/attendance';
import { ApprovalStepsTimeline } from '@/attendance/leaves';
import { DetailDrawer, KvList, RejectReasonModal, Section, StatusPill } from '@/components';
import {
  EMPLOYMENT_TYPE_LABEL,
  type DepartmentDto,
  type DesignationDto,
  type EmploymentType,
  type GradeDto,
  type SectionDto,
} from '@/people';
import { formatDateShort } from '@/lib/dates';
import {
  REQUISITION_STATUS_LABEL,
  REQUISITION_STATUS_TONE,
  REQUISITION_TYPE_LABEL,
} from '../format';
import { requisitionApi } from '../api';
import type { RequisitionDto, RequisitionEmployeeDetailDto } from '../types';
import { RequisitionRowActions } from './RequisitionRowActions';
import { RequisitionDetailEditModal } from './RequisitionDetailEditModal';

const { Text } = Typography;

export type RequisitionDetailDrawerProps = {
  row: RequisitionDto | null;
  onClose: () => void;
  onEdit: (row: RequisitionDto) => void;
  onViewHires?: (row: RequisitionDto) => void;
  departments?: DepartmentDto[];
  sections?: SectionDto[];
  designations?: DesignationDto[];
  grades?: GradeDto[];
  /** Hide the Cancel action in the footer — e.g. in an approvals queue,
   * which is an approver's view, not the requester's own management view. */
  hideCancel?: boolean;
  /**
   * Approver's read-only view of the position rows (approvals inbox): no
   * placement columns (New Employee Code / Temporary Employee Reference) and
   * no row actions — except Lean Remove at stage 1 (Manufacturing Excellence
   * Review). Omit on pages that fill in placement codes after approval.
   */
  approvalView?: boolean;
};

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text
        type="secondary"
        style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em' }}
      >
        {label}
      </Text>
      <div style={{ fontSize: 13, marginTop: 4 }}>{children}</div>
    </div>
  );
}

export function RequisitionDetailDrawer({
  row,
  onClose,
  onEdit,
  onViewHires,
  departments,
  sections,
  designations,
  grades,
  hideCancel,
  approvalView,
}: RequisitionDetailDrawerProps) {
  const open = row != null;
  const queryClient = useQueryClient();
  const [detailsSearch, setDetailsSearch] = useState('');
  const [removingDetail, setRemovingDetail] = useState<RequisitionEmployeeDetailDto | null>(null);
  const [editingDetail, setEditingDetail] = useState<RequisitionEmployeeDetailDto | null>(null);
  const detailsQuery = useQuery({
    queryKey: ['requisitions', row?.id, 'details'],
    queryFn: ({ signal }) => requisitionApi.details(row?.id ?? 0, signal),
    enabled: open,
    staleTime: 30_000,
  });
  // The details rows carry only `shiftId` — names come from the shift
  // catalog (same key the requisition form uses, so usually cached).
  const shiftsQuery = useQuery({
    queryKey: ['shifts'],
    queryFn: ({ signal }) => shiftsApi.list(signal),
    enabled: open,
    staleTime: 5 * 60_000,
  });
  const shiftName = (id?: number) =>
    id == null ? undefined : (shiftsQuery.data?.find((sh) => sh.id === id)?.name ?? `#${id}`);
  const leanRemoveMutation = useMutation({
    mutationFn: (reason: string) => requisitionApi.leanRemove(row?.id ?? 0, removingDetail?.id ?? 0, reason),
    onSuccess: () => {
      setRemovingDetail(null);
      void queryClient.invalidateQueries({ queryKey: ['requisitions', row?.id, 'details'] });
      void queryClient.invalidateQueries({ queryKey: ['requisitions'] });
      void queryClient.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
    },
  });
  const deptName = departments?.find((d) => d.id === row?.departmentId)?.name;
  const canRemoveLeanPosition =
    row?.type === 'NEW_HIRE' &&
    row.currentStage?.stageNo === 1 &&
    row.currentStage.stageRole === 'Manufacturing Excellence Review';

  const totalPositionsHod =
    row?.totalPositionsHod ??
    row?.total_position_hod ??
    (row?.details && row.details.length > 0
      ? row.details.reduce((sum, d) => sum + (d.noOfPosition || 0), 0)
      : row?.vacancies);

  const totalPositionLean = row?.totalPositionLean;
  const hodNotesVal = row?.hotNotes || row?.hodNotes || row?.notes;
  const normalizedDetailsSearch = detailsSearch.trim().toLowerCase();
  const employeeDetails = (detailsQuery.data ?? []).filter((detail) =>
    [
      detail.replacingEmployeeCode,
      detail.replacingEmployeeName,
      detail.newEmployeeCode,
      detail.temporaryEmployeeReference,
      detail.sectionName,
      detail.designationTitle,
      detail.gradeName,
      detail.employmentType,
      detail.employeeCategory,
      detail.employeeGender,
      shiftName(detail.shiftId),
    ].some((value) => value?.toLowerCase().includes(normalizedDetailsSearch)),
  );
  type DetailColumn = NonNullable<TableProps<RequisitionEmployeeDetailDto>['columns']>[number];
  const orDash = (value: unknown) => (value == null || value === '' ? '—' : (value as ReactNode));

  const sectionColumn: DetailColumn = {
    title: 'Section',
    dataIndex: 'sectionName',
    key: 'sectionName',
    width: 150,
    render: orDash,
  };
  const designationColumn: DetailColumn = {
    title: 'Designation',
    dataIndex: 'designationTitle',
    key: 'designationTitle',
    width: 180,
    render: orDash,
  };
  const employmentColumn: DetailColumn = {
    title: 'Employment',
    dataIndex: 'employmentType',
    key: 'employmentType',
    width: 130,
    render: (value: EmploymentType | undefined) => (value ? EMPLOYMENT_TYPE_LABEL[value] : '—'),
  };
  const gradeColumn: DetailColumn = {
    title: 'Grade',
    dataIndex: 'gradeName',
    key: 'gradeName',
    width: 110,
    render: orDash,
  };
  const requiredUntilColumn: DetailColumn = {
    title: 'Required Until',
    dataIndex: 'requiredUntil',
    key: 'requiredUntil',
    width: 140,
    render: (value) => (value ? formatDateShort(value) : '—'),
  };
  // Placement identifiers filled in once a candidate is lined up — edited
  // through the trailing "Action" column's modal.
  const placementColumns: DetailColumn[] = [
    { title: 'New Employee Code', dataIndex: 'newEmployeeCode', key: 'newEmployeeCode', width: 170, render: orDash },
    {
      title: 'Temporary Employee Reference',
      dataIndex: 'temporaryEmployeeReference',
      key: 'temporaryEmployeeReference',
      width: 200,
      render: orDash,
    },
  ];
  const editDetailColumn: DetailColumn = {
    title: 'Action',
    key: 'editDetailAction',
    fixed: 'right',
    width: 90,
    render: (_value, detail) => (
      <Button size="small" onClick={() => setEditingDetail(detail)}>
        Edit
      </Button>
    ),
  };
  // At the Manufacturing Excellence review the reviewer only trims Lean
  // positions — placement codes and their Edit action aren't relevant yet.
  // Approvers never fill placement codes, so their view drops them entirely.
  const showPlacement = !approvalView && !canRemoveLeanPosition;
  const trailingNewHireAction: DetailColumn[] = canRemoveLeanPosition
    ? [
        {
          title: 'Action',
          key: 'leanRemoveAction',
          fixed: 'right',
          width: 110,
          render: (_value, detail) => (
            <Button
              danger
              size="small"
              disabled={detail.leanRemoved === true}
              onClick={() => setRemovingDetail(detail)}
            >
              Remove
            </Button>
          ),
        },
      ]
    : approvalView
      ? []
      : [editDetailColumn];

  const employeeDetailsColumns: DetailColumn[] =
    row?.type === 'NEW_HIRE'
      ? [
          sectionColumn,
          designationColumn,
          {
            title: 'Shift',
            dataIndex: 'shiftId',
            key: 'shiftId',
            width: 140,
            render: (value?: number) => orDash(shiftName(value)),
          },
          { title: 'Category', dataIndex: 'employeeCategory', key: 'employeeCategory', width: 120, render: orDash },
          { title: 'Gender', dataIndex: 'employeeGender', key: 'employeeGender', width: 100, render: orDash },
          employmentColumn,
          gradeColumn,
          requiredUntilColumn,
          ...(showPlacement ? placementColumns : []),
          {
            title: 'Lean Removed',
            dataIndex: 'leanRemoved',
            key: 'leanRemoved',
            width: 130,
            render: (value) =>
              value == null ? (
                '—'
              ) : (
                <StatusPill tone={value ? 'success' : 'neutral'}>{value ? 'Yes' : 'No'}</StatusPill>
              ),
          },
          {
            title: 'Lean Removed Remarks',
            dataIndex: 'leanRemovedRemarks',
            key: 'leanRemovedRemarks',
            width: 220,
            render: orDash,
          },
          ...trailingNewHireAction,
        ]
      : [
          {
            title: 'Replacing Employee Code',
            dataIndex: 'replacingEmployeeCode',
            key: 'replacingEmployeeCode',
            width: 180,
            render: orDash,
          },
          {
            title: 'Replacing Employee Name',
            dataIndex: 'replacingEmployeeName',
            key: 'replacingEmployeeName',
            width: 220,
            render: orDash,
          },
          sectionColumn,
          designationColumn,
          employmentColumn,
          gradeColumn,
          requiredUntilColumn,
          ...(approvalView ? [] : [...placementColumns, editDetailColumn]),
        ];

  return (
    <DetailDrawer
      title={row ? `Requisition #${row.id}` : 'Requisition'}
      open={open}
      onClose={onClose}
      width="clamp(400px, 90vw, 960px)"
      extra={
        row ? (
          <StatusPill tone={REQUISITION_STATUS_TONE[row.status]} size="md">
            {REQUISITION_STATUS_LABEL[row.status]}
          </StatusPill>
        ) : undefined
      }
      footer={
        row ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <RequisitionRowActions
              row={row}
              onEdit={(r) => {
                onEdit(r);
                onClose();
              }}
              hideCancel={hideCancel}
            />
          </div>
        ) : undefined
      }
    >
      {row ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Head Details */}
          <Section title="Requisition Head Details" level={5}>
            <KvList
              items={[
                { label: 'Type', value: REQUISITION_TYPE_LABEL[row.type] },
                { label: 'Department', value: deptName ?? '—' },
                {
                  label: 'Total Positions (HOD)',
                  value: totalPositionsHod != null ? totalPositionsHod : '—',
                },
                ...(totalPositionLean != null
                  ? [{ label: 'Total Positions (Lean)', value: totalPositionLean }]
                  : []),
                { label: 'Requested Date', value: formatDateShort(row.createdAt) },
                ...(row.requestReason || row.reason
                  ? [{ label: 'Request Reason', value: row.requestReason || row.reason }]
                  : []),
                ...(row.minimumEducation
                  ? [{ label: 'Minimum Education', value: row.minimumEducation }]
                  : []),
                ...(row.minimumExperience != null
                  ? [{ label: 'Minimum Experience', value: `${row.minimumExperience} year(s)` }]
                  : []),
                ...(row.minimumSkill
                  ? [{ label: 'Minimum Skill', value: row.minimumSkill }]
                  : []),
              ]}
              minColumnWidth={160}
              gap={14}
            />

            {row.jobDescription ? (
              <div style={{ marginTop: 12 }}>
                <Fact label="Job Description">{row.jobDescription}</Fact>
              </div>
            ) : null}

            {hodNotesVal ? (
              <div style={{ marginTop: 12 }}>
                <Fact label="HOD Notes / Remarks">{hodNotesVal}</Fact>
              </div>
            ) : null}
          </Section>

          <Divider style={{ margin: '4px 0' }} />

          {/* Position Details */}
          <Section title="Position Details" level={5}>
            {detailsQuery.isLoading ? (
              <div style={{ display: 'flex', justifyContent: 'center', padding: 24 }}>
                <Spin size="small" />
              </div>
            ) : detailsQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="Couldn't load employee details"
                action={
                  <Button size="small" onClick={() => detailsQuery.refetch()}>
                    Retry
                  </Button>
                }
              />
            ) : detailsQuery.data && detailsQuery.data.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <Input.Search
                  allowClear
                  placeholder="Search employees or positions"
                  value={detailsSearch}
                  onChange={(event) => setDetailsSearch(event.target.value)}
                  style={{ maxWidth: 320 }}
                />
                <Table<RequisitionEmployeeDetailDto>
                  columns={employeeDetailsColumns}
                  dataSource={employeeDetails}
                  rowKey="id"
                  size="small"
                  scroll={{ x: 'max-content' }}
                  pagination={{
                    pageSize: 10,
                    showSizeChanger: true,
                    showTotal: (total) => `${total} employee${total === 1 ? '' : 's'}`,
                  }}
                  locale={{ emptyText: 'No matching employees' }}
                />
              </div>
            ) : (
              /* Fallback single position details (e.g. for replacement requests) */
              <KvList
                items={[
                  {
                    label: 'Position',
                    value: designations?.find((d) => d.id === row.designationId)?.title ?? `#${row.designationId ?? '—'}`,
                  },
                  { label: 'Section', value: sections?.find((s) => s.id === row.sectionId)?.name ?? '—' },
                  { label: 'Grade', value: grades?.find((g) => g.id === row.gradeId)?.name ?? '—' },
                  {
                    label: 'Employment',
                    value: row.employmentType ? EMPLOYMENT_TYPE_LABEL[row.employmentType] : '—',
                  },
                  {
                    label: 'Vacancies',
                    value: onViewHires ? (
                      <Button
                        type="link"
                        size="small"
                        icon={<Users size={12} />}
                        style={{ padding: 0, height: 'auto' }}
                        onClick={() => onViewHires(row)}
                      >
                        {row.vacancies} · view hires
                      </Button>
                    ) : (
                      row.vacancies
                    ),
                  },
                  {
                    label: 'Required by',
                    value: row.requiredBy ? formatDateShort(row.requiredBy) : '—',
                  },
                ]}
                minColumnWidth={140}
                gap={12}
              />
            )}
          </Section>

          {row.status === 'REJECTED' && row.rejectReason ? (
            <Fact label="Rejection reason">{row.rejectReason}</Fact>
          ) : null}

          {/* Approval chain timeline */}
          <Section title="Approval chain" level={5}>
            <ApprovalStepsTimeline steps={row.steps} currentStageNo={row.currentStage?.stageNo} />
          </Section>
        </div>
      ) : null}
      <RejectReasonModal
        open={removingDetail != null}
        title="Remove this position from Lean?"
        description="Provide the reason for removing this position."
        okText="Remove"
        placeholder="Removal reason (required)"
        onCancel={() => setRemovingDetail(null)}
        onConfirm={(reason) => leanRemoveMutation.mutateAsync(reason)}
        successMessage="Position removed from Lean"
      />
      {row ? (
        <RequisitionDetailEditModal
          requisitionId={row.id}
          detail={editingDetail}
          onClose={() => setEditingDetail(null)}
        />
      ) : null}
    </DetailDrawer>
  );
}

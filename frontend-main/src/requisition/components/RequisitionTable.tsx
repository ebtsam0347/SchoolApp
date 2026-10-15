import { Button, Space, Table, Tag, Typography, type TableProps } from 'antd';

import { StatusPill } from '@/components';
import {
  EMPLOYMENT_TYPE_LABEL,
  type DepartmentDto,
  type DesignationDto,
  type GradeDto,
  type SectionDto,
} from '@/people';
import { formatDateMonthDay } from '@/lib/dates';
import {
  REQUISITION_STATUS_LABEL,
  REQUISITION_STATUS_TONE,
  REQUISITION_TYPE_COLOR,
  REQUISITION_TYPE_LABEL,
} from '../format';
import type { RequisitionDto, RequisitionStatus } from '../types';
import { RequisitionRowActions } from './RequisitionRowActions';

const { Paragraph, Text } = Typography;

type TableColumn = NonNullable<TableProps<RequisitionDto>['columns']>[number];

export type RequisitionTableProps = {
  data: RequisitionDto[];
  loading: boolean;
  departments?: DepartmentDto[];
  sections?: SectionDto[];
  designations?: DesignationDto[];
  grades?: GradeDto[];
  onEdit: (row: RequisitionDto) => void;
  /**
   * Opens a read-only detail drawer for the row (mirrors LeaveTable: a slim
   * "Details" link + click-anywhere-on-row). When given, this replaces the
   * inline action-buttons column — row-scoped actions live in the drawer's
   * footer instead (see RequisitionDetailDrawer). Omit to keep the classic
   * inline-actions column, as the approvals inbox does.
   */
  onOpen?: (row: RequisitionDto) => void;
  /**
   * Adds a "Details" button next to the inline Approve/Reject actions —
   * for pages that want both, unlike `onOpen` which replaces them. Ignored
   * when `onOpen` is set.
   */
  onDetails?: (row: RequisitionDto) => void;
  pagination?: TableProps<RequisitionDto>['pagination'];
  onChange?: TableProps<RequisitionDto>['onChange'];
  /** Opt-in row checkboxes — e.g. for bulk approve/reject in the Approvals
   * Inbox. Omit to keep the table's default (no selection column). */
  rowSelection?: TableProps<RequisitionDto>['rowSelection'];
  /** Hide the Cancel action on every row — e.g. in an approvals queue. Only
   * applies to the inline-actions column (no effect when `onOpen` is set). */
  hideCancel?: boolean;
  /** Hide the Status column — e.g. in an approvals queue, which only ever
   * lists PENDING_APPROVAL rows and shows Stage instead. */
  hideStatus?: boolean;
  /** Per-status label overrides for the Status column (default:
   *  REQUISITION_STATUS_LABEL). */
  statusLabels?: Partial<Record<RequisitionStatus, string>>;
  /**
   * Approvals-inbox layout: Request ID + Request type first, like the other
   * inbox tables. The inbox composite's rows carry no per-position details
   * (designation / section / grade), so this layout shows only what they do
   * carry: department, HOD/Lean totals, reason + requirements, requester,
   * and the current stage with its assignee.
   */
  inboxLayout?: boolean;
};

export function RequisitionTable({
  data,
  loading,
  departments,
  sections,
  designations,
  grades,
  onEdit,
  onOpen,
  onDetails,
  pagination,
  onChange,
  hideCancel,
  hideStatus,
  statusLabels,
  rowSelection,
  inboxLayout,
}: RequisitionTableProps) {
  const deptName = (id?: number) => departments?.find((d) => d.id === id)?.name;
  const sectionName = (id?: number) => sections?.find((s) => s.id === id)?.name;
  const designationTitle = (id?: number) =>
    designations?.find((d) => d.id === id)?.title;
  const gradeName = (id?: number) => grades?.find((g) => g.id === id)?.name;

  const actionsCol: TableColumn = onOpen
    ? {
        title: '',
        key: 'details',
        width: 90,
        align: 'right',
        render: (_v, row) => (
          <Button size="small" type="link" onClick={() => onOpen(row)}>
            Details
          </Button>
        ),
      }
    : {
        title: '',
        key: 'rowActions',
        width: onDetails ? 320 : 240,
        align: 'right',
        render: (_v, row) => (
          <Space size={4} wrap={false}>
            {onDetails ? (
              <Button size="small" onClick={() => onDetails(row)}>
                Details
              </Button>
            ) : null}
            <RequisitionRowActions row={row} onEdit={onEdit} hideCancel={hideCancel} />
          </Space>
        ),
      };

  const statusLabel = (s: RequisitionStatus) =>
    statusLabels?.[s] ?? REQUISITION_STATUS_LABEL[s];
  // Pages that list every status (not just pending) keep the Status column;
  // `hideStatus` drops it.
  const statusCols: TableColumn[] = hideStatus
    ? []
    : [
        {
          title: 'Status',
          key: 'status',
          width: 140,
          render: (_v, row) => (
            <StatusPill tone={REQUISITION_STATUS_TONE[row.status]}>
              {statusLabel(row.status)}
            </StatusPill>
          ),
          sorter: (a, b) => statusLabel(a.status).localeCompare(statusLabel(b.status)),
        },
      ];

  const positionsLabel = (n: number) => `${n} ${n === 1 ? 'position' : 'positions'}`;

  const inboxCols: TableProps<RequisitionDto>['columns'] = [
    {
      title: 'Request',
      key: 'request',
      width: 140,
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <Tag color={REQUISITION_TYPE_COLOR[row.type]} style={{ width: 'fit-content' }}>
            {REQUISITION_TYPE_LABEL[row.type]}
          </Tag>
          <Text type="secondary" style={{ fontSize: 11 }}>
            ID #{row.id}
          </Text>
        </div>
      ),
      sorter: (a, b) => a.id - b.id,
    },
    // Status right after Request, like the other approvals-inbox tables.
    ...statusCols,
    {
      title: 'Department',
      key: 'department',
      width: 180,
      render: (_v, row) => <Text>{deptName(row.departmentId) ?? '—'}</Text>,
      sorter: (a, b) => (deptName(a.departmentId) ?? '').localeCompare(deptName(b.departmentId) ?? ''),
    },
    {
      title: 'Positions',
      key: 'positions',
      width: 160,
      render: (_v, row) => {
        const hod = row.totalPositionsHod ?? row.total_position_hod;
        const lean = row.totalPositionLean;
        if (hod == null && lean == null) return <Text type="secondary">—</Text>;
        return (
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.4 }}>
            {hod != null ? <Text>HOD: {positionsLabel(hod)}</Text> : null}
            {lean != null ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                Lean: {positionsLabel(lean)}
              </Text>
            ) : null}
          </div>
        );
      },
    },
    {
      title: 'Reason & Requirements',
      key: 'reasonRequirements',
      minWidth: 220,
      render: (_v, row) => {
        const requirements = [
          row.minimumEducation,
          row.minimumExperience != null ? `${row.minimumExperience}y exp` : null,
          row.minimumSkill,
        ]
          .filter(Boolean)
          .join(' · ');
        return (
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, gap: 2 }}>
            <Text style={{ fontSize: 13, fontWeight: 500 }}>
              {row.requestReason || row.reason || '—'}
            </Text>
            {requirements ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {requirements}
              </Text>
            ) : null}
          </div>
        );
      },
    },
    {
      title: 'Job Description',
      key: 'jobDescription',
      width: 260,
      // Free text that can run long — two lines in the row, the rest on hover.
      render: (_v, row) =>
        row.jobDescription ? (
          <Paragraph
            ellipsis={{ rows: 2, tooltip: row.jobDescription }}
            style={{ margin: 0, fontSize: 12, width: 240 }}
          >
            {row.jobDescription}
          </Paragraph>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'Requested',
      key: 'createdAt',
      width: 150,
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
          <Text style={{ fontSize: 13 }}>{formatDateMonthDay(row.createdAt)}</Text>
          {row.createdBy ? (
            <Text type="secondary" ellipsis style={{ fontSize: 11 }}>
              by {row.createdBy}
            </Text>
          ) : null}
        </div>
      ),
      sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
      defaultSortOrder: 'descend',
    },
    {
      title: 'Stage',
      key: 'stage',
      width: 160,
      render: (_v, row) =>
        row.currentStage ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Tag color="orange" style={{ marginRight: 0, width: 'fit-content' }}>
              {row.currentStage.stageNo}. {row.currentStage.stageRole}
            </Tag>
            {row.currentStage.assignedToName ? (
              <Text type="secondary" style={{ fontSize: 11 }}>
                {row.currentStage.assignedToName}
              </Text>
            ) : null}
          </div>
        ) : row.status === 'APPROVED' ? (
          <Text type="success" style={{ fontSize: 11, fontWeight: 500 }}>
            Approved from All Ends
          </Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 11 }}>
            —
          </Text>
        ),
    },
    actionsCol,
  ];

  const classicCols: TableProps<RequisitionDto>['columns'] = [
    {
      title: 'Type',
      key: 'type',
      width: 140,
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <Tag color={REQUISITION_TYPE_COLOR[row.type]} style={{ width: 'fit-content' }}>
            {REQUISITION_TYPE_LABEL[row.type]}
          </Tag>
          <Text type="secondary" style={{ fontSize: 11 }}>
            #{row.id}
          </Text>
        </div>
      ),
    },
    {
      title: 'HOD Position',
      key: 'position',
      render: (_v, row) => {
        const detail = row.details?.[0];
        const desigId = row.designationId ?? detail?.designationId;
        const secId = row.sectionId ?? detail?.sectionId;
        const grId = row.gradeId ?? detail?.gradeId;
        const empType = row.employmentType ?? detail?.employmentType;

        const totalPositionsHod =
          row.totalPositionsHod ??
          row.total_position_hod ??
          (row.details && row.details.length > 0
            ? row.details.reduce((sum, d) => sum + (d.noOfPosition || 0), 0)
            : row.vacancies);

        return (
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
              <Text ellipsis style={{ fontWeight: 500 }}>
                {row.details && row.details.length > 1
                  ? `${row.details.length} Positions`
                  : designationTitle(desigId) ?? `#${desigId ?? '—'}`}
              </Text>
              {totalPositionsHod != null ? (
                <Tag color="blue" style={{ fontSize: 11, margin: 0 }}>
                  {totalPositionsHod} {totalPositionsHod === 1 ? 'position' : 'positions'}
                </Tag>
              ) : null}
            </div>
            <Text type="secondary" ellipsis style={{ fontSize: 12, marginTop: 2 }}>
              {[deptName(row.departmentId), sectionName(secId), gradeName(grId)]
                .filter(Boolean)
                .join(' · ') || '—'}
              {empType ? ` · ${EMPLOYMENT_TYPE_LABEL[empType]}` : ''}
            </Text>
          </div>
        );
      },
    },
    {
      title: 'Lean Position',
      dataIndex: 'totalPositionLean',
      key: 'totalPositionLean',
      width: 150,
      render: (value, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, minWidth: 0 }}>
          {value != null ? (
            <Tag color="blue" style={{ width: 'fit-content', fontSize: 11, margin: 0 }}>
              {value} {value === 1 ? 'position' : 'positions'}
            </Tag>
          ) : (
            <Text type="secondary">—</Text>
          )}
          <Text type="secondary" ellipsis style={{ fontSize: 12, marginTop: 2 }}>
            {deptName(row.departmentId) ?? '—'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Reason & Requirements',
      key: 'headDetails',
      minWidth: 200,
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, gap: 2 }}>
          {row.requestReason || row.reason ? (
            <Text style={{ fontSize: 13, fontWeight: 500 }}>
              {row.requestReason || row.reason}
            </Text>
          ) : null}
          <Text type="secondary" style={{ fontSize: 12 }}>
            {[
              row.minimumEducation,
              row.minimumExperience != null ? `${row.minimumExperience}y exp` : null,
              row.minimumSkill,
            ]
              .filter(Boolean)
              .join(' · ') || '—'}
          </Text>
        </div>
      ),
    },
    ...(hideStatus
      ? []
      : [
          {
            title: 'Status',
            key: 'status',
            width: 140,
            render: (_v: unknown, row: RequisitionDto) => (
              <StatusPill tone={REQUISITION_STATUS_TONE[row.status]}>
                {REQUISITION_STATUS_LABEL[row.status]}
              </StatusPill>
            ),
          },
        ]),
    {
      title: 'Stage',
      key: 'stage',
      width: 150,
      render: (_v, row) =>
        row.currentStage ? (
          <Tag color="orange">
            {row.currentStage.stageNo}. {row.currentStage.stageRole}
          </Tag>
        ) : row.status === 'APPROVED' ? (
          <Text type="success" style={{ fontSize: 11, fontWeight: 500 }}>
            Approved from All Ends
          </Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 11 }}>
            —
          </Text>
        ),
    },
    {
      title: 'Requested Date',
      key: 'createdAt',
      width: 130,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatDateMonthDay(row.createdAt)}
        </Text>
      ),
    },
    actionsCol,
  ];

  return (
    <Table<RequisitionDto>
      rowKey={(r) => r.id}
      columns={inboxLayout ? inboxCols : classicCols}
      dataSource={data}
      loading={loading}
      size="middle"
      // ~950px of fixed column widths plus two flexible columns — keep the
      // overflow inside the table rather than on the page.
      scroll={{ x: 'max-content' }}
      pagination={pagination}
      onChange={onChange}
      rowSelection={rowSelection}
      onRow={
        onOpen
          ? (row) => ({
              onClick: () => onOpen(row),
              style: { cursor: 'pointer' },
            })
          : undefined
      }
    />
  );
}

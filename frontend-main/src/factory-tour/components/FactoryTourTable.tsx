import {
  App as AntdApp,
  Button,
  Grid,
  Space,
  Table,
  Tooltip,
  Typography,
  type ButtonProps,
  type TableProps,
} from 'antd';
import { Eye, HandCoins, ReceiptText, SendHorizonal, XCircle } from 'lucide-react';

import { StatusPill } from '@/components';
import type { DepartmentDto, DesignationDto, GradeDto, SectionDto } from '@/people';
import { formatDateTime } from '@/lib';
import {
  continentCountryFor,
  FACTORY_TOUR_STATUS_LABEL,
  factoryTourStatusTone,
  formatRawStatus,
} from '../format';
import type { FactoryTourDto } from '../types';

const { Text } = Typography;

/**
 * Server-computed `actions[]` is the source of truth, but the /travel API
 * does not always populate it — fall back to the one status where submitting
 * is valid so a draft never gets stranded without its button.
 */
function canSubmit(row: FactoryTourDto): boolean {
  return row.actions.includes('SUBMIT') || row.status === 'DRAFT';
}

/** Settlement only opens once the trip itself is done — the service rejects
 * /settlement/prepare before COMPLETED / SETTLEMENT_PENDING / SETTLED. */
const SETTLEMENT_STATUSES = new Set(['COMPLETED', 'SETTLEMENT_PENDING', 'SETTLED']);
function canSettle(row: FactoryTourDto): boolean {
  return Boolean(row.rawStatus && SETTLEMENT_STATUSES.has(row.rawStatus));
}

export type FactoryTourTableProps = {
  data: FactoryTourDto[];
  loading: boolean;
  departments?: DepartmentDto[];
  sections?: SectionDto[];
  designations?: DesignationDto[];
  grades?: GradeDto[];
  /** Omit to render read-only — e.g. reused inside the Approvals Inbox. */
  onCancel?: (row: FactoryTourDto) => void;
  cancellingId?: number;
  /** Adds a "Submit" button on draft rows — sends the request for approval. */
  onSubmit?: (row: FactoryTourDto) => void;
  submittingId?: number;
  /** Adds a "Details" button — opens a read-only drawer for the row. */
  onDetails?: (row: FactoryTourDto) => void;
  /** Adds an "Expenses" button on approved rows — the trip has to be
   * APPROVED or later before the service accepts a claim. */
  onExpenses?: (row: FactoryTourDto) => void;
  /** Adds a "Settlement" button once the trip is COMPLETED or later —
   * prepares/shows the participant's final settlement. */
  onSettlement?: (row: FactoryTourDto) => void;
  /** Opt-in row checkboxes — e.g. for bulk cancel on the self-service page.
   * Omit to keep the table's default (no selection column). */
  rowSelection?: TableProps<FactoryTourDto>['rowSelection'];
};

export function FactoryTourTable({
  data,
  loading,
  departments,
  sections,
  designations,
  grades,
  onCancel,
  cancellingId,
  onSubmit,
  submittingId,
  onDetails,
  onExpenses,
  onSettlement,
  rowSelection,
}: FactoryTourTableProps) {
  const { modal } = AntdApp.useApp();
  // Below md the row actions would eat the route/date columns, so they
  // collapse to icon-only buttons; the table scrolls sideways below that.
  const screens = Grid.useBreakpoint();
  const compact = !screens.md;

  /** Icon-only (label in a tooltip) once the viewport gets narrow. */
  const actionButton = (label: string, props: ButtonProps) =>
    compact ? (
      <Tooltip title={label}>
        <Button {...props} aria-label={label} />
      </Tooltip>
    ) : (
      <Button {...props}>{label}</Button>
    );

  const deptName = (id?: number | null) => departments?.find((d) => d.id === id)?.name ?? '—';
  const sectionName = (id?: number | null) => sections?.find((s) => s.id === id)?.name ?? '—';
  const designationTitle = (id?: number | null) =>
    designations?.find((d) => d.id === id)?.title ?? '—';
  const gradeName = (id?: number | null) => grades?.find((g) => g.id === id)?.name ?? '—';

  const cols: TableProps<FactoryTourDto>['columns'] = [
    {
      title: 'Request',
      key: 'request',
      width: 160,
      render: (_v, row) => (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            whiteSpace: 'nowrap',
            paddingRight: 12,
          }}
        >
          <Text style={{ fontWeight: 500, fontSize: 13 }}>{row.requestId}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {continentCountryFor(row.travelType)}
          </Text>
        </div>
      ),
    },
    {
      title: 'Employee',
      key: 'employee',
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, minWidth: 0 }}>
          <Text ellipsis style={{ fontWeight: 500 }}>
            {row.name}
          </Text>
          <Text type="secondary" ellipsis style={{ fontSize: 12 }}>
            {[row.cardNo, deptName(row.departmentId), sectionName(row.sectionId)]
              .filter(Boolean)
              .join(' · ')}
          </Text>
        </div>
      ),
    },
    {
      title: 'Route',
      key: 'route',
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3, minWidth: 0 }}>
          <Text ellipsis>
            {row.origin} → {row.destination}
          </Text>
          <Text type="secondary" ellipsis style={{ fontSize: 12 }}>
            {[designationTitle(row.designationId), gradeName(row.gradeId)]
              .filter(Boolean)
              .join(' · ')}
          </Text>
        </div>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 150,
      render: (_v, row) => (
        <StatusPill tone={factoryTourStatusTone(row)}>
          {row.rawStatus && row.rawStatus !== row.status
            ? formatRawStatus(row.rawStatus)
            : FACTORY_TOUR_STATUS_LABEL[row.status]}
        </StatusPill>
      ),
    },
    {
      title: 'Tour dates',
      key: 'tourDates',
      width: 150,
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', whiteSpace: 'nowrap' }}>
          <Text style={{ fontSize: 12 }}>{formatDateTime(row.tourStart)}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            to {formatDateTime(row.tourEnd)}
          </Text>
        </div>
      ),
    },
    {
      title: 'Submitted',
      key: 'requestDate',
      width: 100,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
          {row.requestDate}
        </Text>
      ),
    },
    ...(onCancel || onDetails || onSubmit || onExpenses || onSettlement
      ? [
          {
            title: 'Actions',
            key: 'rowActions',
            width: compact ? 158 : 320,
            fixed: 'right' as const,
            align: 'right' as const,
            render: (_v: unknown, row: FactoryTourDto) => (
              <Space size={4} wrap={false} style={{ justifyContent: 'flex-end' }}>
                {onDetails
                  ? actionButton('Details', {
                      size: 'small',
                      icon: compact ? <Eye size={12} /> : undefined,
                      onClick: () => onDetails(row),
                    })
                  : null}
                {onExpenses && row.status === 'APPROVED'
                  ? actionButton('Expenses', {
                      size: 'small',
                      icon: <ReceiptText size={12} />,
                      onClick: () => onExpenses(row),
                    })
                  : null}
                {onSettlement && canSettle(row)
                  ? actionButton('Settlement', {
                      size: 'small',
                      icon: <HandCoins size={12} />,
                      onClick: () => onSettlement(row),
                    })
                  : null}
                {onSubmit && canSubmit(row)
                  ? actionButton('Submit', {
                      size: 'small',
                      type: 'primary',
                      icon: <SendHorizonal size={12} />,
                      loading: submittingId === row.id,
                      onClick: () =>
                        modal.confirm({
                          title: 'Submit this travel request for approval?',
                          content:
                            'It moves out of Draft and can no longer be edited while it awaits a decision.',
                          okText: 'Submit',
                          cancelText: 'Not yet',
                          onOk: () => onSubmit(row),
                        }),
                    })
                  : null}
                {onCancel && row.actions.includes('CANCEL')
                  ? actionButton('Cancel', {
                      size: 'small',
                      type: 'text',
                      danger: true,
                      icon: <XCircle size={12} />,
                      loading: cancellingId === row.id,
                      onClick: () =>
                        modal.confirm({
                          title: 'Cancel this travel request?',
                          content: 'This cannot be undone.',
                          okText: 'Cancel request',
                          okButtonProps: { danger: true },
                          cancelText: 'Keep',
                          onOk: () => onCancel(row),
                        }),
                    })
                  : null}
              </Space>
            ),
          },
        ]
      : []),
  ];

  return (
    <Table<FactoryTourDto>
      rowKey={(r) => r.id}
      columns={cols}
      dataSource={data}
      loading={loading}
      size="middle"
      pagination={false}
      rowSelection={rowSelection}
      scroll={{ x: 900 }}
    />
  );
}

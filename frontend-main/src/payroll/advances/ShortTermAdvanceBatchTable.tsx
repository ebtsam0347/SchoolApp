// Short-term advance batch list — rows from GET /short-term-advance-batches.
// Lines aren't included on the list response; this table shows totals only
// (count + total amount + target period). Drill-in opens a detail drawer
// elsewhere when wired.

import { Button, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { TableColumnsType, TableProps } from 'antd';

import { MoneyCell, StatusPill } from '@/components';
import { RequestIdCell } from '@/attendance/inbox';
import {
  ADVANCE_STATUS_LABEL,
  ADVANCE_STATUS_TONE,
  advanceStageLabel,
  formatPeriod,
  formatDateShort,
} from '../format';
import type { AdvanceStatus, ShortTermAdvanceBatchDto } from '../types';
import { ShortTermAdvanceBatchRowActions } from './ShortTermAdvanceBatchRowActions';

export type ShortTermAdvanceBatchTableProps = {
  data: ShortTermAdvanceBatchDto[];
  loading?: boolean;
  onRowClick?: (row: ShortTermAdvanceBatchDto) => void;
  /** Append an Approve/Reject actions column — for the Approvals Inbox. */
  showActions?: boolean;
  /** Hide the Status column — e.g. in an approvals queue, which only ever
   * lists PENDING/HR_APPROVED rows and shows Stage instead. */
  hideStatus?: boolean;
  /** Per-status label overrides for the Status column (default:
   *  ADVANCE_STATUS_LABEL, which names the step a request is waiting on). */
  statusLabels?: Partial<Record<AdvanceStatus, string>>;
  /** Opt-in row checkboxes — e.g. for bulk approve/reject in the Approvals
   * Inbox. Omit to keep the table's default (no selection column). */
  rowSelection?: TableProps<ShortTermAdvanceBatchDto>['rowSelection'];
  /** Adds a "Details" button next to Approve/Reject — opens a read-only
   *  drawer for the row. Only applies when `showActions` is set. */
  onDetails?: (row: ShortTermAdvanceBatchDto) => void;
  /**
   * Approvals-inbox layout: a leading Request column (code over id, like the
   * other inbox tables), plus the batch's notes.
   */
  inboxLayout?: boolean;
};

export function ShortTermAdvanceBatchTable({
  data,
  loading,
  onRowClick,
  showActions,
  hideStatus,
  statusLabels,
  rowSelection,
  onDetails,
  inboxLayout,
}: ShortTermAdvanceBatchTableProps) {
  const { token } = antdTheme.useToken();

  const statusLabel = (s: AdvanceStatus) => statusLabels?.[s] ?? ADVANCE_STATUS_LABEL[s] ?? '';
  const statusColumns: TableColumnsType<ShortTermAdvanceBatchDto> = hideStatus
    ? []
    : [
        {
          title: 'Status',
          key: 'status',
          width: 150,
          sorter: (a, b) => statusLabel(a.status).localeCompare(statusLabel(b.status)),
          render: (_v, r) => (
            <StatusPill tone={ADVANCE_STATUS_TONE[r.status]}>{statusLabel(r.status)}</StatusPill>
          ),
        },
      ];

  // Inbox layout: Request, then Status — like the other approvals-inbox
  // tables. The classic layout keeps Status after Stage.
  const inboxLeadColumns: TableColumnsType<ShortTermAdvanceBatchDto> = inboxLayout
    ? [
        {
          title: 'Request',
          key: 'request',
          width: 150,
          sorter: (a, b) => a.id - b.id,
          render: (_v, r) => <RequestIdCell id={r.id} code={r.requestCode} labelled />,
        },
        ...statusColumns,
      ]
    : [];

  const notesColumn: TableColumnsType<ShortTermAdvanceBatchDto> = inboxLayout
    ? [
        {
          title: 'Notes',
          key: 'notes',
          width: 220,
          render: (_v, r) =>
            r.notes ? (
              <Typography.Paragraph
                ellipsis={{ rows: 2, tooltip: r.notes }}
                style={{ margin: 0, fontSize: 12, width: 200 }}
              >
                {r.notes}
              </Typography.Paragraph>
            ) : (
              <Typography.Text type="secondary">—</Typography.Text>
            ),
        },
      ]
    : [];

  const columns: TableColumnsType<ShortTermAdvanceBatchDto> = [
    ...inboxLeadColumns,

    {
      title: 'Deduct in',
      key: 'period',
      width: 130,
      sorter: (a, b) =>
        a.targetYear - b.targetYear || a.targetMonth - b.targetMonth,
      render: (_v, r) => formatPeriod(r.targetYear, r.targetMonth),
    },
    {
      title: 'Employees',
      key: 'totalEmployees',
      width: 110,
      align: 'right',
      sorter: (a, b) => a.totalEmployees - b.totalEmployees,
      render: (_v, r) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          {r.totalEmployees}
        </span>
      ),
    },
    {
      title: 'Total',
      key: 'totalAmount',
      width: 150,
      align: 'right',
      sorter: (a, b) => a.totalAmount - b.totalAmount,
      render: (_v, r) => <MoneyCell amount={r.totalAmount} />,
    },
    ...notesColumn,
    {
      title: 'Stage',
      key: 'stage',
      width: 150,
      sorter: (a, b) =>
        (advanceStageLabel(a) ?? '').localeCompare(advanceStageLabel(b) ?? ''),
      render: (_v, r) =>
        advanceStageLabel(r) ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Tag style={{ marginRight: 0, width: 'fit-content' }}>{advanceStageLabel(r)}</Tag>
            {r.currentStage?.assignedToName ? (
              <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                {r.currentStage.assignedToName}
              </Typography.Text>
            ) : null}
          </div>
        ) : (
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            —
          </Typography.Text>
        ),
    },
    ...(inboxLayout ? [] : statusColumns),
    {
      title: 'Submitted',
      key: 'createdAt',
      width: 120,
      sorter: (a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''),
      defaultSortOrder: 'descend',
      render: (_v, r) => (
        <span
          style={{
            fontSize: 12,
            fontVariantNumeric: 'tabular-nums',
            color: token.colorTextSecondary,
          }}
        >
          {formatDateShort(r.createdAt)}
        </span>
      ),
    },
  ];

  if (showActions) {
    columns.push({
      title: '',
      key: '__actions',
      width: onDetails ? 280 : 200,
      align: 'right',
      render: (_v, r) => (
        <Space size={4} wrap={false}>
          {onDetails ? (
            <Button size="small" onClick={() => onDetails(r)}>
              Details
            </Button>
          ) : null}
          <ShortTermAdvanceBatchRowActions row={r} />
        </Space>
      ),
    });
  }

  return (
    <Table<ShortTermAdvanceBatchDto>
      rowKey="id"
      loading={loading}
      dataSource={data}
      columns={columns}
      size={inboxLayout ? 'middle' : 'small'}
      // ~810px of fixed column widths, ~1090px with the actions column on —
      // keep the overflow inside the table rather than on the page.
      scroll={{ x: 'max-content' }}
      pagination={{
        defaultPageSize: 20,
        pageSizeOptions: [10, 20, 50, 100],
        showSizeChanger: true,
        showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
      }}
      onRow={(row) =>
        onRowClick ? { onClick: () => onRowClick(row), style: { cursor: 'pointer' } } : {}
      }
      rowSelection={rowSelection}
    />
  );
}

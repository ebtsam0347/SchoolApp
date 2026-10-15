// Permanent advance list — rows from GET /permanent-advances (page slice).
// Status pill, amounts, monthly + remaining, requested date. The employee
// cell is opt-in (off by default — "My advances" already knows who the rows
// belong to). When on, resolves full employee summaries (name, photo,
// department) in one batched call and links to /people/{id} — same
// employeeColumn used on the Leaves/Overtime/Regularization inbox tables.

import { useMemo, useState } from 'react';
import { Button, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { TableColumnsType, TableProps } from 'antd';
import { useQuery } from '@tanstack/react-query';

import { MoneyCell, StatusPill, employeeColumn } from '@/components';
import { employeesBff, type EmployeeSummary } from '@/attendance';
import { RequestIdCell } from '@/attendance/inbox';
import {
  ADVANCE_STATUS_LABEL,
  ADVANCE_STATUS_TONE,
  advanceStageLabel,
  formatDateShort,
  formatPeriod,
  resolveAdvanceApprovalStage,
} from '../format';
import type { AdvanceStatus, PermanentAdvanceDto } from '../types';
import { PermanentAdvanceHrAdjustModal } from './PermanentAdvanceHrAdjustModal';
import { PermanentAdvanceRowActions } from './PermanentAdvanceRowActions';

export type PermanentAdvanceTableProps = {
  data: PermanentAdvanceDto[];
  loading?: boolean;
  /** Show an employee column linking to /people/{id}. */
  showEmployee?: boolean;
  /** Append an Approve/Reject actions column — for the Approvals Inbox.
   *  Rows waiting on HR also get an "Adjust" button (amount / monthly
   *  deduction / note) ahead of Details. */
  showActions?: boolean;
  /** Hide the Status column — e.g. in an approvals queue, which only ever
   * lists PENDING/HR_APPROVED rows and shows Stage instead. */
  hideStatus?: boolean;
  /** Hide the Stage column — e.g. a list whose rows all sit at one stage. */
  hideStage?: boolean;
  /** Per-status label overrides for the Status column — the defaults
   *  (ADVANCE_STATUS_LABEL) name the step a request is waiting on. */
  statusLabels?: Partial<Record<AdvanceStatus, string>>;
  /** Opt-in row checkboxes — e.g. for bulk approve/reject in the Approvals
   * Inbox. Omit to keep the table's default (no selection column). */
  rowSelection?: TableProps<PermanentAdvanceDto>['rowSelection'];
  /** Adds a "Details" button next to Approve/Reject — opens a read-only
   *  drawer for the row. Only applies when `showActions` is set. */
  onDetails?: (row: PermanentAdvanceDto) => void;
  /** Employee summaries already in hand (the approvals inbox inlines them on
   *  each row) — used instead of the by-ids lookup. */
  employees?: ReadonlyMap<number, EmployeeSummary>;
  /**
   * Approvals-inbox layout: a leading Request column (code over id, like the other
   * inbox tables), then Employee, Amount, Monthly deduction, the bonus /
   * annual-leave offsets and Description. Drops Remaining — always equal to Amount while pending.
   */
  inboxLayout?: boolean;
  /** Extra columns appended after the built-in ones (e.g. a Print button). */
  extraColumns?: TableColumnsType<PermanentAdvanceDto>;
  /** Override the default client-side pagination — e.g. server-paged lists. */
  pagination?: TableProps<PermanentAdvanceDto>['pagination'];
  onChange?: TableProps<PermanentAdvanceDto>['onChange'];
};

export function PermanentAdvanceTable({
  data,
  loading,
  showEmployee,
  showActions,
  hideStatus,
  hideStage,
  statusLabels,
  rowSelection,
  onDetails,
  employees,
  inboxLayout,
  extraColumns,
  pagination,
  onChange,
}: PermanentAdvanceTableProps) {
  const { token } = antdTheme.useToken();
  const [adjusting, setAdjusting] = useState<PermanentAdvanceDto | null>(null);
  // Offsets are 0 / null when that recovery source isn't used — show a dash.
  const offsetCell = (amount: number | null | undefined) =>
    amount ? (
      <MoneyCell amount={amount} />
    ) : (
      <span style={{ color: token.colorTextTertiary }}>—</span>
    );

  const employeeIds = useMemo(
    () => Array.from(new Set(data.map((r) => r.employeeId))),
    [data],
  );
  const employeesQuery = useQuery({
    queryKey: ['bff', 'employees', 'by-ids', employeeIds],
    queryFn: ({ signal }) => employeesBff.byIds(employeeIds, signal),
    enabled: showEmployee && !employees && employeeIds.length > 0,
    staleTime: 5 * 60_000,
  });
  const employeeById = useMemo(() => {
    if (employees) return employees;
    const m = new Map<number, EmployeeSummary>();
    for (const e of employeesQuery.data?.employees ?? []) m.set(e.id, e);
    return m;
  }, [employees, employeesQuery.data]);

  const statusLabel = (s: AdvanceStatus) => statusLabels?.[s] ?? ADVANCE_STATUS_LABEL[s] ?? '';

  const statusColumn: TableColumnsType<PermanentAdvanceDto>[number] = {
    title: 'Status',
    key: 'status',
    width: 140,
    sorter: (a, b) => statusLabel(a.status).localeCompare(statusLabel(b.status)),
    render: (_v, r) => (
      <StatusPill tone={ADVANCE_STATUS_TONE[r.status]}>{statusLabel(r.status)}</StatusPill>
    ),
  };
  // The inbox layout puts Status right after Employee, like the other
  // approvals-inbox tables; the classic layout keeps it after Stage.
  const statusInLead = inboxLayout && !hideStatus;

  const columns: TableColumnsType<PermanentAdvanceDto> = [];

  // columns.push({
  //   title: 'Req Id',
  //   dataIndex: 'requestCode',
  //   key: 'requestCode',
  //   width: 140,
  //   sorter: (a, b) => (a.requestCode ?? `#${a.id}`).localeCompare(b.requestCode ?? `#${b.id}`),
  //   render: (v: string | null, r) => (
  //     <Typography.Text
  //       style={{
  //         fontFamily:
  //           'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
  //         fontSize: 12,
  //         color: token.colorTextSecondary,
  //       }}
  //     >
  //       {v ?? `#${r.id}`}
  //     </Typography.Text>
  //   ),
  // });

  if (inboxLayout) {
    columns.push({
      title: 'Request',
      key: 'request',
      width: 150,
      sorter: (a, b) => a.id - b.id,
      render: (_v, r) => <RequestIdCell id={r.id} code={r.requestCode} labelled />,
    });
  }

  if (showEmployee) {
    columns.push({
      ...employeeColumn<PermanentAdvanceDto>({
        title: 'Employee',
        employee: (r) => {
          const e = employeeById.get(r.employeeId);
          return e
            ? {
                id: e.id,
                fullName: e.fullName,
                employeeCode: e.employeeCode,
                designationTitle: e.designationName,
                departmentName: e.departmentName,
                profilePictureUrl: e.profilePictureUrl,
              }
            : { id: r.employeeId, fullName: `Employee #${r.employeeId}` };
        },
        to: (r) => `/people/${r.employeeId}`,
      }),
      width: 220,
      sorter: (a, b) =>
        (employeeById.get(a.employeeId)?.fullName ?? '').localeCompare(
          employeeById.get(b.employeeId)?.fullName ?? '',
        ),
    });
  }

  if (statusInLead) columns.push(statusColumn);

  columns.push(
    {
      title: 'Amount',
      key: 'amount',
      align: 'right',
      width: 130,
      sorter: (a, b) => a.amount - b.amount,
      render: (_v, r) => <MoneyCell amount={r.amount} />,
    },
    {
      title: inboxLayout ? 'Monthly deduction' : 'Monthly',
      key: 'monthlyDeduction',
      align: 'right',
      width: inboxLayout ? 150 : 120,
      sorter: (a, b) => a.monthlyDeduction - b.monthlyDeduction,
      render: (_v, r) => <MoneyCell amount={r.monthlyDeduction} />,
    },
  );

  if (inboxLayout) {
    columns.push(
      {
        title: 'Bonus offset 1',
        key: 'bonusOffset',
        align: 'right',
        width: 130,
        sorter: (a, b) => (a.bonusOffset ?? 0) - (b.bonusOffset ?? 0),
        render: (_v, r) => offsetCell(r.bonusOffset),
      },
      {
        title: 'Bonus offset 2',
        key: 'bonusOffset2',
        align: 'right',
        width: 130,
        sorter: (a, b) => (a.bonusOffset2 ?? 0) - (b.bonusOffset2 ?? 0),
        render: (_v, r) => offsetCell(r.bonusOffset2),
      },
      {
        title: 'Annual leave offset',
        key: 'annualLeaveOffset',
        align: 'right',
        width: 160,
        sorter: (a, b) => (a.annualLeaveOffset ?? 0) - (b.annualLeaveOffset ?? 0),
        render: (_v, r) => {
          if (!r.annualLeaveOffset) return offsetCell(r.annualLeaveOffset);
          // "YYYY-MM-DD" — only the month matters (the leave-encashment month).
          const [y, m] = (r.annualLeaveMonth ?? '').split('-').map(Number);
          return (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
              <MoneyCell amount={r.annualLeaveOffset} />
              {y && m ? (
                <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                  {formatPeriod(y, m)}
                </Typography.Text>
              ) : null}
            </div>
          );
        },
      },
      {
        title: 'Gratuity at request',
        key: 'gratuityAtRequest',
        align: 'right',
        width: 160,
        sorter: (a, b) => (a.gratuityAtRequest ?? 0) - (b.gratuityAtRequest ?? 0),
        render: (_v, r) => offsetCell(r.gratuityAtRequest),
      },
      {
        title: 'Description',
        key: 'description',
        width: 220,
        render: (_v, r) =>
          r.description ? (
            <Typography.Paragraph
              ellipsis={{ rows: 2, tooltip: r.description }}
              style={{ margin: 0, fontSize: 12, width: 200 }}
            >
              {r.description}
            </Typography.Paragraph>
          ) : (
            <span style={{ color: token.colorTextTertiary }}>—</span>
          ),
      },
    );
  } else {
    columns.push({
      title: 'Remaining',
      key: 'remainingBalance',
      align: 'right',
      width: 130,
      sorter: (a, b) => (a.remainingBalance ?? 0) - (b.remainingBalance ?? 0),
      render: (_v, r) =>
        r.remainingBalance == null ? (
          <span style={{ color: token.colorTextTertiary }}>—</span>
        ) : (
          <MoneyCell amount={r.remainingBalance} />
        ),
    });
  }

  columns.push(
    ...(hideStage
      ? []
      : [
          {
            title: 'Stage',
            key: 'stage',
            width: 150,
            sorter: (a: PermanentAdvanceDto, b: PermanentAdvanceDto) =>
              (advanceStageLabel(a) ?? '').localeCompare(advanceStageLabel(b) ?? ''),
            render: (_v: unknown, r: PermanentAdvanceDto) =>
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
        ]),
    ...(hideStatus || statusInLead ? [] : [statusColumn]),
    {
      title: 'Requested',
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
  );

  if (showActions) {
    columns.push({
      title: '',
      key: '__actions',
      width: onDetails ? 360 : 280,
      align: 'right',
      render: (_v, r) => (
        <Space size={4} wrap={false}>
          {/* HR may revise the figures before approving — only while the
              advance is waiting on HR. */}
          {resolveAdvanceApprovalStage(r) === 'HR_APPROVAL' ? (
            <Button size="small" onClick={() => setAdjusting(r)}>
              Adjust
            </Button>
          ) : null}
          {onDetails ? (
            <Button size="small" onClick={() => onDetails(r)}>
              Details
            </Button>
          ) : null}
          <PermanentAdvanceRowActions row={r} />
        </Space>
      ),
    });
  }

  if (extraColumns) columns.push(...extraColumns);

  return (
    <>
      <Table<PermanentAdvanceDto>
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        size={inboxLayout ? 'middle' : 'small'}
        // Keep the columns scrolling inside the table on a phone rather than
        // dragging the page sideways. No-op on a wide viewport.
        scroll={{ x: 'max-content' }}
        pagination={
          pagination ?? {
            defaultPageSize: 20,
            pageSizeOptions: [10, 20, 50, 100],
            showSizeChanger: true,
            showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
          }
        }
        onChange={onChange}
        rowSelection={rowSelection}
      />
      {showActions ? (
        <PermanentAdvanceHrAdjustModal row={adjusting} onClose={() => setAdjusting(null)} />
      ) : null}
    </>
  );
}

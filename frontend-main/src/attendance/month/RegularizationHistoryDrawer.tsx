// "Regularization history" — the regularizations the caller filed on someone
// else's behalf (a manager for a direct report from the team calendar, or a
// team-request generator for a colleague). Their own self-submissions live
// on My attendance, not here.
//
// Backed by `GET /regularizations/filed-by-me`. Each row arrives as
// `{ request, employee }` — the name is resolved server-side, because the
// people using this (managers) can't call the `employee.read`-gated
// resolvers. Expand a row for the full approval chain.

import { useState } from 'react';
import { Drawer, Segmented, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';

import { PersonCell, RequestState, StatusPill } from '@/components';
import {
  REGULARIZATION_STATUS_LABEL,
  REGULARIZATION_STATUS_TONE,
  formatClock,
  formatDateMonthDay,
} from '../format';
import { attendanceApi } from '../api';
import type { ApprovalInboxRegularizationRow } from '../bff-api';
import type { RegularizationStatus } from '../types';
import { ExpandedSteps } from '../short-leaves/ExpandedSteps';

const { Text } = Typography;

type StatusFilter = RegularizationStatus | 'ALL';
type Row = ApprovalInboxRegularizationRow;

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
];

export type RegularizationHistoryDrawerProps = {
  open: boolean;
  onClose: () => void;
};

export function RegularizationHistoryDrawer({ open, onClose }: RegularizationHistoryDrawerProps) {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const listQuery = useQuery({
    queryKey: ['attendance', 'regularizations', 'filed-by-me', statusFilter, page, pageSize],
    queryFn: ({ signal }) =>
      attendanceApi.regularizationsFiledByMe(
        {
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          page,
          size: pageSize,
        },
        signal,
      ),
    enabled: open,
  });
  const rows = listQuery.data?.content ?? [];
  const totalElements = listQuery.data?.totalElements ?? 0;

  const columns: TableColumnsType<Row> = [
    {
      title: 'Employee',
      key: 'employee',
      width: 230,
      render: (_v, { request, employee }) => (
        <PersonCell
          person={{
            fullName: employee?.fullName ?? `#${request.employeeId}`,
            employeeCode: employee?.employeeCode ?? null,
            profilePictureUrl: employee?.profilePictureUrl ?? undefined,
          }}
        />
      ),
    },
    {
      title: 'Date',
      key: 'workDate',
      width: 110,
      render: (_v, { request }) => (
        <span style={{ fontWeight: 500 }}>{formatDateMonthDay(request.workDate)}</span>
      ),
    },
    {
      title: 'Requested times',
      key: 'times',
      width: 150,
      render: (_v, { request }) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {request.requestedCheckIn ? formatClock(request.requestedCheckIn) : '—'}
          {' – '}
          {request.requestedCheckOut ? formatClock(request.requestedCheckOut) : '—'}
        </span>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 120,
      render: (_v, { request }) => (
        <StatusPill tone={REGULARIZATION_STATUS_TONE[request.status]}>
          {REGULARIZATION_STATUS_LABEL[request.status]}
        </StatusPill>
      ),
    },
    {
      title: 'Stage',
      key: 'stage',
      width: 190,
      render: (_v, { request }) =>
        request.currentStage ? (
          <span style={{ fontSize: 12 }}>
            <Tag>{request.currentStage.stageRole}</Tag>
            <Text type="secondary">{request.currentStage.assignedToName ?? 'role inbox'}</Text>
          </span>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            Finalized
          </Text>
        ),
    },
    {
      title: 'Reason',
      key: 'reason',
      ellipsis: true,
      render: (_v, { request }) => request.reason,
    },
    {
      title: 'Filed',
      key: 'createdAt',
      width: 100,
      render: (_v, { request }) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatDateMonthDay(request.createdAt)}
        </Text>
      ),
    },
  ];

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size={1000}
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <History size={16} />
          Regularization history
        </span>
      }
      extra={
        <Segmented<StatusFilter>
          size="small"
          value={statusFilter}
          onChange={(v) => {
            setStatusFilter(v);
            setPage(0);
          }}
          options={STATUS_FILTERS}
        />
      }
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 13 }}>
        Regularizations you filed for your team members. Expand a row to see where it is in
        the approval chain.
      </Text>
      <RequestState
        loading={false}
        error={listQuery.isError ? listQuery.error : undefined}
        errorTitle="Couldn't load your regularization history"
        empty={!listQuery.isLoading && totalElements === 0}
        emptyIcon={History}
        emptyTitle={
          statusFilter === 'ALL' ? 'No regularizations filed yet' : 'No matching regularizations'
        }
        emptyDescription={
          statusFilter === 'ALL'
            ? 'Open a team member, switch to Full month and click a day to file one for them.'
            : 'Try a different status filter.'
        }
      >
        <Table<Row>
          rowKey={(row) => row.request.id}
          dataSource={rows}
          columns={columns}
          loading={listQuery.isLoading}
          size="middle"
          scroll={{ x: 'max-content' }}
          expandable={{
            expandedRowRender: (row) => <ExpandedSteps row={row.request} />,
            rowExpandable: (row) => row.request.steps.length > 0,
          }}
          pagination={{
            current: page + 1,
            pageSize,
            total: totalElements,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (t) => `${t} request${t === 1 ? '' : 's'}`,
            onChange: (p, sz) => {
              if (sz !== pageSize) {
                setPageSize(sz);
                setPage(0);
              } else {
                setPage(p - 1);
              }
            },
          }}
        />
      </RequestState>
    </Drawer>
  );
}

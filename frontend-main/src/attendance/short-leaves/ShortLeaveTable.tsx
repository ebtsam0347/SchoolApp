// Short leaves table — one row per sub-day leave request: request code / id,
// who it's for, the leave itself, status, and when it was created. A row (or
// its View button) opens the full approval chain. Cancel is inline on your
// own list only (`cancellable`, gated on `actions[]`). Pass `employeeOf` to
// add the "For" column (the team / department lists).

import { useState } from 'react';
import { Button, Space, Table, Typography, type TableColumnsType, type TableProps } from 'antd';
import { Eye } from 'lucide-react';

import { PersonCell, StatusPill } from '@/components';
import { RequestHistoryDrawer, type TrackedRequestRow } from '../approval-track';
import {
  SHORT_LEAVE_STATUS_LABEL,
  SHORT_LEAVE_STATUS_TONE,
  formatDateShort,
  formatDateTime,
  formatMinutes,
} from '../format';
import type { ShortLeaveRequestDto } from '../types';
import { CancelShortLeaveButton } from './CancelShortLeaveButton';

const { Text } = Typography;

export type ShortLeaveEmployee = {
  fullName: string;
  employeeCode?: string | null;
  profilePictureUrl?: string | null;
};

export type ShortLeaveTableProps<T extends ShortLeaveRequestDto = ShortLeaveRequestDto> = {
  data: T[];
  loading: boolean;
  pagination: NonNullable<TableProps<T>['pagination']>;
  /** Who a row is for. Supplying it adds a "For" column. */
  employeeOf?: (row: T) => ShortLeaveEmployee;
  /** Show the Cancel button (your own list only). */
  cancellable?: boolean;
};

/** Times come as a bare `HH:mm[:ss]`. */
const hhmm = (t: string | null | undefined) => (t ? t.slice(0, 5) : '—');

const detailOf = (row: ShortLeaveRequestDto) =>
  `${formatDateShort(row.leaveDate)} · ${hhmm(row.startTime)} – ${hhmm(row.endTime)} · ${formatMinutes(
    row.totalMinutes,
  )}`;

export function ShortLeaveTable<T extends ShortLeaveRequestDto = ShortLeaveRequestDto>({
  data,
  loading,
  pagination,
  employeeOf,
  cancellable = false,
}: ShortLeaveTableProps<T>) {
  const [historyOf, setHistoryOf] = useState<T | null>(null);

  const tracked = (row: T): TrackedRequestRow => ({
    code: row.requestCode,
    person: employeeOf ? employeeOf(row) : null,
    detail: detailOf(row),
    note: row.reason ? `Reason: ${row.reason}` : undefined,
    statusLabel: SHORT_LEAVE_STATUS_LABEL[row.status],
    statusTone: SHORT_LEAVE_STATUS_TONE[row.status],
    rawStatus: row.status,
    currentStage: row.currentStage,
    steps: row.steps,
    createdAt: row.createdAt,
  });

  const employeeCol: TableColumnsType<T> = employeeOf
    ? [
        {
          title: 'For',
          key: 'person',
          width: 240,
          render: (_v, row) => {
            const p = employeeOf(row);
            return (
              <PersonCell
                person={{
                  fullName: p.fullName,
                  employeeCode: p.employeeCode ?? null,
                  profilePictureUrl: p.profilePictureUrl ?? undefined,
                }}
              />
            );
          },
        },
      ]
    : [];

  const cols: TableColumnsType<T> = [
    {
      title: 'Request',
      key: 'code',
      width: 150,
      render: (_v, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong style={{ fontVariantNumeric: 'tabular-nums' }}>
            {row.requestCode}
          </Text>
          <Text type="secondary" style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
            ID #{row.id}
          </Text>
        </Space>
      ),
    },
    ...employeeCol,
    {
      title: 'Leave date',
      key: 'leaveDate',
      width: 120,
      render: (_v, row) => <span style={{ fontWeight: 500 }}>{formatDateShort(row.leaveDate)}</span>,
    },
    {
      title: 'Time',
      key: 'time',
      width: 120,
      render: (_v, row) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          {hhmm(row.startTime)} – {hhmm(row.endTime)}
        </span>
      ),
    },
    {
      title: 'Duration',
      key: 'duration',
      width: 100,
      render: (_v, row) => formatMinutes(row.totalMinutes),
    },
    {
      title: 'Reason',
      key: 'reason',
      width: 220,
      ellipsis: { showTitle: true },
      render: (_v, row) => row.reason || <Text type="secondary">—</Text>,
    },
    {
      title: 'Status',
      key: 'status',
      width: 130,
      render: (_v, row) => (
        <StatusPill tone={SHORT_LEAVE_STATUS_TONE[row.status]}>
          {SHORT_LEAVE_STATUS_LABEL[row.status]}
        </StatusPill>
      ),
    },
    {
      title: 'Created At',
      key: 'createdAt',
      width: 160,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {row.createdAt ? formatDateTime(row.createdAt) : '—'}
        </Text>
      ),
    },
    {
      key: 'actions',
      title: '',
      width: 110,
      align: 'right',
      render: (_v, row) => (
        <Space size={6}>
          <Button size="small" icon={<Eye size={13} />} onClick={() => setHistoryOf(row)}>
            View
          </Button>
          {cancellable ? <CancelShortLeaveButton row={row} /> : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Table<T>
        rowKey="id"
        dataSource={data}
        columns={cols}
        loading={loading}
        pagination={pagination}
        size="middle"
        scroll={{ x: 'max-content' }}
        onRow={(row) => ({
          onClick: (e) => {
            if ((e.target as HTMLElement).closest('a, button')) return;
            setHistoryOf(row);
          },
          style: { cursor: 'pointer' },
        })}
      />
      <RequestHistoryDrawer
        row={historyOf ? tracked(historyOf) : null}
        typeLabel="Short leave"
        activity={historyOf ? { kind: 'short-leave', requestId: historyOf.id } : null}
        onClose={() => setHistoryOf(null)}
      />
    </>
  );
}

// Leave requests table — one row per leave request, in the same layout as the
// short-leave lists: request code / id, who it's for, leave type, dates,
// days, reason, status, and when it was created. A row (or its View button)
// opens the details: `onOpen` for my own list (the page's detail drawer,
// with cancel), else the same detail drawer read-only — approval, activity
// and per-day breakdown. Pass `employeeOf` to add the "For" column (the
// team / department lists).

import { useState } from 'react';
import { Button, Space, Table, Typography, type TableColumnsType, type TableProps } from 'antd';
import { Eye } from 'lucide-react';

import { PersonCell, StatusPill } from '@/components';
import {
  LEAVE_STATUS_LABEL,
  LEAVE_STATUS_TONE,
  formatDateShort,
  formatDateTime,
  prettyLeaveCode,
} from '../format';
import type { LeaveRequestDto } from '../types';
import { LeaveDetailDrawer } from './LeaveDetailDrawer';

const { Text } = Typography;

export type LeaveEmployee = {
  fullName: string;
  employeeCode?: string | null;
  profilePictureUrl?: string | null;
};

export type LeaveTableProps<T extends LeaveRequestDto = LeaveRequestDto> = {
  data: T[];
  loading: boolean;
  pagination: NonNullable<TableProps<T>['pagination']>;
  /** Open a row's details. Without it, View opens a read-only detail drawer. */
  onOpen?: (row: T) => void;
  /** Who a row is for. Supplying it adds a "For" column. */
  employeeOf?: (row: T) => LeaveEmployee;
};

const daysLabel = (n: number) => `${n} day${n === 1 ? '' : 's'}`;

const datesOf = (row: LeaveRequestDto) =>
  row.fromDate !== row.toDate
    ? `${formatDateShort(row.fromDate)} – ${formatDateShort(row.toDate)}`
    : formatDateShort(row.fromDate);

export function LeaveTable<T extends LeaveRequestDto = LeaveRequestDto>({
  data,
  loading,
  pagination,
  onOpen,
  employeeOf,
}: LeaveTableProps<T>) {
  const [historyOf, setHistoryOf] = useState<T | null>(null);
  const open = (row: T) => (onOpen ? onOpen(row) : setHistoryOf(row));

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
      title: 'Type',
      key: 'leaveType',
      width: 140,
      render: (_v, row) => (
        <Space orientation="vertical" size={0}>
          <Text style={{ fontWeight: 500 }}>{prettyLeaveCode(row.leaveTypeCode)}</Text>
          {row.halfDay ? (
            <Text type="secondary" style={{ fontSize: 11 }}>
              Half day{row.halfDayPart ? ` · ${row.halfDayPart}` : ''}
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: 'Dates',
      key: 'dates',
      width: 200,
      render: (_v, row) => <span style={{ fontWeight: 500 }}>{datesOf(row)}</span>,
    },
    {
      title: 'Days',
      key: 'days',
      width: 90,
      render: (_v, row) => daysLabel(row.days),
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
      width: 150,
      render: (_v, row) => (
        <StatusPill tone={LEAVE_STATUS_TONE[row.status]}>{LEAVE_STATUS_LABEL[row.status]}</StatusPill>
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
      width: 90,
      align: 'right',
      render: (_v, row) => (
        <Button size="small" icon={<Eye size={13} />} onClick={() => open(row)}>
          View
        </Button>
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
            open(row);
          },
          style: { cursor: 'pointer' },
        })}
      />
      {onOpen ? null : (
        <LeaveDetailDrawer
          leaveId={historyOf?.id ?? null}
          initial={historyOf}
          employee={historyOf && employeeOf ? employeeOf(historyOf) : null}
          allowCancel={false}
          onClose={() => setHistoryOf(null)}
        />
      )}
    </>
  );
}

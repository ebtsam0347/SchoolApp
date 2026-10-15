// Overtime requests table — one row per OT request, in the same layout as
// the leave / short-leave lists: request code / id, who it's for, work date,
// OT type, factory, paid minutes (`showMinutes` — derived from the work
// date's punches: the BFF on my own list, the request rows elsewhere), reason, status, and when it was created. A row (or its
// View button) opens the approval history with the Activity feed. Cancel is
// inline on my own list only (`cancellable`, gated on `actions[]`). Pass
// `employeeOf` to add the "For" column (the team / department lists).

import { useState } from 'react';
import {
  Button,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
  type TableProps,
} from 'antd';
import { Eye } from 'lucide-react';

import { PersonCell, StatusPill } from '@/components';
import { RequestHistoryDrawer, type TrackedRequestRow } from '../approval-track';
import type { OvertimeListRow } from '../api';
import {
  OVERTIME_STATUS_LABEL,
  OVERTIME_STATUS_TONE,
  OVERTIME_TYPE_COLOR,
  OVERTIME_TYPE_LABEL,
  formatDateShort,
  formatDateTime,
  formatMinutes,
} from '../format';
import type { OvertimeType } from '../types';
import { CancelOvertimeButton } from './CancelOvertimeButton';

const { Text } = Typography;

export type OvertimeEmployee = {
  fullName: string;
  employeeCode?: string | null;
  profilePictureUrl?: string | null;
};

export type OvertimeTableProps<T extends OvertimeListRow = OvertimeListRow> = {
  data: T[];
  loading: boolean;
  pagination: NonNullable<TableProps<T>['pagination']>;
  /** Who a row is for. Supplying it adds a "For" column. */
  employeeOf?: (row: T) => OvertimeEmployee;
  /** Show the paid OT minutes column (rows carry `minutes`). */
  showMinutes?: boolean;
  /** Show the Cancel button (your own list only). */
  cancellable?: boolean;
};

/** The wire field is `overtimeType`; the FE type still says `type`. */
const otTypeOf = (r: OvertimeListRow): OvertimeType | undefined => r.overtimeType ?? r.type;

const TYPE_MINUTES: Record<OvertimeType, keyof NonNullable<OvertimeListRow['minutes']>> = {
  NORMAL: 'otNormalMinutes',
  EXTRA: 'otExtraMinutes',
  SPECIAL: 'otSpecialMinutes',
};

function MinutesCell({ row }: { row: OvertimeListRow }) {
  const type = otTypeOf(row);
  const paid = type && row.minutes ? row.minutes[TYPE_MINUTES[type]] : 0;
  if (row.status !== 'APPROVED') {
    return (
      <Text type="secondary" style={{ fontSize: 12 }}>
        derived after approval
      </Text>
    );
  }
  // An API build that doesn't send minutes on this list yet.
  if (!row.minutes) return <Text type="secondary">—</Text>;
  return (
    <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 500 }}>
      {formatMinutes(paid)}
    </span>
  );
}

export function OvertimeTable<T extends OvertimeListRow = OvertimeListRow>({
  data,
  loading,
  pagination,
  employeeOf,
  showMinutes = false,
  cancellable = false,
}: OvertimeTableProps<T>) {
  const [historyOf, setHistoryOf] = useState<T | null>(null);

  const tracked = (row: T): TrackedRequestRow => {
    const type = otTypeOf(row);
    return {
      code: row.requestCode,
      person: employeeOf ? employeeOf(row) : null,
      detail: `${formatDateShort(row.workDate)} · ${type ? OVERTIME_TYPE_LABEL[type] : 'Overtime'}${
        row.factoryCode ? ` · ${row.factoryCode}` : ''
      }`,
      note: row.reason ? `Reason: ${row.reason}` : undefined,
      statusLabel: OVERTIME_STATUS_LABEL[row.status],
      statusTone: OVERTIME_STATUS_TONE[row.status],
      rawStatus: row.status,
      currentStage: row.currentStage,
      steps: row.steps,
      createdAt: row.createdAt,
    };
  };

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

  const minutesCol: TableColumnsType<T> = showMinutes
    ? [
        {
          title: 'OT minutes',
          key: 'otMinutes',
          width: 150,
          render: (_v, row) => <MinutesCell row={row} />,
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
      title: 'Work date',
      key: 'workDate',
      width: 120,
      render: (_v, row) => <span style={{ fontWeight: 500 }}>{formatDateShort(row.workDate)}</span>,
    },
    {
      title: 'Type',
      key: 'type',
      width: 100,
      render: (_v, row) => {
        const type = otTypeOf(row);
        return type ? (
          <Tag color={OVERTIME_TYPE_COLOR[type]}>{OVERTIME_TYPE_LABEL[type]}</Tag>
        ) : (
          <Text type="secondary">—</Text>
        );
      },
    },
    {
      title: 'Factory',
      key: 'factory',
      width: 120,
      render: (_v, row) => row.factoryCode || <Text type="secondary">—</Text>,
    },
    ...minutesCol,
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
        <StatusPill tone={OVERTIME_STATUS_TONE[row.status]}>
          {OVERTIME_STATUS_LABEL[row.status]}
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
          {cancellable ? <CancelOvertimeButton row={row} /> : null}
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
        typeLabel="Overtime"
        activity={historyOf ? { kind: 'overtime', requestId: historyOf.id } : null}
        onClose={() => setHistoryOf(null)}
      />
    </>
  );
}

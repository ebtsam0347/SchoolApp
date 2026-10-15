// Table for /attendance/shift-overrides. Includes the Edit button (opens
// the parent's edit drawer) and an inline danger-Delete via Popconfirm
// that reverts every date in the range.

import {
  App as AntdApp,
  Button,
  Popconfirm,
  Space,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Edit3, Trash2 } from 'lucide-react';
import { Can } from '@/auth';
import {
  formatMinutes,
  formatShiftRange,
  formatDateMonthDay,
  shiftDurationMinutes,
  shiftOverridesApi,
  type ShiftOverrideDto,
} from '@/attendance';
import { getErrorMessage } from '@/lib';
import { daysBetween } from './helpers';

const { Text } = Typography;

export type OverridesTableProps = {
  data: ShiftOverrideDto[];
  loading: boolean;
  onEdit: (o: ShiftOverrideDto) => void;
};

export function OverridesTable({ data, loading, onEdit }: OverridesTableProps) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const deleteMutation = useMutation({
    mutationFn: (id: number) => shiftOverridesApi.delete(id),
    onSuccess: () => {
      message.success('Override removed; affected dates reverted.');
      queryClient.invalidateQueries({ queryKey: ['shift-overrides'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const cols: TableColumnsType<ShiftOverrideDto> = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      key: 'employeeId',
      width: 120,
      render: (id: number) => <code style={{ fontSize: 12 }}>#{id}</code>,
    },
    {
      title: 'Range',
      key: 'range',
      width: 220,
      render: (_v, row) => (
        <span style={{ fontSize: 12 }}>
          {formatDateMonthDay(row.fromDate)} → {formatDateMonthDay(row.toDate)}{' '}
          <Text type="secondary" style={{ fontSize: 11 }}>
            · {daysBetween(row.fromDate, row.toDate)}d
          </Text>
        </span>
      ),
    },
    {
      title: 'Hours',
      key: 'hours',
      width: 220,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
          <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 13 }}>
            {formatShiftRange(row.shiftStartTime, row.shiftEndTime)}
          </span>
          <Text type="secondary" style={{ fontSize: 11 }}>
            Net{' '}
            {formatMinutes(
              shiftDurationMinutes(
                row.shiftStartTime,
                row.shiftEndTime,
                row.shiftBreakMinutes,
              ),
            )}
            {' · '}break {formatMinutes(row.shiftBreakMinutes)} · grace{' '}
            {formatMinutes(row.shiftGraceMinutes)}
          </Text>
        </span>
      ),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
      render: (r: string | null) => r ?? '—',
    },
    {
      title: '',
      key: 'actions',
      width: 200,
      align: 'right',
      render: (_v, row) => (
        <Space size={4}>
          <Can I="manage" a="attendance">
            <Button
              size="small"
              icon={<Edit3 size={12} />}
              onClick={() => onEdit(row)}
            >
              Edit
            </Button>
            <Popconfirm
              title="Remove this override?"
              description="Every date in the range reverts to the assigned shift (or leaves the row orphaned if there is no assignment) and is recomputed."
              okText="Remove"
              okButtonProps={{ danger: true }}
              onConfirm={() => deleteMutation.mutate(row.id)}
            >
              <Button
                size="small"
                danger
                icon={<Trash2 size={12} />}
                loading={deleteMutation.isPending}
              />
            </Popconfirm>
          </Can>
        </Space>
      ),
    },
  ];

  return (
    <Table
      rowKey="id"
      dataSource={data}
      columns={cols}
      loading={loading}
      size="middle"
      pagination={{ pageSize: 20, hideOnSinglePage: true }}
      // ~760px of fixed column widths — keep the overflow inside the table.
      scroll={{ x: 'max-content' }}
    />
  );
}

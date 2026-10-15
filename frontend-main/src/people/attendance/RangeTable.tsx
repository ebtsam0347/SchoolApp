import {
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';

import { StatusPill } from '@/components';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  formatClock,
  formatClockOn,
  formatMinutes,
  formatDateMonthDay,
  type AttendanceRecordDto,
  type AttendanceStatus,
} from '@/attendance';

const { Text } = Typography;

export type RangeTableProps = {
  records: AttendanceRecordDto[];
  loading: boolean;
};

export function RangeTable({ records, loading }: RangeTableProps) {
  const sorted = records
    .slice()
    .sort((a, b) => b.workDate.localeCompare(a.workDate));

  const cols: TableColumnsType<AttendanceRecordDto> = [
    {
      title: 'Date',
      dataIndex: 'workDate',
      key: 'workDate',
      width: 140,
      render: (d: string) => (
        <span style={{ fontWeight: 500 }}>{formatDateMonthDay(d)}</span>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (s: AttendanceStatus) => (
        <StatusPill tone={ATTENDANCE_STATUS_TONE[s]}>
          {ATTENDANCE_STATUS_LABEL[s]}
        </StatusPill>
      ),
    },
    {
      title: 'In',
      key: 'checkIn',
      width: 80,
      render: (_v, r) => formatClock(r.checkIn),
    },
    {
      title: 'Out',
      key: 'checkOut',
      width: 80,
      render: (_v, r) => formatClockOn(r.checkOut, r.workDate),
    },
    {
      title: 'Worked',
      key: 'worked',
      width: 100,
      render: (_v, r) => formatMinutes(r.workedMinutes),
    },
    {
      title: 'Late',
      key: 'late',
      width: 90,
      render: (_v, r) =>
        r.lateMinutes > 0 ? (
          <Text style={{ color: 'inherit', fontWeight: 500 }}>
            {formatMinutes(r.lateMinutes)}
          </Text>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'OT',
      key: 'ot',
      width: 110,
      render: (_v, r) => {
        const total =
          r.otNormalMinutes + r.otExtraMinutes + r.otSpecialMinutes;
        if (total === 0) return <Text type="secondary">—</Text>;
        return (
          <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>
            <strong>{formatMinutes(total)}</strong>
            {r.otSpecialMinutes > 0 ? <Tag color="purple">special</Tag> : null}
          </span>
        );
      },
    },
  ];

  return (
    <Table
      rowKey="id"
      dataSource={sorted}
      columns={cols}
      loading={loading}
      size="small"
      pagination={{ pageSize: 15, hideOnSinglePage: true }}
      // Keep the columns scrolling inside the table on a phone rather than
      // dragging the page sideways. No-op on a wide viewport.
      scroll={{ x: 'max-content' }}
    />
  );
}

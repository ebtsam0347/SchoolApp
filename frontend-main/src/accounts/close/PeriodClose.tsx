import { Progress, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { KpiGrid, PageHeader, Section, StatCard } from '@/components';
import { CalendarCheck, CheckCircle2, Clock, OctagonAlert } from 'lucide-react';

import { PreviewNotice } from '../PreviewNotice';
import { closeTasks, type CloseTask } from '../mock/data';
import { formatDateShort } from '@/lib/dates';
import { enumLabel } from '@/lib/strings';

const { Text } = Typography;
const { useToken } = antdTheme;

const statusTone: Record<CloseTask['status'], string> = {
  PENDING: 'default',
  IN_PROGRESS: 'blue',
  BLOCKED: 'red',
  DONE: 'green',
};

export function PeriodClose() {
  const { token } = useToken();
  const done = closeTasks.filter((t) => t.status === 'DONE').length;
  const blocked = closeTasks.filter((t) => t.status === 'BLOCKED').length;
  const inProgress = closeTasks.filter((t) => t.status === 'IN_PROGRESS').length;
  const pct = Math.round((done / closeTasks.length) * 100);

  const columns: ColumnsType<CloseTask> = [
    { title: 'Task', dataIndex: 'title', key: 'title', render: (v) => <Text strong>{v}</Text> },
    { title: 'Owner', dataIndex: 'owner', key: 'owner', width: 160 },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (s: CloseTask['status']) => <Tag color={statusTone[s]}>{enumLabel(s)}</Tag>,
    },
    {
      title: 'Due',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 140,
      render: (d: string) => formatDateShort(d),
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="CLOSE"
        title="Period close — May 2026"
        description="The full close runbook with owner assignments, due dates, and live status. Drives the trial balance review."
      />
      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="hero"
          tone="primary"
          icon={CalendarCheck}
          label="Close progress"
          value={`${pct}%`}
          delta={`${done} of ${closeTasks.length} tasks done`}
          trend="up"
        />
        <StatCard
          variant="accent-radial"
          tone="info"
          icon={Clock}
          label="In progress"
          value={inProgress.toString()}
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={OctagonAlert}
          label="Blocked"
          value={blocked.toString()}
          delta="needs CFO attention"
          accentCorner="tr"
        />
        <StatCard
          variant="accent-radial"
          tone="success"
          icon={CheckCircle2}
          label="Completed"
          value={done.toString()}
          accentCorner="tr"
        />
      </KpiGrid>

      <Section title="Runbook">
        <Progress
          percent={pct}
          showInfo={false}
          strokeColor={token.colorPrimary}
          style={{ marginBottom: 12 }}
        />
        <Table<CloseTask>
          rowKey="id"
          columns={columns}
          dataSource={closeTasks}
          pagination={false}
          size="middle"
        />
      </Section>
    </Space>
  );
}

// /people/queues/disciplinary — org-wide disciplinary actions queue.
// Filterable by level / category / status / FY / employee substring.

import { useState } from 'react';
import { createFileRoute, Link } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Input,
  Select,
  Space,
  Table,
  Tag,
  type TableProps,
} from 'antd';
import { Search as SearchIcon, ShieldAlert } from 'lucide-react';

import { KbdHint, StatCard, WarningPill } from '@/components';
import { formatDateShort, useDebounced } from '@/lib';
import {
  DISCIPLINARY_CATEGORY_LABEL,
  disciplinaryApi,
  peopleBff,
  type DisciplinaryActionDto,
  type DisciplinaryCategory,
  type DisciplinaryLevel,
  type DisciplinaryStatus,
} from '@/people';
import { QueuePage } from '@/people/queues';

const LEVELS: DisciplinaryLevel[] = ['FIRST_WARNING', 'SECOND_WARNING', 'FINAL_WARNING'];
const STATUSES: DisciplinaryStatus[] = ['ISSUED', 'CLOSED'];

export const Route = createFileRoute('/_app/people/queues/disciplinary')({
  component: DisciplinaryQueue,
});

function DisciplinaryQueue() {
  const [level, setLevel] = useState<DisciplinaryLevel | undefined>();
  const [category, setCategory] = useState<DisciplinaryCategory | undefined>();
  const [status, setStatus] = useState<DisciplinaryStatus | undefined>();
  const [searchInput, setSearchInput] = useState('');
  const debouncedSearch = useDebounced(searchInput.trim(), 250);
  const [page, setPage] = useState(1);

  const statsQuery = useQuery({
    queryKey: ['people', 'stats'],
    queryFn: ({ signal }) => peopleBff.stats(signal),
  });

  const queueQuery = useQuery({
    queryKey: ['disciplinary', 'global', { level, category, status, q: debouncedSearch, page }],
    queryFn: ({ signal }) =>
      disciplinaryApi.global(
        {
          level,
          category,
          status,
          q: debouncedSearch || undefined,
          page: page - 1,
          size: 25,
        },
        signal,
      ),
  });

  const columns: TableProps<DisciplinaryActionDto>['columns'] = [
    {
      title: 'Level',
      key: 'level',
      width: 180,
      render: (_v, row) =>
        row.level ? (
          <WarningPill
            level={row.level}
            status={row.status}
            decision={row.decision ?? null}
          />
        ) : null,
    },
    {
      title: 'Employee',
      key: 'employee',
      width: 140,
      render: (_v, row) =>
        row.employeeId ? (
          <Link
            to="/people/$id"
            params={{ id: String(row.employeeId) }}
            style={{ textDecoration: 'none' }}
          >
            #{row.employeeId}
          </Link>
        ) : (
          '—'
        ),
    },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      width: 160,
      render: (v?: DisciplinaryCategory) =>
        v ? DISCIPLINARY_CATEGORY_LABEL[v] : '—',
    },
    {
      title: 'Issued',
      dataIndex: 'issuedOn',
      key: 'issuedOn',
      width: 120,
      render: (v?: string) => (v ? formatDateShort(v) : '—'),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: 'Status',
      key: 'status',
      width: 120,
      render: (_v, row) =>
        row.decision ? (
          <Tag color={row.decision === 'UPHELD' ? 'orange' : 'default'}>
            {row.decision}
          </Tag>
        ) : (
          <Tag>{row.status ?? '—'}</Tag>
        ),
    },
  ];

  return (
    <QueuePage
      title="Disciplinary actions"
      description="Org-wide warning queue. Pending rows need a decision; the decide modal lives on the employee's Disciplinary tab."
      onRefresh={() => queueQuery.refetch()}
      isFetching={queueQuery.isFetching}
      kpis={
        <>
          <StatCard
            variant="hero"
            label="Total in queue"
            value={queueQuery.data?.totalElements ?? 0}
            delta="Across all employees · current filters applied"
            icon={ShieldAlert}
          />
          <StatCard
            variant="accent-corner"
            tone="error"
            accentCorner="tr"
            label="Active suspensions"
            value={statsQuery.data?.byStatus?.SUSPENDED ?? 0}
          />
          <StatCard
            variant="accent-corner"
            tone="warning"
            accentCorner="tr"
            label="Showing"
            value={(queueQuery.data?.content?.length ?? 0).toLocaleString('en-PK')}
            delta={`Page ${page}`}
          />
        </>
      }
      filters={
        <Space wrap>
          <Input
            allowClear
            prefix={<SearchIcon size={14} strokeWidth={1.75} />}
            placeholder="Employee name or code"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            style={{ width: 280 }}
            suffix={<KbdHint keys="/" size="sm" />}
          />
          <Select
            allowClear
            placeholder="Any level"
            value={level}
            onChange={setLevel}
            style={{ width: 180 }}
            options={LEVELS.map((l) => ({
              value: l,
              label: l.replace('_', ' ').toLowerCase(),
            }))}
          />
          <Select
            allowClear
            placeholder="Any category"
            value={category}
            onChange={setCategory}
            style={{ width: 200 }}
            options={(Object.keys(DISCIPLINARY_CATEGORY_LABEL) as DisciplinaryCategory[]).map(
              (c) => ({ value: c, label: DISCIPLINARY_CATEGORY_LABEL[c] }),
            )}
          />
          <Select
            allowClear
            placeholder="Any status"
            value={status}
            onChange={setStatus}
            style={{ width: 160 }}
            options={STATUSES.map((s) => ({ value: s, label: s }))}
          />
        </Space>
      }
      error={queueQuery.isError ? queueQuery.error : undefined}
    >
      <Table<DisciplinaryActionDto>
        rowKey={(row) => String(row.id ?? Math.random())}
        loading={queueQuery.isLoading || queueQuery.isFetching}
        dataSource={queueQuery.data?.content ?? []}
        columns={columns}
        size="middle"
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page,
          pageSize: 25,
          total: queueQuery.data?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: setPage,
        }}
      />
    </QueuePage>
  );
}

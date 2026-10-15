// /people/queues/certifications — HR's expiring-cert renewal queue.
// Driven by /certifications?expiringWithin=N (with optional dept/section).

import { useState } from 'react';
import { createFileRoute, Link } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  InputNumber,
  Segmented,
  Select,
  Space,
  Table,
  Typography,
  type TableProps,
} from 'antd';
import { ClipboardList, Download, Sparkles } from 'lucide-react';

import { ExpiryBadge, SelectionToolbar, StatCard } from '@/components';
import { formatDateShort } from '@/lib';
import {
  catalogsApi,
  certificationsApi,
  peopleBff,
  type EmployeeCertificationDto,
} from '@/people';
import { QueuePage } from '@/people/queues';

const { Text } = Typography;

type Mode = 'expiring' | 'expired';

export const Route = createFileRoute('/_app/people/queues/certifications')({
  component: CertificationsQueue,
});

function CertificationsQueue() {
  const [mode, setMode] = useState<Mode>('expiring');
  const [days, setDays] = useState(30);
  const [departmentId, setDepartmentId] = useState<number | undefined>();
  const [sectionId, setSectionId] = useState<number | undefined>();
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);

  const statsQuery = useQuery({
    queryKey: ['people', 'stats'],
    queryFn: ({ signal }) => peopleBff.stats(signal),
  });

  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
  });

  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections', departmentId],
    queryFn: ({ signal }) => catalogsApi.sections(departmentId, signal),
    staleTime: 5 * 60 * 1000,
    enabled: Boolean(departmentId),
  });

  const queueQuery = useQuery({
    queryKey: ['certifications', 'queue', mode, days, departmentId, sectionId],
    queryFn: ({ signal }) =>
      certificationsApi.expiringQueue(
        {
          expiringWithin: mode === 'expiring' ? days : undefined,
          expired: mode === 'expired' ? true : undefined,
          departmentId,
          sectionId,
        },
        signal,
      ),
  });

  const columns: TableProps<EmployeeCertificationDto>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      width: 220,
      render: (_v, row) =>
        row.employeeId ? (
          <Link
            to="/people/$id"
            params={{ id: String(row.employeeId) }}
            style={{ textDecoration: 'none' }}
          >
            <strong>{row.fullName ?? `#${row.employeeId}`}</strong>{' '}
            <Text type="secondary" style={{ fontSize: 12 }}>
              {row.employeeCode}
            </Text>
          </Link>
        ) : (
          '—'
        ),
    },
    {
      title: 'Certification',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Issuing body',
      dataIndex: 'issuingBody',
      key: 'issuingBody',
      render: (v?: string) => v ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Expires',
      dataIndex: 'expiryDate',
      key: 'expiryDate',
      render: (v?: string) =>
        v ? (
          <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            {formatDateShort(v)}
            <ExpiryBadge date={v} mode="expiry" />
          </span>
        ) : (
          <Text type="secondary">No expiry</Text>
        ),
    },
    {
      title: '',
      key: 'open',
      width: 120,
      render: (_v, row) =>
        row.employeeId ? (
          <Link
            to="/people/$id/profile"
            params={{ id: String(row.employeeId) }}
          >
            <Button size="small">Open</Button>
          </Link>
        ) : null,
    },
  ];

  return (
    <QueuePage
      title="Certifications expiring"
      description="Renewal nudge list. Filter by department / section to scope to a unit."
      onRefresh={() => queueQuery.refetch()}
      isFetching={queueQuery.isFetching}
      kpis={
        <>
          <StatCard
            variant="hero"
            label="Expiring within 30 days"
            value={statsQuery.data?.certifications?.expiringWithin30 ?? 0}
            icon={ClipboardList}
          />
          <StatCard
            variant="accent-radial"
            tone="error"
            accentCorner="tr"
            label="Already expired"
            value={statsQuery.data?.certifications?.expired ?? 0}
            icon={Sparkles}
          />
        </>
      }
      filters={
        <Space wrap>
          <Segmented<Mode>
            value={mode}
            onChange={setMode}
            options={[
              { label: 'Expiring soon', value: 'expiring' },
              { label: 'Already expired', value: 'expired' },
            ]}
          />
          {mode === 'expiring' ? (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <Text type="secondary" style={{ fontSize: 13 }}>
                Within
              </Text>
              <InputNumber
                min={1}
                max={365}
                value={days}
                onChange={(v) => setDays(v ?? 30)}
                style={{ width: 90 }}
                suffix="days"
              />
            </span>
          ) : null}
          <Select
            allowClear
            placeholder="Department"
            value={departmentId}
            onChange={(v) => {
              setDepartmentId(v);
              setSectionId(undefined);
            }}
            style={{ width: 200 }}
            loading={departmentsQuery.isLoading}
            options={(departmentsQuery.data ?? [])
              .filter((d) => d.active !== false)
              .map((d) => ({ value: d.id!, label: d.name }))}
          />
          <Select
            allowClear
            placeholder="Section"
            value={sectionId}
            onChange={setSectionId}
            style={{ width: 200 }}
            disabled={!departmentId}
            loading={sectionsQuery.isLoading}
            options={(sectionsQuery.data ?? [])
              .filter((s) => s.active !== false)
              .map((s) => ({ value: s.id!, label: s.name }))}
          />
        </Space>
      }
      error={queueQuery.isError ? queueQuery.error : undefined}
    >
      <Table<EmployeeCertificationDto>
        rowKey={(row) => String(row.id ?? Math.random())}
        loading={queueQuery.isLoading}
        dataSource={queueQuery.data?.content ?? []}
        columns={columns}
        size="middle"
        pagination={{
          pageSize: queueQuery.data?.size ?? 25,
          total: queueQuery.data?.totalElements ?? 0,
          showSizeChanger: false,
        }}
        scroll={{ x: 'max-content' }}
        rowSelection={{
          selectedRowKeys: selectedKeys,
          onChange: setSelectedKeys,
          getCheckboxProps: (row) => ({ disabled: !row.id }),
        }}
      />

      <SelectionToolbar
        selectedCount={selectedKeys.length}
        totalCount={queueQuery.data?.totalElements ?? 0}
        onClear={() => setSelectedKeys([])}
        actions={
          <Button
            icon={<Download size={14} strokeWidth={1.75} />}
            onClick={() => {
              const rows = queueQuery.data?.content ?? [];
              const selectedRows = rows.filter((r) =>
                selectedKeys.includes(String(r.id)),
              );
              downloadCertificationsCsv(selectedRows);
            }}
          >
            Export CSV
          </Button>
        }
      />
    </QueuePage>
  );
}

function downloadCertificationsCsv(rows: EmployeeCertificationDto[]) {
  const header = ['Employee', 'Code', 'Certification', 'Issuing body', 'Expires'];
  const lines = rows.map((r) =>
    [
      r.fullName ?? '',
      r.employeeCode ?? '',
      r.name ?? '',
      r.issuingBody ?? '',
      r.expiryDate ?? '',
    ]
      .map((v) => `"${String(v).replace(/"/g, '""')}"`)
      .join(','),
  );
  const csv = [header.join(','), ...lines].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `certifications-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// /canteen/devices — the readers the canteen desk is responsible for.
//
// Read-only. Hardware lifecycle stays in the HR-admin catalog at
// /admin/devices, which owns creation, comm passwords, HWM resets and
// snapshots; this page answers the narrower question the canteen desk has —
// "is my reader up, and when did it last report?"
//
// Columns follow /admin/devices so the two read the same way, minus its
// row actions. No status or location filter either: a canteen has a handful
// of readers, and filtering a list you can see in full is just chrome.

import { useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Table, Tag, Tooltip, Typography, type TableProps } from 'antd';
import { MonitorSmartphone } from 'lucide-react';

import {
  FilterBar,
  KpiGrid,
  PageHeader,
  RequestState,
  StatCard,
  StatusPill,
} from '@/components';
import { forbiddenOr, formatDateTime, matchesAny } from '@/lib';
import type { AttendanceDeviceDto, AttendanceDeviceStatus } from '@/attendance';
import { CanteenTabs, canteenDevicesApi } from '@/canteen';

const { Text } = Typography;

const STATUS_TONE: Record<AttendanceDeviceStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  ONLINE: 'success',
  OFFLINE: 'warning',
  ERROR: 'error',
  UNKNOWN: 'neutral',
};

const STATUS_LABEL: Record<AttendanceDeviceStatus, string> = {
  ONLINE: 'Online',
  OFFLINE: 'Offline',
  ERROR: 'Error',
  UNKNOWN: 'Unknown',
};

export const Route = createFileRoute('/_app/canteen/devices')({
  component: CanteenDevicesPage,
});

function CanteenDevicesPage() {
  const [q, setQ] = useState('');

  const devicesQuery = useQuery({
    queryKey: ['canteen', 'devices'],
    queryFn: ({ signal }) => canteenDevicesApi.list(signal),
    staleTime: 30_000,
  });

  const all = devicesQuery.data ?? [];
  const rows = q.trim()
    ? all.filter((d) => matchesAny(d, q, ['code', 'name']))
    : all;

  const online = all.filter((d) => d.status === 'ONLINE').length;
  const unmatched = all.reduce((sum, d) => sum + (d.unmatchedCount ?? 0), 0);

  const columns: TableProps<AttendanceDeviceDto>['columns'] = [
    {
      title: 'Device',
      key: 'device',
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 2 }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <code style={{ fontSize: 10, fontWeight: 500 }}>code: {row.code}</code>
          </span>
          <span style={{ fontWeight: 500, fontSize: 13 }}>{row.name}</span>
          
        </span>
      ),
    },
    {
      title: 'ID',
      key: 'id',
      width: 100,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 11 }}>
          {row.id}
        </Text>
      ),
    },
    {
      title: 'Device Type',
      key: 'deviceType',
      width: 150,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 11 }}>
          {row.deviceType ?? '—'}
        </Text>
      ),
    },
    {
      title: 'Location',
      key: 'location',
      width: 200,
      render: (_v, row) => (
        row.location ? (
          <Text type="secondary" style={{ fontSize: 11 }}>
            {row.location}
          </Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 11 }}>
            —
          </Text>
        )
      ),
    },
    {
      title: 'Network',
      key: 'network',
      width: 170,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 2 }}>
          <span style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}>
            {row.deviceIp ?? '—'}
            {row.deviceIp && row.commPort ? `:${row.commPort}` : ''}
          </span>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {row.commPasswordSet ? 'Password set' : 'No password'}
          </Text>
        </span>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 180,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 4 }}>
          <StatusPill tone={STATUS_TONE[row.status]} size="sm">
            {STATUS_LABEL[row.status]}
          </StatusPill>
          <Text type="secondary" style={{ fontSize: 11, fontVariantNumeric: 'tabular-nums' }}>
            {row.lastSeenAt ? formatDateTime(row.lastSeenAt) : 'never seen'}
          </Text>
        </span>
      ),
    },
    {
      title: 'Sync',
      key: 'sync',
      width: 200,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 4 }}>
          <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
            {row.lastPunchedAt ? (
              formatDateTime(row.lastPunchedAt)
            ) : (
              <Text type="secondary" style={{ fontSize: 12 }}>
                no punches yet
              </Text>
            )}
          </span>
          {row.unmatchedCount > 0 ? (
            <Tooltip title="Punches the poller pulled but could not match to an employee. Map the card numbers to clear them.">
              <Tag color="warning" style={{ marginInlineEnd: 0, alignSelf: 'flex-start' }}>
                {row.unmatchedCount.toLocaleString('en-PK')} unmatched
              </Tag>
            </Tooltip>
          ) : null}
        </span>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <CanteenTabs />
      <PageHeader
        eyebrow="Canteen"
        title="Devices"
        description="Readers feeding the canteen meal ledger. Managed from the admin device catalog."
        level={4}
      />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          accentCorner="tr"
          icon={MonitorSmartphone}
          label="Canteen readers"
          value={String(all.length)}
          delta={`${online} online right now`}
        />
        <StatCard
          variant="plain"
          tone={unmatched > 0 ? 'warning' : 'success'}
          label="Unmatched punches"
          value={String(unmatched)}
          delta="Cards with no employee behind them."
        />
      </KpiGrid>

      <FilterBar
        search={{ value: q, onChange: setQ, placeholder: 'Search code or name…' }}
        onReset={q ? () => setQ('') : undefined}
      />

      <RequestState
        loading={devicesQuery.isLoading}
        variant="table"
        error={devicesQuery.isError ? devicesQuery.error : undefined}
        empty={!devicesQuery.isLoading && rows.length === 0}
        emptyIcon={MonitorSmartphone}
        filterActive={Boolean(q)}
        onClearFilters={() => setQ('')}
        emptyTitle="No canteen readers"
        emptyDescription="No device is registered against the canteen yet."
        errorTitle={forbiddenOr(
          devicesQuery.error,
          "You don't have permission to view canteen devices.",
          "Couldn't load canteen devices",
        )}
        onRetry={() => devicesQuery.refetch()}
      >
        <Table<AttendanceDeviceDto>
          rowKey="id"
          dataSource={rows}
          columns={columns}
          size="middle"
          scroll={{ x: 'max-content' }}
          pagination={false}
        />
      </RequestState>
    </div>
  );
}

// /me/belongings — self-service belongings for the signed-in employee.
//
// Shows only:
//   - "Issued to you" — belongings already issued (GET
//     /belonging-issuance/employee/{id}/active), read-only. Changing
//     activation/clearance stays an HR action on /admin/belonging-issuance.

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { Table, Typography, type TableProps } from 'antd';
import { PackageCheck } from 'lucide-react';

import { EmptyState, PageHeader, RequestState, Section, StatusPill } from '@/components';
import { formatDateShort, readSnapshot } from '@/lib';
import { employeesApi } from '@/people';
import {
  belongingIssuanceApi,
  BelongingIssuanceDetailDrawer,
  CLEARANCE_STATUS_LABEL,
  CLEARANCE_STATUS_TONE,
  type BelongingIssuanceDto,
} from '@/belongings';

const { Text } = Typography;

export const Route = createFileRoute('/_app/me_/belongings')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (snap?.user && snap.user.employeeLinked === false) {
      throw redirect({ to: '/account' });
    }
  },
  component: MyBelongingsPage,
});

function MyBelongingsPage() {
  const [viewingRow, setViewingRow] = useState<BelongingIssuanceDto | null>(null);

  const meQuery = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: ({ signal }) => employeesApi.me(signal),
  });
  const employeeId = meQuery.data?.id;

  const activeQuery = useQuery({
    queryKey: ['belonging-issuance', 'employee', employeeId, 'active'],
    queryFn: ({ signal }) => belongingIssuanceApi.employeeActive(employeeId!, signal),
    enabled: employeeId != null,
  });

  const clearanceQuery = useQuery({
    queryKey: ['belonging-issuance', 'employee', employeeId, 'clearance'],
    queryFn: ({ signal }) => belongingIssuanceApi.employeeClearance(employeeId!, signal),
    enabled: employeeId != null,
  });

  const issuedRows = activeQuery.data ?? [];
  const pendingClearance = (clearanceQuery.data ?? []).filter(
    (r) => r.clearanceStatus === 'PENDING',
  );

  const issuedCols: TableProps<BelongingIssuanceDto>['columns'] = [
    { title: 'Asset Type', dataIndex: 'belongingName', key: 'belongingName' },
    {
      title: 'Asset',
      key: 'asset',
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
          <span>{row.assetModel ?? '—'}</span>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {[row.assetYear, row.condition].filter(Boolean).join(' · ') || '—'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Issued',
      key: 'issuedDate',
      render: (_v, row) => (row.issuedDate ? formatDateShort(row.issuedDate) : '—'),
    },
    {
      title: 'Expiry',
      key: 'expiryDate',
      render: (_v, row) => (row.expiryDate ? formatDateShort(row.expiryDate) : '—'),
    },
    {
      title: 'Clearance',
      key: 'clearanceStatus',
      render: (_v, row) =>
        row.clearanceStatus ? (
          <StatusPill tone={CLEARANCE_STATUS_TONE[row.clearanceStatus]}>
            {CLEARANCE_STATUS_LABEL[row.clearanceStatus]}
          </StatusPill>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PageHeader title="Allocated Assets" description="" />

      <Section title="Issued to you">
        <RequestState
          loading={meQuery.isLoading || activeQuery.isLoading}
          variant="table"
          error={activeQuery.isError ? activeQuery.error : undefined}
          errorTitle="Couldn't load your assets"
          onRetry={() => activeQuery.refetch()}
          empty={!activeQuery.isLoading && issuedRows.length === 0}
          emptyFallback={
            <EmptyState
              icon={PackageCheck}
              title="Nothing issued to you yet"
              description="Assets issued by HR will appear here."
            />
          }
        >
          <Table<BelongingIssuanceDto>
            rowKey={(r) => r.id ?? Math.random()}
            columns={issuedCols}
            dataSource={issuedRows}
            loading={activeQuery.isFetching}
            size="middle"
            scroll={{ x: 'max-content' }}
            pagination={false}
            onRow={(row) => ({
              onClick: () => setViewingRow(row),
              style: { cursor: 'pointer' },
            })}
          />
        </RequestState>

        {pendingClearance.length > 0 ? (
          <Text type="secondary">
            {pendingClearance.length} item{pendingClearance.length === 1 ? '' : 's'} awaiting
            clearance.
          </Text>
        ) : null}
      </Section>

      <BelongingIssuanceDetailDrawer row={viewingRow} onClose={() => setViewingRow(null)} />
    </div>
  );
}

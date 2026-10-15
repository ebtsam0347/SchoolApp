// /people/$id/assets — assets issued to this employee. Scoped view of the
// same Belonging Issuance data the admin Asset Issuance page manages
// globally, using the employee-service surface's per-employee endpoints
// (`/belonging-issuance/employee/{id}/active` + `/clearance`).

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute, useParams } from '@tanstack/react-router';
import { Button, Input, Segmented, Table } from 'antd';
import { Package, Search, Tag } from 'lucide-react';

import { Can } from '@/auth';
import { EmptyState, RequestState, Section, StatusPill } from '@/components';
import { catalogsApi, type BelongingMasterDto } from '@/people';
import {
  ACTIVATION_STATUS_LABEL,
  AssetTypeFormDrawer,
  BelongingIssuanceFormDrawer,
  BelongingIssuanceTable,
  CLEARANCE_STATUS_LABEL,
  belongingIssuanceApi,
  type BelongingIssuanceDto,
} from '@/belongings';
import { formatDateShort } from '@/lib/dates';

export const Route = createFileRoute('/_app/people/$id/assets')({
  component: AssetsTab,
});

type View = 'active' | 'unissued';

// Substring match across every column the table renders — asset type, model
// / year / condition, dates, and status labels — so one search box covers
// what would otherwise need a filter per column.
function matchesSearch(row: BelongingIssuanceDto, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  const fields = [
    row.belongingName,
    row.assetModel,
    row.assetYear != null ? String(row.assetYear) : undefined,
    row.condition,
    row.issuedDate ? formatDateShort(row.issuedDate) : undefined,
    row.expiryDate ? formatDateShort(row.expiryDate) : undefined,
    row.activationStatus ? ACTIVATION_STATUS_LABEL[row.activationStatus] : undefined,
    row.clearanceStatus ? CLEARANCE_STATUS_LABEL[row.clearanceStatus] : undefined,
    row.description,
  ];
  return fields.some((f) => f?.toLowerCase().includes(q));
}

function AssetsTab() {
  const { id } = useParams({ from: '/_app/people/$id/assets' });
  const employeeId = Number(id);

  const [view, setView] = useState<View>('active');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<BelongingIssuanceDto | null>(null);
  const [addAssetOpen, setAddAssetOpen] = useState(false);
  const [issueTypeId, setIssueTypeId] = useState<number | undefined>(undefined);
  const [search, setSearch] = useState('');

  const activeQuery = useQuery({
    queryKey: ['belonging-issuance', 'employee', employeeId, 'active'],
    queryFn: ({ signal }) => belongingIssuanceApi.employeeActive(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });
  const clearanceQuery = useQuery({
    queryKey: ['belonging-issuance', 'employee', employeeId, 'clearance'],
    queryFn: ({ signal }) => belongingIssuanceApi.employeeClearance(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  // Asset Types (master data) authorized to this employee that haven't been
  // issued yet — surfaced separately so HR can see + act on authorizations
  // that exist in master data but never turned into an actual issuance.
  const mastersQuery = useQuery({
    queryKey: ['catalog', 'belonging-master-all'],
    queryFn: ({ signal }) => catalogsApi.belongingMasters(signal),
    staleTime: 5 * 60_000,
  });
  const issuedMasterIds = new Set(
    [...(activeQuery.data ?? []), ...(clearanceQuery.data ?? [])]
      .map((r) => r.belongingMasterId)
      .filter((id): id is number => id != null),
  );
  const unissuedTypes = (mastersQuery.data ?? []).filter(
    (m) => m.authorizedEmployeeId === employeeId && m.id != null && !issuedMasterIds.has(m.id),
  );

  // Filter client-side on the row's own status field rather than trusting
  // the endpoint to have already scoped it, then apply the search box on
  // top of that.
  const activeRows = (activeQuery.data ?? []).filter((r) => r.activationStatus !== 'INACTIVE');
  const rows = activeRows.filter((r) => matchesSearch(r, search));

  const openEdit = (row: BelongingIssuanceDto) => {
    setEditing(row);
    setDrawerOpen(true);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <Segmented<View>
          value={view}
          onChange={setView}
          options={[
            {
              value: 'unissued',
              label:
                unissuedTypes.length > 0
                  ? `Not yet issued (${unissuedTypes.length})`
                  : 'Not yet issued',
            },
            { value: 'active', label: 'Active' },
          ]}
        />
        <Can I="write" a="employee">
          <Button icon={<Tag size={14} />} onClick={() => setAddAssetOpen(true)}>
            Add Asset
          </Button>
        </Can>
      </div>

      {view === 'active' && (
        <Input
          allowClear
          prefix={<Search size={14} strokeWidth={1.75} />}
          placeholder="Search asset type, model, year, condition, dates, status…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ maxWidth: 420 }}
          aria-label="Search assets"
        />
      )}

      {view === 'unissued' ? (
        <Section
          title="Asset Types"
          subtitle={`${unissuedTypes.length.toLocaleString('en-PK')} authorized, not yet issued to this employee`}
        >
          <RequestState
            loading={mastersQuery.isLoading}
            variant="table"
            error={mastersQuery.isError ? mastersQuery.error : undefined}
            errorTitle="Couldn't load asset types"
            onRetry={() => mastersQuery.refetch()}
            empty={!mastersQuery.isLoading && unissuedTypes.length === 0}
            emptyFallback={
              <EmptyState
                icon={Tag}
                title="Nothing pending issuance"
                description="Every asset type authorized to this employee has already been issued."
              />
            }
          >
            <Table<BelongingMasterDto>
              rowKey={(m) => m.id ?? m.belongingName ?? ''}
              dataSource={unissuedTypes}
              pagination={false}
              // Scroll wide columns inside the table, not the page.
              scroll={{ x: 'max-content' }}
              columns={[
                { title: 'Asset Type', dataIndex: 'belongingName' },
                {
                  title: 'Status',
                  key: 'status',
                  render: (_v, m) => (
                    <StatusPill tone={m.active === false ? 'neutral' : 'success'}>
                      {m.active === false ? 'Inactive' : 'Active'}
                    </StatusPill>
                  ),
                },
                {
                  title: '',
                  key: 'actions',
                  align: 'right',
                  render: (_v, m) => (
                    <Button size="small" type="primary" onClick={() => setIssueTypeId(m.id)}>
                      Issue
                    </Button>
                  ),
                },
              ]}
            />
          </RequestState>
        </Section>
      ) : (
        <Section
          title="Assets"
          subtitle={
            search.trim()
              ? `${rows.length.toLocaleString('en-PK')} of ${activeRows.length.toLocaleString('en-PK')} active`
              : `${rows.length.toLocaleString('en-PK')} active`
          }
        >
          <RequestState
            loading={activeQuery.isLoading}
            variant="table"
            error={activeQuery.isError ? activeQuery.error : undefined}
            errorTitle="Couldn't load assets"
            onRetry={() => activeQuery.refetch()}
            empty={!activeQuery.isLoading && rows.length === 0}
            emptyFallback={
              <EmptyState
                icon={Package}
                title={search.trim() ? 'No matches' : 'No allocated assets'}
                description={
                  search.trim()
                    ? 'Try a different search term.'
                    : 'Use Actions → Allocate asset above to allocate one to this employee.'
                }
              />
            }
          >
            <BelongingIssuanceTable
              data={rows}
              loading={activeQuery.isFetching}
              hideEmployeeColumn
              onEdit={openEdit}
            />
          </RequestState>
        </Section>
      )}

      <BelongingIssuanceFormDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        mode="employee"
        presetEmployeeId={employeeId}
        issuance={editing}
      />

      <BelongingIssuanceFormDrawer
        open={issueTypeId != null}
        onClose={() => setIssueTypeId(undefined)}
        mode="employee"
        presetEmployeeId={employeeId}
        presetBelongingMasterId={issueTypeId}
      />

      <AssetTypeFormDrawer
        open={addAssetOpen}
        onClose={() => setAddAssetOpen(false)}
        employeeId={employeeId}
      />
    </div>
  );
}
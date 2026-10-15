// /admin/belonging-issuance — issue Belonging Master items to employees and
// track activation / clearance. The employee-service surface only exposes
// filtered lists (`/active`, `/clearance`) — there's no unfiltered "get
// all" — so this page is built around those two views via a segmented
// control instead of a single table + status filter.

import { useMemo, useState } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { Button, Segmented, Space, Tooltip } from 'antd';
import { Package, PackageCheck, Plus, RefreshCcw, ShieldQuestion } from 'lucide-react';

import { Can } from '@/auth';
import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { employeesApi, type EmployeeDto } from '@/people';
import {
  BelongingIssuanceFormDrawer,
  BelongingIssuanceTable,
  belongingIssuanceApi,
  type BelongingIssuanceDto,
} from '@/belongings';

type View = 'active' | 'clearance';

type Search = { view?: View };

export const Route = createFileRoute('/_app/admin/belonging-issuance')({
  validateSearch: (search): Search => ({
    view: search.view === 'clearance' ? 'clearance' : undefined,
  }),
  component: RouteComponent,
});

function RouteComponent() {
  const navigate = useNavigate({ from: '/admin/belonging-issuance' });
  const search = useSearch({ from: '/_app/admin/belonging-issuance' });
  const view: View = search.view ?? 'active';

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<BelongingIssuanceDto | null>(null);

  const activeQuery = useQuery({
    queryKey: ['belonging-issuance', 'active'],
    queryFn: ({ signal }) => belongingIssuanceApi.active(signal),
  });
  const clearanceQuery = useQuery({
    queryKey: ['belonging-issuance', 'clearance'],
    queryFn: ({ signal }) => belongingIssuanceApi.pendingClearance(signal),
  });

  // Filter client-side on the row's own status fields rather than trusting
  // the endpoints to have already scoped them — /clearance has been observed
  // returning issuances whose clearanceStatus is no longer PENDING (already
  // resolved), which made the Active and Pending-clearance tabs show the
  // same rows.
  const rows =
    view === 'active'
      ? (activeQuery.data ?? []).filter((r) => r.activationStatus !== 'INACTIVE')
      : (clearanceQuery.data ?? []).filter((r) => r.clearanceStatus === 'PENDING');
  const listQuery = view === 'active' ? activeQuery : clearanceQuery;

  const employeeIds = useMemo(
    () =>
      Array.from(
        new Set(
          [...(activeQuery.data ?? []), ...(clearanceQuery.data ?? [])]
            .map((r) => r.employeeId)
            .filter((id): id is number => id != null),
        ),
      ),
    [activeQuery.data, clearanceQuery.data],
  );

  const nameQueries = useQueries({
    queries: employeeIds.map((id) => ({
      queryKey: ['employees', 'detail', id],
      queryFn: ({ signal }: { signal: AbortSignal }) => employeesApi.get(id, signal),
      staleTime: 5 * 60_000,
    })),
  });

  const employeeById = useMemo(() => {
    const m = new Map<number, EmployeeDto>();
    for (const q of nameQueries) {
      if (q.data?.id != null) m.set(q.data.id, q.data);
    }
    return m;
  }, [nameQueries]);

  const openCreate = () => {
    setEditing(null);
    setDrawerOpen(true);
  };
  const openEdit = (row: BelongingIssuanceDto) => {
    setEditing(row);
    setDrawerOpen(true);
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Admin"
        title="Allocated Assets"
        description="Allocate assets to employees and track activation & clearance."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
            <Can I="write" a="employee">
              <Button type="primary" icon={<Plus size={14} />} onClick={openCreate}>
                Allocate asset
              </Button>
            </Can>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="success"
          icon={PackageCheck}
          accentCorner="tr"
          label="Active issuances"
          value={`${activeQuery.data?.length ?? 0}`}
        />
        <StatCard
          variant="plain"
          tone={(clearanceQuery.data?.length ?? 0) > 0 ? 'warning' : 'neutral'}
          icon={ShieldQuestion}
          label="Pending clearance"
          value={`${clearanceQuery.data?.length ?? 0}`}
        />
      </KpiGrid>

      <Section
        title="Issuances"
        subtitle={`${rows.length.toLocaleString('en-PK')} ${view === 'active' ? 'active' : 'pending clearance'}`}
        actions={
          <Segmented<View>
            value={view}
            onChange={(v) => navigate({ search: { view: v === 'active' ? undefined : v } })}
            options={[
              { value: 'active', label: 'Active' },
              { value: 'clearance', label: 'Pending clearance' },
            ]}
          />
        }
      >
        <RequestState
          loading={listQuery.isLoading}
          variant="table"
          error={listQuery.isError ? listQuery.error : undefined}
          errorTitle="Couldn't load issuances"
          onRetry={() => listQuery.refetch()}
          empty={!listQuery.isLoading && rows.length === 0}
          emptyFallback={
            <EmptyState
              icon={Package}
              title={view === 'active' ? 'No active issuances' : 'Nothing pending clearance'}
              description={
                view === 'active'
                  ? 'Allocate an asset to an employee to get started.'
                  : 'Issuances awaiting clearance (e.g. on exit or return) will show up here.'
              }
              action={
                view === 'active' ? (
                  <Can I="write" a="employee">
                    <Button type="primary" icon={<Plus size={14} />} onClick={openCreate}>
                      Allocate asset
                    </Button>
                  </Can>
                ) : undefined
              }
            />
          }
        >
          <BelongingIssuanceTable
            data={rows}
            loading={listQuery.isFetching}
            employeeById={employeeById}
            onEdit={openEdit}
          />
        </RequestState>
      </Section>

      <BelongingIssuanceFormDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        issuance={editing}
      />
    </div>
  );
}

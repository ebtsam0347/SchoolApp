// /self-service/travel-request/requests — the "Travel Requests" tab.
//
// Reads come from services/employee's Travel API via the gateway
// (GET /travel/requests, cancel via /travel/requests/{id}/cancel); the
// response is adapted onto the Factory Tour view shape so the table and
// drawers stay unchanged. Create only saves the request as a draft — the
// owner sends it on with the row's Submit button
// (POST /travel/requests/{id}/submit).

import { useState } from 'react';
import type { Key } from 'react';
import { App as AntdApp, Button, Card, Select, Space, Tooltip } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import {
  Ban,
  FileCheck2,
  Plus,
  RefreshCcw,
  Send,
  Truck,
  XCircle,
} from 'lucide-react';

import {
  EmptyState,
  FilterBar,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { getErrorMessage } from '@/lib';
import { catalogsApi } from '@/people';
import {
  FACTORY_TOUR_STATUS_LABEL,
  FactoryTourDetailDrawer,
  FactoryTourExpensesModal,
  FactoryTourFormDrawer,
  FactoryTourSettlementModal,
  FactoryTourTable,
  type FactoryTourDto,
  type FactoryTourStatus,
} from '@/factory-tour';
import { toFactoryTourDto, travelRequestApi } from '@/travel';

const STATUS_VALUES: FactoryTourStatus[] = [
  'DRAFT',
  'PENDING_APPROVAL',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
];

export const Route = createFileRoute('/_app/self-service/travel-request/requests')({
  component: TravelRequestsTab,
});

function TravelRequestsTab() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingTour, setEditingTour] = useState<FactoryTourDto | null>(null);
  const [viewingTour, setViewingTour] = useState<FactoryTourDto | null>(null);
  const [expensesTour, setExpensesTour] = useState<FactoryTourDto | null>(null);
  const [settlementTour, setSettlementTour] = useState<FactoryTourDto | null>(null);
  const [statusFilter, setStatusFilter] = useState<FactoryTourStatus | undefined>();
  const [search, setSearch] = useState('');
  const [selectedKeys, setSelectedKeys] = useState<Key[]>([]);
  const { message, modal } = AntdApp.useApp();
  const qc = useQueryClient();

  const listQuery = useQuery({
    queryKey: ['travel-requests'],
    queryFn: async ({ signal }) => {
      const page = await travelRequestApi.search({}, signal);
      return (page.content ?? []).map(toFactoryTourDto);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (row: FactoryTourDto) => travelRequestApi.cancel(row.id),
    onSuccess: () => {
      message.success('Travel request cancelled');
      qc.invalidateQueries({ queryKey: ['travel-requests'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const submitMutation = useMutation({
    mutationFn: (row: FactoryTourDto) => travelRequestApi.submit(row.id),
    onSuccess: () => {
      message.success('Travel request submitted for approval');
      qc.invalidateQueries({ queryKey: ['travel-requests'] });
      qc.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const bulkCancelMutation = useMutation({
    mutationFn: (selected: FactoryTourDto[]) =>
      Promise.all(selected.map((row) => travelRequestApi.cancel(row.id))),
    onSuccess: (_data, selected) => message.success(`Cancelled ${selected.length}`),
    onError: (err) => message.error(getErrorMessage(err)),
    onSettled: () => {
      setSelectedKeys([]);
      qc.invalidateQueries({ queryKey: ['travel-requests'] });
    },
  });

  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
  });
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections'],
    queryFn: ({ signal }) => catalogsApi.sections(undefined, signal),
    staleTime: 5 * 60 * 1000,
  });
  const designationsQuery = useQuery({
    queryKey: ['catalog', 'designations'],
    queryFn: ({ signal }) => catalogsApi.designations(signal),
    staleTime: 5 * 60 * 1000,
  });
  const gradesQuery = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: 5 * 60 * 1000,
  });

  const allRows = listQuery.data ?? [];
  const rows = statusFilter ? allRows.filter((r) => r.status === statusFilter) : allRows;

  const searchQuery = search.trim().toLowerCase();
  const filteredRows = !searchQuery
    ? rows
    : rows.filter((r) => {
        const fields = [
          r.requestId,
          r.name,
          r.cardNo,
          r.origin,
          r.destination,
          r.purpose,
          FACTORY_TOUR_STATUS_LABEL[r.status],
        ];
        return fields.some((f) => f?.toLowerCase().includes(searchQuery));
      });

  const selectedRows = rows.filter((r) => selectedKeys.includes(r.id));

  const countFor = (status: FactoryTourStatus) =>
    allRows.filter((r) => r.status === status).length;

  const hasActiveFilters = Boolean(statusFilter) || Boolean(search);
  const clearFilters = () => {
    setStatusFilter(undefined);
    setSearch('');
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, paddingBottom: 32 }}>
      <PageHeader
        eyebrow=""
        title="Travel Requests"
        description="Raise a travel/vehicle request for a factory visit. Flow: Draft → Pending approval → Approved."
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
            <Button
              type="primary"
              icon={<Plus size={14} />}
              onClick={() => {
                setEditingTour(null);
                setDrawerOpen(true);
              }}
            >
              Create
            </Button>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={Send}
          accentCorner="tr"
          label="Pending approval"
          value={`${countFor('PENDING_APPROVAL')}`}
          delta="awaiting a decision"
        />
        <StatCard
          variant="plain"
          tone="success"
          icon={FileCheck2}
          label="Approved"
          value={`${countFor('APPROVED')}`}
        />
        <StatCard
          variant="plain"
          tone={countFor('REJECTED') > 0 ? 'error' : 'neutral'}
          icon={XCircle}
          label="Rejected"
          value={`${countFor('REJECTED')}`}
        />
        <StatCard
          variant="plain"
          tone="neutral"
          icon={Ban}
          label="Cancelled"
          value={`${countFor('CANCELLED')}`}
        />
      </KpiGrid>

      <Section
        title="Travel Requests"
        subtitle={`${allRows.length.toLocaleString('en-PK')} total`}
        actions={
          <Select<FactoryTourStatus | undefined>
            allowClear
            placeholder="Status"
            style={{ width: 'clamp(140px, 45vw, 180px)' }}
            value={statusFilter}
            onChange={setStatusFilter}
            options={STATUS_VALUES.map((s) => ({
              value: s,
              label: FACTORY_TOUR_STATUS_LABEL[s],
            }))}
          />
        }
      >
        {selectedRows.length > 0 ? (
          <Card
            size="small"
            styles={{ body: { padding: '10px 14px' } }}
            style={{ marginBottom: 12 }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <span style={{ fontSize: 13 }}>{selectedRows.length} selected</span>
              <Space>
                <Button onClick={() => setSelectedKeys([])}>Clear</Button>
                <Button
                  danger
                  icon={<XCircle size={12} />}
                  loading={bulkCancelMutation.isPending}
                  onClick={() =>
                    modal.confirm({
                      title: `Cancel ${selectedRows.length} travel request(s)?`,
                      content: 'This cannot be undone.',
                      okText: 'Cancel requests',
                      okButtonProps: { danger: true },
                      cancelText: 'Keep',
                      onOk: () => bulkCancelMutation.mutate(selectedRows),
                    })
                  }
                >
                  Cancel
                </Button>
              </Space>
            </div>
          </Card>
        ) : null}

        <FilterBar
          search={{
            value: search,
            onChange: setSearch,
            placeholder: 'Search request ID, destination, purpose…',
          }}
          style={{ marginBottom: 12 }}
        />

        <RequestState
          loading={listQuery.isLoading}
          variant="table"
          error={listQuery.isError ? listQuery.error : undefined}
          errorTitle="Couldn't load your official travel requests"
          onRetry={() => listQuery.refetch()}
          empty={!listQuery.isLoading && filteredRows.length === 0}
          filterActive={hasActiveFilters}
          onClearFilters={clearFilters}
          emptyFallback={
            <EmptyState
              icon={Truck}
              title="No official travel requests yet"
              description="Create a request when you need to travel for a factory visit or tour."
            />
          }
        >
          <FactoryTourTable
            data={filteredRows}
            loading={listQuery.isFetching}
            departments={departmentsQuery.data}
            sections={sectionsQuery.data}
            designations={designationsQuery.data}
            grades={gradesQuery.data}
            onCancel={(row) => cancelMutation.mutate(row)}
            cancellingId={cancelMutation.isPending ? cancelMutation.variables?.id : undefined}
            onSubmit={(row) => submitMutation.mutate(row)}
            submittingId={submitMutation.isPending ? submitMutation.variables?.id : undefined}
            onDetails={setViewingTour}
            onExpenses={setExpensesTour}
            onSettlement={setSettlementTour}
            rowSelection={{
              selectedRowKeys: selectedKeys,
              onChange: setSelectedKeys,
              getCheckboxProps: (row) => ({ disabled: !row.actions.includes('CANCEL') }),
            }}
          />
        </RequestState>
      </Section>

      <FactoryTourFormDrawer
        open={drawerOpen}
        tour={editingTour}
        onClose={() => {
          setDrawerOpen(false);
          setEditingTour(null);
        }}
      />

      <FactoryTourExpensesModal tour={expensesTour} onClose={() => setExpensesTour(null)} />

      <FactoryTourSettlementModal
        tour={settlementTour}
        onClose={() => setSettlementTour(null)}
        departments={departmentsQuery.data}
        designations={designationsQuery.data}
        grades={gradesQuery.data}
      />

      <FactoryTourDetailDrawer
        row={viewingTour}
        onClose={() => setViewingTour(null)}
        departments={departmentsQuery.data}
        sections={sectionsQuery.data}
        designations={designationsQuery.data}
        grades={gradesQuery.data}
        onEdit={(row) => {
          setViewingTour(null);
          setEditingTour(row);
          setDrawerOpen(true);
        }}
      />
    </div>
  );
}

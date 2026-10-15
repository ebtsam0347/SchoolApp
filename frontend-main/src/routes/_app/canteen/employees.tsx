// /canteen/employees — the Labour / Boufet roster.
//
// "Boufet" is a canteen-side staffing bucket, not a payroll grade type, so
// there is nothing in the employee service to derive it from today —
// `GET /canteen/employees` ships in the next phase. Until it answers, the
// page renders an explicit pending notice rather than a red 404 or, worse,
// a plausible-looking list assembled from the wrong source.

import { useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Segmented, Select, Table, Typography, type TableProps } from 'antd';
import { Users } from 'lucide-react';

import {
  FilterBar,
  PageHeader,
  PersonCell,
  RequestState,
  StatusPill,
} from '@/components';
import { forbiddenOr, isNotFound, useDebounced } from '@/lib';
import { catalogsApi } from '@/people';
import {
  CANTEEN_CATEGORY_LABEL,
  CANTEEN_CATEGORY_TONE,
  CanteenTabs,
  PendingBackendNotice,
  canteenEmployeesApi,
  isCanteenCategory,
  type CanteenEmployeeCategory,
  type CanteenEmployeeDto,
} from '@/canteen';

const { Text } = Typography;

/**
 * Only the category rides in the URL — it is the one filter worth linking
 * to ("show me the Boufet list"). Search text, department and pagination
 * stay local, matching how /admin/devices treats its own toolbar.
 */
type RosterSearch = {
  category?: CanteenEmployeeCategory;
};

export const Route = createFileRoute('/_app/canteen/employees')({
  validateSearch: (search): RosterSearch => ({
    category: isCanteenCategory(search.category) ? search.category : undefined,
  }),
  component: CanteenEmployeesPage,
});

function CanteenEmployeesPage() {
  const { category: categoryParam } = Route.useSearch();
  const [category, setCategory] = useState<CanteenEmployeeCategory | 'ALL'>(
    categoryParam ?? 'ALL',
  );
  const [q, setQ] = useState('');
  const [departmentId, setDepartmentId] = useState<number | undefined>(undefined);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(50);
  const debouncedQ = useDebounced(q.trim(), 300);

  const rosterQuery = useQuery({
    queryKey: ['canteen', 'employees', category, debouncedQ, departmentId, page, size],
    queryFn: ({ signal }) =>
      canteenEmployeesApi.list(
        {
          category: category === 'ALL' ? undefined : category,
          q: debouncedQ || undefined,
          departmentId,
          page: page - 1,
          size,
        },
        signal,
      ),
    placeholderData: (prev) => prev,
    retry: (count, err) => !isNotFound(err) && count < 2,
  });

  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
  });

  const pending = isNotFound(rosterQuery.error);
  const rows = rosterQuery.data?.content ?? [];
  const totalElements = rosterQuery.data?.totalElements ?? 0;

  const hasActiveFilters = Boolean(q || departmentId || category !== 'ALL');
  const clearAll = () => {
    setQ('');
    setDepartmentId(undefined);
    setCategory('ALL');
    setPage(1);
  };

  const columns: TableProps<CanteenEmployeeDto>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      width: 260,
      render: (_, e) => (
        <PersonCell
          size="compact"
          person={{
            fullName: e.fullName,
            employeeCode: e.employeeCode,
            profilePictureUrl: e.profilePictureUrl,
            designation: e.designationName,
          }}
        />
      ),
    },
    {
      title: 'Category',
      key: 'category',
      width: 120,
      render: (_, e) => (
        <StatusPill tone={CANTEEN_CATEGORY_TONE[e.category]} size="sm">
          {CANTEEN_CATEGORY_LABEL[e.category]}
        </StatusPill>
      ),
    },
    {
      title: 'Department',
      key: 'department',
      width: 180,
      render: (_, e) => <Text style={{ fontSize: 12 }}>{e.departmentName ?? '—'}</Text>,
    },
    {
      title: 'Section',
      key: 'section',
      width: 160,
      render: (_, e) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {e.sectionName ?? '—'}
        </Text>
      ),
    },
    {
      title: 'Grade',
      key: 'grade',
      width: 120,
      render: (_, e) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {e.gradeName ?? '—'}
        </Text>
      ),
    },
    {
      title: 'Canteen card',
      key: 'cardNo',
      width: 150,
      render: (_, e) => (
        <Text style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {e.cardNo ?? '—'}
        </Text>
      ),
    },
    {
      title: 'Active',
      key: 'active',
      width: 90,
      render: (_, e) => (
        <StatusPill tone={e.active ? 'success' : 'neutral'} size="sm">
          {e.active ? 'Yes' : 'No'}
        </StatusPill>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <CanteenTabs />
      <PageHeader
        eyebrow="Canteen"
        title="Employees"
        description={
          pending
            ? 'Labour and Boufet staff entitled to canteen meals.'
            : `${totalElements.toLocaleString('en-PK')} on the canteen roster`
        }
        level={4}
      />

      <FilterBar
        search={{ value: q, onChange: setQ, placeholder: 'Search name or code…' }}
        filters={
          <>
            <Segmented
              value={category}
              onChange={(v) => {
                setCategory(v as CanteenEmployeeCategory | 'ALL');
                setPage(1);
              }}
              options={[
                { value: 'ALL', label: 'All' },
                { value: 'LABOUR', label: CANTEEN_CATEGORY_LABEL.LABOUR },
                { value: 'BOUFET', label: CANTEEN_CATEGORY_LABEL.BOUFET },
              ]}
            />
            <Select
              allowClear
              placeholder="Department"
              value={departmentId}
              onChange={(v) => {
                setDepartmentId(v ?? undefined);
                setPage(1);
              }}
              options={(departmentsQuery.data ?? []).map((d) => ({
                value: d.id,
                label: d.name,
              }))}
              loading={departmentsQuery.isLoading}
              style={{ minWidth: 180 }}
              showSearch
              optionFilterProp="label"
            />
          </>
        }
        onReset={hasActiveFilters ? clearAll : undefined}
      />

      {pending ? (
        <PendingBackendNotice
          what="canteen roster"
          endpoint="GET /canteen/employees"
        >
          The Labour / Boufet split has no equivalent in the employee-service
          grade catalog, so there is nothing safe to fall back to — the filters
          above are live and will drive the list as soon as the route answers.
        </PendingBackendNotice>
      ) : (
        <RequestState
          loading={rosterQuery.isLoading}
          variant="table"
          error={rosterQuery.isError ? rosterQuery.error : undefined}
          empty={!rosterQuery.isLoading && !rosterQuery.isError && rows.length === 0}
          emptyIcon={Users}
          filterActive={hasActiveFilters}
          onClearFilters={clearAll}
          emptyTitle="Nobody on the canteen roster"
          emptyDescription="No Labour or Boufet employee matches this view."
          errorTitle={forbiddenOr(
            rosterQuery.error,
            "You don't have permission to view the canteen roster.",
            "Couldn't load the canteen roster",
          )}
          onRetry={() => rosterQuery.refetch()}
        >
          <Table<CanteenEmployeeDto>
            rowKey="id"
            loading={rosterQuery.isFetching}
            dataSource={rows}
            columns={columns}
            size="middle"
            scroll={{ x: 'max-content' }}
            pagination={{
              current: page,
              pageSize: size,
              total: totalElements,
              showSizeChanger: true,
              pageSizeOptions: ['25', '50', '100', '200'],
              showTotal: (n, [start, end]) =>
                `${start}–${end} of ${n.toLocaleString('en-PK')}`,
              onChange: (next, nextSize) => {
                setPage(next);
                setSize(nextSize);
              },
            }}
          />
        </RequestState>
      )}
    </div>
  );
}

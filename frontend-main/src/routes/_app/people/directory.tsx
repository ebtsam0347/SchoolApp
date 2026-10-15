// /people/directory — primary workforce directory.
//
// Single composite read against /employees/directory (denormalized
// labels + signed avatar URL + directReportsCount). Filters serialize to
// URL search params so refresh / share preserves the view. Catalog
// dropdowns hit /departments, /sections, /designations, /grades once and
// cache via React Query.

import { useEffect, useState } from 'react';
import { downloadCsv, forbiddenOr, getErrorMessage, useDebounced, useTableColumns } from '@/lib';
import {
  createFileRoute,
  useNavigate,
  useSearch,
} from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  Pagination,
  Segmented,
  Space,
  Table,
  Tooltip,
  theme as antdTheme,
} from 'antd';
import {
  ArrowDownToLine,
  LayoutGrid,
  RefreshCcw,
  Rows3,
  UserPlus,
} from 'lucide-react';

import {
  ColumnsToggle,
  PageHeader,
  RequestState,
  useMobileRowExpandable,
} from '@/components';
import { Can } from '@/auth';
import {
  catalogsApi,
  peopleBff,
  EMPLOYMENT_STATUS_LABEL,
  EMPLOYMENT_TYPE_LABEL,
  type DirectoryParams,
  type EmployeeDirectoryRow,
} from '@/people';
import {
  DirectoryCardGrid,
  DirectoryFilters,
  FilterChips,
  buildDirectoryColumns,
  DIRECTORY_DEFAULT_HIDDEN,
  isStatus,
  isType,
  isView,
  numOrUndef,
  type DirectorySearch,
  type DirectoryView,
} from '@/people/directory';

const { useToken } = antdTheme;

/** Employee code ascending — the order HR reads the roster in. */
const DEFAULT_DIRECTORY_SORT = 'employeeCode,asc';

export const Route = createFileRoute('/_app/people/directory')({
  validateSearch: (search): DirectorySearch => ({
    q: typeof search.q === 'string' ? search.q : undefined,
    status: isStatus(search.status) ? search.status : undefined,
    employmentType: isType(search.employmentType)
      ? search.employmentType
      : undefined,
    departmentId: numOrUndef(search.departmentId),
    sectionId: numOrUndef(search.sectionId),
    designationId: numOrUndef(search.designationId),
    gradeId: numOrUndef(search.gradeId),
    hasEss: typeof search.hasEss === 'boolean' ? search.hasEss : undefined,
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
    sort: typeof search.sort === 'string' ? search.sort : undefined,
    view: isView(search.view) ? search.view : undefined,
  }),
  component: DirectoryPage,
});

function DirectoryPage() {
  const { token } = useToken();
  const { message } = AntdApp.useApp();
  const navigate = useNavigate({ from: '/people/directory' });
  const search = useSearch({ from: '/_app/people/directory' });

  const [searchInput, setSearchInput] = useState(search.q ?? '');
  const debouncedSearch = useDebounced(searchInput.trim(), 250);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    const trimmed = debouncedSearch || undefined;
    if (trimmed === search.q) return;
    navigate({
      search: (prev) => ({ ...prev, q: trimmed, page: 1 }),
      replace: true,
    });
  }, [debouncedSearch, search.q, navigate]);

  const view: DirectoryView = search.view ?? 'table';
  const page = search.page ?? 1;
  // Directory lands in card order. The upstream composite already defaults to
  // employeeCode-asc, but we send it explicitly so the ordering is visible in
  // the URL, survives a server-side default change, and can be reflected in
  // the column header arrow. Cleared back to this whenever the user un-sorts.
  const sort = search.sort ?? DEFAULT_DIRECTORY_SORT;
  // Single page-size default keeps pagination state stable across the
  // table ↔ cards toggle. Was previously 24 in cards / 20 in table.
  const size = search.size ?? 20;

  const directoryQuery = useQuery({
    queryKey: ['people', 'directory', search],
    queryFn: ({ signal }) =>
      peopleBff.directory(
        {
          q: search.q,
          status: search.status,
          employmentType: search.employmentType,
          departmentId: search.departmentId,
          sectionId: search.sectionId,
          designationId: search.designationId,
          gradeId: search.gradeId,
          hasEss: search.hasEss,
          page: page - 1,
          size,
          sort,
        },
        signal,
      ),
  });

  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
  });
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections', search.departmentId],
    queryFn: ({ signal }) => catalogsApi.sections(search.departmentId, signal),
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

  const totalElements = directoryQuery.data?.totalElements ?? 0;
  const rows = directoryQuery.data?.content ?? [];

  const hasActiveFilters = Boolean(
    search.q ||
      search.status ||
      search.employmentType ||
      search.departmentId ||
      search.sectionId ||
      search.designationId ||
      search.gradeId ||
      search.hasEss !== undefined,
  );

  const updateFilter = (patch: Partial<DirectorySearch>) =>
    navigate({
      search: (prev) => ({ ...prev, ...patch, page: 1 }),
      replace: true,
    });

  const clearFilter = (key: keyof DirectorySearch) =>
    updateFilter({ [key]: undefined });

  const clearAll = () => {
    setSearchInput('');
    navigate({ search: () => ({}), replace: true });
  };

  // Exports every row matching the current filters (not just the loaded
  // page) — pages through the same composite the table uses at a fixed
  // chunk size, since the server may cap `size` lower than the full count.
  const handleExport = async () => {
    setExporting(true);
    try {
      const baseParams: DirectoryParams = {
        q: search.q,
        status: search.status,
        employmentType: search.employmentType,
        departmentId: search.departmentId,
        sectionId: search.sectionId,
        designationId: search.designationId,
        gradeId: search.gradeId,
        hasEss: search.hasEss,
        sort,
      };
      const chunkSize = 200;
      const first = await peopleBff.directory({ ...baseParams, page: 0, size: chunkSize });
      const total = first.totalElements ?? 0;
      const allRows: EmployeeDirectoryRow[] = [...(first.content ?? [])];
      const pageCount = Math.ceil(total / chunkSize);
      for (let p = 1; p < pageCount; p++) {
        const res = await peopleBff.directory({ ...baseParams, page: p, size: chunkSize });
        allRows.push(...(res.content ?? []));
      }
      exportDirectoryCsv(allRows);
      message.success(`Exported ${allRows.length.toLocaleString('en-PK')} employees`);
    } catch (err) {
      message.error(getErrorMessage(err));
    } finally {
      setExporting(false);
    }
  };

  const allColumns = buildDirectoryColumns(sort);
  const {
    columns,
    options: columnOptions,
    toggle: toggleColumn,
    reset: resetColumns,
  } = useTableColumns('people.directory', allColumns, {
    defaultHidden: DIRECTORY_DEFAULT_HIDDEN,
  });
  // On mobile, surface hidden columns through an expand row so users see
  // the full record without horizontal scrolling.
  const mobileExpandable = useMobileRowExpandable(allColumns, {
    skipKeys: ['fullName'],
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PageHeader
        eyebrow="People"
        title="Directory"
        description={`${totalElements.toLocaleString('en-PK')} employees`}
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => directoryQuery.refetch()}
                loading={directoryQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button
              icon={<ArrowDownToLine size={16} strokeWidth={1.75} />}
              onClick={handleExport}
              loading={exporting}
            >
              Export
            </Button>
            <Can I="write" a="employee">
              <Button
                type="primary"
                onClick={() => navigate({ to: '/people/onboard' })}
                icon={<UserPlus size={16} strokeWidth={1.75} />}
              >
                Onboard employee
              </Button>
            </Can>
          </Space>
        }
      />

      <div
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 5,
          background: token.colorBgContainer,
          borderRadius: token.borderRadiusLG,
          border: `1px solid ${token.colorBorderSecondary}`,
          padding: 12,
          display: 'flex',
          flexDirection: 'column',
          gap: 10,
        }}
      >
        <DirectoryFilters
          searchInput={searchInput}
          onSearchInputChange={setSearchInput}
          search={search}
          onUpdate={updateFilter}
          departments={departmentsQuery.data}
          sections={sectionsQuery.data}
          designations={designationsQuery.data}
          grades={gradesQuery.data}
          loading={{
            departments: departmentsQuery.isLoading,
            sections: sectionsQuery.isLoading,
            designations: designationsQuery.isLoading,
            grades: gradesQuery.isLoading,
          }}
        />
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 10,
            flexWrap: 'wrap',
          }}
        >
          <FilterChips
            search={search}
            departments={departmentsQuery.data}
            sections={sectionsQuery.data}
            designations={designationsQuery.data}
            grades={gradesQuery.data}
            onClear={clearFilter}
            onClearAll={clearAll}
          />
          <Space size={8} style={{ marginLeft: 'auto' }} wrap>
            {view === 'table' ? (
              <ColumnsToggle
                size="small"
                options={columnOptions}
                onToggle={toggleColumn}
                onReset={resetColumns}
              />
            ) : null}
            <Segmented<DirectoryView>
              value={view}
              onChange={(v) =>
                navigate({
                  search: (prev) => ({
                    ...prev,
                    view: v === 'table' ? undefined : v,
                    size: undefined,
                  }),
                  replace: true,
                })
              }
              options={[
                {
                  value: 'table',
                  icon: <Rows3 size={14} strokeWidth={1.75} />,
                  label: 'Table',
                },
                {
                  value: 'card',
                  icon: <LayoutGrid size={14} strokeWidth={1.75} />,
                  label: 'Cards',
                },
              ]}
              size="small"
            />
          </Space>
        </div>
      </div>

      <RequestState
        loading={directoryQuery.isLoading}
        variant="table"
        error={directoryQuery.isError ? directoryQuery.error : undefined}
        errorTitle={forbiddenOr(
          directoryQuery.error,
          "You don't have permission to view the directory.",
          "Couldn't load directory",
        )}
        onRetry={() => directoryQuery.refetch()}
        empty={!directoryQuery.isLoading && rows.length === 0}
        filterActive={hasActiveFilters}
        onClearFilters={clearAll}
      >
        {view === 'card' ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <DirectoryCardGrid
              rows={rows}
              loading={directoryQuery.isLoading || directoryQuery.isFetching}
            />
            {totalElements > 0 ? (
              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Pagination
                  current={page}
                  pageSize={size}
                  total={totalElements}
                  showSizeChanger
                  pageSizeOptions={['12', '24', '48', '96']}
                  showTotal={(n, [from, to]) =>
                    `${from}-${to} of ${n.toLocaleString('en-PK')}`
                  }
                  onChange={(next, nextSize) =>
                    navigate({
                      search: (prev) => ({
                        ...prev,
                        page: next,
                        size: nextSize,
                      }),
                      replace: true,
                    })
                  }
                />
              </div>
            ) : null}
          </div>
        ) : (
          <Table<EmployeeDirectoryRow>
            rowKey={(row) => String(row.employee?.id ?? Math.random())}
            loading={directoryQuery.isLoading || directoryQuery.isFetching}
            dataSource={rows}
            columns={columns}
            expandable={mobileExpandable}
            size="middle"
            scroll={{ x: 'max-content' }}
            pagination={{
              current: page,
              pageSize: size,
              total: totalElements,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50', '100'],
              showTotal: (n, [from, to]) =>
                `${from}-${to} of ${n.toLocaleString('en-PK')}`,
              onChange: (next, nextSize) =>
                navigate({
                  search: (prev) => ({ ...prev, page: next, size: nextSize }),
                  replace: true,
                }),
            }}
            onChange={(_pagination, _filters, sorter, extra) => {
              if (extra.action !== 'sort') return;
              const s = Array.isArray(sorter) ? sorter[0] : sorter;
              // columnKey, not field: the backend sorts the Employee entity, so it
              // wants `fullName`. `field` is the dataIndex (`['employee','fullName']`)
              // — the shape of the response row — and joins to `employee.fullName`,
              // which 400s. Columns without a dataIndex (Joined) have no field at all.
              //
              // Undefined on the un-sort click, which drops the param and falls
              // back to DEFAULT_DIRECTORY_SORT rather than to whatever the
              // server happens to default to.
              const nextSort =
                s?.order && s.columnKey
                  ? `${String(s.columnKey)},${s.order === 'ascend' ? 'asc' : 'desc'}`
                  : undefined;
              navigate({
                search: (prev) => ({ ...prev, sort: nextSort, page: 1 }),
                replace: true,
              });
            }}
            onRow={(row) => ({
              onClick: (e) => {
                const target = e.target as HTMLElement;
                if (target.closest('a, button')) return;
                if (row.employee?.id) {
                  navigate({
                    to: '/people/$id',
                    params: { id: String(row.employee.id) },
                  });
                }
              },
              style: { cursor: 'pointer' },
            })}
          />
        )}
      </RequestState>
    </div>
  );
}

function exportDirectoryCsv(rows: EmployeeDirectoryRow[]) {
  const header = [
    'Card No',
    'Full name',
    'Designation',
    'Department',
    'Section',
    'Grade',
    'Manager',
    'Direct reports',
    'Status',
    'Employment type',
    'Joined',
    'Confirmed',
    'City',
    'Work email',
    'Phone',
    'ESS',
  ];
  // Manager is "Name · Card No" in one cell — same convention PersonCell
  // and the approval-chain drawers use everywhere else in the app, rather
  // than a bare, name-less card number.
  const managerCell = (row: EmployeeDirectoryRow) => {
    const m = row.resolved?.manager;
    if (!m?.fullName) return '';
    return m.employeeCode ? `${m.fullName} · ${m.employeeCode}` : m.fullName;
  };
  const lines = rows.map((row) => {
    const e = row.employee;
    const r = row.resolved;
    return [
      e?.employeeCode ?? '',
      e?.fullName ?? '',
      r?.designation?.title ?? '',
      r?.department?.name ?? '',
      r?.section?.name ?? '',
      r?.grade?.name ?? '',
      managerCell(row),
      e?.directReportsCount ?? 0,
      e?.employmentStatus ? EMPLOYMENT_STATUS_LABEL[e.employmentStatus] : '',
      e?.employmentType ? EMPLOYMENT_TYPE_LABEL[e.employmentType] : '',
      e?.joiningDate ?? '',
      e?.confirmationDate ?? '',
      r?.city?.name ?? '',
      e?.workEmail ?? '',
      e?.phone ?? '',
      e?.hasEss ? 'Yes' : 'No',
    ];
  });
  downloadCsv(`employee-directory-${new Date().toISOString().slice(0, 10)}.csv`, header, lines);
}

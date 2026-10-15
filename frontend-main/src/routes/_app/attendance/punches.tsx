// /attendance/punches — paged raw-punches browser.
//
// Same gating as /records (HR + admin + attendance-operator). Used during
// daily reconciliation: a paper slip lands at the desk, the operator
// searches by employee + day to verify what's already in the system before
// adding an OPERATOR punch.
//
// Filters mirror the records page where they make sense (date / range,
// employee, dept/section) plus punch-only knobs: source + deviceId.

import { useContext, useEffect, useState } from 'react';
import {
  createFileRoute,
  useNavigate,
  useSearch,
} from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  DatePicker,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';
import { Fingerprint, Pencil, Plus, Trash2 } from 'lucide-react';

import { FilterBar, PageHeader, RequestState } from '@/components';
import { AbilityContext } from '@/auth';
import { EmployeePicker } from '@/people';
import { catalogsApi } from '@/people';
import { forbiddenOr, useDebounced } from '@/lib';
import {
  PUNCH_SOURCE_LABEL,
  attendanceBff,
  formatDateShort,
  formatDateTime,
  type AttendancePunchesRow,
  type PunchDto,
  type PunchSource,
} from '@/attendance';
import {
  AttendanceTabs,
  BulkOperatorPunchDrawer,
  EmployeeSummaryCell,
  PunchAmendModal,
  PunchDeleteModal,
  PunchRecordDrawer,
  collapseDateRange,
  isPunchSource,
  numOrUndef,
  strOrUndef,
} from '@/attendance/admin';

const { Text } = Typography;
const { RangePicker } = DatePicker;

type PunchesSearch = {
  from?: string;
  to?: string;
  employeeId?: number;
  employeeCode?: string;
  departmentId?: number;
  sectionId?: number;
  source?: PunchSource;
  deviceId?: number;
  page?: number;
  size?: number;
  sort?: string;
};

export const Route = createFileRoute('/_app/attendance/punches')({
  validateSearch: (search): PunchesSearch => ({
    from: strOrUndef(search.from),
    to: strOrUndef(search.to),
    employeeId: numOrUndef(search.employeeId),
    employeeCode: strOrUndef(search.employeeCode),
    departmentId: numOrUndef(search.departmentId),
    sectionId: numOrUndef(search.sectionId),
    source: isPunchSource(search.source) ? search.source : undefined,
    deviceId: numOrUndef(search.deviceId),
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
    sort: strOrUndef(search.sort),
  }),
  component: PunchesPage,
});

const SOURCE_OPTIONS: { value: PunchSource; label: string }[] = (
  Object.entries(PUNCH_SOURCE_LABEL) as [PunchSource, string][]
).map(([value, label]) => ({ value, label }));

const SOURCE_TAG_COLOR: Record<PunchSource, string> = {
  BIOMETRIC: 'green',
  WEB: 'blue',
  OPERATOR: 'orange',
  REGULARIZED: 'cyan',
  SYSTEM: 'default',
};

function PunchesPage() {
  const navigate = useNavigate({ from: '/attendance/punches' });
  const search = useSearch({ from: '/_app/attendance/punches' });
  const ability = useContext(AbilityContext);
  // Backend collapsed punch / amend / shift-override into the single
  // `attendance.operator` grant → verb `operator` on subject `attendance`.
  const canOperate = ability.can('operator', 'attendance');
  const canAmend = canOperate;

  const [amendTarget, setAmendTarget] = useState<PunchDto | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<PunchDto | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [selectedRow, setSelectedRow] = useState<AttendancePunchesRow | null>(null);

  const [codeInput, setCodeInput] = useState(search.employeeCode ?? '');
  const debouncedCode = useDebounced(codeInput.trim(), 300);

  useEffect(() => {
    const next = debouncedCode || undefined;
    if (next === search.employeeCode) return;
    navigate({
      search: (prev) => ({ ...prev, employeeCode: next, page: 1 }),
      replace: true,
    });
  }, [debouncedCode, search.employeeCode, navigate]);

  const page = search.page ?? 1;
  const size = search.size ?? 50;

  const { date, from: serverFrom, to: serverTo } = collapseDateRange(
    search.from,
    search.to,
  );

  const punchesQuery = useQuery({
    queryKey: ['attendance', 'punches', search],
    queryFn: ({ signal }) =>
      attendanceBff.punches(
        {
          date,
          from: serverFrom,
          to: serverTo,
          employeeId: search.employeeId,
          employeeCode: search.employeeCode,
          departmentId: search.departmentId,
          sectionId: search.sectionId,
          source: search.source,
          deviceId: search.deviceId,
          page: page - 1,
          size,
          sort: search.sort,
        },
        signal,
      ),
    placeholderData: (prev) => prev,
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

  const totalElements = punchesQuery.data?.totalElements ?? 0;
  const rows = punchesQuery.data?.content ?? [];

  const updateFilter = (patch: Partial<PunchesSearch>) =>
    navigate({
      search: (prev) => ({ ...prev, ...patch, page: 1 }),
      replace: true,
    });

  const clearAll = () => {
    setCodeInput('');
    navigate({ search: () => ({}), replace: true });
  };

  const dateRangeValue =
    search.from || search.to
      ? ([
          search.from ? dayjs(search.from) : null,
          search.to ? dayjs(search.to) : null,
        ] as [dayjs.Dayjs | null, dayjs.Dayjs | null])
      : null;

  const hasActiveFilters =
    search.from ||
    search.to ||
    search.employeeId ||
    search.employeeCode ||
    search.departmentId ||
    search.sectionId ||
    search.source ||
    search.deviceId !== undefined;

  const scopeLabel = !hasActiveFilters
    ? `Showing today · ${formatDateShort(dayjs().format('YYYY-MM-DD'))}`
    : `${totalElements.toLocaleString('en-PK')} punches`;

  const columns: TableProps<AttendancePunchesRow>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      width: 260,
      render: (_, row) => (
        <EmployeeSummaryCell
          summary={row.employee}
          fallbackEmployeeId={row.punch.employeeId}
          linkToProfile={false}
        />
      ),
    },
    {
      title: 'Punched at',
      key: 'punchedAt',
      width: 200,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {formatDateTime(row.punch.punchedAt)}
        </span>
      ),
    },
    {
      title: 'Work date',
      key: 'workDate',
      width: 130,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {formatDateShort(row.punch.workDate)}
        </span>
      ),
    },
    {
      title: 'Source',
      key: 'source',
      width: 140,
      render: (_, row) => (
        <Tag color={SOURCE_TAG_COLOR[row.punch.source]} style={{ margin: 0 }}>
          {PUNCH_SOURCE_LABEL[row.punch.source]}
        </Tag>
      ),
    },
    {
      title: 'Device',
      key: 'deviceId',
      width: 120,
      render: (_, row) =>
        row.punch.deviceId ? (
          <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
            {row.punch.deviceId}
          </span>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: 'Reason',
      key: 'reason',
      render: (_, row) =>
        row.punch.reason ? (
          <span style={{ fontSize: 12 }}>{row.punch.reason}</span>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    ...(canAmend
      ? ([
          {
            title: '',
            key: 'actions',
            width: 96,
            fixed: 'right',
            render: (_, row) => (
              <Space size={4}>
                <Tooltip title="Amend instant + reason">
                  <Button
                    size="small"
                    icon={<Pencil size={14} strokeWidth={1.75} />}
                    onClick={(e) => {
                      e.stopPropagation();
                      setAmendTarget(row.punch);
                    }}
                  />
                </Tooltip>
                <Tooltip title="Delete with reason">
                  <Button
                    size="small"
                    danger
                    icon={<Trash2 size={14} strokeWidth={1.75} />}
                    onClick={(e) => {
                      e.stopPropagation();
                      setDeleteTarget(row.punch);
                    }}
                  />
                </Tooltip>
              </Space>
            ),
          },
        ] satisfies TableProps<AttendancePunchesRow>['columns'])
      : []),
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <AttendanceTabs />
      <PageHeader
        eyebrow="Attendance"
        title="Punches"
        description={scopeLabel}
        level={4}
        actions={
          canOperate ? (
            <Button
              type="primary"
              icon={<Plus size={16} strokeWidth={1.75} />}
              onClick={() => setBulkOpen(true)}
            >
              Bulk add punches
            </Button>
          ) : undefined
        }
      />

      <FilterBar
        filters={
          <>
            <RangePicker
              value={dateRangeValue}
              onChange={(range) =>
                updateFilter({
                  from: range?.[0]?.format('YYYY-MM-DD'),
                  to: range?.[1]?.format('YYYY-MM-DD'),
                })
              }
              format="DD MMM YYYY"
              placeholder={['From', 'To']}
              allowClear
              presets={[
                { label: 'Today', value: [dayjs(), dayjs()] },
                {
                  label: 'Yesterday',
                  value: [dayjs().subtract(1, 'day'), dayjs().subtract(1, 'day')],
                },
                { label: 'Last 7 days', value: [dayjs().subtract(6, 'day'), dayjs()] },
                {
                  label: 'This month',
                  value: [dayjs().startOf('month'), dayjs()],
                },
              ]}
            />
            <EmployeePicker
              value={search.employeeId}
              onChange={(v) => updateFilter({ employeeId: v })}
              style={{ minWidth: 220 }}
              placeholder="Search by name or code"
            />
            <Input
              value={codeInput}
              onChange={(e) => setCodeInput(e.target.value)}
              placeholder="…or employee code"
              style={{ width: 180 }}
              allowClear
            />
            <Select
              allowClear
              placeholder="Department"
              value={search.departmentId}
              onChange={(v) =>
                updateFilter({
                  departmentId: v ?? undefined,
                  sectionId: undefined,
                })
              }
              options={(departmentsQuery.data ?? []).map((d) => ({
                value: d.id,
                label: d.name,
              }))}
              loading={departmentsQuery.isLoading}
              style={{ minWidth: 180 }}
              showSearch
              optionFilterProp="label"
            />
            <Select
              allowClear
              placeholder="Section"
              value={search.sectionId}
              onChange={(v) => updateFilter({ sectionId: v ?? undefined })}
              options={(sectionsQuery.data ?? []).map((s) => ({
                value: s.id,
                label: s.name,
              }))}
              loading={sectionsQuery.isLoading}
              disabled={!search.departmentId && (sectionsQuery.data?.length ?? 0) === 0}
              style={{ minWidth: 180 }}
              showSearch
              optionFilterProp="label"
            />
            <Select
              allowClear
              placeholder="Source"
              value={search.source}
              onChange={(v) => updateFilter({ source: v ?? undefined })}
              options={SOURCE_OPTIONS}
              style={{ minWidth: 160 }}
            />
            <InputNumber
              value={search.deviceId}
              onChange={(v) =>
                updateFilter({ deviceId: typeof v === 'number' ? v : undefined })
              }
              placeholder="Device #"
              min={1}
              style={{ width: 120 }}
            />
          </>
        }
        onReset={hasActiveFilters ? clearAll : undefined}
      />

      <RequestState
        loading={false}
        error={punchesQuery.isError ? punchesQuery.error : undefined}
        empty={!punchesQuery.isLoading && rows.length === 0}
        emptyIcon={Fingerprint}
        emptyTitle={hasActiveFilters ? 'No matching punches' : 'No punches today yet'}
        emptyDescription={
          hasActiveFilters
            ? 'Widen the date range or clear filters.'
            : 'Punches appear as employees scan in. Pick a date range to pull historical days.'
        }
        errorTitle={forbiddenOr(
          punchesQuery.error,
          "You don't have permission to view org-wide punches.",
          "Couldn't load punches",
        )}
      >
        {!hasActiveFilters ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            title="Showing today by default. Pick a date range to see other days."
          />
        ) : null}
        <Table<AttendancePunchesRow>
          rowKey={(row) => row.punch.id}
          loading={punchesQuery.isLoading || punchesQuery.isFetching}
          dataSource={rows}
          columns={columns}
          size="middle"
          sticky
          scroll={{ x: 'max-content' }}
          onRow={(row) => ({
            onClick: () => setSelectedRow(row),
            style: { cursor: 'pointer' },
          })}
          pagination={{
            current: page,
            pageSize: size,
            total: totalElements,
            showSizeChanger: true,
            pageSizeOptions: ['25', '50', '100', '200'],
            showTotal: (n, [start, end]) =>
              `${start}–${end} of ${n.toLocaleString('en-PK')}`,
            onChange: (next, nextSize) =>
              navigate({
                search: (prev) => ({ ...prev, page: next, size: nextSize }),
                replace: true,
              }),
          }}
          onChange={(_pagination, _filters, sorter, extra) => {
            // Table.onChange fires for pagination too; ignore those calls so
            // we don't reset page back to 1 right after pagination.onChange
            // navigated us forward.
            if (extra.action !== 'sort') return;
            const s = Array.isArray(sorter) ? sorter[0] : sorter;
            const next: Partial<PunchesSearch> = {};
            if (s?.order && s.field) {
              const field = Array.isArray(s.field) ? s.field.join('.') : s.field;
              next.sort = `${field},${s.order === 'ascend' ? 'asc' : 'desc'}`;
            } else if (s?.order === undefined) {
              next.sort = undefined;
            }
            updateFilter(next);
          }}
        />
      </RequestState>

      <PunchAmendModal punch={amendTarget} onClose={() => setAmendTarget(null)} />
      <PunchDeleteModal punch={deleteTarget} onClose={() => setDeleteTarget(null)} />
      <BulkOperatorPunchDrawer open={bulkOpen} onClose={() => setBulkOpen(false)} />
      {selectedRow ? (
        <PunchRecordDrawer
          row={selectedRow}
          date={selectedRow.punch.workDate}
          onClose={() => setSelectedRow(null)}
        />
      ) : null}
    </div>
  );
}

// /attendance/records — paged daily attendance rows.
//
// HR + admin + attendance-operator. Filters: date or range, employee
// (picker or code), department, section, status, lateOnly. Server defaults
// to today when no date filter lands; we surface that in the UI so the
// operator knows why an empty state is empty.
//
// URL-driven so tabs / refresh / share preserve the view. Page size,
// sort follow Spring conventions.

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
  Segmented,
  Select,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';
import { Clock, Hourglass, Plus } from 'lucide-react';

import {
  FilterBar,
  PageHeader,
  RequestState,
  StatusPill,
} from '@/components';
import { AbilityContext } from '@/auth';
import { EmployeePicker } from '@/people';
import { catalogsApi } from '@/people';
import { forbiddenOr, formatDateTimeNumeric, useDebounced } from '@/lib';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  attendanceBff,
  formatDateShort,
  formatMinutes,
  type AttendanceRecordsRow,
  type AttendanceStatus,
} from '@/attendance';
import {
  AttendanceRecordDrawer,
  AttendanceTabs,
  EmployeeSummaryCell,
  PunchAddForRecordModal,
  collapseDateRange,
  isAttendanceStatus,
  numOrUndef,
  strOrUndef,
} from '@/attendance/admin';

const { Text } = Typography;
const { RangePicker } = DatePicker;

type RecordsSearch = {
  from?: string;
  to?: string;
  employeeId?: number;
  employeeCode?: string;
  departmentId?: number;
  sectionId?: number;
  status?: AttendanceStatus;
  lateOnly?: boolean;
  page?: number;
  size?: number;
  sort?: string;
};

export const Route = createFileRoute('/_app/attendance/records')({
  validateSearch: (search): RecordsSearch => ({
    from: strOrUndef(search.from),
    to: strOrUndef(search.to),
    employeeId: numOrUndef(search.employeeId),
    employeeCode: strOrUndef(search.employeeCode),
    departmentId: numOrUndef(search.departmentId),
    sectionId: numOrUndef(search.sectionId),
    status: isAttendanceStatus(search.status) ? search.status : undefined,
    lateOnly: typeof search.lateOnly === 'boolean' ? search.lateOnly : undefined,
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
    sort: strOrUndef(search.sort),
  }),
  component: RecordsPage,
});

const STATUS_OPTIONS: { value: AttendanceStatus; label: string }[] = (
  Object.entries(ATTENDANCE_STATUS_LABEL) as [AttendanceStatus, string][]
).map(([value, label]) => ({ value, label }));

function RecordsPage() {
  const navigate = useNavigate({ from: '/attendance/records' });
  const search = useSearch({ from: '/_app/attendance/records' });
  const ability = useContext(AbilityContext);
  const canOperate = ability.can('operator', 'attendance');

  const [addPunchTarget, setAddPunchTarget] =
    useState<AttendanceRecordsRow | null>(null);
  const [selectedRow, setSelectedRow] = useState<AttendanceRecordsRow | null>(null);

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

  const recordsQuery = useQuery({
    queryKey: ['attendance', 'records', search],
    queryFn: ({ signal }) =>
      attendanceBff.records(
        {
          date,
          from: serverFrom,
          to: serverTo,
          employeeId: search.employeeId,
          employeeCode: search.employeeCode,
          departmentId: search.departmentId,
          sectionId: search.sectionId,
          status: search.status,
          lateOnly: search.lateOnly,
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

  const totalElements = recordsQuery.data?.totalElements ?? 0;
  const rows = recordsQuery.data?.content ?? [];

  const updateFilter = (patch: Partial<RecordsSearch>) =>
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
    search.status ||
    search.lateOnly;

  const scopeLabel = !hasActiveFilters
    ? `Showing today · ${formatDateShort(dayjs().format('YYYY-MM-DD'))}`
    : `${totalElements.toLocaleString('en-PK')} records`;

  const columns: TableProps<AttendanceRecordsRow>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      width: 220,
      render: (_, row) => (
        <EmployeeSummaryCell
          summary={row.employee}
          fallbackEmployeeId={row.record.employeeId}
          linkToProfile={false}
        />
      ),
    },
    {
      title: 'Shift Start',
      key: 'shiftStartDateTime',
      minWidth: 90,
      render: (_, row) => <PunchCell at={row.record.shiftStartDateTime} />,
    },
    {
      title: 'Shift End',
      key: 'shiftEndDateTime',
      minWidth: 90,
      render: (_, row) => <PunchCell at={row.record.shiftEndDateTime} />,
    },
    {
      title: 'Break Start',
      key: 'shiftBreakStartTime',
      minWidth: 70,
      render: (_, row) => <TimeCell value={row.record.shiftBreakStartTime} />,
    },
    {
      title: 'Break End',
      key: 'shiftBreakEndTime',
      minWidth: 70,
      render: (_, row) => <TimeCell value={row.record.shiftBreakEndTime} />,
    },
    {
      title: 'Attendance Date',
      key: 'workDate',
      minWidth: 100,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {formatDateShort(row.record.workDate)}
        </span>
      ),
    },
    {
      title: 'In Time',
      key: 'checkIn',
      minWidth: 90,
      render: (_, row) => <PunchCell at={row.record.checkIn} />,
    },
    {
      title: 'Out Time',
      key: 'checkOut',
      minWidth: 90,
      render: (_, row) => <PunchCell at={row.record.checkOut} />,
    },
    {
      title: 'Status',
      key: 'status',
      width: 85,
      render: (_, row) => (
        <StatusPill tone={ATTENDANCE_STATUS_TONE[row.record.status]} size="sm">
          {ATTENDANCE_STATUS_LABEL[row.record.status]}
        </StatusPill>
      ),
    },
    {
      title: (
        <Tooltip title="Minutes past shift start (after grace).">
          <span>Late</span>
        </Tooltip>
      ),
      key: 'lateMinutes',
      width: 55,
      align: 'right',
      render: (_, row) =>
        row.record.lateMinutes > 0 ? (
          <Tag color="orange" style={{ margin: 0 }}>
            {formatMinutes(row.record.lateMinutes)}
          </Tag>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: (
        <Tooltip title="Minutes worked between check-in and check-out, minus break.">
          <span>Worked</span>
        </Tooltip>
      ),
      key: 'workedMinutes',
      width: 75,
      align: 'right',
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {formatMinutes(row.record.workedMinutes)}
        </span>
      ),
    },
    {
      title: (
        <Tooltip title="Approved overtime (normal + extra + special).">
          <span>OT</span>
        </Tooltip>
      ),
      key: 'overtime',
      width: 65,
      align: 'right',
      render: (_, row) => {
        const total =
          row.record.otNormalMinutes +
          row.record.otExtraMinutes +
          row.record.otSpecialMinutes;
        if (total === 0) {
          return (
            <Text type="secondary" style={{ fontSize: 12 }}>
              —
            </Text>
          );
        }
        return (
          <Tag color="purple" style={{ margin: 0 }}>
            {formatMinutes(total)}
          </Tag>
        );
      },
    },
    ...(canOperate
      ? ([
          {
            title: '',
            key: 'actions',
            width: 115,
            fixed: 'right',
            render: (_, row) => (
              <Tooltip title="Add an operator-tagged punch for this employee + day">
                <Button
                  size="small"
                  icon={<Plus size={14} strokeWidth={1.75} />}
                  onClick={(e) => {
                    e.stopPropagation();
                    setAddPunchTarget(row);
                  }}
                >
                  Add punch
                </Button>
              </Tooltip>
            ),
          },
        ] satisfies TableProps<AttendanceRecordsRow>['columns'])
      : []),
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <AttendanceTabs />
      <PageHeader
        eyebrow="Attendance"
        title="Records"
        description={scopeLabel}
        level={4}
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
            <Segmented
              options={[
                { value: 'ALL', label: 'All' },
                ...STATUS_OPTIONS.map((o) => ({ value: o.value, label: o.label })),
              ]}
              value={search.status ?? 'ALL'}
              onChange={(v) =>
                updateFilter({ status: v === 'ALL' ? undefined : (v as AttendanceStatus) })
              }
            />
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 12,
              }}
            >
              <Clock size={14} strokeWidth={1.75} />
              <Text type="secondary" style={{ fontSize: 12 }}>
                Late only
              </Text>
              <Switch
                size="small"
                checked={search.lateOnly === true}
                onChange={(checked) =>
                  updateFilter({ lateOnly: checked ? true : undefined })
                }
              />
            </span>
          </>
        }
        onReset={hasActiveFilters ? clearAll : undefined}
      />

      <RequestState
        loading={false}
        error={recordsQuery.isError ? recordsQuery.error : undefined}
        empty={!recordsQuery.isLoading && rows.length === 0}
        emptyIcon={Hourglass}
        emptyTitle={hasActiveFilters ? 'No matching records' : 'No records for today yet'}
        emptyDescription={
          hasActiveFilters
            ? 'Widen the date range or clear filters.'
            : 'Records appear as employees punch in. Try widening the range to pull historical days.'
        }
        errorTitle={forbiddenOr(
          recordsQuery.error,
          "You don't have permission to view org-wide attendance records.",
          "Couldn't load attendance records",
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
        <Table<AttendanceRecordsRow>
          rowKey={(row) => row.record.id}
          loading={recordsQuery.isLoading || recordsQuery.isFetching}
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
            const next: Partial<RecordsSearch> = {};
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

      <PunchAddForRecordModal
        record={addPunchTarget?.record ?? null}
        employee={addPunchTarget?.employee ?? null}
        onClose={() => setAddPunchTarget(null)}
      />
      {selectedRow ? (
        <AttendanceRecordDrawer
          row={selectedRow}
          date={selectedRow.record.workDate}
          onClose={() => setSelectedRow(null)}
        />
      ) : null}
    </div>
  );
}

/** A date-time (check-in / out, shift start / end): date on top, time below, read straight off the wire value. */
function PunchCell({ at }: { at: string | null }) {
  if (!at) return <Text type="secondary">—</Text>;
  const [date, time] = formatDateTimeNumeric(at).split(' ');
  return (
    <span style={{ display: 'inline-flex', flexDirection: 'column', lineHeight: 1.3, fontVariantNumeric: 'tabular-nums' }}>
      <span style={{ fontSize: 12 }}>{date}</span>
      <Text type="secondary" style={{ fontSize: 11 }}>{time}</Text>
    </span>
  );
}

/** Shift / break clock time ("HH:mm") from a LocalTime string. */
function TimeCell({ value }: { value: string | null }) {
  if (!value) return <Text type="secondary">—</Text>;
  return (
    <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>{value.slice(0, 5)}</span>
  );
}

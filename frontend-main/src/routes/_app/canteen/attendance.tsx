// /canteen/attendance — the canteen meal ledger.
//
// Same browse shape as /attendance/punches (URL-driven filters, Spring
// pagination) so operators who already know the attendance pages don't have
// to relearn anything. One deliberate difference: the range is always sent
// as `from` + `to`, even for a single day. The attendance BFF collapses a
// same-day range into its `date=` shortcut; the canteen endpoint has no such
// parameter and would ignore it, returning the unfiltered list.
//
// Both the filters and the columns are deliberately narrow: a date range and
// one employee, and a table carrying only what the desk reads off it — who,
// their department, when, and any note. Meal, source and device are still on
// the DTO; they were simply more than this screen needs.
//
// The employee filter is the shared EmployeePicker (GET /employees/picker),
// which lists a default page on open and narrows as you type. It resolves to
// an id, so the list is filtered by `employeeId` rather than by a typed-in
// code — no guessing whether a partial string was meant as a whole code.
//
// The two write paths — one-at-a-time via the code lookup, or a whole sheet
// at once — live in the toolbar, and are labelled by *how* they write ("Add
// manually" / "Upload from file") rather than by what they create, because
// that is the distinction the clerk is actually choosing between.

import { useContext, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  DatePicker,
  Popconfirm,
  Space,
  Table,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import dayjs from 'dayjs';
import { Pencil, PencilLine, Trash2, UploadCloud, UtensilsCrossed } from 'lucide-react';

import { FilterBar, PageHeader, PersonCell, RequestState } from '@/components';
import { AbilityContext } from '@/auth';
import { EmployeePicker } from '@/people';
import { forbiddenOr, getErrorMessage, isNotFound } from '@/lib';
import { numOrUndef, strOrUndef } from '@/attendance/admin';
import { formatDateShort } from '@/attendance';
import {
  AddCanteenAttendanceDrawer,
  CanteenTabs,
  EditCanteenAttendanceDrawer,
  PendingBackendNotice,
  UploadCanteenAttendanceDrawer,
  canteenAttendanceApi,
  formatCanteenTime,
  type CanteenAttendanceRow,
} from '@/canteen';

const { Text } = Typography;
const { RangePicker } = DatePicker;

type CanteenAttendanceSearch = {
  from?: string;
  to?: string;
  employeeId?: number;
  page?: number;
  size?: number;
  sort?: string;
};

export const Route = createFileRoute('/_app/canteen/attendance')({
  validateSearch: (search): CanteenAttendanceSearch => ({
    from: strOrUndef(search.from),
    to: strOrUndef(search.to),
    employeeId: numOrUndef(search.employeeId),
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
    sort: strOrUndef(search.sort),
  }),
  component: CanteenAttendancePage,
});

function CanteenAttendancePage() {
  const navigate = useNavigate({ from: '/canteen/attendance' });
  const search = useSearch({ from: '/_app/canteen/attendance' });
  const ability = useContext(AbilityContext);
  const canWrite =
    ability.can('write', 'canteen.attendance') ||
    ability.can('manage', 'canteen') ||
    ability.can('manage', 'attendance');

  const [addOpen, setAddOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [editRow, setEditRow] = useState<CanteenAttendanceRow | null>(null);

  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const deleteMutation = useMutation({
    mutationFn: (id: number) => canteenAttendanceApi.remove(id),
    onSuccess: () => {
      message.success('Canteen entry deleted');
      queryClient.invalidateQueries({ queryKey: ['canteen'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const page = search.page ?? 1;
  const size = search.size ?? 50;

  const listQuery = useQuery({
    queryKey: ['canteen', 'attendance', search],
    queryFn: ({ signal }) =>
      canteenAttendanceApi.list(
        {
          from: search.from,
          to: search.to,
          employeeId: search.employeeId,
          page: page - 1,
          size,
          sort: search.sort,
        },
        signal,
      ),
    placeholderData: (prev) => prev,
    retry: (count, err) => !isNotFound(err) && count < 2,
  });

  const rows = listQuery.data?.content ?? [];
  const totalElements = listQuery.data?.totalElements ?? 0;

  const updateFilter = (patch: Partial<CanteenAttendanceSearch>) =>
    navigate({ search: (prev) => ({ ...prev, ...patch, page: 1 }), replace: true });

  const clearAll = () => navigate({ search: () => ({}), replace: true });

  const dateRangeValue =
    search.from || search.to
      ? ([
          search.from ? dayjs(search.from) : null,
          search.to ? dayjs(search.to) : null,
        ] as [dayjs.Dayjs | null, dayjs.Dayjs | null])
      : null;

  const hasActiveFilters = Boolean(
    search.from || search.to || search.employeeId,
  );

  const columns: TableProps<CanteenAttendanceRow>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      width: 260,
      render: (_, row) => (
        <PersonCell
          size="compact"
          person={{
            fullName:
              row.employee?.fullName ??
              (row.entry?.employeeId != null
                ? `Employee #${row.entry.employeeId}`
                : 'Unknown employee'),
            employeeCode: row.employee?.employeeCode,
            profilePictureUrl: row.employee?.profilePictureUrl,
            designation: row.employee?.designationName,
          }}
        />
      ),
    },
    {
      title: 'Department',
      key: 'department',
      width: 170,
      render: (_, row) => (
        <Text style={{ fontSize: 12 }}>{row.employee?.departmentName ?? '—'}</Text>
      ),
    },
    {
      title: 'Date',
      key: 'attendanceDate',
      width: 130,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {formatDateShort(row.entry?.attendanceDate)}
        </span>
      ),
    },
    {
      title: 'Time',
      key: 'attendanceTime',
      width: 90,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {formatCanteenTime(row.entry?.attendanceTime)}
        </span>
      ),
    },
    {
      title: (
        <Tooltip title="Meals this entry represents.">
          <span>Count</span>
        </Tooltip>
      ),
      key: 'count',
      width: 90,
      align: 'right',
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {row.entry?.count ?? 1}
        </span>
      ),
    },
    {
      title: 'Remarks',
      key: 'remarks',
      render: (_, row) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {row.entry?.remarks ?? '—'}
        </Text>
      ),
    },
    ...(canWrite
      ? ([
          {
            title: '',
            key: 'actions',
            width: 100,
            fixed: 'right',
            render: (_, row) => (
              <Space size={4}>
                <Tooltip title="Edit date, time or count">
                  <Button
                    size="small"
                    type="text"
                    icon={<Pencil size={14} strokeWidth={1.75} />}
                    onClick={() => setEditRow(row)}
                  />
                </Tooltip>
                <Popconfirm
                  title="Delete this entry?"
                  description="The meal is removed from the ledger. This cannot be undone."
                  okText="Delete"
                  okButtonProps={{ danger: true }}
                  cancelText="Keep"
                  onConfirm={() => {
                    if (row.entry?.id != null) deleteMutation.mutate(row.entry.id);
                  }}
                >
                  <Tooltip title="Delete entry">
                    <Button
                      size="small"
                      type="text"
                      danger
                      icon={<Trash2 size={14} strokeWidth={1.75} />}
                      loading={
                        deleteMutation.isPending &&
                        deleteMutation.variables === row.entry?.id
                      }
                    />
                  </Tooltip>
                </Popconfirm>
              </Space>
            ),
          },
        ] satisfies TableProps<CanteenAttendanceRow>['columns'])
      : []),
  ];

  const pending = isNotFound(listQuery.error);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <CanteenTabs />
      <PageHeader
        eyebrow="Canteen"
        title="Attendance"
        description={
          hasActiveFilters
            ? `${totalElements.toLocaleString('en-PK')} entries`
            : `Showing today · ${formatDateShort(dayjs().format('YYYY-MM-DD'))}`
        }
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
                { label: 'This month', value: [dayjs().startOf('month'), dayjs()] },
              ]}
            />
            <EmployeePicker
              value={search.employeeId}
              onChange={(v) => updateFilter({ employeeId: v })}
              placeholder="Employee code or name"
              style={{ minWidth: 240 }}
            />
          </>
        }
        actions={
          canWrite ? (
            <>
              <Button
                icon={<UploadCloud size={15} strokeWidth={1.75} />}
                onClick={() => setUploadOpen(true)}
              >
                Upload from file
              </Button>
              <Button
                type="primary"
                icon={<PencilLine size={15} strokeWidth={1.75} />}
                onClick={() => setAddOpen(true)}
              >
                Add manually
              </Button>
            </>
          ) : null
        }
        onReset={hasActiveFilters ? clearAll : undefined}
      />

      <RequestState
        loading={false}
        error={listQuery.isError && !pending ? listQuery.error : undefined}
        empty={!listQuery.isLoading && !listQuery.isError && rows.length === 0}
        emptyIcon={UtensilsCrossed}
        filterActive={hasActiveFilters}
        onClearFilters={clearAll}
        emptyTitle="No canteen entries"
        emptyDescription="Entries land here as canteen readers report, or as they are added by hand and by file."
        errorTitle={forbiddenOr(
          listQuery.error,
          "You don't have permission to view canteen attendance.",
          "Couldn't load canteen attendance",
        )}
        onRetry={() => listQuery.refetch()}
      >
        {pending ? (
          <PendingBackendNotice
            what="canteen meal ledger"
            endpoint="GET /canteen/attendance"
          />
        ) : (
          <Table<CanteenAttendanceRow>
            rowKey={(row, index) => row.entry?.id ?? `row-${index}`}
            loading={listQuery.isLoading || listQuery.isFetching}
            dataSource={rows}
            columns={columns}
            size="middle"
            sticky
            scroll={{ x: 'max-content' }}
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
          />
        )}
      </RequestState>

      <AddCanteenAttendanceDrawer open={addOpen} onClose={() => setAddOpen(false)} />
      <EditCanteenAttendanceDrawer row={editRow} onClose={() => setEditRow(null)} />
      <UploadCanteenAttendanceDrawer
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
      />
    </div>
  );
}

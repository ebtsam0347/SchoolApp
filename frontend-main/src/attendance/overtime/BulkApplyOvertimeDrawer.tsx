// Bulk OT for a section / department.
//
// Two roll-calls feed the same drawer, because the two callers can read two
// different things — `scope` picks. Both answer the same row shape
// (employee summary + status + punches):
//
//   • `directReports` (default, /time/overtime) — /bff/attendance/team/today:
//     every direct report (and delegated report) on the picked date, in one
//     response; section / search filter client-side.
//
//   • `ownDepartment` (/time/team-requests) — the filer holds
//     TEAM_REQUEST_GENERATION but manages nobody, so team/today answers with
//     an empty roll-call. That scope reads the paginated
//     `GET /overtime/department/attendance` — the caller's own department,
//     filtered server-side by one section and / or a name / employee-code
//     search. (The endpoint's `status` filter isn't used here — every
//     status is shown so ineligible rows can be seen and skipped.)
//
// Eligible rows are pre-ticked as each page loads; a manual untick sticks
// across pages and filters until the date changes. Submit hits
// POST /overtime/bulk, which is department-scoped on the backend ("all
// employees must be in the submitter's department") and silently drops
// anyone without a punch — the roll-call names those rows, so we disable
// them up front.

import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  Radio,
  Select,
  Space,
  Table,
  Typography,
  theme as antdTheme,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { Search, Users } from 'lucide-react';
import type { ColumnsType } from 'antd/es/table';

import {
  DrawerForm,
  PersonCell,
  RequestState,
  StatusPill,
  type StatusPillTone,
} from '@/components';
import { handleFormError, useDebounced } from '@/lib';
import { catalogsApi, peopleBff } from '@/people';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  FACTORY_CODES,
  OVERTIME_TYPE_DESCRIPTION,
  OVERTIME_TYPE_LABEL,
  attendanceBff,
  formatClock,
  formatClockOn,
  type TeamAttendanceStatus,
} from '@/attendance';

import { overtimeApi } from '../api';
import type { BulkOvertimeRequest, FactoryCode, OvertimeType } from '../types';

const TYPE_OPTIONS: OvertimeType[] = ['NORMAL', 'EXTRA', 'SPECIAL'];

const STATUS_LABEL: Record<TeamAttendanceStatus, string> = {
  ...ATTENDANCE_STATUS_LABEL,
  PENDING_PUNCH: 'No punch yet',
};

const STATUS_TONE: Record<TeamAttendanceStatus, StatusPillTone> = {
  ...ATTENDANCE_STATUS_TONE,
  PENDING_PUNCH: 'warning',
};

// Statuses that can be authorized — anyone else is shown for context but
// can't be ticked (the server would silently skip them anyway).
const ELIGIBLE_STATUSES: ReadonlySet<TeamAttendanceStatus> = new Set([
  'PRESENT',
  'HALF_DAY',
]);

/** Which roster the table lists. See the header note. */
export type BulkOvertimeScope = 'directReports' | 'ownDepartment';

export type BulkApplyOvertimeDrawerProps = {
  open: boolean;
  onClose: () => void;
  /** Defaults to `directReports` — the manager roll-call. */
  scope?: BulkOvertimeScope;
};

/** One table row, whichever roll-call it came from. */
type BulkRow = {
  employee: {
    id: number;
    fullName: string;
    employeeCode: string | null;
    profilePictureUrl: string | null;
    sectionId: number | null;
    sectionName: string | null;
    departmentName: string | null;
  };
  status: TeamAttendanceStatus;
  checkIn: string | null;
  checkOut: string | null;
};

function isEligible(row: BulkRow): boolean {
  return ELIGIBLE_STATUSES.has(row.status);
}

type Values = {
  workDate: dayjs.Dayjs;
  type: OvertimeType;
  factoryCode: FactoryCode;
  reason?: string;
};

export function BulkApplyOvertimeDrawer({
  open,
  onClose,
  scope = 'directReports',
}: BulkApplyOvertimeDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const { token } = antdTheme.useToken();

  /** Manager roll-call (true) vs. own-department roll-call. */
  const isTeamScope = scope === 'directReports';

  const [date, setDate] = useState<dayjs.Dayjs>(dayjs());
  const isoDate = date.format('YYYY-MM-DD');

  // Team scope: client-side filters over the whole roll-call.
  const [sectionIds, setSectionIds] = useState<number[]>([]);
  const [searchInput, setSearchInput] = useState('');
  const search = useDebounced(searchInput.trim().toLowerCase(), 200);

  // Department scope: server-side filters + pagination.
  const [deptSectionId, setDeptSectionId] = useState<number | undefined>();
  const [deptSearchInput, setDeptSearchInput] = useState('');
  const deptSearch = useDebounced(deptSearchInput.trim(), 300) || undefined;
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [selected, setSelected] = useState<Set<number>>(new Set());
  /** Eligible rows the filer unticked — kept off when their page reloads. */
  const [unticked, setUnticked] = useState<Set<number>>(new Set());

  /* -------------------------- roll-call A: my team ------------------------ */

  const teamQuery = useQuery({
    queryKey: ['bff', 'attendance', 'team-today', isoDate],
    queryFn: ({ signal }) => attendanceBff.teamToday(isoDate, signal),
    enabled: open && isTeamScope,
  });

  /* ---------------------- roll-call B: my own department ------------------ */

  const departmentQuery = useQuery({
    queryKey: [
      'overtime',
      'department-attendance',
      {
        date: isoDate,
        sectionId: deptSectionId,
        search: deptSearch,
        page,
        pageSize,
      },
    ],
    queryFn: ({ signal }) =>
      overtimeApi.departmentAttendance(
        {
          date: isoDate,
          sectionId: deptSectionId,
          search: deptSearch,
          page: page - 1,
          size: pageSize,
        },
        signal,
      ),
    enabled: open && !isTeamScope,
    // Keep the current page on screen while the next one loads.
    placeholderData: (prev) => prev,
  });

  // Section options: the caller's department sections, from the shared
  // (auth-only) catalog. Same query keys as the Team requests page, so both
  // are usually cache reads.
  const profileQuery = useQuery({
    queryKey: ['account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
    retry: false,
    enabled: open && !isTeamScope,
  });
  const departmentId = profileQuery.data?.resolved?.department?.id;
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections', departmentId],
    queryFn: ({ signal }) => catalogsApi.sections(departmentId, signal),
    staleTime: 5 * 60_000,
    enabled: open && !isTeamScope && departmentId != null,
  });

  /* ------------------------------ shared view ----------------------------- */

  const rollCallQuery = isTeamScope ? teamQuery : departmentQuery;
  const rollCallRows = isTeamScope
    ? teamQuery.data?.rows
    : departmentQuery.data?.content;

  const rows = useMemo<BulkRow[]>(
    () =>
      (rollCallRows ?? []).map((r) => ({
        employee: {
          id: r.employee.id,
          fullName: r.employee.fullName,
          employeeCode: r.employee.employeeCode,
          profilePictureUrl: r.employee.profilePictureUrl,
          sectionId: r.employee.sectionId,
          sectionName: r.employee.sectionName,
          departmentName: r.employee.departmentName,
        },
        status: r.status,
        checkIn: r.checkIn,
        checkOut: r.checkOut,
      })),
    [rollCallRows],
  );

  // Pre-tick every eligible row as its roll-call (or department page)
  // arrives, except ones the filer unticked by hand.
  useEffect(() => {
    if (!rollCallRows) return;
    setSelected((prev) => {
      const next = new Set(prev);
      for (const r of rollCallRows) {
        if (ELIGIBLE_STATUSES.has(r.status) && !unticked.has(r.employee.id)) {
          next.add(r.employee.id);
        }
      }
      return next;
    });
  }, [rollCallRows, unticked]);

  // A new date is a new roll-call — different people are present on
  // different days, so ticks don't carry over.
  useEffect(() => {
    setSelected(new Set());
    setUnticked(new Set());
    setPage(1);
  }, [isoDate]);

  // Each opening starts clean — a cached roll-call doesn't re-fire the
  // effects above, so without this the last batch's ticks would come back.
  useEffect(() => {
    if (open) return;
    setSelected(new Set());
    setUnticked(new Set());
    setSectionIds([]);
    setSearchInput('');
    setDeptSectionId(undefined);
    setDeptSearchInput('');
    setPage(1);
    setDate(dayjs());
  }, [open]);

  const sectionOptions = useMemo(() => {
    if (!isTeamScope) {
      return (sectionsQuery.data ?? [])
        .filter((sec) => sec.id != null)
        .map((sec) => ({ value: sec.id as number, label: sec.name ?? `Section ${sec.id}` }))
        .sort((a, b) => a.label.localeCompare(b.label));
    }
    const m = new Map<number, string>();
    for (const r of rows) {
      const sid = r.employee.sectionId;
      if (sid != null) {
        m.set(sid, r.employee.sectionName ?? `Section ${sid}`);
      }
    }
    return Array.from(m, ([value, label]) => ({ value, label })).sort((a, b) =>
      a.label.localeCompare(b.label),
    );
  }, [isTeamScope, sectionsQuery.data, rows]);

  // The department page is already filtered server-side.
  const visibleRows = useMemo(() => {
    if (!isTeamScope) return rows;
    return rows.filter((r) => {
      if (
        sectionIds.length > 0 &&
        !sectionIds.includes(r.employee.sectionId ?? -1)
      ) {
        return false;
      }
      if (search) {
        const hay = `${r.employee.fullName.toLowerCase()} ${(
          r.employee.employeeCode ?? ''
        ).toLowerCase()}`;
        if (!hay.includes(search)) return false;
      }
      return true;
    });
  }, [isTeamScope, rows, sectionIds, search]);

  const eligibleVisibleIds = useMemo(
    () => visibleRows.filter(isEligible).map((r) => r.employee.id),
    [visibleRows],
  );

  const eligibleTotalCount = useMemo(() => rows.filter(isEligible).length, [rows]);

  const selectedCount = selected.size;

  const tick = (ids: number[], on: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      for (const id of ids) {
        if (on) next.add(id);
        else next.delete(id);
      }
      return next;
    });
    setUnticked((prev) => {
      const next = new Set(prev);
      for (const id of ids) {
        if (on) next.delete(id);
        else next.add(id);
      }
      return next;
    });
  };

  const mutation = useMutation({
    mutationFn: (body: BulkOvertimeRequest) => overtimeApi.bulk(body),
    onSuccess: (res) => {
      const created = res.created.length;
      const skipped = res.skipped.length;
      if (created === 0) {
        message.warning(
          'No OT rows created. None of the selected employees have a punch on that date.',
        );
      } else if (skipped > 0) {
        message.success(
          `Created ${created} OT row${created === 1 ? '' : 's'} · skipped ${skipped} (no punch)`,
        );
      } else {
        message.success(
          `Created ${created} OT row${created === 1 ? '' : 's'}`,
        );
      }
      queryClient.invalidateQueries({ queryKey: ['overtime'] });
      queryClient.invalidateQueries({ queryKey: ['bff', 'overtime'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const employeeColumn: ColumnsType<BulkRow>[number] = {
    title: 'Employee',
    key: 'employee',
    render: (_v, row) => (
      <PersonCell
        size="compact"
        person={{
          fullName: row.employee.fullName,
          employeeCode: row.employee.employeeCode,
          profilePictureUrl: row.employee.profilePictureUrl,
          subtitle:
            row.employee.sectionName ?? row.employee.departmentName ?? null,
        }}
      />
    ),
  };

  const columns: ColumnsType<BulkRow> = [
    employeeColumn,
    {
      title: 'Status',
      key: 'status',
      width: 130,
      render: (_v, row) =>
        row.status ? (
          <StatusPill tone={STATUS_TONE[row.status]}>
            {STATUS_LABEL[row.status]}
          </StatusPill>
        ) : null,
    },
    {
      title: 'Punch',
      key: 'punch',
      width: 140,
      render: (_v, row) =>
        row.checkIn || row.checkOut ? (
          <span
            style={{
              fontVariantNumeric: 'tabular-nums',
              fontSize: 12,
              color: token.colorTextSecondary,
            }}
          >
            {formatClock(row.checkIn)}
            {row.checkOut ? ` – ${formatClockOn(row.checkOut, row.checkIn)}` : ''}
          </span>
        ) : (
          <span style={{ fontSize: 12, color: token.colorTextTertiary }}>—</span>
        ),
    },
  ];

  return (
    <DrawerForm<Values>
      title="Bulk apply OT"
      open={open}
      onClose={onClose}
      form={form}
      size={720}
      submitText={
        selectedCount > 0 ? `Submit OT for ${selectedCount}` : 'Submit'
      }
      submitting={mutation.isPending}
      submitDisabled={selectedCount === 0}
      initialValues={{ workDate: dayjs(), type: 'NORMAL' satisfies OvertimeType }}
      onFinish={(v) => {
        if (selected.size === 0) {
          message.warning('Pick at least one employee');
          return;
        }
        mutation.mutate({
          workDate: v.workDate.format('YYYY-MM-DD'),
          overtimeType: v.type,
          factoryCode: v.factoryCode,
          reason: v.reason,
          employeeIds: Array.from(selected),
        });
      }}
    >
      <Alert
        type="info"
        showIcon
        icon={<Users size={14} />}
        title="Authorize OT for a section"
        description={
          isTeamScope
            ? "Pick a date and narrow to a section. Everyone with a punch is pre-ticked; people without a punch are shown for context but can't be submitted."
            : "Pick a date, then narrow by section and / or name or code. Everyone with a punch is pre-ticked as each page loads; ticks are kept across pages and filters. People without a punch can't be submitted."
        }
        style={{ marginBottom: 16 }}
      />

      <Form.Item
        label="Work date"
        name="workDate"
        rules={[{ required: true, message: 'Pick the date' }]}
      >
        <DatePicker
          style={{ width: '100%' }}
          format="DD MMM YYYY"
          disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
          onChange={(d) => {
            if (d) setDate(d);
          }}
        />
      </Form.Item>

      <Form.Item
        label="OT type"
        name="type"
        rules={[{ required: true, message: 'Pick which OT bucket to pay' }]}
        extra={
          <Form.Item noStyle shouldUpdate={(p, c) => p.type !== c.type}>
            {({ getFieldValue }) => {
              const t = getFieldValue('type') as OvertimeType | undefined;
              return t ? `Applied to every row · ${OVERTIME_TYPE_DESCRIPTION[t]}` : null;
            }}
          </Form.Item>
        }
      >
        <Radio.Group optionType="button" buttonStyle="solid">
          {TYPE_OPTIONS.map((t) => (
            <Radio.Button key={t} value={t}>
              {OVERTIME_TYPE_LABEL[t]}
            </Radio.Button>
          ))}
        </Radio.Group>
      </Form.Item>

      <Form.Item
        label="Factory code"
        name="factoryCode"
        rules={[{ required: true, message: 'Pick the factory code' }]}
        extra="Applied to every row."
      >
        <Select
          placeholder="Select factory code"
          options={FACTORY_CODES.map((c) => ({ value: c, label: c }))}
        />
      </Form.Item>

      <div
        style={{
          display: 'flex',
          gap: 8,
          marginBottom: 12,
          flexWrap: 'wrap',
        }}
      >
        {isTeamScope ? (
          <>
            <Select
              mode="multiple"
              allowClear
              placeholder="Filter by section"
              value={sectionIds}
              onChange={setSectionIds}
              style={{ flex: 1, minWidth: 200 }}
              options={sectionOptions}
              maxTagCount="responsive"
            />
            <Input
              placeholder="Search name or code"
              prefix={<Search size={14} />}
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              style={{ width: 220 }}
              allowClear
            />
          </>
        ) : (
          <>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Filter by section"
              value={deptSectionId}
              onChange={(v) => {
                setDeptSectionId(v);
                setPage(1);
              }}
              loading={sectionsQuery.isLoading}
              style={{ flex: 1, minWidth: 200 }}
              options={sectionOptions}
            />
            <Input
              placeholder="Search name or code"
              prefix={<Search size={14} />}
              value={deptSearchInput}
              onChange={(e) => {
                setDeptSearchInput(e.target.value);
                setPage(1);
              }}
              style={{ width: 220 }}
              allowClear
            />
          </>
        )}
      </div>

      {isTeamScope && teamQuery.data?.truncated ? (
        <Alert
          type="warning"
          showIcon
          title="Too many people to list"
          description="Your team exceeds the 500-person roll-call cap for this date. Narrow the day on the team page first, or apply per section."
          style={{ marginBottom: 12 }}
        />
      ) : null}

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 8,
          gap: 8,
        }}
      >
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {isTeamScope
            ? `${selectedCount} of ${eligibleTotalCount} eligible selected`
            : `${selectedCount} selected · ${(
                departmentQuery.data?.totalElements ?? 0
              ).toLocaleString()} in view`}
        </Typography.Text>
        <Space size="small">
          <Button
            size="small"
            disabled={eligibleVisibleIds.length === 0}
            onClick={() => tick(eligibleVisibleIds, true)}
          >
            {isTeamScope ? 'Select all visible' : 'Select all on page'}
          </Button>
          <Button
            size="small"
            disabled={selectedCount === 0}
            onClick={() => tick(Array.from(selected), false)}
          >
            Clear
          </Button>
        </Space>
      </div>

      <RequestState
        loading={rollCallQuery.isLoading}
        error={rollCallQuery.isError ? rollCallQuery.error : undefined}
        errorTitle={
          isTeamScope
            ? "Couldn't load your team for that date"
            : "Couldn't load your department"
        }
        empty={!rollCallQuery.isLoading && rows.length === 0}
        emptyTitle={
          isTeamScope
            ? 'No team for this date'
            : deptSectionId != null || deptSearch
              ? 'Nobody matches these filters'
              : 'Nobody in your department'
        }
      >
        <Table<BulkRow>
          rowKey={(r) => r.employee.id}
          dataSource={visibleRows}
          columns={columns}
          size="small"
          loading={!isTeamScope && departmentQuery.isFetching}
          pagination={
            isTeamScope
              ? false
              : {
                  current: page,
                  pageSize,
                  total: departmentQuery.data?.totalElements ?? 0,
                  showSizeChanger: true,
                  pageSizeOptions: ['20', '50', '100', '200'],
                  size: 'small',
                  showTotal: (n, [from, to]) => `${from}-${to} of ${n.toLocaleString()}`,
                  onChange: (p, size) => {
                    setPage(size !== pageSize ? 1 : p);
                    setPageSize(size);
                  },
                }
          }
          scroll={{ y: 360 }}
          rowSelection={{
            selectedRowKeys: Array.from(selected),
            onSelect: (row, on) => tick([row.employee.id], on),
            onSelectAll: (on, _sel, changed) =>
              tick(changed.map((r) => r.employee.id), on),
            getCheckboxProps: (row) => ({ disabled: !isEligible(row) }),
          }}
        />
      </RequestState>

      <Form.Item label="Reason" name="reason" style={{ marginTop: 16 }}>
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="Eid season — extended packing shift."
        />
      </Form.Item>
    </DrawerForm>
  );
}

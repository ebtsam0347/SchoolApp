// /attendance/auto-attendance — operator fills one employee's attendance
// over a date range: pick the card no (employee code), the attendance start
// and end dates, and either each day's shift timings (default) or custom
// in / out times; every day in the range gets an IN and an OUT punch,
// pinned as the record's check-in / check-out, with "Auto attendance applied
// through operator" on the record's notes.
//
// Shift-aware: the server resolves each day's shift and places the times on
// the calendar date the shift window implies (same 2h pre-shift rule as the
// device puller), so an 18:00–02:00 shift's record reads check-in 9 Oct
// 18:00, check-out 10 Oct 02:00. The page shows the employee's assigned
// shift(s) for the range and previews the first day's instants.
//
// One call (`POST /attendance/operator/auto-attendance`, one transaction).
// The server skips — and lists with a reason — Sundays / holidays (unless
// "Include Sundays & holidays"), full-day approved leave, locked periods,
// and days that already have punches (unless "Also fill days that already
// have punches"). Future days are filled too (attendance entered ahead).
//
// Gated on `attendance.operator`.

import { useContext, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Avatar,
  Button,
  Checkbox,
  DatePicker,
  Form,
  Input,
  Popconfirm,
  Table,
  Tag,
  TimePicker,
  Typography,
  theme as antdTheme,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { CalendarRange, ShieldAlert, User } from 'lucide-react';

import { Section } from '@/components';
import { AbilityContext } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  EmployeePicker,
  catalogsApi,
  employeesApi,
  type EmployeePickerCandidate,
} from '@/people';
import { AttendanceTabs } from '@/attendance/admin';
import {
  attendanceApi,
  formatClockOn,
  formatDateShort,
  punchInstantFor,
  shiftsApi,
  type AutoAttendanceResponse,
  type AutoAttendanceSkipReason,
} from '@/attendance';

const { Text } = Typography;

export const Route = createFileRoute('/_app/attendance/auto-attendance')({
  component: AutoAttendancePage,
});

/** Matches the server's per-run cap. */
const MAX_DAYS = 62;

const SKIP_LABEL: Record<AutoAttendanceSkipReason, string> = {
  WEEKEND: 'Sunday',
  HOLIDAY: 'Holiday',
  LEAVE: 'On approved leave',
  LOCKED: 'Payroll period locked',
  HAS_PUNCHES: 'Already has punches',
  NO_SHIFT: 'No shift assigned that day',
  OUTSIDE_SHIFT_WINDOW: "Times don't fit the day's shift",
};

const hhmm = (t: string) => t.slice(0, 5);
/** A shift ending at / before its start runs past midnight. */
const crossesMidnight = (start: string, end: string) => hhmm(end) <= hhmm(start);

type FormShape = {
  employeeId: number;
  from: Dayjs;
  to: Dayjs;
  useShiftTimings: boolean;
  inTime?: Dayjs;
  outTime?: Dayjs;
  includeOffDays: boolean;
  replaceExisting: boolean;
  remarks?: string;
};

function AutoAttendancePage() {
  const ability = useContext(AbilityContext);
  const canOperate = ability.can('operator', 'attendance');
  const { token } = antdTheme.useToken();
  const [form] = Form.useForm<FormShape>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const [employee, setEmployee] = useState<EmployeePickerCandidate | undefined>();
  const [result, setResult] = useState<AutoAttendanceResponse | null>(null);

  // The picker row carries name / department / designation; section and
  // photo come from the employee record (API builds whose picker predates
  // them) — section name via the open sections catalog.
  const detailQuery = useQuery({
    queryKey: ['employees', 'by-ids', employee?.id],
    queryFn: ({ signal }) => employeesApi.byIds([employee?.id ?? 0], signal),
    enabled: !!employee && (employee.sectionName == null || employee.profilePictureUrl == null),
    staleTime: 5 * 60_000,
  });
  const detail = detailQuery.data?.[0];
  const sectionsQuery = useQuery({
    queryKey: ['catalog', 'sections', detail?.departmentId],
    queryFn: ({ signal }) => catalogsApi.sections(detail?.departmentId, signal),
    enabled: !!detail?.sectionId && employee?.sectionName == null,
    staleTime: 10 * 60_000,
  });
  const sectionName =
    employee?.sectionName ??
    sectionsQuery.data?.find((s) => s.id === detail?.sectionId)?.name ??
    null;
  const photoUrl = employee?.profilePictureUrl ?? detail?.profilePictureUrl ?? undefined;

  const from = Form.useWatch('from', form);
  const to = Form.useWatch('to', form);
  const inTime = Form.useWatch('inTime', form);
  const outTime = Form.useWatch('outTime', form);
  const useShiftTimings = Form.useWatch('useShiftTimings', form) ?? true;
  const remarks = Form.useWatch('remarks', form)?.trim();
  const days = from && to && !to.isBefore(from, 'day') ? to.diff(from, 'day') + 1 : 0;

  // The employee's shift assignments overlapping the range (today when no
  // range yet), with the catalog's times. Per-day overrides are applied by
  // the server; this is the operator's at-a-glance check.
  const assignmentsQuery = useQuery({
    queryKey: ['shifts', 'employee', employee?.id],
    queryFn: ({ signal }) => shiftsApi.forEmployee(employee?.id ?? 0, signal),
    enabled: !!employee,
    staleTime: 60_000,
  });
  const catalogQuery = useQuery({
    queryKey: ['shifts', 'catalog'],
    queryFn: ({ signal }) => shiftsApi.list(signal),
    enabled: !!employee,
    staleTime: 10 * 60_000,
  });
  const rangeFrom = (from ?? dayjs()).format('YYYY-MM-DD');
  const rangeTo = (to ?? from ?? dayjs()).format('YYYY-MM-DD');
  const shiftsInRange = (assignmentsQuery.data ?? [])
    .filter((a) => a.effectiveFrom <= rangeTo && (!a.effectiveTo || a.effectiveTo >= rangeFrom))
    .map((a) => ({
      assignment: a,
      shift: catalogQuery.data?.find((s) => s.id === a.shiftId),
    }));
  const firstShift = shiftsInRange.find(
    (s) => s.assignment.effectiveFrom <= rangeFrom,
  )?.shift ?? shiftsInRange[0]?.shift;

  // First day's instants, as the server will place them.
  const preview = (() => {
    if (!from) return null;
    const day = from.format('YYYY-MM-DD');
    const start = firstShift?.startTime ?? null;
    const inClock = useShiftTimings ? (start ? hhmm(start) : null) : inTime?.format('HH:mm');
    const outClock = useShiftTimings
      ? firstShift
        ? hhmm(firstShift.endTime)
        : null
      : outTime?.format('HH:mm');
    if (!inClock || !outClock) return null;
    let checkIn = punchInstantFor(day, inClock, start);
    let checkOut = punchInstantFor(day, outClock, start);
    if (!start) {
      // No shift: out at / before in is the next morning (server's fallback).
      checkIn = `${day}T${inClock}:00`;
      checkOut =
        outClock > inClock
          ? `${day}T${outClock}:00`
          : `${dayjs(day).add(1, 'day').format('YYYY-MM-DD')}T${outClock}:00`;
    }
    return { day, checkIn, checkOut, valid: checkOut > checkIn };
  })();

  const mutation = useMutation({
    mutationFn: (v: FormShape) =>
      attendanceApi.autoAttendance({
        employeeId: v.employeeId,
        from: v.from.format('YYYY-MM-DD'),
        to: v.to.format('YYYY-MM-DD'),
        inTime: v.useShiftTimings ? undefined : v.inTime?.format('HH:mm'),
        outTime: v.useShiftTimings ? undefined : v.outTime?.format('HH:mm'),
        includeOffDays: v.includeOffDays,
        replaceExisting: v.replaceExisting,
        remarks: v.remarks?.trim() || undefined,
      }),
    onSuccess: (res) => {
      setResult(res);
      if (res.applied.length > 0) {
        message.success(
          `Auto attendance applied on ${res.applied.length} day${res.applied.length === 1 ? '' : 's'}.`,
        );
      } else {
        message.warning('No days were filled — see the skipped list.');
      }
      void queryClient.invalidateQueries({ queryKey: ['attendance'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'attendance'] });
    },
    onError: (err) => {
      if (isApiError(err)) applyApiErrorToForm(form, err);
      message.error(getErrorMessage(err));
    },
  });

  const infoField = (label: string, value: string | null | undefined) => (
    <div style={{ flex: '1 1 160px', minWidth: 0 }}>
      <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
        {label}
      </Text>
      <div
        style={{
          padding: '6px 11px',
          minHeight: 32,
          borderRadius: token.borderRadius,
          background: token.colorFillTertiary,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {value || <Text type="secondary">—</Text>}
      </div>
    </div>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', width: '100%' }}>
      <AttendanceTabs />

      {!canOperate ? (
        <Alert
          type="warning"
          showIcon
          icon={<ShieldAlert size={16} />}
          style={{ marginBottom: 20 }}
          title="You don't have permission to record attendance for others"
          description="Ask HR to grant access. The form is shown for reference but submissions won't go through."
        />
      ) : null}

      <Section
        title="Auto attendance"
        subtitle="Attendance by card no. and date range — an In and an Out entry on every day, marked as applied through operator."
      >
        <Form<FormShape>
          form={form}
          layout="vertical"
          initialValues={{ useShiftTimings: true, includeOffDays: false, replaceExisting: false }}
          onFinish={(v) => mutation.mutate(v)}
          style={{ display: 'flex', flexDirection: 'column', gap: 4 }}
        >
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              gap: 16,
              padding: 16,
              marginBottom: 16,
              border: `1px dashed ${token.colorBorder}`,
              borderRadius: token.borderRadiusLG,
            }}
          >
            {infoField('Name', employee?.fullName)}
            {infoField('Department', employee?.departmentName)}
            {infoField('Section', sectionName)}
            {infoField('Designation', employee?.designationName)}
            <Avatar
              size={72}
              src={photoUrl}
              icon={<User size={32} />}
              style={{ flexShrink: 0 }}
            />
          </div>

          <Form.Item
            label="Card No."
            name="employeeId"
            rules={[{ required: true, message: 'Pick the employee by card no.' }]}
            style={{ maxWidth: 420 }}
          >
            <EmployeePicker
              placeholder="Type card no. or name"
              onSelectCandidate={(c) => {
                setEmployee(c);
                setResult(null);
              }}
            />
          </Form.Item>

          {employee ? (
            <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Shift{shiftsInRange.length > 1 ? 's' : ''} in this range:
              </Text>
              {assignmentsQuery.isLoading || catalogQuery.isLoading ? (
                <Text type="secondary" style={{ fontSize: 12 }}>loading…</Text>
              ) : shiftsInRange.length === 0 ? (
                <Tag color="warning" style={{ margin: 0 }}>
                  No shift assigned — enter in / out times below
                </Tag>
              ) : (
                shiftsInRange.map(({ assignment, shift }) => (
                  <Tag key={assignment.id} style={{ margin: 0 }}>
                    {assignment.shiftName}
                    {shift ? ` · ${hhmm(shift.startTime)}–${hhmm(shift.endTime)}` : ''}
                    {shift && crossesMidnight(shift.startTime, shift.endTime) ? ' · crosses midnight' : ''}
                    {shiftsInRange.length > 1
                      ? ` · from ${formatDateShort(assignment.effectiveFrom)}${
                          assignment.effectiveTo ? ` to ${formatDateShort(assignment.effectiveTo)}` : ''
                        }`
                      : ''}
                  </Tag>
                ))
              )}
            </div>
          ) : null}

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
            <Form.Item
              label="Attendance start date"
              name="from"
              rules={[{ required: true, message: 'Pick the start date' }]}
              style={{ flex: '1 1 220px' }}
            >
              <DatePicker format="DD MMM YYYY" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="Attendance end date"
              name="to"
              dependencies={['from']}
              rules={[
                { required: true, message: 'Pick the end date' },
                ({ getFieldValue }) => ({
                  validator: (_r, end: Dayjs | undefined) => {
                    const start = getFieldValue('from') as Dayjs | undefined;
                    if (!end || !start) return Promise.resolve();
                    if (end.isBefore(start, 'day')) {
                      return Promise.reject(new Error('End date is before the start date'));
                    }
                    if (end.diff(start, 'day') + 1 > MAX_DAYS) {
                      return Promise.reject(new Error(`At most ${MAX_DAYS} days per run`));
                    }
                    return Promise.resolve();
                  },
                }),
              ]}
              style={{ flex: '1 1 220px' }}
            >
              <DatePicker
                format="DD MMM YYYY"
                style={{ width: '100%' }}
                disabledDate={(d) => !!from && d.isBefore(from, 'day')}
              />
            </Form.Item>
          </div>

          <Form.Item
            name="useShiftTimings"
            valuePropName="checked"
            style={{ marginBottom: 8 }}
            extra="Each day gets its own shift's start as In and end as Out (rotations and overrides included). Untick to enter one in / out time for the whole range."
          >
            <Checkbox>Use each day's shift timings</Checkbox>
          </Form.Item>

          <div
            style={{
              display: useShiftTimings ? 'none' : 'flex',
              flexWrap: 'wrap',
              gap: 16,
            }}
          >
            <Form.Item
              label="In time"
              name="inTime"
              rules={[{ required: !useShiftTimings, message: 'Pick the in time' }]}
              style={{ flex: '1 1 220px' }}
            >
              <TimePicker format="HH:mm" minuteStep={1} needConfirm={false} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="Out time"
              name="outTime"
              dependencies={['inTime', 'useShiftTimings']}
              rules={[
                { required: !useShiftTimings, message: 'Pick the out time' },
                ({ getFieldValue }) => ({
                  validator: (_r, out: Dayjs | undefined) => {
                    const start = getFieldValue('inTime') as Dayjs | undefined;
                    if (out && start && out.format('HH:mm') === start.format('HH:mm')) {
                      return Promise.reject(new Error('Out time is the same as the in time'));
                    }
                    return Promise.resolve();
                  },
                }),
              ]}
              style={{ flex: '1 1 220px' }}
            >
              <TimePicker format="HH:mm" minuteStep={1} needConfirm={false} style={{ width: '100%' }} />
            </Form.Item>
          </div>

          {preview ? (
            <Alert
              type={preview.valid ? 'info' : 'warning'}
              showIcon
              style={{ marginBottom: 16 }}
              title={
                preview.valid
                  ? `${formatDateShort(preview.day)} record → check-in ${formatClockOn(preview.checkIn, preview.day)}, check-out ${formatClockOn(preview.checkOut, preview.day)}`
                  : "Out time isn't after the in time for this shift — those days will be skipped."
              }
              description={
                preview.valid
                  ? 'The attendance date stays the shift day; a check-out after midnight keeps its real (next) date. Every day in the range is placed the same way by its own shift.'
                  : undefined
              }
            />
          ) : null}

          <Form.Item name="includeOffDays" valuePropName="checked" style={{ marginBottom: 4 }}>
            <Checkbox>Include Sundays &amp; holidays</Checkbox>
          </Form.Item>
          <Form.Item
            name="replaceExisting"
            valuePropName="checked"
            extra="Off: days that already have punches are left alone. On: the new In / Out become that day's check-in / check-out; existing punches stay in the log."
          >
            <Checkbox>Also fill days that already have punches</Checkbox>
          </Form.Item>

          <Form.Item label="Remarks (optional)" name="remarks" rules={[{ max: 300 }]}>
            <Input.TextArea
              autoSize={{ minRows: 2, maxRows: 4 }}
              placeholder="e.g. biometric machine down, attendance sheet signed by supervisor"
            />
          </Form.Item>

          <Text type="secondary" style={{ fontSize: 12, marginBottom: 12 }}>
            Narration on each record: “Auto attendance applied through operator (your
            username){remarks ? ` — ${remarks}` : ''}”
          </Text>

          <div>
            <Popconfirm
              title="Apply auto attendance?"
              description={
                employee && days > 0
                  ? `${employee.fullName} (${employee.employeeCode ?? `#${employee.id}`}) · ${days} day${days === 1 ? '' : 's'} · ${
                      useShiftTimings
                        ? "each day's shift timings"
                        : `${inTime?.format('HH:mm') ?? '--'} – ${outTime?.format('HH:mm') ?? '--'}`
                    }`
                  : 'Fill in the form first.'
              }
              okText="Apply"
              onConfirm={() => form.submit()}
              disabled={!canOperate}
            >
              <Button
                type="primary"
                icon={<CalendarRange size={14} />}
                loading={mutation.isPending}
                disabled={!canOperate}
              >
                Apply auto attendance{days > 0 ? ` (${days} day${days === 1 ? '' : 's'})` : ''}
              </Button>
            </Popconfirm>
          </div>
        </Form>
      </Section>

      {result ? (
        <Section
          title="Result"
          subtitle={`${result.applied.length} day${result.applied.length === 1 ? '' : 's'} filled · ${result.skipped.length} skipped`}
        >
          <Alert
            type={result.applied.length > 0 ? 'success' : 'warning'}
            showIcon
            style={{ marginBottom: 16 }}
            title={
              result.applied.length > 0
                ? `In / Out entries added on ${result.applied.length} day${result.applied.length === 1 ? '' : 's'}.`
                : 'No days were filled.'
            }
            description={`Narration: ${result.narration}`}
          />
          {result.applied.length > 0 ? (
            <Table
              rowKey="date"
              size="small"
              style={{ marginBottom: 16 }}
              pagination={result.applied.length > 31 ? { pageSize: 31 } : false}
              dataSource={result.applied}
              columns={[
                {
                  title: 'Attendance date',
                  key: 'date',
                  render: (_v, r) => <Tag color="green" style={{ margin: 0 }}>{formatDateShort(r.date)}</Tag>,
                },
                {
                  title: 'Check-in',
                  key: 'in',
                  render: (_v, r) => formatClockOn(r.checkIn, r.date),
                },
                {
                  title: 'Check-out',
                  key: 'out',
                  render: (_v, r) => formatClockOn(r.checkOut, r.date),
                },
              ]}
            />
          ) : null}
          {result.skipped.length > 0 ? (
            <Table
              rowKey="date"
              size="small"
              pagination={false}
              dataSource={result.skipped}
              columns={[
                {
                  title: 'Skipped day',
                  key: 'date',
                  render: (_v, r) => formatDateShort(r.date),
                },
                {
                  title: 'Why',
                  key: 'reason',
                  render: (_v, r) => SKIP_LABEL[r.reason] ?? r.reason,
                },
              ]}
            />
          ) : null}
        </Section>
      ) : null}
    </div>
  );
}

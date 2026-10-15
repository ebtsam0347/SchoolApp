// /attendance/operator-punch — operator-added punches off paper proof.
//
// Distinct from regularization (employee-driven, multi-stage approval).
// Operator already has the offline approval (signed slip / supervisor's
// note) and just needs to add the missed punch into `attendance_punches`.
// One row per submit; standard dedup on (employee, exact instant) still
// applies; the WEB 2/day cap is bypassed.
//
// Gated on `attendance.operator`.
//
// We also surface a small "recent operator punches" list scoped to the
// employee + day in the form, so the operator can see what's already been
// added without flipping pages.

import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Space,
  Tag,
  TimePicker,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  ClipboardCheck,
  FileText,
  ShieldAlert,
  StickyNote,
} from 'lucide-react';

import { useContext } from 'react';
import { EmptyState, KpiGrid, PageHeader, Section, StatCard, StatusPill, TimelineItem } from '@/components';
import { AbilityContext, Can } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { toLocalDateTime } from '@/lib/dates';
import { EmployeePicker } from '@/people';
import {
  PUNCH_SOURCE_LABEL,
  attendanceApi,
  formatClock,
  formatDateShort,
  type OperatorPunchRequest,
  type PunchDto,
} from '@/attendance';

const { Text } = Typography;

export const Route = createFileRoute('/_app/attendance/operator-punch')({
  component: OperatorPunchPage,
});

type FormShape = {
  employeeId: number;
  date: Dayjs;
  time: Dayjs;
  reason: string;
};

function OperatorPunchPage() {
  const ability = useContext(AbilityContext);
  const canOperate = ability.can('operator', 'attendance');

  const [form] = Form.useForm<FormShape>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const employeeId = Form.useWatch('employeeId', form);
  const date = Form.useWatch('date', form);

  const punchesQuery = useQuery({
    queryKey: ['attendance', 'employee', employeeId, 'punches', date?.format('YYYY-MM-DD')],
    queryFn: ({ signal }) =>
      attendanceApi.punchesForEmployee(
        employeeId!,
        {
          from: date?.format('YYYY-MM-DD') ?? '',
          to: date?.format('YYYY-MM-DD') ?? '',
        },
        signal,
      ),
    enabled: !!(employeeId && date),
    staleTime: 10_000,
  });

  const punches = punchesQuery.data ?? [];

  const mutation = useMutation({
    mutationFn: (body: OperatorPunchRequest) => attendanceApi.operatorPunch(body),
    onSuccess: (punch) => {
      message.success(
        `Operator punch added at ${formatClock(punch.punchedAt)} (source=OPERATOR)`,
      );
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      form.resetFields(['time', 'reason']);
      punchesQuery.refetch();
    },
    onError: (err) => {
      if (isApiError(err)) {
        applyApiErrorToForm(form, err);
      }
      message.error(getErrorMessage(err));
    },
  });

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
        eyebrow="Attendance"
        title="Operator-add a punch"
        description="Record a missed check-in or check-out from a paper slip or supervisor note. The punch is tagged as operator-entered for the audit trail."
      />

      {!canOperate ? (
        <Alert
          type="warning"
          showIcon
          icon={<ShieldAlert size={16} />}
          title="You don't have permission to record punches for others"
          description="Ask HR to grant access. The form is shown for reference but submissions won't go through."
        />
      ) : null}

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          accentCorner="tr"
          icon={ClipboardCheck}
          label="Add-only path"
          value="No edits / deletes"
          delta="Wrong punches go through HR / DBA off-band."
        />
        <StatCard
          variant="plain"
          tone="info"
          icon={FileText}
          label="Reason is required"
          value="Paper-trail note"
          delta='e.g. "missed checkout, paper form #12 / supervisor X"'
        />
        <StatCard
          variant="plain"
          tone="warning"
          icon={StickyNote}
          label="Bypasses WEB 2/day cap"
          value="Standard dedup still applies"
          delta="Same instant + employee = silently ignored."
        />
      </KpiGrid>

      <Section title="New punch">
        <Card styles={{ body: { padding: 18 } }}>
          <Can I="operator" a="attendance">
            <Form
              form={form}
              layout="vertical"
              initialValues={{
                date: dayjs(),
                time: dayjs().hour(9).minute(0).second(0),
              }}
              onFinish={(v) => {
                const punchedAt = toLocalDateTime(
                  v.date
                    .hour(v.time.hour())
                    .minute(v.time.minute())
                    .second(0)
                    .millisecond(0),
                );
                mutation.mutate({
                  employeeId: v.employeeId,
                  punchedAt,
                  reason: v.reason,
                });
              }}
            >
              <Space.Compact style={{ width: '100%' }}>
                <Form.Item
                  label="Card No"
                  name="employeeId"
                  rules={[{ required: true, message: 'Pick the employee' }]}
                  style={{ flex: 1 }}
                >
                  <EmployeePicker
                    placeholder="Search by card no or name"
                    style={{ width: '100%' }}
                  />
                </Form.Item>
                <Form.Item
                  label="Date"
                  name="date"
                  rules={[{ required: true }]}
                  style={{ flex: 1 }}
                >
                  <DatePicker
                    style={{ width: '100%' }}
                    format="DD MMM YYYY"
                    disabledDate={(d) => d && d.isAfter(dayjs(), 'day')}
                  />
                </Form.Item>
                <Form.Item
                  label="Time"
                  name="time"
                  rules={[{ required: true }]}
                  style={{ flex: 1 }}
                >
                  <TimePicker format="HH:mm" minuteStep={1} needConfirm={false} style={{ width: '100%' }} />
                </Form.Item>
              </Space.Compact>

              <Form.Item
                label="Reason (paper proof note)"
                name="reason"
                rules={[
                  { required: true, message: 'Reason is mandatory' },
                  { min: 4, message: 'Be specific — this is the audit trail' },
                ]}
              >
                <Input.TextArea
                  autoSize={{ minRows: 3, maxRows: 5 }}
                  placeholder="missed checkout, paper form #12 / approved by supervisor X"
                />
              </Form.Item>

              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={mutation.isPending}
                  disabled={!canOperate}
                >
                  Record punch
                </Button>
              </div>
            </Form>
          </Can>
        </Card>
      </Section>

      <Section
        title="Existing punches on the selected day"
        subtitle={
          employeeId && date
            ? `${formatDateShort(date.format('YYYY-MM-DD'))} · employee #${employeeId}`
            : 'Pick an employee + date to preview existing punches.'
        }
      >
        <Card styles={{ body: { padding: 18 } }}>
          {!employeeId || !date ? (
            <Text type="secondary">Fill the form to see context.</Text>
          ) : punchesQuery.isLoading ? (
            <Text type="secondary">Loading…</Text>
          ) : punchesQuery.isError ? (
            <Alert
              type="error"
              showIcon
              title="Couldn't load punches for this day"
              description={getErrorMessage(punchesQuery.error)}
            />
          ) : punches.length === 0 ? (
            <EmptyState
              size="md"
              icon={FileText}
              title="No punches yet"
              description="Adding the first one starts the day's record."
            />
          ) : (
            <PunchTimeline punches={punches} />
          )}
        </Card>
      </Section>
    </div>
  );
}

function PunchTimeline({ punches }: { punches: PunchDto[] }) {
  const sorted = punches
    .slice()
    .sort((a, b) => a.punchedAt.localeCompare(b.punchedAt));
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {sorted.map((p, i) => (
        <TimelineItem
          key={p.id}
          isLast={i === sorted.length - 1}
          tone={
            p.source === 'OPERATOR'
              ? 'warning'
              : p.source === 'REGULARIZED'
                ? 'info'
                : 'success'
          }
          title={
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <strong>{formatClock(p.punchedAt)}</strong>
              <Tag style={{ margin: 0, fontSize: 10 }}>
                {PUNCH_SOURCE_LABEL[p.source]}
              </Tag>
              {p.deviceId ? (
                <Text type="secondary" style={{ fontSize: 11 }}>
                  · {p.deviceId}
                </Text>
              ) : null}
              {p.source === 'OPERATOR' ? (
                <StatusPill tone="warning" size="sm">
                  Operator
                </StatusPill>
              ) : null}
            </span>
          }
          description={p.reason ?? undefined}
        />
      ))}
    </div>
  );
}

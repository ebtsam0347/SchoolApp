// Drawer for "apply for leave". Three live computations stitched together:
//   - context (types + balances + holidays) — one BFF call (T6)
//   - preview (working days, balance after, warnings) — debounced server
//     call so the date picker doesn't spam the backend
//   - submit — RFC7807 violations land in the form via applyApiErrorToForm
//
// Two modes:
//   - self mode (default): the caller applies for themselves — or, if they
//     manage someone (or hold the HR override), picks a direct report in the
//     "Apply for" field. attendance-service auto-approves the MANAGER stage
//     when a manager files for their own report.
//   - targeted mode (`onBehalfOf` prop): the caller already chose who the
//     request is for — Team requests opens the drawer straight from a roster
//     row. The target is fixed, shown read-only, and passed straight to the
//     mutation rather than through the form store: rc-field-form drops store
//     values whose Form.Item never mounted (`getFieldsValue()` reads
//     registered fields only), which previously let a request land against
//     the submitter while the drawer said otherwise.

import { useEffect } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  Radio,
  Select,
  Space,
  Tag,
  Typography,
  theme as antdTheme,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type Dayjs } from 'dayjs';
import {
  OnBehalfPicker,
  leavesApi,
  leavesBff,
  prettyLeaveCode,
  useCanSubmitOnBehalf,
  type CreateLeaveRequest,
  type LeaveBalanceDto,
  type LeavePreviewDto,
  type LeaveTypeDto,
} from '@/attendance';
import { handleFormError, useDebounced } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

type ApplyForm = {
  leaveTypeId: number;
  range: [Dayjs, Dayjs];
  halfDay: boolean;
  halfDayPart: 'AM' | 'PM' | null;
  reason: string;
  /** Self mode's "Apply for" picker. Undefined = myself. */
  onBehalfOf?: number;
};

const WARNING_LABELS: Record<string, string> = {
  NEGATIVE_BALANCE: 'This range exceeds your available balance. Shorten the dates to submit.',
  CROSSES_FY:
    'Range crosses the fiscal year. Balance projection assumes the current year only.',
};

/** Who a targeted-mode request is for. */
export type LeaveOnBehalfTarget = {
  id: number;
  fullName: string;
  employeeCode?: string | null;
};

export type ApplyLeaveDrawerProps = {
  open: boolean;
  onClose: () => void;
  /**
   * Fixed on-behalf target. Supplying it switches the drawer to targeted
   * mode: the picker is replaced by a read-only "applying for" banner and
   * this id is always what gets submitted.
   */
  onBehalfOf?: LeaveOnBehalfTarget | null;
};

export function ApplyLeaveDrawer({
  open,
  onClose,
  onBehalfOf,
}: ApplyLeaveDrawerProps) {
  const [form] = Form.useForm<ApplyForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const targeted = onBehalfOf != null;
  // Self mode only: a manager / HR picking who to file for.
  const onBehalf = useCanSubmitOnBehalf();
  const showPicker = !targeted && onBehalf.enabled;
  const pickedId = Form.useWatch('onBehalfOf', form);
  // Who the request is for, when not the caller. The picker's value is only
  // read while its Form.Item is mounted (`showPicker`), so a stale pick can't
  // leak into targeted mode.
  const targetId = onBehalfOf?.id ?? (showPicker ? (pickedId ?? null) : null);

  // `destroyOnHidden` unmounts the fields but not the form instance, and
  // rc-field-form preserves the store, so without this the previous row's
  // dates and reason would follow the next employee into the drawer.
  // Keyed on the fixed target only — a pick in the "Apply for" field must not
  // wipe what's been typed.
  const fixedTargetId = onBehalfOf?.id ?? null;
  useEffect(() => {
    if (open) form.resetFields();
  }, [open, fixedTargetId, form]);

  // Single BFF call (T6) for types + balances + next-90d holidays.
  const contextQuery = useQuery({
    queryKey: ['bff', 'leaves', 'apply-context'],
    queryFn: ({ signal }) => leavesBff.applyContext(signal),
    staleTime: 60_000,
    enabled: open,
  });

  const watchedTypeId = Form.useWatch('leaveTypeId', form);
  const watchedRange = Form.useWatch('range', form);
  const watchedHalfDay = Form.useWatch('halfDay', form);
  const watchedHalfDayPart = Form.useWatch('halfDayPart', form);

  const selectedType = contextQuery.data?.types.find((t) => t.id === watchedTypeId) ?? null;
  // Apply-context balances are caller-scoped — meaningless when applying on
  // behalf, so suppress the balance card in that case. Working-days math is
  // employee-agnostic and stays useful.
  const selectedBalance = targetId
    ? null
    : (contextQuery.data?.balances.find((b) => b.leaveTypeId === watchedTypeId) ?? null);

  // Server-side preview (T15). Same business rules submit enforces. Debounced
  // by 250ms so dragging the date picker doesn't spam /leaves/preview.
  const previewBody: CreateLeaveRequest | null =
    !watchedTypeId || !watchedRange?.[0] || !watchedRange?.[1]
      ? null
      : {
          leaveTypeId: watchedTypeId,
          fromDate: watchedRange[0].format('YYYY-MM-DD'),
          toDate: watchedRange[1].format('YYYY-MM-DD'),
          halfDay: !!watchedHalfDay,
          halfDayPart: watchedHalfDay ? (watchedHalfDayPart ?? 'AM') : undefined,
          // Sent so the projection follows the target the day the server
          // honours it. As of this writing /leaves/preview ignores it and
          // always answers for the caller — checked against the running
          // service, which returned an identical balanceAfter for self, a
          // colleague, and an employee in another department. That is why
          // balances and balance-derived warnings are hidden below whenever
          // the request is for someone else.
          onBehalfOf: targetId ?? undefined,
        };

  const debouncedBody = useDebounced(previewBody, 250);
  const previewQuery = useQuery({
    queryKey: ['leaves', 'preview', debouncedBody],
    queryFn: ({ signal }) => leavesApi.preview(debouncedBody!, signal),
    enabled: open && debouncedBody != null,
    staleTime: 5_000,
  });
  const preview = previewQuery.data;

  // Block FE submit when a paid leave would push the caller's balance below
  // zero. On-behalf submissions skip the check (caller's balance isn't the
  // target's) and unpaid types skip it (no balance to bust).
  const blocksOnNegativeBalance =
    !!preview &&
    !!selectedType?.paid &&
    !targetId &&
    preview.warnings.includes('NEGATIVE_BALANCE');

  const mutation = useMutation({
    mutationFn: (body: CreateLeaveRequest) => leavesApi.apply(body),
    onSuccess: (req) => {
      message.success(
        onBehalfOf
          ? `Leave submitted for ${onBehalfOf.fullName} — ${req.days} day${req.days === 1 ? '' : 's'} pending approval.`
          : targetId
            ? `Leave submitted for your team member — ${req.days} day${req.days === 1 ? '' : 's'} pending approval.`
            : `Leave submitted — ${req.days} day${req.days === 1 ? '' : 's'} pending approval.`,
      );
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <Drawer
      title="Apply for leave"
      open={open}
      onClose={onClose}
      size={480}
      destroyOnHidden
      footer={
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 'var(--app-space-sm)',
          }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            {selectedType?.requiresApproval === false
              ? 'Auto-approved on submit'
              : targeted
                ? // A request generator isn't an approver: the chain opens
                  // fully pending in the target's own manager's inbox.
                  'Routes through their manager → HR'
                : targetId
                  ? onBehalf.hasOverride
                    ? 'Auto-approved — HR override'
                    : 'Manager step auto-approved → routes to HR'
                  : 'Routes through your manager → HR'}
          </Text>
          <Space>
            <Button onClick={onClose}>Cancel</Button>
            <Button
              type="primary"
              loading={mutation.isPending}
              onClick={() => form.submit()}
              disabled={!preview || preview.workingDays === 0 || blocksOnNegativeBalance}
            >
              Submit
            </Button>
          </Space>
        </div>
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={(v) => {
          mutation.mutate({
            leaveTypeId: v.leaveTypeId,
            fromDate: v.range[0].format('YYYY-MM-DD'),
            toDate: v.range[1].format('YYYY-MM-DD'),
            halfDay: v.halfDay,
            halfDayPart: v.halfDay ? (v.halfDayPart ?? 'AM') : undefined,
            reason: v.reason,
            // Targeted mode never round-trips through the form store — see
            // the note at the top of this file.
            onBehalfOf: targetId ?? undefined,
          });
        }}
        initialValues={{ halfDay: false, halfDayPart: 'AM' }}
      >
        {onBehalfOf ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 'var(--app-space-md)' }}
            title={`Applying for ${onBehalfOf.fullName}${
              onBehalfOf.employeeCode ? ` · ${onBehalfOf.employeeCode}` : ''
            }`}
            description="Close this drawer and pick another row to file for someone else."
          />
        ) : null}

        {showPicker ? (
          <Form.Item
            label="Apply for"
            name="onBehalfOf"
            extra={
              onBehalf.hasOverride
                ? 'Submitting for someone else uses your HR override — the chain is auto-approved.'
                : 'Submitting for a direct report skips your approval step.'
            }
          >
            <OnBehalfPicker />
          </Form.Item>
        ) : null}

        <Form.Item
          label="Leave type"
          name="leaveTypeId"
          rules={[{ required: true, message: 'Pick a leave type' }]}
        >
          <Select
            placeholder="Select type"
            loading={contextQuery.isLoading}
            options={(contextQuery.data?.types ?? [])
              .filter((t) => t.active)
              .map((t) => ({
                value: t.id,
                label: (
                  <span
                    style={{
                      display: 'inline-flex',
                      justifyContent: 'space-between',
                      width: '100%',
                    }}
                  >
                    <span>{t.name}</span>
                    {!t.paid ? <Tag style={{ marginLeft: 8 }}>Unpaid</Tag> : null}
                  </span>
                ),
              }))}
          />
        </Form.Item>

        <Form.Item
          label="Date range"
          name="range"
          rules={[{ required: true, message: 'Pick the dates you need off' }]}
        >
          <DatePicker.RangePicker
            style={{ width: '100%' }}
            format="DD MMM YYYY"
          />
        </Form.Item>

        <Form.Item label="Half day?" name="halfDay" valuePropName="checked">
          <Radio.Group
            optionType="button"
            options={[
              { value: false, label: 'Full day(s)' },
              { value: true, label: 'Half day' },
            ]}
            onChange={(e) => form.setFieldValue('halfDay', e.target.value)}
          />
        </Form.Item>
        {watchedHalfDay ? (
          <Form.Item
            label="Which half?"
            name="halfDayPart"
            rules={[{ required: true, message: 'Pick AM or PM' }]}
          >
            <Radio.Group
              optionType="button"
              options={[
                { value: 'AM', label: 'Morning (AM)' },
                { value: 'PM', label: 'Afternoon (PM)' },
              ]}
            />
          </Form.Item>
        ) : null}

        <Form.Item label="Reason" name="reason">
          <Input.TextArea
            autoSize={{ minRows: 3, maxRows: 5 }}
            placeholder={
              targetId
                ? 'Optional — their manager will see this on the approval row.'
                : 'Optional — your manager will see this on the approval row.'
            }
          />
        </Form.Item>

        <ApplyPreview
          preview={preview}
          balance={selectedBalance}
          type={selectedType}
          loading={previewQuery.isFetching && !preview}
          onBehalfOfSomeoneElse={!!targetId}
        />
      </Form>
    </Drawer>
  );
}

function ApplyPreview({
  preview,
  balance,
  type,
  loading,
  onBehalfOfSomeoneElse,
}: {
  preview: LeavePreviewDto | undefined;
  balance: LeaveBalanceDto | null;
  type: LeaveTypeDto | null;
  loading: boolean;
  onBehalfOfSomeoneElse: boolean;
}) {
  const { token } = useToken();

  if (loading) {
    return (
      <Card
        size="small"
        styles={{ body: { padding: 12 } }}
        style={{ background: token.colorFillQuaternary }}
      >
        <Text type="secondary" style={{ fontSize: 12 }}>
          Computing preview…
        </Text>
      </Card>
    );
  }

  if (!preview) return null;

  if (preview.workingDays === 0) {
    return (
      <Alert
        type="warning"
        showIcon
        title="No working days in this range"
        description="Sundays and holidays don't count as leave. Pick at least one weekday."
      />
    );
  }

  const negativeWarning =
    preview.warnings.includes('NEGATIVE_BALANCE') && type?.paid && !onBehalfOfSomeoneElse;
  // Balance + overlap warnings are caller-scoped on the server, so they're
  // misleading when submitting on behalf of someone else. Show only the
  // employee-agnostic warnings (LEAVE_RANGE_EMPTY, CROSSES_FY).
  const visibleWarnings = onBehalfOfSomeoneElse
    ? preview.warnings.filter((w) => w !== 'NEGATIVE_BALANCE' && w !== 'OVERLAPS_EXISTING')
    : preview.warnings;

  return (
    <Card
      size="small"
      style={{
        background: token.colorFillQuaternary,
        border: `1px solid ${token.colorBorderSecondary}`,
      }}
      styles={{ body: { padding: 12 } }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-sm)' }}>
        <PreviewRow
          label="Working days"
          value={`${preview.workingDays} day${preview.workingDays === 1 ? '' : 's'}`}
        />
        {preview.sundaysSkipped > 0 ? (
          <PreviewRow
            label="Sundays skipped"
            value={`${preview.sundaysSkipped}`}
            tone="muted"
          />
        ) : null}
        {preview.holidaysSkipped > 0 ? (
          <PreviewRow
            label="Holidays skipped"
            value={`${preview.holidaysSkipped}`}
            tone="muted"
          />
        ) : null}
        {balance && preview.balanceAfter != null ? (
          <PreviewRow
            label={`Balance after (${prettyLeaveCode(balance.leaveTypeCode)})`}
            value={`${preview.balanceAfter.toFixed(1)} day${preview.balanceAfter === 1 ? '' : 's'}`}
            tone={negativeWarning ? 'error' : 'default'}
          />
        ) : null}
        {onBehalfOfSomeoneElse ? (
          <Text type="secondary" style={{ fontSize: 11 }}>
            Balance isn't shown when filing for someone else — this projection
            is computed against your own entitlement, not theirs. Their manager
            sees the real balance on the approval row.
          </Text>
        ) : null}
        {visibleWarnings.map((w) => (
          <Alert
            key={w}
            type={w === 'NEGATIVE_BALANCE' ? 'error' : 'warning'}
            showIcon
            title={WARNING_LABELS[w] ?? w}
          />
        ))}
      </div>
    </Card>
  );
}

function PreviewRow({
  label,
  value,
  tone = 'default',
}: {
  label: string;
  value: string;
  tone?: 'default' | 'muted' | 'error';
}) {
  const { token } = useToken();
  const color =
    tone === 'error'
      ? token.colorError
      : tone === 'muted'
        ? token.colorTextTertiary
        : token.colorText;
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
      <span style={{ color: token.colorTextSecondary }}>{label}</span>
      <strong style={{ color }}>{value}</strong>
    </div>
  );
}

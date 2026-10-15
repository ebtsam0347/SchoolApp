// Drawer for creating a new delegation. HR with attendance.delegations.admin
// can flip the "On behalf of" toggle in the footer to delegate from a
// manager who isn't the caller — the toggle swaps the endpoint between
// `delegationsApi.create` and `delegationsApi.createOnBehalf`.

import { useEffect, useState } from 'react';
import {
  App as AntdApp,
  Card,
  Checkbox,
  DatePicker,
  Form,
  Input,
  Switch,
  theme as antdTheme,
  Typography,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import {
  ALL_DELEGATION_GRANTS,
  DELEGATION_GRANT_LABEL,
  delegationsApi,
  delegationsBff,
  formatDateShort,
  type CreateDelegationOnBehalfRequest,
  type CreateDelegationRequest,
  type DelegationGrant,
  type RequestType,
} from '@/attendance';
import { Can, useAuth } from '@/auth';
import { DrawerForm } from '@/components';
import { handleFormError, useDebounced } from '@/lib';
import { CandidatePicker } from './CandidatePicker';
import { ALL_REQUEST_TYPES, REQUEST_TYPE_LABEL, type DelegationRequestType } from './request-types';

const { Text } = Typography;
const { useToken } = antdTheme;

type CreateForm = {
  delegateId: number;
  grants: DelegationGrant[];
  requestTypes: DelegationRequestType[];
  range: [Dayjs, Dayjs | null];
  openEnded: boolean;
  reason?: string;
  onBehalfOf?: number;
};

const GRANT_HELP: Record<DelegationGrant, string> = {
  APPROVE: "Your pending approvals show up in their inbox and they can decide them.",
  SUBMIT: 'They can file requests for your people — your department and direct reports.',
};

export type DelegationCreateDrawerProps = {
  open: boolean;
  onClose: () => void;
};

export function DelegationCreateDrawer({ open, onClose }: DelegationCreateDrawerProps) {
  const auth = useAuth();
  const [form] = Form.useForm<CreateForm>();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();

  const canOverride = !!auth.user?.permissions.includes('attendance.delegations.admin');
  const [onBehalfMode, setOnBehalfMode] = useState(false);

  const [search, setSearch] = useState('');
  const debounced = useDebounced(search, 200);
  const candidatesQuery = useQuery({
    queryKey: ['bff', 'delegations', 'candidates', debounced],
    queryFn: ({ signal }) =>
      delegationsBff.candidates({ q: debounced || undefined, limit: 30 }, signal),
    enabled: open,
    staleTime: 30_000,
  });

  // Reset state on close so the drawer doesn't carry stale selections.
  useEffect(() => {
    if (!open) {
      form.resetFields();
      setOnBehalfMode(false);
      setSearch('');
    }
  }, [open, form]);

  const selfMutation = useMutation({
    mutationFn: (body: CreateDelegationRequest) => delegationsApi.create(body),
    onSuccess: () => {
      message.success('Delegation created');
      queryClient.invalidateQueries({ queryKey: ['delegations'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const onBehalfMutation = useMutation({
    mutationFn: (body: CreateDelegationOnBehalfRequest) =>
      delegationsApi.createOnBehalf(body),
    onSuccess: () => {
      message.success('Delegation created on behalf of manager');
      queryClient.invalidateQueries({ queryKey: ['delegations'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const submitting = selfMutation.isPending || onBehalfMutation.isPending;
  const watchedRange = Form.useWatch('range', form);
  const watchedOpenEnded = Form.useWatch('openEnded', form);
  const watchedReason = Form.useWatch('reason', form);

  return (
    <DrawerForm<CreateForm>
      title="New delegation"
      open={open}
      onClose={onClose}
      size={520}
      form={form}
      submitting={submitting}
      submitText="Create"
      initialValues={{
        grants: ['APPROVE'] satisfies DelegationGrant[],
        requestTypes: ALL_REQUEST_TYPES,
        openEnded: false,
      }}
      onFinish={(v) => {
        const fromDate = v.range[0].format('YYYY-MM-DD');
        const toDate =
          v.openEnded || !v.range[1] ? undefined : v.range[1].format('YYYY-MM-DD');
        // Cast past the generated `RequestType` union — the backend only
        // recognizes LEAVE/SHORT_LEAVE/OVERTIME/REGULARIZATION today, but the
        // checkbox list also covers Requisitions/Advances/Travel so the UI is
        // ready once commons-approval grows registrations for those queues.
        const requestTypes =
          v.requestTypes.length === ALL_REQUEST_TYPES.length
            ? undefined
            : (v.requestTypes as unknown as RequestType[]);

        if (onBehalfMode) {
          if (!v.onBehalfOf) {
            form.setFields([{ name: 'onBehalfOf', errors: ['Pick the delegator'] }]);
            return;
          }
          if (!v.reason || v.reason.trim().length < 4) {
            form.setFields([
              { name: 'reason', errors: ['Reason is required for HR override'] },
            ]);
            return;
          }
          onBehalfMutation.mutate({
            delegatorId: v.onBehalfOf,
            delegateId: v.delegateId,
            requestTypes,
            grants: v.grants,
            fromDate,
            toDate,
            reason: v.reason,
          });
        } else {
          selfMutation.mutate({
            delegateId: v.delegateId,
            requestTypes,
            grants: v.grants,
            fromDate,
            toDate,
            reason: v.reason,
          });
        }
      }}
      footerExtra={
        canOverride ? (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Switch
              checked={onBehalfMode}
              onChange={setOnBehalfMode}
              size="small"
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              On behalf of a manager (HR override)
            </Text>
          </span>
        ) : null
      }
    >
        {onBehalfMode ? (
          <Can I="override" a="attendance.approval">
            <Form.Item
              label="Delegator (manager being covered)"
              name="onBehalfOf"
              rules={[{ required: true, message: 'Pick the manager' }]}
            >
              <CandidatePicker
                placeholder="Search by name / employee code"
                onSearch={setSearch}
                candidates={candidatesQuery.data?.users ?? []}
                loading={candidatesQuery.isFetching}
              />
            </Form.Item>
          </Can>
        ) : null}

        {/* First question, before who: the two grants do different things and
            picking neither is meaningless, so it can't sit further down. */}
        <Form.Item
          label="What are you handing over?"
          name="grants"
          rules={[{ required: true, message: 'Pick at least one' }]}
        >
          <Checkbox.Group style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {ALL_DELEGATION_GRANTS.map((g) => (
              <Checkbox key={g} value={g}>
                {DELEGATION_GRANT_LABEL[g]}
                <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                  {GRANT_HELP[g]}
                </Text>
              </Checkbox>
            ))}
          </Checkbox.Group>
        </Form.Item>

        <Form.Item
          label="Delegate (who acts on the manager's behalf)"
          name="delegateId"
          rules={[{ required: true, message: 'Pick the delegate' }]}
        >
          <CandidatePicker
            placeholder="Search by name / employee code"
            onSearch={setSearch}
            candidates={candidatesQuery.data?.users ?? []}
            loading={candidatesQuery.isFetching}
          />
        </Form.Item>

        <Form.Item
          label="Request types"
          name="requestTypes"
          rules={[{ required: true, message: 'Select at least one type' }]}
        >
          <Checkbox.Group
            options={ALL_REQUEST_TYPES.map((t) => ({
              value: t,
              label: REQUEST_TYPE_LABEL[t],
            }))}
          />
        </Form.Item>

        <Form.Item
          label="Range"
          name="range"
          rules={[{ required: true, message: 'Pick a from date' }]}
        >
          <DatePicker.RangePicker
            style={{ width: '100%' }}
            format="DD MMM YYYY"
            allowEmpty={[false, true]}
            disabled={[false, !!watchedOpenEnded]}
          />
        </Form.Item>

        <Form.Item
          name="openEnded"
          valuePropName="checked"
          style={{ marginTop: -12 }}
          help="Open-ended delegations stay active until you revoke them."
        >
          <Checkbox>Open-ended (no end date)</Checkbox>
        </Form.Item>

        <Form.Item
          label={onBehalfMode ? 'Reason (required)' : 'Reason'}
          name="reason"
          rules={
            onBehalfMode
              ? [
                  {
                    required: true,
                    min: 4,
                    message: 'Reason is required for HR override',
                  },
                ]
              : []
          }
        >
          <Input.TextArea
            autoSize={{ minRows: 2, maxRows: 4 }}
            placeholder={
              onBehalfMode
                ? 'Manager unreachable; HR is covering for the trip.'
                : 'Optional — keeps the audit trail readable.'
            }
          />
        </Form.Item>

      <PreviewCallout
        range={watchedRange}
        openEnded={!!watchedOpenEnded}
        reason={watchedReason}
      />
    </DrawerForm>
  );
}

function PreviewCallout({
  range,
  openEnded,
  reason,
}: {
  range: CreateForm['range'] | undefined;
  openEnded: boolean;
  reason: string | undefined;
}) {
  const { token } = useToken();
  if (!range?.[0]) return null;
  return (
    <Card
      size="small"
      style={{
        background: token.colorFillQuaternary,
        border: `1px solid ${token.colorBorderSecondary}`,
      }}
      styles={{ body: { padding: 12, fontSize: 12 } }}
    >
      <strong>You're delegating</strong> from{' '}
      <strong>{formatDateShort(range[0].format('YYYY-MM-DD'))}</strong>{' '}
      {openEnded || !range[1] ? (
        <>until you revoke.</>
      ) : (
        <>
          through <strong>{formatDateShort(range[1].format('YYYY-MM-DD'))}</strong>.
        </>
      )}
      {reason ? (
        <div style={{ marginTop: 6, color: token.colorTextSecondary }}>“{reason}”</div>
      ) : null}
    </Card>
  );
}

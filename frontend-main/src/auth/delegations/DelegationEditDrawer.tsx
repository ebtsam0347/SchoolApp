// Edit drawer — only end date and reason are mutable post-creation. The
// delegator/delegate identity and request types are immutable to keep
// resolution semantics simple (revoke + recreate to change scope).

import { useEffect } from 'react';
import {
  App as AntdApp,
  Card,
  Checkbox,
  DatePicker,
  Form,
  Input,
  Typography,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import {
  delegationsApi,
  formatDateShort,
  toIsoDate,
  type DelegationDto,
  type UpdateDelegationRequest,
} from '@/attendance';
import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';

const { Text } = Typography;

export type DelegationEditDrawerProps = {
  delegation: DelegationDto | null;
  onClose: () => void;
};

type EditForm = { toDate: Dayjs | null; openEnded: boolean; reason?: string };

export function DelegationEditDrawer({
  delegation,
  onClose,
}: DelegationEditDrawerProps) {
  const [form] = Form.useForm<EditForm>();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const open = !!delegation;

  useEffect(() => {
    if (delegation) {
      form.setFieldsValue({
        toDate: delegation.toDate ? dayjs(delegation.toDate) : null,
        openEnded: delegation.toDate == null,
        reason: delegation.reason ?? undefined,
      });
    }
  }, [delegation, form]);

  const mutation = useMutation({
    mutationFn: (body: UpdateDelegationRequest) =>
      delegationsApi.update(delegation?.id ?? 0, body),
    onSuccess: () => {
      message.success('Delegation updated');
      queryClient.invalidateQueries({ queryKey: ['delegations'] });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const watchedOpenEnded = Form.useWatch('openEnded', form);
  const today = toIsoDate(new Date());

  if (!delegation) {
    // No selection — render an empty drawer just to keep mounting stable.
    return (
      <DrawerForm
        open={false}
        onClose={onClose}
        title="Delegation"
        size={420}
        hideSubmit
      >
        <></>
      </DrawerForm>
    );
  }

  return (
    <DrawerForm<EditForm>
      title={`Delegation #${delegation.id}`}
      open={open}
      onClose={onClose}
      size={420}
      form={form}
      submitting={mutation.isPending}
      submitText="Save changes"
      onFinish={(v) =>
        mutation.mutate({
          toDate: v.openEnded ? null : v.toDate?.format('YYYY-MM-DD'),
          reason: v.reason,
        })
      }
    >
      <Card
        size="small"
        styles={{ body: { padding: 12, fontSize: 12, marginBottom: 16 } }}
        style={{ background: 'transparent' }}
      >
        <div>
          Created <strong>{formatDateShort(delegation.fromDate)}</strong>
          {delegation.toDate ? (
            <>
              {' → '}
              <strong>{formatDateShort(delegation.toDate)}</strong>
            </>
          ) : (
            <> · open-ended</>
          )}
        </div>
        <Text type="secondary">
          Effective {delegation.fromDate <= today ? 'now' : 'in the future'}.
        </Text>
      </Card>
      <Form.Item name="openEnded" valuePropName="checked">
        <Checkbox>Open-ended (clear end date)</Checkbox>
      </Form.Item>
      <Form.Item
        label="End date"
        name="toDate"
        rules={
          watchedOpenEnded ? [] : [{ required: true, message: 'Pick an end date' }]
        }
      >
        <DatePicker
          style={{ width: '100%' }}
          format="DD MMM YYYY"
          disabled={!!watchedOpenEnded}
        />
      </Form.Item>
      <Form.Item label="Reason" name="reason">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="Optional context — shows on the row."
        />
      </Form.Item>
    </DrawerForm>
  );
}

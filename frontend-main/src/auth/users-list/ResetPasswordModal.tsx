// "Set temporary password" admin override. Same fieldMap trick as the
// create flow — backend reports password-policy errors on `newPassword`
// while the form input is `temporaryPassword`.

import { Alert, App, Form, Input, Modal } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { adminUsersApi, type UserDto } from '@/auth';
import { handleFormError } from '@/lib';

export type ResetPasswordModalProps = {
  target: UserDto | null;
  onClose: () => void;
  onDone: () => void;
};

export function ResetPasswordModal({
  target,
  onClose,
  onDone,
}: ResetPasswordModalProps) {
  const [form] = Form.useForm<{ temporaryPassword: string }>();
  const { message } = App.useApp();
  const resetMutation = useMutation({
    mutationFn: ({
      id,
      temporaryPassword,
    }: {
      id: number;
      temporaryPassword: string;
    }) => adminUsersApi.resetPassword(id, { temporaryPassword }),
    onSuccess: () => {
      form.resetFields();
      onDone();
    },
    onError: (e) =>
      handleFormError(form, e, message, {
        fieldMap: { newPassword: 'temporaryPassword' },
      }),
  });

  return (
    <Modal
      open={!!target}
      title={target ? `Reset password for ${target.username}` : 'Reset password'}
      onCancel={() => {
        if (!resetMutation.isPending) {
          form.resetFields();
          onClose();
        }
      }}
      okText="Set temporary password"
      okButtonProps={{ danger: true }}
      confirmLoading={resetMutation.isPending}
      onOk={() => form.submit()}
      destroyOnHidden
    >
      <Form
        layout="vertical"
        form={form}
        validateTrigger={['onChange', 'onBlur']}
        onFinish={(values) =>
          target &&
          resetMutation.mutate({
            id: target.id,
            temporaryPassword: values.temporaryPassword,
          })
        }
      >
        <Alert
          type="warning"
          showIcon
          title="Share this password securely"
          description="Send it to the user in person or over a trusted channel — they'll be required to change it at next sign‑in."
          style={{ marginBottom: 'var(--app-space-md)' }}
        />
        <Form.Item
          label="Temporary password"
          name="temporaryPassword"
          rules={[{ required: true, message: 'Required' }]}
        >
          <Input.Password placeholder="e.g. Reset!2026" autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

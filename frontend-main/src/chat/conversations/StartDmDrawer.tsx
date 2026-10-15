// "New direct message" drawer. Picks one user, calls /chat/conversations/dm
// (idempotent get-or-create), and navigates to the resulting conversation.

import { useMutation } from '@tanstack/react-query';
import { App, Form } from 'antd';
import { useNavigate } from '@tanstack/react-router';
import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';
import { useAuth } from '@/auth';
import { conversationsApi } from '../api';
import { UserPicker } from '../UserPicker';

export type StartDmDrawerProps = {
  open: boolean;
  onClose: () => void;
};

type FormValues = { userId: number };

export function StartDmDrawer({ open, onClose }: StartDmDrawerProps) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const auth = useAuth();
  const [form] = Form.useForm<FormValues>();

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      conversationsApi.createDm({ otherUserId: values.userId }),
    onSuccess: (conv) => {
      onClose();
      form.resetFields();
      navigate({ to: '/chat/$id', params: { id: String(conv.id) } });
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm
      title="New direct message"
      open={open}
      onClose={onClose}
      form={form}
      onFinish={mutation.mutate}
      submitting={mutation.isPending}
      submitText="Start chat"
    >
      <Form.Item
        name="userId"
        label="Recipient"
        rules={[{ required: true, message: 'Pick a person to message' }]}
      >
        <UserPicker
          excludeUserIds={auth.user?.userId != null ? [auth.user.userId] : []}
        />
      </Form.Item>
    </DrawerForm>
  );
}


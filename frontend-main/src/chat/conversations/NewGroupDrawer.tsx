// "New group" drawer. Title + member multi-pick → POST /chat/conversations/group.
// Caller becomes OWNER (server-enforced).

import { useMutation } from '@tanstack/react-query';
import { App, Form, Input } from 'antd';
import { useNavigate } from '@tanstack/react-router';
import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';
import { useAuth } from '@/auth';
import { conversationsApi } from '../api';
import { UserPicker } from '../UserPicker';

export type NewGroupDrawerProps = {
  open: boolean;
  onClose: () => void;
};

type FormValues = {
  title: string;
  memberUserIds: number[];
};

export function NewGroupDrawer({ open, onClose }: NewGroupDrawerProps) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const auth = useAuth();
  const [form] = Form.useForm<FormValues>();

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      conversationsApi.createGroup({
        title: values.title.trim(),
        memberUserIds: values.memberUserIds,
      }),
    onSuccess: (conv) => {
      onClose();
      form.resetFields();
      navigate({ to: '/chat/$id', params: { id: String(conv.id) } });
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm
      title="New group"
      open={open}
      onClose={onClose}
      form={form}
      onFinish={mutation.mutate}
      submitting={mutation.isPending}
      submitText="Create group"
    >
      <Form.Item
        name="title"
        label="Group title"
        rules={[{ required: true, message: 'Give the group a title' }]}
      >
        <Input maxLength={200} placeholder="e.g. Sourcing — FY26 Q1" />
      </Form.Item>
      <Form.Item
        name="memberUserIds"
        label="Members"
        rules={[
          {
            required: true,
            type: 'array',
            min: 1,
            message: 'Pick at least one member',
          },
        ]}
      >
        <UserPicker
          multiple
          excludeUserIds={auth.user?.userId != null ? [auth.user.userId] : []}
        />
      </Form.Item>
    </DrawerForm>
  );
}

// Identity panel: read-only tiles by default, or an inline edit form when
// "Edit" is toggled. Name is read-only when the user is employee-linked
// because it's mastered on the employee record.
//
// The edit UI is its own component so `Form.useForm` is only invoked while
// the form is actually mounted — otherwise AntD warns "Instance created by
// useForm is not connected to any Form element".

import { useState } from 'react';
import { App, Button, Form, Input, Space } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Section } from '@/components';
import { adminUsersApi, type UserDto } from '@/auth';
import { handleFormError } from '@/lib';
import { IdentityTiles } from './IdentityTiles';
import { overviewKey } from './query-keys';

export type IdentityPanelProps = {
  userId: number;
  user: UserDto;
};

export function IdentityPanel({ userId, user }: IdentityPanelProps) {
  const [editing, setEditing] = useState(false);

  if (editing) {
    return (
      <IdentityEditPanel
        userId={userId}
        user={user}
        onDone={() => setEditing(false)}
      />
    );
  }

  return (
    <Section
      title="Identity"
      subtitle={
        user.employeeLinked
          ? 'Name is managed on the employee record — edit email here.'
          : undefined
      }
      actions={<Button onClick={() => setEditing(true)}>Edit</Button>}
    >
      <IdentityTiles user={user} />
    </Section>
  );
}

function IdentityEditPanel({
  userId,
  user,
  onDone,
}: {
  userId: number;
  user: UserDto;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const [form] = Form.useForm<{ fullName: string; email: string | null }>();

  const updateMutation = useMutation({
    mutationFn: (body: { fullName?: string; email?: string | null }) =>
      adminUsersApi.update(userId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: overviewKey(userId) });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      message.success('User updated.');
      onDone();
    },
    onError: (e) => handleFormError(form, e, message),
  });

  const linkedReadOnly = user.employeeLinked;

  return (
    <Section
      title="Identity"
      subtitle={
        linkedReadOnly
          ? 'Name is managed on the employee record — edit email here.'
          : undefined
      }
      actions={
        <Space>
          <Button onClick={onDone}>Cancel</Button>
          <Button
            type="primary"
            onClick={() => form.submit()}
            loading={updateMutation.isPending}
          >
            Save
          </Button>
        </Space>
      }
    >
      <Form
        layout="vertical"
        form={form}
        initialValues={{ fullName: user.fullName, email: user.email ?? '' }}
        onFinish={(values) => {
          const body: { fullName?: string; email?: string | null } = {};
          if (!linkedReadOnly && values.fullName !== user.fullName) {
            body.fullName = values.fullName;
          }
          const newEmail = values.email?.trim() || null;
          if (newEmail !== (user.email ?? null)) body.email = newEmail;
          if (Object.keys(body).length === 0) {
            message.info('Nothing to save.');
            onDone();
            return;
          }
          updateMutation.mutate(body);
        }}
      >
        <Form.Item label="Full name" name="fullName" rules={[{ required: true }]}>
          <Input disabled={linkedReadOnly} />
        </Form.Item>
        <Form.Item label="Email" name="email" rules={[{ type: 'email' }]}>
          <Input placeholder="optional" />
        </Form.Item>
      </Form>
    </Section>
  );
}

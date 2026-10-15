// Modal for creating a new user. The auth-service password-policy
// violations always come back on field `newPassword`, even when this
// DTO calls it `temporaryPassword` — the field map remaps so inline
// errors render correctly.

import { App, Form, Input, InputNumber, Modal, Select } from 'antd';
import { useMutation } from '@tanstack/react-query';
import {
  adminUsersApi,
  prettyRoleCode,
  type CreateUserRequest,
  type RoleDto,
  type UserDto,
} from '@/auth';
import { handleFormError } from '@/lib';

type CreateUserFormValues = {
  username: string;
  fullName: string;
  email?: string;
  temporaryPassword: string;
  roles?: string[];
  employeeId?: number | null;
};

export type CreateUserModalProps = {
  open: boolean;
  roles: RoleDto[];
  onClose: () => void;
  onCreated: (user: UserDto) => void;
};

export function CreateUserModal({
  open,
  roles,
  onClose,
  onCreated,
}: CreateUserModalProps) {
  const [form] = Form.useForm<CreateUserFormValues>();
  const { message } = App.useApp();

  const createMutation = useMutation({
    mutationFn: (body: CreateUserRequest) => adminUsersApi.create(body),
    onSuccess: (user) => {
      form.resetFields();
      onCreated(user);
    },
    onError: (e) =>
      handleFormError(form, e, message, {
        fieldMap: { newPassword: 'temporaryPassword' },
      }),
  });

  return (
    <Modal
      open={open}
      title="Create user"
      onCancel={() => {
        if (!createMutation.isPending) {
          form.resetFields();
          onClose();
        }
      }}
      okText="Create"
      onOk={() => form.submit()}
      confirmLoading={createMutation.isPending}
      destroyOnHidden
      width={520}
    >
      <Form
        layout="vertical"
        form={form}
        validateTrigger={['onChange', 'onBlur']}
        onFinish={(values) => {
          // auth-service declares `employeeLinked` as a primitive `boolean`,
          // so it cannot accept null and must be sent. We derive it from
          // whether an employeeId was supplied; the backend treats them as
          // a pair anyway. We also strip empty strings/null so Jackson
          // never sees null on a non-nullable field (e.g. an empty email
          // field stays absent from the payload, not "" or null).
          const trimmedEmail = values.email?.trim();
          const employeeId = values.employeeId ?? undefined;
          const body: CreateUserRequest = {
            username: values.username,
            fullName: values.fullName,
            temporaryPassword: values.temporaryPassword,
            employeeLinked: employeeId !== undefined,
          };
          if (trimmedEmail) body.email = trimmedEmail;
          if (employeeId !== undefined) body.employeeId = employeeId;
          if (values.roles && values.roles.length > 0) body.roles = values.roles;
          createMutation.mutate(body);
        }}
      >
        <Form.Item
          label="Username"
          name="username"
          rules={[{ required: true, message: 'Required' }, { max: 64 }]}
          extra="Permanent. By convention this is the employee code."
        >
          <Input placeholder="e.g. ahmed.raza" autoComplete="off" autoFocus />
        </Form.Item>
        <Form.Item
          label="Full name"
          name="fullName"
          rules={[{ required: true, message: 'Required' }, { max: 128 }]}
        >
          <Input placeholder="e.g. Ahmed Raza" />
        </Form.Item>
        <Form.Item
          label="Email"
          name="email"
          rules={[{ type: 'email', message: 'Must be a valid email' }, { max: 255 }]}
        >
          <Input placeholder="optional" autoComplete="off" />
        </Form.Item>
        <Form.Item
          label="Temporary password"
          name="temporaryPassword"
          rules={[{ required: true, message: 'Required' }]}
          extra="The user must change this on first login."
        >
          <Input.Password placeholder="e.g. Welcome!2026" autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          label="Roles"
          name="roles"
          extra="Leave empty if the user shouldn't have any features yet — you can add roles later."
        >
          <Select
            mode="multiple"
            placeholder="Select roles"
            options={roles
              .filter((r) => !r.serviceAccount)
              .map((r) => ({
                value: r.code,
                label: `${prettyRoleCode(r.code)} — ${r.name}`,
              }))}
            notFoundContent={roles.length === 0 ? 'Loading roles…' : 'No matches'}
          />
        </Form.Item>
        <Form.Item
          label="Employee id"
          name="employeeId"
          extra="Link this user to an employee record. Leave blank for a standalone account."
        >
          <InputNumber style={{ width: '100%' }} placeholder="e.g. 1042" min={1} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

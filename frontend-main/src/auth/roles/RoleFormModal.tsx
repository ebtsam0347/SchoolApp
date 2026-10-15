// Create / edit role modal. Same form for both flows; the only
// difference is whether the `code` input is editable. RFC7807 violations
// land on the form via applyApiErrorToForm.

import { App, Form, Input, Modal, Select } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  rolesApi,
  type CreateRoleRequest,
  type PermissionDto,
  type RoleDto,
  type UpdateRoleRequest,
} from '@/auth';
import { handleFormError } from '@/lib';

type RoleFormValues = {
  code: string;
  name: string;
  description?: string;
  permissions: string[];
};

export type RoleFormModalProps = {
  open: boolean;
  role?: RoleDto;
  permissions: PermissionDto[];
  onClose: () => void;
};

export function RoleFormModal({
  open,
  role,
  permissions,
  onClose,
}: RoleFormModalProps) {
  const isEdit = !!role;
  const [form] = Form.useForm<RoleFormValues>();
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (body: CreateRoleRequest) => rolesApi.create(body),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] });
      message.success(`Created role ${created.code}.`);
      form.resetFields();
      onClose();
    },
    onError: (e) => handleFormError(form, e, message),
  });

  const updateMutation = useMutation({
    mutationFn: (body: UpdateRoleRequest) => rolesApi.update(role?.id ?? 0, body),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] });
      message.success(`Saved role ${updated.code}.`);
      onClose();
    },
    onError: (e) => handleFormError(form, e, message),
  });

  const submitting = createMutation.isPending || updateMutation.isPending;

  return (
    <Modal
      open={open}
      destroyOnHidden
      title={isEdit ? `Edit role ${role!.code}` : 'New role'}
      okText={isEdit ? 'Save' : 'Create'}
      confirmLoading={submitting}
      onCancel={() => {
        if (!submitting) {
          form.resetFields();
          onClose();
        }
      }}
      onOk={() => form.submit()}
      width={640}
    >
      <Form
        layout="vertical"
        form={form}
        initialValues={
          role
            ? {
                code: role.code,
                name: role.name,
                description: role.description ?? '',
                permissions: role.permissions,
              }
            : { permissions: [] }
        }
        onFinish={(values) => {
          if (isEdit) {
            const body: UpdateRoleRequest = {
              name: values.name,
              description: values.description?.trim() || null,
              permissions: values.permissions,
            };
            updateMutation.mutate(body);
          } else {
            const body: CreateRoleRequest = {
              code: values.code.trim().toUpperCase(),
              name: values.name,
              description: values.description?.trim() || null,
              permissions: values.permissions,
            };
            createMutation.mutate(body);
          }
        }}
      >
        <Form.Item
          label="Code"
          name="code"
          rules={[
            { required: true, message: 'Required' },
            {
              pattern: /^[A-Z][A-Z0-9_]{1,31}$/,
              message: 'Use uppercase letters and underscores (e.g. PAYROLL_READ_ONLY).',
            },
          ]}
          extra="UPPER_SNAKE_CASE. Permanent — cannot be changed once set."
        >
          <Input
            placeholder="e.g. PAYROLL_READ_ONLY"
            disabled={isEdit}
            autoComplete="off"
            autoFocus={!isEdit}
          />
        </Form.Item>
        <Form.Item label="Name" name="name" rules={[{ required: true, message: 'Required' }]}>
          <Input placeholder="e.g. Payroll Read-Only" autoFocus={isEdit} />
        </Form.Item>
        <Form.Item label="Description" name="description">
          <Input.TextArea
            rows={2}
            placeholder="Short summary of what this role can do."
            autoSize={{ minRows: 2, maxRows: 4 }}
          />
        </Form.Item>
        <Form.Item
          label="Permissions"
          name="permissions"
          rules={[{ required: true, message: 'Pick at least one permission.' }]}
          extra="Saving overwrites the current permission set."
        >
          <Select
            mode="multiple"
            placeholder="Pick permissions"
            options={permissions.map((p) => ({
              value: p.code,
              label: `${p.code} — ${p.name}`,
            }))}
            optionFilterProp="label"
            showSearch
            maxTagCount="responsive"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

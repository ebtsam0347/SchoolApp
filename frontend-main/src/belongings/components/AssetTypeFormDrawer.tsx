// Quick "Add Asset [Type]" drawer for a specific employee — creates a
// Belonging Master row (POST /belonging-master) authorized to them, without
// leaving the employee's Assets tab. Payload mirrors the Belonging Master
// API exactly: belongingName + authorizedEmployeeId + active (see the
// admin Asset Types page at `/admin/lookups/belongings`, which manages the
// same data unscoped).

import { App as AntdApp, Form, Input, Switch } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { DrawerForm } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { catalogsApi, employeesApi } from '@/people';

export type AssetTypeFormDrawerProps = {
  open: boolean;
  onClose: () => void;
  /** The employee this asset type will be authorized to. */
  employeeId: number;
};

type Values = {
  belongingName: string;
  active?: boolean;
};

export function AssetTypeFormDrawer({ open, onClose, employeeId }: AssetTypeFormDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const employeeQuery = useQuery({
    queryKey: ['employees', 'detail', employeeId],
    queryFn: ({ signal }) => employeesApi.get(employeeId, signal),
    enabled: open,
    staleTime: 5 * 60_000,
  });

  const mutation = useMutation({
    mutationFn: (v: Values) =>
      catalogsApi.createBelongingMaster({
        belongingName: v.belongingName,
        authorizedEmployeeId: employeeId,
        active: v.active ?? true,
      }),
    onSuccess: () => {
      message.success('Asset added');
      queryClient.invalidateQueries({ queryKey: ['catalog', 'belonging-master-all'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => {
      if (isApiError(err)) applyApiErrorToForm(form, err);
      message.error(getErrorMessage(err));
    },
  });

  return (
    <DrawerForm<Values>
      title="Add asset"
      open={open}
      onClose={onClose}
      form={form}
      initialValues={{ active: true }}
      submitText="Add"
      submitting={mutation.isPending}
      onFinish={(v) => mutation.mutate(v)}
    >
      <Form.Item label="Employee">
        <Input
          disabled
          value={
            employeeQuery.data
              ? [employeeQuery.data.fullName, employeeQuery.data.employeeCode]
                  .filter(Boolean)
                  .join(' · ')
              : `#${employeeId}`
          }
        />
      </Form.Item>

      <Form.Item
        name="belongingName"
        label="Asset type name"
        rules={[{ required: true, message: 'Enter the asset type name' }]}
      >
        <Input placeholder="e.g. Laptop, ID Card, Company Phone" />
      </Form.Item>

      <Form.Item
        name="active"
        label="Status"
        valuePropName="checked"
        tooltip="Indicates if the asset type record is active."
      >
        <Switch />
      </Form.Item>
    </DrawerForm>
  );
}
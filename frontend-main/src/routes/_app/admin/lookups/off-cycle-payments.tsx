import { createFileRoute } from '@tanstack/react-router'
import { Form, Input, Switch } from 'antd';
import { CatalogShell } from '@/people/CatalogShell';
import { catalogsApi } from '@/people/api';
import { offCyclePaymentDto } from '@/people/types';
type offCyclePaymentForm = { name: string; description: string; active?: boolean };
export const Route = createFileRoute('/_app/admin/lookups/off-cycle-payments')({
  component: offCyclePaymentsLookup,
})

function offCyclePaymentsLookup() {
  return (
    <CatalogShell<offCyclePaymentDto, offCyclePaymentForm>
      config={{
        title: 'Payment Category',
        description: 'List of payment category.',
        queryKey: ['catalog', 'off-cycle-payments-all'],
        loadList: (signal) => catalogsApi.offCyclePayments({active:true},signal),
        columns: [
          { title: 'Name', dataIndex: 'name', key: 'name' },
          { title: 'Description', dataIndex: 'description', key: 'description' },
        ],
        renderForm: () => (
          <>
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input placeholder="Benefit name" />
            </Form.Item>
            <Form.Item name="description" label="Description" rules={[{ required: true }]}>
              <Input placeholder="Benefit description" />
            </Form.Item>
            <Form.Item
              name="active"
              label="Status"
              valuePropName="checked"
              tooltip="Indicates if the benefit is active."
            >
              <Switch />
            </Form.Item>
          </>
        ),
        toCreate: (v) =>
          catalogsApi.createOffCyclePayment({
            name: v.name,
            description: v.description,
            active: v.active,
          }),
        toUpdate: (v, editing) =>
          catalogsApi.updateOffCyclePayment(editing.id!, {
            name: v.name,
            description: v.description,
            active: v.active,
          }),
        setActive: (row, active) => catalogsApi.updateOffCyclePayment(row.id!, { active }),
      }}
    />
  );
}

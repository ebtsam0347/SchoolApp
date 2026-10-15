import { createFileRoute } from '@tanstack/react-router';
import { Form, Input, Switch } from 'antd';
import { CatalogShell } from '@/people/CatalogShell';
import { catalogsApi, ReligionDto } from '@/people';

type ReligionForm = { name: string; active?: boolean };

export const Route = createFileRoute('/_app/admin/lookups/religions')({
  component: ReligionsLookup,
});

function ReligionsLookup() {
  return (
    <CatalogShell<ReligionDto, ReligionForm>
      config={{
        title: 'Religions',
        description: 'List of supported religions.',
        queryKey: ['catalog', 'religions-all'],
        loadList: (signal) => catalogsApi.religions(signal),
        columns: [
          { title: 'Name', dataIndex: 'name', key: 'name' },
        ],
        renderForm: () => (
          <>
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input placeholder="Religion Name" />
            </Form.Item>
            <Form.Item
              name="active"
              label="Status"
              valuePropName="checked"
              tooltip="Indicates if the religion is active."
            >
              <Switch />
            </Form.Item>
          </>
        ),
        toCreate: (v) =>
          catalogsApi.createReligion({
            name: v.name,
            active: v.active,
          }),
        toUpdate: (v, editing) =>
          catalogsApi.updateReligion(editing.id!, {
            name: v.name,
            active: v.active,
          }),
        setActive: (row, active) => catalogsApi.updateReligion(row.id!, { active }),
      }}
    />
  );
}

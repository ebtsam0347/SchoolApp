import { createFileRoute } from '@tanstack/react-router';
import { Form, Input, Switch, Tag } from 'antd';
import { CatalogShell } from '@/people/CatalogShell';
import { catalogsApi, type DepartmentDto } from '@/people';

type DeptForm = { code: string; name: string; isProduction?: boolean };

export const Route = createFileRoute('/_app/admin/lookups/departments')({
  component: DepartmentsLookup,
});

function DepartmentsLookup() {
  return (
    <CatalogShell<DepartmentDto, DeptForm>
      config={{
        title: 'Departments',
        description: 'Top-level org units. Departments are flat — sections live underneath. Mark production departments to apply production-floor policy rules.',
        queryKey: ['catalog', 'departments-all'],
        loadList: (signal) => catalogsApi.departments(signal),
        columns: [
          { title: 'Code', dataIndex: 'code', key: 'code', width: 160 },
          { title: 'Name', dataIndex: 'name', key: 'name' },
          {
            title: 'Production',
            dataIndex: 'isProduction',
            key: 'isProduction',
            width: 120,
            render: (v?: boolean) => (v ? <Tag color="blue">Production</Tag> : '—'),
          },
        ],
        renderForm: () => (
          <>
            <Form.Item name="code" label="Code" rules={[{ required: true }]}>
              <Input placeholder="PROC" />
            </Form.Item>
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input placeholder="Procurement" />
            </Form.Item>
            <Form.Item
              name="isProduction"
              label="Production department"
              valuePropName="checked"
              tooltip="Production-floor department. Drives canteen rules and other production-specific policy switches."
            >
              <Switch />
            </Form.Item>
          </>
        ),
        toCreate: (v) =>
          catalogsApi.createDepartment({
            code: v.code,
            name: v.name,
            isProduction: v.isProduction,
          }),
        toUpdate: (v, editing) =>
          catalogsApi.updateDepartment(editing.id!, {
            code: v.code,
            name: v.name,
            isProduction: v.isProduction,
          }),
        setActive: (row, active) => catalogsApi.updateDepartment(row.id!, { active }),
      }}
    />
  );
}

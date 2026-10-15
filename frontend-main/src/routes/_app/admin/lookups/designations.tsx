import { createFileRoute } from '@tanstack/react-router';
import { Form, Input } from 'antd';
import { CatalogShell } from '@/people/CatalogShell';
import { catalogsApi, type DesignationDto } from '@/people';

type DesigForm = { code: string; title: string };

export const Route = createFileRoute('/_app/admin/lookups/designations')({
  component: DesignationsLookup,
});

function DesignationsLookup() {
  return (
    <CatalogShell<DesignationDto, DesigForm>
      config={{
        title: 'Designations',
        description: 'Job-title catalog (department-agnostic).',
        queryKey: ['catalog', 'designations-all'],
        loadList: (signal) => catalogsApi.designations(signal),
        columns: [
          { title: 'Code', dataIndex: 'code', key: 'code', width: 200 },
          { title: 'Title', dataIndex: 'title', key: 'title' },
        ],
        renderForm: () => (
          <>
            <Form.Item name="code" label="Code" rules={[{ required: true }]}>
              <Input placeholder="SR_ENG" />
            </Form.Item>
            <Form.Item name="title" label="Title" rules={[{ required: true }]}>
              <Input placeholder="Senior Engineer" />
            </Form.Item>
          </>
        ),
        toCreate: (v) => catalogsApi.createDesignation({ code: v.code, title: v.title }),
        toUpdate: (v, editing) =>
          catalogsApi.updateDesignation(editing.id!, { code: v.code, title: v.title }),
        setActive: (row, active) => catalogsApi.updateDesignation(row.id!, { active }),
      }}
    />
  );
}

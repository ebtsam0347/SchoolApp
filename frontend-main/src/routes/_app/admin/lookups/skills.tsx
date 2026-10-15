import { createFileRoute } from '@tanstack/react-router';
import { Form, Input, Select } from 'antd';
import { CatalogShell } from '@/people/CatalogShell';
import { catalogsApi, type SkillDto, type SkillCategory } from '@/people';

type SkillForm = { code: string; name: string; category?: SkillCategory };
const CATEGORIES: SkillCategory[] = ['TECH', 'LANGUAGE', 'TRADE', 'SPORT', 'OTHER'];

export const Route = createFileRoute('/_app/admin/lookups/skills')({
  component: SkillsLookup,
});

function SkillsLookup() {
  return (
    <CatalogShell<SkillDto, SkillForm>
      config={{
        title: 'Skills',
        description: 'Skill catalog. Categorized loosely so the per-employee skill picker stays manageable.',
        queryKey: ['catalog', 'skills-all'],
        loadList: (signal) => catalogsApi.skillsAll(signal),
        columns: [
          { title: 'Code', dataIndex: 'code', key: 'code', width: 200 },
          { title: 'Name', dataIndex: 'name', key: 'name' },
          { title: 'Category', dataIndex: 'category', key: 'category', width: 140 },
        ],
        renderForm: () => (
          <>
            <Form.Item name="code" label="Code" rules={[{ required: true }]}>
              <Input placeholder="TECH_REACT" />
            </Form.Item>
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input placeholder="React" />
            </Form.Item>
            <Form.Item name="category" label="Category">
              <Select
                allowClear
                options={CATEGORIES.map((c) => ({ value: c, label: c[0] + c.slice(1).toLowerCase() }))}
              />
            </Form.Item>
          </>
        ),
        toCreate: (v) =>
          catalogsApi.createSkill({ code: v.code, name: v.name, category: v.category }),
        toUpdate: (v, editing) =>
          catalogsApi.updateSkill(editing.id!, {
            code: v.code,
            name: v.name,
            category: v.category,
          }),
        setActive: (row, active) => catalogsApi.updateSkill(row.id!, { active }),
      }}
    />
  );
}

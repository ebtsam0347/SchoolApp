import { createFileRoute } from '@tanstack/react-router';
import { Form, Input, Select } from 'antd';
import { CatalogShell } from '@/people/CatalogShell';
import {
  catalogsApi,
  type QualificationDto,
  type QualificationLevel,
} from '@/people';

type QualForm = { code: string; name: string; level: QualificationLevel };
const LEVELS: QualificationLevel[] = [
  'MATRIC',
  'INTERMEDIATE',
  'DIPLOMA',
  'BACHELORS',
  'MASTERS',
  'MPHIL',
  'PHD',
  'OTHER',
];

export const Route = createFileRoute('/_app/admin/lookups/qualifications')({
  component: QualificationsLookup,
});

function QualificationsLookup() {
  return (
    <CatalogShell<QualificationDto, QualForm>
      config={{
        title: 'Qualifications',
        description: 'Academic qualifications catalog (Matric → PhD). Used by employee qualification rows.',
        queryKey: ['catalog', 'qualifications-all'],
        loadList: (signal) => catalogsApi.qualificationsAll(signal),
        columns: [
          { title: 'Code', dataIndex: 'code', key: 'code', width: 200 },
          { title: 'Name', dataIndex: 'name', key: 'name' },
          { title: 'Level', dataIndex: 'level', key: 'level', width: 140 },
        ],
        renderForm: () => (
          <>
            <Form.Item name="code" label="Code" rules={[{ required: true }]}>
              <Input placeholder="BSC_CS" />
            </Form.Item>
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input placeholder="BSc Computer Science" />
            </Form.Item>
            <Form.Item name="level" label="Level" rules={[{ required: true }]}>
              <Select
                options={LEVELS.map((l) => ({ value: l, label: l[0] + l.slice(1).toLowerCase() }))}
              />
            </Form.Item>
          </>
        ),
        toCreate: (v) =>
          catalogsApi.createQualification({ code: v.code, name: v.name, level: v.level }),
        toUpdate: (v, editing) =>
          catalogsApi.updateQualification(editing.id!, {
            code: v.code,
            name: v.name,
            level: v.level,
          }),
        setActive: (row, active) => catalogsApi.updateQualification(row.id!, { active }),
        reorder: { onReorder: (entries) => catalogsApi.reorderQualifications(entries) },
      }}
    />
  );
}

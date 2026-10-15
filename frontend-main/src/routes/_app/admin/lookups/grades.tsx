import { createFileRoute } from '@tanstack/react-router';
import { Form, Input, InputNumber, Select, Tag } from 'antd';
import { CatalogShell } from '@/people/CatalogShell';
import { catalogsApi, type GradeDto, type GradeType } from '@/people';

type GradeForm = {
  code: string;
  name: string;
  level: number;
  description?: string;
  gradeType?: GradeType;
};

const GRADE_TYPE_LABEL: Record<GradeType, string> = {
  MANAGEMENT: 'Management',
  SUPERVISOR: 'Supervisor',
  LABOUR: 'Labour',
};

const GRADE_TYPE_TONE: Record<GradeType, string> = {
  MANAGEMENT: 'blue',
  SUPERVISOR: 'gold',
  LABOUR: 'default',
};

export const Route = createFileRoute('/_app/admin/lookups/grades')({
  component: GradesLookup,
});

function GradesLookup() {
  return (
    <CatalogShell<GradeDto, GradeForm>
      config={{
        title: 'Grades',
        description: 'Seniority grades. Higher level = more senior; the directory sorts by it. Grade type drives the canteen rule (management → weekly, supervisor/labour → per-day).',
        queryKey: ['catalog', 'grades-all'],
        loadList: (signal) => catalogsApi.grades(signal),
        columns: [
          { title: 'Code', dataIndex: 'code', key: 'code', width: 100 },
          { title: 'Name', dataIndex: 'name', key: 'name' },
          { title: 'Level', dataIndex: 'level', key: 'level', width: 90 },
          {
            title: 'Type',
            dataIndex: 'gradeType',
            key: 'gradeType',
            width: 130,
            render: (v?: GradeType) =>
              v ? <Tag color={GRADE_TYPE_TONE[v]}>{GRADE_TYPE_LABEL[v]}</Tag> : '—',
          },
          { title: 'Description', dataIndex: 'description', key: 'description', ellipsis: true },
        ],
        renderForm: () => (
          <>
            <Form.Item name="code" label="Code" rules={[{ required: true }]}>
              <Input placeholder="G7" />
            </Form.Item>
            <Form.Item name="name" label="Name" rules={[{ required: true }]}>
              <Input placeholder="Senior Engineer" />
            </Form.Item>
            <Form.Item name="level" label="Level" rules={[{ required: true }]}>
              <InputNumber min={0} max={20} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="gradeType"
              label="Type"
              tooltip="Drives the canteen rule and other grade-type policy switches. Defaults to Labour."
            >
              <Select<GradeType>
                allowClear
                placeholder="Labour (default)"
                options={[
                  { value: 'MANAGEMENT', label: GRADE_TYPE_LABEL.MANAGEMENT },
                  { value: 'SUPERVISOR', label: GRADE_TYPE_LABEL.SUPERVISOR },
                  { value: 'LABOUR', label: GRADE_TYPE_LABEL.LABOUR },
                ]}
              />
            </Form.Item>
            <Form.Item name="description" label="Description">
              <Input.TextArea rows={2} />
            </Form.Item>
          </>
        ),
        toCreate: (v) =>
          catalogsApi.createGrade({
            code: v.code,
            name: v.name,
            level: v.level,
            description: v.description,
            gradeType: v.gradeType,
          }),
        toUpdate: (v, editing) =>
          catalogsApi.updateGrade(editing.id!, {
            code: v.code,
            name: v.name,
            level: v.level,
            description: v.description,
            gradeType: v.gradeType,
          }),
        setActive: (row, active) => catalogsApi.updateGrade(row.id!, { active }),
        reorder: { onReorder: (entries) => catalogsApi.reorderGrades(entries) },
      }}
    />
  );
}

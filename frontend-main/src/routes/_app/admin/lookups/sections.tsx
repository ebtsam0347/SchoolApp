import { useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Form, Input, Select } from 'antd';
import { CatalogShell } from '@/people/CatalogShell';
import { catalogsApi, type SectionDto } from '@/people';

type SectionForm = { code: string; name: string; departmentId: number };

export const Route = createFileRoute('/_app/admin/lookups/sections')({
  component: SectionsLookup,
});

function SectionsLookup() {
  const [departmentFilter, setDepartmentFilter] = useState<number | undefined>(
    undefined,
  );

  // Departments power both the per-row label fallback and the filter dropdown.
  // Cached separately from the form-side query so the drawer can keep its
  // narrower active-only list.
  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments-all'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
  });

  return (
    <CatalogShell<SectionDto, SectionForm>
      config={{
        title: 'Sections',
        description: 'Sub-units inside a department. A section belongs to exactly one department.',
        queryKey: ['catalog', 'sections-all'],
        loadList: (signal) => catalogsApi.sections(undefined, signal),
        columns: [
          { title: 'Code', dataIndex: 'code', key: 'code', width: 160 },
          { title: 'Name', dataIndex: 'name', key: 'name' },
          {
            title: 'Department',
            key: 'departmentCode',
            render: (_v, row) => row.departmentCode ?? `#${row.departmentId}`,
            width: 160,
          },
        ],
        // Search across code, name, and the department code/name so a query
        // like "proc" surfaces every Procurement section.
        searchMatch: (row, q) => {
          const dept = departmentsQuery.data?.find((d) => d.id === row.departmentId);
          const hay = [row.code, row.name, row.departmentCode, dept?.name]
            .filter(Boolean)
            .join(' ')
            .toLowerCase();
          return hay.includes(q);
        },
        searchPlaceholder: 'Search section or department…',
        extraFilters: (
          <Select<number | 'ALL'>
            value={departmentFilter ?? 'ALL'}
            onChange={(v) => setDepartmentFilter(v === 'ALL' ? undefined : v)}
            style={{ minWidth: 220 }}
            showSearch
            optionFilterProp="label"
            loading={departmentsQuery.isLoading}
            options={[
              { value: 'ALL', label: 'All departments' },
              ...(departmentsQuery.data ?? [])
                .filter((d) => d.active !== false)
                .map((d) => ({ value: d.id!, label: d.name ?? d.code ?? `#${d.id}` })),
            ]}
            aria-label="Filter by department"
          />
        ),
        extraFilter: (row) =>
          departmentFilter == null || row.departmentId === departmentFilter,
        renderForm: () => <SectionFormBody />,
        toCreate: (v) =>
          catalogsApi.createSection({ code: v.code, name: v.name, departmentId: v.departmentId }),
        toUpdate: (v, editing) =>
          catalogsApi.updateSection(editing.id!, {
            code: v.code,
            name: v.name,
            departmentId: v.departmentId,
          }),
        setActive: (row, active) => catalogsApi.updateSection(row.id!, { active }),
      }}
    />
  );
}

function SectionFormBody() {
  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
  });
  return (
    <>
      <Form.Item name="code" label="Code" rules={[{ required: true }]}>
        <Input placeholder="PROC_SOURCING" />
      </Form.Item>
      <Form.Item name="name" label="Name" rules={[{ required: true }]}>
        <Input placeholder="Sourcing" />
      </Form.Item>
      <Form.Item name="departmentId" label="Department" rules={[{ required: true }]}>
        <Select
          showSearch
          optionFilterProp="label"
          loading={departmentsQuery.isLoading}
          options={(departmentsQuery.data ?? [])
            .filter((d) => d.active !== false)
            .map((d) => ({ value: d.id!, label: d.name }))}
        />
      </Form.Item>
    </>
  );
}

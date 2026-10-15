import { useMemo } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { Form, Input, Switch, Typography } from 'antd';
import { useQueries, useQuery } from '@tanstack/react-query';

import { CatalogShell } from '@/people/CatalogShell';
import {
  catalogsApi,
  employeesApi,
  EmployeePicker,
  type BelongingMasterDto,
  type EmployeeDto,
} from '@/people';

const { Text } = Typography;

type BelongingMasterForm = {
  belongingName: string;
  authorizedEmployeeId: number;
  active?: boolean;
};

export const Route = createFileRoute('/_app/admin/lookups/belongings')({
  component: BelongingsLookup,
});

function AuthorizedEmployeeCell({
  employeeId,
  employeeById,
}: {
  employeeId?: number;
  employeeById: Map<number, EmployeeDto>;
}) {
  if (!employeeId) return <Text type="secondary">—</Text>;
  const employee = employeeById.get(employeeId);
  if (!employee) return <Text type="secondary">{`#${employeeId}`}</Text>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
      <span>{employee.fullName}</span>
      <Text type="secondary" style={{ fontSize: 12 }}>
        {employee.employeeCode ?? `#${employeeId}`}
      </Text>
    </div>
  );
}

function BelongingsLookup() {
  const queryKey = ['catalog', 'belonging-master-all'] as const;

  // Same queryKey CatalogShell uses internally for its own list — this read
  // shares the cache instead of firing a second request.
  const listQuery = useQuery({
    queryKey,
    queryFn: ({ signal }) => catalogsApi.belongingMasters(signal),
  });

  const employeeIds = useMemo(
    () =>
      Array.from(
        new Set(
          (listQuery.data ?? [])
            .map((r) => r.authorizedEmployeeId)
            .filter((id): id is number => id != null),
        ),
      ),
    [listQuery.data],
  );

  // Resolve each authorized employee's name for the table — cached 5 min so
  // re-opening the page or editing a row doesn't refetch.
  const nameQueries = useQueries({
    queries: employeeIds.map((id) => ({
      queryKey: ['employees', 'detail', id],
      queryFn: ({ signal }: { signal: AbortSignal }) => employeesApi.get(id, signal),
      staleTime: 5 * 60_000,
    })),
  });

  const employeeById = useMemo(() => {
    const m = new Map<number, EmployeeDto>();
    for (const q of nameQueries) {
      if (q.data?.id != null) m.set(q.data.id, q.data);
    }
    return m;
  }, [nameQueries]);

  return (
    <CatalogShell<BelongingMasterDto, BelongingMasterForm>
      config={{
        title: 'Asset Types',
        description: 'Master list of asset types HR authorizes to specific employees.',
        queryKey,
        loadList: (signal) => catalogsApi.belongingMasters(signal),
        rowLabel: (row) => row.belongingName ?? '',
        columns: [
          { title: 'Asset Type', dataIndex: 'belongingName', key: 'belongingName' },
          {
            title: 'Authorized employee',
            key: 'authorizedEmployeeId',
            render: (_v, row) => (
              <AuthorizedEmployeeCell
                employeeId={row.authorizedEmployeeId}
                employeeById={employeeById}
              />
            ),
          },
        ],
        renderForm: () => (
          <>
            <Form.Item
              name="belongingName"
              label="Asset type name"
              rules={[{ required: true, message: 'Enter the asset type name' }]}
            >
              <Input placeholder="e.g. Laptop, ID Card, Company Phone" />
            </Form.Item>
            <Form.Item
              name="authorizedEmployeeId"
              label="Authorized employee"
              rules={[{ required: true, message: 'Pick who this asset type is authorized to' }]}
            >
              <EmployeePicker placeholder="Search by name or code" />
            </Form.Item>
            <Form.Item
              name="active"
              label="Status"
              valuePropName="checked"
              tooltip="Indicates if the asset type record is active."
            >
              <Switch />
            </Form.Item>
          </>
        ),
        toCreate: (v) =>
          catalogsApi.createBelongingMaster({
            belongingName: v.belongingName,
            authorizedEmployeeId: v.authorizedEmployeeId,
            active: v.active,
          }),
        toUpdate: (v, editing) =>
          catalogsApi.updateBelongingMaster(editing.id!, {
            belongingName: v.belongingName,
            authorizedEmployeeId: v.authorizedEmployeeId,
            active: v.active,
          }),
        // PUT replaces the whole record, so the deactivate/reactivate toggle
        // must resend belongingName + authorizedEmployeeId, not just active.
        setActive: (row, active) =>
          catalogsApi.updateBelongingMaster(row.id!, {
            belongingName: row.belongingName!,
            authorizedEmployeeId: row.authorizedEmployeeId!,
            active,
          }),
        searchMatch: (row, query) => {
          if ((row.belongingName ?? '').toLowerCase().includes(query)) return true;
          const employee =
            row.authorizedEmployeeId != null
              ? employeeById.get(row.authorizedEmployeeId)
              : undefined;
          const name = employee?.fullName?.toLowerCase() ?? '';
          const code = employee?.employeeCode?.toLowerCase() ?? '';
          return name.includes(query) || code.includes(query);
        },
        searchPlaceholder: 'Search asset type or employee…',
      }}
    />
  );
}

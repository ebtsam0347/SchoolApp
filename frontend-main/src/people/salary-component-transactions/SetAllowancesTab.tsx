// Set allowances tab — pick an employee, edit their whole allowance set in
// one form, and save it from a chosen month. Saving never edits history:
// changed amounts close the current line and open a new one from that month
// (an emptied field stops the allowance), so the History tab shows every
// version.

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Alert,
  Button,
  Form,
  InputNumber,
  Typography,
} from 'antd';
import { Save, UserRound } from 'lucide-react';
import { useEffect, useMemo } from 'react';

import { EmptyState, MoneyCell, Section } from '@/components';
import { formatDateNumeric, getErrorMessage } from '@/lib';
import { EmployeePicker } from '@/people/EmployeePicker';
import type { EmployeePickerCandidate } from '@/people/employee-picker';

import { AllowanceEmployeeCard } from './AllowanceEmployeeCard';
import { allowancesApi } from './allowances-api';
import type { AllowanceAmounts, AllowanceEmployee } from './allowances-types';

const { Text } = Typography;

export type SetAllowancesTabProps = {
  canWrite: boolean;
  employee: AllowanceEmployee | undefined;
  onEmployeeChange: (employee: AllowanceEmployee | undefined) => void;
};

type FormValues = Record<string, number | null>;

function toAllowanceEmployee(c: EmployeePickerCandidate): AllowanceEmployee {
  return {
    id: c.id,
    fullName: c.fullName,
    employeeCode: c.employeeCode,
    departmentName: c.departmentName,
    sectionName: c.sectionName ?? null,
    designationName: c.designationName,
    profilePictureUrl: c.profilePictureUrl ?? null,
  };
}

export function SetAllowancesTab({ canWrite, employee, onEmployeeChange }: SetAllowancesTabProps) {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();

  const columnsQuery = useQuery({
    queryKey: ['allowances', 'columns'],
    queryFn: ({ signal }) => allowancesApi.columns(signal),
    staleTime: 5 * 60_000,
  });
  const currentQuery = useQuery({
    queryKey: ['allowances', 'employee', employee?.id],
    queryFn: ({ signal }) => allowancesApi.forEmployee(employee!.id, signal),
    enabled: !!employee,
  });

  const columns = useMemo(() => columnsQuery.data ?? [], [columnsQuery.data]);
  const current = currentQuery.data;

  useEffect(() => {
    if (!current) return;
    const values: FormValues = {};
    for (const c of columns) values[c.code] = current.amounts[c.code] ?? null;
    form.setFieldsValue(values);
  }, [current, columns, form]);

  const watched = Form.useWatch([], form) as FormValues | undefined;
  const draft: AllowanceAmounts = {};
  for (const c of columns) {
    const v = watched?.[c.code];
    if (typeof v === 'number' && v > 0) draft[c.code] = v;
  }
  const draftTotal = Object.values(draft).reduce((s, v) => s + v, 0);
  const currentTotal = Object.values(current?.amounts ?? {}).reduce((s, v) => s + v, 0);
  const changed = columns.filter(
    (c) => (draft[c.code] ?? 0) !== (current?.amounts[c.code] ?? 0),
  );

  const save = useMutation({
    // Saved as of today; the run pays open lines whatever their dates.
    mutationFn: () => allowancesApi.save(employee!.id, { amounts: draft }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['allowances'] });
      message.success(`Allowances saved for ${employee?.fullName}.`);
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Section title="Employee">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ maxWidth: 360 }}>
            <EmployeePicker
              value={employee?.id}
              staticCandidates={employee ? [{ ...employee }] : undefined}
              onChange={(id) => {
                if (id === undefined) onEmployeeChange(undefined);
              }}
              onSelectCandidate={(c) => {
                if (!c) return;
                const picked = toAllowanceEmployee(c);
                onEmployeeChange(picked);
              }}
              style={{ width: '100%' }}
            />
          </div>
          {employee && (
            <AllowanceEmployeeCard employee={employee} />
          )}
        </div>
      </Section>

      {!employee ? (
        <EmptyState
          icon={UserRound}
          title="Pick an employee"
          description="Search by name or card no to see and change their allowances."
        />
      ) : (
        <Section
          title="Allowances"
          subtitle="Monthly amounts in rupees. Leave a field empty to stop that allowance."
        >
          {currentQuery.isError ? (
            <Alert type="error" showIcon title={getErrorMessage(currentQuery.error)} />
          ) : (
            <Form<FormValues>
              form={form}
              layout="vertical"
              disabled={!canWrite || currentQuery.isLoading}
              onFinish={() => save.mutate()}
            >
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                  columnGap: 16,
                }}
              >
                {columns.map((c) => (
                  <Form.Item
                    key={c.code}
                    name={c.code}
                    label={<span title={c.name}>{c.name}</span>}
                    // One-line labels so a long name can't push its input below the others.
                    labelCol={{ style: { whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' } }}
                    extra={
                      changed.includes(c) ? (
                        <Text type="warning">
                          was {current?.amounts[c.code] ? current.amounts[c.code].toLocaleString('en-PK') : 'none'}
                        </Text>
                      ) : current?.since?.[c.code] ? (
                        <Text type="secondary">since {formatDateNumeric(current.since?.[c.code])}</Text>
                      ) : undefined
                    }
                  >
                    <InputNumber<number>
                      min={0}
                      step={500}
                      precision={0}
                      style={{ width: '100%' }}
                      formatter={(v) => (v ? Number(v).toLocaleString('en-PK') : '')}
                      parser={(v) => Number((v ?? '').replace(/,/g, '')) || 0}
                    />
                  </Form.Item>
                ))}
              </div>

              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 16,
                  alignItems: 'flex-end',
                  justifyContent: 'flex-end',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
                  <Text>
                    Total <strong><MoneyCell amount={draftTotal} maximumFractionDigits={0} /></strong>
                    {current && current.lastChanged && (
                      <Text type="secondary">
                        {' '}(now <MoneyCell amount={currentTotal} maximumFractionDigits={0} />)
                      </Text>
                    )}
                  </Text>
                  {canWrite && (
                    <Button
                      type="primary"
                      htmlType="submit"
                      icon={<Save size={14} strokeWidth={1.75} />}
                      loading={save.isPending}
                      disabled={changed.length === 0}
                    >
                      Save
                    </Button>
                  )}
                </div>
              </div>
            </Form>
          )}
        </Section>
      )}
    </div>
  );
}

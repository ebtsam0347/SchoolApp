// Gratuity calculator. Read-only — bundle ships today's value; an asOf
// override falls back to a direct call (rare HR what-if scenario).

import { useState } from 'react';
import { Alert, DatePicker, Tag } from 'antd';
import type { Dayjs } from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { Landmark } from 'lucide-react';
import { KpiGrid, MoneyCell, Section, StatCard } from '@/components';
import { getErrorMessage } from '@/lib';
import { gratuityApi } from '@/payroll';
import { useSalaryTab } from './query-keys';

export function GratuitySection({ employeeId }: { employeeId: number }) {
  const tabQuery = useSalaryTab(employeeId);
  const [asOf, setAsOf] = useState<Dayjs | null>(null);

  // Bundle carries today's gratuity. For a custom asOf, fall back to a
  // direct call (rare HR what-if scenario) so the bundle stays small.
  const customQuery = useQuery({
    queryKey: ['payroll', 'gratuity', employeeId, asOf?.format('YYYY-MM-DD')],
    queryFn: ({ signal }) =>
      gratuityApi.forEmployee(employeeId, asOf?.format('YYYY-MM-DD') ?? '', signal),
    enabled: asOf != null,
    retry: false,
  });

  const g = asOf ? customQuery.data : tabQuery.data?.gratuity;
  const error = asOf ? customQuery.error : null;
  const isError = asOf ? customQuery.isError : false;

  return (
    <Section
      title="Gratuity"
      subtitle="Read-only calculator. Computed on demand from hire date + current basic + statutory thresholds."
      actions={
        <DatePicker
          value={asOf}
          onChange={setAsOf}
          allowClear
          placeholder="Defaults to today"
        />
      }
    >
      {isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't compute gratuity"
          description={getErrorMessage(error)}
        />
      ) : g ? (
        <KpiGrid>
          <StatCard
            label="Eligible"
            value={g.eligible ? 'Yes' : 'No'}
            tone={g.eligible ? 'success' : 'neutral'}
            icon={Landmark}
          />
          <StatCard
            label="Service"
            value={`${g.completedYears} yr${g.completedYears === 1 ? '' : 's'}`}
            tone="info"
          />
          <StatCard
            label="Last basic"
            value={<MoneyCell amount={g.lastBasic} />}
            tone="neutral"
          />
          <StatCard
            label="Gratuity"
            value={
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 6 }}>
                <MoneyCell
                  amount={g.gratuity}
                  style={{ fontWeight: 600 }}
                />
                <Tag
                  color="processing"
                  style={{ margin: 0, fontSize: 13, fontWeight: 500, paddingInline: 10 }}
                >
                  No. of gratuities: <strong>{g.noOfGratuities ?? 0}</strong>
                </Tag>
              </div>
            }
            tone="primary"
            variant="accent-radial"
          />
        </KpiGrid>
      ) : null}
    </Section>
  );
}

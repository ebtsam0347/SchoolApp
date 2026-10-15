// /time/holidays — calendar of public holidays for the company.
//
// Shared read across all employees (no perm gate). HR can add / delete via
// the Can-gated buttons. Year is URL-synced via ?year=YYYY so a deep link
// shows the same calendar.

import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Button, Select, Space } from 'antd';
import {
  CalendarCheck,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Plus,
} from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { Can } from '@/auth';
import { holidaysApi, toIsoDate } from '@/attendance';
import {
  HolidayFormDrawer,
  MonthGroup,
  groupByMonth,
} from '@/attendance/holidays';

type HolidaySearch = { year?: number };

export const Route = createFileRoute('/_app/time/holidays')({
  validateSearch: (search): HolidaySearch => ({
    year:
      typeof search.year === 'number'
        ? search.year
        : typeof search.year === 'string'
          ? Number(search.year) || undefined
          : undefined,
  }),
  component: HolidaysPage,
});

function HolidaysPage() {
  const search = useSearch({ from: '/_app/time/holidays' });
  const navigate = useNavigate({ from: '/time/holidays' });
  const year = search.year ?? new Date().getFullYear();

  const [openCreate, setOpenCreate] = useState(false);

  const holidaysQuery = useQuery({
    queryKey: ['holidays', year],
    queryFn: ({ signal }) => holidaysApi.list(year, signal),
    staleTime: 30 * 60_000,
  });

  const holidays = holidaysQuery.data ?? [];
  const today = toIsoDate(new Date());
  const upcoming = holidays.filter((h) => h.holidayDate >= today);
  const grouped = groupByMonth(holidays);

  const setYear = (next: number) =>
    navigate({ search: (prev) => ({ ...prev, year: next }), replace: true });

  const counts = {
    national: holidays.filter((h) => h.type === 'NATIONAL').length,
    company: holidays.filter((h) => h.type === 'COMPANY').length,
    provincial: holidays.filter((h) => h.type === 'PROVINCIAL').length,
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Time"
        title="Holidays"
        description={`Public holidays the school observes in ${year}.`}
        actions={
          <Space size={4}>
            <Button
              icon={<ChevronLeft size={14} />}
              onClick={() => setYear(year - 1)}
              aria-label="Previous year"
            />
            <Select
              value={year}
              onChange={setYear}
              style={{ width: 100 }}
              options={[year + 1, year, year - 1, year - 2].map((y) => ({
                value: y,
                label: String(y),
              }))}
            />
            <Button
              icon={<ChevronRight size={14} />}
              onClick={() => setYear(year + 1)}
              aria-label="Next year"
            />
            <Can I="manage" a="attendance">
              <Button
                type="primary"
                icon={<Plus size={14} />}
                onClick={() => setOpenCreate(true)}
              >
                Add holiday
              </Button>
            </Can>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          icon={CalendarCheck}
          accentCorner="tr"
          label="Total"
          value={`${holidays.length}`}
          delta={`${upcoming.length} upcoming`}
        />
        <StatCard
          variant="plain"
          tone="error"
          icon={CalendarDays}
          label="National"
          value={`${counts.national}`}
        />
        <StatCard
          variant="plain"
          tone="primary"
          icon={CalendarDays}
          label="Company"
          value={`${counts.company}`}
        />
        <StatCard
          variant="plain"
          tone="info"
          icon={CalendarDays}
          label="Provincial"
          value={`${counts.provincial}`}
        />
      </KpiGrid>

      <Section
        title={`${year} calendar`}
        subtitle="Sundays are weekly off everywhere."
      >
        <RequestState
          loading={false}
          error={holidaysQuery.isError ? holidaysQuery.error : undefined}
          errorTitle="Couldn't load holidays"
          empty={!holidaysQuery.isLoading && holidays.length === 0}
          emptyFallback={
            <EmptyState
              icon={CalendarCheck}
              title="No holidays for this year"
              description="HR can populate the calendar in bulk once each year, or add them as they're announced."
              action={
                <Can I="manage" a="attendance">
                  <Button
                    type="primary"
                    icon={<Plus size={14} />}
                    onClick={() => setOpenCreate(true)}
                  >
                    Add the first one
                  </Button>
                </Can>
              }
            />
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {grouped.map(({ monthLabel, items }) => (
              <MonthGroup
                key={monthLabel}
                monthLabel={monthLabel}
                items={items}
              />
            ))}
          </div>
        </RequestState>
      </Section>

      <HolidayFormDrawer
        open={openCreate}
        defaultYear={year}
        onClose={() => setOpenCreate(false)}
      />
    </div>
  );
}

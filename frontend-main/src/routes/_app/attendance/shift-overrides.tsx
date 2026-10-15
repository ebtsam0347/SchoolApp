// /attendance/shift-overrides — operator-driven per-employee shift overrides.
//
// During high-load days the attendance operator hand-tunes shift times
// for specific employees over a date range — without authoring a
// brand-new catalog entry. Insert / patch / delete each rewrite the
// `attendance_records.shift_*` snapshot and recompute minutes/status
// across the affected dates in the same transaction.
//
// Search shape:
//   * URL `?date=YYYY-MM-DD` → "rows covering this date" (`GET ?date=`)
//   * URL `?employeeId=…&from=…&to=…` → "this employee's overrides"
//
// Default landing is "today". Subcomponents — OverridesTable,
// CreateOverrideDrawer, EditOverrideDrawer — live in
// src/attendance/shift-overrides/.

import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  InputNumber,
  Radio,
  Space,
} from 'antd';
import dayjs from 'dayjs';
import { CalendarRange, Plus, Search, Users } from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { Can } from '@/auth';
import {
  formatDateMonthDay,
  shiftOverridesApi,
  toIsoDate,
  type ShiftOverrideDto,
} from '@/attendance';
import {
  CreateOverrideDrawer,
  EditOverrideDrawer,
  OverridesTable,
  daysBetween,
} from '@/attendance/shift-overrides';

type RouteSearch = {
  date?: string;
  employeeId?: number;
  from?: string;
  to?: string;
};

type Mode = 'BY_DATE' | 'BY_EMPLOYEE';

export const Route = createFileRoute('/_app/attendance/shift-overrides')({
  validateSearch: (s): RouteSearch => ({
    date: typeof s.date === 'string' ? s.date : undefined,
    employeeId:
      typeof s.employeeId === 'number'
        ? s.employeeId
        : typeof s.employeeId === 'string' && s.employeeId.length > 0
          ? Number(s.employeeId) || undefined
          : undefined,
    from: typeof s.from === 'string' ? s.from : undefined,
    to: typeof s.to === 'string' ? s.to : undefined,
  }),
  component: ShiftOverridesPage,
});

function ShiftOverridesPage() {
  const search = useSearch({ from: '/_app/attendance/shift-overrides' });
  const navigate = useNavigate({ from: '/attendance/shift-overrides' });

  const today = toIsoDate(new Date());
  const [mode, setMode] = useState<Mode>(
    search.employeeId ? 'BY_EMPLOYEE' : 'BY_DATE',
  );
  const [openCreate, setOpenCreate] = useState(false);
  const [editing, setEditing] = useState<ShiftOverrideDto | null>(null);

  // If neither filter is set, default the URL to today so refresh / share
  // gives a deterministic landing.
  useEffect(() => {
    if (!search.date && !search.employeeId) {
      navigate({ search: { date: today }, replace: true });
    }
  }, [search.date, search.employeeId, today, navigate]);

  const params =
    mode === 'BY_EMPLOYEE'
      ? {
          employeeId: search.employeeId,
          from: search.from,
          to: search.to,
        }
      : { date: search.date ?? today };

  const listQuery = useQuery({
    queryKey: ['shift-overrides', mode, params],
    queryFn: ({ signal }) => shiftOverridesApi.list(params, signal),
    enabled:
      mode === 'BY_DATE' ||
      (mode === 'BY_EMPLOYEE' && Number.isFinite(search.employeeId)),
  });

  const rows = listQuery.data ?? [];
  const distinctEmployees = new Set(rows.map((r) => r.employeeId)).size;
  const longestRange = rows.reduce((acc, r) => {
    const days = daysBetween(r.fromDate, r.toDate);
    return days > acc ? days : acc;
  }, 0);

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Attendance"
        title="Shift overrides"
        description="Per-employee shift adjustments over a date range. Triggers same-tx recompute of attendance records."
        actions={
          <Can I="manage" a="attendance">
            <Button
              type="primary"
              icon={<Plus size={14} />}
              onClick={() => setOpenCreate(true)}
            >
              New override
            </Button>
          </Can>
        }
      />

      <Alert
        type="info"
        showIcon
        title="One override per employee per date range"
        description="To extend or reshape an existing range, edit it rather than creating a new one — overlapping ranges aren't allowed."
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          accentCorner="tr"
          icon={CalendarRange}
          label="Override rows"
          value={`${rows.length}`}
        />
        <StatCard
          variant="plain"
          tone="info"
          icon={Users}
          label="Employees affected"
          value={`${distinctEmployees}`}
        />
        <StatCard
          variant="plain"
          tone="warning"
          icon={CalendarRange}
          label="Longest range"
          value={longestRange === 0 ? '—' : `${longestRange}d`}
        />
        <StatCard
          variant="plain"
          tone="neutral"
          icon={Search}
          label="Mode"
          value={mode === 'BY_DATE' ? 'By date' : 'By employee'}
        />
      </KpiGrid>

      <Section
        title="Search"
        subtitle="Pick a single date, or scope to an employee + range."
      >
        <Card styles={{ body: { padding: 14 } }}>
          <Radio.Group
            value={mode}
            onChange={(e) => setMode(e.target.value)}
            optionType="button"
            options={[
              { value: 'BY_DATE', label: 'By date' },
              { value: 'BY_EMPLOYEE', label: 'By employee' },
            ]}
            style={{ marginBottom: 12 }}
          />
          {mode === 'BY_DATE' ? (
            <DatePicker
              value={search.date ? dayjs(search.date) : dayjs()}
              format="DD MMM YYYY"
              allowClear={false}
              onChange={(d) =>
                navigate({
                  search: { date: d ? d.format('YYYY-MM-DD') : today },
                  replace: true,
                })
              }
            />
          ) : (
            <Space wrap size={[8, 8]}>
              <InputNumber
                placeholder="Employee id"
                style={{ width: 160 }}
                value={search.employeeId}
                onChange={(v) =>
                  navigate({
                    search: (prev) => ({
                      ...prev,
                      employeeId: typeof v === 'number' ? v : undefined,
                    }),
                    replace: true,
                  })
                }
              />
              <DatePicker
                placeholder="From"
                value={search.from ? dayjs(search.from) : null}
                format="DD MMM YYYY"
                onChange={(d) =>
                  navigate({
                    search: (prev) => ({
                      ...prev,
                      from: d ? d.format('YYYY-MM-DD') : undefined,
                    }),
                    replace: true,
                  })
                }
              />
              <DatePicker
                placeholder="To"
                value={search.to ? dayjs(search.to) : null}
                format="DD MMM YYYY"
                onChange={(d) =>
                  navigate({
                    search: (prev) => ({
                      ...prev,
                      to: d ? d.format('YYYY-MM-DD') : undefined,
                    }),
                    replace: true,
                  })
                }
              />
            </Space>
          )}
        </Card>
      </Section>

      <Section
        title={
          mode === 'BY_DATE'
            ? `Rows covering ${formatDateMonthDay(search.date ?? today)}`
            : 'Employee rows'
        }
      >
        <RequestState
          loading={false}
          error={listQuery.isError ? listQuery.error : undefined}
          empty={!listQuery.isLoading && rows.length === 0}
          errorTitle="Couldn't load overrides"
          emptyFallback={
            <EmptyState
              icon={CalendarRange}
              title="No overrides"
              description={
                mode === 'BY_DATE'
                  ? 'No employee has a shift override covering this date.'
                  : 'Nothing on file for this employee in the supplied range.'
              }
            />
          }
        >
          <OverridesTable
            data={rows}
            loading={listQuery.isLoading}
            onEdit={setEditing}
          />
        </RequestState>
      </Section>

      <CreateOverrideDrawer
        open={openCreate}
        onClose={() => setOpenCreate(false)}
      />
      <EditOverrideDrawer
        override={editing}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

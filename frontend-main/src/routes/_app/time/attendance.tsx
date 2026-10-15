// /time/attendance — my month at a glance.
//
// Three regions, top-down:
//  1. Page header + month picker (URL-synced via ?month=YYYY-MM).
//  2. KPI strip — total worked / total OT / late minutes / absent days.
//  3. Day grid — one tile per calendar day in the month, color-coded by
//     status. Click → drawer with the day's punches + a regularize CTA.
//
// One BFF composite (T2) replaces the records + holidays + leave fan-out.
//
// MonthGrid, DayDetail, RegularizeDrawer, and the grid-build helpers
// live in src/attendance/month/.

import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Button, DatePicker, Drawer, Segmented, Space } from 'antd';
import dayjs from 'dayjs';
import {
  Calendar,
  ChevronLeft,
  ChevronRight,
  Grid3x3,
  Hourglass,
  LayoutGrid,
  Sparkles,
  Timer,
} from 'lucide-react';

import { KpiGrid, PageHeader, RequestState, Section, StatCard } from '@/components';
import {
  attendanceBff,
  formatDateShort,
  formatMinutes,
  parseDateOnly,
  toIsoMonth,
} from '@/attendance';
import {
  DayDetail,
  MonthGrid,
  MonthHeatmap,
  buildMonthGrid,
} from '@/attendance/month';

type AttendanceView = 'tiles' | 'heatmap';
type AttendanceSearch = { month?: string; day?: string; view?: AttendanceView };

export const Route = createFileRoute('/_app/time/attendance')({
  validateSearch: (search): AttendanceSearch => ({
    month: typeof search.month === 'string' ? search.month : undefined,
    day: typeof search.day === 'string' ? search.day : undefined,
    view:
      search.view === 'tiles' || search.view === 'heatmap'
        ? search.view
        : undefined,
  }),
  component: AttendancePage,
});

function AttendancePage() {
  const search = useSearch({ from: '/_app/time/attendance' });
  const navigate = useNavigate({ from: '/time/attendance' });

  const month = search.month ?? toIsoMonth(new Date());
  const monthDate = parseDateOnly(`${month}-01`)!;

  // Single composite call (BFF T2) — server pre-merges record + holiday +
  // leave with HOLIDAY > LEAVE > rec.status > WEEKEND/ABSENT precedence.
  const monthQuery = useQuery({
    queryKey: ['bff', 'attendance', 'me', 'month', month],
    queryFn: ({ signal }) => attendanceBff.month(month, signal),
  });

  const days = buildMonthGrid(monthDate, monthQuery.data?.days ?? []);
  const summary = monthQuery.data?.summary ?? null;

  const setMonth = (delta: number) => {
    const next = new Date(monthDate);
    next.setMonth(next.getMonth() + delta);
    navigate({
      search: (prev) => ({ ...prev, month: toIsoMonth(next) }),
      replace: true,
    });
  };

  const closeDrawer = () =>
    navigate({ search: (prev) => ({ ...prev, day: undefined }), replace: true });

  const openDay = (workDate: string) =>
    navigate({ search: (prev) => ({ ...prev, day: workDate }), replace: true });

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
        eyebrow="Time"
        title="My attendance"
        description="Per-day attendance for the selected month."
        actions={
          <Space size={4}>
            <Button
              icon={<ChevronLeft size={14} />}
              onClick={() => setMonth(-1)}
              aria-label="Previous month"
            />
            <DatePicker
              picker="month"
              value={dayjs(month, 'YYYY-MM')}
              allowClear={false}
              format="MMM YYYY"
              onChange={(d) => {
                if (d) {
                  navigate({
                    search: (prev) => ({ ...prev, month: d.format('YYYY-MM') }),
                    replace: true,
                  });
                }
              }}
            />
            <Button
              icon={<ChevronRight size={14} />}
              onClick={() => setMonth(1)}
              aria-label="Next month"
            />
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="success"
          accentCorner="tr"
          icon={Timer}
          label="Total worked"
          value={formatMinutes(summary?.totalWorkedMinutes ?? 0)}
          delta={
            summary
              ? `${summary.present + summary.halfDay} of ${
                  summary.present + summary.halfDay + summary.absent + summary.leave
                } working days`
              : undefined
          }
        />
        <StatCard
          variant="plain"
          tone="info"
          icon={Sparkles}
          label="Overtime"
          value={formatMinutes(summary?.totalOtMinutes ?? 0)}
        />
        <StatCard
          variant="plain"
          tone={summary && summary.totalLateMinutes > 0 ? 'warning' : 'neutral'}
          icon={Hourglass}
          label="Late minutes"
          value={summary ? formatMinutes(summary.totalLateMinutes) : '0m'}
          delta={
            summary && summary.totalLateMinutes === 0 ? 'On time every day' : undefined
          }
        />
        <StatCard
          variant="plain"
          tone={summary && summary.absent > 0 ? 'error' : 'neutral'}
          icon={Calendar}
          label="Absent days"
          value={`${summary?.absent ?? 0}`}
          delta={
            summary && summary.leave > 0
              ? `${summary.leave} on approved leave`
              : undefined
          }
        />
      </KpiGrid>

      <Section
        title={dayjs(month, 'YYYY-MM').format('MMMM YYYY')}
        subtitle="Click any day to view punches and submit a regularization."
        actions={
          <Segmented<AttendanceView>
            size="small"
            value={search.view ?? 'tiles'}
            onChange={(v) =>
              navigate({
                search: (prev) => ({ ...prev, view: v === 'tiles' ? undefined : v }),
                replace: true,
              })
            }
            options={[
              {
                value: 'tiles',
                icon: <LayoutGrid size={14} strokeWidth={1.75} />,
                label: 'Tiles',
              },
              {
                value: 'heatmap',
                icon: <Grid3x3 size={14} strokeWidth={1.75} />,
                label: 'Heatmap',
              },
            ]}
          />
        }
      >
        <RequestState
          loading={false}
          error={monthQuery.isError ? monthQuery.error : undefined}
          errorTitle="Couldn't load attendance for this month"
        >
          {search.view === 'heatmap' ? (
            <MonthHeatmap days={days} onSelect={openDay} loading={monthQuery.isLoading} />
          ) : (
            <MonthGrid days={days} onSelect={openDay} loading={monthQuery.isLoading} />
          )}
        </RequestState>
      </Section>

      <Drawer
        title={search.day ? formatDateShort(search.day) : 'Day detail'}
        open={Boolean(search.day)}
        onClose={closeDrawer}
        size={480}
        destroyOnHidden
      >
        {search.day ? (
          <DayDetail
            workDate={search.day}
            day={days.find((d) => d.workDate === search.day) ?? null}
            onClose={closeDrawer}
          />
        ) : null}
      </Drawer>
    </div>
  );
}

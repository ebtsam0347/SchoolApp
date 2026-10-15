// /time/today — landing for ESS attendance.
//
// Hero "Punch In / Out" card on the left, leave-balance summary on the
// right. Below: today's KPIs (worked / late / OT / shift), today's punch
// timeline, and upcoming peeks (next holiday + next approved leave).
// One BFF composite (T1) replaces five fan-outs; punches refetch every
// 30s so "you've been working for 4h 23m" stays fresh.
//
// Subcomponents — PunchHeroCard, BalanceSummaryCard, PunchTimeline,
// UpcomingPanel — live in src/attendance/today/.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Empty, Typography } from 'antd';
import { Clock, Coffee, Hourglass, Sparkles, Timer } from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { useAuth } from '@/auth';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  attendanceBff,
  formatDateShort,
  formatMinutes,
  formatShiftRange,
  prettyLeaveCode,
  toIsoDate,
  type AttendanceTodayResponse,
} from '@/attendance';
import {
  BalanceSummaryCard,
  PunchHeroCard,
  PunchTimeline,
  UpcomingPanel,
} from '@/attendance/today';

const { Text } = Typography;

export const Route = createFileRoute('/_app/time/today')({
  component: TodayPage,
});

const POLL_PUNCHES_MS = 30_000;

function TodayPage() {
  const auth = useAuth();
  const today = toIsoDate(new Date());

  // Single composite call replaces five fan-outs (BFF T1).
  const todayQuery = useQuery({
    queryKey: ['bff', 'attendance', 'today'],
    queryFn: ({ signal }) => attendanceBff.today(signal),
    enabled: auth.user?.employeeLinked === true,
    refetchInterval: POLL_PUNCHES_MS,
  });

  const data: AttendanceTodayResponse | undefined = todayQuery.data;
  const todaysRecord = data?.today ?? null;
  const punches = data?.punches ?? [];

  if (auth.user && !auth.user.employeeLinked) {
    return (
      <PageContainer>
        <PageHeader
          eyebrow="Time"
          title="Today"
          description={`Welcome ${auth.user.fullName.split(' ')[0]}.`}
        />
        <EmptyState
          icon={Clock}
          title="No employee record"
          description="Your account is not linked to an employee. Attendance, leaves, and overtime self-service will appear here once HR provisions your employee record."
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        eyebrow="Time"
        title="Today"
        description={formatDateShort(today)}
        actions={
          <Link to="/time/attendance">
            <Button>View month</Button>
          </Link>
        }
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)',
          gap: 16,
          alignItems: 'stretch',
        }}
        className="time-today-hero-grid"
      >
        <PunchHeroCard record={todaysRecord} punches={punches} />
        <BalanceSummaryCard
          summary={data?.balanceSummary}
          loading={todayQuery.isLoading}
        />
      </div>

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="success"
          accentCorner="tr"
          icon={Timer}
          label="Worked today"
          value={formatMinutes(todaysRecord?.workedMinutes ?? 0)}
          delta={
            todaysRecord
              ? `Shift ${formatShiftRange(
                  todaysRecord.shiftStartTime,
                  todaysRecord.shiftEndTime,
                )}`
              : undefined
          }
        />
        <StatCard
          variant={
            todaysRecord && todaysRecord.lateMinutes > 0 ? 'accent-corner' : 'plain'
          }
          tone={todaysRecord && todaysRecord.lateMinutes > 0 ? 'warning' : 'neutral'}
          accentCorner="tr"
          icon={Hourglass}
          label="Late by"
          value={
            todaysRecord && todaysRecord.lateMinutes > 0
              ? formatMinutes(todaysRecord.lateMinutes)
              : 'On time'
          }
        />
        <StatCard
          variant="plain"
          tone="info"
          icon={Sparkles}
          label="Overtime"
          value={
            todaysRecord
              ? formatMinutes(
                  todaysRecord.otNormalMinutes +
                    todaysRecord.otExtraMinutes +
                    todaysRecord.otSpecialMinutes,
                )
              : '0m'
          }
          delta={
            todaysRecord && todaysRecord.otSpecialMinutes > 0
              ? 'Special (Sun/holiday)'
              : todaysRecord && todaysRecord.otExtraMinutes > 0
                ? 'Includes extra (>2h)'
                : undefined
          }
        />
        <StatCard
          variant="plain"
          icon={Coffee}
          tone={
            todaysRecord
              ? (ATTENDANCE_STATUS_TONE[todaysRecord.status] ?? 'neutral')
              : 'neutral'
          }
          label="Day status"
          value={
            todaysRecord ? ATTENDANCE_STATUS_LABEL[todaysRecord.status] : 'Not recorded'
          }
          delta={
            data?.todayHoliday
              ? data.todayHoliday.name
              : data?.activeLeave
                ? prettyLeaveCode(data.activeLeave.leaveTypeCode)
                : undefined
          }
        />
      </KpiGrid>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 3fr) minmax(0, 2fr)',
          gap: 16,
          alignItems: 'flex-start',
        }}
        className="time-today-bottom-grid"
      >
        <Section
          title="Today's punches"
          subtitle={
            punches.length === 0
              ? 'No punches yet — punch in to start your day.'
              : `${punches.length} punch${punches.length === 1 ? '' : 'es'} recorded`
          }
        >
          <Card styles={{ body: { padding: punches.length === 0 ? 32 : 24 } }}>
            <RequestState
              loading={false}
              error={todayQuery.isError ? todayQuery.error : undefined}
              empty={!todayQuery.isLoading && punches.length === 0}
              errorTitle="Couldn't load today's attendance"
              emptyFallback={
                <Empty
                  description={
                    <Text type="secondary">
                      Biometric punches and self-portal punches will appear here as
                      they happen.
                    </Text>
                  }
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              }
            >
              <PunchTimeline punches={punches} />
            </RequestState>
          </Card>
        </Section>

        <UpcomingPanel upcoming={data?.upcoming} />
      </div>
    </PageContainer>
  );
}

function PageContainer({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
        width: '100%',
      }}
    >
      {children}
    </div>
  );
}

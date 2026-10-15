// /attendance/live — Live monitoring.
//
// A single-screen operator wall-board (no page scroll): a compact "today"
// summary strip on top, then two columns that fill the rest of the viewport —
// Recent activity (who just marked attendance: card no + name) on the left, and
// a realtime per-department count on the right. Each column scrolls internally.
//
// Data comes from existing endpoints, near-real-time via polling: the org pulse
// for the summary, the punch feed for recent activity (5s), and the records
// aggregate for the department counts (5s). See docs/attendance-live-dashboard.md.

import { useEffect, useMemo, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Card, DatePicker, Empty, Skeleton, Typography, theme as antdTheme } from 'antd';
import dayjs from 'dayjs';
import { Activity, Building2, LogIn } from 'lucide-react';

import { forbiddenOr } from '@/lib';
import { catalogsApi } from '@/people';
import {
  attendanceBff,
  formatClock,
  formatDateLong,
  type AttendancePunchesRow,
} from '@/attendance';
import {
  AnimatedNumber,
  DEPARTMENT_SAMPLE_SIZE,
  FlipNumber,
  RECENT_ACTIVITY_REFETCH_MS,
  aggregateDepartmentBreakdown,
} from '@/attendance/admin';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/attendance/live')({
  component: LiveMonitoringPage,
});

function LiveMonitoringPage() {
  const { token } = useToken();
  const [date, setDate] = useState<dayjs.Dayjs>(() => dayjs());
  // Whether the operator explicitly picked a date via the picker below. Until
  // then this is a live wall-board — it should keep tracking "today" on its
  // own, so a screen left open past midnight rolls over to the new day and
  // every counter restarts from zero instead of freezing on yesterday's data.
  const [datePinned, setDatePinned] = useState(false);
  const dateIso = date.format('YYYY-MM-DD');
  const isToday = dateIso === dayjs().format('YYYY-MM-DD');

  useEffect(() => {
    if (datePinned) return;
    const id = setInterval(() => {
      const now = dayjs();
      if (now.format('YYYY-MM-DD') !== dateIso) setDate(now);
    }, 30_000);
    return () => clearInterval(id);
  }, [datePinned, dateIso]);

  // Org pulse — the top "today" summary (present / absent / on leave). Kept on
  // the same cadence as the punch feed and department breakdown below so the
  // "marked today" / department counts and the Present tile move in lockstep
  // instead of the summary lagging a poll cycle behind.
  const dashboardQuery = useQuery({
    queryKey: ['attendance', 'operator-dashboard', dateIso],
    queryFn: ({ signal }) => attendanceBff.operatorDashboard(dateIso, undefined, signal),
    placeholderData: (prev) => prev,
    refetchInterval: isToday ? RECENT_ACTIVITY_REFETCH_MS : false,
  });
  const pulse = dashboardQuery.data?.pulse;

  // Recent punches — feeds the left column and the "marked today" flip counter.
  const punchesQuery = useQuery({
    queryKey: ['attendance', 'live-punches', dateIso],
    queryFn: ({ signal }) =>
      attendanceBff.punches(
        { date: dateIso, size: 50, sort: 'punchedAt,desc' },
        signal,
      ),
    placeholderData: (prev) => prev,
    refetchInterval: isToday ? RECENT_ACTIVITY_REFETCH_MS : false,
  });
  const punchRows: AttendancePunchesRow[] = punchesQuery.data?.content ?? [];
  const markedToday = punchesQuery.data?.totalElements;

  // Records aggregate — realtime per-department attendance counts.
  const recordsQuery = useQuery({
    queryKey: ['attendance', 'live-dept', dateIso],
    queryFn: ({ signal }) =>
      attendanceBff.records(
        { date: dateIso, size: DEPARTMENT_SAMPLE_SIZE, sort: 'attendanceDate,desc' },
        signal,
      ),
    placeholderData: (prev) => prev,
    refetchInterval: isToday ? RECENT_ACTIVITY_REFETCH_MS : false,
  });

  const departmentsQuery = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: 5 * 60 * 1000,
  });

  // Merge catalog (all departments) with the per-department present counts so
  // every department shows — even ones with no attendance yet — ranked by the
  // number of employees who've marked attendance so far today.
  const departments = useMemo(() => {
    const agg = aggregateDepartmentBreakdown(recordsQuery.data?.content ?? []);
    const byId = new Map(agg.map((d) => [d.departmentId ?? -1, d]));
    const list = (departmentsQuery.data ?? [])
      .filter((d) => d.id != null && d.name != null)
      .map((d) => ({
        id: d.id!,
        name: d.name!,
        present: byId.get(d.id!)?.present ?? 0,
      }));
    const unassigned = byId.get(-1);
    if (unassigned && unassigned.present > 0) {
      list.push({ id: -1, name: 'Unassigned', present: unassigned.present });
    }
    list.sort((a, b) => b.present - a.present || a.name.localeCompare(b.name));
    return list;
  }, [recordsQuery.data, departmentsQuery.data]);

  const maxPresent = Math.max(1, ...departments.map((d) => d.present));
  const fetching =
    dashboardQuery.isFetching || punchesQuery.isFetching || recordsQuery.isFetching;

  const summaryError = dashboardQuery.isError ? dashboardQuery.error : undefined;

  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        minHeight: 0,
      }}
    >
      {/* ---- Top: today summary ------------------------------------------- */}
      <Card styles={{ body: { padding: '14px 18px' } }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 20,
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
            <span
              aria-hidden
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: `color-mix(in srgb, ${token.colorPrimary} 12%, transparent)`,
                color: token.colorPrimary,
                flexShrink: 0,
              }}
            >
              <Activity size={20} strokeWidth={1.75} />
            </span>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 16, fontWeight: 600, color: token.colorText }}>
                  Live monitoring
                </span>
                <span
                  aria-hidden
                  className={fetching ? 'fs-live-dot fs-live-dot-active' : 'fs-live-dot'}
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: token.colorSuccess,
                  }}
                />
                <Text
                  strong
                  style={{
                    fontSize: 11,
                    letterSpacing: '0.08em',
                    textTransform: 'uppercase',
                    color: token.colorSuccess,
                  }}
                >
                  Live
                </Text>
              </div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {formatDateLong(dateIso)}
              </Text>
            </div>
          </div>

          {/* Hero: employees who've marked attendance today, rolling live. */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <FlipNumber value={markedToday ?? 0} size={30} />
            <Text
              type="secondary"
              style={{
                fontSize: 11,
                lineHeight: 1.2,
                textTransform: 'uppercase',
                letterSpacing: '0.06em',
                maxWidth: 72,
              }}
            >
              marked today
            </Text>
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 24,
              marginLeft: 'auto',
              flexWrap: 'wrap',
            }}
          >
            {summaryError ? (
              <Text type="danger" style={{ fontSize: 13 }}>
                {forbiddenOr(
                  summaryError,
                  "No permission to view attendance monitoring.",
                  "Couldn't load the summary.",
                )}
              </Text>
            ) : (
              <>
                <SummaryStat label="Present" value={pulse?.present} color={token.colorSuccess} />
                <SummaryStat label="Absent" value={pulse?.absent} color={token.colorError} />
                <SummaryStat label="On leave" value={pulse?.onLeave} color={token.colorInfo} />
              </>
            )}
            <DatePicker
              value={date}
              onChange={(d) => {
                if (!d) return;
                setDate(d);
                setDatePinned(!d.isSame(dayjs(), 'day'));
              }}
              format="DD MMM YYYY"
              allowClear={false}
            />
          </div>
        </div>
      </Card>

      {/* ---- Body: two columns filling the rest of the screen ------------- */}
      <div
        style={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          gap: 16,
        }}
      >
        {/* Left — recent activity: card no + name */}
        <PanelCard
          icon={LogIn}
          title="Recent activity"
          meta={`${punchRows.length} shown`}
        >
          {punchesQuery.isLoading && punchRows.length === 0 ? (
            <Skeleton active paragraph={{ rows: 8 }} />
          ) : punchRows.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="No attendance marked yet"
              style={{ marginTop: 40 }}
            />
          ) : (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {punchRows.map((row, i) => (
                <ActivityRow key={row.punch.id} row={row} newest={i === 0} token={token} />
              ))}
            </ul>
          )}
        </PanelCard>

        {/* Right — realtime per-department count */}
        <PanelCard
          icon={Building2}
          title="Departments"
          meta={`${departments.length} total`}
        >
          {recordsQuery.isLoading && departments.length === 0 ? (
            <Skeleton active paragraph={{ rows: 8 }} />
          ) : departments.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="No departments"
              style={{ marginTop: 40 }}
            />
          ) : (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {departments.map((d) => (
                <li
                  key={d.id}
                  style={{
                    padding: '10px 2px',
                    borderBottom: `1px solid ${token.colorBorderSecondary}`,
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                      marginBottom: 6,
                    }}
                  >
                    <span
                      style={{
                        flex: 1,
                        minWidth: 0,
                        fontSize: 13,
                        fontWeight: 500,
                        color: token.colorText,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {d.name}
                    </span>
                    <span
                      style={{
                        fontSize: 16,
                        fontWeight: 700,
                        fontVariantNumeric: 'tabular-nums',
                        color: d.present > 0 ? token.colorText : token.colorTextQuaternary,
                      }}
                    >
                      <AnimatedNumber value={d.present} />
                    </span>
                  </div>
                  <div
                    style={{
                      height: 5,
                      borderRadius: 3,
                      background: token.colorFillQuaternary,
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        height: '100%',
                        width: `${(d.present / maxPresent) * 100}%`,
                        borderRadius: 3,
                        background: token.colorSuccess,
                        transition: 'width 500ms cubic-bezier(0.22, 0.61, 0.36, 1)',
                      }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </PanelCard>
      </div>
    </div>
  );
}

/* --------------------------------- Parts ---------------------------------- */

function SummaryStat({
  label,
  value,
  color,
}: {
  label: string;
  value: number | undefined;
  color: string;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 56 }}>
      <span
        style={{
          fontSize: 11,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          opacity: 0.7,
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontSize: 22,
          fontWeight: 700,
          lineHeight: 1,
          color,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value === undefined ? '—' : <AnimatedNumber value={value} />}
      </span>
    </div>
  );
}

function PanelCard({
  icon: Icon,
  title,
  meta,
  children,
}: {
  icon: typeof LogIn;
  title: string;
  meta: string;
  children: React.ReactNode;
}) {
  return (
    <Card
      style={{
        flex: 1,
        minWidth: 0,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
      styles={{
        body: {
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          padding: '4px 18px 14px',
        },
      }}
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Icon size={16} strokeWidth={1.75} />
          <span style={{ fontSize: 14, fontWeight: 600 }}>{title}</span>
        </span>
      }
      extra={
        <Text type="secondary" style={{ fontSize: 12 }}>
          {meta}
        </Text>
      }
    >
      {children}
    </Card>
  );
}

function ActivityRow({
  row,
  newest,
  token,
}: {
  row: AttendancePunchesRow;
  newest: boolean;
  token: ReturnType<typeof useToken>['token'];
}) {
  const name = row.employee?.fullName ?? `Employee #${row.punch.employeeId}`;
  const cardNo = row.employee?.employeeCode;
  const dept = row.employee?.departmentName;
  const initials = name
    .split(' ')
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase();

  return (
    <li
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '10px 4px',
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
        borderLeft: newest ? `2px solid ${token.colorSuccess}` : '2px solid transparent',
        paddingLeft: 8,
      }}
    >
      <span
        aria-hidden
        style={{
          width: 34,
          height: 34,
          borderRadius: '50%',
          flexShrink: 0,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 12,
          fontWeight: 600,
          background: `color-mix(in srgb, ${token.colorPrimary} 14%, transparent)`,
          color: token.colorPrimary,
        }}
      >
        {initials || '—'}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: 13,
            fontWeight: 500,
            color: token.colorText,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {name}
        </div>
        <div
          style={{
            fontSize: 12,
            color: token.colorTextTertiary,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {cardNo ? `Card ${cardNo}` : `ID ${row.punch.employeeId}`}
          {dept ? ` · ${dept}` : ''}
        </div>
      </div>
      <span
        style={{
          fontSize: 12,
          fontVariantNumeric: 'tabular-nums',
          color: token.colorTextSecondary,
          flexShrink: 0,
        }}
      >
        {formatClock(row.punch.punchedAt)}
      </span>
    </li>
  );
}
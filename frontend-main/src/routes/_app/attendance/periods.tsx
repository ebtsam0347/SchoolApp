// /attendance/periods — payroll-period lock administration.
//
// HR locks a (year, month) once attendance for the period is reconciled, so
// that punch ingest, regularizations, and operator amends are refused
// thereafter. Payroll uses the lock as a precondition to compute. Unlocking
// is intentionally a discrete action with confirmation — it should be rare.
//
// Layout: year selector + a 12-month grid of lock cards. Each card shows the
// month name, lock state, lock metadata when present, and the appropriate
// primary action (Lock or Unlock). Refreshes every 30 seconds while open.

import { useMemo, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntdApp,
  Button,
  Select,
  Space,
  Tooltip,
  Typography,
  theme as antdTheme,
} from 'antd';
import dayjs from 'dayjs';
import { CheckCircle2, Lock, RefreshCcw, Unlock } from 'lucide-react';

import { PageHeader, RequestState, Section, StatusPill } from '@/components';
import { AttendanceTabs } from '@/attendance/admin';
import { Can } from '@/auth';
import { forbiddenOr, getErrorMessage } from '@/lib';
import {
  attendancePeriodsApi,
  formatDateTime,
  type AttendancePeriodLock,
} from '@/attendance';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/attendance/periods')({
  component: PeriodLocksPage,
});

const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

function yearOptions() {
  const cur = new Date().getFullYear();
  return [cur + 1, cur, cur - 1, cur - 2, cur - 3].map((y) => ({
    value: y,
    label: String(y),
  }));
}

function PeriodLocksPage() {
  const queryClient = useQueryClient();
  const { message, modal } = AntdApp.useApp();
  const [year, setYear] = useState(() => new Date().getFullYear());

  const locksQuery = useQuery({
    queryKey: ['admin', 'attendance', 'periods', 'locks', year],
    queryFn: ({ signal }) => attendancePeriodsApi.locks(year, signal),
    refetchInterval: 30_000,
  });

  const lockMutation = useMutation({
    mutationFn: ({ year, month }: { year: number; month: number }) =>
      attendancePeriodsApi.lock(year, month),
    onSuccess: (_, vars) => {
      void queryClient.invalidateQueries({
        queryKey: ['attendance', 'periods', 'locks'],
      });
      message.success(`${MONTHS[vars.month - 1]} ${vars.year} locked.`);
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const unlockMutation = useMutation({
    mutationFn: ({ year, month }: { year: number; month: number }) =>
      attendancePeriodsApi.unlock(year, month),
    onSuccess: (_, vars) => {
      void queryClient.invalidateQueries({
        queryKey: ['attendance', 'periods', 'locks'],
      });
      message.success(`${MONTHS[vars.month - 1]} ${vars.year} unlocked.`);
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const lockByMonth = useMemo(() => {
    const map = new Map<number, AttendancePeriodLock>();
    for (const l of locksQuery.data ?? []) map.set(l.periodMonth, l);
    return map;
  }, [locksQuery.data]);

  const today = dayjs();
  const isFutureMonth = (month: number) =>
    year > today.year() || (year === today.year() && month - 1 > today.month());

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <AttendanceTabs />
      <PageHeader
        eyebrow="Attendance"
        title="Period locks"
        description="Lock a payroll period once attendance is reconciled. Locked periods refuse punch ingest, regularizations, and operator amends until unlocked."
        level={4}
        actions={
          <Space>
            <Select
              value={year}
              onChange={setYear}
              options={yearOptions()}
              style={{ width: 110 }}
            />
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => locksQuery.refetch()}
                loading={locksQuery.isFetching && !locksQuery.isLoading}
                aria-label="Refresh"
              />
            </Tooltip>
          </Space>
        }
      />

      <RequestState
        loading={locksQuery.isLoading}
        error={locksQuery.isError ? locksQuery.error : undefined}
        errorTitle={forbiddenOr(
          locksQuery.error,
          "You don't have permission to manage period locks",
          "Couldn't load period locks",
        )}
      >
        <Section
          title={`${year}`}
          subtitle={`${lockByMonth.size} of 12 month${lockByMonth.size === 1 ? '' : 's'} locked.`}
        >
          <div
            style={{
              display: 'grid',
              gap: 12,
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            }}
          >
            {MONTHS.map((label, i) => {
              const month = i + 1;
              const lock = lockByMonth.get(month) ?? null;
              return (
                <PeriodCard
                  key={month}
                  year={year}
                  month={month}
                  monthLabel={label}
                  lock={lock}
                  isFuture={isFutureMonth(month)}
                  isPending={
                    (lockMutation.isPending &&
                      lockMutation.variables?.year === year &&
                      lockMutation.variables?.month === month) ||
                    (unlockMutation.isPending &&
                      unlockMutation.variables?.year === year &&
                      unlockMutation.variables?.month === month)
                  }
                  onLock={() =>
                    modal.confirm({
                      title: `Lock ${label} ${year}?`,
                      content:
                        'Punch ingest, regularizations, and operator amends for this period will be refused. Unlock if you need to make corrections.',
                      okText: 'Lock period',
                      onOk: () => lockMutation.mutateAsync({ year, month }),
                    })
                  }
                  onUnlock={() =>
                    modal.confirm({
                      title: `Unlock ${label} ${year}?`,
                      content:
                        'The period accepts punch / regularization / amend writes again. Lock again once corrections are done — payroll won\'t compute against an unlocked period.',
                      okText: 'Unlock',
                      okButtonProps: { danger: true },
                      onOk: () => unlockMutation.mutateAsync({ year, month }),
                    })
                  }
                />
              );
            })}
          </div>
        </Section>
      </RequestState>
    </div>
  );
}

function PeriodCard({
  year,
  month,
  monthLabel,
  lock,
  isFuture,
  isPending,
  onLock,
  onUnlock,
}: {
  year: number;
  month: number;
  monthLabel: string;
  lock: AttendancePeriodLock | null;
  isFuture: boolean;
  isPending: boolean;
  onLock: () => void;
  onUnlock: () => void;
}) {
  const { token } = useToken();
  const locked = lock !== null;
  const accent = locked ? token.colorSuccess : token.colorWarning;

  return (
    <article
      style={{
        padding: 16,
        borderRadius: 12,
        background: token.colorBgContainer,
        border: `1px solid color-mix(in srgb, ${accent} 22%, ${token.colorBorderSecondary})`,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        opacity: isFuture && !locked ? 0.65 : 1,
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 8,
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: token.colorText }}>
            {monthLabel}
          </div>
          <Text type="secondary" style={{ fontSize: 11, fontVariantNumeric: 'tabular-nums' }}>
            {String(month).padStart(2, '0')}/{year}
          </Text>
        </div>
        <StatusPill tone={locked ? 'success' : 'warning'} size="sm">
          {locked ? 'Locked' : 'Open'}
        </StatusPill>
      </header>

      <div style={{ minHeight: 38, fontSize: 12 }}>
        {locked ? (
          <Space direction="vertical" size={2} style={{ width: '100%' }}>
            <Text type="secondary" style={{ fontSize: 11 }}>
              Locked {formatDateTime(lock!.lockedAt)}
            </Text>
            <Text type="secondary" style={{ fontSize: 11 }}>
              by user #{lock!.lockedBy}
            </Text>
          </Space>
        ) : isFuture ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            Future period — wait until the month closes.
          </Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            Lock once attendance for the month is reconciled.
          </Text>
        )}
      </div>

      <Can I="manage" a="attendance">
        <div>
          {locked ? (
            <Button
              size="small"
              danger
              icon={<Unlock size={13} strokeWidth={1.75} />}
              onClick={onUnlock}
              loading={isPending}
            >
              Unlock
            </Button>
          ) : (
            <Button
              size="small"
              type="primary"
              icon={<Lock size={13} strokeWidth={1.75} />}
              onClick={onLock}
              loading={isPending}
              disabled={isFuture}
            >
              Lock
            </Button>
          )}
          {locked && (
            <CheckCircle2
              size={13}
              strokeWidth={2}
              style={{
                marginLeft: 8,
                verticalAlign: -2,
                color: token.colorSuccess,
              }}
              aria-hidden
            />
          )}
        </div>
      </Can>
    </article>
  );
}

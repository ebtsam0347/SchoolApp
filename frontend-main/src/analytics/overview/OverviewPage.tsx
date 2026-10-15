// /analytics — six-tile executive overview from /bff/analytics/executive-overview.
//
// The composite endpoint folds the latest values from all four service-side
// analytics calls (people / attendance / leaves / payroll) into one payload.
// Each field can be `null` when its downstream is unavailable — render those
// as "—" rather than letting the whole page error out.
//
// Tiles are deliberately simple: label + value + delta-line (when sensible)
// + a trailing arrow that links into the matching domain page so an HR
// reader can drill from "what" to "why" with one click.

import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Space } from 'antd';
import {
  CalendarClock,
  Clock,
  TrendingDown,
  Users,
  UserX,
  Wallet,
} from 'lucide-react';

import {
  KpiGrid,
  MoneyCell,
  PageHeader,
  RequestState,
  StatCard,
} from '@/components';
import { forbiddenOr } from '@/lib';

import { fetchExecutiveOverview } from '../bff-api';
import type { ExecutiveOverviewDto } from '../types';

interface TileSpec {
  label: string;
  to: string;
  icon: typeof Users;
  /** Render the tile's value from the overview payload. */
  render: (data: ExecutiveOverviewDto) => {
    value: React.ReactNode;
    delta?: React.ReactNode;
  };
}

function formatPct(value: number | null, suffix = '%'): string {
  return Number.isFinite(value) ? `${(value as number).toFixed(2)}${suffix}` : '—';
}

function formatHours(value: number | null): string {
  return Number.isFinite(value) ? `${(value as number).toFixed(1)} h` : '—';
}

function formatNumber(value: number | null): string {
  return Number.isFinite(value)
    ? (value as number).toLocaleString('en-PK')
    : '—';
}

const TILES: TileSpec[] = [
  {
    label: 'Headcount',
    to: '/analytics/people',
    icon: Users,
    render: (d) => ({ value: formatNumber(d.headcount) }),
  },
  {
    label: 'Attrition · rolling 12m',
    to: '/analytics/people',
    icon: UserX,
    render: (d) => ({ value: formatPct(d.attritionRolling12mPct) }),
  },
  {
    label: 'On-time · latest month',
    to: '/analytics/attendance',
    icon: CalendarClock,
    render: (d) => ({ value: formatPct(d.latestMonthOnTimePct) }),
  },
  {
    label: 'Absenteeism · latest month',
    to: '/analytics/attendance',
    icon: TrendingDown,
    render: (d) => ({ value: formatPct(d.latestMonthAbsenteeismPct) }),
  },
  {
    label: 'Overtime · latest month',
    to: '/analytics/attendance',
    icon: Clock,
    render: (d) => ({ value: formatHours(d.latestMonthOtHours) }),
  },
  {
    label: 'Payroll cost · latest month',
    to: '/analytics/payroll',
    icon: Wallet,
    render: (d) => ({
      value: Number.isFinite(d.latestMonthPayrollCost) ? (
        <MoneyCell amount={d.latestMonthPayrollCost as number} compact />
      ) : (
        '—'
      ),
    }),
  },
];

export function OverviewPage() {
  const overviewQuery = useQuery({
    queryKey: ['analytics', 'executive-overview'],
    queryFn: ({ signal }) => fetchExecutiveOverview({ signal }),
    // Snapshot view — reasonable to cache for a couple of minutes so
    // tile-tile-page navigation doesn't refetch.
    staleTime: 2 * 60 * 1000,
  });

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="Insights"
        title="Analytics"
        description="A snapshot across the organisation — drill into any tile for the full trend."
      />

      <RequestState
        loading={overviewQuery.isLoading}
        error={overviewQuery.error}
        errorTitle={forbiddenOr(
          overviewQuery.error,
          "You don't have permission to view analytics",
          "Couldn't load the overview",
        )}
        onRetry={() => overviewQuery.refetch()}
      >
        {overviewQuery.data ? (
          <KpiGrid minColumnWidth={240}>
            {TILES.map((tile) => {
              const { value, delta } = tile.render(overviewQuery.data!);
              return (
                <Link
                  key={tile.label}
                  to={tile.to}
                  style={{ textDecoration: 'none', display: 'block' }}
                >
                  <StatCard
                    label={tile.label}
                    value={value}
                    delta={delta}
                    icon={tile.icon}
                    variant="accent-corner"
                    accentCorner="tr"
                  />
                </Link>
              );
            })}
          </KpiGrid>
        ) : null}
      </RequestState>
    </Space>
  );
}

// Employee search + selected-employee information panel for manual entry.
//
// Backed by `GET /employees/picker?limit=20` — the same lightweight
// typeahead every other "pick an ACTIVE employee" surface uses. It matches
// on both name and code, which suits the canteen desk: they normally read
// the code off the card in front of them, but can fall back to a name when
// the card is unreadable.
//
// Searching starts on the first character — employee codes are short and a
// single digit is a valid one, so any minimum length would make those
// employees unreachable.
//
// Typing a code that matches exactly selects that employee outright; any
// other query lists candidates to click. Either way the person's details
// render before the entry is saved, so the clerk can confirm they are
// stamping the right meal against the right employee.
//
// Controlled on `value` (employeeId). The resolved candidate is handed back
// through `onChange` so callers can name the person in their success toast.

import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Empty, Input, Spin, Tag, Typography, theme as antdTheme } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { IdCard, Search, UtensilsCrossed } from 'lucide-react';

import { KvList, PersonCell } from '@/components';
import { getErrorMessage, useDebounced } from '@/lib';
import { formatDateShort } from '@/attendance';
import { employeePickerApi, type EmployeePickerCandidate } from '@/people';
import { canteenUsageApi } from './api';
import type { CanteenUsageDto } from './types';

const { Text } = Typography;
const { useToken } = antdTheme;

/** Server clamps too — matches the limit the brief specified. */
const PICKER_LIMIT = 20;

export type CanteenEmployeeLookupProps = {
  /** Selected employee id, or undefined when nothing is resolved yet. */
  value?: number;
  onChange?: (
    employeeId: number | undefined,
    candidate: EmployeePickerCandidate | null,
  ) => void;
  /** Hide the canteen usage strip (e.g. inside a tight drawer). */
  hideUsage?: boolean;
  /**
   * Month the usage summary covers — normally the attendance date being
   * entered, so the totals describe the period the new entry lands in rather
   * than whatever month it happens to be today. Defaults to the current month.
   */
  summaryMonth?: Dayjs;
  disabled?: boolean;
};

function codeOf(c: EmployeePickerCandidate): string {
  return (c.employeeCode ?? '').trim().toUpperCase();
}

export function CanteenEmployeeLookup({
  value,
  onChange,
  hideUsage,
  summaryMonth,
  disabled,
}: CanteenEmployeeLookupProps) {
  const { token } = useToken();
  const [query, setQuery] = useState('');
  const debounced = useDebounced(query.trim(), 350);

  const candidatesQuery = useQuery({
    queryKey: ['canteen', 'employee-picker', debounced],
    queryFn: ({ signal }) =>
      employeePickerApi.search({ q: debounced, limit: PICKER_LIMIT }, signal),
    enabled: debounced.length > 0,
    staleTime: 30_000,
  });

  const candidates = useMemo(
    () => candidatesQuery.data?.items ?? [],
    [candidatesQuery.data],
  );

  const exact = useMemo(() => {
    const wanted = debounced.toUpperCase();
    return candidates.find((c) => codeOf(c) === wanted) ?? null;
  }, [candidates, debounced]);

  const selected = useMemo(
    () => candidates.find((c) => c.id === value) ?? null,
    [candidates, value],
  );

  // Auto-select on an exact code hit; clear the selection when the operator
  // edits the query away from whatever is currently resolved. Callers pass
  // an inline `onChange`, so this re-runs on every parent render — both
  // branches are guarded on `value` and no-op once the parent has caught up,
  // which is what keeps that from looping.
  const isFetchingCandidates = candidatesQuery.isFetching;
  useEffect(() => {
    if (exact && exact.id !== value) {
      onChange?.(exact.id, exact);
      return;
    }
    if (!exact && value !== undefined && selected === null && !isFetchingCandidates) {
      onChange?.(undefined, null);
    }
  }, [exact, value, selected, isFetchingCandidates, onChange]);

  const active = exact ?? selected;

  // Whole calendar month around the date being entered — start to end, not
  // month-to-date, so a back-dated entry still reports its own month in full.
  const month = summaryMonth ?? dayjs();
  const from = month.startOf('month').format('YYYY-MM-DD');
  const to = month.endOf('month').format('YYYY-MM-DD');

  const usageQuery = useQuery({
    queryKey: ['canteen', 'usage', active?.id, from, to],
    queryFn: ({ signal }) =>
      canteenUsageApi.forEmployee(active?.id ?? 0, { from, to }, signal),
    enabled: !hideUsage && !!active?.id,
    staleTime: 60_000,
    retry: false,
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Input
        allowClear
        disabled={disabled}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        prefix={<Search size={14} strokeWidth={1.75} />}
        suffix={candidatesQuery.isFetching ? <Spin size="small" /> : null}
        placeholder="Employee code or name — e.g. 1042"
        size="large"
      />

      {debounced.length === 0 ? (
        <Text type="secondary" style={{ fontSize: 12 }}>
          Search by employee code or name. An exact employee-code match is
          selected automatically.
        </Text>
      ) : candidatesQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Could not search for that employee"
          description={getErrorMessage(candidatesQuery.error)}
        />
      ) : active ? (
        <SelectedEmployeeCard
          candidate={active}
          usage={usageQuery.data ?? null}
          usageLoading={usageQuery.isFetching}
          usageError={usageQuery.isError}
          hideUsage={hideUsage}
          from={from}
          to={to}
        />
      ) : candidatesQuery.isFetching ? (
        <div style={{ padding: 16, textAlign: 'center' }}>
          <Spin size="small" />
        </div>
      ) : candidates.length > 0 ? (
        <Card size="small" styles={{ body: { padding: 8 } }}>
          <Text type="secondary" style={{ fontSize: 11, paddingLeft: 8 }}>
            {candidates.length} match{candidates.length === 1 ? '' : 'es'} — pick one:
          </Text>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2, marginTop: 6 }}>
            {candidates.map((c) => (
              <button
                key={c.id}
                type="button"
                onClick={() => {
                  onChange?.(c.id, c);
                  setQuery(c.employeeCode ?? c.fullName);
                }}
                style={{
                  display: 'block',
                  width: '100%',
                  textAlign: 'left',
                  border: 'none',
                  background: 'transparent',
                  padding: '6px 8px',
                  borderRadius: 8,
                  cursor: 'pointer',
                }}
              >
                <PersonCell
                  size="compact"
                  person={{
                    fullName: c.fullName,
                    employeeCode: c.employeeCode,
                    designation: c.designationName,
                  }}
                />
              </button>
            ))}
          </div>
        </Card>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <Text type="secondary" style={{ fontSize: 12 }}>
              No active employee matches that search.
            </Text>
          }
          style={{
            margin: 0,
            padding: 16,
            border: `1px dashed ${token.colorBorderSecondary}`,
            borderRadius: 12,
          }}
        />
      )}
    </div>
  );
}

/** Confirmation panel — who exactly this entry is about to be saved against. */
function SelectedEmployeeCard({
  candidate,
  usage,
  usageLoading,
  usageError,
  hideUsage,
  from,
  to,
}: {
  candidate: EmployeePickerCandidate;
  usage: CanteenUsageDto | null;
  usageLoading: boolean;
  usageError: boolean;
  hideUsage?: boolean;
  from: string;
  to: string;
}) {
  // Entries are ledger rows; meals sums their counts. Showing both makes the
  // gap visible — 4 entries covering 8 meals means double helpings.
  const entryCount = usage?.attendanceEntryCount;
  const mealCount = usage?.canteenMealCount;
  const hasUsage = entryCount != null || mealCount != null;
  return (
    <Card size="small" styles={{ body: { padding: 16 } }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          flexWrap: 'wrap',
          marginBottom: 14,
        }}
      >
        <PersonCell
          size="default"
          person={{
            fullName: candidate.fullName,
            employeeCode: candidate.employeeCode,
            designation: candidate.designationName,
          }}
        />
        <Tag icon={<IdCard size={12} />} style={{ margin: 0 }}>
          {candidate.employeeCode ?? `#${candidate.id}`}
        </Tag>
      </div>

      <KvList
        size="sm"
        minColumnWidth={150}
        items={[
          { label: 'Employee code', value: candidate.employeeCode ?? '—' },
          { label: 'Full name', value: candidate.fullName },
          { label: 'Department', value: candidate.departmentName ?? '—' },
          { label: 'Designation', value: candidate.designationName ?? '—' },
        ]}
      />

      {hideUsage ? null : (
        <div
          style={{
            marginTop: 14,
            paddingTop: 12,
            borderTop: '1px solid rgba(128,128,128,0.18)',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
          }}
        >
          <UtensilsCrossed size={14} strokeWidth={1.75} />
          <Text type="secondary" style={{ fontSize: 12 }}>
            Canteen usage {formatDateShort(from)} – {formatDateShort(to)}:
          </Text>
          {usageError ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              unavailable
            </Text>
          ) : usageLoading && !usage ? (
            <Spin size="small" />
          ) : !hasUsage ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              no entries in this range
            </Text>
          ) : (
            <>
              <Tag color="blue" style={{ margin: 0 }}>
                {entryCount ?? 0} {entryCount === 1 ? 'entry' : 'entries'}
              </Tag>
              <Tag color="green" style={{ margin: 0 }}>
                {mealCount ?? 0} {mealCount === 1 ? 'meal' : 'meals'}
              </Tag>
            </>
          )}
        </div>
      )}
    </Card>
  );
}

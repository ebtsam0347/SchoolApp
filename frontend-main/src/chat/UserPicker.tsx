// Searchable user picker. Backed by /bff/users/search — substring match
// on username + fullName, ACTIVE only, authenticated-only (no
// employee.read or admin gate). Returns the same shape as
// /bff/users/by-ids so we can seed the resolver cache directly.
//
// Multi-select variant via `multiple`. Mirrors AntD's controlled Select
// so it slots into Form.Item like any other numeric input.

import { useQuery } from '@tanstack/react-query';
import { Avatar, Select, Spin } from 'antd';
import { useEffect, useState } from 'react';
import { useDebounced } from '@/lib';
import { chatBff } from './bff-api';
import { seedUsers, fromIdentitySummary } from './user-resolver';
import { initialsOf } from './format';

export type UserPickerCandidate = {
  userId: number;
  username: string;
  fullName: string;
  profilePictureUrl: string | null;
  status: string | null;
};

type BaseProps = {
  placeholder?: string;
  disabled?: boolean;
  /** Server-side cap; the BFF clamps to 50 either way. */
  limit?: number;
  /** UserIds to exclude (e.g. self in DM picker; existing members in group add). */
  excludeUserIds?: number[];
  style?: React.CSSProperties;
  /** Allow clearing the selection. */
  allowClear?: boolean;
};

export type UserPickerProps =
  | (BaseProps & {
      multiple?: false;
      value?: number;
      onChange?: (value: number | undefined) => void;
    })
  | (BaseProps & {
      multiple: true;
      value?: number[];
      onChange?: (value: number[]) => void;
    });

export function UserPicker(props: UserPickerProps) {
  const {
    placeholder = 'Search by name or username',
    disabled,
    limit = 20,
    excludeUserIds = [],
    style,
    allowClear = true,
  } = props;
  const [q, setQ] = useState<string>('');
  const debouncedQ = useDebounced(q.trim(), 200);

  // /bff/users/search requires a non-empty q. We disable the query
  // until the user has typed at least one character so we don't fire
  // empty-string requests on every focus.
  const candidatesQuery = useQuery({
    queryKey: ['chat', 'user-picker', debouncedQ, limit],
    queryFn: ({ signal }) => chatBff.usersSearch(debouncedQ, limit, signal),
    enabled: debouncedQ.length > 0,
    staleTime: 30_000,
  });

  const exclude = new Set(excludeUserIds);
  const users = candidatesQuery.data?.users ?? [];
  const candidates: UserPickerCandidate[] = users.filter(
    (u) => !exclude.has(u.userId),
  );

  // Seed the resolver cache so chat surfaces (member lists, mention chips,
  // bubble headers) can render names + avatars for anyone the picker has
  // surfaced. Same shape as the rest of the resolver so this is a direct
  // pass-through.
  const queryData = candidatesQuery.data;
  useEffect(() => {
    if (!queryData?.users || queryData.users.length === 0) return;
    seedUsers(
      queryData.users.map((u) => ({ userId: u.userId, display: fromIdentitySummary(u) })),
    );
  }, [queryData]);

  const options = candidates.map((c) => ({
    value: c.userId,
    label: c.fullName,
    data: c,
  }));

  const notFound = candidatesQuery.isFetching ? (
    <Spin size="small" />
  ) : debouncedQ ? (
    'No matches'
  ) : (
    'Type to search'
  );

  if (props.multiple) {
    return (
      <Select<number[]>
        mode="multiple"
        showSearch
        filterOption={false}
        value={props.value}
        onChange={(v) => props.onChange?.(v ?? [])}
        onSearch={setQ}
        placeholder={placeholder}
        disabled={disabled}
        style={style}
        allowClear={allowClear}
        notFoundContent={notFound}
        options={options}
        optionRender={renderOption}
      />
    );
  }
  return (
    <Select<number>
      showSearch
      filterOption={false}
      value={props.value}
      onChange={(v) => props.onChange?.(v ?? undefined)}
      onSearch={setQ}
      placeholder={placeholder}
      disabled={disabled}
      style={style}
      allowClear={allowClear}
      notFoundContent={notFound}
      options={options}
      optionRender={renderOption}
    />
  );
}

function renderOption(opt: unknown) {
  // AntD's `optionRender` types the option as DefaultOptionType but the
  // actual runtime shape is `{ data: { data: ourCandidate } }` — we tucked
  // the candidate into the option's `data` slot.
  const c = (opt as { data: { data: UserPickerCandidate } }).data?.data;
  if (!c) return null;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, lineHeight: 1.2 }}>
      {/* Candidates come straight off /bff/users/search, which already
          carries the signed avatar URL — no resolver lookup needed. */}
      <Avatar size={32} src={c.profilePictureUrl ?? undefined}>
        {initialsOf(c.fullName) || '?'}
      </Avatar>
      <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <span style={{ fontWeight: 500 }}>{c.fullName}</span>
        <span style={{ fontSize: 11, opacity: 0.7 }}>
          @{c.username}
        </span>
      </div>
    </div>
  );
}

// /admin/users — paginated user list with rich filters and bulk actions.
//
// Surfaces every operation auth-service exposes for users:
//   - server-side filters: q (substring across username|fullName|email),
//     status, role, employee-linked tri-state
//   - row-level: open detail drawer, attach/detach role, reset password,
//     unlock, disable / re-enable
//   - bulk: nothing yet (BFF wishlist)
//
// Subcomponents — UsersHeader, UserCell, StatusTag, CreateUserModal,
// ResetPasswordModal — live in src/auth/users-list/. The detail page
// lives at /admin/users/$id and renders via Outlet.

import { useEffect, useState } from 'react';
import {
  createFileRoute,
  Outlet,
  useLocation,
  useNavigate,
  useSearch,
} from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import {
  CircleCheck,
  KeyRound,
  Lock,
  LockOpen,
  Users,
  UserCog,
} from 'lucide-react';
import {
  adminUsersApi,
  prettyRoleCode,
  relTime,
  rolesApi,
  roleTagColor,
  type UserDto,
  type UserStatus,
} from '@/auth';
import {
  CreateUserModal,
  LINKED_OPTIONS,
  ResetPasswordModal,
  STATUS_OPTIONS,
  StatusTag,
  UserCell,
  UsersHeader,
  linkedFilterToParam,
  type LinkedFilter,
  type StatusFilter,
} from '@/auth/users-list';
import {
  ColumnsToggle,
  FilterBar,
  RequestState,
  useMobileRowExpandable,
} from '@/components';
import { getErrorMessage, useDebounced, useTableColumns } from '@/lib';

const { Text } = Typography;

type UsersSearch = {
  q?: string;
  status?: StatusFilter;
  role?: string;
  linked?: LinkedFilter;
  page?: number;
  size?: number;
};

const STATUS_VALUES: ReadonlySet<StatusFilter> = new Set([
  'ALL',
  'ACTIVE',
  'LOCKED',
  'DISABLED',
]);
const LINKED_VALUES: ReadonlySet<LinkedFilter> = new Set([
  'ANY',
  'LINKED',
  'STANDALONE',
]);

function isStatusFilter(v: unknown): v is StatusFilter {
  return typeof v === 'string' && STATUS_VALUES.has(v as StatusFilter);
}
function isLinkedFilter(v: unknown): v is LinkedFilter {
  return typeof v === 'string' && LINKED_VALUES.has(v as LinkedFilter);
}
function numOrUndef(v: unknown): number | undefined {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string' && v && Number.isFinite(Number(v))) return Number(v);
  return undefined;
}

export const Route = createFileRoute('/_app/admin/users')({
  validateSearch: (search): UsersSearch => ({
    q: typeof search.q === 'string' && search.q ? search.q : undefined,
    status: isStatusFilter(search.status) ? search.status : undefined,
    role: typeof search.role === 'string' && search.role ? search.role : undefined,
    linked: isLinkedFilter(search.linked) ? search.linked : undefined,
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
  }),
  component: UsersPage,
});

function UsersPage() {
  // `/admin/users/$id` is registered as a child of this route in the
  // generated tree, so this component is rendered for both the listing
  // and the detail. Defer to the child Outlet whenever the path is
  // deeper than /admin/users — otherwise the table re-renders on top of
  // the detail page.
  const location = useLocation();
  const isDetail = location.pathname.replace(/\/$/, '') !== '/admin/users';
  if (isDetail) return <Outlet />;
  return <UsersListing />;
}

function UsersListing() {
  const navigate = useNavigate({ from: '/admin/users' });
  const search = useSearch({ from: '/_app/admin/users' });
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();

  const page = search.page ?? 1;
  const pageSize = search.size ?? 20;
  const statusFilter: StatusFilter = search.status ?? 'ALL';
  const roleFilter = search.role ?? null;
  const linkedFilter: LinkedFilter = search.linked ?? 'ANY';

  const [searchInput, setSearchInput] = useState(search.q ?? '');
  const [createOpen, setCreateOpen] = useState(false);
  const [resetTarget, setResetTarget] = useState<UserDto | null>(null);

  const debouncedSearch = useDebounced(searchInput.trim(), 250);

  useEffect(() => {
    const trimmed = debouncedSearch || undefined;
    if (trimmed === search.q) return;
    void navigate({
      search: (prev) => ({ ...prev, q: trimmed, page: undefined }),
      replace: true,
    });
  }, [debouncedSearch, search.q, navigate]);

  const updateSearch = (patch: Partial<UsersSearch>) =>
    navigate({
      search: (prev) => ({ ...prev, ...patch, page: undefined }),
      replace: true,
    });

  const usersQuery = useQuery({
    queryKey: [
      'admin',
      'users',
      { page, pageSize, statusFilter, roleFilter, linkedFilter, q: debouncedSearch },
    ],
    queryFn: ({ signal }) =>
      adminUsersApi.list(
        {
          page: page - 1,
          size: pageSize,
          status: statusFilter === 'ALL' ? null : statusFilter,
          q: debouncedSearch || null,
          role: roleFilter,
          employeeLinked: linkedFilterToParam(linkedFilter),
          sort: 'updatedAt,desc',
        },
        signal,
      ),
    placeholderData: (prev) => prev,
  });

  const rolesQuery = useQuery({
    queryKey: ['admin', 'roles'],
    queryFn: ({ signal }) => rolesApi.list(signal),
  });

  const unlockMutation = useMutation({
    mutationFn: (id: number) => adminUsersApi.unlock(id),
    onSuccess: (_, id) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      void queryClient.invalidateQueries({
        queryKey: ['bff', 'admin', 'users', id, 'overview'],
      });
      message.success('Account unlocked.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: UserStatus }) =>
      adminUsersApi.update(id, { status }),
    onSuccess: (_, { id }) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      void queryClient.invalidateQueries({
        queryKey: ['bff', 'admin', 'users', id, 'overview'],
      });
      message.success('User updated.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const disableMutation = useMutation({
    mutationFn: (id: number) => adminUsersApi.disable(id),
    onSuccess: (_, id) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      void queryClient.invalidateQueries({
        queryKey: ['bff', 'admin', 'users', id, 'overview'],
      });
      message.success('User disabled. Active sessions revoked.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const list = usersQuery.data?.content ?? [];
  const totalElements = usersQuery.data?.totalElements;

  const togglableColumns: NonNullable<TableProps<UserDto>['columns']> = [
    {
      key: 'user',
      title: 'User',
      dataIndex: 'fullName',
      width: 280,
      render: (_, user) => <UserCell user={user} />,
    },
    {
      key: 'roles',
      title: 'Roles',
      dataIndex: 'roles',
      width: 240,
      render: (_, user) => (
        <Space size={4} wrap>
          {user.roles.length === 0 ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              —
            </Text>
          ) : (
            user.roles.map((r) => (
              <Tag key={r} color={roleTagColor(r)} style={{ margin: 0 }}>
                {prettyRoleCode(r)}
              </Tag>
            ))
          )}
        </Space>
      ),
    },
    {
      key: 'status',
      title: 'Status',
      dataIndex: 'status',
      width: 140,
      render: (_, user) => <StatusTag user={user} />,
    },
    {
      key: 'lastLoginAt',
      title: 'Last sign-in',
      dataIndex: 'lastLoginAt',
      width: 160,
      render: (v: string | null) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {v ? relTime(v) : 'Never'}
        </Text>
      ),
    },
    {
      key: 'employeeLinked',
      title: 'Linked',
      dataIndex: 'employeeLinked',
      width: 110,
      render: (linked: boolean) =>
        linked ? (
          <Tag color="cyan" style={{ margin: 0 }}>
            Employee
          </Tag>
        ) : (
          <Tag style={{ margin: 0 }}>Standalone</Tag>
        ),
    },
  ];

  const {
    columns: visibleTogglable,
    options: columnOptions,
    toggle: toggleColumn,
    reset: resetColumns,
  } = useTableColumns('admin.users', togglableColumns);

  const actionsColumn: NonNullable<TableProps<UserDto>['columns']>[number] = {
    title: '',
    key: 'actions',
    width: 220,
    render: (_, user) => (
      <Space size={4}>
        <Tooltip title="Manage roles, sessions, login history">
          <Button
            size="small"
            icon={<UserCog size={14} strokeWidth={1.75} />}
            onClick={() =>
              navigate({ to: '/admin/users/$id', params: { id: String(user.id) } })
            }
          >
            Open
          </Button>
        </Tooltip>
        <Tooltip title="Reset password (issues a temp; user must change at next login)">
          <Button
            size="small"
            icon={<KeyRound size={14} strokeWidth={1.75} />}
            onClick={() => setResetTarget(user)}
          />
        </Tooltip>
        {user.status === 'LOCKED' ? (
          <Tooltip title="Unlock — let this user sign in again">
            <Button
              size="small"
              icon={<LockOpen size={14} strokeWidth={1.75} />}
              loading={
                unlockMutation.isPending && unlockMutation.variables === user.id
              }
              onClick={() => unlockMutation.mutate(user.id)}
            />
          </Tooltip>
        ) : null}
        {user.status === 'ACTIVE' ? (
          <Tooltip title="Disable — keeps the row, blocks logins, revokes active sessions">
            <Button
              size="small"
              danger
              loading={
                disableMutation.isPending && disableMutation.variables === user.id
              }
              icon={<Lock size={14} strokeWidth={1.75} />}
              onClick={() =>
                modal.confirm({
                  title: `Disable ${user.username}?`,
                  content:
                    'They will be unable to sign in. Any active sessions are revoked immediately.',
                  okText: 'Disable',
                  okButtonProps: { danger: true },
                  onOk: () => disableMutation.mutateAsync(user.id),
                })
              }
            />
          </Tooltip>
        ) : user.status === 'DISABLED' ? (
          <Tooltip title="Re-enable — allows the user to sign in again">
            <Button
              size="small"
              icon={<CircleCheck size={14} strokeWidth={1.75} />}
              onClick={() =>
                updateStatusMutation.mutateAsync({ id: user.id, status: 'ACTIVE' })
              }
            />
          </Tooltip>
        ) : null}
      </Space>
    ),
  };

  const columns: NonNullable<TableProps<UserDto>['columns']> = [
    ...visibleTogglable,
    actionsColumn,
  ];

  // On mobile, surface hidden columns via expand row. Skip 'user' (already
  // shown as the row header) and 'actions' (renders a button row inline).
  const mobileExpandable = useMobileRowExpandable<UserDto>(
    [...togglableColumns, actionsColumn],
    { skipKeys: ['user', 'actions'] },
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <UsersHeader
        totalElements={totalElements}
        fetching={usersQuery.isFetching && !usersQuery.isLoading}
        onRefresh={() => usersQuery.refetch()}
        onCreate={() => setCreateOpen(true)}
      />

      <FilterBar
        style={{ marginBottom: 'var(--app-space-md)' }}
        search={{
          value: searchInput,
          onChange: (v) => setSearchInput(v),
          placeholder: 'Search name, username, email…',
          width: 360,
        }}
        filters={
          <>
            <Segmented
              options={STATUS_OPTIONS as unknown as { label: string; value: string }[]}
              value={statusFilter}
              onChange={(v) => {
                const next = v as StatusFilter;
                updateSearch({ status: next === 'ALL' ? undefined : next });
              }}
            />
            <Select
              allowClear
              placeholder="Any role"
              value={roleFilter ?? undefined}
              onChange={(v) => updateSearch({ role: v || undefined })}
              options={(rolesQuery.data ?? []).map((r) => ({
                value: r.code,
                label: prettyRoleCode(r.code),
              }))}
              style={{ minWidth: 160 }}
            />
            <Segmented
              options={LINKED_OPTIONS as unknown as { label: string; value: string }[]}
              value={linkedFilter}
              onChange={(v) => {
                const next = v as LinkedFilter;
                updateSearch({ linked: next === 'ANY' ? undefined : next });
              }}
            />
          </>
        }
        actions={
          <ColumnsToggle
            options={columnOptions}
            onToggle={toggleColumn}
            onReset={resetColumns}
          />
        }
        onReset={
          searchInput || statusFilter !== 'ALL' || roleFilter || linkedFilter !== 'ANY'
            ? () => {
                setSearchInput('');
                void navigate({ search: () => ({}), replace: true });
              }
            : undefined
        }
      />

      <RequestState
        loading={usersQuery.isLoading}
        variant="table"
        error={usersQuery.isError ? usersQuery.error : undefined}
        onRetry={() => usersQuery.refetch()}
        empty={!usersQuery.isLoading && list.length === 0}
        emptyIcon={Users}
        emptyTitle="No users yet"
        emptyDescription="Create the first user to get started."
        filterActive={
          Boolean(searchInput) ||
          statusFilter !== 'ALL' ||
          Boolean(roleFilter) ||
          linkedFilter !== 'ANY'
        }
        onClearFilters={() => {
          setSearchInput('');
          void navigate({ search: () => ({}), replace: true });
        }}
        errorTitle="Couldn't load users"
      >
        <Table<UserDto>
          rowKey={(r) => r.id}
          size="middle"
          columns={columns}
          dataSource={list}
          expandable={mobileExpandable}
          loading={usersQuery.isFetching && !usersQuery.isLoading}
          pagination={{
            current: page,
            pageSize,
            total: totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
            onChange: (p, ps) =>
              navigate({
                search: (prev) => ({
                  ...prev,
                  page: p === 1 ? undefined : p,
                  size: ps === 20 ? undefined : ps,
                }),
                replace: true,
              }),
          }}
          scroll={{ x: 980 }}
        />
      </RequestState>

      <CreateUserModal
        open={createOpen}
        roles={rolesQuery.data ?? []}
        onClose={() => setCreateOpen(false)}
        onCreated={(user) => {
          void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
          message.success(`Created ${user.username}.`);
          setCreateOpen(false);
          void navigate({
            to: '/admin/users/$id',
            params: { id: String(user.id) },
          });
        }}
      />

      <ResetPasswordModal
        target={resetTarget}
        onClose={() => setResetTarget(null)}
        onDone={() => {
          message.success(
            'Temporary password set — share it with the user out-of-band.',
          );
          setResetTarget(null);
          void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
        }}
      />
    </div>
  );
}

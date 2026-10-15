import { createFileRoute } from '@tanstack/react-router'
// /admin/roles — catalog of roles and permissions, plus role CRUD.
//
// Reads:
//   - GET /admin/roles            — full role catalog (permissions[] inline)
//   - GET /admin/permissions      — permission catalog (group + description
//                                   are server-decided, not parsed client-side
//                                   from the dotted code)
//
// Writes (auth.roles.write — typically ADMIN):
//   - POST   /admin/roles         — create custom role
//   - PATCH  /admin/roles/{id}    — rename / re-permission
//   - DELETE /admin/roles/{id}    — delete (refuses if seeded or held)
//
// Reverse lookup ("which roles grant `auth.users.read`?") is computed
// client side from /admin/roles since both reads are cheap; the dedicated
// /admin/permissions/{code}/roles endpoint exists for when the catalog
// grows past a few hundred permissions.
//
// Subcomponents — RolesHeader, RoleCard, AddPermissionsPicker,
// PermissionGroup, RoleFormModal — live in src/auth/roles/.

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Skeleton, Space } from 'antd';
import { KeyRound, Lock, Plus, ShieldCheck, Sparkles } from 'lucide-react';
import {
  permissionsApi,
  rolesApi,
  type PermissionDto,
  type RoleDto,
} from '@/auth';
import { FilterBar, KpiGrid, RequestState, Section, StatCard } from '@/components';
import { forbiddenOr } from '@/lib';
import {
  PermissionGroup,
  RoleCard,
  RoleFormModal,
  RolesHeader,
} from '@/auth/roles';

export const Route = createFileRoute('/_app/admin/roles')({
  component: RolesPage,
});

function RolesPage() {
  const rolesQuery = useQuery({
    queryKey: ['admin', 'roles'],
    queryFn: ({ signal }) => rolesApi.list(signal),
    staleTime: 24 * 60 * 60 * 1000,
  });

  const permsQuery = useQuery({
    queryKey: ['admin', 'permissions'],
    queryFn: ({ signal }) => permissionsApi.list(signal),
    staleTime: 24 * 60 * 60 * 1000,
  });

  const [permFilter, setPermFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [editTarget, setEditTarget] = useState<RoleDto | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const rolesData = rolesQuery.data;
  const permsData = permsQuery.data;

  const filteredRoles = (() => {
    const list = rolesData ?? [];
    if (!roleFilter) return list;
    const q = roleFilter.toLowerCase();
    return list.filter(
      (r) =>
        r.code.toLowerCase().includes(q) ||
        r.name.toLowerCase().includes(q) ||
        (r.description ?? '').toLowerCase().includes(q) ||
        r.permissions.some((p) => p.toLowerCase().includes(q)),
    );
  })();

  const filteredPerms = (() => {
    const list = permsData ?? [];
    if (!permFilter) return list;
    const q = permFilter.toLowerCase();
    return list.filter(
      (p) =>
        p.code.toLowerCase().includes(q) ||
        p.name.toLowerCase().includes(q) ||
        p.group.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q),
    );
  })();

  // Reverse map: permission code → role codes that grant it.
  const reverseMap = (() => {
    const list = rolesData ?? [];
    const m = new Map<string, string[]>();
    for (const r of list) {
      for (const p of r.permissions) {
        if (!m.has(p)) m.set(p, []);
        m.get(p)!.push(r.code);
      }
    }
    return m;
  })();

  // Group permissions by server-supplied `group` (e.g. "Identity"), not by
  // the dotted code prefix. Falls back to "Other" when group is empty.
  const groupedPerms = (() => {
    const groups = new Map<string, PermissionDto[]>();
    for (const p of filteredPerms) {
      const group = p.group || 'Other';
      if (!groups.has(group)) groups.set(group, []);
      groups.get(group)!.push(p);
    }
    return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
  })();

  const totalRoles = (rolesData ?? []).length;
  const customRoles = (rolesData ?? []).filter((r) => !r.system).length;
  const seededRoles = totalRoles - customRoles;
  const totalPermissions = (permsData ?? []).length;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
      }}
    >
      <RolesHeader
        totalRoles={totalRoles}
        totalPermissions={totalPermissions}
        onCreate={() => setCreateOpen(true)}
      />

      <KpiGrid>
        <StatCard
          label="Total roles"
          value={rolesQuery.isLoading ? '…' : totalRoles.toLocaleString()}
          icon={ShieldCheck}
          tone="info"
          variant="accent-border"
        />
        <StatCard
          label="Custom roles"
          value={rolesQuery.isLoading ? '…' : customRoles.toLocaleString()}
          icon={Sparkles}
          tone="primary"
          variant="accent-border"
        />
        <StatCard
          label="Seeded roles"
          value={rolesQuery.isLoading ? '…' : seededRoles.toLocaleString()}
          icon={Lock}
          tone="neutral"
          variant="accent-border"
        />
        <StatCard
          label="Permissions"
          value={permsQuery.isLoading ? '…' : totalPermissions.toLocaleString()}
          icon={KeyRound}
          tone="success"
          variant="accent-border"
        />
      </KpiGrid>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))',
          gap: 'var(--app-space-lg)',
        }}
      >
        <Section
          title={
            <Space>
              <ShieldCheck size={16} strokeWidth={1.75} />
              Roles
            </Space>
          }
          subtitle="Built-in roles can be edited but not deleted."
          actions={
            <Button
              type="primary"
              size="small"
              icon={<Plus size={14} strokeWidth={1.75} />}
              onClick={() => setCreateOpen(true)}
            >
              New role
            </Button>
          }
        >
          <FilterBar
            style={{ marginBottom: 'var(--app-space-md)' }}
            search={{
              value: roleFilter,
              onChange: setRoleFilter,
              placeholder: 'Search by role, name, description, permission…',
              width: 360,
            }}
            onReset={roleFilter ? () => setRoleFilter('') : undefined}
          />
          <RequestState
            error={rolesQuery.isError ? rolesQuery.error : undefined}
            empty={!rolesQuery.isLoading && filteredRoles.length === 0}
            emptyTitle="No roles match"
            emptyDescription={
              roleFilter
                ? 'Try a different search term or clear the filter.'
                : 'Roles are seeded from configuration — none have been defined yet.'
            }
            errorTitle={forbiddenOr(
              rolesQuery.error,
              "You don't have permission to view roles",
              "Couldn't load roles",
            )}
            loadingFallback={<Skeleton active paragraph={{ rows: 4 }} />}
            loading={rolesQuery.isLoading}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {filteredRoles.map((r) => (
                <RoleCard
                  key={r.id}
                  role={r}
                  allPermissions={permsData ?? []}
                  onEdit={() => setEditTarget(r)}
                />
              ))}
            </div>
          </RequestState>
        </Section>

        <Section
          title={
            <Space>
              <KeyRound size={16} strokeWidth={1.75} />
              Permissions
            </Space>
          }
          subtitle="Permissions gate features. Grouped by area."
        >
          <FilterBar
            style={{ marginBottom: 'var(--app-space-md)' }}
            search={{
              value: permFilter,
              onChange: setPermFilter,
              placeholder: 'Search by code, name, description, group…',
              width: 360,
            }}
            onReset={permFilter ? () => setPermFilter('') : undefined}
          />
          <RequestState
            error={permsQuery.isError ? permsQuery.error : undefined}
            empty={!permsQuery.isLoading && groupedPerms.length === 0}
            emptyTitle="No permissions match"
            emptyDescription={
              permFilter
                ? 'Try a different search term or clear the filter.'
                : 'Permissions are seeded from configuration — none have been defined yet.'
            }
            errorTitle={forbiddenOr(
              permsQuery.error,
              "You don't have permission to view permissions",
              "Couldn't load permissions",
            )}
            loadingFallback={<Skeleton active paragraph={{ rows: 6 }} />}
            loading={permsQuery.isLoading}
          >
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 'var(--app-space-md)',
              }}
            >
              {groupedPerms.map(([group, items]) => (
                <PermissionGroup
                  key={group}
                  name={group}
                  items={items}
                  reverseMap={reverseMap}
                />
              ))}
            </div>
          </RequestState>
        </Section>
      </div>

      <RoleFormModal
        open={createOpen}
        permissions={permsData ?? []}
        onClose={() => setCreateOpen(false)}
      />
      <RoleFormModal
        open={!!editTarget}
        role={editTarget ?? undefined}
        permissions={permsData ?? []}
        onClose={() => setEditTarget(null)}
      />
    </div>
  );
}

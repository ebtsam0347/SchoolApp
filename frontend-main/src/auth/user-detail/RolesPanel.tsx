// Role tags for the user. Click × on a tag to detach (with confirm),
// click "Add role" to open a popover listing the roles the user doesn't
// already have. Permissions count surfaces in the popover so the
// admin sees what they're handing out.

import { useState } from 'react';
import {
  App,
  Popover,
  Space,
  Spin,
  Tag,
  theme as antdTheme,
  Typography,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, ShieldCheck, X } from 'lucide-react';
import { Section } from '@/components';
import {
  adminUsersApi,
  prettyRoleCode,
  roleTagColor,
  type RoleDto,
  type UserDto,
} from '@/auth';
import { getErrorMessage } from '@/lib';
import { overviewKey } from './query-keys';

const { Text } = Typography;
const { useToken } = antdTheme;

export type RolesPanelProps = {
  userId: number;
  user: UserDto;
  catalog: RoleDto[];
};

export function RolesPanel({ userId, user, catalog }: RolesPanelProps) {
  const { token } = useToken();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();

  const attachMutation = useMutation({
    mutationFn: (codes: string[]) => adminUsersApi.attachRoles(userId, { roles: codes }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: overviewKey(userId) });
      message.success('Roles attached.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const detachMutation = useMutation({
    mutationFn: (roleId: number) => adminUsersApi.detachRole(userId, roleId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: overviewKey(userId) });
      message.success('Role detached.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const userRoles = user.roles;
  const available = catalog.filter(
    (r) => !userRoles.includes(r.code) && !r.serviceAccount,
  );
  const [addOpen, setAddOpen] = useState(false);

  return (
    <Section
      title="Roles"
      subtitle="Additive — users can hold multiple. Click × to detach, or + to add."
    >
      <Space size={[6, 6]} wrap>
        {userRoles.length === 0 && (
          <Text type="secondary" style={{ fontSize: 12, fontStyle: 'italic' }}>
            No roles attached — the user can sign in but most features will be hidden.
          </Text>
        )}
        {userRoles.map((code) => {
          const role = catalog.find((r) => r.code === code);
          const removing =
            detachMutation.isPending && detachMutation.variables === role?.id;
          return (
            <Tag
              key={code}
              color={roleTagColor(code)}
              closable={role !== undefined}
              closeIcon={
                removing ? <Spin size="small" /> : <X size={11} strokeWidth={2} />
              }
              onClose={(e) => {
                e.preventDefault();
                if (!role) return;
                modal.confirm({
                  title: `Detach ${prettyRoleCode(code)}?`,
                  content: `${user.username} loses ${role.permissions.length} permission${role.permissions.length === 1 ? '' : 's'} carried by this role.`,
                  okText: 'Detach',
                  okButtonProps: { danger: true },
                  onOk: () => detachMutation.mutateAsync(role.id),
                });
              }}
              style={{
                margin: 0,
                padding: '4px 10px',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 12,
              }}
            >
              <ShieldCheck size={11} strokeWidth={2} />
              {prettyRoleCode(code)}
            </Tag>
          );
        })}
        {available.length > 0 && (
          <Popover
            trigger="click"
            placement="bottomLeft"
            open={addOpen}
            destroyOnHidden
            onOpenChange={setAddOpen}
            content={
              <div style={{ width: 260, display: 'flex', flexDirection: 'column', gap: 4 }}>
                {available.map((r) => (
                  <button
                    key={r.code}
                    type="button"
                    disabled={attachMutation.isPending}
                    onClick={() => {
                      attachMutation.mutate([r.code]);
                      setAddOpen(false);
                    }}
                    style={{
                      textAlign: 'left',
                      padding: '8px 10px',
                      borderRadius: 6,
                      background: 'transparent',
                      border: `1px solid ${token.colorBorderSecondary}`,
                      cursor: 'pointer',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 2,
                    }}
                  >
                    <span
                      style={{ fontSize: 12, fontWeight: 600, color: token.colorText }}
                    >
                      {prettyRoleCode(r.code)}{' '}
                      <span
                        style={{ color: token.colorTextTertiary, fontWeight: 400 }}
                      >
                        — {r.name}
                      </span>
                    </span>
                    {r.description && (
                      <span style={{ fontSize: 11, color: token.colorTextSecondary }}>
                        {r.description}
                      </span>
                    )}
                    <span style={{ fontSize: 10, color: token.colorTextTertiary }}>
                      {r.permissions.length} permission{r.permissions.length === 1 ? '' : 's'}
                    </span>
                  </button>
                ))}
              </div>
            }
          >
            <Tag.CheckableTag
              checked={false}
              style={{
                padding: '4px 10px',
                fontSize: 12,
                border: `1px dashed ${token.colorBorder}`,
                color: token.colorTextSecondary,
                background: 'transparent',
              }}
            >
              <Plus size={11} strokeWidth={2} style={{ verticalAlign: -1, marginRight: 4 }} />
              Add role
            </Tag.CheckableTag>
          </Popover>
        )}
      </Space>
    </Section>
  );
}

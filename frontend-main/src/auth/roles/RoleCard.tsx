// One role card on the /admin/roles page. Permission tags are
// click-to-remove, and a dashed "Add permission" tag opens the
// multi-pick picker. PATCH replaces the full permission set; we rebuild
// the next array client-side and the wire payload looks the same as a
// wholesale edit, but the UX feels surgical.

import { useState } from 'react';
import {
  App,
  Button,
  Popover,
  Space,
  Spin,
  Tag,
  theme as antdTheme,
  Tooltip,
  Typography,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2, X } from 'lucide-react';
import {
  prettyRoleCode,
  roleTagColor,
  rolesApi,
  type PermissionDto,
  type RoleDto,
} from '@/auth';
import { getErrorMessage } from '@/lib';
import { AddPermissionsPicker } from './AddPermissionsPicker';

const { Text } = Typography;
const { useToken } = antdTheme;

// AntD Tag color tokens we set on the role card ↔ accent stripe colors.
// Falls back to colorPrimary in the card when a role has no entry.
const ROLE_TAG_HEX: Record<string, string> = {
  purple: '#722ed1',
  blue: '#1677ff',
  gold: '#d48806',
  cyan: '#08979c',
  default: '#8c8c8c',
};

export type RoleCardProps = {
  role: RoleDto;
  allPermissions: PermissionDto[];
  onEdit: () => void;
};

export function RoleCard({ role, allPermissions, onEdit }: RoleCardProps) {
  const { token } = useToken();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [removingCode, setRemovingCode] = useState<string | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [pendingAdds, setPendingAdds] = useState<string[]>([]);

  const patchPermissions = useMutation({
    mutationFn: (permissions: string[]) => rolesApi.update(role.id, { permissions }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] });
    },
    onError: (e) => message.error(getErrorMessage(e)),
    onSettled: () => {
      setRemovingCode(null);
    },
  });

  const removePermission = (code: string) => {
    setRemovingCode(code);
    patchPermissions.mutate(role.permissions.filter((p) => p !== code));
  };

  const addPermissions = (codes: string[]) => {
    if (codes.length === 0) {
      setAddOpen(false);
      return;
    }
    const next = Array.from(new Set([...role.permissions, ...codes]));
    patchPermissions.mutate(next, {
      onSuccess: () => {
        setPendingAdds([]);
        setAddOpen(false);
        message.success(
          `Added ${codes.length} permission${codes.length === 1 ? '' : 's'} to ${prettyRoleCode(role.code)}.`,
        );
      },
    });
  };

  const deleteMutation = useMutation({
    mutationFn: () => rolesApi.delete(role.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] });
      message.success(`Deleted role ${role.code}.`);
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const available = allPermissions.filter((p) => !role.permissions.includes(p.code));
  const accent = ROLE_TAG_HEX[roleTagColor(role.code)] ?? token.colorPrimary;

  return (
    <article
      style={{
        padding: 'var(--app-space-md)',
        borderRadius: 'var(--app-radius-md)',
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderLeft: `3px solid color-mix(in srgb, ${accent} 60%, ${token.colorBorderSecondary})`,
      }}
    >
      <header
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1fr) auto',
          alignItems: 'flex-start',
          gap: 12,
          marginBottom: role.description ? 6 : 12,
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div
            style={{
              fontSize: 15,
              fontWeight: 600,
              color: token.colorText,
              lineHeight: 1.3,
            }}
          >
            {prettyRoleCode(role.code)}
          </div>
          <div
            style={{
              display: 'flex',
              gap: 8,
              alignItems: 'center',
              flexWrap: 'wrap',
              marginTop: 4,
            }}
          >
            <code
              style={{
                fontSize: 11,
                color: token.colorTextTertiary,
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
              }}
            >
              {role.code}
            </code>
            {role.system ? (
              <Tag style={{ margin: 0, fontSize: 10 }}>Seeded</Tag>
            ) : (
              <Tag color="blue" style={{ margin: 0, fontSize: 10 }}>
                Custom
              </Tag>
            )}
            {role.serviceAccount ? (
              <Tooltip title="Issued for service-to-service tokens, not assigned to people.">
                <Tag color="purple" style={{ margin: 0, fontSize: 10 }}>
                  Service
                </Tag>
              </Tooltip>
            ) : null}
            <span style={{ fontSize: 11, color: token.colorTextTertiary }}>
              {role.permissions.length} permission{role.permissions.length === 1 ? '' : 's'}
            </span>
          </div>
        </div>
        <Space size={2}>
          <Tooltip
            title={
              role.system
                ? 'Edit name, description, or permissions (code is fixed).'
                : 'Edit role'
            }
          >
            <Button
              size="small"
              type="text"
              icon={<Pencil size={13} strokeWidth={1.75} />}
              onClick={onEdit}
            />
          </Tooltip>
          {!role.system && (
            <Tooltip title="Delete custom role (refuses if any user holds it)">
              <Button
                size="small"
                type="text"
                danger
                loading={deleteMutation.isPending}
                icon={<Trash2 size={13} strokeWidth={1.75} />}
                onClick={() =>
                  modal.confirm({
                    title: `Delete role ${role.code}?`,
                    content:
                      'Removes the role and any references on existing users. Refuses if any user currently holds it. Cannot be undone.',
                    okText: 'Delete',
                    okButtonProps: { danger: true },
                    onOk: () => deleteMutation.mutateAsync(),
                  })
                }
              />
            </Tooltip>
          )}
        </Space>
      </header>
      {role.description ? (
        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 10 }}>
          {role.description}
        </Text>
      ) : null}
      <Space size={[6, 6]} wrap>
        {role.permissions.length === 0 && (
          <Text type="secondary" style={{ fontSize: 12, fontStyle: 'italic' }}>
            No permissions attached.
          </Text>
        )}
        {role.permissions.map((code) => {
          const isRemoving = removingCode === code && patchPermissions.isPending;
          return (
            <Tag
              key={code}
              closable
              closeIcon={
                isRemoving ? (
                  <Spin size="small" style={{ marginLeft: 2 }} />
                ) : (
                  <X size={11} strokeWidth={2} />
                )
              }
              onClose={(e) => {
                e.preventDefault();
                if (patchPermissions.isPending) return;
                modal.confirm({
                  title: `Remove ${code}?`,
                  content: `Take ${code} away from ${prettyRoleCode(role.code)}. Anyone holding this role loses the permission immediately.`,
                  okText: 'Remove',
                  okButtonProps: { danger: true },
                  onOk: () => removePermission(code),
                });
              }}
              style={{
                margin: 0,
                padding: '3px 8px',
                fontSize: 11,
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                background: token.colorFillTertiary,
                color: token.colorTextSecondary,
                border: 'none',
                cursor: 'default',
                opacity: patchPermissions.isPending && !isRemoving ? 0.5 : 1,
              }}
            >
              {code}
            </Tag>
          );
        })}
        {available.length > 0 && (
          <Popover
            trigger="click"
            placement="bottomLeft"
            open={addOpen}
            destroyOnHidden
            onOpenChange={(open) => {
              setAddOpen(open);
              if (!open) setPendingAdds([]);
            }}
            content={
              <AddPermissionsPicker
                available={available}
                value={pendingAdds}
                onChange={setPendingAdds}
                onCancel={() => {
                  setAddOpen(false);
                  setPendingAdds([]);
                }}
                onApply={() => addPermissions(pendingAdds)}
                loading={patchPermissions.isPending}
              />
            }
          >
            <Tag.CheckableTag
              checked={false}
              style={{
                padding: '3px 10px',
                fontSize: 11,
                border: `1px dashed ${token.colorBorder}`,
                color: token.colorTextSecondary,
                background: 'transparent',
              }}
            >
              <Plus
                size={11}
                strokeWidth={2}
                style={{ verticalAlign: -1, marginRight: 4 }}
              />
              Add permission
            </Tag.CheckableTag>
          </Popover>
        )}
      </Space>
    </article>
  );
}

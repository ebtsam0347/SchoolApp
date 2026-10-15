// One titled section in the right column: lists every permission in the
// group and shows which roles currently grant it via the reverse map.

import { Space, Tag, theme as antdTheme, Typography } from 'antd';
import {
  prettyRoleCode,
  roleTagColor,
  type PermissionDto,
} from '@/auth';

const { Text } = Typography;
const { useToken } = antdTheme;

export type PermissionGroupProps = {
  name: string;
  items: PermissionDto[];
  reverseMap: Map<string, string[]>;
};

export function PermissionGroup({ name, items, reverseMap }: PermissionGroupProps) {
  const { token } = useToken();
  return (
    <div>
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.06em',
          textTransform: 'uppercase',
          color: token.colorTextTertiary,
          marginBottom: 6,
        }}
      >
        {name}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {items.map((p) => {
          const grants = reverseMap.get(p.code) ?? [];
          return (
            <div
              key={p.id}
              style={{
                display: 'grid',
                gridTemplateColumns: 'minmax(0, 1fr) auto',
                gap: 10,
                alignItems: 'center',
                padding: '8px 12px',
                borderRadius: 'var(--app-radius-sm)',
                background: token.colorBgContainer,
                border: `1px solid ${token.colorBorderSecondary}`,
              }}
            >
              <div style={{ minWidth: 0 }}>
                <code
                  style={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: token.colorText,
                  }}
                >
                  {p.code}
                </code>
                <div style={{ fontSize: 12, color: token.colorTextSecondary, marginTop: 2 }}>
                  {p.name}
                </div>
                {p.description && (
                  <div style={{ fontSize: 11, color: token.colorTextTertiary, marginTop: 2 }}>
                    {p.description}
                  </div>
                )}
              </div>
              <Space size={4} wrap style={{ justifyContent: 'flex-end' }}>
                {grants.length === 0 ? (
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    Unattached
                  </Text>
                ) : (
                  grants.map((code) => (
                    <Tag
                      key={code}
                      color={roleTagColor(code)}
                      style={{ margin: 0, fontSize: 10 }}
                    >
                      {prettyRoleCode(code)}
                    </Tag>
                  ))
                )}
              </Space>
            </div>
          );
        })}
      </div>
    </div>
  );
}

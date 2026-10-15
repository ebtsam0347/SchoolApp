// /account — landing tab is Profile (identity, roles, effective permissions).
//
// Profile and recent activity are separate concerns: identity is stable
// reference data, activity is a stream. Activity lives on its own tab at
// /account/activity. The user-stripe in the parent layout already carries
// at-a-glance facts (last sign-in, sessions count) so this page focuses on
// the more enduring identity data.
//
// auth-service treats `fullName` and `email` as read-only via ESS for
// employee-linked users; the read-only badge + tooltip explains why.

import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import {
  Alert,
  Card,
  Descriptions,
  Empty,
  Input,
  Space,
  Tag,
  Tooltip,
  Typography,
  theme as antdTheme,
} from 'antd';
import {
  AtSign,
  BadgeCheck,
  IdCard,
  Mail,
  Search,
  ShieldCheck,
  UserSquare2,
} from 'lucide-react';
import { useAuth } from '@/auth';
import { Section } from '@/components';

const { Text, Paragraph } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/account/')({
  component: ProfilePage,
});

const ROLE_LABELS: Record<string, { label: string; description: string }> = {
  ADMIN: {
    label: 'Administrator',
    description: 'Full system access — settings, integrations, all modules.',
  },
  HR: {
    label: 'HR Manager',
    description: 'People operations — onboarding, employees, leaves, attendance.',
  },
  PAYROLL: {
    label: 'Payroll',
    description: 'Run payroll, manage components and tax rules.',
  },
  MANAGER: {
    label: 'Manager',
    description: 'Approve leaves and timesheets for your direct reports.',
  },
  EMPLOYEE: {
    label: 'Employee',
    description: 'Self‑service for your own attendance, leaves, and payslips.',
  },
};

function permissionGroup(code: string): string {
  const idx = code.indexOf('.');
  return idx > 0 ? code.slice(0, idx) : 'other';
}

function ProfilePage() {
  const auth = useAuth();
  const { token } = useToken();
  const [filter, setFilter] = useState('');

  if (!auth.user) {
    return <Empty description="Profile unavailable" />;
  }

  const { fullName, username, email, employeeLinked, roles, permissions, lastLoginAt } = auth.user;

  const grouped = Object.entries(
    permissions.reduce<Record<string, string[]>>((acc, p) => {
      const g = permissionGroup(p);
      acc[g] ??= [];
      acc[g].push(p);
      return acc;
    }, {}),
  )
    .map(([key, items]) => [key, items.sort()] as const)
    .sort(([a], [b]) => a.localeCompare(b));

  const visibleGroups = grouped
    .map(([g, items]) => {
      const matched = filter
        ? items.filter((p) => p.toLowerCase().includes(filter.toLowerCase()))
        : items;
      return [g, matched] as const;
    })
    .filter(([, items]) => items.length > 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <Section
        title="Identity"
        subtitle={
          employeeLinked
            ? 'Linked to your employee record. Changes flow from HR — your name and email are managed there.'
            : 'Standalone account. Your name and email are managed by an administrator.'
        }
      >
        <Card styles={{ body: { padding: 24 } }}>
          <Descriptions
            column={{ xs: 1, sm: 1, md: 2 }}
            colon={false}
            styles={{
              label: {
                fontSize: 12,
                textTransform: 'uppercase',
                letterSpacing: '0.06em',
                color: token.colorTextTertiary,
              },
            }}
            items={[
              {
                key: 'fullName',
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <UserSquare2 size={13} strokeWidth={1.75} /> Full name
                  </span>
                ),
                children: <ReadonlyValue value={fullName} reason={employeeLinked ? 'employee' : null} />,
              },
              {
                key: 'username',
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <AtSign size={13} strokeWidth={1.75} /> Username
                  </span>
                ),
                children: (
                  <Tooltip title="Usernames are permanent — they cannot be changed once assigned.">
                    <span style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
                      {username}
                    </span>
                  </Tooltip>
                ),
              },
              {
                key: 'email',
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <Mail size={13} strokeWidth={1.75} /> Email
                  </span>
                ),
                children: email
                  ? <ReadonlyValue value={email} reason="admin" />
                  : <Text type="secondary">Not set</Text>,
              },
              {
                key: 'employeeLinked',
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <IdCard size={13} strokeWidth={1.75} /> Account type
                  </span>
                ),
                children: employeeLinked ? (
                  <Tag color="blue" style={{ margin: 0 }}>
                    Employee‑linked (ESS)
                  </Tag>
                ) : (
                  <Tag style={{ margin: 0 }}>Standalone</Tag>
                ),
              },
              {
                key: 'lastLoginAt',
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <BadgeCheck size={13} strokeWidth={1.75} /> Last sign‑in
                  </span>
                ),
                children: lastLoginAt ? (
                  <span title={new Date(lastLoginAt).toLocaleString()}>
                    {new Date(lastLoginAt).toLocaleString()}
                  </span>
                ) : (
                  <Text type="secondary">First sign‑in</Text>
                ),
              },
            ]}
          />
        </Card>
      </Section>

      <Section
        title="Roles"
        subtitle={`${roles.length} role${roles.length === 1 ? '' : 's'} assigned by your administrator.`}
      >
        {roles.length === 0 ? (
          <Alert
            type="warning"
            showIcon
            title="No roles assigned"
            description="You can sign in but most features will be hidden. Ask your HR admin to grant the appropriate role."
          />
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
              gap: 12,
            }}
          >
            {roles.map((code) => {
              const meta = ROLE_LABELS[code.toUpperCase()] ?? {
                label: code.replace(/_/g, ' '),
                description: 'Custom role.',
              };
              return (
                <Card
                  key={code}
                  styles={{ body: { padding: 16, display: 'flex', gap: 12, alignItems: 'flex-start' } }}
                >
                  <span
                    aria-hidden="true"
                    style={{
                      width: 32,
                      height: 32,
                      borderRadius: 8,
                      flexShrink: 0,
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      background: `color-mix(in srgb, ${token.colorPrimary} 12%, transparent)`,
                      color: token.colorPrimary,
                    }}
                  >
                    <ShieldCheck size={16} strokeWidth={1.75} />
                  </span>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div
                      style={{
                        fontSize: 14,
                        fontWeight: 600,
                        color: token.colorText,
                      }}
                    >
                      {meta.label}
                    </div>
                    <Text
                      type="secondary"
                      style={{ fontSize: 12, lineHeight: 1.5, display: 'block', marginTop: 2 }}
                    >
                      {meta.description}
                    </Text>
                    <code
                      style={{
                        fontSize: 11,
                        color: token.colorTextTertiary,
                        marginTop: 6,
                        display: 'inline-block',
                      }}
                    >
                      {code}
                    </code>
                  </div>
                </Card>
              );
            })}
          </div>
        )}
      </Section>

      <Section
        title="Effective permissions"
        subtitle={`${permissions.length} permission${permissions.length === 1 ? '' : 's'} flattened from your roles.`}
        actions={
          <Input
            allowClear
            size="small"
            placeholder="Filter permissions"
            prefix={<Search size={13} strokeWidth={1.75} />}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            style={{ width: 220 }}
          />
        }
      >
        {permissions.length === 0 ? (
          <Empty description="No permissions" />
        ) : visibleGroups.length === 0 ? (
          <Paragraph type="secondary" style={{ margin: 0 }}>
            No permissions match "{filter}".
          </Paragraph>
        ) : (
          <Space orientation="vertical" size={12} style={{ width: '100%' }}>
            {visibleGroups.map(([group, items]) => (
              <Card
                key={group}
                size="small"
                styles={{ body: { padding: '12px 16px' } }}
                title={
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <code style={{ fontSize: 12, color: token.colorTextSecondary }}>
                      {group}.*
                    </code>
                    <Tag style={{ margin: 0, fontSize: 11 }}>{items.length}</Tag>
                  </span>
                }
              >
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {items.map((p) => (
                    <Tag
                      key={p}
                      style={{
                        margin: 0,
                        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                        fontSize: 11,
                      }}
                    >
                      {p.slice(group.length + 1)}
                    </Tag>
                  ))}
                </div>
              </Card>
            ))}
          </Space>
        )}
      </Section>
    </div>
  );
}

function ReadonlyValue({
  value,
  reason,
}: {
  value: string;
  reason: 'employee' | 'admin' | null;
}) {
  if (!reason) return <span>{value}</span>;
  const tip =
    reason === 'employee'
      ? 'Managed by HR through your employee record.'
      : 'Set by an administrator.';
  return (
    <Tooltip title={tip}>
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
        {value}
        <Tag style={{ margin: 0, fontSize: 10 }}>READ‑ONLY</Tag>
      </span>
    </Tooltip>
  );
}

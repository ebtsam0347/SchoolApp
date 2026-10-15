// Read-only key/value grid for the identity panel. Mostly the same shape
// as our shared KvList, but several rows need monospace + warn-tinted
// values so we keep a thin wrapper here.

import { theme as antdTheme } from 'antd';
import type { ReactNode } from 'react';
import type { UserDto } from '@/auth';

const { useToken } = antdTheme;

type Tile = { label: string; value: ReactNode; mono?: boolean; warn?: boolean };

export function IdentityTiles({ user }: { user: UserDto }) {
  const { token } = useToken();

  const rows: Tile[] = [
    { label: 'User id', value: user.id.toLocaleString(), mono: true },
    { label: 'Username', value: user.username, mono: true },
    { label: 'Full name', value: user.fullName },
    {
      label: 'Email',
      value: user.email ?? <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
    {
      label: 'Account type',
      value: user.employeeLinked ? 'Employee‑linked (ESS)' : 'Standalone',
    },
    {
      label: 'Must change pw',
      value: user.mustChangePw ? 'Yes' : 'No',
      warn: user.mustChangePw,
    },
    {
      label: 'Locked until',
      value: user.lockedUntil ? new Date(user.lockedUntil).toLocaleString() : '—',
      warn: !!user.lockedUntil,
    },
    {
      label: 'Created',
      value: new Date(user.createdAt).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      }),
    },
    {
      label: 'Updated',
      value: new Date(user.updatedAt).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      }),
    },
  ];

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        rowGap: 14,
        columnGap: 24,
      }}
    >
      {rows.map((r) => (
        <div
          key={r.label}
          style={{ minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}
        >
          <span
            style={{
              fontSize: 10,
              fontWeight: 500,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: token.colorTextTertiary,
            }}
          >
            {r.label}
          </span>
          <span
            style={{
              fontSize: 13,
              color: r.warn ? token.colorWarning : token.colorText,
              fontFamily: r.mono
                ? 'ui-monospace, SFMono-Regular, Menlo, monospace'
                : undefined,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={typeof r.value === 'string' ? r.value : undefined}
          >
            {r.value}
          </span>
        </div>
      ))}
    </div>
  );
}

// Compact user cell for the listing — initial-tile avatar + name + handle
// (and email when present). Listing-specific layout; the detail-page hero
// uses UserStripe in user-detail/.

import { theme as antdTheme } from 'antd';
import { initialsOf, type UserDto } from '@/auth';

const { useToken } = antdTheme;

export function UserCell({ user }: { user: UserDto }) {
  const { token } = useToken();
  const initials = initialsOf(user.fullName || user.username);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
      <span
        aria-hidden="true"
        style={{
          width: 34,
          height: 34,
          borderRadius: 'var(--app-radius-sm)',
          flexShrink: 0,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `color-mix(in srgb, ${token.colorPrimary} 12%, transparent)`,
          color: token.colorPrimary,
          fontSize: 12,
          fontWeight: 600,
        }}
      >
        {initials}
      </span>
      <div style={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <span
          style={{
            fontSize: 13,
            fontWeight: 500,
            color: token.colorText,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {user.fullName}
        </span>
        <span
          style={{
            fontSize: 12,
            color: token.colorTextTertiary,
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          @{user.username}
          {user.email ? ` · ${user.email}` : ''}
        </span>
      </div>
    </div>
  );
}

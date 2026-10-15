// User pill in the topbar's right corner: name + role + circular avatar
// (profile picture when the user has one, initials otherwise). Click opens
// the account dropdown (account, sessions, security, sign out). In compact
// mode (mobile) only the avatar shows.
//
// The avatar doubles as the app-wide entry point for changing your picture:
// the topbar is on every page for every role, so "Change photo" here reaches
// anyone with an employee record without them having to find /me first.

import { useState } from 'react';
import { Avatar, Dropdown, theme as antdTheme, type MenuProps } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import {
  Camera,
  IdCard,
  Keyboard,
  KeyRound,
  LifeBuoy,
  LogOut,
  Settings,
  Users,
} from 'lucide-react';
import { useAuth } from '@/auth';
import {
  invalidateMyProfilePicture,
  ProfilePictureUploader,
  useMyEmployee,
} from '@/people';

const { useToken } = antdTheme;

function userMenuItems(canHavePicture: boolean): MenuProps['items'] {
  return [
    { key: 'me', label: 'My profile', icon: <IdCard size={14} strokeWidth={1.75} /> },
    ...(canHavePicture
      ? [
          {
            key: 'photo',
            label: 'Change photo',
            icon: <Camera size={14} strokeWidth={1.75} />,
          },
        ]
      : []),
    { key: 'account', label: 'My account', icon: <Users size={14} strokeWidth={1.75} /> },
    {
      key: 'sessions',
      label: 'Active sessions',
      icon: <Settings size={14} strokeWidth={1.75} />,
    },
    { type: 'divider' },
    {
      key: 'security',
      label: 'Security',
      icon: <KeyRound size={14} strokeWidth={1.75} />,
    },
    {
      key: 'shortcuts',
      label: 'Keyboard shortcuts',
      icon: <Keyboard size={14} strokeWidth={1.75} />,
      extra: '?',
    },
    { key: 'help', label: 'Help & docs', icon: <LifeBuoy size={14} strokeWidth={1.75} /> },
    { type: 'divider' },
    {
      key: 'signout',
      label: 'Sign out',
      icon: <LogOut size={14} strokeWidth={1.75} />,
      danger: true,
    },
  ];
}

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Admin',
  HR: 'HR',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
};

function initialsFrom(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function primaryRoleLabel(roles: readonly string[]): string {
  // Pick the most senior role for display. Order matches typical hierarchy.
  const order = ['ADMIN', 'HR', 'MANAGER', 'EMPLOYEE'];
  const stripped = roles.map((r) => r.replace(/^ROLE_/, '').toUpperCase());
  for (const code of order) {
    if (stripped.includes(code)) return ROLE_LABELS[code] ?? code;
  }
  return stripped[0] ? (ROLE_LABELS[stripped[0]] ?? stripped[0]) : 'Member';
}

export function UserMenu({ compact }: { compact?: boolean }) {
  const { token } = useToken();
  const auth = useAuth();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { profilePictureUrl, canHavePicture } = useMyEmployee();
  const [photoOpen, setPhotoOpen] = useState(false);

  const fullName = auth.user?.fullName ?? 'Loading…';
  const role = auth.user ? primaryRoleLabel(auth.user.roles) : '';
  const initials = auth.user ? initialsFrom(fullName) : '·';

  const onClick: MenuProps['onClick'] = ({ key }) => {
    switch (key) {
      case 'signout':
        void (async () => {
          await auth.logout();
          void navigate({ to: '/login', replace: true });
        })();
        break;
      case 'me':
        void navigate({ to: '/me' });
        break;
      case 'photo':
        setPhotoOpen(true);
        break;
      case 'account':
        void navigate({ to: '/account' });
        break;
      case 'sessions':
        void navigate({ to: '/account/sessions' });
        break;
      case 'security':
        void navigate({ to: '/account/security' });
        break;
      // 'shortcuts' / 'help' wire up in later phases.
    }
  };

  return (
    <>
      <Dropdown
        menu={{ items: userMenuItems(canHavePicture), onClick }}
        trigger={['click']}
        placement="bottomRight"
      >
        <button
          type="button"
          aria-label="User menu"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            background: 'transparent',
            border: 'none',
            cursor: 'pointer',
            color: 'var(--app-topbar-text)',
            padding: compact ? 0 : '4px 4px 4px 8px',
            borderRadius: 'var(--app-radius-full)',
          }}
        >
          {!compact && (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'flex-end',
                minWidth: 0,
              }}
            >
              <span
                style={{
                  fontSize: 13,
                  fontWeight: 500,
                  lineHeight: 1.2,
                  maxWidth: 180,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {fullName}
              </span>
              <span
                style={{
                  fontSize: 11,
                  color: 'var(--app-topbar-text-muted)',
                  lineHeight: 1.2,
                }}
              >
                {role}
              </span>
            </div>
          )}
          <Avatar
            size={32}
            src={profilePictureUrl ?? undefined}
            style={{ background: token.colorPrimary, color: token.colorBgContainer }}
          >
            {initials}
          </Avatar>
        </button>
      </Dropdown>
      {canHavePicture ? (
        <ProfilePictureUploader
          open={photoOpen}
          onClose={() => setPhotoOpen(false)}
          currentUrl={profilePictureUrl}
          fallbackInitials={initials}
          onUploaded={() => invalidateMyProfilePicture(qc)}
        />
      ) : null}
    </>
  );
}

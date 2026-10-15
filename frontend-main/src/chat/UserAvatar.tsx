// Avatar for a chat participant, backed by the user-resolver cache.
//
// The resolver already carries `profilePictureUrl` for every id it knows —
// employee-service signs the avatar GET URL, the BFF joins it onto the auth
// identity in `/bff/users/by-ids` and `/bff/chat/inbox`, and `seedUsers`
// caches it. Chat surfaces were reading `fullName` off that cache but never
// the picture, so everyone rendered as initials.
//
// Resolution is the caller's job: this only reads the cache. Surfaces that
// show ids the inbox composite didn't seed should call `useResolveUsers`
// (MessageTimeline already does for conversation members).

import { Avatar, theme as antdTheme } from 'antd';
import { initialsOf } from './format';
import { useUserDisplay } from './user-resolver';

const { useToken } = antdTheme;

export type UserAvatarProps = {
  userId: number | null | undefined;
  /** Display name to derive initials from when there's no picture. Falls
   *  back to the resolver's own `fullName`. Pass the name the surface is
   *  already showing so the initials can't disagree with the label. */
  name?: string | null;
  size?: number;
};

export function UserAvatar({ userId, name, size = 32 }: UserAvatarProps) {
  const { token } = useToken();
  const display = useUserDisplay(userId ?? undefined);
  const label = name ?? display?.fullName ?? '';

  return (
    <Avatar
      size={size}
      src={display?.profilePictureUrl ?? undefined}
      style={{
        flexShrink: 0,
        background: token.colorFillSecondary,
        color: token.colorTextSecondary,
        fontSize: Math.max(10, Math.round(size * 0.4)),
        fontWeight: 600,
      }}
    >
      {initialsOf(label) || '?'}
    </Avatar>
  );
}

import { theme as antdTheme } from 'antd';
import { usePresenceFor } from '../presence-store';

const { useToken } = antdTheme;

export type PresenceDotProps = {
  userId: number;
  size?: number;
};

export function PresenceDot({ userId, size = 8 }: PresenceDotProps) {
  const { token } = useToken();
  const presence = usePresenceFor(userId);
  const online = presence?.status === 'online';
  return (
    <span
      aria-label={online ? 'Online' : 'Offline'}
      style={{
        display: 'inline-block',
        width: size,
        height: size,
        borderRadius: '50%',
        background: online ? token.colorSuccess : token.colorTextQuaternary,
        boxShadow: online
          ? `0 0 0 2px ${token.colorBgContainer}`
          : `0 0 0 2px ${token.colorBgContainer}`,
      }}
    />
  );
}

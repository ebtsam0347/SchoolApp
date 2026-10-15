import { Typography } from 'antd';
import { relTime } from '@/notifications';
import { usePresenceFor } from '../presence-store';

const { Text } = Typography;

export type LastSeenLabelProps = {
  userId: number;
};

export function LastSeenLabel({ userId }: LastSeenLabelProps) {
  const presence = usePresenceFor(userId);
  if (!presence) return null;
  if (presence.status === 'online') {
    return (
      <Text type="success" style={{ fontSize: 12 }}>
        Online
      </Text>
    );
  }
  if (presence.lastSeenAt) {
    return (
      <Text type="secondary" style={{ fontSize: 12 }}>
        Last seen {relTime(presence.lastSeenAt)}
      </Text>
    );
  }
  return (
    <Text type="secondary" style={{ fontSize: 12 }}>
      Offline
    </Text>
  );
}

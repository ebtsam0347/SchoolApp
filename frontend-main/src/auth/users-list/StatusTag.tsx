// Active / Locked / Disabled tag for the user listing's Status column.
// The Locked variant tooltips the locked_until timestamp when known.

import { Tag, Tooltip } from 'antd';
import { Lock, ShieldAlert, ShieldCheck } from 'lucide-react';
import type { UserDto } from '@/auth';

export function StatusTag({ user }: { user: UserDto }) {
  if (user.status === 'ACTIVE') {
    return (
      <Tag
        color="success"
        icon={<ShieldCheck size={11} strokeWidth={2} />}
        style={{ margin: 0 }}
      >
        Active
      </Tag>
    );
  }
  if (user.status === 'LOCKED') {
    return (
      <Tooltip
        title={
          user.lockedUntil
            ? `Until ${new Date(user.lockedUntil).toLocaleString()}`
            : undefined
        }
      >
        <Tag color="warning" icon={<Lock size={11} strokeWidth={2} />} style={{ margin: 0 }}>
          Locked
        </Tag>
      </Tooltip>
    );
  }
  return (
    <Tag
      color="default"
      icon={<ShieldAlert size={11} strokeWidth={2} />}
      style={{ margin: 0 }}
    >
      Disabled
    </Tag>
  );
}

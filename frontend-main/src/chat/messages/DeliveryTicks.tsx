// Delivery / read indicators on outgoing bubbles. We approximate without
// per-recipient bookkeeping client-side:
//   `sent`      → message is in the timeline (we trust the POST response)
//   `delivered` → conversation has any `chat.message.delivered` envelope
//   `read`      → all peers have a read cursor at-or-past this message id
//
// For the first ship we simplify to a binary: sent vs delivered/read,
// driven by whether `lastReadMessageId` from the server-side state has
// caught up. The conversations API doesn't expose per-peer read cursors
// yet — we'll wire those when the BFF surface lands.

import { theme as antdTheme } from 'antd';
import { Check, CheckCheck } from 'lucide-react';

const { useToken } = antdTheme;

export type DeliveryTicksProps = {
  /** Whether the message has been delivered to at least one recipient. */
  delivered?: boolean;
  /** Whether all recipients have read it. */
  read?: boolean;
};

export function DeliveryTicks({ delivered, read }: DeliveryTicksProps) {
  const { token } = useToken();
  if (read) {
    return <CheckCheck size={14} style={{ color: token.colorPrimary }} />;
  }
  if (delivered) {
    return <CheckCheck size={14} style={{ color: token.colorTextSecondary }} />;
  }
  return <Check size={14} style={{ color: token.colorTextSecondary }} />;
}

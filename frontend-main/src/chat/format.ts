// Display labels + tone mappings. Keep them here so subcomponents stay
// AntD-only and themes can drive colour through tokens.

import type { StatusPillTone } from '@/components';
import type { ConversationType, DeleteReason, MemberRole } from './types';

export const CONVERSATION_TYPE_LABEL: Record<ConversationType, string> = {
  DM: 'Direct',
  GROUP: 'Group',
  BROADCAST: 'Announcement',
};

export const MEMBER_ROLE_LABEL: Record<MemberRole, string> = {
  OWNER: 'Owner',
  MEMBER: 'Member',
};

export const MEMBER_ROLE_TONE: Record<MemberRole, StatusPillTone> = {
  OWNER: 'primary',
  MEMBER: 'neutral',
};

export const DELETE_REASON_LABEL: Record<DeleteReason, string> = {
  SELF: 'Removed by sender',
  ADMIN: 'Removed by HR',
};

/** Human-friendly first-line for the conversation list when no last message exists. */
export const NO_MESSAGES_PLACEHOLDER = 'No messages yet';

/** Strip mention tokens for previews (`@sarah` → `sarah`). */
export function bodyPreview(body: string, max = 80): string {
  const cleaned = body.replace(/@(\S+)/g, '$1').replace(/\s+/g, ' ').trim();
  return cleaned.length > max ? `${cleaned.slice(0, max - 1)}…` : cleaned;
}

/** Conversation list shows the title for groups, the peer's display name for DMs. */
export function conversationDisplayTitle(
  type: ConversationType,
  title: string | null,
  peerName: string | null,
  fallback = 'Conversation',
): string {
  if (type === 'BROADCAST') return title || 'Announcements';
  if (type === 'GROUP') return title || 'Group';
  return peerName || title || fallback;
}

/** Up to two uppercase initials for avatar fallbacks. */
export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((s) => s[0]?.toUpperCase() ?? '')
    .join('');
}

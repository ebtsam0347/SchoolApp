// Notification-service contract types. Mirrors
// `docs/services/notification.openapi.json` — keep in sync if the spec drifts.
//
// `type` is open-ended (the backend reads from a `notification_templates`
// row), so we keep it as `string`. The set of types is documented in
// `docs/services/notification.md` §3 — `LEAVE_*`, `OT_*`, `PAYSLIP_READY`,
// `EMPLOYEE_*`, `SHIFT_OVERRIDDEN`, etc.

export type NotificationCategory = 'APPROVAL' | 'ANNOUNCEMENT' | 'SYSTEM' | 'PAYROLL';
export type NotificationPriority = 'LOW' | 'NORMAL' | 'HIGH';

export type NotificationDto = {
  id: number;
  userId: number;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  /** Opaque server-rendered JSON string. Parse only if a screen needs it. */
  payload: string | null;
  category: NotificationCategory | null;
  priority: NotificationPriority;
  readAt: string | null;
  createdAt: string;
};

export type PageResponseNotificationDto = {
  content: NotificationDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string;
};

export type UnreadCountResponse = {
  count: number;
};

export type PreferenceDto = {
  type: string;
  enabled: boolean;
};

export type UpdatePreferencesRequest = {
  preferences: Record<string, boolean>;
};

export type AnnounceRequest = {
  userIds: number[];
  title: string;
  body?: string;
  link?: string;
  priority?: NotificationPriority;
};

export type AnnounceResponse = {
  delivered: number;
};

export type TemplateDto = {
  id: number;
  type: string;
  /** Human-readable label for preference toggles and admin lists. */
  label: string | null;
  /** One-line explanation of when this notification fires. */
  description: string | null;
  titleTpl: string;
  bodyTpl: string | null;
  linkTpl: string | null;
  category: NotificationCategory | null;
  active: boolean;
};

export type CreateTemplateRequest = {
  type: string;
  /** Human-readable label for preference toggles and admin lists. */
  label?: string;
  /** One-line explanation of when this notification fires. */
  description?: string;
  titleTpl: string;
  bodyTpl?: string;
  linkTpl?: string;
  category?: NotificationCategory;
};

export type UpdateTemplateRequest = {
  label?: string;
  description?: string;
  titleTpl?: string;
  bodyTpl?: string;
  linkTpl?: string;
  category?: NotificationCategory;
  active?: boolean;
};

export type NotificationTypeDto = {
  type: string;
  label: string;
  description: string | null;
  category: NotificationCategory | null;
};

export type BulkMarkReadRequest = {
  ids: number[];
};

export type MarkReadResponse = {
  marked: number;
};

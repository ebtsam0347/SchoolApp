// Typed wrappers for notification-service. Mirrors
// `docs/services/notification.openapi.json`. All requests go through the
// gateway via `src/lib/http.ts` (bearer + idempotency + 401-refresh-retry).
//
// The SSE stream is intentionally not wired here — it lives on the BFF
// (`/bff/notifications/stream`) and is consumed by `useNotificationsStream`
// in `./stream.ts` once the BFF route lands.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type {
  AnnounceRequest,
  AnnounceResponse,
  CreateTemplateRequest,
  MarkReadResponse,
  NotificationCategory,
  NotificationDto,
  NotificationTypeDto,
  PageResponseNotificationDto,
  PreferenceDto,
  TemplateDto,
  UnreadCountResponse,
  UpdatePreferencesRequest,
  UpdateTemplateRequest,
} from './types';

export type ListNotificationsParams = {
  page?: number;
  size?: number;
  sort?: string;
  unread?: boolean;
  category?: NotificationCategory;
};

export const notificationsApi = {
  list: (params: ListNotificationsParams = {}, signal?: AbortSignal) =>
    http.get<PageResponseNotificationDto>(
      `/notifications${qs({
        page: params.page,
        size: params.size,
        sort: params.sort,
        unread: params.unread,
        category: params.category,
      })}`,
      { signal },
    ),
  unreadCount: (signal?: AbortSignal) =>
    http.get<UnreadCountResponse>('/notifications/unread-count', { signal }),
  markRead: (id: number) => http.post<NotificationDto>(`/notifications/${id}/read`),
  markBulkRead: (ids: number[]) =>
    http.post<MarkReadResponse>('/notifications/read', { ids }),
  markAllRead: () => http.post<UnreadCountResponse>('/notifications/read-all'),
  listPreferences: (signal?: AbortSignal) =>
    http.get<PreferenceDto[]>('/notifications/preferences', { signal }),
  updatePreferences: (body: UpdatePreferencesRequest) =>
    http.patch<PreferenceDto[]>('/notifications/preferences', body),
  /** Type catalog — drives the preferences toggles + admin lookups. */
  listTypes: (signal?: AbortSignal) =>
    http.get<NotificationTypeDto[]>('/notifications/types', { signal }),
};

export const adminNotificationsApi = {
  announce: (body: AnnounceRequest) =>
    http.post<AnnounceResponse>('/admin/notifications/announce', body),
  listTemplates: (signal?: AbortSignal) =>
    http.get<TemplateDto[]>('/admin/notifications/templates', { signal }),
  createTemplate: (body: CreateTemplateRequest) =>
    http.post<TemplateDto>('/admin/notifications/templates', body),
  updateTemplate: (id: number, body: UpdateTemplateRequest) =>
    http.patch<TemplateDto>(`/admin/notifications/templates/${id}`, body),
};

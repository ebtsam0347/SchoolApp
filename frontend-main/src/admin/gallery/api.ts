// Client for the Worker's website-image API (worker/media.ts). Goes through the
// shared `http` wrapper so the admin's access token, silent refresh and
// ApiError handling work exactly as for the backend. The paths are absolute
// (same origin) because VITE_API_BASE points at the backend gateway.

import { http } from '@/lib/http';
import type { PhotoSlotDef, SitePage } from '@/site/photo-slots';

export type MediaImage = {
  id: string;
  title: string;
  caption: string | null;
  description: string | null;
  alt: string | null;
  category: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  originalName: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
  /** Slot keys this image is currently published to. */
  slots: string[];
};

export type SlotRow = PhotoSlotDef & {
  image: MediaImage | null;
  updatedAt: string | null;
  updatedBy: string | null;
};

export type SlotsResponse = {
  pages: { key: SitePage; label: string; path: string }[];
  slots: SlotRow[];
};

export type ImageFilters = { q?: string; category?: string; page?: string; assigned?: 'yes' | 'no' };

export type ImageFields = {
  title?: string;
  alt?: string;
  caption?: string;
  description?: string;
  category?: string;
  slotKey?: string;
};

export const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const MAX_IMAGE_MB = 5;

/** Friendly client-side check; the Worker re-validates the real file content. */
export function validateImageFile(file: File): string | null {
  if (!ACCEPTED_TYPES.includes(file.type)) return 'Unsupported file. Choose a JPG, PNG or WebP image.';
  if (file.size === 0) return 'The selected file is empty.';
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) return `Image is too large. The limit is ${MAX_IMAGE_MB} MB.`;
  return null;
}

const root = () => `${window.location.origin}/media/admin`;
const UPLOAD_TIMEOUT_MS = 120_000;

function form(file: File | null, fields: ImageFields = {}): FormData {
  const f = new FormData();
  if (file) f.set('file', file);
  for (const [k, v] of Object.entries(fields)) if (v !== undefined && v !== '') f.set(k, v);
  return f;
}

export const mediaApi = {
  list(filters: ImageFilters, signal?: AbortSignal) {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(filters)) if (v) qs.set(k, v);
    const suffix = qs.size ? `?${qs}` : '';
    return http.get<{ items: MediaImage[]; total: number }>(`${root()}/images${suffix}`, { signal });
  },
  upload(file: File, fields: ImageFields) {
    return http.post<MediaImage>(`${root()}/images`, form(file, fields), { timeoutMs: UPLOAD_TIMEOUT_MS });
  },
  edit(id: string, patch: Partial<Pick<MediaImage, 'title' | 'caption' | 'description' | 'alt' | 'category' | 'sortOrder'>>) {
    return http.patch<MediaImage>(`${root()}/images/${id}`, patch);
  },
  replace(id: string, file: File) {
    return http.put<MediaImage>(`${root()}/images/${id}/file`, form(file), { timeoutMs: UPLOAD_TIMEOUT_MS });
  },
  remove(id: string, force: boolean) {
    return http.del<void>(`${root()}/images/${id}${force ? '?force=true' : ''}`);
  },
  reorder(ids: string[]) {
    return http.post<void>(`${root()}/images/reorder`, { ids });
  },
  slots(signal?: AbortSignal) {
    return http.get<SlotsResponse>(`${root()}/slots`, { signal });
  },
  assign(slotKey: string, imageId: string | null) {
    return http.put<SlotsResponse>(`${root()}/slots/${slotKey}`, { imageId });
  },
};

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

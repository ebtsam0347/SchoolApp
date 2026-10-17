// Client for the Worker's website-image API (worker/media.ts). Requests go out as
// the photo-admin when a /photos session exists (photo-session.ts), otherwise
// through the shared `http` wrapper so the portal admin's access token and silent
// refresh work as for the backend. Paths are absolute (same origin) because
// VITE_API_BASE points at the backend gateway.

import { parseProblemDetail } from '@/lib/api-error';
import { request } from '@/lib/http';
import type { PhotoSlotDef, SitePage } from '@/site/photo-slots';
import { clearPhotoSession, readPhotoSession } from './photo-session';

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

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
type Opts = { signal?: AbortSignal; timeoutMs?: number };

/** Sends a request as the photo-admin (/photos sign-in) when a session exists, else as the portal admin. */
async function send<T>(method: Method, url: string, body?: unknown, opts: Opts = {}): Promise<T> {
  const session = readPhotoSession();
  if (!session) return request<T>(url, { method, body, ...opts });

  const timeout = AbortSignal.timeout(opts.timeoutMs ?? 30_000);
  const res = await fetch(url, {
    method,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${session.token}`,
      ...(body !== undefined && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body === undefined || body instanceof FormData ? (body as FormData | undefined) : JSON.stringify(body),
    signal: opts.signal ? AbortSignal.any([opts.signal, timeout]) : timeout,
  });
  if (res.status === 401) clearPhotoSession(); // expired or password changed: back to the sign-in form
  if (!res.ok) throw await parseProblemDetail(res);
  if (res.status === 204 || !(res.headers.get('content-type') ?? '').includes('json')) return undefined as T;
  return (await res.json()) as T;
}

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
    return send<{ items: MediaImage[]; total: number }>('GET', `${root()}/images${suffix}`, undefined, { signal });
  },
  upload(file: File, fields: ImageFields) {
    return send<MediaImage>('POST', `${root()}/images`, form(file, fields), { timeoutMs: UPLOAD_TIMEOUT_MS });
  },
  edit(id: string, patch: Partial<Pick<MediaImage, 'title' | 'caption' | 'description' | 'alt' | 'category' | 'sortOrder'>>) {
    return send<MediaImage>('PATCH', `${root()}/images/${id}`, patch);
  },
  replace(id: string, file: File) {
    return send<MediaImage>('PUT', `${root()}/images/${id}/file`, form(file), { timeoutMs: UPLOAD_TIMEOUT_MS });
  },
  remove(id: string, force: boolean) {
    return send<void>('DELETE', `${root()}/images/${id}${force ? '?force=true' : ''}`);
  },
  reorder(ids: string[]) {
    return send<void>('POST', `${root()}/images/reorder`, { ids });
  },
  slots(signal?: AbortSignal) {
    return send<SlotsResponse>('GET', `${root()}/slots`, undefined, { signal });
  },
  assign(slotKey: string, imageId: string | null) {
    return send<SlotsResponse>('PUT', `${root()}/slots/${slotKey}`, { imageId });
  },
};

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

// Website image management API (served by the Worker under /media).
//
// Public (no login):
//   GET  /media/slots                     published image for each photo slot
//   GET  /media/file/<uuid>.<ext>         image bytes from KV (immutable URLs)
//
//   POST /media/login                     { password } -> photo-admin session token (see gallery-token.ts)
//
// Admin (Bearer: backend ADMIN access token, or a /media/login session token — see auth.ts):
//   GET    /media/admin/images            library, ?q= &category= &page= &assigned=
//   POST   /media/admin/images            upload (multipart: file, title, ...)
//   PATCH  /media/admin/images/:id        edit title / caption / description / alt / category / order
//   PUT    /media/admin/images/:id/file   replace the file; every slot using it updates
//   DELETE /media/admin/images/:id        delete (409 if in use unless ?force=true)
//   POST   /media/admin/images/reorder    { ids: [...] }
//   GET    /media/admin/slots             every slot with its current image
//   PUT    /media/admin/slots/:key        { imageId: string | null } publish / unpublish
//
// Metadata lives in D1, files in Workers KV (free tier, 25 MiB per value; the
// 5 MB upload limit stays well below). Replacing a file writes a NEW key,
// so public URLs stay immutable and cacheable; the slot JSON is short-lived,
// which is how a replacement shows up without a redeploy.

import { HttpError, requireAdmin, type Admin, type AuthEnv } from './auth';
import { sniffImage } from './image-type';
import { GALLERY_TOKEN_TTL_MS, issueGalleryToken, passwordMatches } from './gallery-token';
import { ensureSchema } from './schema';
import { IMAGE_CATEGORIES, PHOTO_SLOT_DEFS, PHOTO_SLOT_KEYS, SITE_PAGES } from '../src/site/photo-slots';

export interface MediaEnv extends AuthEnv {
  DB?: D1Database;
  /** KV namespace holding the image bytes (key `images/<uuid>.<ext>`, content type in metadata). */
  MEDIA?: KVNamespace<string>;
}

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MAX_BODY_BYTES = MAX_IMAGE_BYTES + 256 * 1024; // file + form fields
const KEY_PREFIX = 'images/';
const FILE_NAME = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$/;
const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

type ImageRow = {
  id: string;
  title: string;
  caption: string | null;
  description: string | null;
  alt: string | null;
  category: string;
  object_key: string;
  content_type: string;
  size_bytes: number;
  original_name: string | null;
  sort_order: number;
  created_at: string;
  updated_at: string;
  created_by: string;
  updated_by: string;
};

// --- responses ---------------------------------------------------------------

function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers },
  });
}

/** RFC 7807 shape, matching what the SPA's parseProblemDetail expects. */
function problem(status: number, code: string, detail: string, extra: Record<string, unknown> = {}): Response {
  return json(
    {
      type: 'about:blank',
      title: detail,
      status,
      code,
      detail,
      correlationId: crypto.randomUUID(),
      timestamp: new Date().toISOString(),
      ...extra,
    },
    status,
    { 'Content-Type': 'application/problem+json' },
  );
}

function imageDto(r: ImageRow, slots: string[] = []) {
  return {
    id: r.id,
    title: r.title,
    caption: r.caption,
    description: r.description,
    alt: r.alt,
    category: r.category,
    url: fileUrl(r),
    contentType: r.content_type,
    sizeBytes: r.size_bytes,
    originalName: r.original_name,
    sortOrder: r.sort_order,
    createdAt: r.created_at,
    updatedAt: r.updated_at,
    createdBy: r.created_by,
    updatedBy: r.updated_by,
    slots,
  };
}

function fileUrl(r: Pick<ImageRow, 'object_key'>): string {
  return `/media/file/${r.object_key.slice(KEY_PREFIX.length)}`;
}

// --- validation --------------------------------------------------------------

function text(v: unknown, max: number, field: string, required = false): string | null {
  if (v === undefined || v === null || v === '') {
    if (required) throw new HttpError(422, 'validation', `${field} is required.`);
    return null;
  }
  if (typeof v !== 'string') throw new HttpError(422, 'validation', `${field} must be text.`);
  const t = v.trim();
  if (!t) {
    if (required) throw new HttpError(422, 'validation', `${field} is required.`);
    return null;
  }
  if (t.length > max) throw new HttpError(422, 'validation', `${field} must be at most ${max} characters.`);
  return t;
}

function category(v: unknown): string {
  if (v === undefined || v === null || v === '') return 'General';
  if (typeof v !== 'string' || !(IMAGE_CATEGORIES as readonly string[]).includes(v)) {
    throw new HttpError(422, 'validation', `Category must be one of: ${IMAGE_CATEGORIES.join(', ')}.`);
  }
  return v;
}

async function readUpload(form: FormData): Promise<{ bytes: Uint8Array; mime: string; ext: string; name: string }> {
  const file = form.get('file');
  if (!(file instanceof File)) throw new HttpError(422, 'validation', 'Choose an image file to upload.');
  if (file.size === 0) throw new HttpError(422, 'validation', 'The selected file is empty.');
  if (file.size > MAX_IMAGE_BYTES) {
    throw new HttpError(413, 'file_too_large', `Image is too large. The limit is ${MAX_IMAGE_BYTES / 1024 / 1024} MB.`);
  }
  const bytes = new Uint8Array(await file.arrayBuffer());
  const kind = sniffImage(bytes);
  if (!kind) throw new HttpError(415, 'unsupported_type', 'Unsupported file. Upload a JPG, PNG or WebP image.');
  // The filename is only kept as a display label — never used in a storage key.
  const name = file.name.replace(/[\u0000-\u001f\\/]/g, '').slice(0, 200);
  return { bytes, mime: kind.mime, ext: kind.ext, name };
}

async function readForm(request: Request): Promise<FormData> {
  const declared = Number(request.headers.get('Content-Length') ?? '0');
  if (declared > MAX_BODY_BYTES) {
    throw new HttpError(413, 'file_too_large', `Image is too large. The limit is ${MAX_IMAGE_BYTES / 1024 / 1024} MB.`);
  }
  if (!(request.headers.get('Content-Type') ?? '').toLowerCase().startsWith('multipart/form-data')) {
    throw new HttpError(415, 'unsupported_type', 'Send the image as multipart/form-data.');
  }
  try {
    return await request.formData();
  } catch {
    throw new HttpError(400, 'bad_request', 'Could not read the upload.');
  }
}

async function readJson(request: Request): Promise<Record<string, unknown>> {
  try {
    const body = await request.json();
    if (body && typeof body === 'object' && !Array.isArray(body)) return body as Record<string, unknown>;
  } catch {
    /* fall through */
  }
  throw new HttpError(400, 'bad_request', 'Request body must be a JSON object.');
}

// --- data access -------------------------------------------------------------

async function slotsByImage(db: D1Database): Promise<Map<string, string[]>> {
  const { results } = await db.prepare('SELECT slot_key, image_id FROM slots WHERE image_id IS NOT NULL').all<{ slot_key: string; image_id: string }>();
  const map = new Map<string, string[]>();
  for (const r of results) map.set(r.image_id, [...(map.get(r.image_id) ?? []), r.slot_key]);
  return map;
}

async function getImage(db: D1Database, id: string): Promise<ImageRow> {
  if (!ID.test(id)) throw new HttpError(404, 'not_found', 'Image not found.');
  const row = await db.prepare('SELECT * FROM images WHERE id = ?').bind(id).first<ImageRow>();
  if (!row) throw new HttpError(404, 'not_found', 'Image not found.');
  return row;
}

async function putObject(bucket: KVNamespace<string>, key: string, bytes: Uint8Array, mime: string): Promise<void> {
  await bucket.put(key, bytes, { metadata: { contentType: mime } });
}

async function dropObject(bucket: KVNamespace<string>, key: string): Promise<void> {
  try {
    await bucket.delete(key);
  } catch (e) {
    // The DB is the source of truth; a leftover object is harmless and unreachable.
    console.error('media: failed to delete object', key, e);
  }
}

// --- public handlers ---------------------------------------------------------

async function publicSlots(env: MediaEnv): Promise<Response> {
  const headers = { 'Cache-Control': 'public, max-age=30, stale-while-revalidate=300' };
  if (!env.DB) return json({ slots: {} }, 200, headers);
  try {
    await ensureSchema(env.DB);
    const { results } = await env.DB.prepare(
      'SELECT s.slot_key, i.object_key, i.alt, i.updated_at FROM slots s JOIN images i ON i.id = s.image_id',
    ).all<{ slot_key: string; object_key: string; alt: string | null; updated_at: string }>();
    const slots: Record<string, { url: string; alt: string | null; updatedAt: string }> = {};
    for (const r of results) {
      if (PHOTO_SLOT_KEYS.has(r.slot_key)) slots[r.slot_key] = { url: fileUrl(r), alt: r.alt, updatedAt: r.updated_at };
    }
    return json({ slots }, 200, headers);
  } catch (e) {
    // The public site must never break because of the image store.
    console.error('media: public slots failed', e);
    return json({ slots: {} }, 200, { 'Cache-Control': 'public, max-age=10' });
  }
}

async function publicFile(request: Request, env: MediaEnv, name: string): Promise<Response> {
  if (!env.MEDIA || !FILE_NAME.test(name)) return new Response('Not found', { status: 404 });
  // The key is unique per upload and never rewritten, so the URL is its own ETag.
  const etag = `"${name}"`;
  const headers = new Headers({
    'Cache-Control': 'public, max-age=31536000, immutable',
    'X-Content-Type-Options': 'nosniff',
    ETag: etag,
  });
  if (request.headers.get('If-None-Match') === etag) return new Response(null, { status: 304, headers });

  const { value, metadata } = await env.MEDIA.getWithMetadata<{ contentType?: string }>(KEY_PREFIX + name, 'arrayBuffer');
  // KV is eventually consistent: a brand-new key can take up to a minute to be
  // readable from every location, so a miss must not be cached for long.
  if (!value) return new Response('Not found', { status: 404, headers: { 'Cache-Control': 'public, max-age=5' } });
  headers.set('Content-Type', metadata?.contentType ?? 'application/octet-stream');
  headers.set('Content-Length', String(value.byteLength));
  return new Response(request.method === 'HEAD' ? null : value, { headers });
}

// --- photo-admin login ---------------------------------------------------------

const MAX_FAILURES = 5;
const FAILURE_WINDOW_MS = 10 * 60_000;

async function login(request: Request, env: MediaEnv): Promise<Response> {
  if (!env.GALLERY_PASSWORD) {
    throw new HttpError(503, 'login_not_configured', 'Photo sign-in is not set up yet. Ask the site owner to set the GALLERY_PASSWORD secret.');
  }
  if (!env.DB) throw new HttpError(503, 'storage_not_configured', 'Image storage is not set up yet (KV namespace and D1 database bindings).');
  const db = env.DB;
  await ensureSchema(db);

  const ip = request.headers.get('CF-Connecting-IP') ?? 'unknown';
  const now = Date.now();
  const recent = await db
    .prepare('SELECT COUNT(*) AS n FROM login_failures WHERE ip = ? AND at > ?')
    .bind(ip, now - FAILURE_WINDOW_MS)
    .first<{ n: number }>();
  if ((recent?.n ?? 0) >= MAX_FAILURES) {
    throw new HttpError(429, 'too_many_attempts', 'Too many wrong passwords. Please wait 10 minutes and try again.');
  }

  const body = await readJson(request);
  const password = typeof body.password === 'string' ? body.password : '';
  if (!password || !(await passwordMatches(password, env.GALLERY_PASSWORD))) {
    await db.batch([
      db.prepare('DELETE FROM login_failures WHERE at < ?').bind(now - FAILURE_WINDOW_MS),
      db.prepare('INSERT INTO login_failures (ip, at) VALUES (?, ?)').bind(ip, now),
    ]);
    throw new HttpError(401, 'bad_credentials', 'Wrong password.');
  }
  await db.prepare('DELETE FROM login_failures WHERE ip = ?').bind(ip).run();
  const issued = await issueGalleryToken(env.GALLERY_PASSWORD, now);
  return json({ ...issued, expiresInSeconds: GALLERY_TOKEN_TTL_MS / 1000 });
}

// --- admin handlers ----------------------------------------------------------

async function listImages(db: D1Database, url: URL): Promise<Response> {
  const q = (url.searchParams.get('q') ?? '').trim().toLowerCase();
  const cat = url.searchParams.get('category');
  const page = url.searchParams.get('page');
  const assigned = url.searchParams.get('assigned'); // 'yes' | 'no'

  const [{ results }, bySlot] = await Promise.all([
    db.prepare('SELECT * FROM images ORDER BY sort_order ASC, created_at DESC').all<ImageRow>(),
    slotsByImage(db),
  ]);
  const pageSlots = page ? new Set(PHOTO_SLOT_DEFS.filter((s) => s.page === page).map((s) => s.key)) : null;

  const items = results
    .map((r) => imageDto(r, bySlot.get(r.id) ?? []))
    .filter((i) => !cat || i.category === cat)
    .filter((i) => !pageSlots || i.slots.some((s) => pageSlots.has(s)))
    .filter((i) => (assigned === 'yes' ? i.slots.length > 0 : assigned === 'no' ? i.slots.length === 0 : true))
    .filter((i) => !q || [i.title, i.caption, i.description, i.alt, i.originalName].some((f) => f?.toLowerCase().includes(q)));
  return json({ items, total: items.length });
}

async function uploadImage(request: Request, env: MediaEnv, db: D1Database, bucket: KVNamespace<string>, admin: Admin): Promise<Response> {
  const form = await readForm(request);
  const up = await readUpload(form);
  const fallbackTitle = up.name.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' ').trim();
  const title = text(form.get('title') ?? fallbackTitle, 120, 'Title', true)!;
  const caption = text(form.get('caption'), 300, 'Caption');
  const description = text(form.get('description'), 1000, 'Description');
  const alt = text(form.get('alt'), 200, 'Alt text');
  const cat = category(form.get('category'));
  const slotKey = text(form.get('slotKey'), 40, 'Slot');
  if (slotKey && !PHOTO_SLOT_KEYS.has(slotKey)) throw new HttpError(422, 'validation', 'Unknown website image slot.');

  const id = crypto.randomUUID();
  const objectKey = `${KEY_PREFIX}${crypto.randomUUID()}.${up.ext}`;
  const now = new Date().toISOString();

  await putObject(bucket, objectKey, up.bytes, up.mime);
  try {
    const stmts = [
      db
        .prepare(
          `INSERT INTO images (id, title, caption, description, alt, category, object_key, content_type, size_bytes, original_name,
                               sort_order, created_at, updated_at, created_by, updated_by)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                   COALESCE((SELECT MIN(sort_order) FROM images), 0) - 10, ?, ?, ?, ?)`,
        )
        .bind(id, title, caption, description, alt, cat, objectKey, up.mime, up.bytes.length, up.name || null, now, now, admin.username, admin.username),
    ];
    if (slotKey) stmts.push(upsertSlot(db, slotKey, id, now, admin.username));
    await db.batch(stmts);
  } catch (e) {
    await dropObject(bucket, objectKey);
    throw e;
  }
  const row = await getImage(db, id);
  return json(imageDto(row, slotKey ? [slotKey] : []), 201);
}

async function editImage(request: Request, db: D1Database, id: string, admin: Admin): Promise<Response> {
  const row = await getImage(db, id);
  const body = await readJson(request);
  const has = (k: string) => Object.prototype.hasOwnProperty.call(body, k);

  const title = has('title') ? text(body.title, 120, 'Title', true)! : row.title;
  const caption = has('caption') ? text(body.caption, 300, 'Caption') : row.caption;
  const description = has('description') ? text(body.description, 1000, 'Description') : row.description;
  const alt = has('alt') ? text(body.alt, 200, 'Alt text') : row.alt;
  const cat = has('category') ? category(body.category) : row.category;
  let sortOrder = row.sort_order;
  if (has('sortOrder')) {
    if (typeof body.sortOrder !== 'number' || !Number.isInteger(body.sortOrder) || Math.abs(body.sortOrder) > 1_000_000) {
      throw new HttpError(422, 'validation', 'Display order must be a whole number.');
    }
    sortOrder = body.sortOrder;
  }

  await db
    .prepare('UPDATE images SET title=?, caption=?, description=?, alt=?, category=?, sort_order=?, updated_at=?, updated_by=? WHERE id=?')
    .bind(title, caption, description, alt, cat, sortOrder, new Date().toISOString(), admin.username, id)
    .run();
  const bySlot = await slotsByImage(db);
  return json(imageDto(await getImage(db, id), bySlot.get(id) ?? []));
}

async function replaceFile(request: Request, db: D1Database, bucket: KVNamespace<string>, id: string, admin: Admin): Promise<Response> {
  const row = await getImage(db, id);
  const up = await readUpload(await readForm(request));
  const newKey = `${KEY_PREFIX}${crypto.randomUUID()}.${up.ext}`;

  await putObject(bucket, newKey, up.bytes, up.mime);
  try {
    await db
      .prepare('UPDATE images SET object_key=?, content_type=?, size_bytes=?, original_name=?, updated_at=?, updated_by=? WHERE id=?')
      .bind(newKey, up.mime, up.bytes.length, up.name || null, new Date().toISOString(), admin.username, id)
      .run();
  } catch (e) {
    await dropObject(bucket, newKey);
    throw e;
  }
  await dropObject(bucket, row.object_key);
  const bySlot = await slotsByImage(db);
  return json(imageDto(await getImage(db, id), bySlot.get(id) ?? []));
}

async function deleteImage(db: D1Database, bucket: KVNamespace<string>, id: string, url: URL): Promise<Response> {
  const row = await getImage(db, id);
  const inUse = (await slotsByImage(db)).get(id) ?? [];
  if (inUse.length > 0 && url.searchParams.get('force') !== 'true') {
    return problem(409, 'in_use', 'This image is published on the website. Remove it from those places first, or confirm deleting it anyway.', {
      slots: inUse,
    });
  }
  await db.batch([db.prepare('UPDATE slots SET image_id = NULL WHERE image_id = ?').bind(id), db.prepare('DELETE FROM images WHERE id = ?').bind(id)]);
  await dropObject(bucket, row.object_key);
  return new Response(null, { status: 204 });
}

async function reorder(request: Request, db: D1Database): Promise<Response> {
  const body = await readJson(request);
  const ids = body.ids;
  if (!Array.isArray(ids) || ids.length === 0 || ids.length > 500 || !ids.every((i) => typeof i === 'string' && ID.test(i))) {
    throw new HttpError(422, 'validation', 'Send the image ids in the new order.');
  }
  await db.batch(ids.map((id, i) => db.prepare('UPDATE images SET sort_order = ? WHERE id = ?').bind(i * 10, id)));
  return new Response(null, { status: 204 });
}

function upsertSlot(db: D1Database, key: string, imageId: string | null, now: string, by: string): D1PreparedStatement {
  return db
    .prepare(
      `INSERT INTO slots (slot_key, image_id, updated_at, updated_by) VALUES (?, ?, ?, ?)
       ON CONFLICT(slot_key) DO UPDATE SET image_id = excluded.image_id, updated_at = excluded.updated_at, updated_by = excluded.updated_by`,
    )
    .bind(key, imageId, now, by);
}

async function listSlots(db: D1Database): Promise<Response> {
  const [{ results: slotRows }, { results: images }] = await Promise.all([
    db.prepare('SELECT slot_key, image_id, updated_at, updated_by FROM slots').all<{ slot_key: string; image_id: string | null; updated_at: string; updated_by: string }>(),
    db.prepare('SELECT * FROM images').all<ImageRow>(),
  ]);
  const imageById = new Map(images.map((i) => [i.id, i]));
  const slotByKey = new Map(slotRows.map((s) => [s.slot_key, s]));
  return json({
    pages: SITE_PAGES,
    slots: PHOTO_SLOT_DEFS.map((def) => {
      const s = slotByKey.get(def.key);
      const img = s?.image_id ? imageById.get(s.image_id) : undefined;
      return { ...def, image: img ? imageDto(img) : null, updatedAt: s?.updated_at ?? null, updatedBy: s?.updated_by ?? null };
    }),
  });
}

async function assignSlot(request: Request, db: D1Database, key: string, admin: Admin): Promise<Response> {
  if (!PHOTO_SLOT_KEYS.has(key)) throw new HttpError(404, 'not_found', 'Unknown website image slot.');
  const body = await readJson(request);
  const imageId = body.imageId;
  if (imageId !== null && (typeof imageId !== 'string' || !ID.test(imageId))) {
    throw new HttpError(422, 'validation', 'Choose an image from the library.');
  }
  if (imageId) await getImage(db, imageId);
  await upsertSlot(db, key, imageId as string | null, new Date().toISOString(), admin.username).run();
  return listSlots(db);
}

// --- entry -------------------------------------------------------------------

export async function handleMedia(request: Request, env: MediaEnv): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, '');
  const method = request.method;

  try {
    // Public
    if (path === '/media/slots' && method === 'GET') return await publicSlots(env);
    if (path.startsWith('/media/file/') && (method === 'GET' || method === 'HEAD')) {
      return await publicFile(request, env, path.slice('/media/file/'.length));
    }

    if (path === '/media/login' && method === 'POST') return await login(request, env);

    if (!path.startsWith('/media/admin/')) return problem(404, 'not_found', 'Not found.');

    // Admin: authenticate first so callers learn nothing about the store.
    const admin = await requireAdmin(request, env);
    if (!env.DB || !env.MEDIA) {
      throw new HttpError(503, 'storage_not_configured', 'Image storage is not set up yet (KV namespace and D1 database bindings).');
    }
    const { DB: db, MEDIA: bucket } = env as Required<MediaEnv>;
    await ensureSchema(db);

    const seg = path.slice('/media/admin/'.length).split('/');
    if (seg[0] === 'images') {
      if (seg.length === 1) {
        if (method === 'GET') return await listImages(db, url);
        if (method === 'POST') return await uploadImage(request, env, db, bucket, admin);
      } else if (seg.length === 2 && seg[1] === 'reorder' && method === 'POST') {
        return await reorder(request, db);
      } else if (seg.length === 2) {
        if (method === 'PATCH') return await editImage(request, db, seg[1], admin);
        if (method === 'DELETE') return await deleteImage(db, bucket, seg[1], url);
      } else if (seg.length === 3 && seg[2] === 'file' && method === 'PUT') {
        return await replaceFile(request, db, bucket, seg[1], admin);
      }
    } else if (seg[0] === 'slots') {
      if (seg.length === 1 && method === 'GET') return await listSlots(db);
      if (seg.length === 2 && method === 'PUT') return await assignSlot(request, db, seg[1], admin);
    }
    return problem(404, 'not_found', 'Not found.');
  } catch (e) {
    if (e instanceof HttpError) return problem(e.status, e.code, e.message);
    console.error('media: unexpected error', e);
    return problem(500, 'internal', 'Something went wrong. Please try again.');
  }
}

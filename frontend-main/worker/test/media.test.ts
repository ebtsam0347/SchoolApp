import { beforeEach, describe, expect, it } from 'vitest';
import { handleMedia, MAX_IMAGE_BYTES } from '../media';
import { inMinutes, JPEG, makeSigner, PNG, req, setup, upload, WEBP } from './helpers';

let ctx: Awaited<ReturnType<typeof setup>>;
beforeEach(async () => {
  ctx = await setup();
});

const call = (r: Request) => handleMedia(r, ctx.env);
const as = (token = ctx.adminToken) => ({ token });

type Img = { id: string; url: string; slots: string[]; title: string; createdBy: string; contentType: string };

async function uploadOk(name = 'photo.png', bytes = PNG, fields: Record<string, string> = { title: 'First photo' }) {
  const res = await call(req('/media/admin/images', { method: 'POST', body: upload(bytes, name, fields), ...as() }));
  expect(res.status).toBe(201);
  return (await res.json()) as Img;
}

type SlotList = { slots: { key: string; page: string; section: string; image: { id: string } | null }[] };
type PublicSlots = { slots: Record<string, { url: string; alt: string | null }> };

describe('public endpoints', () => {
  it('returns no slots when nothing is published, without needing a login', async () => {
    const res = await call(req('/media/slots'));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ slots: {} });
    expect(res.headers.get('Cache-Control')).toContain('max-age=30');
  });

  it('degrades to empty slots when the D1 binding is missing', async () => {
    const res = await handleMedia(req('/media/slots'), { ...ctx.env, DB: undefined });
    expect(await res.json()).toEqual({ slots: {} });
  });

  it('rejects file names that are not generated keys (path traversal)', async () => {
    for (const bad of ['..%2F..%2Fsecret.png', 'x.png', '../a.jpg', 'images/a.png']) {
      const res = await call(req(`/media/file/${bad}`));
      expect(res.status).toBe(404);
    }
  });
});

describe('authentication and authorization', () => {
  const adminRoutes: [string, RequestInit][] = [
    ['/media/admin/images', {}],
    ['/media/admin/images', { method: 'POST', body: upload(PNG, 'a.png') }],
    ['/media/admin/slots', {}],
    ['/media/admin/slots/hero', { method: 'PUT', body: '{}' }],
  ];

  it('refuses anonymous callers on every admin route', async () => {
    for (const [path, init] of adminRoutes) expect((await call(req(path, init))).status).toBe(401);
  });

  it('refuses a signed-in non-admin (403) and writes nothing', async () => {
    const hr = await ctx.signer.sign({ sub: '2', username: 'hr', roles: ['HR'], permissions: ['employee.write'], exp: inMinutes(10) });
    expect((await call(req('/media/admin/images', { token: hr }))).status).toBe(403);
    const up = await call(req('/media/admin/images', { method: 'POST', body: upload(PNG, 'a.png'), token: hr }));
    expect(up.status).toBe(403);
    expect(ctx.env.MEDIA.objects.size).toBe(0);
  });

  it('refuses expired, tampered, unsigned and wrong-key tokens', async () => {
    const expired = await ctx.signer.sign({ sub: '1', username: 'a', roles: ['ADMIN'], exp: inMinutes(-1) });
    expect((await call(req('/media/admin/images', { token: expired }))).status).toBe(401);

    const [h, b, s] = ctx.adminToken.split('.');
    const forged = btoa(JSON.stringify({ sub: '9', username: 'evil', roles: ['ADMIN'], exp: inMinutes(60) })).replace(/=+$/, '');
    expect((await call(req('/media/admin/images', { token: `${h}.${forged}.${s}` }))).status).toBe(401);
    expect((await call(req('/media/admin/images', { token: `${h}.${b}.AAAA` }))).status).toBe(401);

    const none = `${btoa('{"alg":"none"}').replace(/=+$/, '')}.${b}.`;
    expect((await call(req('/media/admin/images', { token: none }))).status).toBe(401);

    const foreign = await (await makeSigner('k1')).sign({ sub: '1', username: 'a', roles: ['ADMIN'], exp: inMinutes(10) });
    expect((await call(req('/media/admin/images', { token: foreign }))).status).toBe(401);
  });

  it('refuses service tokens and accounts that must change their password', async () => {
    const svc = await ctx.signer.sign({ sub: '0', username: 'svc', roles: ['ADMIN'], tokenType: 'service', exp: inMinutes(10) });
    expect((await call(req('/media/admin/images', { token: svc }))).status).toBe(403);
    const pw = await ctx.signer.sign({ sub: '1', username: 'a', roles: ['ADMIN'], pwChangeRequired: true, exp: inMinutes(10) });
    expect((await call(req('/media/admin/images', { token: pw }))).status).toBe(403);
  });

  it('reports 503 (after authenticating) when storage bindings are missing', async () => {
    const res = await handleMedia(req('/media/admin/images', as()), { ...ctx.env, MEDIA: undefined });
    expect(res.status).toBe(503);
    expect((await handleMedia(req('/media/admin/images'), { ...ctx.env, MEDIA: undefined })).status).toBe(401);
  });
});

describe('upload validation', () => {
  it('accepts JPG, PNG and WebP by content and records the admin', async () => {
    for (const [bytes, name] of [[JPEG, 'a.jpeg'], [PNG, 'b.png'], [WEBP, 'c.webp']] as const) {
      const img = await uploadOk(name, bytes, { title: name });
      expect(img.createdBy).toBe('admin.user');
    }
    expect(ctx.env.MEDIA.objects.size).toBe(3);
    for (const key of ctx.env.MEDIA.objects.keys()) expect(key).toMatch(/^images\/[0-9a-f-]{36}\.(jpg|png|webp)$/);
  });

  it('rejects a non-image renamed to .png, and SVG/HTML payloads', async () => {
    const payloads = ['<svg xmlns="http://www.w3.org/2000/svg"><script>1</script></svg>', '<html></html>', 'MZ not an image'];
    for (const payload of payloads) {
      const body = upload(new TextEncoder().encode(payload), 'evil.png');
      const res = await call(req('/media/admin/images', { method: 'POST', body, ...as() }));
      expect(res.status).toBe(415);
    }
    expect(ctx.env.MEDIA.objects.size).toBe(0);
  });

  it('rejects empty, oversized and missing files', async () => {
    const empty = await call(req('/media/admin/images', { method: 'POST', body: upload(new Uint8Array(), 'e.png'), ...as() }));
    expect(empty.status).toBe(422);
    const big = new Uint8Array(MAX_IMAGE_BYTES + 1);
    big.set(PNG);
    const tooBig = await call(req('/media/admin/images', { method: 'POST', body: upload(big, 'big.png'), ...as() }));
    expect(tooBig.status).toBe(413);
    const none = await call(req('/media/admin/images', { method: 'POST', body: new FormData(), ...as() }));
    expect(none.status).toBe(422);
    const notMultipart = await call(req('/media/admin/images', { method: 'POST', json: {}, ...as() }));
    expect(notMultipart.status).toBe(415);
  });

  it('never uses the client file name in storage and defaults the title from it', async () => {
    const body = upload(PNG, 'my-first_photo.png');
    const res = await call(req('/media/admin/images', { method: 'POST', body, ...as() }));
    const img = (await res.json()) as { title: string; url: string };
    expect(img.title).toBe('my first photo');
    expect(img.url).toMatch(/^\/media\/file\/[0-9a-f-]{36}\.png$/);
  });

  it('validates text fields, category and slot', async () => {
    const post = (fields: Record<string, string>) =>
      call(req('/media/admin/images', { method: 'POST', body: upload(PNG, 'a.png', fields), ...as() }));
    expect((await post({ title: 'x'.repeat(121) })).status).toBe(422);
    expect((await post({ title: 't', category: 'Nope' })).status).toBe(422);
    expect((await post({ title: 't', slotKey: 'nope' })).status).toBe(422);
    expect(ctx.env.MEDIA.objects.size).toBe(0);
  });
});

describe('publishing to website slots', () => {
  it('publishes an uploaded image so the public slots endpoint serves it', async () => {
    const img = await uploadOk('hero.jpg', JPEG, { title: 'Hero', alt: 'Children at a table', slotKey: 'hero' });
    expect(img.slots).toEqual(['hero']);

    const pub = (await (await call(req('/media/slots'))).json()) as PublicSlots;
    expect(pub.slots.hero.url).toBe(img.url);
    expect(pub.slots.hero.alt).toBe('Children at a table');

    const file = await call(req(img.url));
    expect(file.status).toBe(200);
    expect(file.headers.get('Content-Type')).toBe('image/jpeg');
    expect(file.headers.get('X-Content-Type-Options')).toBe('nosniff');
    expect(file.headers.get('Cache-Control')).toContain('immutable');
    expect(new Uint8Array(await file.arrayBuffer())).toEqual(JPEG);

    const etag = file.headers.get('ETag')!;
    expect((await call(req(img.url, { headers: { 'If-None-Match': etag } }))).status).toBe(304);
  });

  it('assigns, reassigns and clears a slot, and lists every slot with its page and section', async () => {
    const a = await uploadOk('a.png', PNG, { title: 'A' });
    const b = await uploadOk('b.png', PNG, { title: 'B' });
    const put = (imageId: string | null) => call(req('/media/admin/slots/garden', { method: 'PUT', json: { imageId }, ...as() }));
    const garden = (l: SlotList) => l.slots.find((s) => s.key === 'garden')!;

    let list = (await (await put(a.id)).json()) as SlotList;
    expect(garden(list).image!.id).toBe(a.id);
    expect(garden(list)).toMatchObject({ page: 'our-philosophy', section: 'Nature and responsibility' });
    expect(list.slots.find((s) => s.key === 'hero')!.image).toBeNull();

    list = (await (await put(b.id)).json()) as SlotList;
    expect(garden(list).image!.id).toBe(b.id);

    list = (await (await put(null)).json()) as SlotList;
    expect(garden(list).image).toBeNull();
    expect(((await (await call(req('/media/slots'))).json()) as PublicSlots).slots).toEqual({});
  });

  it('rejects unknown slots and unknown images', async () => {
    const img = await uploadOk();
    const put = (key: string, imageId: unknown) => call(req(`/media/admin/slots/${key}`, { method: 'PUT', json: { imageId }, ...as() }));
    expect((await put('nope', img.id)).status).toBe(404);
    expect((await put('hero', crypto.randomUUID())).status).toBe(404);
    expect((await put('hero', 'x')).status).toBe(422);
  });
});

describe('replace, edit, order and delete', () => {
  it('replaces the file: the slot shows the new image and the old object is removed', async () => {
    const img = await uploadOk('hero.png', PNG, { title: 'Hero', slotKey: 'hero' });
    const oldKey = [...ctx.env.MEDIA.objects.keys()][0];

    const res = await call(req(`/media/admin/images/${img.id}/file`, { method: 'PUT', body: upload(WEBP, 'new.webp'), ...as() }));
    expect(res.status).toBe(200);
    const next = (await res.json()) as Img;
    expect(next.url).not.toBe(img.url);
    expect(next.contentType).toBe('image/webp');
    expect(next.slots).toEqual(['hero']);

    expect(ctx.env.MEDIA.objects.has(oldKey)).toBe(false);
    expect(ctx.env.MEDIA.objects.size).toBe(1);
    expect((await call(req(img.url))).status).toBe(404);
    const pub = (await (await call(req('/media/slots'))).json()) as PublicSlots;
    expect(pub.slots.hero.url).toBe(next.url);
  });

  it('refuses a replacement that is not an image and keeps the original', async () => {
    const img = await uploadOk();
    const body = upload(new TextEncoder().encode('nope'), 'x.png');
    const res = await call(req(`/media/admin/images/${img.id}/file`, { method: 'PUT', body, ...as() }));
    expect(res.status).toBe(415);
    expect(ctx.env.MEDIA.objects.size).toBe(1);
    expect((await call(req(img.url))).status).toBe(200);
  });

  it('edits details and validates input', async () => {
    const img = await uploadOk();
    const patch = (id: string, json: unknown) => call(req(`/media/admin/images/${id}`, { method: 'PATCH', json, ...as() }));
    const ok = await patch(img.id, { title: 'New title', caption: 'Cap', alt: 'Alt', description: 'Desc', category: 'Events', sortOrder: 5 });
    expect(await ok.json()).toMatchObject({ title: 'New title', caption: 'Cap', alt: 'Alt', description: 'Desc', category: 'Events', sortOrder: 5 });
    expect((await patch(img.id, { title: '  ' })).status).toBe(422);
    expect((await patch(img.id, { sortOrder: 1.5 })).status).toBe(422);
    expect((await patch(crypto.randomUUID(), {})).status).toBe(404);
    expect((await patch('not-an-id', {})).status).toBe(404);
  });

  it('searches, filters and reorders the library', async () => {
    const a = await uploadOk('a.png', PNG, { title: 'Garden planting', category: 'Nature & garden' });
    const b = await uploadOk('b.png', PNG, { title: 'Science day', category: 'Activities', slotKey: 'science' });
    const list = async (qs: string) =>
      ((await (await call(req(`/media/admin/images${qs}`, as()))).json()) as { items: { id: string }[] }).items.map((i) => i.id);

    expect(await list('')).toEqual([b.id, a.id]); // newest first until ordered
    expect(await list('?q=garden')).toEqual([a.id]);
    expect(await list('?category=Activities')).toEqual([b.id]);
    expect(await list('?page=learning-approach')).toEqual([b.id]);
    expect(await list('?assigned=no')).toEqual([a.id]);

    const re = await call(req('/media/admin/images/reorder', { method: 'POST', json: { ids: [a.id, b.id] }, ...as() }));
    expect(re.status).toBe(204);
    expect(await list('')).toEqual([a.id, b.id]);
    expect((await call(req('/media/admin/images/reorder', { method: 'POST', json: { ids: ['x'] }, ...as() }))).status).toBe(422);
  });

  it('blocks deleting a published image unless forced, then keeps DB and storage consistent', async () => {
    const img = await uploadOk('hero.png', PNG, { title: 'Hero', slotKey: 'hero' });

    const blocked = await call(req(`/media/admin/images/${img.id}`, { method: 'DELETE', ...as() }));
    expect(blocked.status).toBe(409);
    expect(await blocked.json()).toMatchObject({ code: 'in_use', slots: ['hero'] });
    expect(ctx.env.MEDIA.objects.size).toBe(1);

    const gone = await call(req(`/media/admin/images/${img.id}?force=true`, { method: 'DELETE', ...as() }));
    expect(gone.status).toBe(204);
    expect(ctx.env.MEDIA.objects.size).toBe(0);
    expect(((await (await call(req('/media/slots'))).json()) as PublicSlots).slots).toEqual({});
    expect((await call(req(`/media/admin/images/${img.id}`, { method: 'DELETE', ...as() }))).status).toBe(404);
  });

  it('deletes an unpublished image without force', async () => {
    const img = await uploadOk();
    expect((await call(req(`/media/admin/images/${img.id}`, { method: 'DELETE', ...as() }))).status).toBe(204);
    expect(ctx.env.MEDIA.objects.size).toBe(0);
  });
});

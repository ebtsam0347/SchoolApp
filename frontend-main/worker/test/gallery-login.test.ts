import { beforeEach, describe, expect, it } from 'vitest';
import { issueGalleryToken, verifyGalleryToken, GALLERY_TOKEN_TTL_MS } from '../gallery-token';
import { handleMedia } from '../media';
import { PNG, req, setup, upload } from './helpers';

const PASSWORD = 'test-photo-password';
let ctx: Awaited<ReturnType<typeof setup>>;
beforeEach(async () => {
  ctx = await setup();
  ctx.env.GALLERY_PASSWORD = PASSWORD;
});

const call = (r: Request) => handleMedia(r, ctx.env);
const login = (password: unknown, ip = '203.0.113.7') =>
  call(req('/media/login', { method: 'POST', json: { password }, headers: { 'CF-Connecting-IP': ip } }));

describe('photo-admin login', () => {
  it('is disabled until GALLERY_PASSWORD is set', async () => {
    ctx.env.GALLERY_PASSWORD = undefined;
    const res = await login(PASSWORD);
    expect(res.status).toBe(503);
    expect(await res.json()).toMatchObject({ code: 'login_not_configured' });
  });

  it('issues a session token for the right password, and that token can manage images', async () => {
    const res = await login(PASSWORD);
    expect(res.status).toBe(200);
    const { token, expiresAt } = (await res.json()) as { token: string; expiresAt: string };
    expect(token.startsWith('g1.')).toBe(true);
    expect(new Date(expiresAt).getTime()).toBeGreaterThan(Date.now());

    const up = await call(req('/media/admin/images', { method: 'POST', body: upload(PNG, 'a.png', { title: 'Via photo login', slotKey: 'hero' }), token }));
    expect(up.status).toBe(201);
    expect(await up.json()).toMatchObject({ createdBy: 'photo-admin', slots: ['hero'] });
    expect((await call(req('/media/admin/slots', { token }))).status).toBe(200);
  });

  it('rejects a wrong or missing password without issuing anything', async () => {
    for (const bad of ['nope', '', undefined, 123, PASSWORD + ' ']) {
      const res = await login(bad);
      expect(res.status).toBe(401);
      expect(((await res.json()) as { token?: string }).token).toBeUndefined();
    }
  });

  it('throttles repeated wrong passwords per address, and a correct login clears the count', async () => {
    for (let i = 0; i < 5; i++) expect((await login('wrong')).status).toBe(401);
    expect((await login('wrong')).status).toBe(429);
    expect((await login(PASSWORD)).status).toBe(429); // even the right one while locked out
    expect((await login(PASSWORD, '198.51.100.9')).status).toBe(200); // other address unaffected

    for (let i = 0; i < 4; i++) await login('wrong', '192.0.2.1');
    expect((await login(PASSWORD, '192.0.2.1')).status).toBe(200);
    for (let i = 0; i < 4; i++) expect((await login('wrong', '192.0.2.1')).status).toBe(401); // counter was reset
  });

  it('refuses tampered, expired and old-password tokens', async () => {
    const { token } = await issueGalleryToken(PASSWORD);
    const [, payload, sig] = token.split('.');
    const forged = btoa(JSON.stringify({ exp: Date.now() + 1e9 })).replace(/=+$/, '');
    for (const bad of [`g1.${forged}.${sig}`, `g1.${payload}.AAAA`, 'g1.', 'g1.a.b.c', `${token}x`]) {
      expect((await call(req('/media/admin/images', { token: bad }))).status).toBe(401);
    }
    const old = await issueGalleryToken(PASSWORD, Date.now() - GALLERY_TOKEN_TTL_MS - 1000);
    expect((await call(req('/media/admin/images', { token: old.token }))).status).toBe(401);

    expect((await call(req('/media/admin/images', { token }))).status).toBe(200);
    ctx.env.GALLERY_PASSWORD = 'a-different-password'; // password changed: everyone is signed out
    expect((await call(req('/media/admin/images', { token }))).status).toBe(401);
    ctx.env.GALLERY_PASSWORD = undefined;
    expect((await call(req('/media/admin/images', { token }))).status).toBe(401);
  });

  it('keeps the backend ADMIN login working next to it', async () => {
    expect((await call(req('/media/admin/images', { token: ctx.adminToken }))).status).toBe(200);
  });

  it('verifyGalleryToken only accepts the matching password', async () => {
    const { token } = await issueGalleryToken(PASSWORD);
    expect(await verifyGalleryToken(token, PASSWORD)).toBe(true);
    expect(await verifyGalleryToken(token, 'other')).toBe(false);
  });
});

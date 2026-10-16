import { afterEach, describe, expect, it, vi } from 'vitest';
import { proxyApi } from '../api-proxy';

const login = () => new Request('https://school.test/api/auth/login', { method: 'POST', body: '{}' });

afterEach(() => vi.unstubAllGlobals());

describe('api proxy', () => {
  it('answers with a readable 503 while the placeholder backend is configured, without calling out', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await proxyApi(login(), { UPSTREAM_GATEWAY: 'https://api.example.com' });
    expect(res.status).toBe(503);
    const body = (await res.json()) as { code: string; detail: string; status: number };
    expect(body).toMatchObject({ code: 'BACKEND_UNAVAILABLE', status: 503 });
    expect(body.detail).toContain('not connected');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('turns Cloudflare 52x origin errors and network failures into the same 503', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"error_code":1016}', { status: 530 })));
    expect((await proxyApi(login(), { UPSTREAM_GATEWAY: 'https://api.school.test' })).status).toBe(503);
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('failed')));
    expect((await proxyApi(login(), { UPSTREAM_GATEWAY: 'https://api.school.test' })).status).toBe(503);
  });

  it('passes real backend responses through untouched, including 401 and 500', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"code":"BAD_CREDENTIALS"}', { status: 401 }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await proxyApi(login(), { UPSTREAM_GATEWAY: 'https://api.school.test/' });
    expect(res.status).toBe(401);
    expect(await res.text()).toContain('BAD_CREDENTIALS');
    expect(fetchMock.mock.calls[0][0]).toBe('https://api.school.test/auth/login');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('boom', { status: 500 })));
    expect((await proxyApi(login(), { UPSTREAM_GATEWAY: 'https://api.school.test' })).status).toBe(500);
  });

  it('rejects an unusable gateway value', async () => {
    expect((await proxyApi(login(), { UPSTREAM_GATEWAY: 'not a url' })).status).toBe(503);
  });
});

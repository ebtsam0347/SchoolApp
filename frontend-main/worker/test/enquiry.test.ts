import { afterEach, describe, expect, it, vi } from 'vitest';
import { handleEnquiry } from '../enquiry';

const env = { ENQUIRY_WEBHOOK_URL: 'https://hooks.school.test/enquiry' };
const base = { enquiryType: 'visit', parentName: 'Amina Khan', consent: true };

function post(body: Record<string, unknown>) {
  return handleEnquiry(
    new Request('https://school.test/forms/enquiry', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),
    env,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('enquiry form', () => {
  it('accepts a WhatsApp enquiry with a phone number and no email', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await post({ ...base, contactMethod: 'whatsapp', phone: '+92 300 1234567' });
    expect(res.status).toBe(200);
    const sent = JSON.parse(fetchMock.mock.calls[0][1].body as string);
    expect(sent).toMatchObject({ contactMethod: 'whatsapp', phone: '+92 300 1234567', email: null });
  });

  it('requires a phone number for WhatsApp or phone replies', async () => {
    vi.stubGlobal('fetch', vi.fn());
    expect((await post({ ...base, contactMethod: 'whatsapp' })).status).toBe(422);
    expect((await post({ ...base, contactMethod: 'phone', email: 'a@b.co' })).status).toBe(422);
  });

  it('requires a valid email only when the family asks for an email reply', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 200 })));
    expect((await post({ ...base, contactMethod: 'email' })).status).toBe(422);
    expect((await post({ ...base, contactMethod: 'email', email: 'not-an-email' })).status).toBe(422);
    expect((await post({ ...base, contactMethod: 'email', email: 'amina@example.com' })).status).toBe(200);
  });

  it('rejects a malformed optional email even for phone replies', async () => {
    vi.stubGlobal('fetch', vi.fn());
    expect((await post({ ...base, contactMethod: 'phone', phone: '03001234567', email: 'nope' })).status).toBe(422);
  });
});

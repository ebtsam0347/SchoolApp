// In-memory stand-ins for D1 (node:sqlite) and KV, plus RS256 token minting,
// so the media API can be exercised end to end without Cloudflare.

// `node:sqlite` is loaded via getBuiltinModule because Vite strips the `node:`
// prefix from static imports and then cannot resolve plain `sqlite`.
type SqliteDb = {
  prepare(sql: string): { get(...a: never[]): unknown; all(...a: never[]): unknown[]; run(...a: never[]): unknown };
  exec(sql: string): void;
};
const { DatabaseSync } = (process as unknown as { getBuiltinModule(id: string): { DatabaseSync: new (path: string) => SqliteDb } }).getBuiltinModule(
  'node:sqlite',
);
import { resetKeyCache } from '../auth';
import { resetSchemaCache } from '../schema';
import type { MediaEnv } from '../media';

class FakeStatement {
  constructor(
    private db: SqliteDb,
    readonly sql: string,
    private args: unknown[] = [],
  ) {}
  bind(...args: unknown[]) {
    return new FakeStatement(this.db, this.sql, args);
  }
  async first<T>() {
    return (this.db.prepare(this.sql).get(...(this.args as never[])) as T | undefined) ?? null;
  }
  async all<T>() {
    return { results: this.db.prepare(this.sql).all(...(this.args as never[])) as T[], success: true };
  }
  async run() {
    this.db.prepare(this.sql).run(...(this.args as never[]));
    return { success: true };
  }
  runSync() {
    this.db.prepare(this.sql).run(...(this.args as never[]));
  }
}

export function fakeD1(): D1Database {
  const db = new DatabaseSync(':memory:');
  return {
    prepare: (sql: string) => new FakeStatement(db, sql),
    batch: async (stmts: FakeStatement[]) => {
      db.exec('BEGIN');
      try {
        for (const s of stmts) s.runSync();
        db.exec('COMMIT');
      } catch (e) {
        db.exec('ROLLBACK');
        throw e;
      }
      return stmts.map(() => ({ success: true }));
    },
  } as unknown as D1Database;
}

export type FakeKV = KVNamespace<string> & { objects: Map<string, { bytes: Uint8Array; contentType?: string }> };

/** In-memory KV namespace: only the calls the media API uses. */
export function fakeKV(): FakeKV {
  const objects = new Map<string, { bytes: Uint8Array; contentType?: string }>();
  return {
    objects,
    put: async (key: string, value: Uint8Array, opts?: { metadata?: { contentType?: string } }) => {
      objects.set(key, { bytes: value, contentType: opts?.metadata?.contentType });
    },
    getWithMetadata: async (key: string) => {
      const o = objects.get(key);
      if (!o) return { value: null, metadata: null };
      const copy = o.bytes.slice().buffer;
      return { value: copy, metadata: { contentType: o.contentType } };
    },
    delete: async (key: string) => {
      objects.delete(key);
    },
  } as unknown as FakeKV;
}

// --- tokens ------------------------------------------------------------------

const b64url = (b: ArrayBuffer | Uint8Array | string) => {
  const bytes = typeof b === 'string' ? new TextEncoder().encode(b) : new Uint8Array(b);
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

export async function makeSigner(kid = 'k1') {
  const pair = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify'],
  );
  const jwk = { ...(await crypto.subtle.exportKey('jwk', pair.publicKey)), kid, alg: 'RS256', use: 'sig' };
  return {
    jwks: JSON.stringify({ keys: [jwk] }),
    async sign(claims: Record<string, unknown>, header: Record<string, unknown> = {}) {
      const head = b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid, ...header }));
      const body = b64url(JSON.stringify(claims));
      const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', pair.privateKey, new TextEncoder().encode(`${head}.${body}`));
      return `${head}.${body}.${b64url(sig)}`;
    },
  };
}

export const inMinutes = (m: number) => Math.floor(Date.now() / 1000) + m * 60;

export async function setup() {
  resetKeyCache();
  resetSchemaCache();
  const signer = await makeSigner();
  const env: MediaEnv = { DB: fakeD1(), MEDIA: fakeKV(), JWKS_JSON: signer.jwks };
  const adminToken = await signer.sign({ sub: '1', username: 'admin.user', roles: ['ADMIN'], exp: inMinutes(10) });
  return { env: env as MediaEnv & { MEDIA: FakeKV }, signer, adminToken };
}

// --- request builders ---------------------------------------------------------

export const PNG = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d, 1, 2, 3, 4]);
export const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 0x10, 0x4a, 0x46, 0x49, 0x46, 0, 1]);
export const WEBP = new Uint8Array([0x52, 0x49, 0x46, 0x46, 4, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20]);

export function upload(bytes: Uint8Array, name: string, fields: Record<string, string> = {}): FormData {
  const form = new FormData();
  form.set('file', new File([bytes as BlobPart], name));
  for (const [k, v] of Object.entries(fields)) form.set(k, v);
  return form;
}

export function req(path: string, init: RequestInit & { token?: string; json?: unknown } = {}): Request {
  const { token, json, ...rest } = init;
  const headers = new Headers(rest.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let body = rest.body;
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(json);
  }
  return new Request(`https://school.test${path}`, { ...rest, headers, body });
}

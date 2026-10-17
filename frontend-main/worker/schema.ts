// D1 schema for website images. Applied idempotently by ensureSchema() on the
// first media request after a deploy, so there is no separate migration step
// to forget. Changes after the first release must be additive (new nullable
// columns / new tables) and appended to MIGRATIONS below.
//
// Mirrors the backend conventions: audit columns hold usernames, timestamps
// are ISO-8601 UTC strings (D1 has no timestamp type).

export const MIGRATIONS: string[] = [
  `CREATE TABLE IF NOT EXISTS images (
     id           TEXT PRIMARY KEY,
     title        TEXT NOT NULL,
     caption      TEXT,
     description  TEXT,
     alt          TEXT,
     category     TEXT NOT NULL DEFAULT 'General',
     object_key   TEXT NOT NULL UNIQUE,
     content_type TEXT NOT NULL,
     size_bytes   INTEGER NOT NULL,
     original_name TEXT,
     sort_order   INTEGER NOT NULL DEFAULT 0,
     created_at   TEXT NOT NULL,
     updated_at   TEXT NOT NULL,
     created_by   TEXT NOT NULL,
     updated_by   TEXT NOT NULL
   )`,
  `CREATE INDEX IF NOT EXISTS idx_images_sort ON images (sort_order, created_at DESC)`,
  `CREATE TABLE IF NOT EXISTS slots (
     slot_key   TEXT PRIMARY KEY,
     image_id   TEXT REFERENCES images (id) ON DELETE SET NULL,
     updated_at TEXT NOT NULL,
     updated_by TEXT NOT NULL
   )`,
  // Failed photo-admin sign-ins, for throttling (worker/media.ts).
  `CREATE TABLE IF NOT EXISTS login_failures (
     ip TEXT NOT NULL,
     at INTEGER NOT NULL
   )`,
  `CREATE INDEX IF NOT EXISTS idx_login_failures_ip ON login_failures (ip, at)`,
];

let ready: Promise<void> | null = null;

export function ensureSchema(db: D1Database): Promise<void> {
  ready ??= db
    .batch(MIGRATIONS.map((sql) => db.prepare(sql)))
    .then(() => undefined)
    .catch((e) => {
      ready = null; // retry on the next request
      throw e;
    });
  return ready;
}

/** Test hook: forget that the schema was applied (each test gets a fresh DB). */
export function resetSchemaCache(): void {
  ready = null;
}

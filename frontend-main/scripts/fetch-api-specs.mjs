// Refresh the committed OpenAPI snapshots from the LIVE gateway.
//
// Source of truth = each service's springdoc, aggregated by the gateway under
// /v3/api-docs/<group>. We discover the group list dynamically from
// /v3/api-docs/swagger-config so a new backend service is picked up the next
// time this runs — no hardcoded list to keep in sync.
//
// Output: docs/services/<group>.openapi.json (pretty-printed, stable key order
// from the server). Commit the result. `npm run gen:api` then turns these into
// src/generated/*.ts offline — so CI never needs the gateway running.
//
// Usage:
//   npm run gen:api:fetch                      # against http://localhost:8080
//   API_DOCS_BASE=https://app.hrms.local npm run gen:api:fetch

import { writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const BASE = (process.env.API_DOCS_BASE ?? 'http://localhost:8080').replace(/\/$/, '');
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const OUT_DIR = join(ROOT, 'docs', 'services');

/** Last path segment of /v3/api-docs/<group> → <group>. */
function groupName(url) {
  return url.split('/').filter(Boolean).pop();
}

async function getJson(url) {
  const res = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText} for ${url}`);
  return res.json();
}

async function main() {
  const configUrl = `${BASE}/v3/api-docs/swagger-config`;
  console.log(`Discovering groups from ${configUrl}`);

  let config;
  try {
    config = await getJson(configUrl);
  } catch (e) {
    console.error(
      `\nCould not reach the gateway at ${BASE}.\n` +
        `Is the stack up? Override the host with API_DOCS_BASE=... if it lives elsewhere.\n` +
        `(${e.message})`,
    );
    process.exit(1);
  }

  const groups = (config.urls ?? []).map((u) => ({ name: groupName(u.url), url: `${BASE}${u.url}` }));
  if (groups.length === 0) {
    console.error('swagger-config returned no groups — nothing to fetch.');
    process.exit(1);
  }

  await mkdir(OUT_DIR, { recursive: true });

  const results = [];
  for (const g of groups) {
    const spec = await getJson(g.url);
    const pathCount = Object.keys(spec.paths ?? {}).length;
    const schemaCount = Object.keys(spec.components?.schemas ?? {}).length;
    const file = join(OUT_DIR, `${g.name}.openapi.json`);
    await writeFile(file, `${JSON.stringify(spec, null, 2)}\n`);
    results.push({ group: g.name, paths: pathCount, schemas: schemaCount });
  }

  console.log(`\nRefreshed ${results.length} specs in docs/services/:`);
  for (const r of results) {
    console.log(`  ${r.group.padEnd(14)} ${String(r.paths).padStart(3)} paths  ${String(r.schemas).padStart(3)} schemas`);
  }
  console.log(`\nNext: npm run gen:api  (regenerates src/generated/*.ts)`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

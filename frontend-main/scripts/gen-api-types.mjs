// Generate TypeScript types from the committed OpenAPI snapshots.
//
// Reads every docs/services/*.openapi.json and emits src/generated/<group>.ts
// via openapi-typescript. Deterministic and offline — runs in CI without the
// gateway. Refresh the inputs with `npm run gen:api:fetch` when the backend
// changes; this step turns them into types.
//
// Consume the output like:
//   import type { components } from '@/generated/bff';
//   type EmployeeSummary = components['schemas']['EmployeeSummary'];
//
// The emitted files are committed so `tsc` / CI never depend on running this.
// Do not hand-edit them — regenerate instead.

import { readdir, readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import openapiTS, { astToString } from 'openapi-typescript';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SPEC_DIR = join(ROOT, 'docs', 'services');
const OUT_DIR = join(ROOT, 'src', 'generated');

const HEADER =
  '/* eslint-disable */\n' +
  '/**\n' +
  ' * GENERATED FILE — do not edit by hand.\n' +
  ' * Source: docs/services/<group>.openapi.json (refresh via `npm run gen:api:fetch`).\n' +
  ' * Regenerate: `npm run gen:api`.\n' +
  ' */\n\n';

async function main() {
  const files = (await readdir(SPEC_DIR)).filter((f) => f.endsWith('.openapi.json'));
  if (files.length === 0) {
    console.error(`No *.openapi.json in ${SPEC_DIR}. Run \`npm run gen:api:fetch\` first.`);
    process.exit(1);
  }

  await mkdir(OUT_DIR, { recursive: true });

  for (const f of files.sort()) {
    const group = f.replace(/\.openapi\.json$/, '');
    const schema = JSON.parse(await readFile(join(SPEC_DIR, f), 'utf8'));
    const ast = await openapiTS(schema);
    const out = HEADER + astToString(ast);
    await writeFile(join(OUT_DIR, `${group}.ts`), out);
    console.log(`  src/generated/${group}.ts`);
  }

  console.log(`\nGenerated ${files.length} type modules.`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

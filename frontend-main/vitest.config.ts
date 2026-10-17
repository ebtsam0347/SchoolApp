import { defineConfig } from 'vitest/config';

// Tests for the Cloudflare Worker (worker/) and the public site's translations
// (src/site). They run in plain Node; the Worker ones use in-memory D1/KV fakes.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['worker/**/*.test.ts', 'src/site/**/*.test.ts'],
  },
});

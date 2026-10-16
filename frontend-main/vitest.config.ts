import { defineConfig } from 'vitest/config';

// Unit/integration tests for the Cloudflare Worker (worker/). The SPA has no
// test runner; these run in plain Node against in-memory D1/R2 fakes.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['worker/**/*.test.ts'],
  },
});

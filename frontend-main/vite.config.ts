import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { tanstackRouter } from '@tanstack/router-plugin/vite';
import path from 'node:path';

const ReactCompilerConfig = {
  target: '19',
};

export default defineConfig({
  plugins: [
    // Router plugin must run before React (it generates the routeTree).
    tanstackRouter({
      target: 'react',
      autoCodeSplitting: true,
      routesDirectory: 'src/routes',
      generatedRouteTree: 'src/routeTree.gen.ts',
    }),
    react({
      babel: {
        plugins: [['babel-plugin-react-compiler', ReactCompilerConfig]],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    open: false,
    // Website images are served by the Cloudflare Worker (`npm run cf:dev`,
    // port 8787), not the Vite dev server.
    proxy: { '/media': 'http://localhost:8787' },
  },
});

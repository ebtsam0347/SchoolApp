// One-shot route tree regenerator. Mirrors the tanstackRouter Vite plugin
// inputs from vite.config.ts. Useful when you want a fresh src/routeTree.gen.ts
// without spinning up `vite dev` or `vite build`.

import { Generator, getConfig } from '@tanstack/router-generator';

const root = process.cwd();

const config = await getConfig(
  {
    target: 'react',
    autoCodeSplitting: true,
    routesDirectory: 'src/routes',
    generatedRouteTree: 'src/routeTree.gen.ts',
  },
  root,
);

const generator = new Generator({ config, root });
await generator.run();
console.log('routeTree.gen.ts regenerated');

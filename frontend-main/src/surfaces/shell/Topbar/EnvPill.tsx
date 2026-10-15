// Environment label shown on non-prod deploys. Hidden in production via
// the `env !== 'prod'` check in Topbar.

import { Tag } from 'antd';

export function EnvPill({ env }: { env: 'dev' | 'stg' }) {
  const color = env === 'dev' ? 'gold' : 'orange';
  return (
    <Tag color={color} style={{ margin: 0, fontSize: 10, letterSpacing: '0.08em' }}>
      {env.toUpperCase()}
    </Tag>
  );
}

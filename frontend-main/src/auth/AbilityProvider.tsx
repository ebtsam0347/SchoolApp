// Wires the CASL ability into the React tree. Sits inside <AuthProvider>
// because it derives directly from `useAuth().user` — when the user signs in,
// changes password, or has roles updated server-side and we re-fetch /auth/me,
// the ability rebuilds and every <Can> consumer re-renders.

import { type ReactNode } from 'react';
import { AbilityContext, buildAbility } from './ability';
import { useAuth } from './AuthContext';

export function AbilityProvider({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const ability = buildAbility(auth.user);
  return <AbilityContext value={ability}>{children}</AbilityContext>;
}

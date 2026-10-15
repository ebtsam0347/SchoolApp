// /canteen — no landing page of its own; the meal ledger is the surface
// the canteen desk lives on all day, so send them straight there.

import { createFileRoute, redirect } from '@tanstack/react-router';

export const Route = createFileRoute('/_app/canteen/')({
  beforeLoad: () => {
    throw redirect({ to: '/canteen/attendance' });
  },
});

// /accounts — Accounts (Preview) workspace layout.
//
// No permission gate yet — visibility is controlled by the workspace
// switcher in the topbar. The route exists for everyone with an authed
// session; whether they see it in the sidebar depends on which workspace
// they have selected.

import { createFileRoute, Outlet } from '@tanstack/react-router';

export const Route = createFileRoute('/_app/accounts')({
  component: () => <Outlet />,
});

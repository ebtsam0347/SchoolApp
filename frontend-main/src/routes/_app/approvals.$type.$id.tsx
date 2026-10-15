// /approvals/<type>/<id> — legacy notification deep link.
//
// The seeded notification templates render links like
// `/approvals/overtime/67`, which the app never had a page for. The bell and
// /notifications now translate them before navigating (resolve-link.ts); this
// route catches the same URL when it arrives any other way — an old tab, a
// bookmark, an email — and forwards to the inbox tab with that request open.

import { createFileRoute, redirect } from '@tanstack/react-router';
import { resolveNotificationLink } from '@/notifications';

export const Route = createFileRoute('/_app/approvals/$type/$id')({
  beforeLoad: ({ params }) => {
    const { to, search } = resolveNotificationLink(`/approvals/${params.type}/${params.id}`);
    throw redirect({ to, search, replace: true });
  },
});

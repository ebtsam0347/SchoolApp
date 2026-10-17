import { createFileRoute } from '@tanstack/react-router';
// /admin/gallery — the website image library (see admin/gallery/GalleryPanel.tsx).
// The same panel is also available without the school portal login at /photos.
import { requireAdminRole } from '@/admin/gallery/guard';
import { GalleryPanel } from '@/admin/gallery/GalleryPanel';

export const Route = createFileRoute('/_app/admin/gallery')({
  beforeLoad: requireAdminRole,
  component: GalleryPanel,
});

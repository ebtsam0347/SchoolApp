import { createFileRoute } from '@tanstack/react-router';
// /admin/site-images — which photo appears in each part of the public website
// (see admin/gallery/SiteImagesPanel.tsx). Also available at /photos.
import { requireAdminRole } from '@/admin/gallery/guard';
import { SiteImagesPanel } from '@/admin/gallery/SiteImagesPanel';

export const Route = createFileRoute('/_app/admin/site-images')({
  beforeLoad: requireAdminRole,
  component: SiteImagesPanel,
});

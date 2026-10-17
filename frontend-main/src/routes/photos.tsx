import { createFileRoute } from '@tanstack/react-router';
// /photos — website photo management that does not need the school portal.
//
// A separate sign-in (the GALLERY_PASSWORD secret, see worker/media.ts) opens the
// same Page images and Gallery tools as Admin → Website images, so the school can
// manage the public site's photographs even while the portal backend is offline.

import { useEffect } from 'react';
import { Button, Tabs, theme as antdTheme } from 'antd';
import { ExternalLink, LogOut } from 'lucide-react';
import { GalleryPanel } from '@/admin/gallery/GalleryPanel';
import { PhotosLogin } from '@/admin/gallery/PhotosLogin';
import { SiteImagesPanel } from '@/admin/gallery/SiteImagesPanel';
import { clearPhotoSession, usePhotoSession } from '@/admin/gallery/photo-session';

export const Route = createFileRoute('/photos')({
  component: PhotosPage,
});

function PhotosPage() {
  const { token } = antdTheme.useToken();
  const session = usePhotoSession();

  // Staff-only page: keep it out of search results.
  useEffect(() => {
    document.title = 'Website photos — The Spirit of Islam School';
    const meta = document.createElement('meta');
    meta.name = 'robots';
    meta.content = 'noindex, nofollow';
    document.head.appendChild(meta);
    return () => meta.remove();
  }, []);

  return (
    <div style={{ minHeight: '100dvh', background: token.colorBgLayout }}>
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '10px 20px',
          background: token.colorBgContainer,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
          flexWrap: 'wrap',
        }}
      >
        <img src="/icons/school-logo.png" alt="" width={36} height={36} />
        <div style={{ fontWeight: 600, flex: 1, minWidth: 160 }}>
          Website photos
          <div style={{ fontWeight: 400, fontSize: 12, color: token.colorTextSecondary }}>The Spirit of Islam School</div>
        </div>
        <Button type="link" href="/home" target="_blank" rel="noopener noreferrer" icon={<ExternalLink size={14} />}>
          View website
        </Button>
        {session && (
          <Button icon={<LogOut size={14} />} onClick={clearPhotoSession}>
            Sign out
          </Button>
        )}
      </header>

      <main style={{ maxWidth: 1240, margin: '0 auto', padding: 'var(--app-space-lg, 24px) 16px 48px' }}>
        {session ? (
          <Tabs
            defaultActiveKey="pages"
            destroyOnHidden
            items={[
              { key: 'pages', label: 'Page images', children: <SiteImagesPanel /> },
              { key: 'gallery', label: 'Gallery', children: <GalleryPanel /> },
            ]}
          />
        ) : (
          <PhotosLogin />
        )}
      </main>
    </div>
  );
}

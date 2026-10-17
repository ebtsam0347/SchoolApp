// Choose which photo appears in each section of the public website: pick a page,
// pick the section, then upload or select a gallery image. Saving publishes
// immediately: the public site reads /media/slots, so no redeploy is needed.

import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Card, Segmented, Tag, theme as antdTheme } from 'antd';
import { Camera, ExternalLink, ImageOff } from 'lucide-react';
import { PageHeader, RequestState } from '@/components';
import { getErrorMessage } from '@/lib';
import { SITE_PAGES } from '@/site/photo-slots';
import { mediaApi, type SlotRow } from './api';
import { SafeImg } from './SafeImg';
import { SlotPickerDrawer } from './SlotPickerDrawer';

export function SiteImagesPanel() {
  const { token } = antdTheme.useToken();
  const { message, modal } = AntdApp.useApp();
  const qc = useQueryClient();
  const [pageKey, setPageKey] = useState<string>('all');
  const [editing, setEditing] = useState<SlotRow | null>(null);

  const slots = useQuery({ queryKey: ['media', 'slots'], queryFn: ({ signal }) => mediaApi.slots(signal) });
  const rows = (slots.data?.slots ?? []).filter((s) => pageKey === 'all' || s.page === pageKey);
  const pageOf = (key: string) => SITE_PAGES.find((p) => p.key === key);

  const unpublish = (slot: SlotRow) =>
    modal.confirm({
      title: `Remove the photo from “${slot.label}”?`,
      content: 'The website goes back to the default illustration here. The image stays in the gallery.',
      okText: 'Remove from website',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await mediaApi.assign(slot.key, null);
          await qc.invalidateQueries({ queryKey: ['media'] });
          message.success('Removed from the website. The image is still in the gallery.');
        } catch (e) {
          message.error(getErrorMessage(e));
          throw e;
        }
      },
    });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-lg)', minWidth: 0 }}>
      <PageHeader
        eyebrow="Website"
        title="Page images"
        description="Choose the photograph shown in each part of the public website. Changes go live within about a minute."
      />

      <div style={{ overflowX: 'auto', maxWidth: '100%' }}>
        <Segmented
          value={pageKey}
          onChange={(v) => setPageKey(String(v))}
          options={[{ value: 'all', label: 'All pages' }, ...SITE_PAGES.map((p) => ({ value: p.key, label: p.label }))]}
        />
      </div>

      <RequestState
        loading={slots.isLoading}
        error={slots.error}
        empty={rows.length === 0}
        onRetry={() => slots.refetch()}
        errorTitle="Couldn’t load the website image slots"
        emptyTitle="No image slots on this page"
      >
        <ul
          style={{
            listStyle: 'none',
            margin: 0,
            padding: 0,
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 300px), 1fr))',
            gap: 16,
          }}
        >
          {rows.map((slot) => {
            const page = pageOf(slot.page);
            return (
              <li key={slot.key}>
                <Card
                  size="small"
                  styles={{ body: { padding: 14 } }}
                  cover={
                    <div style={{ aspectRatio: '4 / 3', background: token.colorFillTertiary, display: 'grid', placeItems: 'center', overflow: 'hidden' }}>
                      {slot.image ? (
                        <SafeImg src={slot.image.url} alt={slot.image.alt ?? slot.image.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      ) : (
                        <div style={{ textAlign: 'center', color: token.colorTextTertiary, padding: 12 }}>
                          <ImageOff size={26} strokeWidth={1.5} aria-hidden="true" />
                          <div style={{ fontSize: 13, marginTop: 6 }}>No photo yet — the website shows an illustration</div>
                        </div>
                      )}
                    </div>
                  }
                >
                  <div style={{ fontWeight: 600 }}>{slot.label}</div>
                  <div style={{ color: token.colorTextSecondary, fontSize: 13, margin: '2px 0 8px' }}>
                    {page?.label} · {slot.section}
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 10 }}>
                    {slot.image ? (
                      <Tag color="green" style={{ margin: 0 }}>
                        Published: {slot.image.title}
                      </Tag>
                    ) : (
                      <Tag style={{ margin: 0 }}>Not set</Tag>
                    )}
                  </div>
                  <div style={{ fontSize: 12, color: token.colorTextTertiary, marginBottom: 12 }}>
                    Also shown on: {slot.usedOn.filter((u) => !u.startsWith(`${page?.label} · ${slot.section}`)).join('; ') || '—'}
                  </div>
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <Button type="primary" size="small" icon={<Camera size={14} />} onClick={() => setEditing(slot)}>
                      {slot.image ? 'Change image' : 'Choose image'}
                    </Button>
                    {slot.image && (
                      <Button size="small" danger onClick={() => unpublish(slot)}>
                        Remove
                      </Button>
                    )}
                    {page && (
                      <Button size="small" type="link" href={page.path} target="_blank" rel="noopener noreferrer" icon={<ExternalLink size={13} />}>
                        View page
                      </Button>
                    )}
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      </RequestState>

      <SlotPickerDrawer slot={editing} onClose={() => setEditing(null)} />
    </div>
  );
}

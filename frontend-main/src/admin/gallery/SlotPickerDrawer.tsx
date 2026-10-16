// Change the image shown in one website slot: pick from the gallery (or upload
// a new one), preview it cropped the way the page will show it, then publish.

import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Drawer, Empty, Input, Skeleton, Tag, theme as antdTheme } from 'antd';
import { Check, Search, Upload as UploadIcon } from 'lucide-react';
import { getErrorMessage, useDebounced } from '@/lib';
import { SITE_PAGES } from '@/site/photo-slots';
import { mediaApi, type MediaImage, type SlotRow } from './api';
import { ImageFormModal } from './ImageFormModal';
import { SafeImg } from './SafeImg';

function cssRatio(ratio: string): string {
  return ratio === 'auto' ? '4 / 3' : ratio;
}

export function SlotPickerDrawer({ slot, onClose }: { slot: SlotRow | null; onClose: () => void }) {
  const { token } = antdTheme.useToken();
  const { message } = AntdApp.useApp();
  const qc = useQueryClient();
  const [selected, setSelected] = useState<MediaImage | null>(null);
  const [search, setSearch] = useState('');
  const q = useDebounced(search, 250);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setSelected(slot?.image ?? null);
    setSearch('');
  }, [slot?.key, slot?.image]);

  const library = useQuery({
    queryKey: ['media', 'images', { q }],
    queryFn: ({ signal }) => mediaApi.list({ q }, signal),
    enabled: !!slot,
  });

  const publish = async () => {
    if (!slot || !selected) return;
    setSaving(true);
    try {
      await mediaApi.assign(slot.key, selected.id);
      await qc.invalidateQueries({ queryKey: ['media'] });
      message.success(`“${slot.label}” now shows “${selected.title}” on the website.`);
      onClose();
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const unchanged = !!slot && selected?.id === slot.image?.id;
  const pageLabel = slot ? SITE_PAGES.find((p) => p.key === slot.page)?.label : '';

  return (
    <>
      <Drawer
        open={!!slot}
        onClose={onClose}
        width="min(760px, 100vw)"
        destroyOnHidden
        title={slot ? `${slot.label}` : ''}
        extra={
          <Button type="primary" disabled={!selected || unchanged} loading={saving} onClick={publish}>
            Save and publish
          </Button>
        }
      >
        {slot && (
          <div style={{ display: 'grid', gap: 20 }}>
            <div>
              <div style={{ color: token.colorTextSecondary, fontSize: 13, marginBottom: 8 }}>
                {pageLabel} · {slot.section} · shown at {cssRatio(slot.ratio).replace(' / ', ':')} (cropped to fit)
              </div>
              <div
                style={{
                  aspectRatio: cssRatio(slot.ratio),
                  maxHeight: 320,
                  margin: '0 auto',
                  borderRadius: token.borderRadiusLG,
                  overflow: 'hidden',
                  background: token.colorFillTertiary,
                  display: 'grid',
                  placeItems: 'center',
                }}
              >
                {selected ? (
                  <SafeImg src={selected.url} alt={selected.alt ?? selected.title} loading="eager" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <span style={{ color: token.colorTextTertiary }}>Choose an image below</span>
                )}
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
                {slot.usedOn.map((u) => (
                  <Tag key={u} style={{ margin: 0 }}>
                    {u}
                  </Tag>
                ))}
              </div>
            </div>

            <div>
              <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
                <Input
                  allowClear
                  prefix={<Search size={14} aria-hidden="true" />}
                  placeholder="Search the gallery"
                  aria-label="Search the gallery"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  style={{ flex: '1 1 220px' }}
                />
                <Button icon={<UploadIcon size={14} />} onClick={() => setUploadOpen(true)}>
                  Upload new image
                </Button>
              </div>

              {library.isLoading ? (
                <Skeleton active />
              ) : library.error ? (
                <p role="alert" style={{ color: token.colorError }}>
                  {getErrorMessage(library.error)}
                </p>
              ) : (library.data?.items.length ?? 0) === 0 ? (
                <Empty description={q ? 'No images match your search.' : 'The gallery is empty. Upload the first image.'} />
              ) : (
                <ul
                  style={{
                    listStyle: 'none',
                    margin: 0,
                    padding: 0,
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))',
                    gap: 10,
                  }}
                >
                  {library.data!.items.map((img) => {
                    const on = selected?.id === img.id;
                    return (
                      <li key={img.id}>
                        <button
                          type="button"
                          onClick={() => setSelected(img)}
                          aria-pressed={on}
                          aria-label={img.title}
                          style={{
                            all: 'unset',
                            cursor: 'pointer',
                            display: 'block',
                            position: 'relative',
                            width: '100%',
                            aspectRatio: '1',
                            borderRadius: token.borderRadius,
                            overflow: 'hidden',
                            outline: on ? `3px solid ${token.colorPrimary}` : `1px solid ${token.colorBorderSecondary}`,
                            outlineOffset: on ? -3 : -1,
                            boxSizing: 'border-box',
                          }}
                        >
                          <SafeImg src={img.url} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
                          {on && (
                            <span
                              style={{
                                position: 'absolute',
                                top: 6,
                                right: 6,
                                background: token.colorPrimary,
                                color: token.colorTextLightSolid,
                                borderRadius: 999,
                                width: 22,
                                height: 22,
                                display: 'grid',
                                placeItems: 'center',
                              }}
                            >
                              <Check size={14} aria-hidden="true" />
                            </span>
                          )}
                        </button>
                        <div style={{ fontSize: 12, marginTop: 4, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={img.title}>
                          {img.title}
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </div>
        )}
      </Drawer>

      <ImageFormModal
        mode="upload"
        open={uploadOpen && !!slot}
        defaultSlotKey={slot?.key}
        onClose={() => setUploadOpen(false)}
        onDone={async () => {
          // The upload already published it to this slot.
          await qc.invalidateQueries({ queryKey: ['media'] });
          setUploadOpen(false);
          onClose();
        }}
      />
    </>
  );
}

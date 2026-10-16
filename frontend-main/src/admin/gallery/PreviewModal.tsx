// Large preview of a gallery image with its details and where it is published.

import { Modal, Tag, theme as antdTheme } from 'antd';
import { PHOTO_SLOT_DEFS, SITE_PAGES } from '@/site/photo-slots';
import { formatBytes, type MediaImage } from './api';
import { SafeImg } from './SafeImg';

export function PreviewModal({ image, onClose }: { image: MediaImage | null; onClose: () => void }) {
  const { token } = antdTheme.useToken();
  const published = image ? image.slots.map((k) => PHOTO_SLOT_DEFS.find((s) => s.key === k)).filter((s) => !!s) : [];

  return (
    <Modal open={!!image} onCancel={onClose} footer={null} width={860} title={image?.title} destroyOnHidden>
      {image && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 20 }}>
          <div
            style={{
              borderRadius: token.borderRadiusLG,
              overflow: 'hidden',
              background: token.colorFillTertiary,
              alignSelf: 'start',
            }}
          >
            <SafeImg src={image.url} alt={image.alt ?? image.title} loading="eager" style={{ display: 'block', width: '100%', maxHeight: '62vh', objectFit: 'contain' }} />
          </div>
          <dl style={{ margin: 0, display: 'grid', gap: 10, alignContent: 'start', fontSize: 14 }}>
            <Row label="Category">{image.category}</Row>
            {image.caption && <Row label="Caption">{image.caption}</Row>}
            {image.description && <Row label="Description">{image.description}</Row>}
            <Row label="Alternative text">{image.alt || <span style={{ color: token.colorTextTertiary }}>Not set (site default is used)</span>}</Row>
            <Row label="File">
              {image.contentType.replace('image/', '').toUpperCase()} · {formatBytes(image.sizeBytes)}
              {image.originalName ? ` · ${image.originalName}` : ''}
            </Row>
            <Row label="Published on">
              {published.length === 0 ? (
                <span style={{ color: token.colorTextTertiary }}>Not published — gallery only</span>
              ) : (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {published.map((s) => (
                    <Tag key={s.key} color="blue" style={{ margin: 0 }}>
                      {SITE_PAGES.find((p) => p.key === s.page)?.label} · {s.label}
                    </Tag>
                  ))}
                </div>
              )}
            </Row>
            <Row label="Last updated">
              {new Date(image.updatedAt).toLocaleString()} by {image.updatedBy}
            </Row>
          </dl>
        </div>
      )}
    </Modal>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  const { token } = antdTheme.useToken();
  return (
    <div>
      <dt style={{ fontSize: 12, color: token.colorTextSecondary, marginBottom: 2 }}>{label}</dt>
      <dd style={{ margin: 0 }}>{children}</dd>
    </div>
  );
}

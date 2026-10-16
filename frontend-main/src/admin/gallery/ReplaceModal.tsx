// Replace the picture behind an existing gallery image. Every website slot the
// image is published to switches to the new file automatically.

import { useEffect, useMemo, useState } from 'react';
import { Alert, App as AntdApp, Button, Modal, Upload, theme as antdTheme } from 'antd';
import { ArrowRight, ImagePlus } from 'lucide-react';
import { getErrorMessage } from '@/lib';
import { PHOTO_SLOT_DEFS } from '@/site/photo-slots';
import { SafeImg } from './SafeImg';
import { ACCEPTED_TYPES, MAX_IMAGE_MB, formatBytes, mediaApi, validateImageFile, type MediaImage } from './api';

export function ReplaceModal({
  image,
  onClose,
  onDone,
}: {
  image: MediaImage | null;
  onClose: () => void;
  onDone: (image: MediaImage) => void;
}) {
  const { token } = antdTheme.useToken();
  const { message } = AntdApp.useApp();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setFile(null);
    setError(null);
  }, [image?.id]);

  const preview = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);
  useEffect(() => () => (preview ? URL.revokeObjectURL(preview) : undefined), [preview]);

  const places = image ? image.slots.map((k) => PHOTO_SLOT_DEFS.find((s) => s.key === k)?.label ?? k) : [];

  const submit = async () => {
    if (!image || !file) return;
    setSaving(true);
    try {
      const saved = await mediaApi.replace(image.id, file);
      message.success('Image replaced. The website now shows the new picture.');
      onDone(saved);
      onClose();
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const frame = {
    aspectRatio: '4 / 3',
    borderRadius: token.borderRadiusLG,
    overflow: 'hidden',
    background: token.colorFillTertiary,
  } as const;

  return (
    <Modal
      open={!!image}
      onCancel={saving ? undefined : onClose}
      title="Replace image"
      width={720}
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={onClose} disabled={saving}>
          Cancel
        </Button>,
        <Button key="ok" type="primary" loading={saving} disabled={!file} onClick={submit}>
          Replace image
        </Button>,
      ]}
    >
      {image && (
        <>
          {places.length > 0 && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={`This image is published on: ${places.join(', ')}.`}
              description="Those places will show the new picture as soon as you confirm."
            />
          )}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', gap: 12, alignItems: 'center' }}>
            <figure style={{ margin: 0 }}>
              <div style={frame}>
                <SafeImg src={image.url} alt={image.alt ?? image.title} loading="eager" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              </div>
              <figcaption style={{ fontSize: 12, color: token.colorTextSecondary, marginTop: 6 }}>Current</figcaption>
            </figure>
            <ArrowRight size={18} aria-hidden="true" />
            <figure style={{ margin: 0 }}>
              <div style={{ ...frame, display: 'grid', placeItems: 'center' }}>
                {preview ? (
                  <SafeImg src={preview} alt="New" loading="eager" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <span style={{ color: token.colorTextTertiary, fontSize: 13 }}>No file chosen</span>
                )}
              </div>
              <figcaption style={{ fontSize: 12, color: token.colorTextSecondary, marginTop: 6 }}>
                {file ? `${file.name} · ${formatBytes(file.size)}` : 'New'}
              </figcaption>
            </figure>
          </div>
          <Upload.Dragger
            accept={ACCEPTED_TYPES.join(',')}
            maxCount={1}
            showUploadList={false}
            style={{ marginTop: 16 }}
            beforeUpload={(f) => {
              const err = validateImageFile(f);
              setError(err);
              setFile(err ? null : f);
              return false;
            }}
          >
            <p style={{ margin: 0 }}>
              <ImagePlus size={24} strokeWidth={1.5} aria-hidden="true" />
            </p>
            <p style={{ margin: '6px 0 2px', fontWeight: 500 }}>Click or drag the new image here</p>
            <p style={{ margin: 0, color: token.colorTextSecondary, fontSize: 12 }}>JPG, PNG or WebP · up to {MAX_IMAGE_MB} MB</p>
          </Upload.Dragger>
          {error && (
            <p role="alert" style={{ margin: '8px 0 0', color: token.colorError, fontSize: 13 }}>
              {error}
            </p>
          )}
        </>
      )}
    </Modal>
  );
}

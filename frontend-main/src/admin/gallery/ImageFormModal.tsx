// Upload a new image, or edit an existing image's details. Shared form so both
// flows validate and label things the same way.

import { useEffect, useMemo, useState } from 'react';
import { App as AntdApp, Button, Form, Input, InputNumber, Modal, Select, Upload, theme as antdTheme } from 'antd';
import { ImagePlus } from 'lucide-react';
import { getErrorMessage } from '@/lib';
import { IMAGE_CATEGORIES, PHOTO_SLOT_DEFS } from '@/site/photo-slots';
import { SafeImg } from './SafeImg';
import { ACCEPTED_TYPES, MAX_IMAGE_MB, formatBytes, mediaApi, validateImageFile, type MediaImage } from './api';

type Values = {
  title: string;
  alt?: string;
  caption?: string;
  description?: string;
  category: string;
  sortOrder?: number;
  slotKey?: string;
};

type Props =
  | { mode: 'upload'; open: boolean; onClose: () => void; onDone: (image: MediaImage) => void; defaultSlotKey?: string }
  | { mode: 'edit'; open: boolean; onClose: () => void; onDone: (image: MediaImage) => void; image: MediaImage | null };

export function ImageFormModal(props: Props) {
  const { open, onClose, onDone } = props;
  const { token } = antdTheme.useToken();
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<Values>();
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const editing = props.mode === 'edit' ? props.image : null;
  const defaultSlotKey = props.mode === 'upload' ? props.defaultSlotKey : undefined;

  useEffect(() => {
    if (!open) return;
    setFile(null);
    setFileError(null);
    form.resetFields();
    form.setFieldsValue(
      editing
        ? {
            title: editing.title,
            alt: editing.alt ?? '',
            caption: editing.caption ?? '',
            description: editing.description ?? '',
            category: editing.category,
            sortOrder: editing.sortOrder,
          }
        : { category: 'General', slotKey: defaultSlotKey },
    );
  }, [open, editing, defaultSlotKey, form]);

  const previewUrl = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);
  useEffect(() => () => (previewUrl ? URL.revokeObjectURL(previewUrl) : undefined), [previewUrl]);

  const shownPreview = previewUrl ?? editing?.url ?? null;

  const choose = (f: File) => {
    const err = validateImageFile(f);
    setFileError(err);
    setFile(err ? null : f);
    if (!err && !form.getFieldValue('title')) form.setFieldValue('title', f.name.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' '));
    return false; // never auto-upload; the form submits
  };

  const submit = async (v: Values) => {
    if (props.mode === 'upload' && !file) {
      setFileError('Choose an image to upload.');
      return;
    }
    setSaving(true);
    try {
      let saved: MediaImage;
      if (props.mode === 'upload') {
        saved = await mediaApi.upload(file!, {
          title: v.title,
          alt: v.alt,
          caption: v.caption,
          description: v.description,
          category: v.category,
          slotKey: v.slotKey,
        });
        message.success(v.slotKey ? 'Image uploaded and published to the website.' : 'Image uploaded to the gallery.');
      } else {
        saved = await mediaApi.edit(props.image!.id, {
          title: v.title,
          alt: v.alt?.trim() || null,
          caption: v.caption?.trim() || null,
          description: v.description?.trim() || null,
          category: v.category,
          sortOrder: v.sortOrder ?? props.image!.sortOrder,
        });
        message.success('Image details saved.');
      }
      onDone(saved);
      onClose();
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={saving ? undefined : onClose}
      title={props.mode === 'upload' ? 'Upload image' : 'Edit image details'}
      width={720}
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={onClose} disabled={saving}>
          Cancel
        </Button>,
        <Button key="save" type="primary" loading={saving} onClick={() => form.submit()}>
          {props.mode === 'upload' ? 'Upload' : 'Save changes'}
        </Button>,
      ]}
    >
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 20 }}>
        <div>
          {shownPreview && (
            <div
              style={{
                aspectRatio: '4 / 3',
                borderRadius: token.borderRadiusLG,
                overflow: 'hidden',
                background: token.colorFillTertiary,
                marginBottom: 12,
              }}
            >
              <SafeImg src={shownPreview} alt="Preview" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            </div>
          )}
          {props.mode === 'upload' ? (
            <Upload.Dragger
              accept={ACCEPTED_TYPES.join(',')}
              maxCount={1}
              showUploadList={false}
              beforeUpload={choose}
              style={{ padding: shownPreview ? 0 : undefined }}
            >
              <p style={{ margin: 0 }}>
                <ImagePlus size={26} strokeWidth={1.5} aria-hidden="true" />
              </p>
              <p style={{ margin: '6px 0 2px', fontWeight: 500 }}>{file ? 'Choose a different image' : 'Click or drag an image here'}</p>
              <p style={{ margin: 0, color: token.colorTextSecondary, fontSize: 12 }}>
                JPG, PNG or WebP · up to {MAX_IMAGE_MB} MB
              </p>
            </Upload.Dragger>
          ) : (
            editing && (
              <p style={{ margin: 0, color: token.colorTextSecondary, fontSize: 12 }}>
                {editing.contentType.replace('image/', '').toUpperCase()} · {formatBytes(editing.sizeBytes)}. To change the picture itself, use
                “Replace image”.
              </p>
            )
          )}
          {file && (
            <p style={{ margin: '8px 0 0', color: token.colorTextSecondary, fontSize: 12 }}>
              {file.name} · {formatBytes(file.size)}
            </p>
          )}
          {fileError && (
            <p role="alert" style={{ margin: '8px 0 0', color: token.colorError, fontSize: 13 }}>
              {fileError}
            </p>
          )}
        </div>

        <Form form={form} layout="vertical" onFinish={submit} requiredMark="optional" disabled={saving}>
          <Form.Item name="title" label="Title" rules={[{ required: true, whitespace: true, message: 'Enter a title.' }, { max: 120 }]}>
            <Input maxLength={120} placeholder="e.g. Children building with blocks" />
          </Form.Item>
          <Form.Item
            name="alt"
            label="Alternative text"
            extra="Describes the picture for screen readers. Leave empty to keep the site’s default description."
            rules={[{ max: 200 }]}
          >
            <Input maxLength={200} placeholder="What is shown in the picture" />
          </Form.Item>
          <Form.Item name="caption" label="Caption" rules={[{ max: 300 }]}>
            <Input maxLength={300} />
          </Form.Item>
          <Form.Item name="description" label="Description" rules={[{ max: 1000 }]}>
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
          <Form.Item name="category" label="Category" rules={[{ required: true }]}>
            <Select options={IMAGE_CATEGORIES.map((c) => ({ value: c, label: c }))} />
          </Form.Item>
          {props.mode === 'edit' ? (
            <Form.Item name="sortOrder" label="Display order" extra="Lower numbers appear first in the gallery.">
              <InputNumber precision={0} style={{ width: '100%' }} />
            </Form.Item>
          ) : (
            <Form.Item name="slotKey" label="Publish to website" extra="Choose where this image should appear. You can also do this later.">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Keep in gallery only"
                options={PHOTO_SLOT_DEFS.map((s) => ({ value: s.key, label: `${s.label} — ${s.section}` }))}
              />
            </Form.Item>
          )}
        </Form>
      </div>
    </Modal>
  );
}

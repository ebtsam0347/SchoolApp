// The website image library: upload, search, edit, replace, reorder and delete.
// An image becomes visible on the public site when it is assigned to a page
// section (Page images tab / /admin/site-images) or chosen while uploading.
// Backed by the Worker's /media/admin API (worker/media.ts): files in KV, details in D1.

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Input, Select } from 'antd';
import { Images, Plus, Search } from 'lucide-react';
import { PageHeader, RequestState } from '@/components';
import { getErrorMessage, useDebounced } from '@/lib';
import { IMAGE_CATEGORIES, PHOTO_SLOT_DEFS, SITE_PAGES } from '@/site/photo-slots';
import { mediaApi, type MediaImage } from './api';
import { ImageFormModal } from './ImageFormModal';
import { ImageTile } from './ImageTile';
import { PreviewModal } from './PreviewModal';
import { ReplaceModal } from './ReplaceModal';

export function GalleryPanel() {
  const { message, modal } = AntdApp.useApp();
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const q = useDebounced(search, 250);
  const [category, setCategory] = useState<string>();
  const [page, setPage] = useState<string>();
  const [assigned, setAssigned] = useState<'yes' | 'no'>();
  const [uploadOpen, setUploadOpen] = useState(false);
  const [editing, setEditing] = useState<MediaImage | null>(null);
  const [replacing, setReplacing] = useState<MediaImage | null>(null);
  const [previewing, setPreviewing] = useState<MediaImage | null>(null);

  const filters = { q, category, page, assigned };
  const filterActive = !!(q || category || page || assigned);
  const images = useQuery({
    queryKey: ['media', 'images', filters],
    queryFn: ({ signal }) => mediaApi.list(filters, signal),
  });
  const items = images.data?.items ?? [];
  const refresh = () => qc.invalidateQueries({ queryKey: ['media'] });

  const move = useMutation({
    mutationFn: ({ index, dir }: { index: number; dir: -1 | 1 }) => {
      const ids = items.map((i) => i.id);
      [ids[index], ids[index + dir]] = [ids[index + dir], ids[index]];
      return mediaApi.reorder(ids);
    },
    onSuccess: () => refresh(),
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const confirmDelete = (image: MediaImage) => {
    const places = image.slots.map((k) => PHOTO_SLOT_DEFS.find((s) => s.key === k)?.label ?? k);
    modal.confirm({
      title: `Delete “${image.title}”?`,
      content:
        places.length > 0
          ? `This image is published on: ${places.join(', ')}. Deleting it removes it from the website too (those places go back to the default illustration). This cannot be undone.`
          : 'This permanently removes the image from the gallery. This cannot be undone.',
      okText: 'Delete image',
      okButtonProps: { danger: true },
      cancelText: 'Keep it',
      onOk: async () => {
        try {
          await mediaApi.remove(image.id, image.slots.length > 0);
          message.success('Image deleted.');
          await refresh();
        } catch (e) {
          message.error(getErrorMessage(e));
          throw e;
        }
      },
    });
  };

  const clearFilters = () => {
    setSearch('');
    setCategory(undefined);
    setPage(undefined);
    setAssigned(undefined);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-lg)', minWidth: 0 }}>
      <PageHeader
        eyebrow="Website"
        title="Gallery"
        description="Your library of school photographs. Upload here, then choose where each picture appears under Page images."
        actions={
          <Button type="primary" icon={<Plus size={14} />} onClick={() => setUploadOpen(true)}>
            Upload image
          </Button>
        }
      />

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <Input
          allowClear
          prefix={<Search size={14} aria-hidden="true" />}
          placeholder="Search by title, caption or alt text"
          aria-label="Search images"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ flex: '1 1 260px', maxWidth: 420 }}
        />
        <Select
          allowClear
          placeholder="Category"
          aria-label="Filter by category"
          value={category}
          onChange={setCategory}
          style={{ minWidth: 160 }}
          options={IMAGE_CATEGORIES.map((c) => ({ value: c, label: c }))}
        />
        <Select
          allowClear
          placeholder="Website page"
          aria-label="Filter by website page"
          value={page}
          onChange={setPage}
          style={{ minWidth: 170 }}
          options={SITE_PAGES.map((p) => ({ value: p.key, label: p.label }))}
        />
        <Select
          allowClear
          placeholder="Published?"
          aria-label="Filter by published status"
          value={assigned}
          onChange={setAssigned}
          style={{ minWidth: 140 }}
          options={[
            { value: 'yes', label: 'Published' },
            { value: 'no', label: 'Gallery only' },
          ]}
        />
      </div>

      <RequestState
        loading={images.isLoading}
        error={images.error}
        empty={items.length === 0}
        onRetry={() => images.refetch()}
        errorTitle="Couldn’t load the gallery"
        emptyIcon={Images}
        emptyTitle="No images yet"
        emptyDescription="Upload your first school photograph to start building the gallery."
        emptyAction={
          <Button type="primary" icon={<Plus size={14} />} onClick={() => setUploadOpen(true)}>
            Upload image
          </Button>
        }
        filterActive={filterActive}
        onClearFilters={clearFilters}
      >
        <ul
          style={{
            listStyle: 'none',
            margin: 0,
            padding: 0,
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 220px), 1fr))',
            gap: 16,
          }}
        >
          {items.map((image, index) => (
            <li key={image.id}>
              <ImageTile
                image={image}
                canMove={!filterActive && !move.isPending}
                isFirst={index === 0}
                isLast={index === items.length - 1}
                onPreview={() => setPreviewing(image)}
                onEdit={() => setEditing(image)}
                onReplace={() => setReplacing(image)}
                onDelete={() => confirmDelete(image)}
                onMove={(dir) => move.mutate({ index, dir })}
              />
            </li>
          ))}
        </ul>
      </RequestState>

      <ImageFormModal mode="upload" open={uploadOpen} onClose={() => setUploadOpen(false)} onDone={() => refresh()} />
      <ImageFormModal mode="edit" open={!!editing} image={editing} onClose={() => setEditing(null)} onDone={() => refresh()} />
      <ReplaceModal image={replacing} onClose={() => setReplacing(null)} onDone={() => refresh()} />
      <PreviewModal image={previewing} onClose={() => setPreviewing(null)} />
    </div>
  );
}

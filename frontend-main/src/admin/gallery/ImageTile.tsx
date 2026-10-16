// One gallery card: consistent 4:3 thumbnail, key details and the actions.

import { Button, Card, Tag, Tooltip, theme as antdTheme } from 'antd';
import { ArrowLeft, ArrowRight, Eye, Pencil, RefreshCw, Trash2 } from 'lucide-react';
import type { MediaImage } from './api';
import { SafeImg } from './SafeImg';

export function ImageTile({
  image,
  canMove,
  isFirst,
  isLast,
  onPreview,
  onEdit,
  onReplace,
  onDelete,
  onMove,
}: {
  image: MediaImage;
  /** Moving is only meaningful on the unfiltered library. */
  canMove: boolean;
  isFirst: boolean;
  isLast: boolean;
  onPreview: () => void;
  onEdit: () => void;
  onReplace: () => void;
  onDelete: () => void;
  onMove: (dir: -1 | 1) => void;
}) {
  const { token } = antdTheme.useToken();
  return (
    <Card
      size="small"
      styles={{ body: { padding: 12 } }}
      cover={
        <button
          type="button"
          onClick={onPreview}
          aria-label={`Preview ${image.title}`}
          style={{ all: 'unset', cursor: 'zoom-in', display: 'block', aspectRatio: '4 / 3', background: token.colorFillTertiary, overflow: 'hidden' }}
        >
          <SafeImg src={image.url} alt={image.alt ?? image.title} style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
        </button>
      }
    >
      <div style={{ fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={image.title}>
        {image.title}
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, margin: '6px 0 10px' }}>
        <Tag style={{ margin: 0 }}>{image.category}</Tag>
        {image.slots.length > 0 ? (
          <Tag color="green" style={{ margin: 0 }}>
            Published · {image.slots.length}
          </Tag>
        ) : (
          <Tag style={{ margin: 0 }}>Gallery only</Tag>
        )}
      </div>
      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
        <Tooltip title="Preview">
          <Button size="small" icon={<Eye size={14} />} aria-label="Preview" onClick={onPreview} />
        </Tooltip>
        <Tooltip title="Edit details">
          <Button size="small" icon={<Pencil size={14} />} aria-label="Edit details" onClick={onEdit} />
        </Tooltip>
        <Tooltip title="Replace image">
          <Button size="small" icon={<RefreshCw size={14} />} aria-label="Replace image" onClick={onReplace} />
        </Tooltip>
        {canMove && (
          <>
            <Tooltip title="Move earlier">
              <Button size="small" icon={<ArrowLeft size={14} />} aria-label="Move earlier" disabled={isFirst} onClick={() => onMove(-1)} />
            </Tooltip>
            <Tooltip title="Move later">
              <Button size="small" icon={<ArrowRight size={14} />} aria-label="Move later" disabled={isLast} onClick={() => onMove(1)} />
            </Tooltip>
          </>
        )}
        <Tooltip title="Delete">
          <Button size="small" danger icon={<Trash2 size={14} />} aria-label="Delete" onClick={onDelete} style={{ marginLeft: 'auto' }} />
        </Tooltip>
      </div>
    </Card>
  );
}

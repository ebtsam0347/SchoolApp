import { Card, theme as antdTheme, Typography } from 'antd';
import {
  FileArchive,
  FileImage,
  FileSpreadsheet,
  FileText,
  File as FileIcon,
} from 'lucide-react';
import type { CSSProperties, ReactNode } from 'react';

const { useToken } = antdTheme;
const { Text } = Typography;

export type DocumentCardProps = {
  title: string;
  /** Original file name, shown beneath title. */
  fileName?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  uploadedAt?: string | Date | null;
  uploadedBy?: string | null;
  /** Right-aligned actions (View, Download, Delete buttons). */
  actions?: ReactNode;
  /** Compact for tables/sidebars; default for the Documents tab grid. */
  size?: 'compact' | 'default';
  className?: string;
  styles?: { root?: CSSProperties; body?: CSSProperties };
};

function pickIcon(contentType?: string | null, fileName?: string | null) {
  const ct = (contentType ?? '').toLowerCase();
  const ext = (fileName ?? '').split('.').pop()?.toLowerCase() ?? '';
  if (ct.startsWith('image/') || ['png', 'jpg', 'jpeg', 'webp', 'gif'].includes(ext)) {
    return FileImage;
  }
  if (
    ct.includes('spreadsheet') ||
    ct.includes('excel') ||
    ['xls', 'xlsx', 'csv'].includes(ext)
  ) {
    return FileSpreadsheet;
  }
  if (ct.includes('zip') || ['zip', 'tar', 'gz', '7z', 'rar'].includes(ext)) {
    return FileArchive;
  }
  if (ct.includes('pdf') || ct.includes('text') || ['pdf', 'txt', 'md'].includes(ext)) {
    return FileText;
  }
  return FileIcon;
}

function formatBytes(bytes?: number | null): string | null {
  if (!bytes && bytes !== 0) return null;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatDate(value: string | Date): string {
  const d = value instanceof Date ? value : new Date(value);
  return new Intl.DateTimeFormat('en-PK', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(d);
}

export function DocumentCard({
  title,
  fileName,
  contentType,
  sizeBytes,
  uploadedAt,
  uploadedBy,
  actions,
  size = 'default',
  className,
  styles: slot,
}: DocumentCardProps) {
  const { token } = useToken();
  const Icon = pickIcon(contentType, fileName);

  const meta = [
    formatBytes(sizeBytes),
    uploadedAt ? `Uploaded ${formatDate(uploadedAt)}` : null,
    uploadedBy ? `by ${uploadedBy}` : null,
  ].filter(Boolean);

  const compact = size === 'compact';
  const padding = compact ? 12 : 16;
  const iconSize = compact ? 32 : 44;
  const iconStroke = compact ? 18 : 22;

  return (
    <Card
      className={className}
      hoverable
      style={{ ...slot?.root }}
      styles={{
        body: {
          padding,
          display: 'flex',
          alignItems: 'center',
          gap: compact ? 10 : 14,
          ...slot?.body,
        },
      }}
    >
      <div
        style={{
          width: iconSize,
          height: iconSize,
          borderRadius: token.borderRadiusLG,
          background: `color-mix(in srgb, ${token.colorPrimary} 12%, transparent)`,
          color: token.colorPrimary,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <Icon size={iconStroke} strokeWidth={1.75} aria-hidden />
      </div>

      <div style={{ minWidth: 0, flex: 1 }}>
        <div
          style={{
            fontSize: compact ? 13 : 14,
            fontWeight: 600,
            color: token.colorTextHeading,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {title}
        </div>
        {fileName ? (
          <Text
            type="secondary"
            style={{
              display: 'block',
              fontSize: 12,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {fileName}
          </Text>
        ) : null}
        {meta.length > 0 ? (
          <Text
            type="secondary"
            style={{
              display: 'block',
              fontSize: 11,
              marginTop: 2,
              color: token.colorTextTertiary,
            }}
          >
            {meta.join(' · ')}
          </Text>
        ) : null}
      </div>

      {actions ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0 }}>
          {actions}
        </div>
      ) : null}
    </Card>
  );
}

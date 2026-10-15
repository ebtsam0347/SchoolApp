import { theme as antdTheme, Typography } from 'antd';
import { Download, Paperclip } from 'lucide-react';
import type { AttachmentDto } from '../types';

const { Text } = Typography;
const { useToken } = antdTheme;

export type AttachmentChipProps = {
  attachment: AttachmentDto;
};

const KB = 1024;
const MB = KB * 1024;

function formatSize(bytes: number): string {
  if (bytes >= MB) return `${(bytes / MB).toFixed(1)} MB`;
  if (bytes >= KB) return `${Math.round(bytes / KB)} KB`;
  return `${bytes} B`;
}

export function AttachmentChip({ attachment }: AttachmentChipProps) {
  const { token } = useToken();
  const href = attachment.downloadUrl ?? '#';
  const isLink = !!attachment.downloadUrl;

  return (
    <a
      href={href}
      target={isLink ? '_blank' : undefined}
      rel={isLink ? 'noreferrer' : undefined}
      onClick={(e) => {
        if (!isLink) e.preventDefault();
      }}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        padding: '6px 10px',
        borderRadius: token.borderRadius,
        background: token.colorFillSecondary,
        color: token.colorText,
        textDecoration: 'none',
        cursor: isLink ? 'pointer' : 'default',
        opacity: isLink ? 1 : 0.6,
        maxWidth: 280,
      }}
    >
      <Paperclip size={14} style={{ flexShrink: 0 }} />
      <div style={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <Text ellipsis style={{ fontSize: 12, fontWeight: 500 }}>
          {attachment.originalName}
        </Text>
        <Text type="secondary" style={{ fontSize: 11 }}>
          {formatSize(attachment.sizeBytes)}
        </Text>
      </div>
      {isLink && <Download size={14} style={{ flexShrink: 0, opacity: 0.6 }} />}
    </a>
  );
}

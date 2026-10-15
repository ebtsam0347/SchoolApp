// Read-only JSON renderer for audit drawers, raw-payload tabs, and any
// place we surface a server response for inspection. Monospace, theme-aware
// background, copy-to-clipboard built in.
//
// Why not a full syntax-highlighter dependency: the audit explorer renders
// 50–100 row payloads per session and we don't want to ship Prism/Shiki
// just for that. The plain-text rendering is good enough; readability comes
// from `JSON.stringify(value, null, 2)` and the contained scroll box.

import { App as AntdApp, Button, theme as antdTheme, Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import { Copy } from 'lucide-react';

const { Text } = Typography;
const { useToken } = antdTheme;

export type JsonViewerProps = {
  /** Any JSON-serializable value. Strings are passed through as-is. */
  value: unknown;
  /** Indent passed to JSON.stringify. Defaults to 2. */
  indent?: number;
  /** Max height for the scroll box. Defaults to 480. */
  maxHeight?: number | string;
  /** Hide the copy button. */
  hideCopy?: boolean;
  /** Toolbar slot for extra buttons (e.g. "View by correlation"). */
  toolbar?: ReactNode;
  /** Override the success toast on copy. Set null to suppress. */
  copyMessage?: string | null;
  className?: string;
  style?: CSSProperties;
};

export function JsonViewer({
  value,
  indent = 2,
  maxHeight = 480,
  hideCopy,
  toolbar,
  copyMessage = 'Copied to clipboard',
  className,
  style,
}: JsonViewerProps) {
  const { token } = useToken();
  const { message } = AntdApp.useApp();

  const text =
    typeof value === 'string' ? value : safeStringify(value, indent);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      if (copyMessage) message.success(copyMessage);
    } catch {
      message.error('Could not copy. Select the text manually.');
    }
  };

  return (
    <div className={className} style={style}>
      {(!hideCopy || toolbar) && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 'var(--app-space-sm)',
            marginBottom: 'var(--app-space-sm)',
          }}
        >
          {toolbar}
          {hideCopy ? null : (
            <Button
              size="small"
              icon={<Copy size={12} strokeWidth={1.75} />}
              onClick={handleCopy}
              style={{ marginLeft: 'auto' }}
            >
              Copy
            </Button>
          )}
        </div>
      )}
      <pre
        style={{
          margin: 0,
          padding: 'var(--app-space-md)',
          background: token.colorFillTertiary,
          color: token.colorText,
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: 'var(--app-radius-sm)',
          fontFamily:
            'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Monaco, Consolas, monospace',
          fontSize: 12,
          lineHeight: 1.55,
          maxHeight,
          overflow: 'auto',
          whiteSpace: 'pre',
        }}
      >
        <Text style={{ color: 'inherit' }}>{text}</Text>
      </pre>
    </div>
  );
}

function safeStringify(value: unknown, indent: number): string {
  try {
    return JSON.stringify(value, null, indent);
  } catch (err) {
    return `// JSON.stringify failed: ${(err as Error).message}\n${String(value)}`;
  }
}

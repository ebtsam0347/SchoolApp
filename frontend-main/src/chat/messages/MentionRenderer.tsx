import { theme as antdTheme } from 'antd';
import { Fragment } from 'react';

const { useToken } = antdTheme;

const MENTION_RE = /@([\w.-]+)/g;

export type MentionRendererProps = {
  body: string;
};

/** Renders the message body with `@token` segments tinted via `colorPrimary`. */
export function MentionRenderer({ body }: MentionRendererProps) {
  const { token } = useToken();
  const parts: Array<{ type: 'text' | 'mention'; value: string }> = [];
  let last = 0;
  for (const m of body.matchAll(MENTION_RE)) {
    const [full] = m;
    const start = m.index ?? 0;
    if (start > last) parts.push({ type: 'text', value: body.slice(last, start) });
    parts.push({ type: 'mention', value: full });
    last = start + full.length;
  }
  if (last < body.length) parts.push({ type: 'text', value: body.slice(last) });

  return (
    <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
      {parts.map((p, i) => (
        <Fragment key={i}>
          {p.type === 'mention' ? (
            <span
              style={{
                color: token.colorPrimary,
                fontWeight: 500,
                background: `color-mix(in srgb, ${token.colorPrimary} 12%, transparent)`,
                padding: '0 4px',
                borderRadius: 4,
              }}
            >
              {p.value}
            </span>
          ) : (
            p.value
          )}
        </Fragment>
      ))}
    </span>
  );
}
